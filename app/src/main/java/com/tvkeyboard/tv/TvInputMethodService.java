package com.tvkeyboard.tv;

import android.graphics.Bitmap;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.media.AudioManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.tvkeyboard.R;
import com.tvkeyboard.common.NetworkUtils;
import com.tvkeyboard.common.QrCodeGenerator;
import com.tvkeyboard.common.TvHttpServer;
import com.tvkeyboard.common.TvWebSocketServer;
import com.tvkeyboard.common.TvServerManager;

/**
 * TV Input Method Service (IME mode).
 *
 * v3 changes:
 * - Confirm / Clear / Dismiss buttons removed from IME panel.
 *   Users use remote ✓ (DPAD_CENTER / ENTER) to confirm, BACK to dismiss.
 * - IME panel now occupies 75 % of screen height (up from 65 %).
 * - Background is semi-transparent so content behind remains visible.
 */
public class TvInputMethodService extends InputMethodService implements TvWebSocketServer.Listener {

    private TvWebSocketServer wsServer;
    private TvHttpServer httpServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConfirmKeyEventTracker confirmKeyTracker = new ConfirmKeyEventTracker();
    private String currentText = "";

    // Views — no buttons in IME panel anymore
    private View inputView;
    private boolean panelVisible;
    private ImageView ivQrCode;
    private TextView tvInputText;
    private TextView tvPlaceholder;
    private TextView tvClientStatus;
    private TextView tvIpAddress;
    private TextView tvSessionBadge;

    private static final String PREFS_NAME = "ime_panel_prefs";
    private static final String PREF_HEIGHT_FRACTION = "height_fraction";
    private static final String PREF_REMOTE_ENABLED = "remote_enabled";

    private PanelSizeMode panelSizeMode = PanelSizeMode.TALL;
    private TextView btnSizeToggle;
    private TextView btnSwitchIme;
    private TextView btnRemoteService;
    private View cardRemoteHint;
    private View bottomStrip;
    private View leftColumn;

    @Override
    public void onCreate() {
        super.onCreate();
        panelSizeMode = PanelSizeMode.fromFraction(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getFloat(PREF_HEIGHT_FRACTION, PanelSizeMode.TALL.heightFraction));
    }

    private void startServers() {
        if (!isRemoteEnabled()) {
            wsServer = null;
            return;
        }
        TvServerManager.register(this, this);
        wsServer = TvServerManager.getWebSocketServer();
    }

