package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Dims Home and redraws only the selected tile, including its existing name and cached shadow. */
final class TileMenuBackdrop extends Drawable {
    private final View root, gridShade;
    private View tile;
    private final Paint dim = new Paint();
    private final int[] rootAt = new int[2], tileAt = new int[2];
    private final long duration;
    private ValueAnimator animation;

    TileMenuBackdrop(View root, View gridShade, boolean reducedMotion) {
        this.root = root;
        this.gridShade = gridShade;
        dim.setColor(0x00000000);
        duration = reducedMotion ? 60 : 180;
    }

    // Reuse one overlay, reversing an unfinished dismissal from its current opacity.
    void show(View tile) {
        this.tile = tile;
        setBounds(0, 0, root.getWidth(), root.getHeight());
        root.getOverlay().add(this);
        fadeTo(PanelStyle.BACKDROP_ALPHA, false);
    }

    void dismiss() { fadeTo(0, true); }

    private void fadeTo(int alpha, boolean remove) {
        if (animation != null) {
            animation.removeAllListeners();
            animation.cancel();
        }
        animation = ValueAnimator.ofInt(dim.getAlpha(), alpha);
        animation.setDuration(duration);
        animation.addUpdateListener(a -> setAlpha((int) a.getAnimatedValue()));
        if (remove) animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                root.getOverlay().remove(TileMenuBackdrop.this);
                tile = null;
            }
        });
        animation.start();
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), dim);
        if (tile == null || !tile.isAttachedToWindow()) return;
        root.getLocationInWindow(rootAt);
        tile.getLocationInWindow(tileAt);
        int save = canvas.save();
        canvas.translate(tileAt[0] - rootAt[0], tileAt[1] - rootAt[1]);
        // Draw the existing view directly. No screenshot, bitmap copy or full-screen blur.
        tile.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        dim.setAlpha(alpha);
        if (gridShade != null) gridShade.getBackground().setAlpha(PanelStyle.gridShadeAlpha(alpha));
        invalidateSelf();
    }
    @Override public int getAlpha() { return dim.getAlpha(); }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
