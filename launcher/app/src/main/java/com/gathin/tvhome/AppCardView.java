package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/** One scale animation owns both focus and press feedback, including long presses. */
public final class AppCardView extends FrameLayout {
    private static final DecelerateInterpolator EASING = new DecelerateInterpolator(1.5f);
    private boolean motion, reducedMotion;
    private float focusScale = AppAdapter.FOCUS_SCALE;
    private Interpolator focusEasing = EASING;

    public AppCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void configureMotion(boolean enabled, boolean reduced, float scale, float overshoot) {
        motion = enabled;
        reducedMotion = reduced;
        focusScale = scale;
        focusEasing = overshoot > 0 && !reduced ? new OvershootInterpolator(overshoot) : EASING;
        // Reuse the card texture across focus changes instead of rebuilding it per zoom.
        setLayerType(enabled && !reduced ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
    }

    private float targetScale() {
        float focus = motion && isFocused() ? focusScale : 1f;
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
                    .setInterpolator(isFocused() && !isPressed() ? focusEasing : EASING)
                    .setUpdateListener(a -> invalidateShadow());
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
