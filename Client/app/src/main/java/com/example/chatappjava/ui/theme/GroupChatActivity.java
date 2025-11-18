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

import com.example.chatappjava.network.ApiClient;
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
    
    @Override
    protected int getLayoutResource() {
        return R.layout.activity_private_chat; // Reuse the same layout for now
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImagePicker();
    }
    
    @Override
    protected void handleVideoCall() {
        Toast.makeText(this, "Group video call feature coming soon", Toast.LENGTH_SHORT).show();
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
