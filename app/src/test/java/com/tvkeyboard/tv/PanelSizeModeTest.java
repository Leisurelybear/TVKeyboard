package com.tvkeyboard.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PanelSizeModeTest {

    @Test
    public void cyclesThroughAllModes() {
        assertSame(PanelSizeMode.TALL, PanelSizeMode.FULL.next());
        assertSame(PanelSizeMode.HALF, PanelSizeMode.TALL.next());
        assertSame(PanelSizeMode.QUARTER, PanelSizeMode.HALF.next());
        assertSame(PanelSizeMode.FULL, PanelSizeMode.QUARTER.next());
    }

    @Test
    public void exposesHeightFractions() {
        assertEquals(1.00f, PanelSizeMode.FULL.heightFraction, 0.0001f);
        assertEquals(0.75f, PanelSizeMode.TALL.heightFraction, 0.0001f);
        assertEquals(0.50f, PanelSizeMode.HALF.heightFraction, 0.0001f);
        assertEquals(0.25f, PanelSizeMode.QUARTER.heightFraction, 0.0001f);
    }

    @Test
    public void parsesKnownFractions() {
        assertSame(PanelSizeMode.FULL, PanelSizeMode.fromFraction(1.0f));
        assertSame(PanelSizeMode.TALL, PanelSizeMode.fromFraction(0.75f));
        assertSame(PanelSizeMode.HALF, PanelSizeMode.fromFraction(0.5f));
        assertSame(PanelSizeMode.QUARTER, PanelSizeMode.fromFraction(0.25f));
    }

    @Test
    public void fallsBackToTallOnUnknownFraction() {
        assertSame(PanelSizeMode.TALL, PanelSizeMode.fromFraction(0.9f));
        assertSame(PanelSizeMode.TALL, PanelSizeMode.fromFraction(-1f));
    }
}
