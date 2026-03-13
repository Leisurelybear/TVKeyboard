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
import com.tvkeyboard.common.TvHttpServer;
import com.tvkeyboard.common.TvWebSocketServer;

/**
 * TV standalone activity (App mode, not IME).
 * Hosts both HTTP + WebSocket servers.
 * Phone scans QR → opens browser → types → text syncs here.
 */
public class TvInputActivity extends AppCompatActivity implements TvWebSocketServer.Listener {

    private ImageView ivQrCode;
    private TextView tvInputText, tvPlaceholder, tvClientStatus, tvIpAddress, tvUrl;
    private Button btnConfirm, btnDismiss, btnClear;

    private TvWebSocketServer wsServer;
    private TvHttpServer httpServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_input);

        ivQrCode       = findViewById(R.id.iv_qr_code);
        tvInputText    = findViewById(R.id.tv_input_text);
        tvPlaceholder  = findViewById(R.id.tv_placeholder);
        tvClientStatus = findViewById(R.id.tv_client_status);
        tvIpAddress    = findViewById(R.id.tv_ip_address);
        tvUrl          = findViewById(R.id.tv_url);
        btnConfirm     = findViewById(R.id.btn_confirm);
        btnDismiss     = findViewById(R.id.btn_dismiss);
        btnClear       = findViewById(R.id.btn_clear);

        btnConfirm.setOnClickListener(v -> onConfirm());
        btnDismiss.setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> onClear());

        startServers();
    }

    private void startServers() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        String url = NetworkUtils.buildQrContent(ip, TvHttpServer.HTTP_PORT);

        tvIpAddress.setText(ip);
        if (tvUrl != null) tvUrl.setText(url);

        int sizePx = (int) (220 * getResources().getDisplayMetrics().density);
        Bitmap bmp = QrCodeGenerator.generate(url, sizePx);
        if (bmp != null) ivQrCode.setImageBitmap(bmp);

        wsServer = new TvWebSocketServer(TvWebSocketServer.DEFAULT_PORT, this);
        wsServer.setConnectionLostTimeout(30);
        try { wsServer.start(); } catch (Exception e) {
            Toast.makeText(this, "WebSocket 启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        httpServer = new TvHttpServer(TvWebSocketServer.DEFAULT_PORT);
        try { httpServer.start(); } catch (Exception e) {
            Toast.makeText(this, "HTTP 服务启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- WebSocket callbacks ----

    @Override
    public void onClientConnected(String sessionId, String clientIp) {
        mainHandler.post(() -> {
            int count = wsServer != null ? wsServer.getConnectedClientCount() : 1;
            tvClientStatus.setText(count + " 人已连接 ✓");
            tvClientStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        });
    }

    @Override
    public void onClientDisconnected(String sessionId, int remaining) {
        mainHandler.post(() -> {
            if (remaining == 0) {
                tvClientStatus.setText("等待扫码连接...");
                tvClientStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            } else {
                tvClientStatus.setText(remaining + " 人已连接");
            }
        });
    }

    @Override
    public void onTextReceived(String text, String sessionId) {
        currentText = text;
        mainHandler.post(() -> updateInputDisplay(text));
    }

    @Override
    public void onActionReceived(String action, String sessionId) {
        mainHandler.post(() -> {
            switch (action) {
                case "confirm":   onConfirm(); break;
                case "backspace":
                    if (!currentText.isEmpty()) {
                        currentText = currentText.substring(0, currentText.length() - 1);
                        updateInputDisplay(currentText);
                        if (wsServer != null) wsServer.broadcastSync(currentText, "tv");
                    }
                    break;
                case "clear":   onClear(); break;
                case "dismiss": finish(); break;
            }
        });
    }

    private void onConfirm() {
        Toast.makeText(this, "已确认: " + currentText, Toast.LENGTH_SHORT).show();
        if (wsServer != null) wsServer.broadcastAction("confirmed");
    }

    private void onClear() {
        currentText = "";
        updateInputDisplay("");
        if (wsServer != null) {
            wsServer.broadcastSync("", "tv");
            wsServer.broadcastAction("cleared");
        }
    }

    private void updateInputDisplay(String text) {
        boolean empty = text == null || text.isEmpty();
        tvInputText.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvPlaceholder.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) tvInputText.setText(text);
        btnConfirm.setEnabled(!empty);
        btnClear.setEnabled(!empty);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wsServer != null) try { wsServer.stop(1000); } catch (Exception ignored) {}
        if (httpServer != null) httpServer.stop();
    }
}
