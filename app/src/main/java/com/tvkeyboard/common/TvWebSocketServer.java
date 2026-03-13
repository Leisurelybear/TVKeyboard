package com.tvkeyboard.common;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server running on TV side.
 *
 * Multi-user strategy: "last writer wins"
 * - Each browser tab sends a unique sessionId in every message
 * - When text arrives, it's applied immediately and broadcast to all
 * - All sessions receive sync so their textarea stays consistent
 * - Session count is broadcast so web UI can warn about concurrent editors
 */
public class TvWebSocketServer extends WebSocketServer {

    public static final int DEFAULT_PORT = 8765;

    public interface Listener {
        void onClientConnected(String sessionId, String clientIp);
        void onClientDisconnected(String sessionId, int remaining);
        void onTextReceived(String text, String sessionId);
        void onActionReceived(String action, String sessionId);
    }

    private final Listener listener;

    // conn → sessionId
    private final Map<WebSocket, String> sessionMap = new ConcurrentHashMap<>();
    // sessionId → conn
    private final Map<String, WebSocket> connMap = new ConcurrentHashMap<>();

    public TvWebSocketServer(int port, Listener listener) {
        super(new InetSocketAddress(port));
        this.listener = listener;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String tempId = "tmp_" + System.currentTimeMillis();
        sessionMap.put(conn, tempId);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String sessionId = sessionMap.remove(conn);
        if (sessionId != null) connMap.remove(sessionId);
        int remaining = sessionMap.size();
        broadcastSessionCount();
        if (listener != null) listener.onClientDisconnected(sessionId, remaining);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            String type = extractJsonValue(message, "type");
            String sessionId = extractJsonValue(message, "sessionId");

            if (!sessionId.isEmpty()) {
                String oldId = sessionMap.get(conn);
                if (oldId != null && !oldId.equals(sessionId)) connMap.remove(oldId);
                sessionMap.put(conn, sessionId);
                connMap.put(sessionId, conn);
            } else {
                sessionId = sessionMap.getOrDefault(conn, "unknown");
            }

            switch (type) {
                case "hello":
                    broadcastSessionCount();
                    String ip = conn.getRemoteSocketAddress().getAddress().getHostAddress();
                    if (listener != null) listener.onClientConnected(sessionId, ip);
                    break;
                case "text":
                    String text = extractJsonValue(message, "value");
                    if (listener != null) listener.onTextReceived(text, sessionId);
                    broadcastSync(text, sessionId);
                    break;
                case "action":
                    String action = extractJsonValue(message, "value");
                    if (listener != null) listener.onActionReceived(action, sessionId);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {}

    // ---- Broadcast helpers ----

    public void broadcastSync(String text, String originSessionId) {
        String msg = "{\"type\":\"sync\""
                + ",\"value\":\"" + escapeJson(text) + "\""
                + ",\"sessionId\":\"" + escapeJson(originSessionId) + "\"}";
        broadcast(msg);
    }

    public void broadcastSessionCount() {
        String msg = "{\"type\":\"sessions\",\"count\":" + sessionMap.size() + "}";
        broadcast(msg);
    }

    public void broadcastAction(String action) {
        String msg = "{\"type\":\"action\",\"value\":\"" + action + "\"}";
        broadcast(msg);
    }

    public void broadcast(String msg) {
        for (WebSocket conn : sessionMap.keySet()) {
            if (conn.isOpen()) {
                try { conn.send(msg); } catch (Exception ignored) {}
            }
        }
    }

    public int getConnectedClientCount() {
        return sessionMap.size();
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
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n")
                .replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }
}
