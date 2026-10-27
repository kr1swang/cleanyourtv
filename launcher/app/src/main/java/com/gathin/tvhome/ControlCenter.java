package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Home-owned overlay. No activity transition, blur, bitmap capture or idle work. */
final class ControlCenter {
    private static final long TRANSITION_MS = 180;
    final Activity activity;
    private final PanelStyle style;
    final LinearLayout rows;
    private final Dialog dialog;
    private final LinearLayout body;
    private final ScrollView scroll;
    private final boolean reducedMotion;
    private Runnable back;
    private Runnable leavePage;
    private AlertDialog prompt;
    private boolean restoreFocus = true;
    private boolean closing;

    ControlCenter(Activity activity, boolean reducedMotion, Runnable onClosed) {
        this.activity = activity;
        style = new PanelStyle(activity);
        this.reducedMotion = reducedMotion;
        dialog = new Dialog(activity) {
            @Override public void cancel() { ControlCenter.this.close(true); }
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll = new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        scroll.addView(rows);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(body);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((d, code, event) -> {
            if (closing) return true;
            if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (back != null) back.run(); else close(true);
            }
            return true;
        });
        dialog.setOnDismissListener(d -> {
            clearPage();
            body.animate().cancel();
            closing = false;
            if (restoreFocus) onClosed.run();
        });
    }

    void show() {
        dialog.show();
        Window w = dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams p = w.getAttributes();
        p.dimAmount = 0f;
        p.gravity = Gravity.TOP | Gravity.END;
        // 4dp window margin + 12dp body + 4dp scroll inset aligns rows with the Dock's edge.
        p.x = dp(4);
        // 8dp window margin + 8dp body + 4dp scroll inset matches the 20dp right gap.
        p.y = dp(8);
        // All pages share 288dp visible rows plus 16dp of padding on each side.
        p.width = Math.min(dp(320), activity.getResources().getDisplayMetrics().widthPixels - dp(48));
        p.height = activity.getResources().getDisplayMetrics().heightPixels - dp(48);
        p.windowAnimations = 0;
        w.setAttributes(p);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    boolean isShowing() { return dialog.isShowing(); }

    void page(Runnable onBack) {
        boolean replacing = rows.getChildCount() > 0;
        clearPage();
        back = onBack;
        rows.removeAllViews();
        scroll.scrollTo(0, 0);
        // Move the viewport with its content. Translating rows alone clips the reverse
        // transition against the ScrollView's 4dp inset; the body has 16dp of room.
        body.setAlpha(0);
        body.setTranslationX(reducedMotion ? 0 : dp(replacing && onBack == null ? -12 : 12));
        animatePanel(1, 0, PanelStyle.BACKDROP_DIM, null);
    }

    /** One animation clock drives the panel and system-composited backdrop together. */
    private void animatePanel(float alpha, float translation, float dim, Runnable end) {
        Window window = dialog.getWindow();
        float fromDim = window.getAttributes().dimAmount;
        body.animate().alpha(alpha).translationX(translation)
                .setDuration(reducedMotion ? 60 : TRANSITION_MS)
                .setUpdateListener(fromDim == dim ? null : animation ->
                        window.setDimAmount(fromDim + (dim - fromDim) * animation.getAnimatedFraction()))
                .withEndAction(end).start();
    }

    void onLeave(Runnable action) { leavePage = action; }

    private void clearPage() {
        body.animate().cancel();
        if (prompt != null) { prompt.dismiss(); prompt = null; }
        Runnable cleanup = leavePage;
        leavePage = null;
        if (cleanup != null) cleanup.run();
    }

    void close(boolean restore) {
        if (!dialog.isShowing()) return;
        restoreFocus = restore;
        if (!restore) {
            body.animate().cancel();
            dialog.dismiss();
        } else if (!closing) {
            closing = true;
            body.animate().cancel();
            animatePanel(0, reducedMotion ? 0 : dp(12), 0, dialog::dismiss);
        }
    }

    void focus(int index) {
        int count = rows.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = rows.getChildAt((Math.max(0, index) + i) % count);
            if (row.getVisibility() == View.VISIBLE && row.isFocusable()) {
                row.requestFocus();
                return;
            }
        }
    }

