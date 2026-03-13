package com.tvkeyboard.tv;

import android.graphics.Bitmap;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.tvkeyboard.R;
import com.tvkeyboard.common.NetworkUtils;
import com.tvkeyboard.common.QrCodeGenerator;
import com.tvkeyboard.common.TvHttpServer;
import com.tvkeyboard.common.TvWebSocketServer;

/**
 * TV IME panel — half-screen, serves web page via HTTP so phone needs no app.
 * Multi-user: shows connected count; last writer wins.
 */
public class TvInputMethodService extends InputMethodService implements TvWebSocketServer.Listener {

    private TvWebSocketServer wsServer;
    private TvHttpServer httpServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentText = "";

    private View inputView;
    private ImageView ivQrCode;
    private TextView tvInputText;
    private TextView tvPlaceholder;
    private TextView tvClientStatus;
    private TextView tvIpAddress;
    private Button btnConfirm;
    private Button btnDismiss;
    private Button btnClear;

    @Override
    public void onCreate() {
        super.onCreate();
        startServers();
    }

    private void startServers() {
        String ip = NetworkUtils.getLocalIpAddress(this);

        // WebSocket server for real-time sync
        wsServer = new TvWebSocketServer(TvWebSocketServer.DEFAULT_PORT, this);
        wsServer.setConnectionLostTimeout(30);
        try { wsServer.start(); } catch (Exception e) { e.printStackTrace(); }

        // HTTP server for serving the web input page
        httpServer = new TvHttpServer(TvWebSocketServer.DEFAULT_PORT);
        try { httpServer.start(); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public View onCreateInputView() {
        inputView = LayoutInflater.from(this).inflate(R.layout.ime_tv_panel, null);

        // ---- KEY FIX: constrain height to ~40% of screen = half-screen feel ----
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        inputView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (screenHeight * 0.42f)
        ));

        ivQrCode      = inputView.findViewById(R.id.iv_qr_code);
        tvInputText   = inputView.findViewById(R.id.tv_input_text);
        tvPlaceholder = inputView.findViewById(R.id.tv_placeholder);
        tvClientStatus= inputView.findViewById(R.id.tv_client_status);
        tvIpAddress   = inputView.findViewById(R.id.tv_ip_address);
        btnConfirm    = inputView.findViewById(R.id.btn_confirm);
        btnDismiss    = inputView.findViewById(R.id.btn_dismiss);
        btnClear      = inputView.findViewById(R.id.btn_clear);

        setupQrCode();
        btnConfirm.setOnClickListener(v -> confirmInput());
        btnDismiss.setOnClickListener(v -> requestHideSelf(0));
        btnClear.setOnClickListener(v -> clearInput());

        return inputView;
    }

    private void setupQrCode() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        // QR points to HTTP web page — no app needed on phone
        String url = NetworkUtils.buildQrContent(ip, TvHttpServer.HTTP_PORT);
        tvIpAddress.setText(url);

        int sizePx = (int) (160 * getResources().getDisplayMetrics().density);
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

    // ---- WebSocket callbacks (new interface with sessionId) ----

    @Override
    public void onClientConnected(String sessionId, String clientIp) {
        mainHandler.post(() -> {
            if (tvClientStatus == null) return;
            int count = wsServer != null ? wsServer.getConnectedClientCount() : 1;
            tvClientStatus.setText(count == 1
                    ? "1 人已连接 ✓"
                    : count + " 人已连接 ✓");
            tvClientStatus.setTextColor(0xFF00AA00);
        });
    }

    @Override
    public void onClientDisconnected(String sessionId, int remaining) {
        mainHandler.post(() -> {
            if (tvClientStatus == null) return;
            if (remaining == 0) {
                tvClientStatus.setText("等待扫码连接...");
                tvClientStatus.setTextColor(0xFF888888);
            } else {
                tvClientStatus.setText(remaining + " 人已连接");
                tvClientStatus.setTextColor(0xFF00AA00);
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
                case "confirm": confirmInput(); break;
                case "backspace":
                    if (!currentText.isEmpty()) {
                        currentText = currentText.substring(0, currentText.length() - 1);
                        updateDisplay(currentText);
                        if (getCurrentInputConnection() != null)
                            getCurrentInputConnection().deleteSurroundingText(1, 0);
                        if (wsServer != null) wsServer.broadcastSync(currentText, "tv");
                    }
                    break;
                case "clear":   clearInput(); break;
                case "dismiss": requestHideSelf(0); break;
            }
        });
    }

    // ---- Actions ----

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
        if (btnConfirm != null) btnConfirm.setEnabled(!empty);
        if (btnClear != null) btnClear.setEnabled(!empty);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wsServer != null) try { wsServer.stop(500); } catch (Exception ignored) {}
        if (httpServer != null) httpServer.stop();
    }
}
