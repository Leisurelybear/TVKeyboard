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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tvkeyboard.R;
import com.tvkeyboard.common.PhoneWebSocketClient;
import com.tvkeyboard.common.TvWebSocketServer;

public class PhoneInputActivity extends AppCompatActivity implements PhoneWebSocketClient.Listener {

    private static final int REQUEST_QR_SCAN = 100;

    private EditText etInput;
    private TextView tvStatus;
    private TextView tvTvPreview;
    private Button btnConfirm;
    private Button btnDiscard;
    private Button btnBackspace;
    private Button btnClear;
    private Button btnScan;
    private Button btnBack;
    private Button btnDpadUp, btnDpadDown, btnDpadLeft, btnDpadRight, btnDpadCenter;
    private View layoutConnected;
    private View layoutDisconnected;

    private PhoneWebSocketClient wsClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_input);

        etInput          = findViewById(R.id.et_input);
        tvStatus         = findViewById(R.id.tv_status);
        tvTvPreview      = findViewById(R.id.tv_tv_preview);
        btnConfirm       = findViewById(R.id.btn_confirm);
        btnDiscard       = findViewById(R.id.btn_discard);
        btnBackspace     = findViewById(R.id.btn_backspace);
        btnClear         = findViewById(R.id.btn_clear);
        btnScan          = findViewById(R.id.btn_scan);
        btnBack          = findViewById(R.id.btn_back);
        btnDpadUp        = findViewById(R.id.btn_dpad_up);
        btnDpadDown      = findViewById(R.id.btn_dpad_down);
        btnDpadLeft      = findViewById(R.id.btn_dpad_left);
        btnDpadRight     = findViewById(R.id.btn_dpad_right);
        btnDpadCenter    = findViewById(R.id.btn_dpad_center);
        layoutConnected  = findViewById(R.id.layout_connected);
        layoutDisconnected = findViewById(R.id.layout_disconnected);

        showDisconnected();

        // 扫码连接
        btnScan.setOnClickListener(v ->
                startActivityForResult(new Intent(this, QrScanActivity.class), REQUEST_QR_SCAN));

        // 确认输入
        btnConfirm.setOnClickListener(v -> sendAction("confirm"));

        // 取消
        btnDiscard.setOnClickListener(v -> {
            sendAction("dismiss");
            finish();
        });

        // 退格（本地 + 同步）
        btnBackspace.setOnClickListener(v -> {
            String text = etInput.getText().toString();
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                etInput.setText(text);
                etInput.setSelection(text.length());
                // 同步给电视（TextWatcher 会触发）
            } else {
                // 输入框已空，让电视执行退格
                sendAction("backspace");
            }
        });

        // 清空
        btnClear.setOnClickListener(v -> {
            etInput.setText("");
            sendAction("clear");
        });

        // 返回键 → 电视执行返回
        btnBack.setOnClickListener(v -> sendAction("back"));

        // 方向键
        btnDpadUp.setOnClickListener(v    -> sendAction("dpad_up"));
        btnDpadDown.setOnClickListener(v  -> sendAction("dpad_down"));
        btnDpadLeft.setOnClickListener(v  -> sendAction("dpad_left"));
        btnDpadRight.setOnClickListener(v -> sendAction("dpad_right"));
        // OK键 = 确认输入
        btnDpadCenter.setOnClickListener(v -> sendAction("confirm"));

        // 实时同步文字
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
            }
        });
    }

    private void sendAction(String action) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.sendAction(action);
        } else {
            Toast.makeText(this, "未连接到电视", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK && data != null) {
            connectToTv(data.getStringExtra(QrScanActivity.EXTRA_QR_RESULT));
        }
    }

    private void connectToTv(String qrContent) {
        String[] parts = com.tvkeyboard.common.NetworkUtils.parseQrContent(qrContent);
        if (parts == null) {
            Toast.makeText(this, "无效的二维码", Toast.LENGTH_SHORT).show();
            return;
        }
        String ip = parts[0];
        int port;
        try { port = Integer.parseInt(parts[1]); }
        catch (Exception e) { port = TvWebSocketServer.DEFAULT_PORT; }

        tvStatus.setText("连接中... " + ip);
        final String fIp = ip;
        final int fPort = port;
        new Thread(() -> {
            try {
                wsClient = new PhoneWebSocketClient(fIp, fPort, this);
                wsClient.connectBlocking();
            } catch (Exception e) {
                mainHandler.post(() -> tvStatus.setText("连接失败: " + e.getMessage()));
            }
        }).start();
    }

    // ---- WebSocket 回调 ----

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            showConnected();
            String text = etInput.getText().toString();
            if (!text.isEmpty() && wsClient != null) wsClient.sendText(text);
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
        mainHandler.post(() -> tvTvPreview.setText("电视显示: " + text));
    }

    @Override
    public void onActionReceived(String action) {
        mainHandler.post(() -> {
            switch (action) {
                case "confirmed":
                    Toast.makeText(this, "电视已确认 ✓", Toast.LENGTH_SHORT).show();
                    break;
                case "dismissed":
                    finish();
                    break;
            }
        });
    }

    @Override
    public void onError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "错误: " + message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onSessionsUpdate(int count) {}

    private void showConnected() {
        layoutDisconnected.setVisibility(View.GONE);
        layoutConnected.setVisibility(View.VISIBLE);
        tvStatus.setText("已连接 ✓");
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
        if (wsClient != null) try { wsClient.close(); } catch (Exception ignored) {}
    }
}
