package com.tvkeyboard.tv;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.tvkeyboard.R;
import com.tvkeyboard.common.NetworkUtils;
import com.tvkeyboard.common.QrCodeGenerator;
import com.tvkeyboard.common.TvWebSocketServer;

import android.graphics.Bitmap;

/**
 * TV Input Method Service.
 * When a text field is focused on TV, this IME shows a panel with:
 * - QR code for phone to connect
 * - Real-time display of text being typed on phone
 * - Confirm / Dismiss / Clear buttons
 */
public class TvInputMethodService extends InputMethodService implements TvWebSocketServer.Listener {

    private TvWebSocketServer wsServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentText = "";

    // IME panel views
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
        startWsServer();
    }

    private void startWsServer() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        int port = TvWebSocketServer.DEFAULT_PORT;

        wsServer = new TvWebSocketServer(port, this);
        wsServer.setConnectionLostTimeout(30);
        try {
            wsServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public View onCreateInputView() {
        inputView = LayoutInflater.from(this).inflate(R.layout.ime_tv_panel, null);

        ivQrCode = inputView.findViewById(R.id.iv_qr_code);
        tvInputText = inputView.findViewById(R.id.tv_input_text);
        tvPlaceholder = inputView.findViewById(R.id.tv_placeholder);
        tvClientStatus = inputView.findViewById(R.id.tv_client_status);
        tvIpAddress = inputView.findViewById(R.id.tv_ip_address);
        btnConfirm = inputView.findViewById(R.id.btn_confirm);
        btnDismiss = inputView.findViewById(R.id.btn_dismiss);
        btnClear = inputView.findViewById(R.id.btn_clear);

        setupQrCode();

        btnConfirm.setOnClickListener(v -> confirmInput());
        btnDismiss.setOnClickListener(v -> requestHideSelf(0));
        btnClear.setOnClickListener(v -> clearInput());

        return inputView;
    }

    private void setupQrCode() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        int port = TvWebSocketServer.DEFAULT_PORT;

        tvIpAddress.setText(ip + ":" + port);

        String qrContent = NetworkUtils.buildQrContent(ip, port);
        int sizePx = (int) (200 * getResources().getDisplayMetrics().density);
        Bitmap qrBitmap = QrCodeGenerator.generate(qrContent, sizePx);
        if (qrBitmap != null && ivQrCode != null) {
            ivQrCode.setImageBitmap(qrBitmap);
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentText = "";
        updateDisplay("");
        if (wsServer != null) wsServer.broadcastCurrentText("");
    }

    // ---- WebSocket callbacks ----

    @Override
    public void onClientConnected(String clientIp) {
        mainHandler.post(() -> {
            if (tvClientStatus != null) {
                tvClientStatus.setText("手机已连接 ✓");
                tvClientStatus.setTextColor(0xFF00AA00);
            }
        });
    }

    @Override
    public void onClientDisconnected(String clientIp) {
        mainHandler.post(() -> {
            if (tvClientStatus != null) {
                int count = wsServer != null ? wsServer.getConnectedClientCount() : 0;
                if (count == 0) {
                    tvClientStatus.setText("等待手机连接...");
                    tvClientStatus.setTextColor(0xFF888888);
                }
            }
        });
    }

    @Override
    public void onTextReceived(String text) {
        currentText = text;
        mainHandler.post(() -> {
            updateDisplay(text);
            // Sync to input field
            if (getCurrentInputConnection() != null) {
                getCurrentInputConnection().deleteSurroundingText(9999, 0);
                getCurrentInputConnection().commitText(text, 1);
            }
        });
    }

    @Override
    public void onActionReceived(String action) {
        mainHandler.post(() -> {
            switch (action) {
                case "confirm":
                    confirmInput();
                    break;
                case "backspace":
                    if (!currentText.isEmpty()) {
                        currentText = currentText.substring(0, currentText.length() - 1);
                        updateDisplay(currentText);
                        if (getCurrentInputConnection() != null) {
                            getCurrentInputConnection().deleteSurroundingText(1, 0);
                        }
                        if (wsServer != null) wsServer.broadcastCurrentText(currentText);
                    }
                    break;
                case "clear":
                    clearInput();
                    break;
                case "dismiss":
                    requestHideSelf(0);
                    break;
            }
        });
    }

    // ---- Actions ----

    private void confirmInput() {
        if (getCurrentInputConnection() != null) {
            getCurrentInputConnection().performEditorAction(EditorInfo.IME_ACTION_DONE);
        }
        if (wsServer != null) wsServer.broadcastAction("confirmed");
        requestHideSelf(0);
    }

    private void clearInput() {
        currentText = "";
        updateDisplay("");
        if (getCurrentInputConnection() != null) {
            getCurrentInputConnection().deleteSurroundingText(9999, 0);
        }
        if (wsServer != null) wsServer.broadcastCurrentText("");
    }

    private void updateDisplay(String text) {
        if (tvInputText == null || tvPlaceholder == null) return;
        if (text == null || text.isEmpty()) {
            tvInputText.setVisibility(View.GONE);
            tvPlaceholder.setVisibility(View.VISIBLE);
        } else {
            tvInputText.setVisibility(View.VISIBLE);
            tvPlaceholder.setVisibility(View.GONE);
            tvInputText.setText(text);
        }
        if (btnConfirm != null) btnConfirm.setEnabled(text != null && !text.isEmpty());
        if (btnClear != null) btnClear.setEnabled(text != null && !text.isEmpty());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wsServer != null) {
            try {
                wsServer.stop(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
