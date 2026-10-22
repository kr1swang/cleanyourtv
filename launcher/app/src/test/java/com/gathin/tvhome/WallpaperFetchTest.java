package com.gathin.tvhome;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;

public class WallpaperFetchTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final byte[] image = {1, 2, 3, 4};
    private final byte[] video = {5, 6, 7, 8, 9};

    private WallpaperCatalog.Asset asset(String name, byte[] contents) throws Exception {
        return new WallpaperCatalog.Asset(
                name,
                "https://github.com/kr1swang/cleanyourtv/releases/download/test/" + name,
                contents.length,
                WallpaperFetch.hex(MessageDigest.getInstance("SHA-256").digest(contents)));
    }

    private WallpaperCatalog.Variant variant(boolean moving) throws Exception {
        return new WallpaperCatalog.Variant(
                "test",
                "gate",
                "Gate",
                1920,
                1080,
                moving ? 60 : 0,
                asset("poster.png", image),
                moving ? asset("video.mov", video) : null);
    }

    private void assertNoPartial(File dir) {
        assertEquals(
                0, Objects.requireNonNull(dir.listFiles((d, n) -> n.endsWith(".part"))).length);
    }

    @Test
    public void downloadsBothAssetsAndReportsCombinedProgress() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(true);
        List<Long> progress = new ArrayList<>();
        new WallpaperFetch(url -> new ByteArrayInputStream(url.endsWith(".png") ? image : video))
                .fetch(
                        v,
                        dir,
                        () -> false,
                        (n, total) -> {
                            assertEquals(9, total);
                            progress.add(n);
                        });
        assertArrayEquals(image, Files.readAllBytes(v.image.file(dir).toPath()));
        assertArrayEquals(video, Files.readAllBytes(v.video.file(dir).toPath()));
        assertEquals(Long.valueOf(9), progress.get(progress.size() - 1));
        assertNoPartial(dir);
    }

    @Test
    public void verifiedCacheWorksOfflineWithoutOpeningNetwork() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(false);
        Files.write(v.image.file(dir).toPath(), image);
        new WallpaperFetch(
                        url -> {
                            throw new AssertionError("Network must not be opened");
                        })
                .fetch(v, dir, () -> false, (n, total) -> {});
    }

    @Test
    public void sharedPosterIsDownloadedOnlyOnceAcrossFrameRates() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant still = variant(false), moving = variant(true);
        Files.write(still.image.file(dir).toPath(), image);
        AtomicInteger calls = new AtomicInteger();
        new WallpaperFetch(
                        url -> {
                            assertTrue(url.endsWith(".mov"));
                            calls.incrementAndGet();
                            return new ByteArrayInputStream(video);
                        })
                .fetch(moving, dir, () -> false, (n, total) -> {});
        assertEquals(1, calls.get());
    }

    @Test
    public void badSameSizeDownloadCannotBecomeUsable() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(false);
        assertThrows(
                IOException.class,
                () ->
                        new WallpaperFetch(url -> new ByteArrayInputStream(new byte[] {9, 9, 9, 9}))
                                .fetch(v, dir, () -> false, (n, total) -> {}));
        assertFalse(v.present(dir));
        assertNoPartial(dir);
    }

    @Test
    public void truncatedDownloadPreservesOtherCachedBackgrounds() throws Exception {
        File dir = temp.newFolder();
        File current = new File(dir, "current.png");
        Files.write(current.toPath(), image);
        WallpaperCatalog.Variant v = variant(false);
        assertThrows(
                IOException.class,
                () ->
                        new WallpaperFetch(url -> new ByteArrayInputStream(new byte[] {1}))
                                .fetch(v, dir, () -> false, (n, total) -> {}));
        assertArrayEquals(image, Files.readAllBytes(current.toPath()));
        assertFalse(v.present(dir));
        assertNoPartial(dir);
    }

    @Test
    public void oversizedResponseIsRejected() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(false);
        assertThrows(
                IOException.class,
                () ->
                        new WallpaperFetch(url -> new ByteArrayInputStream(new byte[100]))
                                .fetch(v, dir, () -> false, (n, total) -> {}));
        assertFalse(v.present(dir));
        assertNoPartial(dir);
    }

    @Test
    public void cancellationCleansPartialAndKeepsVerifiedPoster() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(true);
        AtomicBoolean cancel = new AtomicBoolean();
        assertThrows(
                InterruptedIOException.class,
                () ->
                        new WallpaperFetch(
                                        url -> {
                                            if (url.endsWith(".mov")) cancel.set(true);
                                            return new ByteArrayInputStream(
                                                    url.endsWith(".png") ? image : video);
                                        })
                                .fetch(v, dir, cancel::get, (n, total) -> {}));
        assertTrue(v.image.present(dir));
        assertFalse(v.video.present(dir));
        assertNoPartial(dir);
    }

    @Test
    public void corruptedCacheIsReplacedOnlyAfterSuccessfulValidation() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(false);
        Files.write(v.image.file(dir).toPath(), new byte[] {9, 9, 9, 9});
        assertThrows(
                IOException.class,
                () ->
                        new WallpaperFetch(
                                        url -> {
                                            throw new IOException("offline");
                                        })
                                .fetch(v, dir, () -> false, (n, total) -> {}));
        assertArrayEquals(new byte[] {9, 9, 9, 9}, Files.readAllBytes(v.image.file(dir).toPath()));
        new WallpaperFetch(url -> new ByteArrayInputStream(image))
                .fetch(v, dir, () -> false, (n, total) -> {});
        assertArrayEquals(image, Files.readAllBytes(v.image.file(dir).toPath()));
    }

    @Test
    public void abandonedPartialIsRemovedBeforeRetry() throws Exception {
        File dir = temp.newFolder();
        Files.write(new File(dir, "download-old.part").toPath(), video);
        new WallpaperFetch(url -> new ByteArrayInputStream(image))
                .fetch(variant(false), dir, () -> false, (n, total) -> {});
        assertNoPartial(dir);
    }

    @Test
    public void unavailableVideoDoesNotMarkVariantComplete() throws Exception {
        File dir = temp.newFolder();
        WallpaperCatalog.Variant v = variant(true);
        assertThrows(
                IOException.class,
                () ->
                        new WallpaperFetch(
                                        url -> {
                                            if (url.endsWith(".mov"))
                                                throw new IOException("HTTP 404");
                                            return new ByteArrayInputStream(image);
                                        })
                                .fetch(v, dir, () -> false, (n, total) -> {}));
        assertFalse(v.present(dir));
        assertNoPartial(dir);
    }
}
