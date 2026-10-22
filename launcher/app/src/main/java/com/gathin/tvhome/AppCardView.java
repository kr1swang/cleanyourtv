package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** One scale animation owns both focus and press feedback, including long presses. */
public final class AppCardView extends FrameLayout {
    private static final DecelerateInterpolator EASING = new DecelerateInterpolator(1.5f);
    private boolean motion, reducedMotion;

    public AppCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void configureMotion(boolean enabled, boolean reduced) {
        motion = enabled;
        reducedMotion = reduced;
    }

    private float targetScale() {
        float focus = motion && isFocused() ? AppAdapter.FOCUS_SCALE : 1f;
        return motion && !reducedMotion && isPressed() ? focus * 0.94f : focus;
    }

    void updateScale(long duration) {
        animate().cancel();
        float scale = targetScale();
        if (!motion || duration == 0 || !isAttachedToWindow()) {
            setScaleX(scale);
            setScaleY(scale);
            invalidateShadow();
        } else {
            animate().scaleX(scale).scaleY(scale).setDuration(duration)
                    .setInterpolator(EASING)
                    .setUpdateListener(a -> invalidateShadow());
            // Upstream reduced motion skips the hardware layer for its short zoom.
            if (!reducedMotion) animate().withLayer();
            animate().start();
        }
    }

    private void invalidateShadow() {
        if (getParent() instanceof View) ((View) getParent()).invalidate();
    }

    @Override public void setPressed(boolean pressed) {
        boolean changed = pressed != isPressed();
        super.setPressed(pressed);
        if (changed) updateScale(reducedMotion ? 0 : pressed ? 70 : 110);
    }

    /** Clear a stale press without starting a release animation just to cancel it. */
    void resetFeedback() {
        super.setPressed(false);
        updateScale(0);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        resetFeedback();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) resetFeedback();
    }
}
