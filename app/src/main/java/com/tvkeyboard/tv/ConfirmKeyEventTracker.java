package com.tvkeyboard.tv;

import android.view.KeyEvent;

final class ConfirmKeyEventTracker {

    private boolean pressed;

    static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    void onKeyDown(int repeatCount) {
        if (repeatCount == 0) pressed = true;
    }

    boolean onKeyUp(boolean canceled) {
        if (!pressed) return false;
        pressed = false;
        return !canceled;
    }
}
