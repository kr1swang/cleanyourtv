package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visuals for control-center rows and tile actions. No blur or background capture. */
final class PanelStyle {
    static final float BACKDROP_DIM = 0.5f;
    static final int BACKDROP_ALPHA = Math.round(255 * BACKDROP_DIM);
    static final int SURFACE_ALPHA = Math.round(255 * 0.75f);
    private final Context context;

    PanelStyle(Context context) { this.context = context; }

    TextView text(int size, boolean secondary) {
        TextView t = new TextView(context);
        t.setTextSize(size);
        t.setTextColor(colors(secondary));
        t.setTypeface(context.getResources().getFont(R.font.outfit_medium));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    TextView addActionRow(LinearLayout rows, int icon, int title) {
        TextView row = text(16, false);
        row.setText(title);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(background());
        row.setOnFocusChangeListener((v, focused) -> v.setElevation(focused ? dp(6) : 0));
        if (icon != 0) {
            Drawable image = context.getDrawable(icon).mutate();
            image.setBounds(0, 0, dp(20), dp(20));
            row.setCompoundDrawablesRelative(image, null, null, null);
            row.setCompoundDrawablePadding(dp(16));
            row.setCompoundDrawableTintList(colors(false));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        if (rows.getChildCount() > 0) lp.topMargin = dp(8);
        rows.addView(row, lp);
        return row;
    }

    ColorStateList colors(boolean secondary) {
        return new ColorStateList(new int[][] {new int[] {android.R.attr.state_focused}, new int[0]},
                new int[] {secondary ? 0xFF444950 : 0xFF151920, secondary ? 0xFFBAC1CD : Color.WHITE});
    }

    StateListDrawable background() {
        return background(20);
    }

    StateListDrawable background(int radius) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_focused}, shape(0xFFF4F5F8, radius));
        d.addState(new int[0], shape(surfaceColor(0x323845), radius));
        return d;
    }

    GradientDrawable shape(int color) {
        return shape(color, 24);
    }

    static int surfaceColor(int rgb) { return (SURFACE_ALPHA << 24) | rgb; }

    /** Keep the expanded-grid shade plus the menu shade at 50%, rather than stacking to 75%. */
    static int gridShadeAlpha(int overlayAlpha) {
        return Math.round((BACKDROP_ALPHA - overlayAlpha) * 255f / (255 - overlayAlpha));
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {color, color});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x38FFFFFF);
        return d;
    }

    int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
}
