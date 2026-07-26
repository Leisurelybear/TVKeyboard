package com.tvkeyboard.tv;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfirmKeyEventTrackerTest {

    @Test
    public void recognizesBothConfirmKeyCodes() {
        assertTrue(ConfirmKeyEventTracker.isConfirmKey(KeyEvent.KEYCODE_ENTER));
        assertTrue(ConfirmKeyEventTracker.isConfirmKey(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(ConfirmKeyEventTracker.isConfirmKey(KeyEvent.KEYCODE_DPAD_DOWN));
    }

    @Test
    public void confirmsOnlyOnceAfterKeyUp() {
        ConfirmKeyEventTracker tracker = new ConfirmKeyEventTracker();

        tracker.onKeyDown(0);

        assertTrue(tracker.onKeyUp(false));
        assertFalse(tracker.onKeyUp(false));
    }

    @Test
    public void ignoresRepeatAndCanceledEvents() {
        ConfirmKeyEventTracker tracker = new ConfirmKeyEventTracker();

        tracker.onKeyDown(0);
        tracker.onKeyDown(1);

        assertFalse(tracker.onKeyUp(true));
        assertFalse(tracker.onKeyUp(false));
    }
}
