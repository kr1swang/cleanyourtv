"""tools/trace_report.py: the numbers it takes from a trace, and how it reports runs and changes.

The report reads traces with Perfetto's query engine, which CI never downloads. Its SQL is plain
SQLite, though, so these tests run the same queries against a small in-memory database laid out
like a trace (process, thread, thread_track, slice, actual_frame_timeline_slice) and check the
numbers by hand. A test that needs the real engine runs only when the optional perfetto package is
installed and CLEANYOURTV_TRACE names a trace recorded with tools/trace.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("cyt_trace_report", ROOT / "tools" / "trace_report.py")
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)

PKG = "com.gathin.tvhome"
MS = 1_000_000


def fake_trace(cold=False, frames=(), draws=(), missed=1, gpu=()):
    """A database shaped like a trace of the launcher coming to the front at t = 1000 ms."""
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript("""
        create table process (upid integer, pid integer, name text);
        create table thread (utid integer, upid integer, tid integer, name text);
        create table thread_track (id integer, utid integer);
        create table slice (id integer primary key, ts integer, dur integer, name text, track_id integer);
        create table actual_frame_timeline_slice (ts integer, dur integer, upid integer, jank_type text);
        insert into process values (1, 90, 'com.gathin.tvhome'), (2, 100, 'com.gathin.tvhome'), (3, 200, 'other');
        insert into thread values (1, 2, 100, 'com.gathin.tvhome'), (2, 2, 101, 'RenderThread'),
                                  (3, 1, 90, 'com.gathin.tvhome');
        insert into thread_track values (10, 1), (20, 2), (30, 3);
    """)
    main = [("performStart:com.gathin.tvhome.MainActivity", 1000, 5), ("activityResume", 1005, 30),
            ("traversal", 1040, 20), ("traversal", 1200, 5)]
    if cold:
        main = [("bindApplication", 900, 40), ("activityStart", 945, 50)] + main
    for name, ts, dur in main:
        db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, ?, 10)", (ts * MS, dur * MS, name))
    db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, 'performStart:old', 30)", (100 * MS, MS))
    for ts, dur in [(1055, 10)] + list(draws):
        db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, 'DrawFrames 1', 20)", (ts * MS, dur * MS))
    for ts, dur in gpu:
        db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, 'waiting for GPU completion 5', 20)",
                   (ts * MS, dur * MS))
    for i, dur in enumerate(frames):
        jank = "App Deadline Missed" if i < missed else "Buffer Stuffing"
        db.execute("insert into actual_frame_timeline_slice values (?, ?, 2, ?)", ((1060 + 20 * i) * MS, dur * MS, jank))
    return lambda sql: [dict(r) for r in db.execute(sql)]


class StartMetricsTest(unittest.TestCase):
    def test_warm_return_from_performstart_to_the_first_frame_drawn(self):
        m = report.start_metrics(fake_trace(frames=[16, 16, 16]), PKG)
        self.assertEqual(m["first frame"], 65)        # DrawFrame ends at 1065, after the traversal's 1060
        self.assertEqual(m["onResume"], 30)
        self.assertEqual(m["first traversal"], 20)
        self.assertEqual(m["frames"], 1)
        self.assertEqual(m["missed"], 1)               # buffer stuffing is not a missed deadline
        self.assertNotIn("bindApplication", m)

    def test_cold_start_counts_from_bindapplication(self):
        m = report.start_metrics(fake_trace(cold=True), PKG)
        self.assertEqual(m["first frame"], 165)
        self.assertEqual((m["bindApplication"], m["activityStart"]), (40, 50))

    def test_frames_outside_the_window_are_not_counted(self):
        m = report.start_metrics(fake_trace(draws=[(1500, 8), (2600, 8)]), PKG)
        self.assertEqual(m["frames"], 2)

    def test_newest_launcher_process_is_used(self):
        self.assertEqual(report.threads(fake_trace(), PKG), (2, 1, 2))

    def test_a_trace_without_the_launcher_gives_nothing(self):
        self.assertEqual(report.start_metrics(fake_trace(), "com.example.other"), {})


class NavMetricsTest(unittest.TestCase):
    def test_frame_times_draws_gpu_and_missed_deadlines(self):
        q = fake_trace(frames=[10, 12, 14, 16, 40], missed=2, draws=[(1100, 6), (1120, 8)], gpu=[(1101, 2), (1121, 4)])
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 5)
        self.assertEqual(m["frame median"], 14)
        self.assertEqual(m["frame p90"], 40)
        self.assertEqual(m["draw median"], 8)
        self.assertEqual(m["gpu median"], 3)
        self.assertEqual(m["missed"], 2)


class ReportTest(unittest.TestCase):
    RUNS = [("home-1", {"first frame": 90, "onResume": 40}), ("home-2", {"first frame": 300, "onResume": 290}),
            ("home-3", {"first frame": 80, "onResume": 35})]
    AFTER = [("home-1", {"first frame": 50, "onResume": 2}), ("home-2", {"first frame": 45, "onResume": 3})]

    def test_runs_with_median_range_and_a_starred_outlier(self):
        text = report.report(self.RUNS, None, False, nav=False)
        row = next(l for l in text.splitlines() if l.startswith("start to first frame"))
        self.assertIn("300*", row)
        self.assertIn("90", row.split()[-2])        # median
        self.assertIn("80–300", row)
        self.assertIn("more than twice the median", text)

    def test_comparison_shows_the_change_in_the_median(self):
        text = report.report(self.RUNS, self.AFTER, True, nav=False)
        self.assertIn("| start to first frame drawn (ms) | 90 | 48 | -47% |", text)
        self.assertIn("|---|---|---|---|", text)

    def test_metrics_no_run_has_are_left_out(self):
        text = report.report(self.RUNS, None, False, nav=False)
        self.assertNotIn("bindApplication", text)


class FilesTest(unittest.TestCase):
    def test_folders_expand_in_run_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            for n in (1, 2, 10):
                Path(tmp, f"home-{n}.pftrace").write_bytes(b"")
            self.assertEqual([os.path.basename(p) for p in report.expand([tmp])],
                             ["home-1.pftrace", "home-2.pftrace", "home-10.pftrace"])

    def test_scenario_comes_from_the_sidecar_then_the_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "run-1.pftrace")
            path.with_suffix(".json").write_text('{"scenario": "nav"}')
            self.assertEqual(report.scenario_of(str(path)), "nav")
            self.assertEqual(report.scenario_of(str(Path(tmp, "cold-3.pftrace"))), "cold")

    def test_missing_perfetto_says_what_to_install(self):
        with mock.patch.dict(sys.modules, {"perfetto": None, "perfetto.trace_processor": None}), \
                contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
            report.opener()
        self.assertIn("requirements-perf.txt", str(caught.exception.code))


@unittest.skipUnless(os.environ.get("CLEANYOURTV_TRACE") and importlib.util.find_spec("perfetto"),
                     "needs the optional perfetto package and CLEANYOURTV_TRACE=<a trace from tools/trace.py>")
class RealTraceTest(unittest.TestCase):
    def test_the_real_engine_runs_the_same_queries(self):
        path = os.environ["CLEANYOURTV_TRACE"]
        q, close = report.opener()(path)
        try:
            metrics = report.nav_metrics(q, PKG) if report.scenario_of(path) == "nav" else report.start_metrics(q, PKG)
        finally:
            close()
        self.assertIn("frames", metrics)


if __name__ == "__main__":
    unittest.main()