    @Override
    public View onCreateInputView() {
        startServers();
        inputView = LayoutInflater.from(this).inflate(R.layout.ime_tv_panel, null);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        inputView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (screenHeight * panelSizeMode.heightFraction)
        ));

        ivQrCode        = inputView.findViewById(R.id.iv_qr_code);
        tvInputText     = inputView.findViewById(R.id.tv_input_text);
        tvPlaceholder   = inputView.findViewById(R.id.tv_placeholder);
        tvClientStatus  = inputView.findViewById(R.id.tv_client_status);
        tvIpAddress     = inputView.findViewById(R.id.tv_ip_address);
        tvSessionBadge  = inputView.findViewById(R.id.tv_session_badge);

        btnSizeToggle  = inputView.findViewById(R.id.btn_size_toggle);
        btnSwitchIme   = inputView.findViewById(R.id.btn_switch_ime);
        btnRemoteService = inputView.findViewById(R.id.btn_remote_service);
        cardRemoteHint = inputView.findViewById(R.id.card_remote_hint);
        bottomStrip    = inputView.findViewById(R.id.bottom_strip);
        leftColumn     = inputView.findViewById(R.id.left_column);

        btnSizeToggle.setOnClickListener(v -> {
            panelSizeMode = panelSizeMode.next();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putFloat(PREF_HEIGHT_FRACTION, panelSizeMode.heightFraction)
                    .apply();
            applySizeMode();
        });
        btnSwitchIme.setOnClickListener(v -> showImePicker());
        btnRemoteService.setOnClickListener(v -> toggleRemoteService());
        btnRemoteService.setText(isRemoteEnabled() ? "停止远程" : "启动远程");

        applySizeMode();

        setupQrCode();
        return inputView;
    }

    private void setupQrCode() {
        if (!isRemoteEnabled() || TvServerManager.getToken() == null) {
            if (ivQrCode != null) ivQrCode.setImageDrawable(null);
            if (tvIpAddress != null) tvIpAddress.setText("远程服务已停止");
            return;
        }
        String ip = NetworkUtils.getLocalIpAddress(this);
        String url = NetworkUtils.buildQrContent(ip, TvHttpServer.HTTP_PORT, TvServerManager.getToken());

        if (tvIpAddress != null) tvIpAddress.setText(ip + ":" + TvHttpServer.HTTP_PORT);

        int sizePx = (int) (148 * getResources().getDisplayMetrics().density);
        Bitmap bmp = QrCodeGenerator.generate(url, sizePx);
        if (bmp != null && ivQrCode != null) ivQrCode.setImageBitmap(bmp);
    }

    private void applySizeMode() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        inputView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (screenHeight * panelSizeMode.heightFraction)
        ));
        if (btnSizeToggle != null) {
            btnSizeToggle.setText("尺寸：" + panelSizeMode.displayName());
        }
        applyCompactMode();
    }

    private void applyCompactMode() {
        if (cardRemoteHint == null || bottomStrip == null || leftColumn == null || ivQrCode == null) return;
        boolean compact = panelSizeMode.heightFraction <= PanelSizeMode.HALF.heightFraction;
        boolean tiny = panelSizeMode == PanelSizeMode.QUARTER;

        cardRemoteHint.setVisibility(compact ? View.GONE : View.VISIBLE);
        bottomStrip.setVisibility(compact ? View.GONE : View.VISIBLE);

        float density = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams qrLp = ivQrCode.getLayoutParams();
        qrLp.width = (int) ((tiny ? 96 : 148) * density);
        qrLp.height = qrLp.width;
        ivQrCode.setLayoutParams(qrLp);

        leftColumn.getLayoutParams().width = (int) ((tiny ? 160 : 200) * density);
        leftColumn.requestLayout();
    }

    private void showImePicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private boolean isRemoteEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_REMOTE_ENABLED, true);
    }

    private void toggleRemoteService() {
        if (TvServerManager.getWebSocketServer() != null) {
            TvServerManager.disconnectAll();
            TvServerManager.stop();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_REMOTE_ENABLED, false).apply();
            wsServer = null;
            btnRemoteService.setText("启动远程");
            tvClientStatus.setText("远程服务已停止");
            tvClientStatus.setTextColor(0xFFFFB74D);
        } else {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_REMOTE_ENABLED, true).apply();
            startServers();
            setupQrCode();
            boolean enabled = TvServerManager.getWebSocketServer() != null;
            btnRemoteService.setText(enabled ? "停止远程" : "启动远程");
            tvClientStatus.setText(enabled ? "等待扫码连接..." : "远程服务启动失败");
            tvClientStatus.setTextColor(enabled ? 0xFF888888 : 0xFFFF6E6E);
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        panelVisible = true;
        if (!restarting) {
            // 只有全新打开才清空，restarting=true 表示重新显示不要清空
            currentText = "";
            updateDisplay("");
            if (wsServer != null) wsServer.broadcastSync("", "tv");
        }
    }

    // ── Remote control key interception ──────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (ConfirmKeyEventTracker.isConfirmKey(keyCode)) {
            if (!panelVisible || inputView == null || inputView.getVisibility() != View.VISIBLE) {
                return confirmKeyTracker.shouldSuppressRapidPress();
            }
            confirmKeyTracker.onKeyDown(event.getRepeatCount());
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showImePicker();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            requestHideSelf(0);
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (ConfirmKeyEventTracker.isConfirmKey(keyCode)) {
            if (!panelVisible || inputView == null || inputView.getVisibility() != View.VISIBLE) {
                return confirmKeyTracker.shouldSuppressRapidPress();
            }
            if (confirmKeyTracker.onKeyUp(event.isCanceled())) confirmInput();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        return true;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        panelVisible = false;
        super.onFinishInputView(finishingInput);
    }

    // ── WebSocket.Listener callbacks ─────────────────────────────────────────

    @Override
    public void onClientConnected(String sessionId, String clientIp) {
        mainHandler.post(() -> {
            if (tvClientStatus == null) return;
            int count = wsServer != null ? wsServer.getConnectedClientCount() : 1;
            tvClientStatus.setText(count == 1 ? "已连接 ✓" : count + " 台已连接 ✓");
            tvClientStatus.setTextColor(0xFF4CAF50);
            if (tvSessionBadge != null && count > 1) {
                tvSessionBadge.setText(count + " 人同时输入");
                tvSessionBadge.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onClientDisconnected(String sessionId, int remaining) {
        mainHandler.post(() -> {
            if (tvClientStatus == null) return;
            if (remaining == 0) {
                tvClientStatus.setText("等待扫码连接...");
                tvClientStatus.setTextColor(0xFF888888);
                if (tvSessionBadge != null) tvSessionBadge.setVisibility(View.GONE);
            } else {
                tvClientStatus.setText(remaining + " 台已连接 ✓");
                tvClientStatus.setTextColor(0xFF4CAF50);
            }
        });
    }

    @Override
    public void onTextReceived(String text, String sessionId) {
        currentText = text;
        mainHandler.post(() -> {
            updateDisplay(text);
            if (getCurrentInputConnection() != null) {
                getCurrentInputConnection().setComposingText(text, 1);
            }
        });
    }

    @Override
    public void onActionReceived(String action, String sessionId) {
        mainHandler.post(() -> {
            switch (action) {
                case "confirm":
                    confirmInput();
                    break;
                case "backspace":
                    if (!currentText.isEmpty()) {
                        currentText = currentText.substring(0, currentText.offsetByCodePoints(currentText.length(), -1));
                        updateDisplay(currentText);
                        if (getCurrentInputConnection() != null)
                            getCurrentInputConnection().setComposingText(currentText, 1);
                        if (wsServer != null) wsServer.broadcastSync(currentText, "tv");
                    }
                    break;
                case "clear":
                    clearInput();
                    break;
                case "dismiss":
                    requestHideSelf(0);
                    break;
                case "dpad_up":
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP);
                    break;
                case "dpad_down":
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN);
                    break;
                case "dpad_left":
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                    break;
                case "dpad_right":
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                    break;
                case "back":
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_BACK);
                    break;
                case "menu":
                    showImePicker();
                    break;
                case "volume_up":
                    adjustVolume(AudioManager.ADJUST_RAISE);
                    break;
                case "volume_down":
                    adjustVolume(AudioManager.ADJUST_LOWER);
                    break;
            }
        });
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void confirmInput() {
        if (getCurrentInputConnection() != null) getCurrentInputConnection().finishComposingText();
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        if (wsServer != null) wsServer.broadcastAction("confirmed");
        requestHideSelf(0);
    }

    private void adjustVolume(int direction) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) audio.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
    }

    private void clearInput() {
        currentText = "";
        updateDisplay("");
        if (getCurrentInputConnection() != null)
            getCurrentInputConnection().setComposingText("", 1);
        if (wsServer != null) {
            wsServer.broadcastSync("", "tv");
            wsServer.broadcastAction("cleared");
        }
    }

    private void updateDisplay(String text) {
        if (tvInputText == null || tvPlaceholder == null) return;
        boolean empty = (text == null || text.isEmpty());
        tvInputText.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvPlaceholder.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) tvInputText.setText(text);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TvServerManager.unregister(this);
    }

    private void deletePreviousCodePoint(android.view.inputmethod.InputConnection connection) {
        CharSequence before = connection.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) return;
        int count = Character.charCount(Character.codePointBefore(before, before.length()));
        connection.deleteSurroundingText(count, 0);
    }
}
