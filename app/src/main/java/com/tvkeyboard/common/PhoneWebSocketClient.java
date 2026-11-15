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
        void onSessionsUpdate(int count);
        void onActionReceived(String action);
        void onError(String message);
    }

    private final Listener listener;
    private final String authToken;
    private final String sessionId = "native_" + UUID.randomUUID().toString().substring(0, 8);

    public PhoneWebSocketClient(String tvIp, int port, String authToken, Listener listener) throws Exception {
        super(new URI("ws://" + tvIp + ":" + port));
        this.authToken = authToken;
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        // Send hello with session ID
        send("{\"type\":\"hello\",\"sessionId\":\"" + JsonUtils.escape(sessionId) + "\",\"token\":\"" + JsonUtils.escape(authToken) + "\"}");
        if (listener != null) listener.onConnected();
    }

    @Override
    public void onMessage(String message) {
        if (listener == null) return;
        try {
            if (message.contains("\"type\":\"sync\"")) {
                listener.onSyncReceived(JsonUtils.extractString(message, "value"));
            } else if (message.contains("\"type\":\"sessions\"")) {
                // extract count (numeric, not string)
                listener.onSessionsUpdate(JsonUtils.extractInt(message, "count"));
            } else if (message.contains("\"type\":\"action\"")) {
                listener.onActionReceived(JsonUtils.extractString(message, "value"));
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
        if (isOpen()) send("{\"type\":\"text\",\"value\":\"" + JsonUtils.escape(text)
                + "\",\"sessionId\":\"" + JsonUtils.escape(sessionId) + "\",\"token\":\"" + JsonUtils.escape(authToken) + "\"}");
    }

    public void sendAction(String action) {
        if (isOpen()) send("{\"type\":\"action\",\"value\":\"" + JsonUtils.escape(action)
                + "\",\"sessionId\":\"" + JsonUtils.escape(sessionId) + "\",\"token\":\"" + JsonUtils.escape(authToken) + "\"}");
    }

}
