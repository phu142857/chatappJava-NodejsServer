package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.GroupMembersAdapter;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class GroupMembersActivity extends AppCompatActivity implements GroupMembersAdapter.OnMemberClickListener {
    
    private TextView tvGroupName, tvMemberCount;
    private ImageView ivBack, ivAddMember;
    private RecyclerView rvMembers;
    private android.view.View btnAddMember;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAddMember;
    private EditText etSearch;
    private View emptyState;
    
    private Chat currentChat;
    private List<User> members;
    private List<User> allMembers;
    private GroupMembersAdapter membersAdapter;
    private SharedPreferencesManager sharedPrefsManager;
    private ApiClient apiClient;
    private AvatarManager avatarManager;
    private AlertDialog memberOptionsDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);
        
        initViews();
        initData();
        setupClickListeners();
        setupRecyclerView();
        loadGroupMembers();
    }
    
    private void initViews() {
        tvGroupName = findViewById(R.id.tv_group_name);
        tvMemberCount = findViewById(R.id.tv_member_count);
        ivBack = findViewById(R.id.iv_back);
        rvMembers = findViewById(R.id.rv_members);
        etSearch = findViewById(R.id.et_search);
        emptyState = findViewById(R.id.empty_state);
    }
    
    private void initData() {
        sharedPrefsManager = new SharedPreferencesManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        members = new ArrayList<>();
        allMembers = new ArrayList<>();
        
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

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterMembers(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }
    
    private void setupRecyclerView() {
        membersAdapter = new GroupMembersAdapter(members, this);
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        rvMembers.setAdapter(membersAdapter);
    }
    
    private void loadGroupMembers() {
        if (currentChat == null) return;
        
        tvGroupName.setText(currentChat.getName());
        tvMemberCount.setText(currentChat.getParticipantCount() + " members");
        
        // Check if current user is the group owner
        String currentUserId = sharedPrefsManager.getUserId();
        String creatorId = currentChat.getCreatorId();
        
        // Debug logging
        android.util.Log.d("GroupMembersActivity", "Current User ID: " + currentUserId);
        android.util.Log.d("GroupMembersActivity", "Creator ID: " + creatorId);
        android.util.Log.d("GroupMembersActivity", "Is Owner: " + (currentUserId != null && currentUserId.equals(creatorId)));
        
        
        if (currentChat.getAvatar() != null && !currentChat.getAvatar().isEmpty()) {
            String avatarUrl = currentChat.getAvatar();
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
            }
        }
        
        fetchGroupMembers();
    }
    
    private void fetchGroupMembers() {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        Toast.makeText(this, "Loading members...", Toast.LENGTH_SHORT).show();
        
        apiClient.getGroupMembers(token, currentChat.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupMembersActivity.this, "Failed to load members: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                String responseBody = response.body().string();
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
                    
                    members.clear();
                    allMembers.clear();
                    for (int i = 0; i < membersArray.length(); i++) {
                        JSONObject memberJson = membersArray.getJSONObject(i);
                        android.util.Log.d("GroupMembersActivity", "Parsing member " + i + ": " + memberJson.toString());
                        User member = User.fromJson(memberJson);
                        android.util.Log.d("GroupMembersActivity", "Parsed member: " + member.getDisplayName() + 
                                          ", ID: " + member.getId());
                        members.add(member);
                        allMembers.add(member);
                    }
                    
                    // Apply current filter text if any
                    String currentQuery = etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "";
                    filterMembers(currentQuery);
                    
                    if (members.isEmpty()) {
                        Toast.makeText(this, "No members found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String message = jsonResponse.optString("message", "Failed to load members");
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to load members: " + statusCode, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing members data", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterMembers(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        members.clear();
        if (q.isEmpty()) {
            members.addAll(allMembers);
        } else {
            for (User u : allMembers) {
                String displayName = u.getDisplayName() != null ? u.getDisplayName().toLowerCase(Locale.ROOT) : "";
                String username = u.getUsername() != null ? u.getUsername().toLowerCase(Locale.ROOT) : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase(Locale.ROOT) : "";
                String phone = u.getPhoneNumber() != null ? u.getPhoneNumber().toLowerCase(Locale.ROOT) : "";
                if (displayName.contains(q) || username.contains(q) || email.contains(q) || phone.contains(q)) {
                    members.add(u);
                }
            }
        }
        membersAdapter.notifyDataSetChanged();
        // Toggle empty state
        if (emptyState != null) {
            emptyState.setVisibility(members.isEmpty() ? View.VISIBLE : View.GONE);
        }
        // Update count to reflect current visible items
        tvMemberCount.setText(members.size() + " members");
    }

    @Override
    public void onMemberClick(User member) {
        showMemberOptionsDialog(member);
    }
    
    private void showMemberOptionsDialog(User member) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_member_options, null);

        // Initialize views
        de.hdodenhof.circleimageview.CircleImageView ivMemberAvatar = dialogView.findViewById(R.id.iv_member_avatar);
        TextView tvMemberName = dialogView.findViewById(R.id.tv_member_name);
        TextView tvMemberUsername = dialogView.findViewById(R.id.tv_member_username);
        View btnClose = dialogView.findViewById(R.id.btn_close);
        View optionViewProfile = dialogView.findViewById(R.id.option_view_profile);
        View optionRemoveMember = dialogView.findViewById(R.id.option_remove_member);

        // Set member info
        tvMemberName.setText(member.getDisplayName());
        tvMemberUsername.setText("@" + member.getUsername());

        // Load avatar
        String avatarUrl = member.getFullAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                com.squareup.picasso.Picasso.get().load(avatarUrl)
                    .placeholder(R.drawable.ic_person_avatar)
                    .error(R.drawable.ic_person_avatar)
                    .into(ivMemberAvatar);
            } catch (Exception ignored) {
                ivMemberAvatar.setImageResource(R.drawable.ic_person_avatar);
            }
        } else {
            ivMemberAvatar.setImageResource(R.drawable.ic_person_avatar);
        }

        // Check permissions
        String currentUserId = sharedPrefsManager != null ? sharedPrefsManager.getUserId() : null;
        String creatorId = currentChat != null ? currentChat.getCreatorId() : null;
        
        boolean isOwner = creatorId != null && !creatorId.isEmpty() && currentUserId != null && currentUserId.equals(creatorId);
        boolean isAdmin = currentChat != null && currentChat.isGroupChat() && currentChat.hasManagementPermissions(currentUserId);
        boolean hasManagementPermissions = isOwner || isAdmin;
        boolean isSelf = currentUserId != null && currentUserId.equals(member.getId());

        // Show/hide remove option based on permissions
        if (hasManagementPermissions && !isSelf) {
            optionRemoveMember.setVisibility(View.VISIBLE);
        } else {
            optionRemoveMember.setVisibility(View.GONE);
        }

        // Set click listeners
        btnClose.setOnClickListener(v -> {
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
        });

        optionViewProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileViewActivity.class);
            try {
                intent.putExtra("user", member.toJson().toString());
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error opening profile", Toast.LENGTH_SHORT).show();
            }
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
        });

        optionRemoveMember.setOnClickListener(v -> {
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
            confirmRemoveMember(member);
        });

        builder.setView(dialogView);
        memberOptionsDialog = builder.create();
        if (memberOptionsDialog.getWindow() != null) {
            android.view.Window w = memberOptionsDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        memberOptionsDialog.show();
    }
    
    private void confirmRemoveMember(User member) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            "Remove Member",
            "Are you sure you want to remove " + member.getDisplayName() + " from this group?",
            "Remove",
            "Cancel",
            () -> removeMember(member),
            null,
            false
        );
    }
    
    private void removeMember(User member) {
        Toast.makeText(this, "Removing member...", Toast.LENGTH_SHORT).show();
        
        // Debug logging
        android.util.Log.d("GroupMembersActivity", "Removing member: " + member.getDisplayName() + 
                          ", ID: " + member.getId() + 
                          ", Chat ID: " + currentChat.getId());
        
        String token = sharedPrefsManager.getToken();
        try {
            JSONObject memberData = new JSONObject();
            memberData.put("userId", member.getId());
            
            android.util.Log.d("GroupMembersActivity", "Sending remove request: " + memberData.toString());
            
            apiClient.removeMember(token, currentChat.getId(), memberData, new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(GroupMembersActivity.this, "Member removed successfully", Toast.LENGTH_SHORT).show();
                            // Remove from local list
                            members.remove(member);
                            allMembers.remove(member);
                            // Re-apply current filter to keep view consistent
                            String currentQuery = etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "";
                            filterMembers(currentQuery);
                            // Update member count
                            tvMemberCount.setText(members.size() + " members");
                        } else {
                            // Try to parse error message from response
                            String errorMessage = "Failed to remove member";
                            try {
                                JSONObject errorJson = new JSONObject(responseBody);
                                if (errorJson.has("message")) {
                                    errorMessage = errorJson.getString("message");
                                }
                            } catch (JSONException e) {
                                // Use default message if parsing fails
                            }
                            Toast.makeText(GroupMembersActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupMembersActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing request", Toast.LENGTH_SHORT).show();
        }
    }
}
