package com.gathin.tvhome;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class WallpaperCatalogTest {
    @Test
    public void shippedCatalogContainsAllSixVariants() throws Exception {
        try (InputStream in = new FileInputStream("../../themes/apple-style/wallpaper-catalog.properties")) {
            WallpaperCatalog catalog = new WallpaperCatalog(in);
            assertEquals(6, catalog.variants.size());
            assertEquals("Golden Gate", catalog.find("gg-4k-60").title);
            assertEquals(60, catalog.find("gg-4k-60").fps);
        }
    }

    private String metadata() {
        return "variants=hd,uhd\n"
                   + "background.title=Gate\n"
                   + "hd.background=background\n"
                   + "hd.width=1920\n"
                   + "hd.height=1080\n"
                   + "hd.fps=0\n"
                   + "hd.image=a\n"
                   + "uhd.background=background\n"
                   + "uhd.width=3840\n"
                   + "uhd.height=2160\n"
                   + "uhd.fps=0\n"
                   + "uhd.image=b\n"
                + asset("a", "hd.png")
                + asset("b", "4k.png");
    }

    private String asset(String key, String name) {
        return key
                + ".name="
                + name
                + "\n"
                + key
                + ".url=https://github.com/kr1swang/cleanyourtv/releases/download/v1/"
                + name
                + "\n"
                + key
                + ".size=4\n"
                + key
                + ".sha256="
                + String.join("", java.util.Collections.nCopies(64, "a"))
                + "\n";
    }

    private WallpaperCatalog read(String s) throws IOException {
        return new WallpaperCatalog(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void defaultFollowsDisplayResolution() throws Exception {
        WallpaperCatalog c = read(metadata());
        assertEquals("hd", c.defaultImage(1920, 1080).id);
        assertEquals("uhd", c.defaultImage(3840, 2160).id);
        assertEquals("uhd", c.defaultImage(7680, 4320).id);
        assertEquals("Gate", c.find("hd").title);
    }

    @Test
    public void unsafeFilenameRejected() {
        assertThrows(
                IOException.class,
                () -> read(metadata().replace("a.name=hd.png", "a.name=../outside.png")));
    }

    @Test
    public void untrustedUrlRejected() {
        assertThrows(
                IOException.class,
                () ->
                        read(
                                metadata()
                                        .replace(
                                                "https://github.com/kr1swang/cleanyourtv/",
                                                "https://example.com/")));
    }

    @Test
    public void duplicateIdsRejected() {
        assertThrows(
                IOException.class,
                () -> read(metadata().replace("variants=hd,uhd", "variants=hd,hd")));
    }

    @Test
    public void missingVideoRejected() {
        assertThrows(IOException.class, () -> read(metadata().replace("hd.fps=0", "hd.fps=60")));
    }

    @Test
    public void unknownFrameRateRejected() {
        assertThrows(IOException.class, () -> read(metadata().replace("hd.fps=0", "hd.fps=25")));
    }

    @Test
    public void missingChecksumRejected() {
        assertThrows(IOException.class, () -> read(metadata().replace("a.sha256=", "unused=")));
    }
}
