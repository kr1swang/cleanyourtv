"""Run the real HomeWallpaper coordinator with deterministic player/lifecycle doubles.

The doubles replace Android surfaces and decoding, not the coordinator under test. This checks
selection races without a TV; real decoder timing, SurfaceView composition and FPS still need one.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/HomeWallpaper.java"

ACTIVITY = """package android.app;
public class Activity {
    public boolean hasWindowFocus() { return true; }
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
}
"""
FRAME = """package android.widget;
public class FrameLayout {
    public void removeView(Object v) {}
    public void setBackgroundColor(int c) {}
    public void setBackground(Object v) {}
}
"""
DOUBLES = """package com.gathin.tvhome;
import android.app.Activity;
import android.widget.FrameLayout;
import java.io.File;
import java.util.*;
import java.util.concurrent.Future;
class Config {
    static Config current;
    File wallpaper;
    List<File> wallpapers;
    boolean wallpaperEnabled = true, animations = true, reducedMotion;
    int backgroundColor, skyIntensity = 20;
    Config(String size) {
        wallpaper = new File("library", size + ".png");
        wallpapers = Collections.singletonList(wallpaper);
    }
    static Config load(Activity a) { return current; }
}
class WallpaperLibrary {
    static final String PREFIX = "download:";
    static String selected;
    static Runnable fetched;
    static String choice(Activity a) { return selected; }
    static File directory(Activity a) { return new File("library"); }
    static Future<?> prefetch(Activity a, boolean allow, Runnable ready) { fetched = ready; return null; }
}
class WallpaperPlayer {
    static final List<WallpaperPlayer> made = new ArrayList<>();
    final Config config;
    boolean cached, detached, stopped, closed;
    Runnable ready;
    WallpaperPlayer(Activity a, FrameLayout root, Config c) { this(a, root, c, false); }
    WallpaperPlayer(Activity a, FrameLayout root, Config c, boolean defer) {
        config = c; cached = !defer; made.add(this);
    }
    Object poster() { return cached ? this : null; }
    boolean canReuse(Config c) { return cached && config.wallpapers.equals(c.wallpapers); }
    void start() { stopped = false; }
    void start(Runnable r) { ready = r; start(); }
    void complete() { cached = true; if (ready != null) ready.run(); }
    void stop() { stopped = true; }
    void detach() { detached = true; closed = true; }
    void close() { closed = true; }
    void update() {}
    void separateSurface(boolean separate) {}
    void releaseForMemoryPressure() { cached = false; }
}
class VideoWallpaper {
    static final List<VideoWallpaper> made = new ArrayList<>();
    final String selection = WallpaperLibrary.selected;
    boolean released, playing;
    static VideoWallpaper attach(Activity a, FrameLayout root, Object poster, boolean animate) {
        if (WallpaperLibrary.selected.endsWith("static")) return null;
        VideoWallpaper v = new VideoWallpaper(); made.add(v); return v;
    }
    void resumePlayback() { playing = true; released = false; }
    void pausePlayback() { released = true; playing = false; }
}
"""
HARNESS = """package com.gathin.tvhome;
import android.app.Activity;
import android.widget.FrameLayout;
public class Harness {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void choose(String size, String format) {
        Config.current = new Config(size);
        WallpaperLibrary.selected = "download:" + size + "-" + format;
    }
    static WallpaperPlayer lastImage() { return WallpaperPlayer.made.get(WallpaperPlayer.made.size()-1); }
    static VideoWallpaper lastVideo() { return VideoWallpaper.made.get(VideoWallpaper.made.size()-1); }
    public static void main(String[] args) {
        choose("fhd", "30");
        HomeWallpaper home = new HomeWallpaper(new Activity(), new FrameLayout(), Config.current, n -> {});
        home.onStart(); home.start();
        WallpaperPlayer first = lastImage();
        VideoWallpaper originalVideo = lastVideo();
        switch (args[0]) {
        case "shared_preview":
            choose("fhd", "60"); home.refresh();
            check(WallpaperPlayer.made.size() == 1 && !first.detached);
            check(originalVideo.released && lastVideo().playing);
            home.refresh();
            check(VideoWallpaper.made.size() == 2);
            choose("fhd", "static"); home.refresh();
            check(WallpaperPlayer.made.size() == 1 && !home.hasVideo());
            break;
        case "keep_old_until_ready":
            choose("4k", "60"); home.refresh();
            WallpaperPlayer pending = lastImage();
            check(pending != first && !pending.cached && !first.detached && originalVideo.playing);
            pending.complete();
            check(first.detached && originalVideo.released && lastVideo().playing);
            check(lastVideo().selection.equals("download:4k-60"));
            break;
        case "reverse_before_ready":
            choose("4k", "60"); home.refresh();
            WallpaperPlayer cancelled = lastImage();
            choose("fhd", "30"); home.refresh();
            cancelled.complete();
            check(cancelled.detached && !first.detached && originalVideo.playing);
            check(VideoWallpaper.made.size() == 1);
            break;
        case "stop_and_resume":
            choose("4k", "60"); home.refresh();
            WallpaperPlayer stopped = lastImage();
            home.stop(); home.onStop(); stopped.complete();
            check(stopped.detached && !first.detached && originalVideo.released);
            check(VideoWallpaper.made.size() == 1);
            home.onStart(); home.refresh(); home.start(); lastImage().complete();
            check(first.detached && lastVideo().selection.equals("download:4k-60"));
            break;
        case "choice_changes_before_callback":
            choose("4k", "60"); home.refresh();
            WallpaperPlayer stale = lastImage();
            choose("4k", "30"); stale.complete();
            check(stale.detached && !first.detached && VideoWallpaper.made.size() == 1);
            lastImage().complete();
            check(lastVideo().selection.equals("download:4k-30"));
            break;
        case "close_rejects_callbacks":
            choose("4k", "60"); home.refresh();
            WallpaperPlayer late = lastImage();
            home.close(); late.complete(); WallpaperLibrary.fetched.run();
            check(first.closed && late.detached && originalVideo.released);
            check(VideoWallpaper.made.size() == 1);
            break;
        default: throw new AssertionError(args[0]);
        }
    }
}
"""


class HomeWallpaperLifecycle(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        java_home = os.environ.get("JAVA_HOME")
        cls.java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
        javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
        if not cls.java or not javac:
            raise unittest.SkipTest("JDK required for the wallpaper lifecycle harness")
        if subprocess.run([javac, "-version"], capture_output=True).returncode:
            raise unittest.SkipTest("JDK not configured; set JAVA_HOME as for the launcher build")
        cls.tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.tmp.cleanup)
        folder = Path(cls.tmp.name)
        sources = {"Activity.java": ACTIVITY, "FrameLayout.java": FRAME,
                   "Doubles.java": DOUBLES, "Harness.java": HARNESS}
        for name, contents in sources.items():
            (folder / name).write_text(contents, encoding="utf-8")
        built = subprocess.run([javac, "-d", str(folder), str(SOURCE),
                                *(str(folder / name) for name in sources)],
                               capture_output=True, text=True, timeout=30)
        if built.returncode:
            raise AssertionError(built.stderr)

    def test_background_switch_lifecycle(self):
        for scenario in ("shared_preview", "keep_old_until_ready", "reverse_before_ready",
                         "stop_and_resume", "choice_changes_before_callback", "close_rejects_callbacks"):
            with self.subTest(scenario=scenario):
                ran = subprocess.run([self.java, "-cp", self.tmp.name, "com.gathin.tvhome.Harness", scenario],
                                     capture_output=True, text=True, timeout=10)
                self.assertEqual(0, ran.returncode, ran.stdout + ran.stderr)
