package com.gathin.tvhome;

import android.graphics.Matrix;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;

import androidx.recyclerview.widget.RecyclerView;

/** Animates only the carried tile after layout, without owning the grid's row translations. */
final class ReorderMotion implements ViewTreeObserver.OnPreDrawListener {
    private final RecyclerView grid;
    private final Transformation transform = new Transformation();
    private final float[] matrix = new float[9];
    private final DecelerateInterpolator easing = new DecelerateInterpolator(1.5f);
    private View animated;
    private long pendingId = RecyclerView.NO_ID;
    private float fromX, fromY;

    ReorderMotion(RecyclerView grid) {
        this.grid = grid;
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(View view) { }
            @Override public void onChildViewDetachedFromWindow(View view) {
                if (view == animated) clearAnimation();
            }
        });
    }

    void capture(long id) {
        // Several key repeats can arrive before layout. Keep the last position actually drawn.
        if (pendingId == id) return;
        RecyclerView.ViewHolder holder = grid.findViewHolderForItemId(id);
        if (holder == null) {
            stop();
            return;
        }
        View view = holder.itemView;
        float x = view.getX(), y = view.getY();
        if (view == animated && view.getAnimation() != null) {
            transform.clear();
            view.getAnimation().getTransformation(AnimationUtils.currentAnimationTimeMillis(), transform);
            transform.getMatrix().getValues(matrix);
            x += matrix[Matrix.MTRANS_X];
            y += matrix[Matrix.MTRANS_Y];
        }
        stop();
        fromX = x;
        fromY = y;
        pendingId = id;
        grid.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override public boolean onPreDraw() {
        grid.getViewTreeObserver().removeOnPreDrawListener(this);
        RecyclerView.ViewHolder holder = grid.findViewHolderForItemId(pendingId);
        pendingId = RecyclerView.NO_ID;
        if (holder == null) return true;
        View view = holder.itemView;
        float dx = fromX - view.getX(), dy = fromY - view.getY();
        if (Math.abs(dx) < 1 && Math.abs(dy) < 1) return true;
        TranslateAnimation slide = new TranslateAnimation(dx, 0, dy, 0);
        slide.setDuration(180);
        slide.setInterpolator(easing);
        slide.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) { }
            @Override public void onAnimationRepeat(Animation animation) { }
            @Override public void onAnimationEnd(Animation animation) {
                if (view.getAnimation() == animation) clearAnimation();
            }
        });
        animated = view;
        view.setTranslationZ(1f);
        view.startAnimation(slide);
        return true;
    }

    void stop() {
        if (pendingId != RecyclerView.NO_ID) {
            grid.getViewTreeObserver().removeOnPreDrawListener(this);
            pendingId = RecyclerView.NO_ID;
        }
        clearAnimation();
    }

    private void clearAnimation() {
        if (animated == null) return;
        animated.clearAnimation();
        animated.setTranslationZ(0f);
        animated = null;
    }
}
