package com.gathin.tvhome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pinned release metadata. No network or Android dependencies. */
final class WallpaperCatalog {
    static final class Asset {
        final String name, url, sha256;
        final long size;

        Asset(String name, String url, long size, String sha256) {
            if (!name.matches("[A-Za-z0-9_.-]+")
                    || name.equals(".")
                    || name.equals("..")
                    || !url.startsWith("https://github.com/kr1swang/cleanyourtv/releases/download/")
                    || size <= 0
                    || !sha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid wallpaper asset: " + name);
            this.name = name;
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
        }

        File file(File directory) {
            return new File(directory, sha256 + "-" + name);
        }

        boolean present(File directory) {
            File f = file(directory);
            return f.isFile() && f.length() == size;
        }
    }

    static final class Variant {
        final String id, background, title;
        final int width, height, fps;
        final Asset image, video;

        Variant(
                String id,
                String background,
                String title,
                int width,
                int height,
                int fps,
                Asset image,
                Asset video) {
            if (width <= 0
                    || height <= 0
                    || (fps != 0 && fps != 30 && fps != 60)
                    || (fps == 0) != (video == null))
                throw new IllegalArgumentException("Invalid variant: " + id);
            this.id = id;
            this.background = background;
            this.title = title;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.image = image;
            this.video = video;
        }

        List<Asset> assets() {
            return video == null ? Collections.singletonList(image) : Arrays.asList(image, video);
        }

        boolean present(File directory) {
            for (Asset a : assets()) if (!a.present(directory)) return false;
            return true;
        }

        long bytes() {
            return image.size + (video == null ? 0 : video.size);
        }
    }

    final List<Variant> variants;

    WallpaperCatalog(InputStream stream) throws IOException {
        Properties p = new Properties();
        p.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<Variant> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            for (String id : required(p, "variants").split(",")) {
                if (!id.matches("[a-z0-9-]+") || !ids.add(id))
                    throw new IllegalArgumentException("Duplicate/invalid variant");
                String background = required(p, id + ".background");
                int fps = Integer.parseInt(required(p, id + ".fps"));
                items.add(
                        new Variant(
                                id,
                                background,
                                required(p, background + ".title"),
                                Integer.parseInt(required(p, id + ".width")),
                                Integer.parseInt(required(p, id + ".height")),
                                fps,
                                asset(p, required(p, id + ".image")),
                                fps == 0 ? null : asset(p, required(p, id + ".video"))));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid wallpaper catalog", e);
        }
        variants = Collections.unmodifiableList(items);
    }

    Variant find(String id) {
        for (Variant v : variants) if (v.id.equals(id)) return v;
        return null;
    }

    Variant defaultImage() {
        for (Variant v : variants)
            if (v.fps == 0 && v.width == 1920 && v.height == 1080) return v;
        return null;
    }

    private static Asset asset(Properties p, String key) {
        return new Asset(
                required(p, key + ".name"),
                required(p, key + ".url"),
                Long.parseLong(required(p, key + ".size")),
                required(p, key + ".sha256"));
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isEmpty()) throw new IllegalArgumentException("Missing " + key);
        return v;
    }
}
