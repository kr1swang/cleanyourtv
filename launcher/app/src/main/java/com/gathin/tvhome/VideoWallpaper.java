package com.gathin.tvhome;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import java.io.File;
import org.json.JSONObject;

/** Optional, silent theme video. A SurfaceView lets the decoder compose behind the launcher UI. */
final class VideoWallpaper extends SurfaceView implements SurfaceHolder.Callback, DisplayManager.DisplayListener {
    private static final String TAG = "VideoWallpaper";
    private final VideoWallpaperSources sources;
    private File playingFile;
    private final View poster;
    private final boolean animateTransition;
    private MediaPlayer player;
    private boolean resumed;
    private boolean failed;

    static VideoWallpaper attach(Context context, FrameLayout root, View poster, boolean animateTransition) {
        File dir = Config.dir(context);
        if (dir == null) return null;
        try {
            JSONObject json = Config.readTheme(context);
            if (json == null) return null;
            String name = json.optString("video_wallpaper", "");
            if (name.isEmpty()) return null;
            VideoWallpaperSources sources = new VideoWallpaperSources(dir, name,
                    json.optString("video_wallpaper_1080p", ""));
            if (sources.select(0, 0) == null) return null;
            VideoWallpaper view = new VideoWallpaper(context, sources, poster, animateTransition);
            root.addView(view, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return view;
        } catch (Exception e) {
            Log.w(TAG, "Cannot read theme video; keeping the static background", e);
            return null;
        }
    }

    private VideoWallpaper(Context context, VideoWallpaperSources sources, View poster, boolean animateTransition) {
        super(context);
        this.sources = sources;
        this.poster = poster;
        this.animateTransition = animateTransition;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        getHolder().addCallback(this);
    }

    void resumePlayback() {
        resumed = true;
        failed = false;
        startIfReady();
    }

    void pausePlayback() {
        resumed = false;
        releasePlayer();
    }

    private void startIfReady() {
        if (!resumed || failed || player != null || getWindowVisibility() != VISIBLE
                || !getHolder().getSurface().isValid()) return;
        File file = selectedFile();
        if (file == null) return;
        MediaPlayer next = new MediaPlayer();
        player = next;
        playingFile = file;
        try {
            next.setDataSource(file.getPath());
            next.setDisplay(getHolder());
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setOnPreparedListener(mp -> {
                if (player != mp || !resumed) return;
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                // A new decoder always starts at the beginning, matching the static poster.
                Log.i(TAG, "Playing " + mp.getVideoWidth() + "x" + mp.getVideoHeight()
                        + " from " + mp.getCurrentPosition() + "ms: " + file.getName());
                mp.start();
            });
            next.setOnInfoListener((mp, what, extra) -> {
                if (player == mp && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    if (poster != null) {
                        if (animateTransition) {
                            poster.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                                if (player == mp) {
                                    poster.setVisibility(GONE);
                                    poster.setAlpha(1f);
                                }
                            }).start();
                        } else {
                            poster.setVisibility(GONE);
                        }
                    }
                    Log.i(TAG, "First video frame rendered");
                }
                return false;
            });
            next.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) {
                    Log.w(TAG, "Playback failed: " + what + "/" + extra);
                    failed = true;
                    releasePlayer();
                }
                return true; // No system error dialog over Home.
            });
            next.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "Cannot prepare video; keeping the static background", e);
            failed = true;
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (poster != null) {
            poster.animate().cancel();
            poster.setAlpha(1f);
            poster.setVisibility(VISIBLE);
        }
        MediaPlayer previous = player;
        player = null;
        playingFile = null;
        if (previous == null) return;
        previous.setOnPreparedListener(null);
        previous.setOnInfoListener(null);
        previous.setOnErrorListener(null);
        previous.release();
        Log.i(TAG, "Decoder released");
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { startIfReady(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { releasePlayer(); }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // SurfaceView's constructor can call this before our fields have been initialized.
        if (sources == null) return;
        if (visibility == VISIBLE) startIfReady();
        else releasePlayer();
    }

    private File selectedFile() {
        Display display = getDisplay();
        Display.Mode mode = display == null ? null : display.getMode();
        return sources.select(mode == null ? 0 : mode.getPhysicalWidth(),
                mode == null ? 0 : mode.getPhysicalHeight());
    }

    @Override public void onDisplayChanged(int displayId) {
        Display display = getDisplay();
        if (!resumed || display == null || display.getDisplayId() != displayId) return;
        File selected = selectedFile();
        if (selected == null ? playingFile == null : selected.equals(playingFile)) return;
        releasePlayer();
        failed = false;
        startIfReady();
    }

    @Override public void onDisplayAdded(int displayId) { }
    @Override public void onDisplayRemoved(int displayId) { }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DisplayManager manager = getContext().getSystemService(DisplayManager.class);
        if (manager != null) manager.registerDisplayListener(this, new Handler(Looper.getMainLooper()));
    }

    @Override protected void onDetachedFromWindow() {
        DisplayManager manager = getContext().getSystemService(DisplayManager.class);
        if (manager != null) manager.unregisterDisplayListener(this);
        pausePlayback();
        super.onDetachedFromWindow();
    }
}
