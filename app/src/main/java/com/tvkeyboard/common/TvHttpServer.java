package com.tvkeyboard.common;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP server that serves the web-based input page.
 * Phone users scan QR code → browser opens → type text → synced to TV via WebSocket.
 */
public class TvHttpServer {

    public static final int HTTP_PORT = 8766;
    private static final String TAG = "TvHttpServer";

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private volatile boolean running = false;
    private final int wsPort;
    private final String authToken;

    public TvHttpServer(int wsPort, String authToken) {
        this.wsPort = wsPort;
        this.authToken = authToken;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(HTTP_PORT));
        running = true;
        executor.execute(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(5000);
                    executor.execute(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) Log.e(TAG, "Accept error", e);
                }
            }
        });
        Log.i(TAG, "HTTP server started on port " + HTTP_PORT);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        executor.shutdownNow();
    }

    private void handleClient(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            if (authToken != null && !authToken.isEmpty()) {
                String[] requestParts = requestLine.split(" ");
                String target = requestParts.length > 1 ? requestParts[1] : "";
                int query = target.indexOf('?');
                String supplied = "";
                if (query >= 0) {
                    for (String item : target.substring(query + 1).split("&")) {
                        if (item.startsWith("token=")) supplied = item.substring(6);
                    }
                }
                if (!authToken.equals(supplied)) {
                    byte[] body = "Unauthorized".getBytes(StandardCharsets.UTF_8);
                    String response = "HTTP/1.1 401 Unauthorized\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = client.getOutputStream();
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.write(body);
                    out.flush();
                    return;
                }
            }

            // Read and discard headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}

            String html = buildHtmlPage(wsPort, authToken);
            byte[] body = html.getBytes(StandardCharsets.UTF_8);

            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html; charset=UTF-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";

            OutputStream out = client.getOutputStream();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Handle client error", e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Build the full HTML page with embedded JS WebSocket client.
     * The page connects to the TV's WebSocket server and syncs input in real time.
     */
    private String buildHtmlPage(int wsPort, String authToken) {
        return "<!DOCTYPE html>\n"
            + "<html lang=\"zh\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">\n"
            + "<title>TV 远程输入</title>\n"
            + "<style>\n"
            + "  * { box-sizing: border-box; margin: 0; padding: 0; }\n"
            + "  body {\n"
            + "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n"
            + "    background: #0f0f23;\n"
            + "    color: #fff;\n"
            + "    min-height: 100vh;\n"
            + "    display: flex;\n"
            + "    flex-direction: column;\n"
            + "  }\n"
            + "  .header {\n"
            + "    background: #1a1a35;\n"
            + "    padding: 16px 20px;\n"
            + "    display: flex;\n"
            + "    align-items: center;\n"
            + "    justify-content: space-between;\n"
            + "    border-bottom: 1px solid #333366;\n"
            + "  }\n"
            + "  .header h1 { font-size: 18px; font-weight: 600; }\n"
            + "  .status-dot {\n"
            + "    width: 10px; height: 10px;\n"
            + "    border-radius: 50%;\n"
            + "    background: #555;\n"
            + "    display: inline-block;\n"
            + "    margin-right: 6px;\n"
            + "    transition: background 0.3s;\n"
            + "  }\n"
            + "  .status-dot.connected { background: #4caf50; }\n"
            + "  .status-dot.error { background: #f44336; }\n"
            + "  #statusText { font-size: 13px; color: #aaa; }\n"
            + "  .main { flex: 1; padding: 20px; display: flex; flex-direction: column; gap: 16px; }\n"
            + "  .tv-preview {\n"
            + "    background: #12122a;\n"
            + "    border: 1px solid #2a2a55;\n"
            + "    border-radius: 10px;\n"
            + "    padding: 14px 16px;\n"
            + "  }\n"
            + "  .tv-preview label { font-size: 11px; color: #666688; display: block; margin-bottom: 6px; }\n"
            + "  #tvPreview {\n"
            + "    font-size: 18px;\n"
            + "    color: #9999cc;\n"
            + "    min-height: 28px;\n"
            + "    word-break: break-all;\n"
            + "  }\n"
            + "  .sessions-bar {\n"
            + "    font-size: 12px;\n"
            + "    color: #666688;\n"
            + "    text-align: center;\n"
            + "    padding: 6px;\n"
            + "    background: #1a1a35;\n"
            + "    border-radius: 6px;\n"
            + "  }\n"
            + "  .input-area {\n"
            + "    flex: 1;\n"
            + "    display: flex;\n"
            + "    flex-direction: column;\n"
            + "    gap: 12px;\n"
            + "  }\n"
            + "  textarea {\n"
            + "    flex: 1;\n"
            + "    width: 100%;\n"
            + "    min-height: 140px;\n"
            + "    background: #12122a;\n"
            + "    border: 1px solid #2a2a55;\n"
            + "    border-radius: 10px;\n"
            + "    color: #eeeeff;\n"
            + "    font-size: 20px;\n"
            + "    padding: 14px;\n"
            + "    resize: none;\n"
            + "    outline: none;\n"
            + "    line-height: 1.5;\n"
            + "  }\n"
            + "  textarea:focus { border-color: #5c6bc0; }\n"
            + "  textarea::placeholder { color: #444466; }\n"
            + "  .toolbar {\n"
            + "    display: flex;\n"
            + "    gap: 10px;\n"
            + "    align-items: center;\n"
            + "  }\n"
            + "  #historySelect {\n"
            + "    flex: 1; min-width: 90px; height: 46px; border: none; border-radius: 8px;\n"
            + "    background: #2a2a45; color: #ccc; padding: 0 10px; font-size: 14px;\n"
            + "  }\n"
            + "  .quick-phrases { display: flex; gap: 8px; flex-wrap: wrap; }\n"
            + "  .phrase-chip { border: 1px solid #3b3b66; background: #20203d; color: #ccc; border-radius: 16px; padding: 7px 11px; font-size: 13px; }\n"
            + "  .phrase-chip .remove { color: #888; margin-left: 5px; cursor: pointer; }\n"
            + "  .btn {\n"
            + "    border: none;\n"
            + "    border-radius: 8px;\n"
            + "    font-size: 15px;\n"
            + "    font-weight: 600;\n"
            + "    cursor: pointer;\n"
            + "    padding: 12px 16px;\n"
            + "    transition: opacity 0.2s, transform 0.1s;\n"
            + "    -webkit-tap-highlight-color: transparent;\n"
            + "  }\n"
            + "  .btn:active { transform: scale(0.96); opacity: 0.85; }\n"
            + "  .btn-icon {\n"
            + "    background: #2a2a45;\n"
            + "    color: #ccc;\n"
            + "    width: 46px;\n"
            + "    height: 46px;\n"
            + "    display: flex;\n"
            + "    align-items: center;\n"
            + "    justify-content: center;\n"
            + "    font-size: 18px;\n"
            + "    flex-shrink: 0;\n"
            + "  }\n"
            + "  .btn-confirm {\n"
            + "    background: #388e3c;\n"
            + "    color: #fff;\n"
            + "    flex: 1;\n"
            + "    height: 52px;\n"
            + "    font-size: 17px;\n"
            + "  }\n"
            + "  .btn-confirm:disabled { background: #2a4a2a; color: #666; }\n"
            + "  .btn-cancel { background: #b71c1c; color: #fff; height: 52px; }\n"
            + "  .btn-back { background: #37474f; color: #ccc; height: 52px; padding: 0 20px; }\n"
            + "  .actions { display: flex; gap: 10px; }\n"
            + "  .conflict-notice {\n"
            + "    display: none;\n"
            + "    background: #4a3000;\n"
            + "    border: 1px solid #ff9800;\n"
            + "    color: #ffcc02;\n"
            + "    border-radius: 8px;\n"
            + "    padding: 10px 14px;\n"
            + "    font-size: 13px;\n"
            + "    text-align: center;\n"
            + "  }\n"
            + "  .offline-notice {\n"
            + "    display: none;\n"
            + "    background: #2a0000;\n"
            + "    border: 1px solid #f44336;\n"
            + "    color: #ff8a80;\n"
            + "    border-radius: 8px;\n"
            + "    padding: 12px;\n"
            + "    text-align: center;\n"
            + "    font-size: 14px;\n"
            + "  }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"header\">\n"
            + "  <h1>📺 TV 远程输入</h1>\n"
            + "  <span><span class=\"status-dot\" id=\"dot\"></span><span id=\"statusText\">连接中...</span></span>\n"
            + "</div>\n"
            + "<div class=\"main\">\n"
            + "  <div class=\"offline-notice\" id=\"offlineNotice\">\n"
            + "    ⚠️ 与电视连接断开，请刷新页面重试\n"
            + "    <br><br><button class=\"btn btn-confirm\" onclick=\"location.reload()\">重新连接</button>\n"
            + "  </div>\n"
            + "  <div class=\"tv-preview\">\n"
            + "    <label>电视端实时显示</label>\n"
            + "    <div id=\"tvPreview\">（等待输入...）</div>\n"
            + "  </div>\n"
            + "  <div class=\"sessions-bar\" id=\"sessionsBar\">正在连接...</div>\n"
            + "  <div class=\"conflict-notice\" id=\"conflictNotice\">⚡ 另一个用户正在输入，你的内容将在他们完成后生效</div>\n"
            + "  <div class=\"input-area\">\n"
            + "    <div class=\"quick-phrases\" id=\"quickPhrases\"></div>\n"
            + "    <div class=\"toolbar\">\n"
            + "      <button class=\"btn btn-icon\" id=\"btnBackspace\" title=\"退格\">⌫</button>\n"
            + "      <button class=\"btn btn-icon\" id=\"btnClear\" title=\"清空\">🗑</button>\n"
            + "      <button class=\"btn btn-icon\" id=\"btnVolumeDown\" title=\"音量减\">🔉</button>\n"
            + "      <button class=\"btn btn-icon\" id=\"btnVolumeUp\" title=\"音量加\">🔊</button>\n"
            + "      <button class=\"btn btn-icon\" id=\"btnMenu\" title=\"菜单\">☰</button>\n"
            + "      <select id=\"historySelect\" title=\"历史输入\"><option value=\"\">历史输入</option></select>\n"
            + "    </div>\n"
            + "    <textarea id=\"inputBox\" placeholder=\"在此输入文字，实时同步到电视...\" autofocus></textarea>\n"
            + "    <div class=\"actions\">\n"
            + "      <button class=\"btn btn-back\" id=\"btnBack\" title=\"发送返回键给电视\">← 返回</button>\n"
            + "      <button class=\"btn btn-cancel\" id=\"btnCancel\">取消</button>\n"
            + "      <button class=\"btn btn-confirm\" id=\"btnConfirm\" disabled>✓ 确认发送</button>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "</div>\n"
            + "<script>\n"
            // Generate a unique session ID for this browser tab
            + "const sessionId = 'sess_' + Math.random().toString(36).slice(2, 10);\n"
            + "const wsPort = " + wsPort + ";\n"
            + "const authToken = '" + escapeHtmlJs(authToken) + "';\n"
            + "const wsHost = location.hostname;\n"
            + "let ws = null;\n"
            + "let reconnectTimer = null;\n"
            + "let lastSentText = '';\n"
            + "let isOtherTyping = false;\n"
            + "let lastConfirmAt = 0;\n"
            + "\n"
            + "const dot = document.getElementById('dot');\n"
            + "const statusText = document.getElementById('statusText');\n"
            + "const inputBox = document.getElementById('inputBox');\n"
            + "const tvPreview = document.getElementById('tvPreview');\n"
            + "const sessionsBar = document.getElementById('sessionsBar');\n"
            + "const conflictNotice = document.getElementById('conflictNotice');\n"
            + "const offlineNotice = document.getElementById('offlineNotice');\n"
            + "const btnConfirm = document.getElementById('btnConfirm');\n"
            + "const btnCancel = document.getElementById('btnCancel');"
            + "const btnBack = document.getElementById('btnBack');\n"
            + "const btnBackspace = document.getElementById('btnBackspace');\n"
            + "const btnClear = document.getElementById('btnClear');\n"
            + "const btnVolumeDown = document.getElementById('btnVolumeDown');\n"
            + "const btnVolumeUp = document.getElementById('btnVolumeUp');\n"
            + "const btnMenu = document.getElementById('btnMenu');\n"
            + "const historySelect = document.getElementById('historySelect');\n"
            + "const HISTORY_KEY = 'tvkeyboard_history_v1';\n"
            + "const MAX_HISTORY = 20;\n"
            + "const PHRASES_KEY = 'tvkeyboard_phrases_v1';\n"
            + "\n"
            + "function connect() {\n"
            + "  ws = new WebSocket('ws://' + wsHost + ':' + wsPort + '?token=' + encodeURIComponent(authToken));\n"
            + "  ws.onopen = () => {\n"
            + "    dot.className = 'status-dot connected';\n"
            + "    statusText.textContent = '已连接';\n"
            + "    offlineNotice.style.display = 'none';\n"
            // Send hello with sessionId so TV knows who we are
            + "    ws.send(JSON.stringify({type:'hello', sessionId, token:authToken}));\n"
            + "  };\n"
            + "  ws.onclose = () => {\n"
            + "    dot.className = 'status-dot error';\n"
            + "    statusText.textContent = '连接断开';\n"
            + "    offlineNotice.style.display = 'block';\n"
            + "    reconnectTimer = setTimeout(connect, 3000);\n"
            + "  };\n"
            + "  ws.onerror = () => {\n"
            + "    dot.className = 'status-dot error';\n"
            + "    statusText.textContent = '连接错误';\n"
            + "  };\n"
            + "  ws.onmessage = (e) => {\n"
            + "    const msg = JSON.parse(e.data);\n"
            + "    if (msg.type === 'sync') {\n"
            // Only update our textarea if the sync came from someone else
            + "      tvPreview.textContent = msg.value || '（等待输入...）';\n"
            + "      if (msg.sessionId !== sessionId) {\n"
            + "        inputBox.value = msg.value;\n"
            + "        lastSentText = msg.value;\n"
            + "        btnConfirm.disabled = !msg.value;\n"
            + "      }\n"
            + "    } else if (msg.type === 'sessions') {\n"
            + "      const count = msg.count;\n"
            + "      sessionsBar.textContent = count <= 1\n"
            + "        ? '🟢 仅你一人连接'\n"
            + "        : '👥 当前 ' + count + ' 人同时连接，最后输入的内容生效';\n"
            + "      isOtherTyping = count > 1;\n"
            + "      conflictNotice.style.display = count > 1 ? 'block' : 'none';\n"
            + "    } else if (msg.type === 'action') {\n"
            + "      if (msg.value === 'confirmed') {\n"
            + "        showToast('✓ 电视已确认输入');\n"
            + "        inputBox.value = '';\n"
            + "        lastSentText = '';\n"
            + "        btnConfirm.disabled = true;\n"
            + "      } else if (msg.value === 'dismissed') {\n"
            + "        showToast('输入法已关闭');\n"
            + "      } else if (msg.value === 'cleared') {\n"
            + "        inputBox.value = '';\n"
            + "        lastSentText = '';\n"
            + "        btnConfirm.disabled = true;\n"
            + "      }\n"
            + "    }\n"
            + "  };\n"
            + "}\n"
            + "\n"
            + "function sendText(text) {\n"
            + "  if (ws && ws.readyState === WebSocket.OPEN) {\n"
            + "    ws.send(JSON.stringify({type:'text', value:text, sessionId, token:authToken}));\n"
            + "    lastSentText = text;\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function sendAction(action) {\n"
            + "  if (ws && ws.readyState === WebSocket.OPEN) {\n"
            + "    ws.send(JSON.stringify({type:'action', value:action, sessionId, token:authToken}));\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "function confirmInput() {\n"
            + "  const now = Date.now();\n"
            + "  if (now - lastConfirmAt < 350) return;\n"
            + "  lastConfirmAt = now;\n"
            + "  saveHistory(inputBox.value);\n"
            + "  sendAction('confirm');\n"
            + "}\n"
            + "function readHistory() {\n"
            + "  try { const value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); return Array.isArray(value) ? value.filter(v => typeof v === 'string' && v) : []; }\n"
            + "  catch (e) { return []; }\n"
            + "}\n"
            + "function saveHistory(text) {\n"
            + "  if (!text || !text.trim()) return;\n"
            + "  const values = readHistory().filter(v => v !== text);\n"
            + "  values.unshift(text);\n"
            + "  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(values.slice(0, MAX_HISTORY))); } catch (e) {}\n"
            + "  renderHistory();\n"
            + "}\n"
            + "function renderHistory() {\n"
            + "  const current = historySelect.value;\n"
            + "  historySelect.replaceChildren(new Option('历史输入', ''));\n"
            + "  readHistory().forEach((value, index) => historySelect.add(new Option((index + 1) + '. ' + value.slice(0, 24), value)));\n"
            + "  if (readHistory().includes(current)) historySelect.value = current;\n"
            + "}\n"
            + "function readPhrases() {\n"
            + "  try { const value = JSON.parse(localStorage.getItem(PHRASES_KEY) || '[]'); return Array.isArray(value) ? value.filter(v => typeof v === 'string' && v) : []; }\n"
            + "  catch (e) { return []; }\n"
            + "}\n"
            + "function renderPhrases() {\n"
            + "  quickPhrases.replaceChildren();\n"
            + "  readPhrases().forEach(value => {\n"
            + "    const chip = document.createElement('button'); chip.className = 'phrase-chip'; chip.type = 'button'; chip.textContent = value.slice(0, 20);\n"
            + "    chip.title = value; chip.addEventListener('click', () => { inputBox.value = value; inputBox.focus(); inputBox.setSelectionRange(value.length, value.length); btnConfirm.disabled = false; sendText(value); });\n"
            + "    const remove = document.createElement('span'); remove.className = 'remove'; remove.textContent = '×'; remove.addEventListener('click', event => { event.stopPropagation(); const next = readPhrases().filter(item => item !== value); localStorage.setItem(PHRASES_KEY, JSON.stringify(next)); renderPhrases(); });\n"
            + "    chip.appendChild(remove); quickPhrases.appendChild(chip);\n"
            + "  });\n"
            + "  const add = document.createElement('button'); add.className = 'phrase-chip'; add.type = 'button'; add.textContent = '＋ 添加短语'; add.addEventListener('click', () => { const value = prompt('输入常用短语'); if (!value || !value.trim()) return; const next = readPhrases().filter(item => item !== value.trim()); next.unshift(value.trim()); localStorage.setItem(PHRASES_KEY, JSON.stringify(next.slice(0, 12))); renderPhrases(); });\n"
            + "  quickPhrases.appendChild(add);\n"
            + "}\n"
            + "const quickPhrases = document.getElementById('quickPhrases');\n"
            + "renderPhrases();\n"
            + "\n"
            + "historySelect.addEventListener('change', () => {\n"
            + "  if (!historySelect.value) return;\n"
            + "  inputBox.value = historySelect.value;\n"
            + "  inputBox.focus();\n"
            + "  inputBox.setSelectionRange(inputBox.value.length, inputBox.value.length);\n"
            + "  btnConfirm.disabled = false;\n"
            + "  sendText(inputBox.value);\n"
            + "});\n"
            + "renderHistory();\n"
            + "\n"
            // Debounce text sync — send 80ms after user stops typing
            + "let debounceTimer = null;\n"
            + "inputBox.addEventListener('input', () => {\n"
            + "  const text = inputBox.value;\n"
            + "  btnConfirm.disabled = !text;\n"
            + "  clearTimeout(debounceTimer);\n"
            + "  debounceTimer = setTimeout(() => sendText(text), 80);\n"
            + "});\n"
            + "\n"
            + "btnConfirm.addEventListener('click', confirmInput);\n"
            + "btnCancel.addEventListener('click', () => sendAction('dismiss'));"
            + "btnBack.addEventListener('click', () => sendAction('back'));\n"
            + "btnBackspace.addEventListener('click', () => {\n"
            + "  const pos = inputBox.selectionStart;\n"
            + "  if (pos > 0) {\n"
            + "    inputBox.value = inputBox.value.slice(0, pos - 1) + inputBox.value.slice(pos);\n"
            + "    inputBox.setSelectionRange(pos - 1, pos - 1);\n"
            + "    sendText(inputBox.value);\n"
            + "    btnConfirm.disabled = !inputBox.value;\n"
            + "  }\n"
            + "});\n"
            + "btnClear.addEventListener('click', () => {\n"
            + "  inputBox.value = '';\n"
            + "  sendAction('clear');\n"
            + "  btnConfirm.disabled = true;\n"
            + "});\n"
            + "btnVolumeDown.addEventListener('click', () => sendAction('volume_down'));\n"
            + "btnVolumeUp.addEventListener('click', () => sendAction('volume_up'));\n"
            + "btnMenu.addEventListener('click', () => sendAction('menu'));\n"
            + "inputBox.addEventListener('keydown', event => {\n"
            + "  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); confirmInput(); }\n"
            + "  else if (event.key === 'Escape') { event.preventDefault(); sendAction('dismiss'); }\n"
            + "});\n"
            + "\n"
            + "function showToast(msg) {\n"
            + "  const t = document.createElement('div');\n"
            + "  t.textContent = msg;\n"
            + "  t.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);"
            + "background:#388e3c;color:#fff;padding:10px 20px;border-radius:20px;"
            + "font-size:14px;z-index:999;white-space:nowrap;';\n"
            + "  document.body.appendChild(t);\n"
            + "  setTimeout(() => t.remove(), 2500);\n"
            + "}\n"
            + "\n"
            + "connect();\n"
            + "</script>\n"
            + "</body>\n"
            + "</html>\n";
    }

    private String escapeHtmlJs(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }
}
