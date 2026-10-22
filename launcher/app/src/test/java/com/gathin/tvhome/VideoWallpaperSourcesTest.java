package com.gathin.tvhome;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class VideoWallpaperSourcesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File video(String name) throws Exception {
        File file = temporary.newFile(name);
        Files.write(file.toPath(), new byte[] {1});
        return file.getCanonicalFile();
    }

    private VideoWallpaperSources sources(String... names) throws Exception {
        return new VideoWallpaperSources(temporary.getRoot(), Arrays.asList(names));
    }

    @Test public void selectsFromOutputModeAndCanSwitchBothDirections() throws Exception {
        File original = video("wallpaper_4K.mov");
        File fullHd = video("wallpaper_FHD.mov");
        VideoWallpaperSources sources = sources(original.getName(), fullHd.getName());
        assertEquals(fullHd, sources.select(1920, 1080));
        assertEquals(original, sources.select(3840, 2160));
        assertEquals(fullHd, sources.select(1920, 1080));
        assertEquals(fullHd, sources.select(1280, 720));
        assertEquals(fullHd, sources.select(1080, 1920));
        assertEquals(original, sources.select(2560, 1440));
        assertEquals(original, sources.select(0, 0));
    }

    @Test public void arrayOrderDoesNotDecideResolution() throws Exception {
        File fullHd = video("wallpaper_FHD_30fps.mov");
        File uhd = video("wallpaper_4K_60fps.mov");
        for (String[] order : new String[][] {
                {uhd.getName(), fullHd.getName()},
                {fullHd.getName(), uhd.getName()}}) {
            VideoWallpaperSources sources = sources(order);
            assertEquals(fullHd, sources.select(1280, 720));
            assertEquals(fullHd, sources.select(1920, 1080));
            assertEquals(uhd, sources.select(2560, 1440));
            assertEquals(uhd, sources.select(7680, 4320));
        }
    }

    @Test public void resolutionSelectionSupportsBothFrameRates() throws Exception {
        for (int fps : new int[] {30, 60}) {
            File fullHd = video("wallpaper_FHD_" + fps + "fps.mov");
            File uhd = video("wallpaper_4K_" + fps + "fps.mov");
            VideoWallpaperSources sources = sources(uhd.getName(), fullHd.getName());
            assertEquals(fullHd, sources.select(1920, 1080));
            assertEquals(uhd, sources.select(3840, 2160));
        }
    }

    @Test public void missingOrEmptyVariantFallsBackAndNewFileIsDetected() throws Exception {
        File original = video("wallpaper_4K.mov");
        VideoWallpaperSources sources = sources(original.getName(), "wallpaper_FHD.mov");
        assertEquals(original, sources.select(1920, 1080));
        File fullHd = temporary.newFile("wallpaper_FHD.mov").getCanonicalFile();
        assertEquals(original, sources.select(1920, 1080));
        Files.write(fullHd.toPath(), new byte[] {1});
        assertEquals(fullHd, sources.select(1920, 1080));
        Files.delete(fullHd.toPath());
        assertEquals(original, sources.select(1920, 1080));
    }

    @Test public void onlyFullHdAvailableStillPlays() throws Exception {
        File fullHd = video("wallpaper_FHD.mov");
        assertEquals(fullHd, sources("wallpaper_4K.mov", fullHd.getName()).select(3840, 2160));
    }

    @Test public void unlabelledSingleFileRemainsSupported() throws Exception {
        File original = video("custom.mov");
        assertEquals(original, sources(original.getName()).select(1920, 1080));
    }

    @Test public void unlabelledFileIsFallbackForMissingSizedFiles() throws Exception {
        File fallback = video("custom.mov");
        File fullHd = video("wallpaper_FHD.mov");
        VideoWallpaperSources sources = sources(fallback.getName(), fullHd.getName());
        assertEquals(fullHd, sources.select(1920, 1080));
        Files.delete(fullHd.toPath());
        assertEquals(fallback, sources.select(1920, 1080));
    }

    @Test public void emptyListOrAbsentFilesLeaveStaticPoster() throws Exception {
        assertNull(sources().select(1920, 1080));
        assertNull(sources("", "missing.mov").select(1920, 1080));
    }

    @Test public void pathsCannotEscapeThemeDirectory() throws Exception {
        File outside = video("outside.mov");
        File directory = temporary.newFolder("theme");
        assertNull(new VideoWallpaperSources(directory, Arrays.asList("../outside.mov")).select(3840, 2160));
        Files.createSymbolicLink(new File(directory, "link.mov").toPath(), outside.toPath());
        assertNull(new VideoWallpaperSources(directory, Arrays.asList("link.mov")).select(1920, 1080));
    }
}
