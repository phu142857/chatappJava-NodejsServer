package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.cardview.widget.CardView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class GroupSettingsActivity extends AppCompatActivity {

    private TextView tvGroupName;
    private ImageView ivBack;
    private Switch switchPublic;
    private TextView tvRequestCount;
    
    private Chat currentChat;
    private SharedPreferencesManager sharedPrefsManager;
    private ApiClient apiClient;
    private boolean isUpdatingSettings = false;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private AlertDialog imageSelectDialog;
    private AlertDialog groupInfoDialog;
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);
        
        initViews();
        initData();
        initPickers();
        setupClickListeners();
        loadGroupData();
    }

    private void initPickers() {
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Bundle extras = result.getData().getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        File imageFile = saveBitmapToCache(imageBitmap);
                        if (imageFile != null) {
                            uploadGroupAvatar(imageFile);
                        } else {
                            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                File f = copyUriToCache(uri);
                if (f != null) {
                    uploadGroupAvatar(f);
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    private void initViews() {
        tvGroupName = findViewById(R.id.tv_group_name);
        ivBack = findViewById(R.id.iv_back);
        switchPublic = findViewById(R.id.switch_public);
        tvRequestCount = findViewById(R.id.tv_request_count);
    }
    
    private void initData() {
        sharedPrefsManager = new SharedPreferencesManager(this);
        apiClient = new ApiClient();
        
        Intent intent = getIntent();
        if (intent.hasExtra("chat")) {
            try {
                String chatJson = intent.getStringExtra("chat");
                JSONObject chatJsonObj = new JSONObject(chatJson);
                currentChat = Chat.fromJson(chatJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading chat data", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
        
        // Group Information
        findViewById(R.id.option_view_group_info).setOnClickListener(v -> showGroupInfo());
        findViewById(R.id.option_change_avatar).setOnClickListener(v -> showAvatarOptions());
        
        // Member Management
        findViewById(R.id.option_add_members).setOnClickListener(v -> addMembers());
        findViewById(R.id.option_remove_members).setOnClickListener(v -> removeMembers());
        findViewById(R.id.option_join_requests).setOnClickListener(v -> showJoinRequests());
        
        // Danger Zone
        findViewById(R.id.option_leave_group).setOnClickListener(v -> confirmLeaveGroup());
        findViewById(R.id.option_delete_group).setOnClickListener(v -> confirmDeleteGroup());
        
        // Privacy Settings
        switchPublic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingSettings) {
                updateGroupSettings(isChecked);
            }
        });
        
        // Add click listener for disabled switch to show permission message
        switchPublic.setOnClickListener(v -> {
            if (!switchPublic.isEnabled()) {
                Toast.makeText(this, "Only the group creator can change privacy settings", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadGroupData() {
        if (currentChat == null) return;
        
        tvGroupName.setText(currentChat.getName());
        
        // Check if current user is the creator/owner
        String currentUserId = sharedPrefsManager.getUserId();
        String creatorId = currentChat.getCreatorId();
        
        // Debug logging
        android.util.Log.d("GroupSettings", "Current User ID: " + currentUserId);
        android.util.Log.d("GroupSettings", "Creator ID: " + creatorId);
        android.util.Log.d("GroupSettings", "Chat ID: " + currentChat.getId());
        
        // If creatorId is null or empty, check if user is admin in participants (fallback)
        boolean isCreator;
        if (creatorId == null || creatorId.isEmpty()) {
            // Check if current user is admin in participants
            isCreator = currentChat.isGroupChat() && currentChat.isUserAdmin(currentUserId);
            android.util.Log.d("GroupSettings", "Creator ID is null/empty, checking admin status: " + isCreator);
            
            // Final fallback: if still not creator and it's a group chat, assume creator to avoid locking
            if (!isCreator && currentChat.isGroupChat()) {
                android.util.Log.d("GroupSettings", "Final fallback: assuming creator to avoid locking");
                isCreator = true;
            }
        } else {
            isCreator = currentUserId != null && currentUserId.equals(creatorId);
        }
        android.util.Log.d("GroupSettings", "Is Creator: " + isCreator);
        
        // Set flag to prevent listener from triggering during initial load
        isUpdatingSettings = true;
        switchPublic.setChecked(currentChat.isPublicGroup());
        isUpdatingSettings = false;
        
        // Enable/disable switch based on creator status
        switchPublic.setEnabled(isCreator);
        
        // Show/hide permission message
        updatePermissionMessage(isCreator);
        
        // Lock other creator-only options instead of hiding
        lockCreatorOnlyOptions(isCreator);
        
        // Load join requests count
        fetchJoinRequestCount();
    }
    
    @SuppressLint("SetTextI18n")
    private void updatePermissionMessage(boolean isCreator) {
        // Find the description text view for privacy settings
        TextView tvPrivacyDescription = findViewById(R.id.tv_privacy_description);
        if (tvPrivacyDescription != null) {
            if (isCreator) {
                tvPrivacyDescription.setText("When enabled, anyone can find and join this group. When disabled, only invited members can join.");
            } else {
                tvPrivacyDescription.setText("Only the group creator can change privacy settings.");
                tvPrivacyDescription.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        }
    }
    
    private void lockCreatorOnlyOptions(boolean isCreator) {
        // We keep items visible, but disable and show a small lock message if not creator
        lockOption(R.id.option_change_avatar, !isCreator, "Only group creator can change avatar");
        lockOption(R.id.option_add_members, !isCreator, "Only group creator can add members");
        lockOption(R.id.option_remove_members, !isCreator, "Only group creator can remove members");
        lockOption(R.id.option_join_requests, !isCreator, "Only group creator can view join requests");
        lockOption(R.id.option_delete_group, !isCreator, "Only group creator can delete group");

        // Leave group is allowed for members; if creator, disable/guide
        lockOption(R.id.option_leave_group, isCreator, "Creator cannot leave. Delete group instead");
    }

    private void lockOption(int optionId, boolean lock, String message) {
        View v = findViewById(optionId);
        if (v == null) return;
        v.setEnabled(!lock);
        v.setAlpha(lock ? 0.5f : 1.0f);
        if (lock) {
            v.setOnClickListener(x -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
        }
    }
    
    private void fetchJoinRequestCount() {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        apiClient.getJoinRequestsCount(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        int count = json.optJSONObject("data") != null ? json.optJSONObject("data").optInt("count", 0) : 0;
                        runOnUiThread(() -> {
                            if (count > 0) {
                                tvRequestCount.setVisibility(View.VISIBLE);
                                tvRequestCount.setText(String.valueOf(count));
                            } else {
                                tvRequestCount.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        runOnUiThread(() -> tvRequestCount.setVisibility(View.GONE));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> tvRequestCount.setVisibility(View.GONE));
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> tvRequestCount.setVisibility(View.GONE));
            }
        });
    }
    
    private void showGroupInfo() {
        if (currentChat == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group_info, null);

        de.hdodenhof.circleimageview.CircleImageView ivAvatar = dialogView.findViewById(R.id.iv_group_avatar);
        TextView tvName = dialogView.findViewById(R.id.tv_group_name);
        TextView tvMembers = dialogView.findViewById(R.id.tv_group_members);
        TextView tvCreated = dialogView.findViewById(R.id.tv_group_created);
        TextView tvGroupId = dialogView.findViewById(R.id.tv_group_id);
        View btnCopyId = dialogView.findViewById(R.id.btn_copy_id);
        View btnViewMembers = dialogView.findViewById(R.id.btn_view_members);
        View btnChangeAvatar = dialogView.findViewById(R.id.btn_change_avatar);
        View btnClose = dialogView.findViewById(R.id.btn_close);

        tvName.setText(currentChat.getName());
        tvMembers.setText(currentChat.getParticipantCount() + " members");
        tvCreated.setText("Created: " + (currentChat.getCreatedAt() != 0 ? new java.util.Date(currentChat.getCreatedAt()).toString() : "Unknown"));
        tvGroupId.setText("ID: " + currentChat.getId());

        // Avatar
        String avatarUrl = currentChat.getFullAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            // Use a placeholder loader via ImageView setImageURI or a loader in your project
            try {
                com.squareup.picasso.Picasso.get().load(avatarUrl).placeholder(R.drawable.ic_group_avatar).error(R.drawable.ic_group_avatar).into(ivAvatar);
            } catch (Exception ignored) {
                ivAvatar.setImageResource(R.drawable.ic_group_avatar);
            }
        } else {
            ivAvatar.setImageResource(R.drawable.ic_group_avatar);
        }

        btnCopyId.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Group ID", currentChat.getId());
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Copied group ID", Toast.LENGTH_SHORT).show();
        });

        btnViewMembers.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupMembersActivity.class);
            try {
                intent.putExtra("chat", currentChat.toJson().toString());
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error opening members list", Toast.LENGTH_SHORT).show();
            }
            if (groupInfoDialog != null) groupInfoDialog.dismiss();
        });

        btnChangeAvatar.setOnClickListener(v -> {
            if (groupInfoDialog != null) groupInfoDialog.dismiss();
            showAvatarOptions();
        });

        btnClose.setOnClickListener(v -> {
            if (groupInfoDialog != null) groupInfoDialog.dismiss();
        });

        builder.setView(dialogView);
        groupInfoDialog = builder.create();
        if (groupInfoDialog.getWindow() != null) {
            android.view.Window w = groupInfoDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        groupInfoDialog.show();
    }
    
    private void showAvatarOptions() {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_image_select, null);
        
        // Get views
        LinearLayout cameraOption = dialogView.findViewById(R.id.option_camera);
        LinearLayout galleryOption = dialogView.findViewById(R.id.option_gallery);
        LinearLayout cancelButton = dialogView.findViewById(R.id.btn_cancel);
        
        // Set click listeners
        cameraOption.setOnClickListener(v -> {
            try {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraLauncher.launch(takePictureIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
            }
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        galleryOption.setOnClickListener(v -> {
            try {
                galleryLauncher.launch("image/*");
            } catch (Exception e) {
                Toast.makeText(this, "Gallery not available", Toast.LENGTH_SHORT).show();
            }
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        cancelButton.setOnClickListener(v -> {
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
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
    
    private void addMembers() {
        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra("mode", "add_members");
        
        try {
            intent.putExtra("chat", currentChat.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening contact selection", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void removeMembers() {
        Intent intent = new Intent(this, GroupMembersActivity.class);
        intent.putExtra("mode", "remove_members");
        
        try {
            intent.putExtra("chat", currentChat.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening members list", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showJoinRequests() {
        Intent intent = new Intent(this, GroupJoinRequestsActivity.class);
        try {
            intent.putExtra("chat", currentChat.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening join requests", Toast.LENGTH_SHORT).show();
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
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.leaveGroup(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(GroupSettingsActivity.this, "Left group", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(GroupSettingsActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(GroupSettingsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.deleteChat(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(GroupSettingsActivity.this, "Group deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(GroupSettingsActivity.this, "Failed to delete group", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(GroupSettingsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private void updateGroupSettings(boolean isPublic) {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Set flag to prevent listener from triggering during update
        isUpdatingSettings = true;
        
        try {
            JSONObject settingsData = new JSONObject();
            JSONObject settings = new JSONObject();
            settings.put("isPublic", isPublic);
            settingsData.put("settings", settings);
            
            Toast.makeText(this, "Updating group settings...", Toast.LENGTH_SHORT).show();
            
            apiClient.updateGroupSettings(token, currentChat.getId(), settingsData, new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(GroupSettingsActivity.this, "Group settings updated successfully", Toast.LENGTH_SHORT).show();
                            // Update local chat object
                            currentChat.setIsPublic(isPublic);
                        } else {
                            Toast.makeText(GroupSettingsActivity.this, "Failed to update group settings", Toast.LENGTH_SHORT).show();
                            // Revert switch state safely
                            switchPublic.setChecked(!isPublic);
                        }
                        // Reset flag after update
                        isUpdatingSettings = false;
                    });
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupSettingsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // Revert switch state safely
                        switchPublic.setChecked(!isPublic);
                        // Reset flag after update
                        isUpdatingSettings = false;
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing request", Toast.LENGTH_SHORT).show();
            // Revert switch state safely
            switchPublic.setChecked(!isPublic);
            // Reset flag after update
            isUpdatingSettings = false;
        }
    }

    @Nullable
    private File copyUriToCache(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            File outFile = new File(getCacheDir(), "group_avatar_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();
            out.flush();
            out.close();
            return outFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private File saveBitmapToCache(Bitmap bitmap) {
        try {
            File outFile = new File(getCacheDir(), "camera_avatar_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            return outFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void uploadGroupAvatar(File imageFile) {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Uploading avatar...", Toast.LENGTH_SHORT).show();
        apiClient.uploadGroupAvatar(token, currentChat.getId(), imageFile, new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(GroupSettingsActivity.this, "Avatar updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(GroupSettingsActivity.this, "Failed to upload avatar", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(GroupSettingsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
}
