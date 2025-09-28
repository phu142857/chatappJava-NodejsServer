package com.example.chatappjava.config;

/**
 * Server configuration class for managing server URLs and endpoints
 * Simple configuration that only supports specific IP addresses
 */
public class ServerConfig {
    
    // ===== SERVER CONFIGURATION =====
    // Change these values to match your server setup
    
    // Server IP and Port (change this to your actual server IP)
//    private static final String SERVER_IP = "192.168.2.12";
//    private static final String SERVER_IP = "10.197.192.224";
    private static final String SERVER_IP = "103.75.183.125";
    private static final int SERVER_PORT = 5000;
    
    // Protocol configuration
    private static final boolean USE_HTTPS = false; // Set to true for production with SSL
    private static final boolean USE_WSS = false;   // Set to true for secure WebSocket
    
    // ===== TURN SERVER CONFIGURATION =====
    // TURN server for NAT traversal (required for physical device ↔ emulator calls)
    public static final String TURN_URL_UDP = "turn:openrelay.metered.ca:80?transport=udp";
    public static final String TURN_URL_TCP = "turn:openrelay.metered.ca:80?transport=tcp";
    public static final String TURN_USERNAME = "openrelayproject";
    public static final String TURN_PASSWORD = "openrelayproject";
    
    // ===== URL GENERATION =====
    
    /**
     * Get the base server URL
     */
    public static String getBaseUrl() {
        String protocol = USE_HTTPS ? "https" : "http";
        return protocol + "://" + SERVER_IP + ":" + SERVER_PORT;
    }
    
    /**
     * Get the WebSocket/Socket.IO URL
     */
    public static String getWebSocketUrl() {
        return getBaseUrl();
    }
    
    /**
     * Get the API base URL
     */
    public static String getApiBaseUrl() {
        return getBaseUrl() + "/api";
    }
    
    /**
     * Get the notification WebSocket URL
     */
    public static String getNotificationWebSocketUrl() {
        String protocol = USE_WSS ? "wss" : "ws";
        return protocol + "://" + SERVER_IP + ":" + SERVER_PORT + "/ws/";
    }
    
    // ===== SERVER INFO =====
    
    /**
     * Get current server IP
     */
    public static String getServerIp() {
        return SERVER_IP;
    }
    
    /**
     * Get current server port
     */
    public static int getServerPort() {
        return SERVER_PORT;
    }
    
    /**
     * Check if using HTTPS
     */
    public static boolean isUsingHttps() {
        return USE_HTTPS;
    }
    
    /**
     * Check if using secure WebSocket
     */
    public static boolean isUsingWss() {
        return USE_WSS;
    }
    
    // ===== COMMON ENDPOINTS =====
    
    /**
     * Get authentication endpoints
     */
    public static class Auth {
        public static String getLoginUrl() {
            return getApiBaseUrl() + "/auth/login";
        }
        
        public static String getRegisterUrl() {
            return getApiBaseUrl() + "/auth/register";
        }
        
        public static String getLogoutUrl() {
            return getApiBaseUrl() + "/auth/logout";
        }
    }
    
    
    /**
     * Get message endpoints
     */
    public static class Message {
        public static String getMessagesUrl(String chatId) {
            return getApiBaseUrl() + "/messages/" + chatId;
        }
        
        public static String getSendMessageUrl() {
            return getApiBaseUrl() + "/messages";
        }
    }
    
    // ===== DEBUG INFO =====
    
    /**
     * Get debug information about current configuration
     */
    public static String getDebugInfo() {
        return "ServerConfig{" +
                "serverIp=" + getServerIp() +
                ", serverPort=" + getServerPort() +
                ", baseUrl=" + getBaseUrl() +
                ", apiBaseUrl=" + getApiBaseUrl() +
                ", webSocketUrl=" + getWebSocketUrl() +
                ", notificationWebSocketUrl=" + getNotificationWebSocketUrl() +
                ", useHttps=" + isUsingHttps() +
                ", useWss=" + isUsingWss() +
                "}";
    }
}
