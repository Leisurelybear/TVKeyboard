package com.tvkeyboard.tv;

import android.view.KeyEvent;

final class ConfirmKeyEventTracker {

    private boolean pressed;
    private long lastConfirmAt;
    private static final long DEBOUNCE_MS = 350L;

    static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    void onKeyDown(int repeatCount) {
        if (repeatCount == 0) pressed = true;
    }

    boolean onKeyUp(boolean canceled) {
        if (!pressed) return false;
        pressed = false;
        if (canceled) return false;
        long now = System.currentTimeMillis();
        if (now - lastConfirmAt < DEBOUNCE_MS) return false;
        lastConfirmAt = now;
        return true;
    }
}
