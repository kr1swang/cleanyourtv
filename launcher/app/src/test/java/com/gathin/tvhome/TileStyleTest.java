package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class TileStyleTest {
    @Test public void appleKeepsTheExistingSixColumnGeometry() {
        TileStyle style = TileStyle.from("apple");
        int width = AppAdapter.tileWidthPx(1792, 896, 6, 0, false, true, 2f, style);
        assertEquals(247, width);
        assertEquals(147, style.height(width));
        assertEquals(1.20f, style.focusScale(false), 0f);
        assertEquals(1.20f, style.focusScale(true), 0f);
        assertEquals(32, style.dockInset(2f));
        assertEquals(30f * 247 / 248, style.radius(width, 2f), 0.001f);
    }

    @Test public void defaultKeepsUpstreamGeometryAcrossColumnCounts() {
        TileStyle style = TileStyle.from("default");
        for (int columns = 4; columns <= 7; columns++) {
            int width = style.width(1696, columns, 2f);
            assertEquals(AppAdapter.tileWidthPx(1696, columns, 2f), width);
            assertEquals(AppAdapter.tileHeightPx(width), style.height(width));
            assertEquals(AppAdapter.cornerRadius(width, 2f), style.radius(width, 2f), 0f);
        }
        assertEquals(1.10f, style.focusScale(false), 0f);
        assertEquals(1.12f, style.focusScale(true), 0f);
        assertEquals(0, style.dockInset(2f));
    }

    @Test public void bothStylesKeepConcentricDockCornersAndRoomForFocus() {
        for (TileStyle style : TileStyle.values()) {
            for (int columns = 4; columns <= 7; columns++) {
                int inner = 1792;
                int width = style.width(inner, columns, 2f);
                float padding = DockDecoration.evenPaddingPx(inner, columns, width, 100f);
                float radius = style.radius(width, 2f);
                assertEquals(padding, DockDecoration.radiusPx(radius, padding) - radius, 0.001f);
                assertTrue(width * style.focusScale(false) < inner / columns);
            }
        }
    }
}
