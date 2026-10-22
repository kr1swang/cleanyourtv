# tools/trace.py and tools/trace_report.py

Record Perfetto traces of the launcher on the TV, then report where its time goes. For headline numbers
only (key latency, jank, cold start, PSS), use `tools/bench.py` instead: it needs no extra install.

## Setup

```bash
tools/venv/bin/pip install -r tools/requirements-perf.txt
```

Recording needs only `tools/requirements.txt` and a TV on Android 9 or later. The report's first run
downloads Perfetto's query engine (about 13 MB, SHA-256 checked) to `~/.local/share/perfetto`. To avoid
that, download `trace_processor_shell` yourself and pass `--engine PATH`.

## Record

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/trace.py home --runs 5 --out traces/before
```

| Scenario | Recorded | Use for |
|---|---|---|
| `home` | HOME pressed while another app is in front | Most Home changes |
| `cold` | `am start -W` after a force-stop, another app in front | Start-up work (`onCreate`, config, fonts) |
| `coldsys` | `am force-stop` with Home in front; Android restarts it | The restart after Home was killed for memory |
| `nav` | 24 D-pad moves, 0.35 s apart | Focus, scrolling, drawing |
| `custom` | `--step CMD` (repeatable) after `--setup CMD` | Anything else; `sleep N` steps set the length |

- **Output:** each run writes `<scenario>-<n>.pftrace` (10–20 MB) and a `.json` with the commands and the
  TV's model and build. `traces/` is gitignored.
- **The other app:** `--app package/activity`, or by default the TV's Settings.

## Report

```bash
tools/venv/bin/python tools/trace_report.py traces/before --vs traces/after --markdown
```

| Scenario | Numbers |
|---|---|
| Starts (`home`, `cold`, `coldsys`, `custom`) | Start to first frame drawn; the steps inside it (`bindApplication`, `onCreate`, `onStart`, `onResume`, first layout and draw); then the frames drawn and missed deadlines in the next 1.5 s |
| `nav` | Frame time (median, 90th percentile); RenderThread draw; GPU wait; missed deadlines |

With `--vs`, a third table shows the change in each median. A run over twice the median is starred.

## Rules

- **Repeat runs.** Record 5 runs per build, on the same TV with the same config, theme and wallpaper.
  Compare medians.
- **Keep starred runs.** They're usually memory pressure on a 1 GB TV. Report them; don't drop them.
- **Don't run `am start` after a force-stop with Home in front.** Android has already restarted Home, so
  `onResume` appears twice. Use `cold` or `coldsys`.
- **Check the remote afterwards.** A force-stop switches off the accessibility helpers. `trace.py` puts the
  list back after the last run, but on some TCL sets a helper still won't bind (`docs/TROUBLESHOOTING.md`).
- **Look in the right place for GPU savings.** Less overdraw shows as GPU wait in `nav`, not as main-thread
  time.
- **Expect no latency gain from off-main-thread work.** It removes work, but doesn't shorten the time to
  the first frame.
- **Never commit the `.pftrace` files.** They hold the TV's process list.
