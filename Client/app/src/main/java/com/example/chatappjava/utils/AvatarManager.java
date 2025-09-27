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
    
    private static AvatarManager instance;
    private Context context;
    private SharedPreferences prefs;
    private Handler handler;
    private Set<String> cachedAvatars;
    
    private AvatarManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.handler = new Handler(Looper.getMainLooper());
        this.cachedAvatars = new HashSet<>(prefs.getStringSet(KEY_CACHED_AVATARS, new HashSet<>()));
    }
    
    public static synchronized AvatarManager getInstance(Context context) {
        if (instance == null) {
            instance = new AvatarManager(context);
        }
        return instance;
    }
    
    /**
     * Load avatar for a user with caching and scheduled refresh
     */
    public void loadAvatar(String avatarUrl, ImageView imageView, int placeholderResId) {
        android.util.Log.d("AvatarManager", "loadAvatar called with URL: " + avatarUrl);
        
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            android.util.Log.d("AvatarManager", "Avatar URL is null or empty, using placeholder");
            imageView.setImageResource(placeholderResId);
            return;
        }
        
        // Use avatarUrl directly (should already be full URL from model)
        String fullAvatarUrl = avatarUrl;
        android.util.Log.d("AvatarManager", "AvatarManager.loadAvatar() called with: " + fullAvatarUrl);
        
        // Check if we need to refresh avatars (at midnight daily)
        if (shouldRefreshAvatars()) {
            clearAvatarCache();
            scheduleNextRefresh();
        }
        
        android.util.Log.d("AvatarManager", "Loading avatar with Picasso: " + fullAvatarUrl);
        
        // Load avatar with Picasso
        Picasso.get()
                .load(fullAvatarUrl)
                .placeholder(placeholderResId)
                .error(placeholderResId)
                .into(imageView, new com.squareup.picasso.Callback() {
                    @Override
                    public void onSuccess() {
                        android.util.Log.d("AvatarManager", "Avatar loaded successfully: " + fullAvatarUrl);
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("AvatarManager", "Failed to load avatar: " + fullAvatarUrl, e);
                    }
                });
        
        // Cache this avatar URL (use full URL for caching)
        cachedAvatars.add(fullAvatarUrl);
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
     * Get cached avatar URLs
     */
    public Set<String> getCachedAvatars() {
        return new HashSet<>(cachedAvatars);
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
