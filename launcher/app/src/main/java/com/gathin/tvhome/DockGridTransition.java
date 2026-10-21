package com.gathin.tvhome;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Rect;

import androidx.recyclerview.widget.RecyclerView;

/** Moves the existing grid between the wallpaper shelf and the full app view. */
final class DockGridTransition {
    private final RecyclerView grid;
    private final View clock;
    private final int columns;
    private final float density;
    private final boolean animate;
    private final DecelerateInterpolator easing = new DecelerateInterpolator(1.5f);
    private int rowHeight;
    private int dockHeight;
    private boolean expanded;
    private int scrollOffset;
    private int cardCenter;
    private final int dockGap;
    private final int rowGap;
    private float rowOffset;
    private ValueAnimator spacing;
    private final View backdropShade;

    DockGridTransition(RecyclerView grid, View clock, int columns, float density, boolean animate,
                       View backdropShade) {
        this.grid = grid;
        this.clock = clock;
        this.columns = columns;
        this.density = density;
        this.animate = animate;
        this.backdropShade = backdropShade;
        dockGap = Math.round(15 * density);
        rowGap = Math.round(12 * density);
        rowOffset = -dockGap;
        grid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(Rect out, View view, RecyclerView parent,
                                                RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos >= 0 && view.findViewById(R.id.card) != null) {
                    out.bottom = pos < columns ? dockGap : rowGap;
                }
            }
        });
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(View view) { offsetRow(view); }
            @Override public void onChildViewDetachedFromWindow(View view) { }
        });
    }

    boolean hasRowGeometry() {
        return rowHeight > 0;
    }

    int bottomPadding(int height, int itemHeight, int appCount) {
        rowHeight = itemHeight + rowGap;
        int cardHeight = itemHeight - Math.round(AppAdapter.ITEM_EXTRA_DP * density);
        cardCenter = Math.round(8 * density + cardHeight / 2f);
        dockHeight = itemHeight - Math.round(16 * density)
                + Math.round(AppAdapter.DOCK_EXTRA_DP * density) + dockGap;
        // Reordering or reloading can reuse an attached view on the other side of the dock boundary.
        setRowOffset(rowOffset);
        return bottomPadding(height, expandedTop(), dockHeight, rowHeight, cardCenter,
                appCount, columns);
    }

    // Give the last card the same travel as every preceding card, even in a partial row.
    static int bottomPadding(int height, int top, int dockHeight, int rowHeight,
                             int cardCenter, int count, int columns) {
        int rows = (count + columns - 1) / columns;
        if (rows <= 1) return 0;
        int content = dockHeight + (rows - 1) * rowHeight;
        int lastCenter = top + content - rowHeight + cardCenter;
        int travel = Math.max(0, lastCenter - focusBottom(height));
        return Math.max(0, height - top - content + travel);
    }

    static int focusBottom(int height) {
        // The reference leaves a glimpse of the next row below the selected card and its name.
        return Math.round(height * 0.765f);
    }

    // Only scroll the distance needed to keep the card inside the focus band. Use the previous
    // target, not an in-flight view position, so horizontal moves and reversals remain stable.
    static int scrollOffset(int previous, int center, int height) {
        int bottom = focusBottom(height);
        int minimum = Math.max(0, center - bottom);
        int maximum = Math.max(0, center - (height - bottom));
        return Math.max(minimum, Math.min(previous, maximum));
    }

    static int dockTop(int itemTop, int row, int dockHeight, int rowHeight) {
        return row == 0 ? itemTop : itemTop - dockHeight - (row - 1) * rowHeight;
    }

    /** Own every app row so RecyclerView cannot apply a different bottom inset to deeper rows. */
    void onFocused(int position, View item) {
        int row = position / columns;
        setExpanded(row > 0);
        int center = expandedTop() + dockHeight + (row - 1) * rowHeight + cardCenter;
        scrollOffset = expanded ? scrollOffset(scrollOffset, center, grid.getHeight()) : 0;
        RecyclerView.LayoutManager layout = grid.getLayoutManager();
        int top = dockTop(layout.getDecoratedTop(item), row, dockHeight, rowHeight);
        int target = expanded ? expandedTop() - scrollOffset : grid.getPaddingTop();
        int dy = top - target;
        if (Math.abs(dy) <= 1) return;
        grid.stopScroll();
        if (animate) grid.smoothScrollBy(0, dy, easing, 360);
        else grid.scrollBy(0, dy);
    }

    void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        clock.animate().cancel();
        if (spacing != null) spacing.cancel();
        float alpha = expanded ? 0f : 1f;
        if (animate) clock.animate().alpha(alpha).setDuration(220).start();
        else clock.setAlpha(alpha);
        float target = expanded ? 0f : -dockGap;
        if (!animate) {
            setRowOffset(target);
        } else {
            spacing = ValueAnimator.ofFloat(rowOffset, target);
            spacing.setDuration(360);
            spacing.setInterpolator(easing);
            spacing.addUpdateListener(a -> setRowOffset((float) a.getAnimatedValue()));
            spacing.start();
        }
    }

    void stop() {
        grid.stopScroll();
        clock.animate().cancel();
        clock.setAlpha(expanded ? 0f : 1f);
        if (spacing != null) spacing.cancel();
        setRowOffset(expanded ? 0f : -dockGap);
    }

    private void setRowOffset(float offset) {
        rowOffset = offset;
        backdropShade.setAlpha(Math.max(0f, Math.min(1f, 1f + offset / dockGap)));
        for (int i = 0; i < grid.getChildCount(); i++) offsetRow(grid.getChildAt(i));
    }

    private void offsetRow(View item) {
        int pos = grid.getChildAdapterPosition(item);
        item.setTranslationY(pos >= columns ? rowOffset : 0f);
    }

    private int expandedTop() {
        // Card starts at 51dp; its band extends 20dp above it, matching the reference's top margin.
        return Math.round(37 * density);
    }
}
