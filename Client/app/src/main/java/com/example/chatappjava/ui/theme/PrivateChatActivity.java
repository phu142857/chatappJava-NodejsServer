package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

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
            // Clear any existing listeners to prevent conflicts
            socketManager.removeCallStatusListener();
            
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
                        // Reset UI if this event corresponds to current call
                        if (currentCallId != null && currentCallId.equals(callId)) {
                            android.util.Log.d("PrivateChatActivity", "Call declined, resetting UI");
                            Toast.makeText(PrivateChatActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                            com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
                            if (sm != null) sm.resetActiveCall();
                            currentCallId = null;
                            isJoiningCall = false; // Reset flag
                            updateCallUI(false); // Hide cancel button
                        } else {
                            // Fallback: if UI is showing calling state but callId tracking is lost, still reset
                            boolean isCancelVisible = ivCancelCall != null && ivCancelCall.getVisibility() == View.VISIBLE;
                            if (isCancelVisible) {
                                android.util.Log.w("PrivateChatActivity", "CallDeclined for different callId but UI shows calling. Forcing reset.");
                                Toast.makeText(PrivateChatActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                                com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
                                if (sm != null) sm.resetActiveCall();
                                currentCallId = null;
                                isJoiningCall = false;
                                updateCallUI(false);
                            }
                        }
                    });
                }
                
                @Override
                public void onCallEnded(String callId) {
                    runOnUiThread(() -> {
                        if (currentCallId != null && currentCallId.equals(callId) && !isJoiningCall) {
                            android.util.Log.d("PrivateChatActivity", "Call ended, resetting UI");
                            Toast.makeText(PrivateChatActivity.this, "Call ended", Toast.LENGTH_SHORT).show();
                            com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
                            if (sm != null) sm.resetActiveCall();
                            currentCallId = null;
                            updateCallUI(false); // Hide cancel button
                        }
                    });
                }
            });
        }
    }
    
    private void joinCallAndStartVideo(String callId) {
        String token = databaseManager.getToken();
        apiClient.joinCall(token, callId, new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("PrivateChatActivity", "Failed to join call", e);
                    Toast.makeText(PrivateChatActivity.this, "Failed to join call", Toast.LENGTH_SHORT).show();
                    isJoiningCall = false; // Reset flag on failure
                });
            }
            
            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
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
            String token = databaseManager.getToken();
            apiClient.endCall(token, currentCallId, new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        android.util.Log.e("PrivateChatActivity", "Failed to cancel call", e);
                        Toast.makeText(PrivateChatActivity.this, "Failed to cancel call", Toast.LENGTH_SHORT).show();
                        isJoiningCall = false; // Reset flag on failure
                    });
                }
                
                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(PrivateChatActivity.this, "Call cancelled", Toast.LENGTH_SHORT).show();
                        } else {
                            android.util.Log.e("PrivateChatActivity", "Failed to cancel call: " + response.code());
                            Toast.makeText(PrivateChatActivity.this, "Failed to cancel call", Toast.LENGTH_SHORT).show();
                        }
                        com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
                        if (sm != null) sm.resetActiveCall();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chat_options, null);
        
        // Show/hide options based on chat type (private chat)
        dialogView.findViewById(R.id.card_add_members).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_remove_members).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_leave_group).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_delete_group).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_unfriend).setVisibility(View.VISIBLE);
        
        // Set click listeners for each option
        dialogView.findViewById(R.id.option_view_info).setOnClickListener(v -> {
            showChatInfo();
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_delete_chat).setOnClickListener(v -> {
            confirmDeleteChat();
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_unfriend).setOnClickListener(v -> {
            confirmUnfriend();
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        builder.setView(dialogView);
        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            android.view.Window w = currentDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        currentDialog.show();
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
        String token = databaseManager.getToken();
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
        
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Delete Chat",
                "Are you sure you want to delete this chat? All messages will be lost.",
                "Delete",
                "Cancel",
                this::deleteChat,
                null,
                false
        );
    }
    
    private void deleteChat() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
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
        
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Unfriend",
                "Are you sure you want to unfriend " + otherUser.getDisplayName() + "?",
                "Unfriend",
                "Cancel",
                this::unfriendUser,
                null,
                false
        );
    }
    
    private void unfriendUser() {
        Toast.makeText(this, "Unfriending...", Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
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
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reload user data when returning to activity
        // This ensures profile changes are reflected immediately
        if (otherUser != null) {
            // Reload other user's data from server
            loadOtherUserData();
        }
        
        // Also reload current user's data to ensure consistency
        loadCurrentUserData();
    }
    
    private void loadOtherUserData() {
        if (otherUser == null) {
            android.util.Log.d("PrivateChatActivity", "loadOtherUserData: otherUser is null, skipping");
            return;
        }
        
        android.util.Log.d("PrivateChatActivity", "loadOtherUserData: Loading data for user " + otherUser.getId());
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            android.util.Log.d("PrivateChatActivity", "loadOtherUserData: No token available, skipping");
            return;
        }
        
        apiClient.authenticatedGet("/api/users/" + otherUser.getId(), token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                android.util.Log.e("PrivateChatActivity", "Failed to reload other user data: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                android.util.Log.d("PrivateChatActivity", "loadOtherUserData response: " + response.code());
                
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        android.util.Log.d("PrivateChatActivity", "loadOtherUserData response body: " + responseBody);
                        
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        org.json.JSONObject data = jsonResponse.optJSONObject("data");
                        if (data == null) {
                            android.util.Log.e("PrivateChatActivity", "Response data is null");
                            return;
                        }
                        
                        org.json.JSONObject userData = null;
                        // Try to get user from different formats
                        if (data.has("user")) {
                            userData = data.optJSONObject("user");
                        } else if (data.has("users")) {
                            // Response has users array (from search API)
                            org.json.JSONArray usersArray = data.optJSONArray("users");
                            if (usersArray != null && usersArray.length() > 0) {
                                userData = usersArray.getJSONObject(0);
                            }
                        } else {
                            // Data might be the user object directly
                            userData = data;
                        }
                        
                        if (userData == null) {
                            android.util.Log.e("PrivateChatActivity", "Cannot find user data in response");
                            return;
                        }
                        
                        org.json.JSONObject finalUserData = userData;
                        runOnUiThread(() -> {
                            try {
                                otherUser = User.fromJson(finalUserData);
                                android.util.Log.d("PrivateChatActivity", "Updated otherUser data: id=" + (otherUser != null ? otherUser.getId() : "null"));
                                
                                // If still no ID, try to get from _id field directly
                                if (otherUser != null && (otherUser.getId() == null || otherUser.getId().isEmpty())) {
                                    String userId = finalUserData.optString("_id", finalUserData.optString("id", ""));
                                    if (!userId.isEmpty()) {
                                        otherUser.setId(userId);
                                        android.util.Log.d("PrivateChatActivity", "Set userId manually: " + userId);
                                    }
                                }
                                
                                updateUI(); // Refresh the UI with updated data
                            } catch (JSONException e) {
                                android.util.Log.e("PrivateChatActivity", "Error parsing user from JSON: " + e.getMessage(), e);
                            }
                        });
                    } catch (Exception e) {
                        android.util.Log.e("PrivateChatActivity", "Error parsing other user data: " + e.getMessage(), e);
                    }
                } else {
                    android.util.Log.e("PrivateChatActivity", "Failed to load other user data: " + response.code());
                }
            }
        });
    }
    
    private void loadCurrentUserData() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        apiClient.getMe(token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                android.util.Log.e("PrivateChatActivity", "Failed to reload current user data: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        org.json.JSONObject userData = jsonResponse.getJSONObject("data").getJSONObject("user");
                        
                        runOnUiThread(() -> {
                            // Update shared preferences with new user info
                            databaseManager.saveLoginInfo(token, userData.toString());
                        });
                    } catch (Exception e) {
                        android.util.Log.e("PrivateChatActivity", "Error parsing current user data: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up socket listeners to prevent memory leaks
        if (socketManager != null) {
            socketManager.removeCallStatusListener();
        }
    }
}
