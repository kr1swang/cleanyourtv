package com.gathin.tvhome;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Optional, silent theme video. A SurfaceView lets the decoder compose behind the launcher UI. */
final class VideoWallpaper extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "VideoWallpaper";
    private final File file;
    private final View poster;
    private final boolean animateTransition;
    private MediaPlayer player;
    private boolean resumed;
    private boolean failed;

    static VideoWallpaper attach(Context context, FrameLayout root, View poster, boolean animateTransition) {
        File dir = Config.dir(context);
        if (dir == null) return null;
        try {
            File config = new File(dir, Config.FILE_NAME);
            if (!config.isFile()) return null;
            JSONObject json = new JSONObject(new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
            String name = json.optString("video_wallpaper", "");
            if (name.isEmpty()) return null;
            File file = new File(dir, name).getCanonicalFile();
            if (!dir.getCanonicalFile().equals(file.getParentFile()) || !file.isFile()) return null;
            VideoWallpaper view = new VideoWallpaper(context, file, poster, animateTransition);
            root.addView(view, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return view;
        } catch (Exception e) {
            Log.w(TAG, "Cannot read theme video; keeping the static background", e);
            return null;
        }
    }

    private VideoWallpaper(Context context, File file, View poster, boolean animateTransition) {
        super(context);
        this.file = file;
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
        MediaPlayer next = new MediaPlayer();
        player = next;
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
                        + " from " + mp.getCurrentPosition() + "ms");
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
        if (file == null) return;
        if (visibility == VISIBLE) startIfReady();
        else releasePlayer();
    }

    @Override protected void onDetachedFromWindow() {
        pausePlayback();
        super.onDetachedFromWindow();
    }
}
