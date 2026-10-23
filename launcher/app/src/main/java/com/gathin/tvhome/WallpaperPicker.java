package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

/** Single-line background choices inside the Home overlay; downloads remain on the existing worker. */
final class WallpaperPicker {
    private final Activity activity;
    private final ControlCenter panel;
    private final Runnable onApplied;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<View> choices = new ArrayList<>();
    private final List<WallpaperCatalog.Variant> variants = new ArrayList<>();
    private ProgressBar progress;
    private AlertDialog progressDialog;
    private TextView progressMessage;
    private Future<?> job;
    private int generation;

    WallpaperPicker(ControlCenter panel, Runnable onApplied) {
        this.onApplied = onApplied;
        this.panel = panel;
        this.activity = panel.activity;
        panel.onLeave(this::cancelJob);
        try {
            variants.addAll(WallpaperLibrary.catalog(activity).variants);
            variants.sort(Comparator.comparing((WallpaperCatalog.Variant v) -> v.background)
                    .thenComparingInt(v -> v.width).thenComparingInt(v -> v.fps));
            boolean multiple = variants.stream().map(v -> v.background).distinct().count() > 1;
            for (WallpaperCatalog.Variant variant : variants) {
                String name = multiple ? WallpaperLibrary.label(variant) : WallpaperLibrary.formatLabel(variant);
                choices.add(panel.row(0, name, "", () -> {
                    WallpaperCatalog.Variant active = activeVariant();
                    if (active == null || !active.id.equals(variant.id)) download(variant);
                }));
            }
            panel.row(R.drawable.ic_brush_cleaning, getString(R.string.bg_clear), "", () -> {
                try {
                    panel.confirm(R.string.bg_clear,
                            getString(R.string.bg_clear_confirm, mib(WallpaperLibrary.unusedBytes(activity))),
                            android.R.string.ok, this::deleteUnused);
                } catch (IOException e) { fail(e); }
            });
            refresh();
            panel.focus(0);
        } catch (IOException e) { fail(e); }
    }

    private int dp(int n) { return panel.dp(n); }
    private String getString(int id, Object... args) { return activity.getString(id, args); }
    private String mib(long n) { return String.format(Locale.getDefault(), "%.1f MB", n / 1048576.0); }

    private void refresh() {
        WallpaperCatalog.Variant selected = activeVariant();
        for (int i = 0; i < choices.size(); i++) {
            WallpaperCatalog.Variant v = variants.get(i);
            View row = choices.get(i);
            panel.setRowIcon(row, selected != null && selected.id.equals(v.id)
                    ? R.drawable.ic_control_check : 0);
            String size = mib(v.bytes());
            ((TextView) row.findViewById(android.R.id.text2)).setText(v.present(WallpaperLibrary.directory(activity))
                    ? getString(R.string.bg_file_status, size, getString(R.string.bg_downloaded)) : size);
            row.setSelected(selected != null && selected.id.equals(v.id));
        }
    }

    private WallpaperCatalog.Variant activeVariant() {
        try {
            WallpaperCatalog.Variant v = WallpaperLibrary.choice(activity) == null
                    ? WallpaperLibrary.defaultVariant(activity) : WallpaperLibrary.selected(activity);
            return v != null && v.present(WallpaperLibrary.directory(activity)) ? v : null;
        } catch (IOException ignored) { return null; }
    }

    private void showProgress(String title, int message, boolean cancellable) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        progressMessage = new TextView(activity);
        progressMessage.setText(message);
        progressMessage.setTextSize(18);
        content.addView(progressMessage);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(12));
        bar.topMargin = dp(16);
        content.addView(progress, bar);
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
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
        panel.style(progressDialog);
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
        showProgress(WallpaperLibrary.label(v), R.string.bg_verifying, true);
        final int run = ++generation;
        job =
                WallpaperLibrary.WORKER.submit(
                        () -> {
                            try {
                                new WallpaperFetch()
                                        .fetch(
                                                v,
                                                WallpaperLibrary.directory(activity),
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
                                                WallpaperLibrary.apply(activity, v);
                                                refresh();
                                                onApplied.run();
                                                Toast.makeText(
                                                                activity,
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
                                WallpaperLibrary.deleteUnused(activity);
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
        panel.confirm(R.string.bg_library, getString(R.string.bg_failed), android.R.string.ok, () -> {});
    }

    private void cancelJob() {
        ++generation;
        if (job != null) {
            job.cancel(true);
            job = null;
        }
        dismissProgress();
    }

}
