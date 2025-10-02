package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);
        
        initViews();
        initData();
        setupClickListeners();
        loadGroupData();
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
                return;
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
            updateGroupSettings(isChecked);
        });
    }
    
    private void loadGroupData() {
        if (currentChat == null) return;
        
        tvGroupName.setText(currentChat.getName());
        switchPublic.setChecked(currentChat.isPublicGroup());
        
        // Load join requests count
        fetchJoinRequestCount();
    }
    
    private void fetchJoinRequestCount() {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        // TODO: Implement fetch join request count
        // For now, hide the badge
        tvRequestCount.setVisibility(View.GONE);
    }
    
    private void showGroupInfo() {
        if (currentChat == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Group Information")
                .setMessage("Group Name: " + currentChat.getName() + "\n" +
                           "Members: " + currentChat.getParticipantCount() + "\n" +
                           "Created: " + (currentChat.getCreatedAt() != 0 ? new java.util.Date(currentChat.getCreatedAt()).toString() : "Unknown"))
                .setPositiveButton("View Members", (dialog, which) -> {
                    Intent intent = new Intent(this, GroupMembersActivity.class);
                    try {
                        intent.putExtra("chat", currentChat.toJson().toString());
                        startActivity(intent);
                    } catch (JSONException e) {
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
        String[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Avatar")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            // TODO: Implement camera capture
                            Toast.makeText(this, "Camera feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            // TODO: Implement gallery selection
                            Toast.makeText(this, "Gallery feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            dialog.dismiss();
                            break;
                    }
                });
        builder.show();
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
        Toast.makeText(this, "Leave group feature coming soon", Toast.LENGTH_SHORT).show();
        // TODO: Implement leave group functionality
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
        Toast.makeText(this, "Delete group feature coming soon", Toast.LENGTH_SHORT).show();
        // TODO: Implement delete group functionality
    }
    
    private void updateGroupSettings(boolean isPublic) {
        String token = sharedPrefsManager.getToken();
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
            
            apiClient.updateGroupSettings(token, currentChat.getId(), settingsData, new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(GroupSettingsActivity.this, "Group settings updated successfully", Toast.LENGTH_SHORT).show();
                            // Update local chat object
                            currentChat.setIsPublic(isPublic);
                        } else {
                            Toast.makeText(GroupSettingsActivity.this, "Failed to update group settings", Toast.LENGTH_SHORT).show();
                            // Revert switch state
                            switchPublic.setChecked(!isPublic);
                        }
                    });
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupSettingsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // Revert switch state
                        switchPublic.setChecked(!isPublic);
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing request", Toast.LENGTH_SHORT).show();
            // Revert switch state
            switchPublic.setChecked(!isPublic);
        }
    }
}
