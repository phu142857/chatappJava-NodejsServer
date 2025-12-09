package com.example.chatappjava.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.example.chatappjava.models.Message;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.network.ApiClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SyncManager handles background synchronization of messages, posts, and user data
 * Implements delta sync to fetch only changes since last sync timestamp
 */
public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_LAST_FOREGROUND_SYNC = "last_foreground_sync";
    private static final long FOREGROUND_SYNC_INTERVAL_MS = 2 * 1000; // 2 seconds
    private static final long BACKGROUND_SYNC_INTERVAL_MS = 15 * 60 * 1000; // 15 minutes
    
    private static SyncManager instance;
    private final Context context;
    private final DatabaseHelper dbHelper;
    private final ApiClient apiClient;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final ConversationRepository conversationRepository;
    private final android.os.Handler mainHandler;
    
    // Sync listeners
    public interface SyncListener {
        void onSyncComplete(String resourceType, boolean success, int itemsUpdated);
        void onSyncError(String resourceType, String error);
    }
    
    private final List<SyncListener> syncListeners = new ArrayList<>();
    
    private SyncManager(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
        this.apiClient = new ApiClient();
        this.messageRepository = new MessageRepository(context);
        this.postRepository = new PostRepository(context);
        this.conversationRepository = new ConversationRepository(context);
        this.mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    }
    
    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Add sync listener
     */
    public void addSyncListener(SyncListener listener) {
        if (listener != null && !syncListeners.contains(listener)) {
            syncListeners.add(listener);
        }
    }
    
    /**
     * Remove sync listener
     */
    public void removeSyncListener(SyncListener listener) {
        syncListeners.remove(listener);
    }
    
    /**
     * Check if foreground sync is needed (every 2 seconds)
     */
    public boolean shouldSyncForeground() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_FOREGROUND_SYNC, 0);
        long now = System.currentTimeMillis();
        return (now - lastSync) >= FOREGROUND_SYNC_INTERVAL_MS;
    }
    
    /**
     * Perform foreground sync (when app is active)
     */
    public void syncForeground(String token) {
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "Cannot sync: token is null or empty");
            return;
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_FOREGROUND_SYNC, 0);
        long now = System.currentTimeMillis();
        
        if ((now - lastSync) < FOREGROUND_SYNC_INTERVAL_MS) {
            Log.d(TAG, "Foreground sync skipped: too soon since last sync");
            return;
        }
        
        Log.d(TAG, "Starting foreground sync");
        syncAll(token, false);
        
        prefs.edit().putLong(KEY_LAST_FOREGROUND_SYNC, now).apply();
    }
    
    /**
     * Perform background sync (when app is minimized)
     */
    public void syncBackground(String token) {
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "Cannot sync: token is null or empty");
            return;
        }
        
        Log.d(TAG, "Starting background sync");
        syncAll(token, true);
    }
    
    /**
     * Sync all resources (messages, posts, conversations)
     */
    private void syncAll(String token, boolean isBackground) {
        syncMessages(token, isBackground);
        syncPosts(token, isBackground);
        syncConversations(token, isBackground);
    }
    
    /**
     * Sync messages - fetch only new messages since last sync
     */
    public void syncMessages(String token, boolean isBackground) {
        long lastSyncTimestamp = getLastSyncTimestamp("messages");
        
        String endpoint = "/api/updates/messages?since=" + lastSyncTimestamp;
        apiClient.authenticatedGet(endpoint, token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Failed to sync messages: " + e.getMessage());
                setLastSyncError("messages", e.getMessage());
                notifySyncError("messages", e.getMessage());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.code() == 304) {
                    // No updates available
                    Log.d(TAG, "Messages sync: No updates (304)");
                    setLastSyncSuccess("messages", true);
                    notifySyncComplete("messages", true, 0);
                    return;
                }
                
                if (!response.isSuccessful()) {
                    String error = "HTTP " + response.code();
                    Log.e(TAG, "Messages sync failed: " + error);
                    setLastSyncError("messages", error);
                    notifySyncError("messages", error);
                    return;
                }
                
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.getJSONObject("data");
                        JSONArray messages = data.optJSONArray("messages");
                        
                        int count = 0;
                        if (messages != null) {
                            for (int i = 0; i < messages.length(); i++) {
                                JSONObject msgJson = messages.getJSONObject(i);
                                Message message = Message.fromJson(msgJson);
                                messageRepository.saveMessage(message);
                                count++;
                            }
                        }
                        
                        long newTimestamp = data.optLong("updated_at", System.currentTimeMillis());
                        setLastSyncTimestamp("messages", newTimestamp);
                        setLastSyncSuccess("messages", true);
                        
                        Log.d(TAG, "Messages sync complete: " + count + " messages updated");
                        notifySyncComplete("messages", true, count);
                    } else {
                        String error = json.optString("message", "Unknown error");
                        Log.e(TAG, "Messages sync failed: " + error);
                        setLastSyncError("messages", error);
                        notifySyncError("messages", error);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing messages sync response: " + e.getMessage());
                    setLastSyncError("messages", e.getMessage());
                    notifySyncError("messages", e.getMessage());
                }
            }
        });
    }
    
    /**
     * Sync posts - fetch only new posts since last sync
     */
    public void syncPosts(String token, boolean isBackground) {
        long lastSyncTimestamp = getLastSyncTimestamp("posts");
        
        String endpoint = "/api/updates/posts?since=" + lastSyncTimestamp;
        apiClient.authenticatedGet(endpoint, token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Failed to sync posts: " + e.getMessage());
                setLastSyncError("posts", e.getMessage());
                notifySyncError("posts", e.getMessage());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.code() == 304) {
                    // No updates available
                    Log.d(TAG, "Posts sync: No updates (304)");
                    setLastSyncSuccess("posts", true);
                    notifySyncComplete("posts", true, 0);
                    return;
                }
                
                if (!response.isSuccessful()) {
                    String error = "HTTP " + response.code();
                    Log.e(TAG, "Posts sync failed: " + error);
                    setLastSyncError("posts", error);
                    notifySyncError("posts", error);
                    return;
                }
                
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.getJSONObject("data");
                        JSONArray posts = data.optJSONArray("posts");
                        
                        int count = 0;
                        if (posts != null) {
                            for (int i = 0; i < posts.length(); i++) {
                                JSONObject postJson = posts.getJSONObject(i);
                                Post post = Post.fromJson(postJson);
                                postRepository.savePost(post);
                                count++;
                            }
                        }
                        
                        long newTimestamp = data.optLong("updated_at", System.currentTimeMillis());
                        setLastSyncTimestamp("posts", newTimestamp);
                        setLastSyncSuccess("posts", true);
                        
                        Log.d(TAG, "Posts sync complete: " + count + " posts updated");
                        notifySyncComplete("posts", true, count);
                    } else {
                        String error = json.optString("message", "Unknown error");
                        Log.e(TAG, "Posts sync failed: " + error);
                        setLastSyncError("posts", error);
                        notifySyncError("posts", error);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing posts sync response: " + e.getMessage());
                    setLastSyncError("posts", e.getMessage());
                    notifySyncError("posts", e.getMessage());
                }
            }
        });
    }
    
    /**
     * Sync conversations - fetch only updated conversations since last sync
     */
    public void syncConversations(String token, boolean isBackground) {
        long lastSyncTimestamp = getLastSyncTimestamp("conversations");
        
        String endpoint = "/api/updates/conversations?since=" + lastSyncTimestamp;
        apiClient.authenticatedGet(endpoint, token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Failed to sync conversations: " + e.getMessage());
                setLastSyncError("conversations", e.getMessage());
                notifySyncError("conversations", e.getMessage());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.code() == 304) {
                    // No updates available
                    Log.d(TAG, "Conversations sync: No updates (304)");
                    setLastSyncSuccess("conversations", true);
                    notifySyncComplete("conversations", true, 0);
                    return;
                }
                
                if (!response.isSuccessful()) {
                    String error = "HTTP " + response.code();
                    Log.e(TAG, "Conversations sync failed: " + error);
                    setLastSyncError("conversations", error);
                    notifySyncError("conversations", error);
                    return;
                }
                
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.getJSONObject("data");
                        JSONArray conversations = data.optJSONArray("conversations");
                        
                        int count = 0;
                        if (conversations != null) {
                            for (int i = 0; i < conversations.length(); i++) {
                                JSONObject convJson = conversations.getJSONObject(i);
                                com.example.chatappjava.models.Chat chat = com.example.chatappjava.models.Chat.fromJson(convJson);
                                conversationRepository.saveConversation(chat);
                                count++;
                            }
                        }
                        
                        long newTimestamp = data.optLong("updated_at", System.currentTimeMillis());
                        setLastSyncTimestamp("conversations", newTimestamp);
                        setLastSyncSuccess("conversations", true);
                        
                        Log.d(TAG, "Conversations sync complete: " + count + " conversations updated");
                        notifySyncComplete("conversations", true, count);
                    } else {
                        String error = json.optString("message", "Unknown error");
                        Log.e(TAG, "Conversations sync failed: " + error);
                        setLastSyncError("conversations", error);
                        notifySyncError("conversations", error);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing conversations sync response: " + e.getMessage());
                    setLastSyncError("conversations", e.getMessage());
                    notifySyncError("conversations", e.getMessage());
                }
            }
        });
    }
    
    /**
     * Get last sync timestamp for a resource type
     */
    private long getLastSyncTimestamp(String resourceType) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_SYNC_METADATA,
            new String[]{DatabaseHelper.COL_SYNC_LAST_SYNC_TIMESTAMP},
            DatabaseHelper.COL_SYNC_RESOURCE_TYPE + " = ?",
            new String[]{resourceType},
            null, null, null
        );
        
        long timestamp = 0;
        if (cursor.moveToFirst()) {
            timestamp = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        
        return timestamp;
    }
    
    /**
     * Set last sync timestamp for a resource type
     */
    private void setLastSyncTimestamp(String resourceType, long timestamp) {
        // Execute on main thread to avoid SQLite connection pool issues
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            setLastSyncTimestampInternal(resourceType, timestamp);
        } else {
            mainHandler.post(() -> setLastSyncTimestampInternal(resourceType, timestamp));
        }
    }
    
    private void setLastSyncTimestampInternal(String resourceType, long timestamp) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(DatabaseHelper.COL_SYNC_RESOURCE_TYPE, resourceType);
            values.put(DatabaseHelper.COL_SYNC_LAST_SYNC_TIMESTAMP, timestamp);
            values.put(DatabaseHelper.COL_SYNC_LAST_SYNC_SUCCESS, 1);
            values.putNull(DatabaseHelper.COL_SYNC_LAST_SYNC_ERROR);
            
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_SYNC_METADATA,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error setting sync timestamp: " + e.getMessage());
        }
    }
    
    /**
     * Set last sync success status
     */
    private void setLastSyncSuccess(String resourceType, boolean success) {
        // Execute on main thread to avoid SQLite connection pool issues
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            setLastSyncSuccessInternal(resourceType, success);
        } else {
            mainHandler.post(() -> setLastSyncSuccessInternal(resourceType, success));
        }
    }
    
    private void setLastSyncSuccessInternal(String resourceType, boolean success) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(DatabaseHelper.COL_SYNC_LAST_SYNC_SUCCESS, success ? 1 : 0);
            if (success) {
                values.putNull(DatabaseHelper.COL_SYNC_LAST_SYNC_ERROR);
            }
            
            db.update(
                DatabaseHelper.TABLE_SYNC_METADATA,
                values,
                DatabaseHelper.COL_SYNC_RESOURCE_TYPE + " = ?",
                new String[]{resourceType}
            );
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error setting sync success: " + e.getMessage());
        }
    }
    
    /**
     * Set last sync error
     */
    private void setLastSyncError(String resourceType, String error) {
        // Execute on main thread to avoid SQLite connection pool issues
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            setLastSyncErrorInternal(resourceType, error);
        } else {
            mainHandler.post(() -> setLastSyncErrorInternal(resourceType, error));
        }
    }
    
    private void setLastSyncErrorInternal(String resourceType, String error) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(DatabaseHelper.COL_SYNC_LAST_SYNC_SUCCESS, 0);
            values.put(DatabaseHelper.COL_SYNC_LAST_SYNC_ERROR, error);
            
            db.update(
                DatabaseHelper.TABLE_SYNC_METADATA,
                values,
                DatabaseHelper.COL_SYNC_RESOURCE_TYPE + " = ?",
                new String[]{resourceType}
            );
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error setting sync error: " + e.getMessage());
        }
    }
    
    /**
     * Notify sync listeners
     */
    private void notifySyncComplete(String resourceType, boolean success, int itemsUpdated) {
        for (SyncListener listener : syncListeners) {
            try {
                listener.onSyncComplete(resourceType, success, itemsUpdated);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying sync listener: " + e.getMessage());
            }
        }
    }
    
    private void notifySyncError(String resourceType, String error) {
        for (SyncListener listener : syncListeners) {
            try {
                listener.onSyncError(resourceType, error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying sync listener: " + e.getMessage());
            }
        }
    }
}

