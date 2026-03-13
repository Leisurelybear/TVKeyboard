package com.tvkeyboard.common;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * WebSocket server running on TV side.
 * Receives text input from phone clients and broadcasts state back.
 */
public class TvWebSocketServer extends WebSocketServer {

    public static final int DEFAULT_PORT = 8765;

    public interface Listener {
        void onClientConnected(String clientIp);
        void onClientDisconnected(String clientIp);
        void onTextReceived(String text);
        void onActionReceived(String action); // "confirm", "backspace", "clear", "dismiss"
    }

    private final Listener listener;
    private final Set<WebSocket> clients = new HashSet<>();

    public TvWebSocketServer(int port, Listener listener) {
        super(new InetSocketAddress(port));
        this.listener = listener;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        String ip = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        if (listener != null) listener.onClientConnected(ip);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        String ip = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        if (listener != null) listener.onClientDisconnected(ip);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (listener == null) return;
        // Protocol: {"type":"text","value":"hello"} or {"type":"action","value":"confirm"}
        try {
            if (message.contains("\"type\":\"text\"")) {
                String value = extractJsonValue(message, "value");
                listener.onTextReceived(value);
                // Echo current text to all clients
                broadcastCurrentText(value);
            } else if (message.contains("\"type\":\"action\"")) {
                String value = extractJsonValue(message, "value");
                listener.onActionReceived(value);
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
    public void onStart() {
        // Server started
    }

    /**
     * Broadcast current text state to all connected phone clients
     */
    public void broadcastCurrentText(String text) {
        String msg = "{\"type\":\"sync\",\"value\":\"" + escapeJson(text) + "\"}";
        for (WebSocket client : clients) {
            if (client.isOpen()) {
                client.send(msg);
            }
        }
    }

    /**
     * Notify phone that TV confirmed/dismissed input
     */
    public void broadcastAction(String action) {
        String msg = "{\"type\":\"action\",\"value\":\"" + action + "\"}";
        for (WebSocket client : clients) {
            if (client.isOpen()) {
                client.send(msg);
            }
        }
    }

    public int getConnectedClientCount() {
        return clients.size();
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
