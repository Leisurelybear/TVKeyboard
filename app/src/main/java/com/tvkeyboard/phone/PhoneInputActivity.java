package com.tvkeyboard.phone;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tvkeyboard.R;
import com.tvkeyboard.common.PhoneWebSocketClient;
import com.tvkeyboard.common.TvWebSocketServer;

/**
 * Phone-side input screen.
 * After connecting to TV, user types here and input is synced to TV in real time.
 */
public class PhoneInputActivity extends AppCompatActivity implements PhoneWebSocketClient.Listener {

    private static final int REQUEST_QR_SCAN = 100;

    private EditText etInput;
    private TextView tvStatus;
    private TextView tvTvPreview;
    private Button btnConfirm;
    private Button btnDiscard;
    private ImageButton btnBackspace;
    private ImageButton btnClear;
    private Button btnScan;
    private View layoutConnected;
    private View layoutDisconnected;

    private PhoneWebSocketClient wsClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isConnecting = false;
    private String pendingText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_input);

        etInput = findViewById(R.id.et_input);
        tvStatus = findViewById(R.id.tv_status);
        tvTvPreview = findViewById(R.id.tv_tv_preview);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnDiscard = findViewById(R.id.btn_discard);
        btnBackspace = findViewById(R.id.btn_backspace);
        btnClear = findViewById(R.id.btn_clear);
        btnScan = findViewById(R.id.btn_scan);
        layoutConnected = findViewById(R.id.layout_connected);
        layoutDisconnected = findViewById(R.id.layout_disconnected);

        showDisconnected();

        btnScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, QrScanActivity.class);
            startActivityForResult(intent, REQUEST_QR_SCAN);
        });

        btnConfirm.setOnClickListener(v -> {
            if (wsClient != null) wsClient.sendAction("confirm");
            Toast.makeText(this, "已发送确认", Toast.LENGTH_SHORT).show();
        });

        btnDiscard.setOnClickListener(v -> {
            if (wsClient != null) wsClient.sendAction("dismiss");
            finish();
        });

        btnBackspace.setOnClickListener(v -> {
            String text = etInput.getText().toString();
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                etInput.setText(text);
                etInput.setSelection(text.length());
            }
        });

        btnClear.setOnClickListener(v -> {
            etInput.setText("");
        });

        // Real-time text sync
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (wsClient != null && wsClient.isOpen()) {
                    wsClient.sendText(text);
                }
                btnConfirm.setEnabled(!text.isEmpty());
                btnClear.setEnabled(!text.isEmpty());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK && data != null) {
            String qrContent = data.getStringExtra(QrScanActivity.EXTRA_QR_RESULT);
            connectToTv(qrContent);
        }
    }

    private void connectToTv(String qrContent) {
        com.tvkeyboard.common.NetworkUtils nu = null;
        String[] parts = com.tvkeyboard.common.NetworkUtils.parseQrContent(qrContent);
        if (parts == null) {
            Toast.makeText(this, "无效的二维码", Toast.LENGTH_SHORT).show();
            return;
        }
        String ip = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            port = TvWebSocketServer.DEFAULT_PORT;
        }

        tvStatus.setText("连接中... " + ip);
        isConnecting = true;

        final String finalIp = ip;
        final int finalPort = port;
        new Thread(() -> {
            try {
                wsClient = new PhoneWebSocketClient(finalIp, finalPort, this);
                wsClient.connectBlocking();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("连接失败: " + e.getMessage());
                    isConnecting = false;
                });
            }
        }).start();
    }

    // ---- WebSocket callbacks ----

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            isConnecting = false;
            showConnected();
            // Send any pending text
            String text = etInput.getText().toString();
            if (!text.isEmpty() && wsClient != null) {
                wsClient.sendText(text);
            }
        });
    }

    @Override
    public void onDisconnected(String reason) {
        mainHandler.post(() -> {
            showDisconnected();
            Toast.makeText(this, "连接断开", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onSyncReceived(String text) {
        // TV echoed back current text – could update preview
        mainHandler.post(() -> tvTvPreview.setText("电视显示: " + text));
    }

    @Override
    public void onActionReceived(String action) {
        mainHandler.post(() -> {
            if ("confirmed".equals(action)) {
                Toast.makeText(this, "电视已确认输入 ✓", Toast.LENGTH_SHORT).show();
            } else if ("dismissed".equals(action)) {
                Toast.makeText(this, "电视已关闭输入法", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    @Override
    public void onError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "错误: " + message, Toast.LENGTH_SHORT).show());
    }

    // ---- UI state ----

    private void showConnected() {
        layoutDisconnected.setVisibility(View.GONE);
        layoutConnected.setVisibility(View.VISIBLE);
        tvStatus.setText("已连接到电视 ✓");
        etInput.requestFocus();
    }

    private void showDisconnected() {
        layoutDisconnected.setVisibility(View.VISIBLE);
        layoutConnected.setVisibility(View.GONE);
        tvStatus.setText("未连接");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wsClient != null) {
            try {
                wsClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
