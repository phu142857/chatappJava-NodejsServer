package com.example.chatappjava;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.ui.call.RingingActivity;
import com.example.chatappjava.utils.SharedPreferencesManager;
import org.json.JSONException;

/**
 * Global Application class to manage Socket.io connection and incoming calls
 */
public class ChatApplication extends Application {
    private static final String TAG = "ChatApplication";
    private static ChatApplication instance;
    private SocketManager socketManager;
    private SharedPreferencesManager sharedPrefsManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // Initialize managers
        sharedPrefsManager = new SharedPreferencesManager(this);
        socketManager = SocketManager.getInstance();
        
        // Setup global socket connection
        setupGlobalSocketManager();
    }
    
    public static ChatApplication getInstance() {
        return instance;
    }
    
    public SocketManager getSocketManager() {
        return socketManager;
    }
    
    public SharedPreferencesManager getSharedPrefsManager() {
        return sharedPrefsManager;
    }
    
    private void setupGlobalSocketManager() {
        String token = sharedPrefsManager.getToken();
        String userId = sharedPrefsManager.getUserId();
        
        if (token != null && userId != null) {
            socketManager.connect(token, userId);
            
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
}