    View row(int icon, String label, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(style.background());
        row.setOnFocusChangeListener((v, focused) -> v.setElevation(focused ? dp(6) : 0));
        TextView mark = style.text(18, false);
        mark.setId(android.R.id.icon);
        mark.setGravity(Gravity.CENTER);
        mark.setDuplicateParentStateEnabled(true);
        row.addView(mark, new LinearLayout.LayoutParams(dp(26), -2));
        setRowIcon(row, icon);
        TextView name = style.text(16, false);
        name.setId(android.R.id.text1);
        name.setText(label);
        name.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams n = new LinearLayout.LayoutParams(0, -2, 1);
        n.setMarginStart(dp(10));
        n.setMarginEnd(dp(8));
        row.addView(name, n);
        TextView status = style.text(12, true);
        status.setId(android.R.id.text2);
        status.setText(detail);
        status.setDuplicateParentStateEnabled(true);
        row.addView(status);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.bottomMargin = dp(12);
        rows.addView(row, lp);
        if (action != null) row.setOnClickListener(v -> { if (!closing) action.run(); });
        row.setOnKeyListener((v, key, event) -> {
            // Keep navigation within this list; do not let focus escape to Home.
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            int at = rows.indexOfChild(v);
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                int step = key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                for (int i = at + step; i >= 0 && i < rows.getChildCount(); i += step) {
                    View next = rows.getChildAt(i);
                    if (next.getVisibility() == View.VISIBLE && next.isFocusable()) return false;
                }
                return true;
            }
            return false;
        });
        return row;
    }

    void setRowIcon(View row, int icon) {
        TextView mark = row.findViewById(android.R.id.icon);
        android.graphics.drawable.Drawable image = icon == 0 ? null : activity.getDrawable(icon).mutate();
        if (image != null) image.setBounds(0, 0, dp(20), dp(20));
        mark.setCompoundDrawables(image, null, null, null);
        mark.setCompoundDrawableTintList(style.colors(false));
    }

    void confirm(int heading, String message, int accept, Runnable action) {
        prompt = new AlertDialog.Builder(activity).setTitle(heading).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(accept, (d, which) -> action.run()).create();
        style(prompt);
        prompt.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    void style(AlertDialog alert) {
        alert.show();
        alert.getWindow().setBackgroundDrawable(style.shape(PanelStyle.surfaceColor(0x2A303C)));
        alert.getWindow().setDimAmount(PanelStyle.BACKDROP_DIM);
        // Match the panel typography without adding another dialog framework.
        type(alert.getWindow().getDecorView());
        for (int which : new int[] {AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = alert.getButton(which);
            if (button == null || button.getVisibility() != View.VISIBLE) continue;
            // Nested corners: 24dp dialog radius minus the 12dp outer inset.
            button.setBackground(style.background(12));
            View buttonBar = (View) button.getParent();
            buttonBar.setPadding(dp(12), dp(8), dp(12), dp(12));
            button.setTextColor(style.colors(false));
            button.setAllCaps(false);
            button.setPadding(dp(18), 0, dp(18), 0);
            button.setMinWidth(dp(100));
            android.view.ViewGroup.LayoutParams params = button.getLayoutParams();
            params.height = dp(44);
            if (params instanceof android.view.ViewGroup.MarginLayoutParams) {
                ((android.view.ViewGroup.MarginLayoutParams) params).setMarginStart(dp(8));
            }
            button.setLayoutParams(params);
        }
    }

    private void type(View v) {
        if (v instanceof TextView) ((TextView) v).setTypeface(activity.getResources().getFont(R.font.outfit_medium));
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) type(group.getChildAt(i));
        }
    }

    int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
}
