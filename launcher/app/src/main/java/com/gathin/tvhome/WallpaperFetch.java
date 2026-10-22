package com.gathin.tvhome;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.function.BooleanSupplier;

/** Bounded streaming downloads. A valid file is never replaced by an incomplete one. */
final class WallpaperFetch {
    interface Progress {
        void update(long bytes, long total);
    }

    interface Opener {
        InputStream open(String url) throws IOException;
    }

    private final Opener opener;

    WallpaperFetch() {
        this(WallpaperFetch::openHttps);
    }

    WallpaperFetch(Opener opener) {
        this.opener = opener;
    }

    void fetch(
            WallpaperCatalog.Variant variant,
            File directory,
            BooleanSupplier cancelled,
            Progress progress)
            throws IOException {
        Files.createDirectories(directory.toPath());
        // Android queues these jobs on one worker. A killed download may have left a temporary
        // file.
        File[] leftovers =
                directory.listFiles(
                        (dir, name) -> name.startsWith("download-") && name.endsWith(".part"));
        if (leftovers != null) for (File file : leftovers) Files.deleteIfExists(file.toPath());
        long total = variant.bytes(), done = 0, missing = 0;
        for (WallpaperCatalog.Asset a : variant.assets())
            if (!a.present(directory)) missing += a.size;
        if (directory.getUsableSpace() < missing + 1024 * 1024)
            throw new IOException("Insufficient storage");
        for (WallpaperCatalog.Asset a : variant.assets()) {
            check(cancelled);
            if (valid(a.file(directory), a, cancelled)) {
                done += a.size;
                progress.update(done, total);
                continue;
            }
            if (directory.getUsableSpace() < a.size + 1024 * 1024)
                throw new IOException("Insufficient storage");
            File temp = File.createTempFile("download-", ".part", directory);
            try {
                MessageDigest digest = digest();
                long count = 0, reported = 0;
                try (InputStream in = opener.open(a.url);
                        OutputStream out = new FileOutputStream(temp)) {
                    byte[] bytes = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(bytes)) != -1) {
                        check(cancelled);
                        count += n;
                        if (count > a.size) throw new IOException("Download exceeds expected size");
                        digest.update(bytes, 0, n);
                        out.write(bytes, 0, n);
                        long now = System.nanoTime();
                        if (now - reported > 200_000_000L) {
                            progress.update(done + count, total);
                            reported = now;
                        }
                    }
                }
                check(cancelled);
                if (count != a.size || !hex(digest.digest()).equals(a.sha256))
                    throw new IOException("Wallpaper checksum mismatch");
                try {
                    Files.move(
                            temp.toPath(),
                            a.file(directory).toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(
                            temp.toPath(),
                            a.file(directory).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                done += count;
                progress.update(done, total);
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        }
    }

    static boolean valid(File file, WallpaperCatalog.Asset asset, BooleanSupplier cancelled)
            throws IOException {
        if (!file.isFile() || file.length() != asset.size) return false;
        MessageDigest hash = digest();
        try (InputStream in = new FileInputStream(file)) {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) != -1) {
                check(cancelled);
                hash.update(b, 0, n);
            }
        }
        return hex(hash.digest()).equals(asset.sha256);
    }

    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Cancelled");
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }

    private static InputStream openHttps(String address) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15_000);
            c.setReadTimeout(20_000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept-Encoding", "identity");
            try {
                int status = c.getResponseCode();
                if (status == 301
                        || status == 302
                        || status == 303
                        || status == 307
                        || status == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    url = new URL(url, location);
                    c.disconnect();
                    continue;
                }
                if (status != 200) throw new IOException("HTTP " + status);
                return new FilterInputStream(c.getInputStream()) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            c.disconnect();
                        }
                    }
                };
            } catch (IOException e) {
                c.disconnect();
                throw e;
            }
        }
        throw new IOException("Too many redirects");
    }
}
