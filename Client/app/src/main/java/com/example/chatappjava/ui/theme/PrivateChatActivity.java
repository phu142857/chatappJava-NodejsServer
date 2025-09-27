package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.chatappjava.ChatApplication;
import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.SocketManager;

import org.json.JSONException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;

public class PrivateChatActivity extends BaseChatActivity {
    private String currentCallId;
    private ImageView ivCancelCall;
    private boolean isJoiningCall = false; // Flag to prevent race conditions
    
    @Override
    protected int getLayoutResource() {
        return R.layout.activity_private_chat;
    }
    
    @Override
    protected void loadChatData() {
        updateUI();
        loadMessages();
    }
    
    @Override
    protected void initViews() {
        super.initViews();
        ivCancelCall = findViewById(R.id.iv_cancel_call);
    }
    
    @Override
    protected void setupClickListeners() {
        super.setupClickListeners();
        
        // Cancel call button
        if (ivCancelCall != null) {
            ivCancelCall.setOnClickListener(v -> cancelCall());
        }
    }
    
    @Override
    protected void setupSocketManager() {
        super.setupSocketManager();
        
        // Set up call status listener for outgoing calls using global SocketManager
        socketManager = ChatApplication.getInstance().getSocketManager();
        if (socketManager != null) {
            socketManager.setCallStatusListener(new SocketManager.CallStatusListener() {
                @Override
                public void onCallAccepted(String callId) {
                    runOnUiThread(() -> {
                        if (currentCallId != null && currentCallId.equals(callId) && !isJoiningCall) {
                            android.util.Log.d("PrivateChatActivity", "Call accepted, joining video call");
                            isJoiningCall = true; // Set flag to prevent race conditions
                            // B accepted the call, now A can join
                            joinCallAndStartVideo(callId);
                        }
                    });
                }
                
                @Override
                public void onCallDeclined(String callId) {
                    runOnUiThread(() -> {
                        android.util.Log.d("PrivateChatActivity", "Received call_declined event for callId: " + callId + ", currentCallId: " + currentCallId);
                        if (currentCallId != null && currentCallId.equals(callId)) {
                            android.util.Log.d("PrivateChatActivity", "Call declined, resetting UI");
                            Toast.makeText(PrivateChatActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                            currentCallId = null;
                            isJoiningCall = false; // Reset flag
                            updateCallUI(false); // Hide cancel button
                        }
                    });
                }
                
                @Override
                public void onCallEnded(String callId) {
                    runOnUiThread(() -> {
                        if (currentCallId != null && currentCallId.equals(callId) && !isJoiningCall) {
                            android.util.Log.d("PrivateChatActivity", "Call ended, resetting UI");
                            Toast.makeText(PrivateChatActivity.this, "Call ended", Toast.LENGTH_SHORT).show();
                            currentCallId = null;
                            updateCallUI(false); // Hide cancel button
                        }
                    });
                }
            });
        }
    }
    
    private void joinCallAndStartVideo(String callId) {
        String token = sharedPrefsManager.getToken();
        apiClient.joinCall(token, callId, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("PrivateChatActivity", "Failed to join call", e);
                    Toast.makeText(PrivateChatActivity.this, "Failed to join call", Toast.LENGTH_SHORT).show();
                    isJoiningCall = false; // Reset flag on failure
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            // Launch VideoCallActivity
                            Intent intent = new Intent(PrivateChatActivity.this, com.example.chatappjava.ui.call.VideoCallActivity.class);
                            intent.putExtra("chat", currentChat.toJson().toString());
                            intent.putExtra("caller", otherUser.toJson().toString());
                            intent.putExtra("callId", callId);
                            intent.putExtra("callType", "video");
                            intent.putExtra("isIncomingCall", false);
                            
                            startActivity(intent);
                            currentCallId = null;
                            isJoiningCall = false; // Reset flag
                            updateCallUI(false); // Hide cancel button
                        } catch (Exception e) {
                            android.util.Log.e("PrivateChatActivity", "Error launching video call", e);
                            Toast.makeText(PrivateChatActivity.this, "Error starting video call", Toast.LENGTH_SHORT).show();
                            isJoiningCall = false; // Reset flag on error
                        }
                    } else {
                        android.util.Log.e("PrivateChatActivity", "Failed to join call: " + response.code());
                        Toast.makeText(PrivateChatActivity.this, "Failed to join call", Toast.LENGTH_SHORT).show();
                        isJoiningCall = false; // Reset flag on error
                    }
                });
            }
        });
    }
    
    private void updateCallUI(boolean isCalling) {
        if (ivVideoCall != null && ivCancelCall != null) {
            if (isCalling) {
                ivVideoCall.setVisibility(View.GONE);
                ivCancelCall.setVisibility(View.VISIBLE);
            } else {
                ivVideoCall.setVisibility(View.VISIBLE);
                ivCancelCall.setVisibility(View.GONE);
            }
        }
    }
    
    private void cancelCall() {
        if (currentCallId != null) {
            String token = sharedPrefsManager.getToken();
            apiClient.endCall(token, currentCallId, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        android.util.Log.e("PrivateChatActivity", "Failed to cancel call", e);
                        Toast.makeText(PrivateChatActivity.this, "Failed to cancel call", Toast.LENGTH_SHORT).show();
                        isJoiningCall = false; // Reset flag on failure
                    });
                }
                
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(PrivateChatActivity.this, "Call cancelled", Toast.LENGTH_SHORT).show();
                        } else {
                            android.util.Log.e("PrivateChatActivity", "Failed to cancel call: " + response.code());
                            Toast.makeText(PrivateChatActivity.this, "Failed to cancel call", Toast.LENGTH_SHORT).show();
                        }
                        currentCallId = null;
                        isJoiningCall = false; // Reset flag
                        updateCallUI(false); // Hide cancel button
                    });
                }
            });
        }
    }
    
    @Override
    protected void updateUI() {
        if (currentChat != null) {
            tvChatName.setText(currentChat.getDisplayName());
            tvStatus.setText("Online"); // You can implement real-time status here
            
            // Load other user's avatar
            if (otherUser != null) {
                String avatarUrl = otherUser.getAvatar();
                android.util.Log.d("PrivateChatActivity", "User avatar URL: " + avatarUrl);
                
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    // Construct full URL if needed
                    if (!avatarUrl.startsWith("http")) {
                        avatarUrl = "http://" + ServerConfig.getServerIp() +
                                   ":" + ServerConfig.getServerPort() + avatarUrl;
                        android.util.Log.d("PrivateChatActivity", "Constructed full URL: " + avatarUrl);
                    }
                    android.util.Log.d("PrivateChatActivity", "Loading avatar with AvatarManager");
                    avatarManager.loadAvatar(avatarUrl, ivProfile, R.drawable.ic_profile_placeholder);
                } else {
                    android.util.Log.d("PrivateChatActivity", "No avatar URL, using placeholder");
                    ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
                }
            } else {
                android.util.Log.d("PrivateChatActivity", "No otherUser, using placeholder");
                ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } else {
            android.util.Log.d("PrivateChatActivity", "No currentChat");
        }
    }

    @Override
    protected void showChatOptions() {
        String[] options = {"View Profile", "Delete Chat", "Unfriend"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chat Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showChatInfo();
                            break;
                        case 1:
                            confirmDeleteChat();
                            break;
                        case 2:
                            confirmUnfriend();
                            break;
                    }
                });
        builder.show();
    }
    
    @Override
    protected void handleSendMessage() {
        String content = etMessage.getText().toString().trim();
        if (!content.isEmpty()) {
            sendMessage(content);
        }
    }
    
    @Override
    protected void handleVideoCall() {
        if (otherUser == null) {
            Toast.makeText(this, "Cannot perform video call", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Start video call immediately
        initiateVideoCall();
    }
    
    private void initiateVideoCall() {
        Toast.makeText(this, "Calling...", Toast.LENGTH_SHORT).show();
        
        // Create call on server first
        String token = sharedPrefsManager.getToken();
        String chatId = currentChat.getId();
        
        apiClient.initiateCall(token, chatId, "video", new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("PrivateChatActivity", "Failed to initiate call", e);
                    Toast.makeText(PrivateChatActivity.this, "Failed to start call", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        String callId = jsonResponse.getString("callId");
                        
                        // Store callId for later use when B accepts
                        currentCallId = callId;
                        
                        runOnUiThread(() -> {
                            Toast.makeText(PrivateChatActivity.this, "Calling " + otherUser.getUsername() + "...", Toast.LENGTH_LONG).show();
                            updateCallUI(true); // Show cancel button
                        });
                    } else {
                        final int statusCode = response.code();
                        runOnUiThread(() -> {
                            android.util.Log.e("PrivateChatActivity", "Failed to initiate call: " + statusCode);
                            Toast.makeText(PrivateChatActivity.this, "Failed to start call", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.e("PrivateChatActivity", "Error parsing call response", e);
                    runOnUiThread(() -> {
                        Toast.makeText(PrivateChatActivity.this, "Error starting call", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void showChatInfo() {
        if (otherUser != null) {
            // For private chat, show other user's profile
            Intent intent = new Intent(this, ProfileViewActivity.class);
            try {
                intent.putExtra("user", otherUser.toJson().toString());
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error opening profile", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No user information available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void confirmDeleteChat() {
        if (currentChat == null) {
            Toast.makeText(this, "Chat not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete this chat? All messages will be lost.")
                .setPositiveButton("Delete", (dialog, which) -> deleteChat())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void deleteChat() {
        Toast.makeText(this, "Deleting chat...", Toast.LENGTH_SHORT).show();
        
        String token = sharedPrefsManager.getToken();
        apiClient.deleteChat(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(PrivateChatActivity.this, "Chat deleted successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(PrivateChatActivity.this, "Failed to delete chat", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(PrivateChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void confirmUnfriend() {
        if (otherUser == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Unfriend")
                .setMessage("Are you sure you want to unfriend " + otherUser.getDisplayName() + "?")
                .setPositiveButton("Unfriend", (dialog, which) -> unfriendUser())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void unfriendUser() {
        Toast.makeText(this, "Unfriending...", Toast.LENGTH_SHORT).show();
        
        String token = sharedPrefsManager.getToken();
        String otherUserId = otherUser != null ? otherUser.getId() : 
                           (currentChat.getOtherParticipant() != null ? currentChat.getOtherParticipant().getId() : null);
        
        if (otherUserId == null) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.unfriendUser(token, otherUserId, new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.code() == 200) {
                        Toast.makeText(PrivateChatActivity.this, "Unfriended successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(PrivateChatActivity.this, "Failed to unfriend", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(PrivateChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
