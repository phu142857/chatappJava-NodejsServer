package com.example.chatappjava.ui.theme;

import static androidx.core.content.ContextCompat.startActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.github.dhaval2404.imagepicker.ImagePicker;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;

import org.json.JSONException;

import java.io.IOException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class GroupChatActivity extends BaseChatActivity {

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Uri selectedImageUri;
    private boolean isChangingAvatar = false;
    
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
            tvStatus.setText(currentChat.getParticipantCount() + " members");
            
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
            
            // Add click listener to group avatar for changing avatar
            if (ivProfile != null) {
                ivProfile.setOnClickListener(v -> {
                    Toast.makeText(this, "Tap to change group avatar", Toast.LENGTH_SHORT).show();
                    showAvatarOptions();
                });
            }
        } else {
            android.util.Log.d("GroupAvatar", "Current chat is null");
        }
    }
    
    @Override
    protected void showChatOptions() {
        String[] options = {"View Group Info", "Change Avatar", "Add Members", "Remove Members", "Leave Group", "Delete Group"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Group Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showGroupInfo();
                            break;
                        case 1:
                            showAvatarOptions();
                            break;
                        case 2:
                            addMembers();
                            break;
                        case 3:
                            removeMembers();
                            break;
                        case 4:
                            confirmLeaveGroup();
                            break;
                        case 5:
                            confirmDeleteGroup();
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
        String[] options = {"Take Photo", "Choose from Gallery", "Remove Avatar"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Group Avatar - All members can change")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            // Take photo
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
                            break;
                        case 1:
                            // Choose from gallery
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
                            break;
                        case 2:
                            // Remove avatar
                            confirmRemoveAvatar();
                            break;
                    }
                });
        builder.show();
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
            
            String token = sharedPrefsManager.getToken();
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
        
        String token = sharedPrefsManager.getToken();
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
        new AlertDialog.Builder(this)
                .setTitle("Delete Group")
                .setMessage("Are you sure you want to delete this group? This action cannot be undone and all messages will be lost.")
                .setPositiveButton("Delete", (dialog, which) -> deleteGroup())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void deleteGroup() {
        if (currentChat == null) return;
        
        Toast.makeText(this, "Deleting group...", Toast.LENGTH_SHORT).show();
        
        String token = sharedPrefsManager.getToken();
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
}
