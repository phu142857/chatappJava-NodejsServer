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

import org.json.JSONArray;
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
    private java.util.Map<String, String> memberRoles; // userId -> role display text
    
    
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

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh latest group state from server to ensure privacy toggle matches backend
        refreshCurrentChatFromServer();
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
        memberRoles = new java.util.HashMap<>();
        
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
        
        // Member Management - listeners will be set after permissions are loaded
        // They are set in lockCreatorOnlyOptions() method
        
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
                Toast.makeText(this, "Only group creator and admins can change privacy settings", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadGroupData() {
        if (currentChat == null) return;
        
        tvGroupName.setText(currentChat.getName());
        
        // Fetch member roles first to determine permissions accurately
        fetchMemberRoles();
    }
    
    private void fetchMemberRoles() {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            updatePermissionsWithFallback();
            return;
        }
        
        apiClient.getGroupMembers(token, currentChat.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    // Fallback to Chat model check if API fails
                    updatePermissionsWithFallback();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    handleLoadMembersResponse(response.code(), responseBody);
                });
            }
        });
    }
    
    private void handleLoadMembersResponse(int statusCode, String responseBody) {
        try {
            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONArray membersArray = data.getJSONArray("members");
                    
                    memberRoles.clear();
                    String creatorId = currentChat.getCreatorId();
                    
                    for (int i = 0; i < membersArray.length(); i++) {
                        JSONObject memberJson = membersArray.getJSONObject(i);
                        String memberId = memberJson.optString("id", memberJson.optString("_id", ""));
                        
                        // Parse role information
                        boolean isOwner = memberJson.optBoolean("isOwner", false) || 
                                        (creatorId != null && creatorId.equals(memberId));
                        String role = memberJson.optString("role", "member");
                        
                        // Determine display text for role
                        String roleDisplayText;
                        if (isOwner) {
                            roleDisplayText = "Owner";
                        } else if ("admin".equalsIgnoreCase(role)) {
                            roleDisplayText = "Admin";
                        } else if ("moderator".equalsIgnoreCase(role)) {
                            roleDisplayText = "Moderator";
                        } else {
                            roleDisplayText = "Member";
                        }
                        
                        memberRoles.put(memberId, roleDisplayText);
                    }
                    
                    // Now update permissions based on actual roles
                    updatePermissions();
                } else {
                    updatePermissionsWithFallback();
                }
            } else {
                updatePermissionsWithFallback();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            updatePermissionsWithFallback();
        }
    }
    
    private void updatePermissionsWithFallback() {
        // Fallback to Chat model check if API fails
        String currentUserId = sharedPrefsManager.getUserId();
        String creatorId = currentChat.getCreatorId();
        boolean isOwner = creatorId != null && !creatorId.isEmpty() && currentUserId != null && currentUserId.equals(creatorId);
        boolean hasManagementPermissions = isOwner || (currentChat.isGroupChat() && currentChat.hasManagementPermissions(currentUserId));
        updatePermissions(hasManagementPermissions);
    }
    
    private void updatePermissions() {
        String currentUserId = sharedPrefsManager.getUserId();
        String creatorId = currentChat.getCreatorId();
        boolean isOwner = creatorId != null && !creatorId.isEmpty() && currentUserId != null && currentUserId.equals(creatorId);
        
        boolean hasManagementPermissions = isOwner;
        boolean hasPrivacySettingsPermission = isOwner; // Only owner and admins can change privacy
        
        // If we have member roles, check the actual role
        if (memberRoles != null && !memberRoles.isEmpty() && currentUserId != null) {
            String currentUserRole = memberRoles.get(currentUserId);
            boolean isModerator = "Moderator".equals(currentUserRole);
            boolean isAdmin = "Admin".equals(currentUserRole);
            
            // Management permissions: owner, admin, or moderator
            hasManagementPermissions = isOwner || isAdmin || isModerator;
            // Privacy settings: only owner or admin (moderators excluded)
            hasPrivacySettingsPermission = isOwner || isAdmin;
            
            android.util.Log.d("GroupSettings", "Current User Role: " + currentUserRole);
            android.util.Log.d("GroupSettings", "Is Owner: " + isOwner);
            android.util.Log.d("GroupSettings", "Is Admin: " + isAdmin);
            android.util.Log.d("GroupSettings", "Is Moderator: " + isModerator);
        } else {
            // Fallback: check Chat model
            hasManagementPermissions = isOwner || (currentChat.isGroupChat() && currentChat.hasManagementPermissions(currentUserId));
            // For privacy, only check if owner or admin (not moderator)
            hasPrivacySettingsPermission = isOwner || (currentChat.isGroupChat() && currentChat.isUserAdmin(currentUserId));
        }
        
        updatePermissions(hasManagementPermissions, hasPrivacySettingsPermission);
    }
    
    private void updatePermissions(boolean hasManagementPermissions, boolean hasPrivacySettingsPermission) {
        android.util.Log.d("GroupSettings", "Has Management Permissions: " + hasManagementPermissions);
        android.util.Log.d("GroupSettings", "Has Privacy Settings Permission: " + hasPrivacySettingsPermission);
        
        // Set flag to prevent listener from triggering during initial load
        isUpdatingSettings = true;
        // Prefer server explicit flag; else fallback to cached local value; else keep current UI state
        Boolean cached = getCachedIsPublic(currentChat.getId());
        boolean target = currentChat.hasExplicitPublicFlag() ? currentChat.isPublicGroup()
                : (cached != null ? cached : switchPublic.isChecked());
        switchPublic.setChecked(target);
        isUpdatingSettings = false;
        
        // Enable/disable switch based on privacy settings permission (owner/admin only, not moderators)
        switchPublic.setEnabled(hasPrivacySettingsPermission);
        
        // Show/hide permission message
        updatePermissionMessage(hasPrivacySettingsPermission);
        
        // Lock other creator-only options instead of hiding
        lockCreatorOnlyOptions(hasManagementPermissions);
        
        // Load join requests count
        fetchJoinRequestCount();
    }
    
    private void updatePermissions(boolean hasManagementPermissions) {
        // Overload for backward compatibility - assume privacy permission same as management
        updatePermissions(hasManagementPermissions, hasManagementPermissions);
    }

    private void refreshCurrentChatFromServer() {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty() || currentChat == null) return;
        apiClient.getChats(token, new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject json = new JSONObject(body);
                    org.json.JSONArray arr = json.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject chatObj = arr.optJSONObject(i);
                            if (chatObj != null && currentChat.getId().equals(chatObj.optString("_id"))) {
                                Chat updated = Chat.fromJson(chatObj);
                                currentChat = updated;
                                runOnUiThread(() -> {
                                    // Prevent listener triggering while updating UI
                                    isUpdatingSettings = true;
                                    Boolean cached2 = getCachedIsPublic(currentChat.getId());
                                    boolean target2 = currentChat.hasExplicitPublicFlag() ? currentChat.isPublicGroup()
                                            : (cached2 != null ? cached2 : switchPublic.isChecked());
                                    switchPublic.setChecked(target2);
                                    isUpdatingSettings = false;
                                });
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void onFailure(Call call, IOException e) { /* no-op */ }
        });
    }
    
    @SuppressLint("SetTextI18n")
    private void updatePermissionMessage(boolean hasManagementPermissions) {
        // Find the description text view for privacy settings
        TextView tvPrivacyDescription = findViewById(R.id.tv_privacy_description);
        if (tvPrivacyDescription != null) {
            if (hasManagementPermissions) {
                tvPrivacyDescription.setText("When enabled, anyone can find and join this group. When disabled, only invited members can join.");
            } else {
                tvPrivacyDescription.setText("Only group creator and admins can change privacy settings.");
                tvPrivacyDescription.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        }
    }
    
    private void lockCreatorOnlyOptions(boolean hasManagementPermissions) {
        // Check delete permission separately (only owner/admin, not moderators)
        String currentUserId = sharedPrefsManager.getUserId();
        String creatorId = currentChat.getCreatorId();
        boolean isOwner = creatorId != null && !creatorId.isEmpty() && currentUserId != null && currentUserId.equals(creatorId);
        boolean hasDeletePermission = isOwner;
        
        if (memberRoles != null && !memberRoles.isEmpty() && currentUserId != null) {
            String currentUserRole = memberRoles.get(currentUserId);
            boolean isAdmin = "Admin".equals(currentUserRole);
            hasDeletePermission = isOwner || isAdmin;
        } else {
            // Fallback: check Chat model
            hasDeletePermission = isOwner || (currentChat.isGroupChat() && currentChat.isUserAdmin(currentUserId));
        }
        
        // We keep items visible, but disable and show a small lock message if not creator/admin/moderator
        lockOption(R.id.option_add_members, !hasManagementPermissions, "Only group creator, admins, and moderators can add members", v -> addMembers());
        // Members can always view the members list (no lock), but management actions are restricted
        View membersOption = findViewById(R.id.option_remove_members);
        if (membersOption != null) {
            membersOption.setEnabled(true);
            membersOption.setAlpha(1.0f);
            membersOption.setOnClickListener(v -> removeMembers());
        }
        lockOption(R.id.option_join_requests, !hasManagementPermissions, "Only group creator, admins, and moderators can view join requests", v -> showJoinRequests());
        lockOption(R.id.option_delete_group, !hasDeletePermission, "Only group creator and admins can delete group", v -> confirmDeleteGroup());

        // Leave group is allowed for members; if creator, disable/guide
        lockOption(R.id.option_leave_group, isOwner, "Creator cannot leave. Delete group instead", v -> confirmLeaveGroup());
    }

    private void lockOption(int optionId, boolean lock, String message, View.OnClickListener actionListener) {
        View v = findViewById(optionId);
        if (v == null) return;
        v.setEnabled(!lock);
        v.setAlpha(lock ? 0.5f : 1.0f);
        if (lock) {
            v.setOnClickListener(x -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
        } else {
            v.setOnClickListener(actionListener);
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

            apiClient.updateGroupSettings(token, currentChat.getId(), settingsData, new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            // Update local chat object
                            currentChat.setIsPublic(isPublic);
                            cacheIsPublic(currentChat.getId(), isPublic);
                        } else {
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

    private void cacheIsPublic(String chatId, boolean isPublic) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("ChatPrivacyCache", MODE_PRIVATE);
            sp.edit().putBoolean("pub_" + chatId, isPublic).apply();
        } catch (Exception ignored) {}
    }

    private Boolean getCachedIsPublic(String chatId) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("ChatPrivacyCache", MODE_PRIVATE);
            if (sp.contains("pub_" + chatId)) {
                return sp.getBoolean("pub_" + chatId, false);
            }
            return null;
        } catch (Exception e) { return null; }
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
