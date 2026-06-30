package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.EmptyStateHelper;
import com.example.chatappjava.utils.SkeletonHelper;

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
    private View ivBack;
    private RecyclerView rvMembers;
    private EditText etSearch;
    private ImageButton ivClear;
    private View emptyState;
    private View listSkeleton;
    
    private Chat currentChat;
    private List<User> members;
    private List<User> allMembers;
    private GroupMembersAdapter membersAdapter;
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private AvatarManager avatarManager;
    private AlertDialog memberOptionsDialog;
    private java.util.Map<String, String> memberRoles; // userId -> role display text
    
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
        View backWell = findViewById(R.id.toolbar_back_well);
        if (backWell != null) {
            backWell.setVisibility(View.VISIBLE);
        }
        tvGroupName = findViewById(R.id.tv_toolbar_title);
        tvMemberCount = findViewById(R.id.tv_member_count);
        ivBack = findViewById(R.id.iv_toolbar_back);
        rvMembers = findViewById(R.id.rv_members);
        etSearch = findViewById(R.id.et_search);
        ivClear = findViewById(R.id.iv_clear);
        if (etSearch != null) {
            etSearch.setHint(R.string.search_members_hint);
        }
        if (ivClear != null) {
            ivClear.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.setText("");
                }
            });
        }
        emptyState = findViewById(R.id.empty_state);
        listSkeleton = findViewById(R.id.list_skeleton);
        EmptyStateHelper.bind(
                emptyState,
                R.string.empty_group_members_title,
                R.string.empty_group_members_subtitle,
                R.drawable.ic_group_empty
        );
    }
    
    private void initData() {
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        members = new ArrayList<>();
        allMembers = new ArrayList<>();
        memberRoles = new java.util.HashMap<>();
        
        Intent intent = getIntent();
        if (intent.hasExtra("chat")) {
            try {
                String chatJson = intent.getStringExtra("chat");
                JSONObject chatJsonObj = new JSONObject(chatJson);
                currentChat = Chat.fromJson(chatJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.error_load_chat), Toast.LENGTH_SHORT).show();
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
                    if (ivClear != null) {
                        ivClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
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
        String currentUserId = databaseManager.getUserId();
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
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        SkeletonHelper.setListLoading(listSkeleton, true);
        rvMembers.setVisibility(View.GONE);

        apiClient.getGroupMembers(token, currentChat.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    rvMembers.setVisibility(View.VISIBLE);
                    Toast.makeText(GroupMembersActivity.this, getString(R.string.error_load_members, e.getMessage()), Toast.LENGTH_SHORT).show();
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
        SkeletonHelper.setListLoading(listSkeleton, false);
        rvMembers.setVisibility(View.VISIBLE);
        try {
            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONArray membersArray = data.getJSONArray("members");
                    
                    int previousSize = members.size();
                    members.clear();
                    allMembers.clear();
                    memberRoles.clear();
                    String creatorId = currentChat != null ? currentChat.getCreatorId() : null;
                    String newOwnerId = null; // Track new owner from server response
                    
                    for (int i = 0; i < membersArray.length(); i++) {
                        JSONObject memberJson = membersArray.getJSONObject(i);
                        android.util.Log.d("GroupMembersActivity", "Parsing member " + i + ": " + memberJson.toString());
                        User member = User.fromJsonStatic(memberJson);
                        android.util.Log.d("GroupMembersActivity", "Parsed member: " + member.getDisplayName() + 
                                          ", ID: " + member.getId());
                        members.add(member);
                        allMembers.add(member);
                        
                        // Parse role information
                        boolean isOwner = memberJson.optBoolean("isOwner", false) || 
                                        (creatorId != null && creatorId.equals(member.getId()));
                        String role = memberJson.optString("role", "member");
                        
                        // Track the owner ID from server response
                        if (isOwner) {
                            newOwnerId = member.getId();
                        }
                        
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
                        
                        memberRoles.put(member.getId(), roleDisplayText);
                    }
                    
                    // Update chat's creator ID if it changed (from server response)
                    if (newOwnerId != null && currentChat != null && 
                        (creatorId == null || !creatorId.equals(newOwnerId))) {
                        currentChat.setCreatorId(newOwnerId);
                        android.util.Log.d("GroupMembersActivity", "Updated chat creator ID to: " + newOwnerId);
                    }
                    
                    // Update adapter with role information
                    if (membersAdapter != null) {
                        membersAdapter.setMemberRoles(memberRoles);
                    }
                    
                    // Apply current filter text if any
                    String currentQuery = etSearch != null && etSearch.getText() != null ? etSearch.getText().toString() : "";
                    filterMembers(currentQuery, previousSize);
                    
                    if (members.isEmpty()) {
                        Toast.makeText(this, getString(R.string.empty_group_members_title), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String message = jsonResponse.optString("message", "Failed to load members");
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.error_load_members_code, statusCode), Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_parsing_members_data), Toast.LENGTH_SHORT).show();
        }
    }

    private void filterMembers(String query) {
        filterMembers(query, members.size());
    }

    private void filterMembers(String query, int previousSize) {
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
        notifyMembersListChanged(previousSize, members.size());
        // Toggle empty state
        if (emptyState != null) {
            emptyState.setVisibility(members.isEmpty() ? View.VISIBLE : View.GONE);
        }
        // Update count to reflect current visible items
        tvMemberCount.setText(members.size() + " members");
    }

    private void notifyMembersListChanged(int previousSize, int newSize) {
        if (membersAdapter == null) {
            return;
        }
        if (previousSize == newSize) {
            if (newSize > 0) {
                membersAdapter.notifyItemRangeChanged(0, newSize);
            }
            return;
        }
        if (newSize > previousSize) {
            if (previousSize > 0) {
                membersAdapter.notifyItemRangeChanged(0, previousSize);
            }
            membersAdapter.notifyItemRangeInserted(previousSize, newSize - previousSize);
            return;
        }
        if (newSize > 0) {
            membersAdapter.notifyItemRangeChanged(0, newSize);
        }
        membersAdapter.notifyItemRangeRemoved(newSize, previousSize - newSize);
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
        View optionTransferOwnership = dialogView.findViewById(R.id.option_transfer_ownership);
        View optionPromoteToModerator = dialogView.findViewById(R.id.option_promote_to_moderator);
        View optionDemoteToMember = dialogView.findViewById(R.id.option_demote_to_member);
        View optionRemoveMember = dialogView.findViewById(R.id.option_remove_member);

        // Set member info
        tvMemberName.setText(member.getDisplayName());
        tvMemberUsername.setText(getString(R.string.username_format, member.getUsername()));

        // Load avatar
        String avatarUrl = member.getFullAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            try {
                com.squareup.picasso.Picasso.get().load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(ivMemberAvatar);
            } catch (Exception ignored) {
                ivMemberAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } else {
            ivMemberAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Check permissions
        String currentUserId = databaseManager != null ? databaseManager.getUserId() : null;
        String creatorId = currentChat != null ? currentChat.getCreatorId() : null;
        
        boolean isOwner = creatorId != null && !creatorId.isEmpty() && currentUserId != null && currentUserId.equals(creatorId);
        boolean isSelf = currentUserId != null && currentUserId.equals(member.getId());
        boolean isMemberOwner = creatorId != null && creatorId.equals(member.getId());

        // Get current user's role from memberRoles map (more accurate than Chat model)
        String currentUserRoleDisplay = memberRoles != null ? memberRoles.get(currentUserId) : null;
        boolean currentUserIsModerator = "Moderator".equals(currentUserRoleDisplay);
        boolean currentUserIsAdmin = "Admin".equals(currentUserRoleDisplay) || isOwner;
        
        // Calculate hasManagementPermissions based on actual role
        boolean hasManagementPermissions = isOwner || currentUserIsAdmin || currentUserIsModerator;
        
        // Get member's current role
        String memberRoleDisplay = memberRoles != null ? memberRoles.get(member.getId()) : null;
        boolean isModerator = "Moderator".equals(memberRoleDisplay);
        boolean isMember = "Member".equals(memberRoleDisplay) || memberRoleDisplay == null;
        boolean isAdmin = "Admin".equals(memberRoleDisplay);

        // Show/hide transfer ownership option (ONLY for owner, cannot transfer to self)
        if (isOwner && !isSelf && !isMemberOwner) {
            optionTransferOwnership.setVisibility(View.VISIBLE);
        } else {
            optionTransferOwnership.setVisibility(View.GONE);
        }

        // Show/hide role change options (ONLY for owner, moderators cannot promote)
        // Owners can promote/demote, but moderators cannot
        if (isOwner && !isSelf && !isMemberOwner) {
            if (isMember) {
                // Show promote to moderator option
                optionPromoteToModerator.setVisibility(View.VISIBLE);
                optionDemoteToMember.setVisibility(View.GONE);
            } else if (isModerator) {
                // Show demote to member option
                optionPromoteToModerator.setVisibility(View.GONE);
                optionDemoteToMember.setVisibility(View.VISIBLE);
            } else {
                // Admin or other roles - hide both options
                optionPromoteToModerator.setVisibility(View.GONE);
                optionDemoteToMember.setVisibility(View.GONE);
            }
        } else {
            // Moderators and others cannot promote/demote
            optionPromoteToModerator.setVisibility(View.GONE);
            optionDemoteToMember.setVisibility(View.GONE);
        }

        // Show/hide remove option based on permissions
        // Moderators can remove members, but NOT the owner
        // Owners and admins can also remove members (except owner)
        if (hasManagementPermissions && !isSelf && !isMemberOwner) {
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
                Toast.makeText(this, getString(R.string.error_error_opening_profile), Toast.LENGTH_SHORT).show();
            }
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
        });

        optionTransferOwnership.setOnClickListener(v -> {
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
            confirmTransferOwnership(member);
        });

        optionPromoteToModerator.setOnClickListener(v -> {
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
            changeMemberRole(member, "moderator");
        });

        optionDemoteToMember.setOnClickListener(v -> {
            if (memberOptionsDialog != null) memberOptionsDialog.dismiss();
            changeMemberRole(member, "member");
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
    
    private void changeMemberRole(User member, String newRole) {
        Toast.makeText(this, getString(R.string.status_changing_role), Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
        try {
            JSONObject roleData = new JSONObject();
            roleData.put("userId", member.getId());
            roleData.put("role", newRole);
            
            apiClient.updateMemberRole(token, currentChat.getId(), roleData, new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                if (json.optBoolean("success", false)) {
                                    String roleDisplayText = "moderator".equals(newRole) ? "Moderator" : "Member";
                                    memberRoles.put(member.getId(), roleDisplayText);
                                    
                                    // Update adapter
                                    if (membersAdapter != null) {
                                        membersAdapter.setMemberRoles(memberRoles);
                                    }
                                    
                                    Toast.makeText(GroupMembersActivity.this, 
                                        "Role changed to " + roleDisplayText, Toast.LENGTH_SHORT).show();
                                } else {
                                    String errorMessage = json.optString("message", "Failed to change role");
                                    Toast.makeText(GroupMembersActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(GroupMembersActivity.this, getString(R.string.error_parse), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String errorMessage = json.optString("message", "Failed to change role");
                                Toast.makeText(GroupMembersActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            } catch (JSONException e) {
                                Toast.makeText(GroupMembersActivity.this, getString(R.string.error_change_role_code, response.code()), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupMembersActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_preparing_request), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void confirmTransferOwnership(User member) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            "Transfer Ownership",
            "Are you sure you want to transfer group ownership to " + member.getDisplayName() + "? You will become a moderator.",
            "Transfer",
            "Cancel",
            () -> transferOwnership(member),
            null,
            false
        );
    }

    private void transferOwnership(User member) {
        Toast.makeText(this, getString(R.string.status_transferring_ownership), Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
        try {
            JSONObject ownershipData = new JSONObject();
            ownershipData.put("newOwnerId", member.getId());
            
            // Use chat ID (the endpoint accepts chat ID and finds the associated group)
            String chatId = currentChat.getId();
            
            apiClient.transferGroupOwnership(token, chatId, ownershipData, new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                if (json.optBoolean("success", false)) {
                                    Toast.makeText(GroupMembersActivity.this, 
                                        getString(R.string.success_ownership_transferred_successfully), Toast.LENGTH_SHORT).show();
                                    
                                    // Update local data - old owner becomes moderator, new owner becomes owner
                                    String currentUserId = databaseManager.getUserId();
                                    memberRoles.put(currentUserId, "Moderator");
                                    memberRoles.put(member.getId(), "Owner");
                                    
                                    // Update current chat's creator ID immediately so UI reflects change right away
                                    if (currentChat != null) {
                                        currentChat.setCreatorId(member.getId());
                                    }
                                    
                                    // Update adapter immediately to reflect role changes
                                    if (membersAdapter != null) {
                                        membersAdapter.setMemberRoles(memberRoles);
                                    }
                                    
                                    // Reload members to get fresh data from server and ensure consistency
                                    fetchGroupMembers();
                                } else {
                                    String errorMessage = json.optString("message", "Failed to transfer ownership");
                                    Toast.makeText(GroupMembersActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(GroupMembersActivity.this, getString(R.string.error_parse), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String errorMessage = json.optString("message", "Failed to transfer ownership");
                                Toast.makeText(GroupMembersActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            } catch (JSONException e) {
                                Toast.makeText(GroupMembersActivity.this, getString(R.string.error_transfer_ownership_code, response.code()), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupMembersActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_preparing_request), Toast.LENGTH_SHORT).show();
        }
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
        Toast.makeText(this, getString(R.string.status_removing_member), Toast.LENGTH_SHORT).show();
        
        // Debug logging
        android.util.Log.d("GroupMembersActivity", "Removing member: " + member.getDisplayName() + 
                          ", ID: " + member.getId() + 
                          ", Chat ID: " + currentChat.getId());
        
        String token = databaseManager.getToken();
        try {
            JSONObject memberData = new JSONObject();
            memberData.put("userId", member.getId());
            
            android.util.Log.d("GroupMembersActivity", "Sending remove request: " + memberData);
            
            apiClient.removeMember(token, currentChat.getId(), memberData, new Callback() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(GroupMembersActivity.this, getString(R.string.success_member_removed_successfully), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(GroupMembersActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_preparing_request), Toast.LENGTH_SHORT).show();
        }
    }
}
