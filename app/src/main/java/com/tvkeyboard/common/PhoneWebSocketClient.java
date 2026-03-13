package com.tvkeyboard.common;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * WebSocket client running on phone side.
 * Connects to TV's WebSocket server to send text input.
 */
public class PhoneWebSocketClient extends WebSocketClient {

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onSyncReceived(String text);     // TV echoes current text
        void onActionReceived(String action); // TV confirmed/dismissed
        void onError(String message);
    }

    private final Listener listener;

    public PhoneWebSocketClient(String tvIp, int port, Listener listener) throws Exception {
        super(new URI("ws://" + tvIp + ":" + port));
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (listener != null) listener.onConnected();
    }

    @Override
    public void onMessage(String message) {
        if (listener == null) return;
        try {
            if (message.contains("\"type\":\"sync\"")) {
                String value = extractJsonValue(message, "value");
                listener.onSyncReceived(value);
            } else if (message.contains("\"type\":\"action\"")) {
                String value = extractJsonValue(message, "value");
                listener.onActionReceived(value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (listener != null) listener.onDisconnected(reason);
    }

    @Override
    public void onError(Exception ex) {
        if (listener != null) listener.onError(ex.getMessage());
    }

    /**
     * Send current text to TV
     */
    public void sendText(String text) {
        if (isOpen()) {
            String msg = "{\"type\":\"text\",\"value\":\"" + escapeJson(text) + "\"}";
            send(msg);
        }
    }

    /**
     * Send action to TV: confirm / backspace / clear / dismiss
     */
    public void sendAction(String action) {
        if (isOpen()) {
            String msg = "{\"type\":\"action\",\"value\":\"" + action + "\"}";
            send(msg);
        }
    }

    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return unescapeJson(json.substring(start, end));
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
