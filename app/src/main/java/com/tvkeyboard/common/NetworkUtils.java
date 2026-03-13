package com.tvkeyboard.common;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkUtils {

    /**
     * Get device's local LAN IP address
     */
    public static String getLocalIpAddress(Context context) {
        // Try WiFi first
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int ipInt = wifiManager.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return Formatter.formatIpAddress(ipInt);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback: enumerate network interfaces
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "127.0.0.1";
    }

    /**
     * Build WebSocket URL for QR code
     * Format: tvkb://192.168.1.100:8765
     */
    public static String buildQrContent(String ip, int port) {
        return "tvkb://" + ip + ":" + port;
    }

    /**
     * Parse IP and port from QR content
     */
    public static String[] parseQrContent(String content) {
        // content: tvkb://192.168.1.100:8765
        if (content == null || !content.startsWith("tvkb://")) return null;
        String rest = content.substring("tvkb://".length());
        int colonIdx = rest.lastIndexOf(":");
        if (colonIdx < 0) return null;
        return new String[]{rest.substring(0, colonIdx), rest.substring(colonIdx + 1)};
    }
}
