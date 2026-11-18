package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PanelStyleTest {
    @Test public void disablingAnimationsAlsoDisablesReducedFades() {
        assertEquals(0, PanelStyle.transitionDuration(false, false));
        assertEquals(0, PanelStyle.transitionDuration(false, true));
        assertEquals(60, PanelStyle.transitionDuration(true, true));
        assertEquals(180, PanelStyle.transitionDuration(true, false));
    }

    @Test public void closedMenuRestoresGridShade() {
        assertEquals(PanelStyle.BACKDROP_ALPHA, PanelStyle.gridShadeAlpha(0));
    }

    @Test public void openMenuReplacesGridShade() {
        assertEquals(0, PanelStyle.gridShadeAlpha(PanelStyle.BACKDROP_ALPHA));
    }

    @Test public void transitionDoesNotDoubleDimWallpaper() {
        for (int overlay = 0; overlay <= PanelStyle.BACKDROP_ALPHA; overlay++) {
            float combined = overlay + PanelStyle.gridShadeAlpha(overlay) * (1f - overlay / 255f);
            assertEquals(PanelStyle.BACKDROP_ALPHA, combined, 0.5f);
        }
    }
}
