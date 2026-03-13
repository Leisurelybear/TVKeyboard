package com.tvkeyboard.tv;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tvkeyboard.R;
import com.tvkeyboard.common.NetworkUtils;
import com.tvkeyboard.common.QrCodeGenerator;
import com.tvkeyboard.common.TvWebSocketServer;

/**
 * TV-side input screen.
 * Shows QR code for phone to scan, displays real-time typed text,
 * and provides Confirm / Dismiss buttons.
 */
public class TvInputActivity extends AppCompatActivity implements TvWebSocketServer.Listener {

    private static final int QR_SIZE_DP = 240;

    private ImageView ivQrCode;
    private TextView tvInputText;
    private TextView tvPlaceholder;
    private TextView tvClientStatus;
    private TextView tvIpAddress;
    private Button btnConfirm;
    private Button btnDismiss;
    private Button btnClear;

    private TvWebSocketServer wsServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_input);

        ivQrCode = findViewById(R.id.iv_qr_code);
        tvInputText = findViewById(R.id.tv_input_text);
        tvPlaceholder = findViewById(R.id.tv_placeholder);
        tvClientStatus = findViewById(R.id.tv_client_status);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnDismiss = findViewById(R.id.btn_dismiss);
        btnClear = findViewById(R.id.btn_clear);

        btnConfirm.setOnClickListener(v -> onConfirm());
        btnDismiss.setOnClickListener(v -> onDismiss());
        btnClear.setOnClickListener(v -> onClear());

        startServer();
    }

    private void startServer() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        int port = TvWebSocketServer.DEFAULT_PORT;

        tvIpAddress.setText(ip + ":" + port);

        // Generate QR code
        String qrContent = NetworkUtils.buildQrContent(ip, port);
        int sizePx = (int) (QR_SIZE_DP * getResources().getDisplayMetrics().density);
        Bitmap qrBitmap = QrCodeGenerator.generate(qrContent, sizePx);
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap);
        }

        // Start WebSocket server
        wsServer = new TvWebSocketServer(port, this);
        wsServer.setConnectionLostTimeout(30);
        try {
            wsServer.start();
        } catch (Exception e) {
            Toast.makeText(this, "服务启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- WebSocket callbacks ----

    @Override
    public void onClientConnected(String clientIp) {
        mainHandler.post(() -> {
            tvClientStatus.setText("手机已连接: " + clientIp);
            tvClientStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        });
    }

    @Override
    public void onClientDisconnected(String clientIp) {
        mainHandler.post(() -> {
            int count = wsServer != null ? wsServer.getConnectedClientCount() : 0;
            if (count == 0) {
                tvClientStatus.setText("等待手机连接...");
                tvClientStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        });
    }

    @Override
    public void onTextReceived(String text) {
        currentText = text;
        mainHandler.post(() -> updateInputDisplay(text));
    }

    @Override
    public void onActionReceived(String action) {
        mainHandler.post(() -> {
            switch (action) {
                case "confirm":
                    onConfirm();
                    break;
                case "backspace":
                    if (!currentText.isEmpty()) {
                        currentText = currentText.substring(0, currentText.length() - 1);
                        updateInputDisplay(currentText);
                        if (wsServer != null) wsServer.broadcastCurrentText(currentText);
                    }
                    break;
                case "clear":
                    onClear();
                    break;
                case "dismiss":
                    onDismiss();
                    break;
            }
        });
    }

    // ---- Button handlers ----

    private void onConfirm() {
        // In IME mode this would commit text; here we just show a toast
        Toast.makeText(this, "已确认: " + currentText, Toast.LENGTH_SHORT).show();
        if (wsServer != null) wsServer.broadcastAction("confirmed");
        // Optionally clear after confirm
        // currentText = "";
        // updateInputDisplay("");
    }

    private void onClear() {
        currentText = "";
        updateInputDisplay("");
        if (wsServer != null) wsServer.broadcastCurrentText("");
    }

    private void onDismiss() {
        if (wsServer != null) wsServer.broadcastAction("dismissed");
        finish();
    }

    // ---- UI update ----

    private void updateInputDisplay(String text) {
        if (text == null || text.isEmpty()) {
            tvInputText.setVisibility(View.GONE);
            tvPlaceholder.setVisibility(View.VISIBLE);
        } else {
            tvInputText.setVisibility(View.VISIBLE);
            tvPlaceholder.setVisibility(View.GONE);
            tvInputText.setText(text);
        }

        // Show cursor blink effect
        btnConfirm.setEnabled(!text.isEmpty());
        btnClear.setEnabled(!text.isEmpty());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
