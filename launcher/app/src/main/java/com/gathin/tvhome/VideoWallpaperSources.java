package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects local renditions named *_WIDTHxHEIGHT.ext using the physical output mode. */
final class VideoWallpaperSources {
    private static final Pattern SIZE = Pattern.compile("_(\\d+)x(\\d+)\\.[^.]+$");
    private final List<Source> sources = new ArrayList<>();

    VideoWallpaperSources(File directory, List<String> names) throws IOException {
        File parent = directory.getCanonicalFile();
        for (String name : names) {
            if (name.isEmpty()) continue;
            File file = new File(directory, name).getCanonicalFile();
            if (!parent.equals(file.getParentFile())) continue;
            int width = 0, height = 0;
            Matcher size = SIZE.matcher(name);
            if (size.find()) {
                try {
                    width = Integer.parseInt(size.group(1));
                    height = Integer.parseInt(size.group(2));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (width <= 0 || height <= 0) continue;
            }
            sources.add(new Source(file, Math.max(width, height), Math.min(width, height)));
        }
    }

    File select(int width, int height) {
        int longSide = Math.max(width, height), shortSide = Math.min(width, height);
        Source best = null, largest = null, unnamed = null;
        for (Source source : sources) {
            if (!source.file.isFile() || source.file.length() == 0) continue;
            if (source.width == 0) {
                if (unnamed == null) unnamed = source;
                continue;
            }
            if (largest == null || source.pixels() > largest.pixels()) largest = source;
            if (shortSide > 0 && source.width >= longSide && source.height >= shortSide
                    && (best == null || source.pixels() < best.pixels())) best = source;
        }
        // Unknown output or no rendition large enough: keep the best available quality.
        Source selected = best != null ? best : largest != null ? largest : unnamed;
        return selected == null ? null : selected.file;
    }

    private static final class Source {
        final File file;
        final int width, height;

        Source(File file, int width, int height) {
            this.file = file;
            this.width = width;
            this.height = height;
        }

        long pixels() { return (long) width * height; }
    }
}
