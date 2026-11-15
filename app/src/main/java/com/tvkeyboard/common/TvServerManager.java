package com.tvkeyboard.common;

import android.content.Context;

import java.security.SecureRandom;
import android.util.Base64;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Owns the single HTTP/WebSocket pair shared by the IME and TV activity. */
public final class TvServerManager {
    private static final Set<TvWebSocketServer.Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static TvWebSocketServer wsServer;
    private static TvHttpServer httpServer;
    private static String token;

    private TvServerManager() {}

    public static synchronized void register(Context context, TvWebSocketServer.Listener listener) {
        if (wsServer == null) {
            token = newToken();
            wsServer = new TvWebSocketServer(TvWebSocketServer.DEFAULT_PORT, new FanoutListener(), token);
            wsServer.setConnectionLostTimeout(30);
            try { wsServer.start(); } catch (Exception ignored) { wsServer = null; }
            if (wsServer == null) return;
            httpServer = new TvHttpServer(TvWebSocketServer.DEFAULT_PORT, token);
            try { httpServer.start(); } catch (Exception ignored) { httpServer = null; }
            if (httpServer == null) {
                try { wsServer.stop(500); } catch (Exception ignored) {}
                wsServer = null;
                token = null;
                return;
            }
        }
        if (listener != null) LISTENERS.add(listener);
    }

    public static synchronized void unregister(TvWebSocketServer.Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
        if (LISTENERS.isEmpty()) stop();
    }

    public static synchronized void stop() {
        if (wsServer != null) try { wsServer.stop(500); } catch (Exception ignored) {}
        if (httpServer != null) httpServer.stop();
        wsServer = null;
        httpServer = null;
        token = null;
        LISTENERS.clear();
    }

    public static TvWebSocketServer getWebSocketServer() { return wsServer; }
    public static String getToken() { return token; }
    public static int getClientCount() { return wsServer == null ? 0 : wsServer.getConnectedClientCount(); }
    public static void broadcastSync(String text, String origin) { if (wsServer != null) wsServer.broadcastSync(text, origin); }
    public static void broadcastAction(String action) { if (wsServer != null) wsServer.broadcastAction(action); }
    public static synchronized void disconnectAll() { if (wsServer != null) wsServer.disconnectAll("Disconnected by TV"); }

    private static String newToken() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static final class FanoutListener implements TvWebSocketServer.Listener {
        @Override public void onClientConnected(String id, String ip) { for (TvWebSocketServer.Listener l : LISTENERS) l.onClientConnected(id, ip); }
        @Override public void onClientDisconnected(String id, int remaining) { for (TvWebSocketServer.Listener l : LISTENERS) l.onClientDisconnected(id, remaining); }
        @Override public void onTextReceived(String text, String id) { for (TvWebSocketServer.Listener l : LISTENERS) l.onTextReceived(text, id); }
        @Override public void onActionReceived(String action, String id) { for (TvWebSocketServer.Listener l : LISTENERS) l.onActionReceived(action, id); }
    }
}
