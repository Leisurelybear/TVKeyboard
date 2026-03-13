package com.tvkeyboard.common;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.UUID;

/**
 * WebSocket client for the native phone app (optional – web page is the primary client now).
 */
public class PhoneWebSocketClient extends WebSocketClient {

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onSyncReceived(String text);
        void onActionReceived(String action);
        void onError(String message);
        default void onSessionsUpdate(int count) {}
    }

    private final Listener listener;
    private final String sessionId = "native_" + UUID.randomUUID().toString().substring(0, 8);

    public PhoneWebSocketClient(String tvIp, int port, Listener listener) throws Exception {
        super(new URI("ws://" + tvIp + ":" + port));
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        // Send hello with session ID
        send("{\"type\":\"hello\",\"sessionId\":\"" + sessionId + "\"}");
        if (listener != null) listener.onConnected();
    }

    @Override
    public void onMessage(String message) {
        if (listener == null) return;
        try {
            if (message.contains("\"type\":\"sync\"")) {
                listener.onSyncReceived(extractValue(message, "value"));
            } else if (message.contains("\"type\":\"sessions\"")) {
                // extract count (numeric, not string)
                String countStr = extractNumeric(message, "count");
                listener.onSessionsUpdate(Integer.parseInt(countStr));
            } else if (message.contains("\"type\":\"action\"")) {
                listener.onActionReceived(extractValue(message, "value"));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (listener != null) listener.onDisconnected(reason);
    }

    @Override
    public void onError(Exception ex) {
        if (listener != null) listener.onError(ex.getMessage());
    }

    public void sendText(String text) {
        if (isOpen()) send("{\"type\":\"text\",\"value\":\"" + escape(text)
                + "\",\"sessionId\":\"" + sessionId + "\"}");
    }

    public void sendAction(String action) {
        if (isOpen()) send("{\"type\":\"action\",\"value\":\"" + action
                + "\",\"sessionId\":\"" + sessionId + "\"}");
    }

    private String extractValue(String json, String key) {
        String s = "\"" + key + "\":\"";
        int i = json.indexOf(s); if (i < 0) return "";
        i += s.length();
        int j = json.indexOf("\"", i); if (j < 0) return "";
        return json.substring(i, j).replace("\\\"","\"").replace("\\n","\n")
                .replace("\\t","\t").replace("\\\\","\\");
    }

    private String extractNumeric(String json, String key) {
        String s = "\"" + key + "\":";
        int i = json.indexOf(s); if (i < 0) return "0";
        i += s.length();
        int j = i;
        while (j < json.length() && Character.isDigit(json.charAt(j))) j++;
        return json.substring(i, j);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
}
