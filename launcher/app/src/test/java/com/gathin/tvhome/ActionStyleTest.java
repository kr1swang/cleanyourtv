package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ActionStyleTest {
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
