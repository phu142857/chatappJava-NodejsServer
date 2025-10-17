package com.example.chatappjava.config;

/**
 * Server configuration class for managing server URLs and endpoints
 * Simple configuration that only supports specific IP addresses
 */
public class ServerConfig {
    
    // ===== SERVER CONFIGURATION =====
    // Change these values to match your server setup
    
    // Server IP and Port (change this to your actual server IP)
    private static final String SERVER_IP = "103.75.183.125";
//    private static final String SERVER_IP = "192.168.2.123";
    private static final int SERVER_PORT = 49664;
    
    // Protocol configuration
    private static final boolean USE_HTTPS = false; // Set to true for production with SSL
    private static final boolean USE_WSS = false;   // Set to true for secure WebSocket

    // ===== URL GENERATION =====
    
    /**
     * Get the base server URL
     */
    public static String getBaseUrl() {
        String protocol = isUsingHttps() ? "https" : "http";
        return protocol + "://" + getServerIp() + ":" + getServerPort();
    }
    
    /**
     * Get the WebSocket/Socket.IO URL
     */
    public static String getWebSocketUrl() {
        return getBaseUrl();
    }

    // ===== SERVER INFO =====
    
    /**
     * Get current server IP
     */
    public static String getServerIp() {
        com.example.chatappjava.ChatApplication app = com.example.chatappjava.ChatApplication.getInstance();
        if (app != null) {
            com.example.chatappjava.utils.SharedPreferencesManager prefs = app.getSharedPrefsManager();
            String override = prefs.getOverrideServerIp();
            if (override != null && !override.isEmpty()) return override;
        }
        return SERVER_IP;
    }
    
    /**
     * Get current server port
     */
    public static int getServerPort() {
        com.example.chatappjava.ChatApplication app = com.example.chatappjava.ChatApplication.getInstance();
        if (app != null) {
            com.example.chatappjava.utils.SharedPreferencesManager prefs = app.getSharedPrefsManager();
            int override = prefs.getOverrideServerPort();
            if (override > 0) return override;
        }
        return SERVER_PORT;
    }
    
    /**
     * Check if using HTTPS
     */
    public static boolean isUsingHttps() {
        com.example.chatappjava.ChatApplication app = com.example.chatappjava.ChatApplication.getInstance();
        if (app != null) {
            com.example.chatappjava.utils.SharedPreferencesManager prefs = app.getSharedPrefsManager();
            Boolean override = prefs.getOverrideUseHttps();
            if (override != null) return override;
        }
        return USE_HTTPS;
    }
    
    /**
     * Check if using secure WebSocket
     */
    public static boolean isUsingWss() {
        com.example.chatappjava.ChatApplication app = com.example.chatappjava.ChatApplication.getInstance();
        if (app != null) {
            com.example.chatappjava.utils.SharedPreferencesManager prefs = app.getSharedPrefsManager();
            Boolean override = prefs.getOverrideUseWss();
            if (override != null) return override;
        }
        return USE_WSS;
    }
}
