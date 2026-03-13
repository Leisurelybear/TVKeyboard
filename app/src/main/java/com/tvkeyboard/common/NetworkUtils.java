package com.tvkeyboard.common;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkUtils {

    public static String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return Formatter.formatIpAddress(ip);
            }
        } catch (Exception e) { e.printStackTrace(); }

        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                Enumeration<InetAddress> addrs = ifaces.nextElement().getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getHostAddress().contains("."))
                        return a.getHostAddress();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        return "127.0.0.1";
    }

    /**
     * QR code points to the HTTP web page (no app needed on phone).
     * http://192.168.1.100:8766
     */
    public static String buildQrContent(String ip, int httpPort) {
        return "http://" + ip + ":" + httpPort;
    }

    /** Legacy WebSocket URL parser for native phone app (kept for compatibility) */
    public static String[] parseQrContent(String content) {
        if (content == null) return null;
        // support both tvkb:// and http://
        String rest = content;
        if (rest.startsWith("tvkb://")) rest = rest.substring("tvkb://".length());
        else if (rest.startsWith("http://")) rest = rest.substring("http://".length());
        else return null;
        // strip path
        int slash = rest.indexOf('/');
        if (slash >= 0) rest = rest.substring(0, slash);
        int colon = rest.lastIndexOf(':');
        if (colon < 0) return null;
        return new String[]{rest.substring(0, colon), rest.substring(colon + 1)};
    }
}
