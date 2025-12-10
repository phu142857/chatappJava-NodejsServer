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
                            android.util.Log.d("PrivateChatActivity", "Call accepted");
                            isJoiningCall = false;
                            currentCallId = null;
                            updateCallUI(false);
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

            // Ensure otherUser is set from currentChat if not already set
            if (otherUser == null && currentChat.isPrivateChat()) {
                otherUser = currentChat.getOtherParticipant();
                android.util.Log.d("PrivateChatActivity", "Set otherUser from currentChat: " + 
                    (otherUser != null ? otherUser.getId() : "null"));
            }

            // Clean up avatar ImageView first to prevent showing wrong avatar
            if (ivProfile != null) {
                // Cancel any pending Picasso request
                com.squareup.picasso.Picasso.get().cancelRequest(ivProfile);
                // Clear the tag to reset state
                ivProfile.setTag(null);
                // Show placeholder immediately while loading
                ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
            }

            // Load other user's avatar
            if (otherUser != null) {
                String avatarUrl = otherUser.getAvatar();
                android.util.Log.d("PrivateChatActivity", "Loading avatar for user " + otherUser.getId() + 
                    ", avatar URL: " + avatarUrl);
                
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
                    if (ivProfile != null) {
                        ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                }
            } else {
                android.util.Log.d("PrivateChatActivity", "No otherUser, using placeholder");
                if (ivProfile != null) {
                    ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        } else {
            android.util.Log.d("PrivateChatActivity", "No currentChat");
            if (ivProfile != null) {
                com.squareup.picasso.Picasso.get().cancelRequest(ivProfile);
                ivProfile.setTag(null);
                ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
            }
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
        if (currentChat == null) {
            Toast.makeText(this, "Chat not loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get other user info
        String otherUserId = null;
        String otherUserName = null;
        String otherUserAvatar = null;
        
        String currentUserId = databaseManager.getUserId();
        
        // Try to get from participantIds
        if (currentChat.getParticipantIds() != null && !currentChat.getParticipantIds().isEmpty()) {
            for (String participantId : currentChat.getParticipantIds()) {
                if (participantId != null && !participantId.isEmpty() && !participantId.equals(currentUserId)) {
                    otherUserId = participantId;
                    break;
                }
            }
        }
        
        // Try from otherParticipant
        if (otherUserId == null && currentChat.getOtherParticipant() != null) {
            otherUserId = currentChat.getOtherParticipant().getId();
            otherUserName = currentChat.getOtherParticipant().getDisplayName();
            otherUserAvatar = currentChat.getOtherParticipant().getAvatar();
        }
        
        // Try from otherUser variable
        if (otherUserId == null && otherUser != null) {
            otherUserId = otherUser.getId();
            otherUserName = otherUser.getDisplayName();
            otherUserAvatar = otherUser.getAvatar();
        }
        
        if (otherUserId == null) {
            Toast.makeText(this, "Unable to get other user info", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Initiate call
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Make variables final for use in lambda
        final String finalOtherUserId = otherUserId;
        final String finalOtherUserName = otherUserName != null ? otherUserName : "Unknown";
        final String finalOtherUserAvatar = otherUserAvatar;
        final String finalChatId = currentChat.getId();
        
        // Call API to initiate call
        apiClient.initiateCall(token, currentChat.getId(), "video", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("PrivateChatActivity", "Failed to initiate call", e);
                    Toast.makeText(PrivateChatActivity.this, "Failed to start call", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        if (jsonResponse.optBoolean("success", false)) {
                            org.json.JSONObject data = jsonResponse.optJSONObject("data");
                            if (data != null) {
                                String callId = data.getString("callId");
                                
                                // Launch PrivateVideoCallActivity
                                Intent intent = new Intent(PrivateChatActivity.this, com.example.chatappjava.ui.call.PrivateVideoCallActivity.class);
                                intent.putExtra("callId", callId);
                                intent.putExtra("chatId", finalChatId);
                                intent.putExtra("remoteUserId", finalOtherUserId);
                                intent.putExtra("remoteUserName", finalOtherUserName);
                                intent.putExtra("remoteUserAvatar", finalOtherUserAvatar);
                                intent.putExtra("isCaller", true);
                                startActivity(intent);
                            }
                        } else {
                            String message = jsonResponse.optString("message", "Failed to start call");
                            Toast.makeText(PrivateChatActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (org.json.JSONException e) {
                        android.util.Log.e("PrivateChatActivity", "Error parsing call response", e);
                        Toast.makeText(PrivateChatActivity.this, "Error starting call", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void showChatInfo() {
        String currentUserId = databaseManager.getUserId();
        android.util.Log.d("PrivateChatActivity", "showChatInfo: currentUserId = " + currentUserId);
        
        if (currentChat == null || !currentChat.isPrivateChat()) {
            Toast.makeText(this, "Chat không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Always get the other participant ID from participantIds to ensure correctness
        String otherUserId = null;
        if (currentChat.getParticipantIds() != null && !currentChat.getParticipantIds().isEmpty()) {
            for (String participantId : currentChat.getParticipantIds()) {
                if (participantId != null && !participantId.isEmpty() && !participantId.equals(currentUserId)) {
                    otherUserId = participantId;
                    android.util.Log.d("PrivateChatActivity", "showChatInfo: Found other participant ID from participantIds: " + otherUserId);
                    break;
                }
            }
        }
        
        // If not found in participantIds, try from otherParticipant
        if (otherUserId == null && currentChat.getOtherParticipant() != null) {
            String otherParticipantId = currentChat.getOtherParticipant().getId();
            if (otherParticipantId != null && !otherParticipantId.isEmpty() && !otherParticipantId.equals(currentUserId)) {
                otherUserId = otherParticipantId;
                android.util.Log.d("PrivateChatActivity", "showChatInfo: Found other participant ID from otherParticipant: " + otherUserId);
            }
        }
        
        // If still not found, try from otherUser variable
        if (otherUserId == null && otherUser != null && otherUser.getId() != null) {
            String otherUserVarId = otherUser.getId();
            if (!otherUserVarId.isEmpty() && !otherUserVarId.equals(currentUserId)) {
                otherUserId = otherUserVarId;
                android.util.Log.d("PrivateChatActivity", "showChatInfo: Found other participant ID from otherUser: " + otherUserId);
            }
        }
        
        if (otherUserId == null || otherUserId.equals(currentUserId)) {
            android.util.Log.e("PrivateChatActivity", "showChatInfo: Cannot determine other user ID. currentUserId: " + 
                currentUserId + ", participantIds: " + (currentChat.getParticipantIds() != null ? currentChat.getParticipantIds().toString() : "null"));
            Toast.makeText(this, "Không thể xác định người dùng đối phương", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Verify otherUser object matches the ID before using it
        if (otherUser != null && otherUser.getId() != null && otherUser.getId().equals(otherUserId) && !otherUserId.equals(currentUserId)) {
            // Use existing otherUser object if it matches
            try {
                android.util.Log.d("PrivateChatActivity", "showChatInfo: Using existing otherUser object for ID: " + otherUserId);
                Intent intent = new Intent(this, ProfileViewActivity.class);
                intent.putExtra("user", otherUser.toJson().toString());
                startActivity(intent);
                return;
            } catch (JSONException e) {
                android.util.Log.w("PrivateChatActivity", "showChatInfo: Failed to serialize otherUser, fetching from server: " + e.getMessage());
            }
        }
        
        // Fetch user profile from server to ensure we have correct data
        android.util.Log.d("PrivateChatActivity", "showChatInfo: Fetching user profile from server for ID: " + otherUserId);
        fetchAndShowUserProfile(otherUserId);
    }
    
    private void fetchAndShowUserProfile(String userId) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.authenticatedGet("/api/users/" + userId, token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("PrivateChatActivity", "Failed to fetch user profile: " + e.getMessage());
                    Toast.makeText(PrivateChatActivity.this, "Không thể tải thông tin người dùng", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        org.json.JSONObject data = jsonResponse.optJSONObject("data");
                        org.json.JSONObject userData = null;
                        
                        if (data != null) {
                            if (data.has("user")) {
                                userData = data.optJSONObject("user");
                            } else {
                                userData = data;
                            }
                        }
                        
                        if (userData != null) {
                            org.json.JSONObject finalUserData = userData;
                            runOnUiThread(() -> {
                                try {
                                    Intent intent = new Intent(PrivateChatActivity.this, ProfileViewActivity.class);
                                    intent.putExtra("user", finalUserData.toString());
                                    android.util.Log.d("PrivateChatActivity", "Opening profile for fetched user: " + 
                                        finalUserData.optString("_id", finalUserData.optString("id", "")));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    android.util.Log.e("PrivateChatActivity", "Error opening profile: " + e.getMessage());
                                    Toast.makeText(PrivateChatActivity.this, "Lỗi mở profile", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(PrivateChatActivity.this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        android.util.Log.e("PrivateChatActivity", "Error parsing user data: " + e.getMessage());
                        runOnUiThread(() -> {
                            Toast.makeText(PrivateChatActivity.this, "Lỗi xử lý dữ liệu", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(PrivateChatActivity.this, "Không thể tải thông tin người dùng", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
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
        
        // Clean up avatar first to prevent showing wrong avatar
        if (ivProfile != null) {
            com.squareup.picasso.Picasso.get().cancelRequest(ivProfile);
            ivProfile.setTag(null);
        }
        
        // Reload user data when returning to activity
        // This ensures profile changes are reflected immediately
        if (otherUser != null) {
            // Reload other user's data from server
            loadOtherUserData();
        } else if (currentChat != null && currentChat.isPrivateChat()) {
            // If otherUser is null, try to get it from currentChat
            otherUser = currentChat.getOtherParticipant();
            if (otherUser != null) {
                updateUI();
            }
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
