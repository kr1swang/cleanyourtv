package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Makes packaged defaults available to the existing file-based theme and wallpaper readers. */
final class BundledTheme {
    private BundledTheme() { }

    static synchronized File file(Context context, String name) {
        // Android clears this cache on app upgrades. Clearing app data/cache also regenerates it
        // from the APK, so neither a stale default nor a user's external override is copied back.
        File dir = new File(context.getCodeCacheDir(), "default-theme");
        File target = new File(dir, name);
        if (target.isFile() && target.length() > 0) return target;
        File temporary = new File(dir, name + ".tmp");
        try (InputStream in = context.getAssets().open("default-theme/" + name)) {
            Files.createDirectories(dir.toPath());
            Files.copy(in, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            Log.w("TvHome.Theme", "Cannot load bundled " + name, e);
            return null;
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                Log.w("TvHome.Theme", "Cannot remove incomplete " + name);
            }
        }
    }
}
