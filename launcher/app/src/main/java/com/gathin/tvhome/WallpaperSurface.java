package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

/** A retained background buffer: tile animations never redraw the full-screen wallpaper. */
final class WallpaperSurface extends SurfaceView implements SurfaceHolder.Callback2 {
    private final ImageView fallback;
    private final int backgroundColor;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;

    WallpaperSurface(ImageView fallback, Bitmap bitmap, int backgroundColor) {
        super(fallback.getContext());
        this.fallback = fallback;
        this.bitmap = bitmap;
        this.backgroundColor = backgroundColor;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        getHolder().addCallback(this);
    }

    void show(Bitmap next) {
        bitmap = next;
        drawWallpaper();
    }

    void releaseBitmap() { bitmap = null; }

    private void drawWallpaper() {
        if (bitmap == null || !getHolder().getSurface().isValid()) return;
        try {
            Canvas canvas = getHolder().lockHardwareCanvas();
            if (canvas == null) return;
            try {
                canvas.drawColor(backgroundColor);
                float scale = Math.max((float) canvas.getWidth() / bitmap.getWidth(),
                        (float) canvas.getHeight() / bitmap.getHeight());
                float width = bitmap.getWidth() * scale;
                float height = bitmap.getHeight() * scale;
                float left = (canvas.getWidth() - width) / 2;
                float top = (canvas.getHeight() - height) / 2;
                canvas.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), paint);
            } finally {
                getHolder().unlockCanvasAndPost(canvas);
            }
            fallback.setVisibility(View.INVISIBLE);
        } catch (RuntimeException failure) {
            // Surface allocation/driver failures keep the existing ImageView path usable.
            fallback.setVisibility(View.VISIBLE);
            Log.w("WallpaperSurface", "Keeping wallpaper fallback", failure);
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        drawWallpaper();
    }

    @Override public void surfaceRedrawNeeded(SurfaceHolder holder) {
        drawWallpaper();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        fallback.setVisibility(View.VISIBLE);
    }
}
