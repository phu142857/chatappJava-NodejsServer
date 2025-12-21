package com.example.chatappjava;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.ui.call.RingingActivity;
import com.example.chatappjava.utils.DatabaseManager;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONException;

/**
 * Global Application class to manage Socket.io connection and incoming calls
 */
public class ChatApplication extends Application {
    private static final String TAG = "ChatApplication";
    private static ChatApplication instance;
    private SocketManager socketManager;
    private DatabaseManager databaseManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // Initialize managers
        databaseManager = new DatabaseManager(this);
        socketManager = SocketManager.getInstance();
        
        // Initialize FCM
        initializeFCM();
        
        // Setup global socket connection
        setupGlobalSocketManager();
    }
    
    public static ChatApplication getInstance() {
        return instance;
    }
    
    public SocketManager getSocketManager() {
        return socketManager;
    }
    
    public DatabaseManager getSharedPrefsManager() {
        return databaseManager;
    }
    
    private void setupGlobalSocketManager() {
        String token = databaseManager.getToken();
        String userId = databaseManager.getUserId();
        
        if (token != null && userId != null) {
            socketManager.connect(token, userId, getApplicationContext());
            
            // Set up global incoming call listener
            socketManager.setIncomingCallListener((callId, caller, chatId, callType) -> {
                Log.d(TAG, "Global incoming call received: " + callId);

                // Launch RingingActivity from any screen
                Intent intent = new Intent(getApplicationContext(), RingingActivity.class);
                try {
                    // Create a minimal chat object for the call
                    Chat chat = new Chat();
                    chat.setId(chatId);
                    chat.setType("private");

                    intent.putExtra("chat", chat.toJson().toString());
                    intent.putExtra("caller", caller.toJson().toString());
                    intent.putExtra("callId", callId);
                    intent.putExtra("callType", callType);
                    intent.putExtra("isIncomingCall", true);

                    // Add flags to launch from background
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    startActivity(intent);

                } catch (JSONException e) {
                    Log.e(TAG, "Error launching global incoming call", e);
                }
            });
        }
    }
    
    /**
     * Reconnect socket when user logs in
     */
    public void reconnectSocket() {
        setupGlobalSocketManager();
    }
    
    /**
     * Disconnect socket when user logs out
     */
    public void disconnectSocket() {
        if (socketManager != null) {
            socketManager.removeIncomingCallListener();
            socketManager.removeCallStatusListener();
            socketManager.disconnect();
        }
    }
    
    /**
     * Initialize Firebase Cloud Messaging
     */
    private void initializeFCM() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();
                Log.d(TAG, "FCM Registration Token: " + token);
                
                // Register token with server if user is logged in
                String userToken = databaseManager.getToken();
                if (userToken != null && !userToken.isEmpty()) {
                    registerFCMTokenWithServer(token);
                }
            });
    }
    
    /**
     * Register FCM token with server
     */
    private void registerFCMTokenWithServer(String fcmToken) {
        String userToken = databaseManager.getToken();
        if (userToken == null || userToken.isEmpty()) {
            Log.d(TAG, "User not logged in, skipping FCM token registration");
            return;
        }
        
        com.example.chatappjava.network.ApiClient apiClient = new com.example.chatappjava.network.ApiClient();
        apiClient.registerFCMToken(userToken, fcmToken, new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "FCM token registered successfully with server");
                } else {
                    Log.e(TAG, "Failed to register FCM token: " + response.code());
                }
                response.close();
            }

            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.e(TAG, "Error registering FCM token with server", e);
            }
        });
    }
    
    /**
     * Re-register FCM token (call this after login)
     */
    public void reRegisterFCMToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }

                String token = task.getResult();
                registerFCMTokenWithServer(token);
            });
    }
}
