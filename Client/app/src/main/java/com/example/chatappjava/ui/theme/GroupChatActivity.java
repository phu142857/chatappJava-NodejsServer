package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.ui.call.GroupVideoCallActivity;
import com.github.dhaval2404.imagepicker.ImagePicker;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class GroupChatActivity extends BaseChatActivity {

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Uri selectedImageUri;
    private boolean isChangingAvatar = false;
    private AlertDialog imageSelectDialog;
    private LinearLayout groupCallNotification;
    private TextView tvGroupCallNotificationText;
    private String activeCallId = null;
    
    @Override
    protected int getLayoutResource() {
        return R.layout.activity_private_chat; // Reuse the same layout for now
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImagePicker();
        ensureSocketConnection();
        
        // Initialize notification view after layout is set
        // Use post to ensure layout is fully inflated
        findViewById(android.R.id.content).post(() -> {
            setupGroupCallNotification();
            checkForActiveGroupCall();
        });
    }
    
    private void ensureSocketConnection() {
        if (socketManager != null) {
            boolean isConnected = socketManager.isConnected();
            android.util.Log.d("GroupChatActivity", "Socket connection status: " + isConnected);
            
            if (!isConnected) {
                android.util.Log.w("GroupChatActivity", "Socket not connected, attempting to reconnect...");
                
                // Get credentials from database
                String token = databaseManager.getToken();
                String userId = databaseManager.getUserId();
                
                if (token != null && !token.isEmpty() && userId != null && !userId.isEmpty()) {
                    socketManager.connect(token, userId, GroupChatActivity.this);
                    
                    // Give it a moment to connect
                    new android.os.Handler().postDelayed(() -> {
                        boolean nowConnected = socketManager.isConnected();
                        android.util.Log.d("GroupChatActivity", "Socket connection after retry: " + nowConnected);
                        if (!nowConnected) {
                            Toast.makeText(this, "Real-time updates may be delayed", Toast.LENGTH_SHORT).show();
                        } else {
                            android.util.Log.d("GroupChatActivity", "Socket reconnected successfully");
                        }
                    }, 1000);
                } else {
                    android.util.Log.e("GroupChatActivity", "Cannot reconnect: missing token or userId");
                }
            } else {
                android.util.Log.d("GroupChatActivity", "Socket already connected");
            }
        } else {
            android.util.Log.e("GroupChatActivity", "SocketManager is null!");
        }
    }
    
    @Override
    protected void handleVideoCall() {
        android.util.Log.d("GroupChatActivity", "handleVideoCall called");
        
        // Check if we have a valid chat
        if (currentChat == null || currentChat.getId() == null) {
            android.util.Log.e("GroupChatActivity", "Cannot start call: chat not loaded");
            Toast.makeText(this, "Unable to start call: chat not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // CRITICAL FIX: Always check server for active call first (even if activeCallId is set)
        // This ensures we join existing calls instead of creating new ones
        // The activeCallId might be stale, or user might have missed the notification
        android.util.Log.d("GroupChatActivity", "Always checking server for active call before proceeding...");
        checkForActiveCallBeforeStarting();
    }
    
    private void checkForActiveCallBeforeStarting() {
        if (currentChat == null) {
            android.util.Log.e("GroupChatActivity", "Cannot check for active call: currentChat is null");
            startNewGroupCall();
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            android.util.Log.e("GroupChatActivity", "Cannot check for active call: no token");
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("GroupChatActivity", "Checking for active call in chat: " + currentChat.getId());
        
        apiClient.authenticatedGet("/api/group-calls/chat/" + currentChat.getId() + "/active", token, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                android.util.Log.e("GroupChatActivity", "Failed to check for active call", e);
                runOnUiThread(() -> {
                    // If check fails, start new call (better UX than blocking)
                    android.util.Log.d("GroupChatActivity", "Check failed, starting new call");
                    startNewGroupCall();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                android.util.Log.d("GroupChatActivity", "Active call check response: " + response.code());
                android.util.Log.d("GroupChatActivity", "Response body: " + responseBody);
                
                runOnUiThread(() -> {
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                // CRITICAL FIX: Use optJSONObject to safely handle null data
                                JSONObject data = jsonResponse.optJSONObject("data");
                                if (data != null && data.has("callId") && !data.isNull("callId")) {
                                    String existingCallId = data.getString("callId");
                                    String status = data.optString("status", "");
                                    
                                    // CRITICAL: Always join if call exists and is in any active state
                                    // Don't check status too strictly - if call exists, join it
                                    if ("active".equals(status) || "notified".equals(status) || "ringing".equals(status) || "initiated".equals(status)) {
                                        android.util.Log.d("GroupChatActivity", "Found active call: " + existingCallId + " (status: " + status + "), joining");
                                        activeCallId = existingCallId; // Update local state
                                        joinActiveGroupCall();
                                        return;
                                    } else {
                                        android.util.Log.w("GroupChatActivity", "Call exists but status is not active: " + status);
                                    }
                                } else {
                                    android.util.Log.d("GroupChatActivity", "No active call found (data is null or missing callId)");
                                }
                            }
                        } catch (JSONException e) {
                            android.util.Log.e("GroupChatActivity", "Error parsing active call response", e);
                            e.printStackTrace();
                        }
                    }
                    
                    // No active call found, start new one
                    android.util.Log.d("GroupChatActivity", "No active call found, starting new call");
                    startNewGroupCall();
                });
            }
        });
    }
    
    private void startNewGroupCall() {
        android.util.Log.d("GroupChatActivity", "Starting NEW group call");
        
        if (currentChat == null || currentChat.getId() == null) {
            Toast.makeText(this, "Unable to start call: chat not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start a new group video call
        Intent intent = new Intent(this, GroupVideoCallActivity.class);
        intent.putExtra("chatId", currentChat.getId());
        intent.putExtra("groupName", currentChat.getName());
        intent.putExtra("isCaller", true); // Starting new call
        intent.putExtra("callType", "video");
        // Note: No callId - this will create a new call
        
        try {
            startActivity(intent);
            android.util.Log.d("GroupChatActivity", "Started new call activity");
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Failed to start call activity", e);
            Toast.makeText(this, "Failed to start call: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    
    @Override
    protected void showAttachmentOptions() {
        // Use BaseChatActivity logic for chat images
        isChangingAvatar = false;
        super.showAttachmentOptions();
    }
    
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        if (isChangingAvatar) {
                            uploadGroupAvatar();
                        } else {
                            // Handle chat image using BaseChatActivity logic
                            handleSelectedImage(selectedImageUri);
                        }
                    }
                } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                    Toast.makeText(GroupChatActivity.this, ImagePicker.getError(result.getData()), Toast.LENGTH_SHORT).show();
                }
            }
        );
    }
    
    @Override
    protected void setupRecyclerView() {
        super.setupRecyclerView();
        // Set group chat mode for message adapter
        if (messageAdapter != null) {
            messageAdapter.setGroupChat(true);
            android.util.Log.d("GroupChatActivity", "Set isGroupChat = true for MessageAdapter");
        } else {
            android.util.Log.e("GroupChatActivity", "MessageAdapter is null!");
        }
    }
    
    @Override
    protected void loadChatData() {
        android.util.Log.d("GroupChatActivity", "loadChatData called");
        android.util.Log.d("GroupChatActivity", "currentChat: " + (currentChat != null ? currentChat.getName() : "null"));
        android.util.Log.d("GroupChatActivity", "ivProfile: " + (ivProfile != null ? "not null" : "null"));
        android.util.Log.d("GroupChatActivity", "avatarManager: " + (avatarManager != null ? "not null" : "null"));
        
        updateUI();
        loadMessages();
    }
    
    @Override
    protected void updateUI() {
        if (currentChat != null) {
            tvChatName.setText(currentChat.getName());
            
            // Show initial participant count from chat data
            int participantCount = currentChat.getParticipantCount();
            android.util.Log.d("GroupChatActivity", "Initial participant count: " + participantCount);
            
            // Fetch actual member count from server (like GroupMembersActivity does)
            fetchActualMemberCount();
            
            // Load group avatar
            String avatarUrl = currentChat.getFullAvatarUrl();
            android.util.Log.d("GroupAvatar", "Group avatar URL: " + avatarUrl);
            android.util.Log.d("GroupAvatar", "Current chat avatar field: " + currentChat.getAvatar());
            
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                android.util.Log.d("GroupAvatar", "Loading group avatar: " + avatarUrl);
                if (avatarManager != null) {
                    avatarManager.loadAvatar(avatarUrl, ivProfile, R.drawable.ic_group_avatar);
                } else {
                    android.util.Log.e("GroupAvatar", "AvatarManager is null!");
                    ivProfile.setImageResource(R.drawable.ic_group_avatar);
                }
            } else {
                android.util.Log.d("GroupAvatar", "No group avatar, using placeholder");
                ivProfile.setImageResource(R.drawable.ic_group_avatar);
            }
            
            // Disable changing avatar from group chat avatar click
            if (ivProfile != null) {
                ivProfile.setOnClickListener(null);
            }
        } else {
            android.util.Log.d("GroupAvatar", "Current chat is null");
        }
    }
    
    @Override
    protected void showChatOptions() {
        // Open Group Settings Activity instead of showing dialog
        openGroupSettings();
    }
    
    private void openGroupSettings() {
        Intent intent = new Intent(this, GroupSettingsActivity.class);
        try {
            intent.putExtra("chat", currentChat.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening group settings", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showChatOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group_chat_options, null);

        // Load and display join requests count if available
        TextView tvRequestCount = dialogView.findViewById(R.id.tv_request_count);
        View optionJoinRequests = dialogView.findViewById(R.id.option_join_requests);
        if (optionJoinRequests != null) {
            optionJoinRequests.setOnClickListener(v -> {
                if (currentDialog != null) currentDialog.dismiss();
                if (currentChat == null) return;
                Intent intent = new Intent(this, GroupJoinRequestsActivity.class);
                try {
                    intent.putExtra("chat", currentChat.toJson().toString());
                } catch (org.json.JSONException ignored) {}
                startActivity(intent);
            });
            fetchJoinRequestCount(count -> {
                if (count > 0) {
                    tvRequestCount.setText(String.valueOf(count));
                    tvRequestCount.setVisibility(View.VISIBLE);
                } else {
                    tvRequestCount.setVisibility(View.GONE);
                }
            });
        }


        // Set click listeners for each option
        dialogView.findViewById(R.id.option_view_group_info).setOnClickListener(v -> {
            showGroupInfo();
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_delete_group).setOnClickListener(v -> {
            confirmDeleteGroup();
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        // Remove/disable call options for group chat if present in layout


        builder.setView(dialogView);
        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            android.view.Window w = currentDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        currentDialog.show();
    }

    private interface CountCallback { void onResult(int count); }

    private void fetchJoinRequestCount(CountCallback cb) {
        try {
            if (currentChat == null) { cb.onResult(0); return; }
            String token = databaseManager.getToken();
            if (token == null || token.isEmpty()) { cb.onResult(0); return; }
            // Placeholder endpoint; implement on server to return { success, data: { count } }
            String chatId = currentChat.getId();
            apiClient.authenticatedGet("/api/groups/" + chatId + "/join-requests/count", token, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) { runOnUiThread(() -> cb.onResult(0)); }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            if (response.code() == 200 && json.optBoolean("success", false)) {
                                int count = json.getJSONObject("data").optInt("count", 0);
                                cb.onResult(count);
                            } else { cb.onResult(0); }
                        } catch (org.json.JSONException e) { cb.onResult(0); }
                    });
                }
            });
        } catch (Exception e) { cb.onResult(0); }
    }
    
    @Override
    protected void handleSendMessage() {
        String content = etMessage.getText().toString().trim();
        if (!content.isEmpty()) {
            sendMessage(content);
        }
    }
    
    private void showGroupInfo() {
        if (currentChat == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Group Information")
                .setMessage("Group Name: " + currentChat.getName() + "\n" +
                           "Members: " + currentChat.getParticipantCount() + "\n" +
                           "Created: " + (currentChat.getCreatedAt() != 0 ? new Date(currentChat.getCreatedAt()).toString() : "Unknown"))
                .setPositiveButton("View Members", (dialog, which) -> {
                    // Navigate to GroupMembersActivity
                    Intent intent = new Intent(this, GroupMembersActivity.class);
                    try {
                        intent.putExtra("chat", currentChat.toJson().toString());
                        startActivity(intent);
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error opening members list", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Change Avatar", (dialog, which) -> {
                    showAvatarOptions();
                })
                .setNegativeButton("Close", null)
                .show();
    }
    
    private void showAvatarOptions() {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attachment_select, null);
        
        // Get views
        LinearLayout cameraOption = dialogView.findViewById(R.id.option_camera);
        LinearLayout galleryOption = dialogView.findViewById(R.id.option_gallery);
        LinearLayout cancelButton = dialogView.findViewById(R.id.btn_cancel);
        LinearLayout removeAvatarCard = dialogView.findViewById(R.id.option_remove_avatar);
        LinearLayout removeAvatarButton = dialogView.findViewById(R.id.btn_remove_avatar);
        
        // Show remove avatar option
        if (removeAvatarCard != null) {
            removeAvatarCard.setVisibility(View.VISIBLE);
        }
        
        // Update title
        TextView titleView = dialogView.findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText("Group Avatar - All members can change");
        }
        
        // Set click listeners
        cameraOption.setOnClickListener(v -> {
            isChangingAvatar = true;
            ImagePicker.with(this)
                    .cameraOnly()
                    .crop()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .createIntent(intent -> {
                        imagePickerLauncher.launch(intent);
                        return null;
                    });
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        galleryOption.setOnClickListener(v -> {
            isChangingAvatar = true;
            ImagePicker.with(this)
                    .galleryOnly()
                    .crop()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .createIntent(intent -> {
                        imagePickerLauncher.launch(intent);
                        return null;
                    });
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        if (removeAvatarButton != null) {
            removeAvatarButton.setOnClickListener(v -> {
                confirmRemoveAvatar();
                if (imageSelectDialog != null) {
                    imageSelectDialog.dismiss();
                }
            });
        }
        
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                if (imageSelectDialog != null) {
                    imageSelectDialog.dismiss();
                }
            });
        }
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);
        
        imageSelectDialog = builder.create();
        
        // Set transparent background to avoid white area issues
        if (imageSelectDialog.getWindow() != null) {
            android.view.Window w = imageSelectDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        imageSelectDialog.show();
    }
    
    private void confirmRemoveAvatar() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Avatar")
                .setMessage("Are you sure you want to remove the group avatar?")
                .setPositiveButton("Remove", (dialog, which) -> removeAvatar())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void removeAvatar() {
        Toast.makeText(this, "Removing avatar...", Toast.LENGTH_SHORT).show();
        
        // For now, just show a message
        // In a real implementation, you would call an API to remove the avatar
        Toast.makeText(this, "Remove avatar feature coming soon", Toast.LENGTH_SHORT).show();
    }
    
    private void uploadGroupAvatar() {
        if (selectedImageUri == null || currentChat == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Uploading avatar...", Toast.LENGTH_SHORT).show();
        
        try {
            // Convert URI to File
            java.io.File imageFile = new java.io.File(selectedImageUri.getPath());
            
            String token = databaseManager.getToken();
            apiClient.uploadGroupAvatar(token, currentChat.getId(), imageFile, new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupChatActivity.this, "Failed to upload avatar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws java.io.IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        if (response.code() == 200) {
                            try {
                                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    String avatarUrl = jsonResponse.getJSONObject("data").getString("avatarUrl");
                                    
                                    android.util.Log.d("GroupAvatar", "Avatar upload response: " + avatarUrl);
                                    
                                    // Update current chat with new avatar URL
                                    currentChat.setAvatar(avatarUrl);
                                    
                                    android.util.Log.d("GroupAvatar", "Updated chat avatar: " + currentChat.getAvatar());
                                    
                                    // Refresh the UI with new avatar
                                    updateUI();
                                    
                                    Toast.makeText(GroupChatActivity.this, "Avatar updated successfully", Toast.LENGTH_SHORT).show();
                                    isChangingAvatar = false;
                                } else {
                                    Toast.makeText(GroupChatActivity.this, "Failed to upload avatar: " + jsonResponse.optString("message"), Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(GroupChatActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(GroupChatActivity.this, "Failed to upload avatar: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                        isChangingAvatar = false;
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing image for upload", Toast.LENGTH_SHORT).show();
            isChangingAvatar = false;
        }
    }
    
    private void testPublicImage() {
        android.util.Log.d("GroupAvatar", "Testing with public image");
        String publicImageUrl = "https://via.placeholder.com/150x150/0000FF/FFFFFF?text=TEST";
        
        com.squareup.picasso.Picasso.get()
                .load(publicImageUrl)
                .placeholder(R.drawable.ic_group_avatar)
                .error(R.drawable.ic_group_avatar)
                .into(ivProfile, new com.squareup.picasso.Callback() {
                    @Override
                    public void onSuccess() {
                        android.util.Log.d("GroupAvatar", "Public image SUCCESS: " + publicImageUrl);
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("GroupAvatar", "Public image FAILED: " + publicImageUrl, e);
                    }
                });
    }

    private void addMembers() {
        // Navigate to contact selection for adding members
        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra("mode", "add_members");

        try {
            intent.putExtra("chat", currentChat.toJson().toString());
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error converting chat to JSON", Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    
    private void removeMembers() {
        // Navigate to GroupMembersActivity for removing members
        Intent intent = new Intent(this, GroupMembersActivity.class);
        try {
            intent.putExtra("chat", currentChat.toJson().toString());
            startActivity(intent);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening members list", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void confirmLeaveGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Group")
                .setMessage("Are you sure you want to leave this group? You will no longer receive messages from this group.")
                .setPositiveButton("Leave", (dialog, which) -> leaveGroup())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void leaveGroup() {
        if (currentChat == null) return;
        
        Toast.makeText(this, "Leaving group...", Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
        apiClient.leaveGroup(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            
                            if (jsonResponse.optBoolean("deleted", false)) {
                                // Group was deleted
                                String message = jsonResponse.optString("message", "Group deleted successfully");
                                Toast.makeText(GroupChatActivity.this, message, Toast.LENGTH_LONG).show();
                            } else {
                                // Normal leave
                                Toast.makeText(GroupChatActivity.this, "Left group successfully", Toast.LENGTH_SHORT).show();
                            }
                            
                            // Add small delay to ensure server processing is complete
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                finish(); // Close the chat activity
                            }, 500);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(GroupChatActivity.this, "Left group successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    } else {
                        Toast.makeText(GroupChatActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void confirmDeleteGroup() {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            "Delete Group",
            "Are you sure you want to delete this group? This action cannot be undone and all messages will be lost.",
            "Delete",
            "Cancel",
            this::deleteGroup,
            null,
            false
        );
    }
    
    private void deleteGroup() {
        if (currentChat == null) return;
        
        Toast.makeText(this, "Deleting group...", Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
        apiClient.deleteChat(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(GroupChatActivity.this, "Group deleted successfully", Toast.LENGTH_SHORT).show();
                        finish(); // Close the chat activity
                    } else {
                        Toast.makeText(GroupChatActivity.this, "Failed to delete group", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reload group data when returning to activity
        // This ensures group changes are reflected immediately
        if (currentChat != null) {
            loadGroupData();
            checkForActiveGroupCall();
        }
    }
    
    private void setupGroupCallNotification() {
        groupCallNotification = findViewById(R.id.group_call_notification);
        tvGroupCallNotificationText = findViewById(R.id.tv_group_call_notification_text);
        
        android.util.Log.d("GroupChatActivity", "setupGroupCallNotification:");
        android.util.Log.d("GroupChatActivity", "  groupCallNotification: " + (groupCallNotification != null ? "found" : "NULL"));
        android.util.Log.d("GroupChatActivity", "  tvGroupCallNotificationText: " + (tvGroupCallNotificationText != null ? "found" : "NULL"));
        
        if (groupCallNotification != null) {
            groupCallNotification.setOnClickListener(v -> joinActiveGroupCall());
            android.util.Log.d("GroupChatActivity", "Notification view initialized and click listener set");
        } else {
            android.util.Log.e("GroupChatActivity", "CRITICAL: group_call_notification view not found in layout!");
        }
        
        // Listen for socket events about group calls
        setupGroupCallSocketListeners();
    }
    
    private void setupGroupCallSocketListeners() {
        if (socketManager == null) {
            android.util.Log.e("GroupChatActivity", "SocketManager is null, cannot setup listeners");
            return;
        }
        
        android.util.Log.d("GroupChatActivity", "Setting up group call socket listeners");
        android.util.Log.d("GroupChatActivity", "Current chat: " + (currentChat != null ? currentChat.getId() : "null"));
        
        // Remove existing listeners to avoid duplicates
        socketManager.off("group_call_passive_alert");
        socketManager.off("group_call_passive_alert_broadcast");
        socketManager.off("group_call_started");
        socketManager.off("call_ended");
        socketManager.off("group_call_participant_joined");
        socketManager.off("group_call_participant_left");
        
        // LISTENER 1: Primary event - direct to user room
        socketManager.on("group_call_passive_alert", args -> {
            android.util.Log.d("GroupChatActivity", "Received group_call_passive_alert");
            handleGroupCallAlert(args);
        });
        
        // LISTENER 2: Broadcast event - to all connected clients
        socketManager.on("group_call_passive_alert_broadcast", args -> {
            android.util.Log.d("GroupChatActivity", "Received group_call_passive_alert_broadcast");
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String targetChatId = data.optString("targetChatId", "");
                    
                    // Filter: only process if this is for our chat
                    if (currentChat != null && currentChat.getId().equals(targetChatId)) {
                        android.util.Log.d("GroupChatActivity", "Broadcast matches our chat, processing");
                        handleGroupCallAlert(args);
                    } else {
                        android.util.Log.d("GroupChatActivity", "Broadcast for different chat, ignoring");
                    }
                } catch (Exception e) {
                    android.util.Log.e("GroupChatActivity", "Error filtering broadcast", e);
                }
            }
        });
        
        // LISTENER 3: Alternative event name (group_call_started)
        socketManager.on("group_call_started", args -> {
            android.util.Log.d("GroupChatActivity", "Received group_call_started");
            handleGroupCallAlert(args);
        });
        
        // Continue with other listeners
        continueSetupGroupCallSocketListeners();
        
        android.util.Log.d("GroupChatActivity", "All group call socket listeners registered");
    }
    
    private void handleGroupCallAlert(Object[] args) {
        android.util.Log.d("GroupChatActivity", "handleGroupCallAlert called with " + args.length + " args");
        
        if (args.length > 0 && args[0] instanceof JSONObject) {
            try {
                JSONObject data = (JSONObject) args[0];
                String eventChatId = data.optString("chatId", "");
                String callId = data.optString("callId", "");
                String callerName = data.optString("callerName", "Unknown");
                JSONObject callerObj = data.optJSONObject("caller");
                String callerId = callerObj != null ? callerObj.optString("id", "") : "";
                
                android.util.Log.d("GroupChatActivity", "Call alert details:");
                android.util.Log.d("GroupChatActivity", "  eventChatId: " + eventChatId);
                android.util.Log.d("GroupChatActivity", "  callId: " + callId);
                android.util.Log.d("GroupChatActivity", "  callerName: " + callerName);
                android.util.Log.d("GroupChatActivity", "  callerId: " + callerId);
                android.util.Log.d("GroupChatActivity", "  currentChatId: " + (currentChat != null ? currentChat.getId() : "null"));
                
                // Validate we have required data
                if (callId.isEmpty()) {
                    android.util.Log.e("GroupChatActivity", "Call alert missing callId, ignoring");
                    return;
                }
                
                // CRITICAL FIX: Don't show notification if current user is the caller
                String currentUserId = databaseManager.getUserId();
                if (currentUserId != null && !currentUserId.isEmpty() && 
                    callerId != null && !callerId.isEmpty() && 
                    currentUserId.equals(callerId)) {
                    android.util.Log.d("GroupChatActivity", "✗ Current user is the caller, not showing notification");
                    return;
                }
                
                // Only show notification for this chat
                if (currentChat != null && currentChat.getId().equals(eventChatId)) {
                    android.util.Log.d("GroupChatActivity", "✓ Chat matches! Showing notification");
                    android.util.Log.d("GroupChatActivity", "  callId: " + callId);
                    android.util.Log.d("GroupChatActivity", "  callerName: " + callerName);
                    android.util.Log.d("GroupChatActivity", "  currentChatId: " + currentChat.getId());
                    
                    runOnUiThread(() -> {
                        android.util.Log.d("GroupChatActivity", "On UI thread, calling showGroupCallNotification");
                        showGroupCallNotification(callId);
                        Toast.makeText(this, callerName + " started a video call", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    android.util.Log.d("GroupChatActivity", "✗ Chat doesn't match, ignoring");
                    android.util.Log.d("GroupChatActivity", "  Expected: " + (currentChat != null ? currentChat.getId() : "null"));
                    android.util.Log.d("GroupChatActivity", "  Got: " + eventChatId);
                }
            } catch (Exception e) {
                android.util.Log.e("GroupChatActivity", "Error parsing group call alert", e);
                e.printStackTrace();
            }
        } else {
            android.util.Log.e("GroupChatActivity", "Invalid args for group call alert");
        }
    }
    
    private void continueSetupGroupCallSocketListeners() {
        // Listen for call ended
        socketManager.on("call_ended", args -> {
            android.util.Log.d("GroupChatActivity", "Received call_ended event");
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String endedCallId = data.optString("callId", "");
                    
                    android.util.Log.d("GroupChatActivity", "Call ended: " + endedCallId + ", active: " + activeCallId);
                    
                    // Hide notification if this is the active call OR if no specific callId matches (broadcast)
                    if (endedCallId.isEmpty() || endedCallId.equals(activeCallId)) {
                        android.util.Log.d("GroupChatActivity", "Hiding notification for ended call");
                        runOnUiThread(this::hideGroupCallNotification);
                    }
                } catch (Exception e) {
                    android.util.Log.e("GroupChatActivity", "Error parsing call ended", e);
                }
            }
        });
        
        // Listen for participants joining (to keep notification in sync)
        socketManager.on("group_call_participant_joined", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.optString("callId", "");
                    String userId = data.optString("userId", "");
                    String username = data.optString("username", "Someone");
                    
                    // If this is for our active call and it's not us, keep showing notification
                    if (callId.equals(activeCallId) && !userId.equals(databaseManager.getUserId())) {
                        android.util.Log.d("GroupChatActivity", username + " joined the call");
                    }
                } catch (Exception e) {
                    android.util.Log.e("GroupChatActivity", "Error parsing participant joined", e);
                }
            }
        });
        
        // Listen for participants leaving
        socketManager.on("group_call_participant_left", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.optString("callId", "");
                    boolean allLeft = data.optBoolean("allLeft", false);
                    
                    // If all participants left, hide notification
                    if (callId.equals(activeCallId) && allLeft) {
                        android.util.Log.d("GroupChatActivity", "All participants left, hiding notification");
                        runOnUiThread(this::hideGroupCallNotification);
                    }
                } catch (Exception e) {
                    android.util.Log.e("GroupChatActivity", "Error parsing participant left", e);
                }
            }
        });
        
        android.util.Log.d("GroupChatActivity", "Group call socket listeners registered");
    }
    
    private void checkForActiveGroupCall() {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        String currentUserId = databaseManager.getUserId();
        
        // Check for active group call via API
        apiClient.authenticatedGet("/api/group-calls/chat/" + currentChat.getId() + "/active", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                android.util.Log.e("GroupChatActivity", "Failed to check for active call", e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        if (response.code() == 200) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                // CRITICAL FIX: Check if data exists and is not null
                                if (!jsonResponse.has("data") || jsonResponse.isNull("data")) {
                                    android.util.Log.d("GroupChatActivity", "No call data in response (data is null or missing)");
                                    hideGroupCallNotification();
                                    return;
                                }
                                
                                JSONObject data = jsonResponse.getJSONObject("data");
                                if (data == null || data.isNull("callId")) {
                                    android.util.Log.d("GroupChatActivity", "No call data in response");
                                    hideGroupCallNotification();
                                    return;
                                }
                                
                                String callId = data.getString("callId");
                                String status = data.optString("status", "");
                                
                                // CRITICAL FIX: Don't show notification for ended calls
                                if ("ended".equals(status)) {
                                    android.util.Log.d("GroupChatActivity", "Call is ended, hiding notification");
                                    hideGroupCallNotification();
                                    return;
                                }
                                
                                // CRITICAL FIX: Don't show notification if current user is the caller
                                JSONArray participants = data.optJSONArray("participants");
                                if (participants != null && currentUserId != null && !currentUserId.isEmpty()) {
                                    for (int i = 0; i < participants.length(); i++) {
                                        JSONObject participant = participants.getJSONObject(i);
                                        String participantUserId = participant.optString("userId", "");
                                        boolean isCaller = participant.optBoolean("isCaller", false);
                                        
                                        if (currentUserId.equals(participantUserId) && isCaller) {
                                            android.util.Log.d("GroupChatActivity", "Current user is the caller, not showing notification");
                                            hideGroupCallNotification();
                                            return;
                                        }
                                    }
                                }
                                
                                // Show notification if call is active and user is not the caller
                                if ("active".equals(status) || "notified".equals(status)) {
                                    showGroupCallNotification(callId);
                                } else {
                                    hideGroupCallNotification();
                                }
                            } else {
                                // No active call found
                                hideGroupCallNotification();
                            }
                        } else {
                            // No active call (404 or other error)
                            hideGroupCallNotification();
                        }
                    } catch (JSONException e) {
                        android.util.Log.e("GroupChatActivity", "Error parsing active call response", e);
                        hideGroupCallNotification();
                    }
                });
            }
        });
    }
    
    private void showGroupCallNotification(String callId) {
        android.util.Log.d("GroupChatActivity", "showGroupCallNotification called with callId: " + callId);
        android.util.Log.d("GroupChatActivity", "  groupCallNotification view: " + (groupCallNotification != null ? "exists" : "NULL"));
        
        if (callId == null || callId.isEmpty()) {
            android.util.Log.e("GroupChatActivity", "Cannot show notification: callId is null or empty");
            return;
        }
        
        this.activeCallId = callId;
        
        if (groupCallNotification == null) {
            android.util.Log.e("GroupChatActivity", "CRITICAL: groupCallNotification is null! Trying to reinitialize...");
            // Try to reinitialize
            groupCallNotification = findViewById(R.id.group_call_notification);
            if (groupCallNotification == null) {
                android.util.Log.e("GroupChatActivity", "Still null after reinit! View might not be in layout.");
                Toast.makeText(this, "Notification view not found", Toast.LENGTH_SHORT).show();
                return;
            }
            groupCallNotification.setOnClickListener(v -> joinActiveGroupCall());
        }
        
        android.util.Log.d("GroupChatActivity", "Setting notification visible and animating");
        runOnUiThread(() -> {
            try {
                groupCallNotification.setVisibility(View.VISIBLE);
                groupCallNotification.bringToFront(); // Ensure it's on top
                
                // Animate in
                groupCallNotification.setAlpha(0f);
                groupCallNotification.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
                
                android.util.Log.d("GroupChatActivity", "Notification shown successfully for call: " + callId);
            } catch (Exception e) {
                android.util.Log.e("GroupChatActivity", "Error showing notification", e);
                e.printStackTrace();
            }
        });
    }
    
    private void hideGroupCallNotification() {
        this.activeCallId = null;
        if (groupCallNotification != null) {
            // Animate out
            groupCallNotification.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> groupCallNotification.setVisibility(View.GONE))
                    .start();
        }
        android.util.Log.d("GroupChatActivity", "Hiding group call notification");
    }
    
    private void joinActiveGroupCall() {
        android.util.Log.d("GroupChatActivity", "joinActiveGroupCall called");
        android.util.Log.d("GroupChatActivity", "currentChat: " + (currentChat != null ? currentChat.getId() : "null"));
        android.util.Log.d("GroupChatActivity", "activeCallId: " + activeCallId);
        
        if (currentChat == null) {
            android.util.Log.e("GroupChatActivity", "Cannot join: currentChat is null");
            Toast.makeText(this, "Chat not loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // CRITICAL FIX: If activeCallId is not set, check server first before giving up
        if (activeCallId == null || activeCallId.isEmpty()) {
            android.util.Log.w("GroupChatActivity", "activeCallId not set, checking server for active call...");
            // Check server for active call and join it
            checkForActiveCallBeforeStarting();
            return;
        }
        
        android.util.Log.d("GroupChatActivity", "Starting GroupVideoCallActivity with:");
        android.util.Log.d("GroupChatActivity", "  chatId: " + currentChat.getId());
        android.util.Log.d("GroupChatActivity", "  groupName: " + currentChat.getName());
        android.util.Log.d("GroupChatActivity", "  callId: " + activeCallId);
        android.util.Log.d("GroupChatActivity", "  isCaller: false");
        
        // Join the existing group call
        Intent intent = new Intent(this, GroupVideoCallActivity.class);
        intent.putExtra("chatId", currentChat.getId());
        intent.putExtra("groupName", currentChat.getName());
        intent.putExtra("callId", activeCallId); // CRITICAL: Pass the activeCallId
        intent.putExtra("isCaller", false); // Joining, not initiating
        intent.putExtra("callType", "video");
        
        try {
            startActivity(intent);
            android.util.Log.d("GroupChatActivity", "GroupVideoCallActivity started successfully with callId: " + activeCallId);
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Failed to start GroupVideoCallActivity", e);
            Toast.makeText(this, "Failed to open call: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void fetchActualMemberCount() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty() || currentChat == null) return;
        
        // Use the same method as GroupMembersActivity to get actual member count
        apiClient.getGroupMembers(token, currentChat.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                android.util.Log.e("GroupChatActivity", "Failed to fetch member count: " + e.getMessage());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    handleMemberCountResponse(response.code(), responseBody);
                });
            }
        });
    }
    
    private void handleMemberCountResponse(int statusCode, String responseBody) {
        try {
            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONArray membersArray = data.getJSONArray("members");
                    
                    int actualMemberCount = membersArray.length();
                    android.util.Log.d("GroupChatActivity", "Actual member count from server: " + actualMemberCount);

                } else {
                    android.util.Log.e("GroupChatActivity", "Failed to get member count: " + jsonResponse.optString("message"));
                }
            } else {
                android.util.Log.e("GroupChatActivity", "Failed to get member count: " + statusCode);
            }
        } catch (JSONException e) {
            android.util.Log.e("GroupChatActivity", "Error parsing member count response: " + e.getMessage());
        }
    }
    
    private void loadGroupData() {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        // Use ApiClient to get updated group data
        apiClient.authenticatedGet("/api/chats/" + currentChat.getId(), token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                android.util.Log.e("GroupChatActivity", "Failed to reload group data: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        org.json.JSONObject chatData = jsonResponse.getJSONObject("data").getJSONObject("chat");
                        
                        runOnUiThread(() -> {
                            try {
                                android.util.Log.d("GroupChatActivity", "Server response chatData: " + chatData.toString());
                                currentChat = Chat.fromJson(chatData);
                                android.util.Log.d("GroupChatActivity", "After parsing - Participant count: " + currentChat.getParticipantCount());
                                android.util.Log.d("GroupChatActivity", "After parsing - Participant IDs: " + currentChat.getParticipantIds());
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            updateUI(); // Refresh the UI with updated data
                        });
                    } catch (Exception e) {
                        android.util.Log.e("GroupChatActivity", "Error parsing group data: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    private void showGroupSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group_settings, null);
        
        // Set group name
        TextView tvGroupName = dialogView.findViewById(R.id.tv_group_name);
        tvGroupName.setText(currentChat.getName());
        
        // Set current privacy setting
        android.widget.Switch switchPublic = dialogView.findViewById(R.id.switch_public);
        switchPublic.setChecked(currentChat.isPublicGroup());
        
        // Set button listeners
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        android.widget.Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            boolean isPublic = switchPublic.isChecked();
            updateGroupSettings(isPublic);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void updateGroupSettings(boolean isPublic) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            JSONObject settingsData = new JSONObject();
            JSONObject settings = new JSONObject();
            settings.put("isPublic", isPublic);
            settingsData.put("settings", settings);
            
            Toast.makeText(this, "Updating group settings...", Toast.LENGTH_SHORT).show();
            
            apiClient.updateGroupSettings(token, currentChat.getId(), settingsData, new okhttp3.Callback() {
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(GroupChatActivity.this, "Group settings updated successfully", Toast.LENGTH_SHORT).show();
                            // Update local chat object
                            currentChat.setIsPublic(isPublic);
                        } else {
                            Toast.makeText(GroupChatActivity.this, "Failed to update group settings", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing request", Toast.LENGTH_SHORT).show();
        }
    }
    
}
