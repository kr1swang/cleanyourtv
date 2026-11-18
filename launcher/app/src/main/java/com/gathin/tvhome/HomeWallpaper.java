package com.gathin.tvhome;

import android.app.Activity;
import android.widget.FrameLayout;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

/** Owns the fork's image/video swap; decoding and rotation still belong to WallpaperPlayer. */
final class HomeWallpaper {
    private final Activity activity;
    private final FrameLayout root;
    private final IntConsumer skyIntensity;
    private WallpaperPlayer image, pending;
    private VideoWallpaper video;
    private Future<?> download;
    private String selection, pendingSelection;
    private boolean allowDefault, downloaded, started, closed;

    HomeWallpaper(Activity activity, FrameLayout root, Config config, IntConsumer skyIntensity) {
        this.activity = activity;
        this.root = root;
        this.skyIntensity = skyIntensity;
        // Preserve the upstream cold-start image. Only in-place changes defer their first decode.
        install(new WallpaperPlayer(activity, root, config), config, WallpaperLibrary.choice(activity));
    }

    boolean hasVideo() { return video != null; }

    void onStart() {
        started = true;
        if (video != null) {
            if (image.poster() == null) image.start();
            video.resumePlayback();
        }
        download = WallpaperLibrary.prefetch(activity, allowDefault, () -> {
            if (closed || activity.isFinishing() || activity.isDestroyed()) return;
            downloaded = true;
            if (activity.hasWindowFocus()) refresh();
        });
    }

    void start() {
        if (video == null) image.start();
        if (pending != null) pending.start();
    }

    void stop() {
        image.stop();
        if (pending != null) pending.stop();
    }

    void onStop() {
        started = false;
        stop();
        cancelPending();
        if (download != null) { download.cancel(true); download = null; }
        if (video != null) video.pausePlayback();
    }

    void update() { image.update(); }
    void releaseForMemoryPressure() { image.releaseForMemoryPressure(); }

    void close() {
        closed = true;
        onStop();
        image.close();
    }

    /** Keep the old background visible while the requested image is decoded off the UI thread. */
    void refresh() {
        if (!started || closed) return;
        String choice = WallpaperLibrary.choice(activity);
        if (pending != null && Objects.equals(pendingSelection, choice) && !downloaded) return;
        cancelPending();
        if (Objects.equals(selection, choice) && !downloaded) return;
        Config config = Config.load(activity);
        downloaded = false;
        // Catalog images have immutable hash-based filenames; a 30/60fps switch shares one image.
        boolean libraryImage = config.wallpaper != null
                && WallpaperLibrary.directory(activity).equals(config.wallpaper.getParentFile());
        if (libraryImage && image.canReuse(config)) {
            install(image, config, choice);
            return;
        }
        WallpaperPlayer next = new WallpaperPlayer(activity, root, config, true);
        pending = next;
        pendingSelection = choice;
        next.start(() -> {
            if (pending != next) return;
            // A preference may have changed while decoding, even before Home receives focus again.
            if (!Objects.equals(choice, WallpaperLibrary.choice(activity))) {
                cancelPending();
                refresh();
                return;
            }
            pending = null;
            pendingSelection = null;
            install(next, config, choice);
        });
    }

    private void cancelPending() {
        if (pending != null) pending.detach();
        pending = null;
        pendingSelection = null;
    }

    private void install(WallpaperPlayer next, Config config, String choice) {
        if (video != null) {
            video.pausePlayback();
            root.removeView(video);
            video = null;
        }
        if (image != null && image != next) image.detach();
        image = next;
        selection = choice;
        root.setBackgroundColor(config.backgroundColor);
        allowDefault = config.wallpaperEnabled && (config.wallpapers.isEmpty()
                || (config.wallpaper != null
                    && WallpaperLibrary.directory(activity).equals(config.wallpaper.getParentFile())));
        if (config.wallpaperEnabled && (choice == null || choice.startsWith(WallpaperLibrary.PREFIX))) {
            video = VideoWallpaper.attach(activity, root, image.poster(), config.animations && !config.reducedMotion);
        }
        image.separateSurface(video == null);
        if (image.poster() != null) root.setBackground(null);
        skyIntensity.accept(video == null ? config.skyIntensity : 0);
        if (started) {
            if (video != null) video.resumePlayback();
            else image.start();
        }
    }
}
