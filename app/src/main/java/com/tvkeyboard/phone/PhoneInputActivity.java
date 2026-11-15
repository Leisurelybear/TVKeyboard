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
import android.app.AlertDialog;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;

import com.tvkeyboard.R;
import com.tvkeyboard.common.PhoneWebSocketClient;

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
    private Button btnVolumeDown, btnVolumeUp;
    private Button btnMenu;
    private Button btnHistory;
    private Button btnDpadUp, btnDpadDown, btnDpadLeft, btnDpadRight, btnDpadCenter;
    private View layoutConnected;
    private View layoutDisconnected;

    private PhoneWebSocketClient wsClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String lastIp;
    private String lastToken;
    private int lastPort;
    private int reconnectGeneration;
    private final Runnable reconnectTask = () -> {
        if (lastIp != null && !isFinishing()) connectToTv(lastIp, lastPort, lastToken);
    };

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
        btnVolumeDown    = findViewById(R.id.btn_volume_down);
        btnVolumeUp      = findViewById(R.id.btn_volume_up);
        btnMenu          = findViewById(R.id.btn_menu);
        btnHistory       = findViewById(R.id.btn_history);
        btnDpadUp        = findViewById(R.id.btn_dpad_up);
        btnDpadDown      = findViewById(R.id.btn_dpad_down);
        btnDpadLeft      = findViewById(R.id.btn_dpad_left);
        btnDpadRight     = findViewById(R.id.btn_dpad_right);
        btnDpadCenter    = findViewById(R.id.btn_dpad_center);
        layoutConnected  = findViewById(R.id.layout_connected);
        layoutDisconnected = findViewById(R.id.layout_disconnected);

        showDisconnected();
        btnHistory.setOnClickListener(v -> showHistory());

        // 扫码连接
        btnScan.setOnClickListener(v ->
                startActivityForResult(new Intent(this, QrScanActivity.class), REQUEST_QR_SCAN));

        // 确认输入
        btnConfirm.setOnClickListener(v -> {
            saveHistory(etInput.getText().toString());
            sendAction("confirm");
        });

        // 取消
        btnDiscard.setOnClickListener(v -> {
            sendAction("dismiss");
            finish();
        });

        // 退格（本地 + 同步）
        btnBackspace.setOnClickListener(v -> {
            String text = etInput.getText().toString();
            if (!text.isEmpty()) {
                text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
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
        btnVolumeDown.setOnClickListener(v -> sendAction("volume_down"));
        btnVolumeUp.setOnClickListener(v -> sendAction("volume_up"));
        btnMenu.setOnClickListener(v -> sendAction("menu"));

        // 方向键
        btnDpadUp.setOnClickListener(v    -> sendAction("dpad_up"));
        btnDpadDown.setOnClickListener(v  -> sendAction("dpad_down"));
        btnDpadLeft.setOnClickListener(v  -> sendAction("dpad_left"));
        btnDpadRight.setOnClickListener(v -> sendAction("dpad_right"));
        // OK键 = 确认输入
        btnDpadCenter.setOnClickListener(v -> {
            saveHistory(etInput.getText().toString());
            sendAction("confirm");
        });

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

    private void saveHistory(String text) {
        if (text == null || text.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("phone_input", MODE_PRIVATE);
        List<String> values = new ArrayList<>(Arrays.asList(prefs.getString("history", "").split("\\u0001", -1)));
        values.remove("");
        values.remove(text);
        values.add(0, text);
        if (values.size() > 20) values = new ArrayList<>(values.subList(0, 20));
        prefs.edit().putString("history", joinHistory(values)).apply();
    }

    private String joinHistory(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append('\u0001');
            out.append(value.replace("\u0001", " "));
        }
        return out.toString();
    }

    private void showHistory() {
        String raw = getSharedPreferences("phone_input", MODE_PRIVATE).getString("history", "");
        if (raw.isEmpty()) {
            Toast.makeText(this, "暂无历史输入", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = raw.split("\\u0001", -1);
        new AlertDialog.Builder(this)
                .setTitle("历史输入")
                .setItems(items, (dialog, which) -> {
                    etInput.setText(items[which]);
                    etInput.setSelection(etInput.length());
                })
                .setNegativeButton("关闭", null)
                .show();
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
        int port = 8765;
        try { port = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
        String token = parts.length > 2 ? parts[2] : "";
        connectToTv(ip, port, token);
    }

    private void connectToTv(String ip, int port, String token) {
        lastIp = ip;
        lastPort = port;
        lastToken = token;
        final int generation = ++reconnectGeneration;

        tvStatus.setText("连接中... " + ip);
        final String fIp = ip;
        final int fPort = port;
        new Thread(() -> {
            try {
                if (wsClient != null) try { wsClient.close(); } catch (Exception ignored) {}
                final PhoneWebSocketClient[] holder = new PhoneWebSocketClient[1];
                PhoneWebSocketClient client = new PhoneWebSocketClient(fIp, fPort, token,
                        new PhoneWebSocketClient.Listener() {
                            private boolean active() { return holder[0] == wsClient && generation == reconnectGeneration; }
                            @Override public void onConnected() { if (active()) PhoneInputActivity.this.onConnected(); }
                            @Override public void onDisconnected(String reason) { if (active()) PhoneInputActivity.this.onDisconnected(reason); }
                            @Override public void onSyncReceived(String text) { if (active()) PhoneInputActivity.this.onSyncReceived(text); }
                            @Override public void onSessionsUpdate(int count) { if (active()) PhoneInputActivity.this.onSessionsUpdate(count); }
                            @Override public void onActionReceived(String action) { if (active()) PhoneInputActivity.this.onActionReceived(action); }
                            @Override public void onError(String message) { if (active()) PhoneInputActivity.this.onError(message); }
                        });
                holder[0] = client;
                wsClient = client;
                if (!client.connectBlocking(8, TimeUnit.SECONDS) && generation == reconnectGeneration) {
                    mainHandler.post(() -> scheduleReconnect(generation));
                }
                if (generation != reconnectGeneration && client.isOpen()) client.close();
            } catch (Exception e) {
                if (generation == reconnectGeneration) {
                    mainHandler.post(() -> {
                        scheduleReconnect(generation);
                    });
                }
            }
        }).start();
    }

    // ---- WebSocket 回调 ----

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(reconnectTask);
            showConnected();
            String text = etInput.getText().toString();
            if (!text.isEmpty() && wsClient != null) wsClient.sendText(text);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        mainHandler.post(() -> {
            showDisconnected();
            scheduleReconnect(reconnectGeneration);
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
        mainHandler.post(() -> {
            if (!isFinishing() && lastIp != null) scheduleReconnect(reconnectGeneration);
        });
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

    private void scheduleReconnect(int generation) {
        if (generation != reconnectGeneration || lastIp == null || isFinishing()) return;
        tvStatus.setText("连接失败，3 秒后重试");
        mainHandler.removeCallbacks(reconnectTask);
        mainHandler.postDelayed(reconnectTask, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reconnectGeneration++;
        mainHandler.removeCallbacks(reconnectTask);
        if (wsClient != null) try { wsClient.close(); } catch (Exception ignored) {}
    }
}
