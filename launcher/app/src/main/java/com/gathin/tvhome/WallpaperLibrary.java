package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Android storage/selection adapter; downloaded files never overwrite the external theme. */
final class WallpaperLibrary {
    static final String PREFIX = "download:";
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static WallpaperCatalog catalog;
    private static long retryAfter;

    private WallpaperLibrary() {}

    static synchronized WallpaperCatalog catalog(Context ctx) throws IOException {
        if (catalog == null)
            try (InputStream in = ctx.getAssets().open("wallpaper-catalog.properties")) {
                catalog = new WallpaperCatalog(in);
            }
        return catalog;
    }

    static File directory(Context ctx) {
        return new File(ctx.getFilesDir(), "wallpaper-library");
    }

    static SharedPreferences preferences(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static String choice(Context ctx) {
        return preferences(ctx).getString(Config.WALLPAPER_CHOICE_KEY, null);
    }

    static boolean isSelected(Context ctx) {
        String c = choice(ctx);
        return c != null && c.startsWith(PREFIX);
    }

    static WallpaperCatalog.Variant selected(Context ctx) throws IOException {
        String c = choice(ctx);
        return c != null && c.startsWith(PREFIX)
                ? catalog(ctx).find(c.substring(PREFIX.length()))
                : null;
    }

    static WallpaperCatalog.Variant defaultVariant(Context ctx) throws IOException {
        WindowManager wm = ctx.getSystemService(WindowManager.class);
        Display.Mode mode = wm == null ? null : wm.getDefaultDisplay().getMode();
        return catalog(ctx)
                .defaultImage(
                        mode == null ? 3840 : mode.getPhysicalWidth(),
                        mode == null ? 2160 : mode.getPhysicalHeight());
    }

    static File poster(Context ctx) {
        try {
            WallpaperCatalog.Variant v = isSelected(ctx) ? selected(ctx) : defaultVariant(ctx);
            return v != null && v.image.present(directory(ctx))
                    ? v.image.file(directory(ctx))
                    : null;
        } catch (IOException e) {
            return null;
        }
    }

    static File video(Context ctx) {
        try {
            WallpaperCatalog.Variant v = selected(ctx);
            return v != null && v.video != null && v.present(directory(ctx))
                    ? v.video.file(directory(ctx))
                    : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void apply(Context ctx, WallpaperCatalog.Variant v) throws IOException {
        if (!v.present(directory(ctx))) throw new IOException("Download incomplete");
        if (!preferences(ctx).edit().putString(Config.WALLPAPER_CHOICE_KEY, PREFIX + v.id).commit())
            throw new IOException("Cannot save wallpaper selection");
    }

    static String label(Context ctx, WallpaperCatalog.Variant v) {
        return v.title + " · " + formatLabel(ctx, v);
    }

    static String formatLabel(Context ctx, WallpaperCatalog.Variant v) {
        return (v.width >= 3840 ? "4K" : "FHD")
                + " "
                + (v.fps == 0 ? "Static" : v.fps + "fps");
    }

    static String currentLabel(Context ctx) {
        try {
            WallpaperCatalog.Variant v = selected(ctx);
            return v == null ? null : label(ctx, v);
        } catch (IOException e) {
            return null;
        }
    }

    static long unusedBytes(Context ctx) throws IOException {
        Set<String> keep = activeFiles(ctx);
        long size = 0;
        File[] files = directory(ctx).listFiles();
        if (files != null)
            for (File f : files) if (f.isFile() && !keep.contains(f.getName())) size += f.length();
        return size;
    }

    static void deleteUnused(Context ctx) throws IOException {
        Set<String> keep = activeFiles(ctx);
        File[] files = directory(ctx).listFiles();
        if (files != null)
            for (File f : files)
                if (f.isFile() && !keep.contains(f.getName()) && !f.delete())
                    throw new IOException("Cannot delete " + f.getName());
    }

    private static Set<String> activeFiles(Context ctx) throws IOException {
        WallpaperCatalog.Variant v = selected(ctx);
        // The default poster may still be used with legacy external video files.
        if (v == null && choice(ctx) == null) v = defaultVariant(ctx);
        Set<String> keep = new HashSet<>();
        if (v != null)
            for (WallpaperCatalog.Asset a : v.assets()) keep.add(a.file(directory(ctx)).getName());
        return keep;
    }

    /**
     * First-use poster, or recovery after downloaded files were removed. Never downloads on every
     * Home press.
     */
    static synchronized Future<?> prefetch(Context owner, boolean missingDefault, Runnable ready) {
        if (SystemClock.elapsedRealtime() < retryAfter) return null;
        final Context ctx = owner.getApplicationContext();
        final String before = choice(ctx);
        final WallpaperCatalog.Variant v;
        try {
            v =
                    isSelected(ctx)
                            ? selected(ctx)
                            : before == null && missingDefault ? defaultVariant(ctx) : null;
            if (v == null || v.present(directory(ctx))) return null;
        } catch (IOException e) {
            return null;
        }
        retryAfter = SystemClock.elapsedRealtime() + 120_000;
        return WORKER.submit(
                () -> {
                    try {
                        new WallpaperFetch()
                                .fetch(
                                        v,
                                        directory(ctx),
                                        () -> Thread.currentThread().isInterrupted(),
                                        (n, total) -> {});
                        if (Objects.equals(before, choice(ctx))
                                && !Thread.currentThread().isInterrupted())
                            new Handler(Looper.getMainLooper()).post(ready);
                    } catch (IOException e) {
                        android.util.Log.w("WallpaperLibrary", "Background download deferred", e);
                    }
                });
    }
}
