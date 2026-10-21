package com.gathin.tvhome;

import java.io.File;
import java.nio.file.Files;
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

    @Test public void selectsFromOutputModeAndCanSwitchBothDirections() throws Exception {
        File original = video("wallpaper_3840x2160.mov");
        File fullHd = video("wallpaper_1920x1080.mov");
        VideoWallpaperSources sources = new VideoWallpaperSources(temporary.getRoot(),
                original.getName(), fullHd.getName());
        assertEquals(fullHd, sources.select(1920, 1080));
        assertEquals(original, sources.select(3840, 2160));
        assertEquals(fullHd, sources.select(1920, 1080));
        assertEquals(fullHd, sources.select(1280, 720));
        assertEquals(fullHd, sources.select(1080, 1920));
        assertEquals(original, sources.select(2560, 1440));
        assertEquals(original, sources.select(0, 0));
    }

    @Test public void missingOrEmptyFullHdFallsBackAndNewFileIsDetected() throws Exception {
        File original = video("original.mov");
        VideoWallpaperSources sources = new VideoWallpaperSources(temporary.getRoot(),
                original.getName(), "full-hd.mov");
        assertEquals(original, sources.select(1920, 1080));
        File fullHd = temporary.newFile("full-hd.mov").getCanonicalFile();
        assertEquals(original, sources.select(1920, 1080));
        Files.write(fullHd.toPath(), new byte[] {1});
        assertEquals(fullHd, sources.select(1920, 1080));
        Files.delete(fullHd.toPath());
        assertEquals(original, sources.select(1920, 1080));
    }

    @Test public void onlyFullHdAvailableStillPlays() throws Exception {
        File fullHd = video("full-hd.mov");
        VideoWallpaperSources sources = new VideoWallpaperSources(temporary.getRoot(),
                "missing.mov", fullHd.getName());
        assertEquals(fullHd, sources.select(3840, 2160));
    }

    @Test public void singleFileThemeRemainsSupported() throws Exception {
        File original = video("custom.mov");
        VideoWallpaperSources sources = new VideoWallpaperSources(temporary.getRoot(), original.getName(), "");
        assertEquals(original, sources.select(1920, 1080));
    }

    @Test public void absentFilesLeaveStaticPoster() throws Exception {
        assertNull(new VideoWallpaperSources(temporary.getRoot(), "missing.mov", "").select(1920, 1080));
    }

    @Test public void pathsCannotEscapeThemeDirectory() throws Exception {
        File outside = video("outside.mov");
        File directory = temporary.newFolder("theme");
        assertNull(new VideoWallpaperSources(directory, "../outside.mov", "").select(3840, 2160));
        Files.createSymbolicLink(new File(directory, "link.mov").toPath(), outside.toPath());
        assertNull(new VideoWallpaperSources(directory, "link.mov", "").select(1920, 1080));
    }
}
