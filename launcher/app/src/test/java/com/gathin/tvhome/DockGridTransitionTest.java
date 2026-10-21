package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockGridTransitionTest {
    @Test public void everyAppRowLocatesTheSameDock() {
        assertEquals(74, DockGridTransition.dockTop(74, 0, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(345, 1, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(606, 2, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(867, 3, 271, 261));
    }

    @Test public void thirdSixColumnRowScrollsOnlyAsFarAsTheReferenceFocusLine() {
        assertEquals(826, DockGridTransition.focusBottom(1080));
        assertEquals(0, DockGridTransition.scrollOffset(0, 435, 1080));
        assertEquals(0, DockGridTransition.scrollOffset(0, 696, 1080));
        int offset = DockGridTransition.scrollOffset(0, 957, 1080);
        assertEquals(131, offset); // A partial row, rather than the old full 261px jump.
        assertEquals(826, 957 - offset);
        assertEquals(131, DockGridTransition.scrollOffset(offset, 957, 1080));
    }

    @Test public void bothColumnCountsKeepFocusInsideTheBandInBothDirections() {
        for (int[] geometry : new int[][] {{339, 329, 124}, {271, 261, 90}}) {
            int dock = geometry[0], rowHeight = geometry[1], cardCenter = geometry[2];
            int offset = 0;
            for (int row = 1; row <= 10; row++) {
                int center = 74 + dock + (row - 1) * rowHeight + cardCenter;
                offset = DockGridTransition.scrollOffset(offset, center, 1080);
                assertTrue(center - offset >= 254);
                assertTrue(center - offset <= 826);
                assertEquals(offset, DockGridTransition.scrollOffset(offset, center, 1080));
            }
            for (int row = 9; row >= 1; row--) {
                int center = 74 + dock + (row - 1) * rowHeight + cardCenter;
                offset = DockGridTransition.scrollOffset(offset, center, 1080);
                assertTrue(center - offset >= 254);
                assertTrue(center - offset <= 826);
                assertEquals(offset, DockGridTransition.scrollOffset(offset, center, 1080));
            }
        }
    }

    @Test public void reversingDirectionDoesNotMoveUntilFocusCrossesTheOppositeEdge() {
        int offset = DockGridTransition.scrollOffset(0, 1479, 1080);
        assertEquals(653, offset);
        assertEquals(offset, DockGridTransition.scrollOffset(offset, 1218, 1080));
        assertEquals(offset, DockGridTransition.scrollOffset(offset, 957, 1080));
        assertEquals(442, DockGridTransition.scrollOffset(offset, 696, 1080));
        // Reverse again while an animation may still be running: use its target, not view pixels.
        assertEquals(442, DockGridTransition.scrollOffset(442, 957, 1080));
        assertEquals(442, DockGridTransition.scrollOffset(442, 1218, 1080));
        assertEquals(653, DockGridTransition.scrollOffset(442, 1479, 1080));
    }

    @Test public void shortListCanExpandAndLongListsReachTheSameLastFocusLine() {
        assertEquals(0, DockGridTransition.bottomPadding(1080, 74, 271, 261, 90, 6, 6));
        assertEquals(474, DockGridTransition.bottomPadding(1080, 74, 271, 261, 90, 7, 6));
        assertEquals(83, DockGridTransition.bottomPadding(1080, 74, 271, 261, 90, 40, 6));
        for (int[] geometry : new int[][] {{4, 339, 329, 124}, {6, 271, 261, 90}}) {
            int columns = geometry[0], dock = geometry[1], rowHeight = geometry[2], cardCenter = geometry[3];
            for (int count = columns + 1; count <= columns * 10; count++) {
                int rows = (count + columns - 1) / columns;
                int content = dock + (rows - 1) * rowHeight;
                int lastCenter = 74 + content - rowHeight + cardCenter;
                int travel = DockGridTransition.scrollOffset(0, lastCenter, 1080);
                int padding = DockGridTransition.bottomPadding(1080, 74, dock, rowHeight, cardCenter, count, columns);
                assertTrue(74 + content + padding - travel >= 1080);
            }
        }
    }

    @Test public void geometryScalesWithResolution() {
        assertEquals(262, DockGridTransition.scrollOffset(0, 1914, 2160));
        assertEquals(166, DockGridTransition.bottomPadding(2160, 148, 542, 522, 180, 40, 6));
    }
}
