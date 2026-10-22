package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

/** Remote-only picker. Files are verified on a worker before the selected preference changes. */
public final class WallpaperSettingsActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<RadioButton> choices = new ArrayList<>();
    private TextView status;
    private ProgressBar progress;
    private AlertDialog progressDialog;
    private TextView progressMessage;
    private WallpaperCatalog catalog;
    private final List<WallpaperCatalog.Variant> variants = new ArrayList<>();
    private Future<?> job;
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101419);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(40);
        body.setPadding(dp(64), pad, dp(64), pad);
        scroll.addView(body);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText(R.string.bg_library);
        title.setTextSize(30);
        title.setTextColor(0xFFFFFFFF);
        body.addView(title);
        status = new TextView(this);
        status.setTextSize(17);
        status.setTextColor(0xFFCCCCCC);
        status.setPadding(0, dp(12), 0, dp(12));
        body.addView(status);
        try {
            catalog = WallpaperLibrary.catalog(this);
            variants.addAll(catalog.variants);
            variants.sort(Comparator.comparing((WallpaperCatalog.Variant v) -> v.background)
                    .thenComparingInt(v -> v.width).thenComparingInt(v -> v.fps));
            boolean multipleBackgrounds = catalog.variants.stream()
                    .map(v -> v.background).distinct().count() > 1;
            String last = "";
            int lastWidth = 0;
            LinearLayout row = null;
            for (WallpaperCatalog.Variant variant : variants) {
                boolean newBackground = !last.equals(variant.background);
                if (multipleBackgrounds && newBackground) {
                    TextView heading = new TextView(this);
                    heading.setText(variant.title);
                    heading.setTextColor(0xFFFFFFFF);
                    heading.setTextSize(22);
                    heading.setPadding(0, dp(16), 0, dp(8));
                    body.addView(heading);
                }
                if (row == null || newBackground || lastWidth != variant.width) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    body.addView(row);
                }
                last = variant.background;
                lastWidth = variant.width;
                RadioButton b = new RadioButton(this) {
                    // Selection follows the applied file, not an unconfirmed click or download.
                    @Override public void toggle() {}
                };
                b.setButtonDrawable(new android.graphics.drawable.InsetDrawable(
                        b.getButtonDrawable(), dp(12), 0, 0, 0));
                b.setTextSize(18);
                b.setTextColor(0xFFFFFFFF);
                b.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFB3E1C5));
                b.setPadding(dp(12), 0, dp(12), 0);
                b.setCompoundDrawablePadding(dp(10));
                b.setBackgroundResource(R.drawable.background_radio_focus);
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(76), 1f);
                cell.setMargins(0, dp(6), dp(8), dp(6));
                row.addView(b, cell);
                choices.add(b);
                b.setOnClickListener(v -> {
                    if (!b.isChecked()) confirm(variant);
                });
            }
            Button clear = button(body, getString(R.string.bg_clear));
            clear.setOnClickListener(
                    v -> {
                        try {
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.bg_clear)
                                    .setMessage(
                                            getString(
                                                    R.string.bg_clear_confirm,
                                                    mib(WallpaperLibrary.unusedBytes(this))))
                                    .setPositiveButton(
                                            android.R.string.ok, (d, w) -> deleteUnused())
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        } catch (IOException e) {
                            fail(e);
                        }
                    });
            refresh();
            if (!choices.isEmpty()) choices.get(0).requestFocus();
        } catch (IOException e) {
            fail(e);
        }
    }

    private Button button(LinearLayout body, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundResource(R.drawable.background_radio_focus);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(5);
        body.addView(b, lp);
        return b;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private String mib(long n) {
        return String.format(Locale.getDefault(), "%.1f MB", n / 1048576.0);
    }

    private void refresh() {
        if (catalog == null) return;
        WallpaperCatalog.Variant selected = activeVariant();
        String active = selected == null ? null : WallpaperLibrary.label(this, selected);
        if (active == null) {
            String choice = WallpaperLibrary.choice(this);
            active =
                    choice == null
                            ? getString(R.string.wallpaper_theme_default)
                            : "none".equals(choice) ? getString(R.string.wallpaper_none) : choice;
        }
        status.setText(getString(R.string.bg_current, active));
        for (int i = 0; i < choices.size(); i++) {
            WallpaperCatalog.Variant v = variants.get(i);
            long size = 0;
            for (WallpaperCatalog.Asset a : v.assets()) size += a.size;
            String suffix = mib(size) + (v.present(WallpaperLibrary.directory(this))
                    ? " · " + getString(R.string.bg_downloaded) : "");
            choices.get(i).setText(WallpaperLibrary.formatLabel(this, v) + "\n" + suffix);
            choices.get(i).setChecked(selected != null && selected.id.equals(v.id));
        }
    }

    private WallpaperCatalog.Variant activeVariant() {
        try {
            return WallpaperLibrary.choice(this) == null
                    ? WallpaperLibrary.defaultVariant(this) : WallpaperLibrary.selected(this);
        } catch (IOException ignored) {
            return null; // Catalog errors are reported when opening the page.
        }
    }

    private void confirm(WallpaperCatalog.Variant v) {
        new AlertDialog.Builder(this)
                .setTitle(WallpaperLibrary.label(this, v))
                .setMessage(R.string.bg_confirm)
                .setPositiveButton(R.string.bg_apply, (d, w) -> download(v))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showProgress(String title, int message, boolean cancellable) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        progressMessage = new TextView(this);
        progressMessage.setText(message);
        progressMessage.setTextSize(18);
        content.addView(progressMessage);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(12));
        bar.topMargin = dp(16);
        content.addView(progress, bar);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(title).setView(content).setCancelable(cancellable);
        if (cancellable) dialog.setNegativeButton(android.R.string.cancel, (d, w) -> {
            cancelJob();
            refresh();
        });
        progressDialog = dialog.create();
        progressDialog.setOnCancelListener(d -> {
            cancelJob();
            refresh();
        });
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
        progressDialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (cancellable) progressDialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    private void dismissProgress() {
        if (progressDialog != null) progressDialog.dismiss();
        progressDialog = null;
        progress = null;
        progressMessage = null;
    }

    private void download(WallpaperCatalog.Variant v) {
        showProgress(WallpaperLibrary.label(this, v), R.string.bg_verifying, true);
        final int run = ++generation;
        job =
                WallpaperLibrary.WORKER.submit(
                        () -> {
                            try {
                                new WallpaperFetch()
                                        .fetch(
                                                v,
                                                WallpaperLibrary.directory(this),
                                                () -> Thread.currentThread().isInterrupted(),
                                                (done, total) ->
                                                        ui.post(
                                                                () -> {
                                                                    if (run != generation) return;
                                                                    progress.setIndeterminate(false);
                                                                    progress.setProgress(
                                                                            (int)
                                                                                    (done * 100
                                                                                            / total));
                                                                    progressMessage.setText(
                                                                            getString(
                                                                                    R.string
                                                                                            .bg_progress,
                                                                                    mib(done),
                                                                                    mib(total)));
                                                                }));
                                ui.post(
                                        () -> {
                                            if (run != generation) return;
                                            job = null;
                                            dismissProgress();
                                            try {
                                                WallpaperLibrary.apply(this, v);
                                                setResult(RESULT_OK);
                                                refresh();
                                                Toast.makeText(
                                                                this,
                                                                R.string.wallpaper_applied,
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                            } catch (IOException e) {
                                                fail(e);
                                            }
                                        });
                            } catch (IOException e) {
                                ui.post(
                                        () -> {
                                            if (run == generation) {
                                                job = null;
                                                fail(e);
                                            }
                                        });
                            }
                        });
    }

    private void deleteUnused() {
        showProgress(getString(R.string.bg_clear), R.string.bg_cleaning, false);
        final int run = ++generation;
        job =
                WallpaperLibrary.WORKER.submit(
                        () -> {
                            try {
                                WallpaperLibrary.deleteUnused(this);
                                ui.post(
                                        () -> {
                                            if (run == generation) {
                                                job = null;
                                                dismissProgress();
                                                refresh();
                                            }
                                        });
                            } catch (IOException e) {
                                ui.post(
                                        () -> {
                                            if (run == generation) {
                                                job = null;
                                                fail(e);
                                            }
                                        });
                            }
                        });
    }

    private void fail(Exception e) {
        Log.w("WallpaperLibrary", "Wallpaper operation failed", e);
        dismissProgress();
        refresh();
        new AlertDialog.Builder(this).setMessage(R.string.bg_failed)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void cancelJob() {
        ++generation;
        if (job != null) {
            job.cancel(true);
            job = null;
        }
        dismissProgress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (job == null) refresh();
    }

    @Override
    protected void onStop() {
        cancelJob(); // Leaving the picker cancels; the currently selected background stays
                     // untouched.
        super.onStop();
    }
}
