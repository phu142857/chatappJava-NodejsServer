package com.example.chatappjava.utils;

/**
 * Utility class for handling URL construction and validation
 * Eliminates duplicate URL building logic across models
 */
public class UrlUtils {
    
    /**
     * Constructs full URL from relative path for avatars and other resources
     * @param relativePath Relative path (can be null or empty)
     * @return Full URL or null if input is invalid
     */
    public static String getFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        
        // Return as-is if already a full URL
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        
        // Ensure path starts with /
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        
        // Construct full URL using server configuration
        return "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + path;
    }
    
    /**
     * Constructs full avatar URL specifically for avatar images
     * @param avatarPath Avatar relative path
     * @return Full avatar URL or null if input is invalid
     */
    public static String getFullAvatarUrl(String avatarPath) {
        return getFullUrl(avatarPath);
    }
    
    /**
     * Checks if a string is a valid URL
     * @param url String to check
     * @return true if valid URL, false otherwise
     */
    public static boolean isValidUrl(String url) {
        return url != null && !url.isEmpty() && 
               (url.startsWith("http://") || url.startsWith("https://"));
    }
    
    /**
     * Checks if a path is relative (needs server URL construction)
     * @param path Path to check
     * @return true if relative, false if already absolute or invalid
     */
    public static boolean isRelativePath(String path) {
        return path != null && !path.isEmpty() && 
               !path.startsWith("http://") && !path.startsWith("https://");
    }
}
