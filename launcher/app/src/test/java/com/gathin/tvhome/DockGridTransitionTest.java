package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockGridTransitionTest {
    @Test public void firstAppRowLocatesTheSameDockAsTheDockItself() {
        assertEquals(797, DockGridTransition.dockTop(797, 0, 241, 237));
        assertEquals(797, DockGridTransition.dockTop(1038, 1, 241, 237));
        assertEquals(797, DockGridTransition.dockTop(1275, 2, 241, 237));
    }

    @Test public void shortListCanStillScrollToTheExpandedTop() {
        int bottom = DockGridTransition.bottomPadding(1080, 74, 241, 237, 7, 6, 80);
        assertEquals(528, bottom);
        assertEquals(1080, 74 + 241 + 237 + bottom);
    }

    @Test public void fullGridKeepsNormalBottomRoom() {
        assertEquals(80, DockGridTransition.bottomPadding(1080, 74, 241, 237, 24, 6, 80));
    }

    @Test public void expandedRowsMatchTheReferenceSpacing() {
        // 1080p: dock artwork y=102, then app rows y=361, 622, 883.
        assertEquals(74, DockGridTransition.dockTop(345, 1, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(606, 2, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(867, 3, 271, 261));
    }

    @Test public void partialRowsAndHigherDensityKeepEnoughScrollRange() {
        for (int count = 7; count <= 24; count++) {
            int rows = (count + 5) / 6;
            int bottom = DockGridTransition.bottomPadding(2160, 148, 482, 474, count, 6, 160);
            assertTrue(148 + 482 + (rows - 1) * 474 + bottom >= 2160);
        }
    }
}
