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
import android.widget.ImageView;
import android.widget.TextView;

import com.tvkeyboard.R;
import com.tvkeyboard.common.NetworkUtils;
import com.tvkeyboard.common.QrCodeGenerator;
import com.tvkeyboard.common.TvHttpServer;
import com.tvkeyboard.common.TvWebSocketServer;

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
    private String currentText = "";

    // Views — no buttons in IME panel anymore
    private View inputView;
    private ImageView ivQrCode;
    private TextView tvInputText;
    private TextView tvPlaceholder;
    private TextView tvClientStatus;
    private TextView tvIpAddress;
    private TextView tvSessionBadge;

    @Override
    public void onCreate() {
        super.onCreate();
        startServers();
    }

    private void startServers() {
        wsServer = new TvWebSocketServer(TvWebSocketServer.DEFAULT_PORT, this);
        wsServer.setConnectionLostTimeout(30);
        try { wsServer.start(); } catch (Exception e) { e.printStackTrace(); }

        httpServer = new TvHttpServer(TvWebSocketServer.DEFAULT_PORT);
        try { httpServer.start(); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public View onCreateInputView() {
        inputView = LayoutInflater.from(this).inflate(R.layout.ime_tv_panel, null);

        // 75% screen height — more room, still shows content above
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        inputView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (screenHeight * 0.75f)
        ));

        ivQrCode        = inputView.findViewById(R.id.iv_qr_code);
        tvInputText     = inputView.findViewById(R.id.tv_input_text);
        tvPlaceholder   = inputView.findViewById(R.id.tv_placeholder);
        tvClientStatus  = inputView.findViewById(R.id.tv_client_status);
        tvIpAddress     = inputView.findViewById(R.id.tv_ip_address);
        tvSessionBadge  = inputView.findViewById(R.id.tv_session_badge);

        // No buttons to wire up — remote control handles confirm & dismiss.

        setupQrCode();
        return inputView;
    }

    private void setupQrCode() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        String url = NetworkUtils.buildQrContent(ip, TvHttpServer.HTTP_PORT);

        if (tvIpAddress != null) tvIpAddress.setText(ip + ":" + TvHttpServer.HTTP_PORT);

        int sizePx = (int) (148 * getResources().getDisplayMetrics().density);
        Bitmap bmp = QrCodeGenerator.generate(url, sizePx);
        if (bmp != null && ivQrCode != null) ivQrCode.setImageBitmap(bmp);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentText = "";
        updateDisplay("");
        if (wsServer != null) wsServer.broadcastSync("", "tv");
    }

    // ── Remote control key interception ──────────────────────────────────────

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            confirmInput();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            requestHideSelf(0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
                getCurrentInputConnection().deleteSurroundingText(9999, 0);
                getCurrentInputConnection().commitText(text, 1);
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
                        currentText = currentText.substring(0, currentText.length() - 1);
                        updateDisplay(currentText);
                        if (getCurrentInputConnection() != null)
                            getCurrentInputConnection().deleteSurroundingText(1, 0);
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
            }
        });
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void confirmInput() {
        if (getCurrentInputConnection() != null)
            getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_DONE);
        if (wsServer != null) wsServer.broadcastAction("confirmed");
        requestHideSelf(0);
    }

    private void clearInput() {
        currentText = "";
        updateDisplay("");
        if (getCurrentInputConnection() != null)
            getCurrentInputConnection().deleteSurroundingText(9999, 0);
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
        if (wsServer   != null) try { wsServer.stop(500);  } catch (Exception ignored) {}
        if (httpServer != null) httpServer.stop();
    }
}
