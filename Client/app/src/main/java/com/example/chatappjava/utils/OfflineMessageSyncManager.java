package com.example.chatappjava.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.chatappjava.models.Message;
import com.example.chatappjava.network.ApiClient;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long SYNC_DEBOUNCE_MS = 2000; // Prevent duplicate syncs within 2 seconds
    /** Defer POST retry after HTTP timeout so delta sync can reconcile first. */
    private static final long SEND_TIMEOUT_GRACE_MS = 12000;

    private static volatile OfflineMessageSyncManager instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    private final Context context;
    private final MessageRepository messageRepository;
    private final ApiClient apiClient;
    private final DatabaseManager databaseManager;
    private volatile boolean isSyncing = false;
    private long lastSyncTime = 0;
    private final Object syncLock = new Object();
    private final ConcurrentHashMap<String, Long> sendTimeoutGraceUntil = new ConcurrentHashMap<>();
    /** Prevent parallel POST for the same client nonce across activity instances. */
    private final ConcurrentHashMap<String, Boolean> inFlightNonces = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PendingSyncListener pendingSyncListener;

    public interface PendingSyncListener {
        void onMessageSynced(String tempMessageId, Message serverMessage);

        default void onMessageSyncFailed(String tempMessageId, String error) {
            // optional
        }
    }

    public static OfflineMessageSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new OfflineMessageSyncManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void setPendingSyncListener(PendingSyncListener listener) {
        this.pendingSyncListener = listener;
    }

    /**
     * HTTP send timed out — server may still have saved the message.
     * POST retry is deferred so delta sync / DB reconcile can run first.
     */
    public void markSendTimeout(String tempMessageId) {
        if (tempMessageId == null || tempMessageId.isEmpty()) {
            return;
        }
        sendTimeoutGraceUntil.put(tempMessageId, System.currentTimeMillis() + SEND_TIMEOUT_GRACE_MS);
        Log.d(TAG, "Send timeout grace started for " + tempMessageId);
        mainHandler.postDelayed(this::syncPendingMessages, SEND_TIMEOUT_GRACE_MS + 500);
    }

    public void clearSendTimeoutGrace(String tempMessageId) {
        if (tempMessageId != null) {
            sendTimeoutGraceUntil.remove(tempMessageId);
        }
    }

    private boolean isInSendTimeoutGrace(String tempMessageId) {
        Long until = sendTimeoutGraceUntil.get(tempMessageId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            sendTimeoutGraceUntil.remove(tempMessageId);
            return false;
        }
        return true;
    }

    private static boolean isTimeoutError(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase(Locale.US);
        return lower.contains("timeout") || lower.contains("timed out");
    }
    
    public OfflineMessageSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.messageRepository = new MessageRepository(this.context);
        this.apiClient = new ApiClient();
        this.databaseManager = new DatabaseManager(this.context);
    }
    
    /**
     * Check if device has network connectivity
     */
    public boolean isNetworkAvailable() {
        ConnectivityManager cm =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) {
            return false;
        }
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
    }
    
    /**
     * Sync all pending messages with the server
     * Should be called when network becomes available
     * Thread-safe with debounce to prevent duplicate syncs
     */
    public void syncPendingMessages() {
        synchronized (syncLock) {
            // Check if sync is already in progress
            if (isSyncing) {
                Log.d(TAG, "Sync already in progress, skipping...");
                return;
            }
            
            // Debounce: prevent multiple syncs within short time window
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSyncTime < SYNC_DEBOUNCE_MS) {
                Log.d(TAG, "Sync debounced - too soon after last sync (elapsed: " + (currentTime - lastSyncTime) + "ms)");
                return;
            }
            
            if (!isNetworkAvailable()) {
                Log.d(TAG, "No network available, cannot sync");
                return;
            }
            
            // Get pending messages and filter out any that might have been synced already
            List<Message> pendingMessages = messageRepository.getPendingMessages();
            if (pendingMessages.isEmpty()) {
                Log.d(TAG, "No pending messages to sync");
                return;
            }
            
            // Double-check: verify messages are still pending before starting sync
            // This prevents race condition where status was updated between getPendingMessages() and now
            List<Message> stillPending = new java.util.ArrayList<>();
            for (Message msg : pendingMessages) {
                // Re-check status to ensure message is still pending
                // We'll rely on the database query, but add extra safety
                if (msg.getId() != null && !msg.getId().startsWith("temp_")) {
                    // Message has server ID, might already be synced - skip
                    Log.d(TAG, "Skipping message with server ID: " + msg.getId());
                    continue;
                }
                stillPending.add(msg);
            }
            
            if (stillPending.isEmpty()) {
                Log.d(TAG, "No messages to sync after filtering");
                return;
            }
            
            for (Message msg : stillPending) {
                Log.d(TAG, "SYNC_PENDING id=" + msg.getId()
                        + " nonce=" + msg.getClientNonce()
                        + " content=" + msg.getContent());
            }

            Log.d(TAG, "Starting sync for " + stillPending.size() + " pending messages");
            isSyncing = true;
            lastSyncTime = currentTime;
            
            // Start sync in background to avoid blocking
            new Thread(() -> {
                syncMessagesSequentially(stillPending, 0);
            }).start();
        }
    }
    
    /**
     * Sync messages one by one to avoid overwhelming the server
     */
    private void syncMessagesSequentially(List<Message> messages, int index) {
        if (index >= messages.size()) {
            synchronized (syncLock) {
                isSyncing = false;
            }
            Log.d(TAG, "Finished syncing all messages");
            return;
        }
        
        Message message = messages.get(index);
        
        // Double-check message is still pending before syncing
        // This prevents duplicate syncs if status was updated by another thread
        if (!messageRepository.isMessagePending(message.getId())) {
            Log.d(TAG, "Message " + message.getId() + " is no longer pending, skipping");
            syncMessagesSequentially(messages, index + 1);
            return;
        }

        final String tempMessageId = message.getId();
        if (isInSendTimeoutGrace(tempMessageId)) {
            Log.d(TAG, "Deferring POST retry during timeout grace: " + tempMessageId);
            syncMessagesSequentially(messages, index + 1);
            return;
        }

        final String nonce = message.getClientNonce();
        if (nonce != null && !nonce.isEmpty() && inFlightNonces.putIfAbsent(nonce, Boolean.TRUE) != null) {
            Log.d(TAG, "POST already in flight for nonce=" + nonce + ", skipping duplicate");
            syncMessagesSequentially(messages, index + 1);
            return;
        }

        Message existingServer = messageRepository.findMatchingServerMessage(message);
        if (existingServer != null) {
            Log.d(TAG, "SKIP_POST already synced temp=" + tempMessageId
                    + " real=" + existingServer.getId());
            messageRepository.resolvePendingWithServerMessage(tempMessageId, existingServer);
            clearSendTimeoutGrace(tempMessageId);
            if (pendingSyncListener != null) {
                pendingSyncListener.onMessageSynced(tempMessageId, existingServer);
            }
            syncMessagesSequentially(messages, index + 1);
            return;
        }

        syncSingleMessage(message, new SyncCallback() {
            @Override
            public void onSuccess(String newMessageId, Message serverMessage) {
                releaseInFlightNonce(nonce);
                synchronized (syncLock) {
                    messageRepository.updateSyncStatus(
                        tempMessageId,
                        newMessageId,
                        "synced",
                        null
                    );
                }

                Log.d(TAG, "Successfully synced message: " + tempMessageId + " -> " + newMessageId);

                clearSendTimeoutGrace(tempMessageId);

                if (pendingSyncListener != null && serverMessage != null) {
                    pendingSyncListener.onMessageSynced(tempMessageId, serverMessage);
                }

                syncMessagesSequentially(messages, index + 1);
            }

            @Override
            public void onFailure(String error) {
                releaseInFlightNonce(nonce);
                int attempts = messageRepository.incrementSyncAttempts(tempMessageId);
                synchronized (syncLock) {
                    if (attempts >= MAX_SYNC_ATTEMPTS) {
                        messageRepository.markMessageAsFailed(tempMessageId, error);
                        if (pendingSyncListener != null) {
                            pendingSyncListener.onMessageSyncFailed(tempMessageId, error);
                        }
                    } else {
                        messageRepository.markMessageAsPending(tempMessageId, error);
                    }
                }

                Log.e(TAG, "Failed to sync message: " + tempMessageId + " - " + error
                        + " (attempt " + attempts + "/" + MAX_SYNC_ATTEMPTS + ")");
                if (isTimeoutError(error) && attempts < MAX_SYNC_ATTEMPTS) {
                    markSendTimeout(tempMessageId);
                }
                syncMessagesSequentially(messages, index + 1);
            }
        });
    }

    private void releaseInFlightNonce(String nonce) {
        if (nonce != null && !nonce.isEmpty()) {
            inFlightNonces.remove(nonce);
        }
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
                                JSONObject msgJson = data != null ? data.optJSONObject("message") : null;
                                if (msgJson != null) {
                                    String newMessageId = msgJson.optString("_id", "");
                                    if (!newMessageId.isEmpty()) {
                                        Message serverMessage = Message.fromJson(msgJson);
                                        if ((serverMessage.getClientNonce() == null
                                                || serverMessage.getClientNonce().isEmpty())
                                                && message.getClientNonce() != null) {
                                            serverMessage.setClientNonce(message.getClientNonce());
                                        }
                                        boolean deduped = data != null && data.optBoolean("deduplicated", false);
                                        Log.d(TAG, "Message synced successfully: " + message.getId()
                                                + " -> " + newMessageId + (deduped ? " (deduped)" : ""));
                                        callback.onSuccess(newMessageId, serverMessage);
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
        void onSuccess(String newMessageId, Message serverMessage);
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

