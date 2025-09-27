package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class GroupMembersActivity extends AppCompatActivity implements GroupMembersAdapter.OnMemberClickListener {
    
    private TextView tvGroupName, tvMemberCount;
    private ImageView ivBack, ivAddMember;
    private RecyclerView rvMembers;
    private android.view.View btnAddMember;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAddMember;
    
    private Chat currentChat;
    private List<User> members;
    private GroupMembersAdapter membersAdapter;
    private SharedPreferencesManager sharedPrefsManager;
    private ApiClient apiClient;
    private AvatarManager avatarManager;
    
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
        ivAddMember = findViewById(R.id.iv_add_member);
        rvMembers = findViewById(R.id.rv_members);
        btnAddMember = findViewById(R.id.btn_add_member);
        fabAddMember = findViewById(R.id.fab_add_member);
    }
    
    private void initData() {
        sharedPrefsManager = new SharedPreferencesManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        members = new ArrayList<>();
        
        // Get chat data from intent
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
        // Back button
        ivBack.setOnClickListener(v -> finish());
        
        // Add member buttons
        btnAddMember.setOnClickListener(v -> showAddMemberDialog());
        fabAddMember.setOnClickListener(v -> showAddMemberDialog());
        ivAddMember.setOnClickListener(v -> showAddMemberDialog());
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
        
        // Load group avatar if available
        if (currentChat.getAvatar() != null && !currentChat.getAvatar().isEmpty()) {
            // Construct full URL for group avatar
            String avatarUrl = currentChat.getAvatar();
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
            }
            // Note: We would need to add an ImageView for group avatar in the layout
            // For now, just show a placeholder message
        }
        
        // Fetch member details from the server
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
                    for (int i = 0; i < membersArray.length(); i++) {
                        JSONObject memberJson = membersArray.getJSONObject(i);
                        android.util.Log.d("GroupMembersActivity", "Parsing member " + i + ": " + memberJson.toString());
                        User member = User.fromJson(memberJson);
                        android.util.Log.d("GroupMembersActivity", "Parsed member: " + member.getDisplayName() + 
                                          ", ID: " + member.getId());
                        members.add(member);
                    }
                    
                    membersAdapter.notifyDataSetChanged();
                    tvMemberCount.setText(members.size() + " members");
                    
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
    
    private void showAddMemberDialog() {
        // Navigate to SearchActivity for adding members
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
    
    @Override
    public void onMemberClick(User member) {
        // Show member profile or options
        new AlertDialog.Builder(this)
                .setTitle("Member Options")
                .setMessage("Member: " + member.getDisplayName())
                .setPositiveButton("View Profile", (dialog, which) -> {
                    // Navigate to member profile
                    Intent intent = new Intent(this, ProfileViewActivity.class);
                    try {
                        intent.putExtra("user", member.toJson().toString());
                        startActivity(intent);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error opening profile", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Remove", (dialog, which) -> {
                    confirmRemoveMember(member);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }
    
    private void confirmRemoveMember(User member) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Member")
                .setMessage("Are you sure you want to remove " + member.getDisplayName() + " from this group?")
                .setPositiveButton("Remove", (dialog, which) -> removeMember(member))
                .setNegativeButton("Cancel", null)
                .show();
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
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(GroupMembersActivity.this, "Member removed successfully", Toast.LENGTH_SHORT).show();
                            // Remove from local list
                            members.remove(member);
                            membersAdapter.notifyDataSetChanged();
                            // Update member count
                            tvMemberCount.setText(members.size() + " members");
                        } else {
                            Toast.makeText(GroupMembersActivity.this, "Failed to remove member", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupMembersActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing request", Toast.LENGTH_SHORT).show();
        }
    }
}
