package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;

/** Chooses a local rendition from the HDMI mode, independently of the UI's render size. */
final class VideoWallpaperSources {
    private final File original;
    private final File fullHd;

    VideoWallpaperSources(File directory, String original, String fullHd) throws IOException {
        this.original = localFile(directory, original);
        this.fullHd = localFile(directory, fullHd);
    }

    File select(int width, int height) {
        boolean fitsFullHd = width > 0 && height > 0
                && Math.max(width, height) <= 1920 && Math.min(width, height) <= 1080;
        if (fitsFullHd && available(fullHd)) return fullHd;
        if (available(original)) return original;
        return available(fullHd) ? fullHd : null;
    }

    private static boolean available(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    private static File localFile(File directory, String name) throws IOException {
        if (name.isEmpty()) return null;
        File file = new File(directory, name).getCanonicalFile();
        return directory.getCanonicalFile().equals(file.getParentFile()) ? file : null;
    }
}
