package com.example.chatappjava.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.example.chatappjava.models.Message;
import com.example.chatappjava.network.ApiClient;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Manages synchronization of offline messages with the server
 * Handles sending pending messages when network is available
 */
public class OfflineMessageSyncManager {
    private static final String TAG = "OfflineMessageSync";
    private static final int MAX_SYNC_ATTEMPTS = 3;
    
    private final Context context;
    private final MessageRepository messageRepository;
    private final ApiClient apiClient;
    private final DatabaseManager databaseManager;
    private boolean isSyncing = false;
    
    public OfflineMessageSyncManager(Context context) {
        this.context = context;
        this.messageRepository = new MessageRepository(context);
        this.apiClient = new ApiClient();
        this.databaseManager = new DatabaseManager(context);
    }
    
    /**
     * Check if device has network connectivity
     */
    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }
    
    /**
     * Sync all pending messages with the server
     * Should be called when network becomes available
     */
    public void syncPendingMessages() {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping...");
            return;
        }
        
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, cannot sync");
            return;
        }
        
        List<Message> pendingMessages = messageRepository.getPendingMessages();
        if (pendingMessages.isEmpty()) {
            Log.d(TAG, "No pending messages to sync");
            return;
        }
        
        Log.d(TAG, "Starting sync for " + pendingMessages.size() + " pending messages");
        isSyncing = true;
        
        syncMessagesSequentially(pendingMessages, 0);
    }
    
    /**
     * Sync messages one by one to avoid overwhelming the server
     */
    private void syncMessagesSequentially(List<Message> messages, int index) {
        if (index >= messages.size()) {
            isSyncing = false;
            Log.d(TAG, "Finished syncing all messages");
            return;
        }
        
        Message message = messages.get(index);
        syncSingleMessage(message, new SyncCallback() {
            @Override
            public void onSuccess(String newMessageId) {
                // Update message with server ID and mark as synced
                messageRepository.updateSyncStatus(
                    message.getId(),
                    newMessageId,
                    "synced",
                    null
                );
                
                // Continue with next message
                syncMessagesSequentially(messages, index + 1);
            }
            
            @Override
            public void onFailure(String error) {
                // Mark as failed and continue (don't block other messages)
                messageRepository.updateSyncStatus(
                    message.getId(),
                    null,
                    "failed",
                    error
                );
                
                // Continue with next message
                syncMessagesSequentially(messages, index + 1);
            }
        });
    }
    
    /**
     * Sync a single message to the server
     */
    private void syncSingleMessage(Message message, SyncCallback callback) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            callback.onFailure("No authentication token");
            return;
        }
        
        try {
            // Prepare message JSON for API
            JSONObject messageJson = new JSONObject();
            messageJson.put("chatId", message.getChatId());
            messageJson.put("content", message.getContent());
            messageJson.put("type", message.getType());
            messageJson.put("timestamp", message.getTimestamp());
            
            if (message.getReplyToMessageId() != null && !message.getReplyToMessageId().isEmpty()) {
                messageJson.put("replyTo", message.getReplyToMessageId());
            }
            
            if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                // Attachments should already be in JSON format
                messageJson.put("attachments", new org.json.JSONArray(message.getAttachments()));
            }
            
            // Add clientNonce for deduplication
            if (message.getClientNonce() != null && !message.getClientNonce().isEmpty()) {
                messageJson.put("clientNonce", message.getClientNonce());
            }
            
            // Send message via API
            apiClient.sendMessage(token, messageJson, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to sync message: " + e.getMessage());
                    callback.onFailure(e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.optJSONObject("data");
                                if (data != null) {
                                    String newMessageId = data.optString("_id", "");
                                    if (!newMessageId.isEmpty()) {
                                        Log.d(TAG, "Message synced successfully: " + message.getId() + " -> " + newMessageId);
                                        callback.onSuccess(newMessageId);
                                        return;
                                    }
                                }
                            }
                            callback.onFailure("Invalid server response");
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing sync response: " + e.getMessage());
                            callback.onFailure("Parse error: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "Sync failed with status: " + response.code());
                        callback.onFailure("HTTP " + response.code());
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error preparing message for sync: " + e.getMessage());
            callback.onFailure("JSON error: " + e.getMessage());
        }
    }
    
    /**
     * Callback interface for sync operations
     */
    private interface SyncCallback {
        void onSuccess(String newMessageId);
        void onFailure(String error);
    }
    
    /**
     * Get count of pending messages
     */
    public int getPendingMessageCount() {
        return messageRepository.getPendingMessageCount();
    }
    
    /**
     * Check if sync is in progress
     */
    public boolean isSyncing() {
        return isSyncing;
    }
}

