package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ActionStyleTest {
    @Test public void layoutAndMenuAreOptIn() {
        assertEquals("apple", Config.homeLayout(" APPLE "));
        assertEquals("panel", Config.tileMenuStyle(" PANEL "));
        for (String value : new String[]{null, "", "default", "unknown"}) {
            assertEquals("default", Config.homeLayout(value));
            assertEquals("default", Config.tileMenuStyle(value));
        }
        assertEquals("default", Config.homeLayout("panel"));
        assertEquals("default", Config.tileMenuStyle("apple"));
    }
    @Test public void panelIsExplicit() {
        assertEquals(Config.ACTION_PANEL, Config.actionStyle("panel"));
        assertEquals(Config.ACTION_PANEL, Config.actionStyle(" PANEL "));
    }
    @Test public void defaultsToUpstreamButtons() {
        for (String value : new String[]{null, "", "buttons", "pabel", "unknown"}) {
            assertEquals(Config.ACTION_BUTTONS, Config.actionStyle(value));
        }
    }
}
