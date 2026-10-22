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
    private final List<Button> choices = new ArrayList<>();
    private TextView status;
    private ProgressBar progress;
    private Button cancel, clear;
    private WallpaperCatalog catalog;
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
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        body.addView(progress);
        cancel = button(body, getString(android.R.string.cancel));
        cancel.setVisibility(View.GONE);
        cancel.setOnClickListener(
                v -> {
                    cancelJob();
                    refresh();
                });
        try {
            catalog = WallpaperLibrary.catalog(this);
            String last = "";
            for (WallpaperCatalog.Variant variant : catalog.variants) {
                if (!last.equals(variant.background)) {
                    last = variant.background;
                    TextView heading = new TextView(this);
                    heading.setText(
                            getResources()
                                            .getConfiguration()
                                            .getLocales()
                                            .get(0)
                                            .getLanguage()
                                            .equals("zh")
                                    ? variant.titleZh
                                    : variant.title);
                    heading.setTextColor(0xFFFFFFFF);
                    heading.setTextSize(22);
                    heading.setPadding(0, dp(16), 0, dp(8));
                    body.addView(heading);
                }
                Button b = button(body, "");
                choices.add(b);
                b.setOnClickListener(v -> confirm(variant));
            }
            clear = button(body, getString(R.string.bg_clear));
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
        String active = WallpaperLibrary.currentLabel(this);
        if (active == null) {
            String choice = WallpaperLibrary.choice(this);
            active =
                    choice == null
                            ? getString(R.string.wallpaper_theme_default)
                            : "none".equals(choice) ? getString(R.string.wallpaper_none) : choice;
        }
        status.setText(getString(R.string.bg_current, active));
        for (int i = 0; i < choices.size(); i++) {
            WallpaperCatalog.Variant v = catalog.variants.get(i);
            long missing = 0;
            for (WallpaperCatalog.Asset a : v.assets())
                if (!a.present(WallpaperLibrary.directory(this))) missing += a.size;
            String suffix = missing == 0 ? getString(R.string.bg_downloaded) : mib(missing);
            choices.get(i).setText(WallpaperLibrary.label(this, v) + " · " + suffix);
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

    private void busy(boolean busy) {
        for (Button b : choices) b.setEnabled(!busy);
        if (clear != null) clear.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        cancel.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            progress.setProgress(0);
            cancel.requestFocus();
        }
    }

    private void download(WallpaperCatalog.Variant v) {
        busy(true);
        status.setText(R.string.bg_verifying);
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
                                                                    progress.setProgress(
                                                                            (int)
                                                                                    (done * 100
                                                                                            / total));
                                                                    status.setText(
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
                                            busy(false);
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
                                                busy(false);
                                                fail(e);
                                            }
                                        });
                            }
                        });
    }

    private void deleteUnused() {
        busy(true);
        status.setText(R.string.bg_cleaning);
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
                                                busy(false);
                                                refresh();
                                            }
                                        });
                            } catch (IOException e) {
                                ui.post(
                                        () -> {
                                            if (run == generation) {
                                                job = null;
                                                busy(false);
                                                fail(e);
                                            }
                                        });
                            }
                        });
    }

    private void fail(Exception e) {
        Log.w("WallpaperLibrary", "Wallpaper operation failed", e);
        status.setText(R.string.bg_failed);
    }

    private void cancelJob() {
        ++generation;
        if (job != null) {
            job.cancel(true);
            job = null;
        }
        busy(false);
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
