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
        int bottom = DockGridTransition.bottomPadding(1080, 74, 241, 237, 7, 6);
        assertEquals(528, bottom);
        assertEquals(1080, 74 + 241 + 237 + bottom);
    }

    @Test public void fullGridLeavesRoomForTheLastFixedRowSlot() {
        assertEquals(54, DockGridTransition.bottomPadding(1080, 74, 241, 237, 24, 6));
    }

    @Test public void expandedRowsMatchTheReferenceSpacing() {
        // 1080p: dock artwork y=102, then app rows y=361, 622, 883.
        assertEquals(74, DockGridTransition.dockTop(345, 1, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(606, 2, 271, 261));
        assertEquals(74, DockGridTransition.dockTop(867, 3, 271, 261));
    }

    @Test public void scrollingDownKeepsTheSameFocusSlotInFourAndSixColumns() {
        // Real 1080p geometry: four-column MyVideo and Spotify both have card y=758.
        assertEquals(2, DockGridTransition.visibleAppRows(1080, 74, 339, 329));
        assertEquals(9, DockGridTransition.bottomPadding(1080, 74, 339, 329, 16, 4));
        assertEquals(2, DockGridTransition.visibleAppRows(1080, 74, 271, 261));
        assertEquals(213, DockGridTransition.bottomPadding(1080, 74, 271, 261, 40, 6));
        for (int columns : new int[] {4, 6}) {
            int shift = 0;
            for (int position = columns * 2; position < columns * 10; position += columns) {
                int row = position / columns;
                shift = DockGridTransition.scrollRows(shift, row, 2);
                assertEquals(2, row - shift);
                assertEquals(shift, DockGridTransition.scrollRows(shift, row, 2));
            }
            // Reverse direction without consulting an in-flight pixel position; Home resets it.
            assertEquals(7, DockGridTransition.scrollRows(8, 8, 2));
            assertEquals(0, DockGridTransition.scrollRows(8, 0, 2));
        }
    }

    @Test public void partialLastRowsUseTheSameBottomSlotAsFullRows() {
        for (int columns : new int[] {4, 6}) {
            for (int count = columns * 3 + 1; count <= columns * 5; count++) {
                assertEquals(213, DockGridTransition.bottomPadding(1080, 74, 271, 261, count, columns));
            }
        }
    }

    @Test public void partialRowsAndHigherDensityKeepEnoughScrollRange() {
        for (int count = 7; count <= 24; count++) {
            int rows = (count + 5) / 6;
            int bottom = DockGridTransition.bottomPadding(2160, 148, 482, 474, count, 6);
            assertTrue(148 + 482 + (rows - 1) * 474 + bottom >= 2160);
        }
    }
}
