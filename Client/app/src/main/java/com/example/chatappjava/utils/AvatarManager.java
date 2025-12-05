package com.example.chatappjava.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.example.chatappjava.config.ServerConfig;
import com.squareup.picasso.Picasso;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages avatar loading and caching with scheduled refresh
 */
public class AvatarManager {
    private static final String PREFS_NAME = "avatar_cache";
    private static final String KEY_LAST_REFRESH = "last_avatar_refresh";
    private static final String KEY_CACHED_AVATARS = "cached_avatars";
    private static final String KEY_INVALID_AVATARS = "invalid_avatars"; // URLs that returned 404
    
    private static AvatarManager instance;
    private final SharedPreferences prefs;
    private final Handler handler;
    private final Set<String> cachedAvatars;
    private final Set<String> invalidAvatars; // URLs that returned 404
    
    private AvatarManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.handler = new Handler(Looper.getMainLooper());
        this.cachedAvatars = new HashSet<>(prefs.getStringSet(KEY_CACHED_AVATARS, new HashSet<>()));
        this.invalidAvatars = new HashSet<>(prefs.getStringSet(KEY_INVALID_AVATARS, new HashSet<>()));
    }
    
    public static synchronized AvatarManager getInstance(Context context) {
        if (instance == null) {
            instance = new AvatarManager(context);
        }
        return instance;
    }
    
    /**
     * Load avatar for a user with asynchronous loading and placeholder
     * Avatar is loaded in background thread to prevent UI blocking
     * Placeholder is shown immediately while image is loading
     */
    public void loadAvatar(String avatarUrl, ImageView imageView, int placeholderResId) {
        android.util.Log.d("AvatarManager", "loadAvatar called with URL: " + avatarUrl);
        
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            android.util.Log.d("AvatarManager", "Avatar URL is null or empty, showing placeholder");
            imageView.setImageResource(placeholderResId);
            imageView.setTag(null); // Clear tag
            return;
        }
        
        // Skip loading if this URL is known to be invalid (returned 404 before)
        if (invalidAvatars.contains(avatarUrl)) {
            android.util.Log.d("AvatarManager", "Avatar URL is known to be invalid (404), showing placeholder: " + avatarUrl);
            imageView.setImageResource(placeholderResId);
            imageView.setTag(null);
            return;
        }
        
        // Check if imageView already has this URL loaded (prevent flickering)
        // Use a simple tag key to store the current avatar URL
        Object tag = imageView.getTag();
        String currentUrl = (tag instanceof String) ? (String) tag : null;
        
        if (avatarUrl.equals(currentUrl) && imageView.getDrawable() != null) {
            // Already loaded with same URL, skip reload to prevent flickering
            android.util.Log.d("AvatarManager", "Avatar already loaded, skipping reload: " + avatarUrl);
            return;
        }
        
        // Show placeholder immediately to provide instant feedback
        // This ensures user sees something while image loads in background
        imageView.setImageResource(placeholderResId);
        
        // Use avatarUrl directly (should already be full URL from model)
        android.util.Log.d("AvatarManager", "AvatarManager.loadAvatar() called with: " + avatarUrl);
        
        // Check if we need to refresh avatars (at midnight daily)
        if (shouldRefreshAvatars()) {
            clearAvatarCache();
            scheduleNextRefresh();
        }
        
        android.util.Log.d("AvatarManager", "Loading avatar with Picasso (async): " + avatarUrl);
        
        // Cancel any pending request for this ImageView to prevent conflicts
        Picasso.get().cancelRequest(imageView);
        
        // Save URL to tag for future checks
        imageView.setTag(avatarUrl);
        
        // Load avatar asynchronously with Picasso
        // Picasso automatically loads images in background thread, so UI won't be blocked
        // User can scroll and interact while images are loading
        Picasso.get()
                .load(avatarUrl)
                .placeholder(placeholderResId) // Show placeholder while loading (already set above for immediate display)
                .error(placeholderResId) // Show placeholder on error
                .fit() // Resize to fit ImageView dimensions (reduces memory usage)
                .centerCrop() // Crop to center (maintains aspect ratio)
                .noFade() // Prevent fade animation that causes flickering
                .priority(com.squareup.picasso.Picasso.Priority.NORMAL) // Normal priority (won't block other images)
                .into(imageView, new com.squareup.picasso.Callback() {
                    @Override
                    public void onSuccess() {
                        android.util.Log.d("AvatarManager", "Avatar loaded successfully (async): " + avatarUrl);
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        // Check if it's a 404 error (avatar doesn't exist on server - this is normal)
                        // NetworkRequestHandler.ResponseException contains "HTTP 404" in its message
                        boolean is404Error = false;
                        String errorMessage = e != null ? e.getMessage() : null;
                        if (errorMessage != null && errorMessage.contains("HTTP 404")) {
                            is404Error = true;
                        }
                        
                        // Log 404 errors as debug messages (expected behavior - avatar may not exist),
                        // other errors as warnings (network issues, etc.)
                        if (is404Error) {
                            // Mark this URL as invalid to avoid retrying in the future
                            // This helps when server data was deleted and old avatar URLs no longer exist
                            if (!invalidAvatars.contains(avatarUrl)) {
                                invalidAvatars.add(avatarUrl);
                                saveInvalidAvatars();
                                android.util.Log.d("AvatarManager", "Marked avatar URL as invalid (404): " + avatarUrl);
                            }
                            // Don't log 404 errors - they're expected when avatars don't exist
                            // Just show placeholder silently
                        } else {
                            // Log other errors as warnings (not critical, but worth noting)
                            android.util.Log.w("AvatarManager", "Failed to load avatar: " + avatarUrl, e);
                        }
                        // Placeholder is already shown via error() configuration
                    }
                });
        
        // Cache this avatar URL (use full URL for caching)
        cachedAvatars.add(avatarUrl);
        saveCachedAvatars();
    }
    
    /**
     * Load avatar for a user with default placeholder
     */
    public void loadAvatar(String avatarUrl, ImageView imageView) {
        loadAvatar(avatarUrl, imageView, com.example.chatappjava.R.drawable.circle_background);
    }
    
    /**
     * Check if avatars should be refreshed (at midnight daily)
     */
    private boolean shouldRefreshAvatars() {
        long lastRefresh = prefs.getLong(KEY_LAST_REFRESH, 0);
        long currentTime = System.currentTimeMillis();
        
        Calendar lastRefreshCal = Calendar.getInstance();
        lastRefreshCal.setTimeInMillis(lastRefresh);
        
        Calendar currentCal = Calendar.getInstance();
        currentCal.setTimeInMillis(currentTime);
        
        // Check if it's a new day (after midnight)
        return currentCal.get(Calendar.DAY_OF_YEAR) > lastRefreshCal.get(Calendar.DAY_OF_YEAR) ||
               currentCal.get(Calendar.YEAR) > lastRefreshCal.get(Calendar.YEAR);
    }
    
    /**
     * Schedule next refresh at midnight
     */
    private void scheduleNextRefresh() {
        Calendar nextMidnight = Calendar.getInstance();
        nextMidnight.add(Calendar.DAY_OF_MONTH, 1);
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        
        long delay = nextMidnight.getTimeInMillis() - System.currentTimeMillis();
        
        handler.postDelayed(() -> {
            clearAvatarCache();
            scheduleNextRefresh();
        }, delay);
    }
    
    /**
     * Clear avatar cache and update last refresh time
     */
    public void clearAvatarCache() {
        // Clear Picasso cache
        Picasso.get().invalidate("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort());
        
        // Clear cached avatar URLs
        cachedAvatars.clear();
        saveCachedAvatars();
        
        // Update last refresh time
        prefs.edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply();
    }
    
    /**
     * Force refresh all avatars (called on app reload)
     */
    public void forceRefreshAvatars() {
        clearAvatarCache();
    }
    
    /**
     * Save cached avatar URLs to preferences
     */
    private void saveCachedAvatars() {
        prefs.edit().putStringSet(KEY_CACHED_AVATARS, cachedAvatars).apply();
    }
    
    /**
     * Save invalid avatar URLs (404) to preferences
     */
    private void saveInvalidAvatars() {
        prefs.edit().putStringSet(KEY_INVALID_AVATARS, invalidAvatars).apply();
    }
    
    /**
     * Clear invalid avatars list (useful when server data is restored)
     */
    public void clearInvalidAvatars() {
        invalidAvatars.clear();
        saveInvalidAvatars();
    }

    /**
     * Initialize the avatar manager (call this in Application class or main activity)
     */
    public void initialize() {
        // Schedule next refresh if not already scheduled
        if (!shouldRefreshAvatars()) {
            scheduleNextRefresh();
        }
    }
    
}
