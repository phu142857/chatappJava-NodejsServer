package com.example.chatappjava.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.chatappjava.ChatApplication;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.ui.call.RingingActivity;
import com.example.chatappjava.ui.theme.HomeActivity;
import com.example.chatappjava.ui.theme.PostDetailActivity;
import com.example.chatappjava.ui.theme.PrivateChatActivity;
import com.example.chatappjava.network.ApiClient;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "default";
    private static final String CHANNEL_NAME = "Chat Notifications";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        
        // Register token with server
        registerTokenWithServer(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Message data: " + remoteMessage.getData());

        // Check if message contains data payload
        if (remoteMessage.getData().size() > 0) {
            handleDataMessage(remoteMessage);
        }

        // Check if message contains notification payload
        if (remoteMessage.getNotification() != null) {
            handleNotificationMessage(remoteMessage);
        }
    }

    private void handleDataMessage(RemoteMessage remoteMessage) {
        String type = remoteMessage.getData().get("type");
        
        if (type == null) {
            return;
        }

        switch (type) {
            case "message":
                handleMessageNotification(remoteMessage);
                break;
            case "call":
                handleCallNotification(remoteMessage);
                break;
            case "friend_request":
                handleFriendRequestNotification(remoteMessage);
                break;
            case "post_notification":
                handlePostNotification(remoteMessage);
                break;
            default:
                // Show default notification
                showNotification(
                    remoteMessage.getNotification() != null 
                        ? remoteMessage.getNotification().getTitle() 
                        : "New Notification",
                    remoteMessage.getNotification() != null 
                        ? remoteMessage.getNotification().getBody() 
                        : "You have a new notification",
                    null
                );
        }
    }

    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            showNotification(
                notification.getTitle(),
                notification.getBody(),
                null
            );
        }
    }

    private void handleMessageNotification(RemoteMessage remoteMessage) {
        String title = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getTitle() 
            : "New Message";
        String body = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getBody() 
            : "You have a new message";
        String chatId = remoteMessage.getData().get("chatId");
        String chatType = remoteMessage.getData().get("chatType");

        Intent intent = new Intent(this, PrivateChatActivity.class);
        if (chatId != null) {
            try {
                Chat chat = new Chat();
                chat.setId(chatId);
                chat.setType(chatType != null ? chatType : "private");
                intent.putExtra("chat", chat.toJson().toString());
            } catch (JSONException e) {
                Log.e(TAG, "Error creating chat object", e);
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        showNotification(title, body, pendingIntent);
    }

    private void handleCallNotification(RemoteMessage remoteMessage) {
        String callId = remoteMessage.getData().get("callId");
        String callerId = remoteMessage.getData().get("callerId");
        String chatId = remoteMessage.getData().get("chatId");
        String callType = remoteMessage.getData().get("callType");
        String title = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getTitle() 
            : "Incoming Call";
        String body = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getBody() 
            : "You have an incoming call";

        Log.d(TAG, "Handling call notification: callId=" + callId + ", callerId=" + callerId + ", chatId=" + chatId + ", callType=" + callType);

        // Validate required data
        if (callId == null || callerId == null) {
            Log.e(TAG, "Missing required call data: callId=" + callId + ", callerId=" + callerId);
            return;
        }

        // Get socket manager
        com.example.chatappjava.network.SocketManager socketManager = 
            com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        boolean isSocketConnected = socketManager != null && socketManager.isConnected();
        
        // CRITICAL: Always process push notification to ensure RingingActivity is opened
        // Push notification is the reliable fallback when:
        // 1. Socket is not connected (app is closed/background)
        // 2. Socket event failed to open activity (race condition, app state issues)
        // 3. Socket event hasn't arrived yet (network delay)
        
        // Set activeCallId to prevent duplicate handling
        if (isSocketConnected && socketManager != null) {
            String activeCallId = socketManager.getActiveCallId();
            
            // Clear any old activeCallId that might block this call
            if (activeCallId != null && !activeCallId.equals(callId)) {
                Log.d(TAG, "Clearing old activeCallId: " + activeCallId);
                socketManager.clearActiveCallId(activeCallId);
            }
            
            // Set activeCallId for this call (even if already set, to ensure consistency)
            socketManager.setActiveCallId(callId);
            
            if (activeCallId != null && activeCallId.equals(callId)) {
                Log.d(TAG, "Call already active via socket, but push notification will ensure RingingActivity is displayed");
            }
        }

        // Create intent for RingingActivity
        Intent intent = new Intent(this, RingingActivity.class);
        try {
            // Use chatId if provided, otherwise use callId as fallback
            String chatIdToUse = (chatId != null && !chatId.isEmpty()) ? chatId : callId;
            
            Chat chat = new Chat();
            chat.setId(chatIdToUse);
            chat.setType("private");
            
            JSONObject callerJson = new JSONObject();
            callerJson.put("_id", callerId);
            
            intent.putExtra("chat", chat.toJson().toString());
            intent.putExtra("caller", callerJson.toString());
            intent.putExtra("callId", callId);
            intent.putExtra("callType", callType != null ? callType : "voice");
            intent.putExtra("isIncomingCall", true);
            
            // Add flags to launch from background/service
            // FLAG_ACTIVITY_NEW_TASK: Required to start activity from service/background
            // FLAG_ACTIVITY_CLEAR_TOP: If activity already exists, bring it to front instead of creating new
            // FLAG_ACTIVITY_SINGLE_TOP: Prevent multiple instances if activity is already on top
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                          Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                          Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            // CRITICAL: Always open RingingActivity from push notification immediately
            // This ensures the incoming call screen is displayed even if:
            // - Socket event failed to open it
            // - Socket event hasn't arrived yet
            // - App is in background/closed
            // - Race condition between socket and push notification
            Log.d(TAG, "Opening RingingActivity from push notification for callId: " + callId);
            
            // Use Handler to ensure activity is opened on main thread
            // This is required when starting activity from a service/background context
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    startActivity(intent);
                    Log.d(TAG, "Successfully opened RingingActivity from push notification");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open RingingActivity from push notification", e);
                    // Fall through to show notification as backup
                    showNotificationAsFallback(intent, title, body);
                }
            });
            
            // Don't show push notification immediately - we're trying to open activity
            // If activity open fails, fallback notification will be shown
            return;
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating call intent", e);
        }

        // Fallback: If we couldn't open activity, show notification as backup
        showNotificationAsFallback(intent, title, body);
    }
    
    private void showNotificationAsFallback(Intent intent, String title, String body) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        showNotification(title, body, pendingIntent);
    }

    private void handleFriendRequestNotification(RemoteMessage remoteMessage) {
        String title = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getTitle() 
            : "New Friend Request";
        String body = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getBody() 
            : "You have a new friend request";

        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("openTab", "friendRequests");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        showNotification(title, body, pendingIntent);
    }

    private void handlePostNotification(RemoteMessage remoteMessage) {
        String title = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getTitle() 
            : "New Post";
        String body = remoteMessage.getNotification() != null 
            ? remoteMessage.getNotification().getBody() 
            : "You have a new post notification";
        String postId = remoteMessage.getData().get("postId");

        Intent intent;
        if (postId != null) {
            intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra("postId", postId);
        } else {
            intent = new Intent(this, HomeActivity.class);
            intent.putExtra("openTab", "posts");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        showNotification(title, body, pendingIntent);
    }

    private void showNotification(String title, String body, PendingIntent pendingIntent) {
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You may need to create this icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL);

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent);
        }

        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for chat messages, calls, and other updates");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void registerTokenWithServer(String token) {
        ChatApplication app = (ChatApplication) getApplication();
        com.example.chatappjava.utils.DatabaseManager dbManager = app.getSharedPrefsManager();
        String userToken = dbManager.getToken();

        if (userToken == null || userToken.isEmpty()) {
            Log.d(TAG, "User not logged in, skipping token registration");
            return;
        }

        ApiClient apiClient = new ApiClient();
        apiClient.registerFCMToken(userToken, token, new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "FCM token registered successfully");
                } else {
                    Log.e(TAG, "Failed to register FCM token: " + response.code());
                }
                response.close();
            }

            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.e(TAG, "Error registering FCM token", e);
            }
        });
    }
}

