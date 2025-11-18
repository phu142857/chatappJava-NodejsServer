package com.example.chatappjava.ui.theme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.adapters.ChatListAdapter;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.ConversationRepository;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.example.chatappjava.config.ServerConfig;
import com.squareup.picasso.Picasso;
import com.example.chatappjava.adapters.CallListAdapter;
import com.example.chatappjava.models.Call;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements ChatListAdapter.OnChatClickListener, CallListAdapter.OnCallClickListener{
    
    private static final String TAG = "HomeActivity";
    
    // UI Components
    // tvMessagesTitle removed - no longer needed
    private ImageView ivSearch, ivMoreVert;
    private View btnNewGroupAction;
    private View efabNewGroup;
    private TextView tvChats, tvGroups, tvCalls;
    private RecyclerView rvChatList;
    private LinearLayout llFriendRequests;
    private TextView tvFriendRequestCount;
    private TextView tvFriendRequestsTitle;
    
    // User Profile Components
    private de.hdodenhof.circleimageview.CircleImageView ivUserAvatar;
    private TextView tvUserName;
    
    // Data and Services
    private DatabaseManager databaseManager;
    private ConversationRepository conversationRepository;
    private ApiClient apiClient;
    private ChatListAdapter chatAdapter;
    private CallListAdapter callAdapter;
    private List<Chat> chatList;
    private List<Call> callList;
    private AvatarManager avatarManager;
    private boolean isLoadingChats = false;
    private boolean isLoadingCalls = false;
    private boolean isPolling = false;
    private AlertDialog currentDialog;
    private final java.util.Set<String> blockedUserIds = new java.util.HashSet<>();
    private static final long HOME_POLL_INTERVAL_MS = 2000L;
    private final Handler homePollHandler = new Handler(Looper.getMainLooper());
    private final Runnable homePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) {
                if (!isLoadingChats) {
                    loadChats();
                    loadFriendRequestCount();
                }
                homePollHandler.postDelayed(this, HOME_POLL_INTERVAL_MS);
            }
        }
    };
    
    // Current tab tracking
    private int currentTab = 0; // 0: Chats, 1: Groups, 2: Calls
    // Hold reference to message listener for add/remove
    private com.example.chatappjava.network.SocketManager.MessageListener homeMessageListener;
    private android.content.BroadcastReceiver blockedChangedReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        initializeViews();
        initializeServices();
        setupClickListeners();
        setupRecyclerView();
        loadUserData();
        loadUserProfile();
        loadFriendRequestCount();
        // Preload blocked users to filter chat list
        loadBlockedUsers();
        // Load chats initially so private chats are visible on Home
        loadChats();
        
        // Initialize default tab selection
        switchTab(0);

        // Handle system back using OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirm();
            }
        });

        // Listen for blocked list changes (unblock triggers refresh)
        blockedChangedReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                if ("com.example.chatappjava.ACTION_BLOCKED_USERS_CHANGED".equals(intent.getAction())) {
                    loadBlockedUsers();
                    loadChats();
                }
            }
        };
        registerReceiver(blockedChangedReceiver,
                new android.content.IntentFilter("com.example.chatappjava.ACTION_BLOCKED_USERS_CHANGED"),
                android.content.Context.RECEIVER_NOT_EXPORTED);
    }

    // removed duplicate onResume (blocked users refresh handled in main onResume below)
    
    private void initializeViews() {
        // messages_title removed from layout
        ivSearch = findViewById(R.id.iv_search);
        ivMoreVert = findViewById(R.id.iv_more_vert);
        tvChats = findViewById(R.id.tv_chats);
        tvGroups = findViewById(R.id.tv_groups);
        tvCalls = findViewById(R.id.tv_calls);
        rvChatList = findViewById(R.id.rv_chat_list);
        llFriendRequests = findViewById(R.id.ll_friend_requests);
        tvFriendRequestCount = findViewById(R.id.tv_friend_request_count);
        // Initialize friend requests title (different layout ids possible)
        tvFriendRequestsTitle = findViewById(R.id.tv_requests_title);
        // New Group Extended FAB
        efabNewGroup = findViewById(R.id.efab_new_group);
        
        // User Profile Components
        ivUserAvatar = findViewById(R.id.iv_user_avatar);
        tvUserName = findViewById(R.id.tv_user_name);
    }
    
    private void initializeServices() {
        databaseManager = new DatabaseManager(this);
        conversationRepository = new ConversationRepository(this);
        apiClient = new ApiClient();
        chatList = new ArrayList<>();
        callList = new ArrayList<>();
        avatarManager = AvatarManager.getInstance(this);
        avatarManager.initialize(); // Initialize avatar manager with scheduled refresh

        // Check if user is logged in
        if (!databaseManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }
    }
    
    private void setupClickListeners() {
        // Search functionality
        ivSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, SearchActivity.class);
                startActivity(intent);
            }
        });
        
        // Title removed - no click listener needed
        
        // More button: go directly to Settings
        ivMoreVert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(HomeActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
            }
        });

        // Friend requests
        llFriendRequests.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, FriendRequestActivity.class);
                startActivityForResult(intent, 1001);
            }
        });
        
        // Tab navigation
        tvChats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
                // Refresh chats when switching back to Chats tab
                loadChats();
            }
        });
        
        tvGroups.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });
        
        tvCalls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });
        if (efabNewGroup != null) {
            efabNewGroup.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent createGroupIntent = new Intent(HomeActivity.this, CreateGroupActivity.class);
                    startActivity(createGroupIntent);
                }
            });
        }
    }
    
    private void setupRecyclerView() {
        chatAdapter = new ChatListAdapter(this, chatList, this);
        
        // Setup call adapter
        callAdapter = new CallListAdapter(this);
        callAdapter.setOnCallClickListener(this);
        callAdapter.setCurrentUserId(databaseManager.getUserId());
        
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(chatAdapter);
    }
    
    private void loadUserData() {
        // No title needed - user profile is displayed separately
    }
    
    private void loadUserProfile() {
        // Check if views are initialized
        if (ivUserAvatar == null || tvUserName == null) {
                Log.e(TAG, "User profile views not initialized!");
            return;
        }
        
        // Load user profile information
        String username = databaseManager.getUserName();
        String avatarUrl = databaseManager.getUserAvatar();
        
        Log.d(TAG, "Loading user profile - Username: " + username + ", Avatar URL: " + avatarUrl);
        
        // Set username
        if (username != null && !username.isEmpty()) {
            tvUserName.setText(username);
        } else {
            tvUserName.setText("User");
        }
        
        // Load avatar - handle URL like ProfileActivity
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Log.d(TAG, "Loading avatar from URL: " + avatarUrl);
            
            // If it's a relative URL, prepend the server base URL (like ProfileActivity)
            String fullAvatarUrl = avatarUrl;
            if (!avatarUrl.startsWith("http")) {
                fullAvatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + avatarUrl;
                Log.d(TAG, "Constructed full URL: " + fullAvatarUrl);
            }
            
            try {
                // Try AvatarManager first
                avatarManager.loadAvatar(fullAvatarUrl, ivUserAvatar, R.drawable.ic_profile_placeholder);
                
                // Backup: Also try Picasso directly (like ProfileActivity)
                Picasso.get()
                        .load(fullAvatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(ivUserAvatar);
                        
            } catch (Exception e) {
                Log.e(TAG, "Error loading avatar: " + e.getMessage());
                // Fallback to direct Picasso load
                try {
                    Picasso.get()
                            .load(fullAvatarUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(ivUserAvatar);
                } catch (Exception e2) {
                    Log.e(TAG, "Picasso also failed: " + e2.getMessage());
                    ivUserAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        } else {
            Log.d(TAG, "No avatar URL, using placeholder");
            ivUserAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // Force visibility
        ivUserAvatar.setVisibility(View.VISIBLE);
        
        // Make user profile clickable to go to profile
        View.OnClickListener profileClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
                startActivity(intent);
            }
        };
        
        // Both avatar and profile section are clickable
        ivUserAvatar.setOnClickListener(profileClickListener);
        findViewById(R.id.user_profile_section).setOnClickListener(profileClickListener);
    }
    
    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        
        // Reset all tab states
        tvChats.setSelected(false);
        tvGroups.setSelected(false);
        tvCalls.setSelected(false);
        // Show title only on Chats tab
        if (tvFriendRequestsTitle != null) {
            tvFriendRequestsTitle.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        }
        // Show entire Friend Requests layout only on Chats tab
        if (llFriendRequests != null) {
            llFriendRequests.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        }
        // Toggle New Group Extended FAB visibility: only on Groups tab
        if (efabNewGroup != null) {
            efabNewGroup.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        }
        
        // Highlight selected tab (color will be handled by tab_text_selector)
        switch (tabIndex) {
            case 0:
                tvChats.setSelected(true);
                // Switch to chat adapter and show only private chats
                rvChatList.setAdapter(chatAdapter);
                applyChatsFilter();
                break;
            case 1:
                tvGroups.setSelected(true);
                // Switch to chat adapter and filter to show only group chats
                rvChatList.setAdapter(chatAdapter);
                applyGroupsFilter();
                break;
            case 2:
                tvCalls.setSelected(true);
                // Switch to call adapter and load call history
                rvChatList.setAdapter(callAdapter);
                loadCallHistory();
                break;
        }
    }

    private void applyChatsFilter() {
        if (chatAdapter == null) return;
        List<Chat> privates = new ArrayList<>();
        for (Chat c : chatList) {
            if (c != null && !c.isGroupChat()) {
                if (!isChatWithBlockedUser(c)) {
                    privates.add(c);
                }
            }
        }
        chatAdapter.updateChats(privates);
    }
    
    private void applyGroupsFilter() {
        if (chatAdapter == null) return;
        List<Chat> groups = new ArrayList<>();
        System.out.println("HomeActivity: Applying groups filter to " + chatList.size() + " chats");
        for (Chat c : chatList) {
            if (c != null && c.isGroupChat()) {
                System.out.println("HomeActivity: Adding group to filter: " + c.getName() + " (ID: " + c.getId() + ")");
                groups.add(c);
            }
        }
        System.out.println("HomeActivity: Groups filter result: " + groups.size() + " groups");
        chatAdapter.updateChats(groups);
    }
    
    private void loadCallHistory() {
        if (isLoadingCalls) {
            return;
        }
        isLoadingCalls = true;
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            isLoadingCalls = false;
            return;
        }
        
        System.out.println("HomeActivity: Starting to load call history with token: " + token.substring(0, 10) + "...");
        
        apiClient.getCallHistory(token, 50, 1, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    System.out.println("HomeActivity: Failed to load call history: " + e.getMessage());
                    isLoadingCalls = false;
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    handleCallHistoryResponse(response.code(), responseBody);
                    isLoadingCalls = false;
                });
            }
        });
    }
    
    private void handleCallHistoryResponse(int statusCode, String responseBody) {
        try {
            System.out.println("HomeActivity: Call History Response Code: " + statusCode);
            System.out.println("HomeActivity: Call History Response Body: " + responseBody);
            
            if (statusCode == 200) {
                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    org.json.JSONObject data = jsonResponse.getJSONObject("data");
                    org.json.JSONArray callsArray = data.getJSONArray("calls");
                    
                    System.out.println("HomeActivity: Found " + callsArray.length() + " calls");
                    
                    // Parse calls and update adapter
                    List<Call> calls = new ArrayList<>();
                    for (int i = 0; i < callsArray.length(); i++) {
                        org.json.JSONObject callJson = callsArray.getJSONObject(i);
                        System.out.println("HomeActivity: Call " + i + ": " + callJson.toString());
                        
                        try {
                            Call call = Call.fromJson(callJson);
                            calls.add(call);
                            System.out.println("HomeActivity: Successfully parsed call: " + call.getCallId() + " - " + call.getDisplayName());
                        } catch (Exception e) {
                            System.out.println("HomeActivity: Error parsing call " + i + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    // Update call list and adapter
                    this.callList = calls;
                    if (callAdapter != null) {
                        callAdapter.updateCalls(calls);
                        System.out.println("HomeActivity: Updated call adapter with " + calls.size() + " calls");
                    } else {
                        System.out.println("HomeActivity: Call adapter is null, cannot update calls");
                    }
                    
                } else {
                    System.out.println("HomeActivity: Failed to load call history: " + jsonResponse.optString("message", "Unknown error"));
                }
            } else if (statusCode == 401) {
                System.out.println("HomeActivity: Session expired while loading call history");
                databaseManager.clearLoginInfo();
                redirectToLogin();
            } else {
                System.out.println("HomeActivity: Failed to load call history with status: " + statusCode);
            }
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            System.out.println("HomeActivity: Error parsing call history response: " + e.getMessage());
        }
    }

    private void reloadHome() {
        // Refresh both chats and friend requests
        loadFriendRequestCount();
        loadChats();
        
        // Force refresh avatars on manual reload
        avatarManager.forceRefreshAvatars();
        
        Toast.makeText(this, "Reloading...", Toast.LENGTH_SHORT).show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void confirmDeleteChat(Chat chat) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Delete Chat",
                "Are you sure you want to delete this chat?",
                "Delete",
                "Cancel",
                () -> {
                    String token = databaseManager.getToken();
                    if (token == null || token.isEmpty()) {
                        Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new ApiClient().deleteChat(token, chat.getId(), new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Failed to delete chat", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            runOnUiThread(() -> {
                                if (response.code() == 200) {
                                    Toast.makeText(HomeActivity.this, "Chat deleted", Toast.LENGTH_SHORT).show();
                                    reloadHome();
                                } else {
                                    Toast.makeText(HomeActivity.this, "Delete chat failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                },
                null,
                false
        );
    }

    private void confirmUnfriend(Chat chat) {
        if (!chat.isPrivateChat()) {
            Toast.makeText(this, "Unfriend applies to private chats", Toast.LENGTH_SHORT).show();
            return;
        }
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Unfriend",
                "Are you sure you want to unfriend this user?",
                "Unfriend",
                "Cancel",
                () -> {
                    String token = databaseManager.getToken();
                    if (token == null || token.isEmpty()) {
                        Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String otherUserId = null;
                    if (chat.getOtherParticipant() != null) {
                        otherUserId = chat.getOtherParticipant().getId();
                    } else if (!chat.getParticipantIds().isEmpty()) {
                        // Fallback: pick a participant that's not current user
                        for (String pid : chat.getParticipantIds()) {
                            if (!pid.equals(databaseManager.getUserId())) {
                                otherUserId = pid;
                                break;
                            }
                        }
                    }
                    if (otherUserId == null || otherUserId.isEmpty()) {
                        Toast.makeText(this, "Cannot determine user to unfriend", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new ApiClient().unfriendUser(token, otherUserId, new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Failed to unfriend", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            runOnUiThread(() -> {
                                if (response.code() == 200) {
                                    Toast.makeText(HomeActivity.this, "Unfriended successfully", Toast.LENGTH_SHORT).show();
                                    // Do not remove chat; just reload to reflect state
                                    reloadHome();
                                } else {
                                    Toast.makeText(HomeActivity.this, "Unfriend failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                },
                null,
                false
        );
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        if (!databaseManager.isLoggedIn()) {
            redirectToLogin();
        } else {
            // Reload user profile data when returning to activity
            loadUserProfile();
            // Refresh friend request count when returning to activity
            loadFriendRequestCount();
            // Refresh blocked users and then chats
            loadBlockedUsers();
            // Also refresh chat list to keep it up to date
            loadChats();
            startHomePolling();
            // Setup socket listener for member removal
            setupSocketManager();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHomePolling();
        // Remove message listener to avoid leaks
        com.example.chatappjava.network.SocketManager socketManager = 
            com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        if (socketManager != null && homeMessageListener != null) {
            socketManager.removeMessageListener(homeMessageListener);
        }
    }
    
    private void setupSocketManager() {
        com.example.chatappjava.network.SocketManager socketManager = 
            com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        
        if (socketManager != null) {
            // Clear any existing listeners to prevent conflicts
            socketManager.removeMemberRemovedListener();
            
            // Add member removal listener
            socketManager.setMemberRemovedListener(new com.example.chatappjava.network.SocketManager.MemberRemovedListener() {
                @Override
                public void onMemberRemoved(String chatId, String chatName) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Member removed from group: " + chatName);
                        Toast.makeText(HomeActivity.this, "You have been removed from " + chatName, Toast.LENGTH_LONG).show();
                        // Refresh chat list to remove the group
                        loadChats();
                    });
                }

                @Override
                public void onMemberRemovedFromGroup(String chatId, String removedUserId, int totalMembers) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Member removed from group: " + removedUserId + ", total members: " + totalMembers);
                        // Refresh chat list to update member count
                        loadChats();
                    });
                }
            });

            // Add message multi-listener to refresh list on new messages
            if (homeMessageListener == null) {
                homeMessageListener = new com.example.chatappjava.network.SocketManager.MessageListener() {
                    @Override
                    public void onPrivateMessage(org.json.JSONObject messageJson) {
                        runOnUiThread(() -> loadChats());
                    }
                    @Override
                    public void onGroupMessage(org.json.JSONObject messageJson) {
                        runOnUiThread(() -> loadChats());
                    }
                    @Override
                    public void onMessageEdited(org.json.JSONObject messageJson) {
                        runOnUiThread(() -> loadChats());
                    }
                    @Override
                    public void onMessageDeleted(org.json.JSONObject messageMetaJson) {
                        runOnUiThread(() -> loadChats());
                    }
                    @Override
                    public void onReactionUpdated(org.json.JSONObject reactionJson) {
                        // no-op for home list
                    }
                };
            }
            socketManager.addMessageListener(homeMessageListener);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up socket listeners to prevent memory leaks
        com.example.chatappjava.network.SocketManager socketManager = 
            com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        if (socketManager != null) {
            socketManager.removeMemberRemovedListener();
        }
        if (blockedChangedReceiver != null) {
            try { unregisterReceiver(blockedChangedReceiver); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Friend request was accepted/rejected, refresh count and chat list
            loadFriendRequestCount();
            loadChats(); // Refresh chat list to show new chat if friend was accepted
        }
    }
    
    private void showExitConfirm() {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Exit App",
                "Are you sure you want to exit?",
                "Yes",
                "No",
                () -> { stopHomePolling(); finish(); },
                null,
                false
        );
    }

    private void loadFriendRequestCount() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            updateFriendRequestBadge(0, 0);
            return;
        }

        apiClient.getFriendRequests(token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> updateFriendRequestBadge(0, 0));
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        if (response.code() == 200) {
                            org.json.JSONObject data = jsonResponse.getJSONObject("data");
                            org.json.JSONArray requestsArray = data.getJSONArray("requests");

                            int totalCount = requestsArray.length();
                            int pendingReceivedCount = 0;
                            for (int i = 0; i < requestsArray.length(); i++) {
                                org.json.JSONObject requestJson = requestsArray.getJSONObject(i);
                                String status = requestJson.optString("status", "");
                                String receiverId = requestJson.optString("receiverId", "");
                                if ("pending".equals(status) && receiverId.equals(databaseManager.getUserId())) {
                                    pendingReceivedCount++;
                                }
                            }

                            updateFriendRequestBadge(totalCount, pendingReceivedCount);
                        } else {
                            updateFriendRequestBadge(0, 0);
                        }
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                        updateFriendRequestBadge(0, 0);
                    }
                });
            }
        });
    }

    private void updateFriendRequestBadge(int totalCount, int pendingReceivedCount) {
        if (tvFriendRequestsTitle != null) {
            String baseTitle = "Friends";
            tvFriendRequestsTitle.setText(baseTitle + " (" + totalCount + ")");
        }
        if (pendingReceivedCount > 0) {
            tvFriendRequestCount.setText(String.valueOf(pendingReceivedCount));
            tvFriendRequestCount.setVisibility(View.VISIBLE);
        } else {
            tvFriendRequestCount.setVisibility(View.GONE);
        }
    }

    private void startHomePolling() {
        if (isPolling) return;
        isPolling = true;
        homePollHandler.postDelayed(homePollRunnable, HOME_POLL_INTERVAL_MS);
    }

    private void stopHomePolling() {
        if (!isPolling) return;
        isPolling = false;
        homePollHandler.removeCallbacks(homePollRunnable);
    }
    
    private void loadChats() {
        if (isLoadingChats) {
            return;
        }
        isLoadingChats = true;
        System.out.println("HomeActivity: Loading chats...");
        
        // First, try to load from local database (works offline)
        loadChatsFromDatabase();
        
        // Force refresh avatars on reload
        avatarManager.forceRefreshAvatars();
        
        // Then try to load from server if network is available
        if (!isNetworkAvailable()) {
            System.out.println("HomeActivity: No network, showing chats from database");
            isLoadingChats = false;
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            System.out.println("HomeActivity: No token available for loading chats");
            isLoadingChats = false;
            return;
        }
        
        apiClient.getChats(token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    System.out.println("HomeActivity: Failed to load chats: " + e.getMessage());
                    // If we failed to load from server, show chats from database
                    if (chatList.isEmpty()) {
                        loadChatsFromDatabase();
                    }
                    isLoadingChats = false;
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    handleChatsResponse(response.code(), responseBody);
                    isLoadingChats = false;
                });
            }
        });
    }
    
    /**
     * Load chats from local database
     */
    private void loadChatsFromDatabase() {
        if (conversationRepository == null) return;
        
        List<Chat> dbChats = conversationRepository.getAllConversations();
        if (!dbChats.isEmpty()) {
            this.chatList = dbChats;
            if (chatAdapter != null) {
                if (currentTab == 1) {
                    applyGroupsFilter();
                } else {
                    applyChatsFilter();
                }
            }
            System.out.println("HomeActivity: Loaded " + dbChats.size() + " chats from database");
        }
    }
    
    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private boolean isChatWithBlockedUser(Chat chat) {
        try {
            if (chat == null || chat.isGroupChat()) return false;
            // Prefer otherParticipant when available
            String otherId = null;
            if (chat.getParticipantIds() != null) {
                for (String pid : chat.getParticipantIds()) {
                    if (pid != null && !pid.equals(databaseManager.getUserId())) { otherId = pid; break; }
                }
            }
            // Fallback to potential field on otherParticipant if provided
            if (otherId == null && chat.toJson().has("otherParticipant")) {
                // no-op: participantIds already covers this normally
            }
            return otherId != null && blockedUserIds.contains(otherId);
        } catch (Exception ignored) { return false; }
    }

    private void loadBlockedUsers() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;
        apiClient.getBlockedUsers(token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) { /* ignore */ }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful()) return;
                String body = response.body().string();
                try {
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    org.json.JSONArray arr = json.getJSONObject("data").getJSONArray("users");
                    java.util.Set<String> ids = new java.util.HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject u = arr.getJSONObject(i);
                        String id = u.optString("_id", u.optString("id", ""));
                        if (!id.isEmpty()) ids.add(id);
                    }
                    runOnUiThread(() -> {
                        blockedUserIds.clear();
                        blockedUserIds.addAll(ids);
                        // Refresh current list view if on Chats tab
                        if (currentTab == 0) applyChatsFilter();
                    });
                } catch (Exception ignored) {}
            }
        });
    }
    
    private void handleChatsResponse(int statusCode, String responseBody) {
        try {
            System.out.println("HomeActivity: Chats Response Code: " + statusCode);
            System.out.println("HomeActivity: Chats Response Body: " + responseBody);
            
            if (statusCode == 200) {
                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    org.json.JSONObject data = jsonResponse.getJSONObject("data");
                    org.json.JSONArray chatsArray = data.getJSONArray("chats");
                    
                    System.out.println("HomeActivity: Found " + chatsArray.length() + " chats");
                    
                    // Parse chats and update adapter
                    List<Chat> chats = new ArrayList<>();
                    for (int i = 0; i < chatsArray.length(); i++) {
                        org.json.JSONObject chatJson = chatsArray.getJSONObject(i);
                        System.out.println("HomeActivity: Chat " + i + ": " + chatJson.toString());
                        
                        try {
                            Chat chat = Chat.fromJson(chatJson);
                            chats.add(chat);
                            
                            // Save to database for offline access
                            if (conversationRepository != null) {
                                conversationRepository.saveConversation(chat);
                            }
                        } catch (Exception e) {
                            System.out.println("HomeActivity: Error parsing chat " + i + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    // Store full list, then update adapter based on current tab
                    this.chatList = chats;
                    if (chatAdapter != null) {
                        if (currentTab == 1) {
                            applyGroupsFilter();
                        } else {
                            applyChatsFilter();
                        }
                        System.out.println("HomeActivity: Updated adapter with " + (currentTab == 1 ? "groups filter" : "all chats") );
                    } else {
                        System.out.println("HomeActivity: Adapter is null, cannot update chats");
                    }
                    
                } else {
                    System.out.println("HomeActivity: Failed to load chats: " + jsonResponse.optString("message", "Unknown error"));
                }
            } else if (statusCode == 401) {
                System.out.println("HomeActivity: Session expired while loading chats");
                databaseManager.clearLoginInfo();
                // Redirect to login
                Intent intent = new Intent(HomeActivity.this, com.example.chatappjava.ui.theme.LoginActivity.class);
                startActivity(intent);
                finish();
            } else {
                System.out.println("HomeActivity: Failed to load chats with status: " + statusCode);
            }
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            System.out.println("HomeActivity: Error parsing chats response: " + e.getMessage());
        }
    }
    
    private void showGroupInfo(Chat chat) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Group Information")
                .setMessage("Group Name: " + chat.getName() + "\n" +
                           "Members: " + chat.getParticipantCount() + "\n" +
                           "Created: " + (chat.getCreatedAt() != 0 ? new java.util.Date(chat.getCreatedAt()).toString() : "Unknown"))
                .setPositiveButton("OK", null)
                .setNeutralButton("View Members", (dialog, which) -> {
                    Intent intent = new Intent(this, GroupMembersActivity.class);
                    intent.putExtra("chatId", chat.getId());
                    intent.putExtra("mode", "view");
                    startActivity(intent);
                })
                .show();
    }
    
    private void addMembers(Chat chat) {
        Toast.makeText(this, "Add members feature coming soon", Toast.LENGTH_SHORT).show();
    }
    
    private void removeMembers(Chat chat) {
        Toast.makeText(this, "Remove members feature coming soon", Toast.LENGTH_SHORT).show();
    }
    
    private void confirmLeaveGroup(Chat chat) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Leave Group",
                "Are you sure you want to leave this group? You will no longer receive messages from this group.",
                "Leave",
                "Cancel",
                () -> leaveGroup(chat),
                null,
                false
        );
    }
    
    private void leaveGroup(Chat chat) {
        Toast.makeText(this, "Leaving group...", Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
        apiClient.leaveGroup(token, chat.getId(), new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            
                            if (jsonResponse.optBoolean("deleted", false)) {
                                // Group was deleted
                                String message = jsonResponse.optString("message", "Group deleted successfully");
                                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                            } else {
                                // Normal leave
                                Toast.makeText(HomeActivity.this, "Left group successfully", Toast.LENGTH_SHORT).show();
                            }
                            
                            // Add small delay to ensure server processing is complete
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                loadChats(); // Refresh chat list
                            }, 500);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(HomeActivity.this, "Left group successfully", Toast.LENGTH_SHORT).show();
                            loadChats();
                        }
                    } else {
                        Toast.makeText(HomeActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(HomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void confirmDeleteGroup(Chat chat) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Delete Group",
                "Are you sure you want to delete this group? This action cannot be undone and all messages will be lost.",
                "Delete",
                "Cancel",
                () -> deleteGroup(chat),
                null,
                false
        );
    }
    
    private void deleteGroup(Chat chat) {
        Toast.makeText(this, "Deleting group...", Toast.LENGTH_SHORT).show();
        
        String token = databaseManager.getToken();
        apiClient.deleteChat(token, chat.getId(), new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(HomeActivity.this, "Group deleted successfully", Toast.LENGTH_SHORT).show();
                        loadChats(); // Refresh chat list
                    } else {
                        Toast.makeText(HomeActivity.this, "Failed to delete group", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(HomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    // ChatListAdapter.OnChatClickListener implementation
    @Override
    public void onChatClick(Chat chat) {
        Intent intent;
        
        // Choose appropriate activity based on chat type
        if (chat.isGroupChat()) {
            intent = new Intent(this, GroupChatActivity.class);
        } else {
            intent = new Intent(this, PrivateChatActivity.class);
        }
        
        // Pass chat data
        try {
            intent.putExtra("chat", chat.toJson().toString());
            
            // For private chats, pass the other participant's info
            if (chat.isPrivateChat() && chat.getOtherParticipant() != null) {
                intent.putExtra("user", chat.getOtherParticipant().toJson().toString());
            }
            
            startActivity(intent);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showChatInfo(Chat chat) {
        if (chat.isPrivateChat() && chat.getOtherParticipant() != null) {
            // For private chat, show other user's profile
            Intent intent = new Intent(this, ProfileViewActivity.class);
            try {
                intent.putExtra("user", chat.getOtherParticipant().toJson().toString());
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error opening profile", Toast.LENGTH_SHORT).show();
            }
        } else if (chat.isGroupChat()) {
            // For group chat, show group info
            String chatName = chat.getDisplayName();
            String chatType = "Group Chat";
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Chat Information")
                    .setMessage("Name: " + chatName + "\nType: " + chatType)
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            Toast.makeText(this, "No chat information available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onChatLongClick(Chat chat) {
        if (chat.isGroupChat()) {
            // Show group-specific options
            showGroupChatOptions(chat);
        } else {
            // Show private chat options
            showPrivateChatOptions(chat);
        }
    }
    
    private void showGroupChatOptions(Chat chat) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_group_chat_options, null);
        
        // Check if current user is the group owner
        String currentUserId = databaseManager != null ? databaseManager.getUserId() : null;
        String creatorId = chat.getCreatorId();
        boolean isOwner = creatorId != null && !creatorId.isEmpty() && currentUserId != null && currentUserId.equals(creatorId);
        
        // Get option views
        LinearLayout optionViewInfo = dialogView.findViewById(R.id.option_view_group_info);
        LinearLayout optionDeleteChat = dialogView.findViewById(R.id.option_delete_chat);
        LinearLayout optionDeleteGroup = dialogView.findViewById(R.id.option_delete_group);
        LinearLayout optionLeaveGroup = dialogView.findViewById(R.id.option_leave_group);
        LinearLayout optionJoinRequests = dialogView.findViewById(R.id.option_join_requests);
        
        // Show/hide options based on user role
        // Owner: show delete options, hide leave group, show join requests
        // Moderator/Member: show leave group, hide delete options, hide join requests
        if (optionDeleteChat != null) {
            optionDeleteChat.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        }
        
        if (optionDeleteGroup != null) {
            optionDeleteGroup.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        }
        
        if (optionLeaveGroup != null) {
            optionLeaveGroup.setVisibility(isOwner ? View.GONE : View.VISIBLE);
        }
        
        if (optionJoinRequests != null) {
            optionJoinRequests.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        }
        
        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        // Set click listeners with null checks
        if (optionViewInfo != null) {
            optionViewInfo.setOnClickListener(v -> {
                showChatInfo(chat);
                dialog.dismiss();
            });
        }
        
        if (optionDeleteChat != null && isOwner) {
            optionDeleteChat.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDeleteChat(chat);
            });
        }
        
        if (optionDeleteGroup != null && isOwner) {
            optionDeleteGroup.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDeleteGroup(chat);
            });
        }
        
        if (optionLeaveGroup != null && !isOwner) {
            optionLeaveGroup.setOnClickListener(v -> {
                dialog.dismiss();
                confirmLeaveGroup(chat);
            });
        }
        
        if (optionJoinRequests != null && isOwner) {
            optionJoinRequests.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(this, GroupJoinRequestsActivity.class);
                try {
                    intent.putExtra("chat", chat.toJson().toString());
                    startActivity(intent);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error opening join requests", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        dialog.show();
    }
    
    private void showPrivateChatOptions(Chat chat) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chat_options, null);

        // Hide group-specific options for private chat
        dialogView.findViewById(R.id.card_add_members).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_remove_members).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_leave_group).setVisibility(View.GONE);
        dialogView.findViewById(R.id.card_delete_group).setVisibility(View.GONE);

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // Set click listeners
        dialogView.findViewById(R.id.option_view_info).setOnClickListener(v -> {
            showChatInfo(chat);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.option_delete_chat).setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteChat(chat);
        });

        // Check friendship status and show appropriate options
        resolveFriendshipAndToggleOptions(chat, dialogView, dialog);

        dialog.show();
    }
    
    private void resolveFriendshipAndToggleOptions(Chat chat, View dialogView, AlertDialog dialog) {
        User otherUser = chat.getOtherParticipant();
        if (otherUser == null) {
            // Default to block option if no other participant
            dialogView.findViewById(R.id.card_unfriend).setVisibility(View.GONE);
            dialogView.findViewById(R.id.card_block_user).setVisibility(View.VISIBLE);
            
            // Set block click listener
            dialogView.findViewById(R.id.option_block_user).setOnClickListener(v -> {
                dialog.dismiss();
                confirmBlockUser(chat);
            });
            return;
        }

        // Check local isFriend first
        boolean localIsFriend = otherUser.isFriend();
        if (localIsFriend) {
            dialogView.findViewById(R.id.card_unfriend).setVisibility(View.VISIBLE);
            dialogView.findViewById(R.id.card_block_user).setVisibility(View.GONE);
            
            // Set unfriend click listener
            dialogView.findViewById(R.id.option_unfriend).setOnClickListener(v -> {
                dialog.dismiss();
                confirmUnfriend(chat);
            });
            return;
        }

        // If not locally marked as friend, check with server
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            dialogView.findViewById(R.id.card_unfriend).setVisibility(View.GONE);
            dialogView.findViewById(R.id.card_block_user).setVisibility(View.VISIBLE);
            
            // Set block click listener
            dialogView.findViewById(R.id.option_block_user).setOnClickListener(v -> {
                dialog.dismiss();
                confirmBlockUser(chat);
            });
            return;
        }

        apiClient.authenticatedGet("/api/users/friends", token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    dialogView.findViewById(R.id.card_unfriend).setVisibility(View.GONE);
                    dialogView.findViewById(R.id.card_block_user).setVisibility(View.VISIBLE);
                    
                    // Set block click listener
                    dialogView.findViewById(R.id.option_block_user).setOnClickListener(v -> {
                        dialog.dismiss();
                        confirmBlockUser(chat);
                    });
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "";
                boolean found = false;
                try {
                    if (response.isSuccessful()) {
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        org.json.JSONObject data = json.has("data") ? json.getJSONObject("data") : json;
                        org.json.JSONArray arr = data.has("friends") ? data.getJSONArray("friends") : new org.json.JSONArray();
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject u = arr.getJSONObject(i);
                            String fid = u.optString("id", u.optString("_id", ""));
                            if (otherUser.getId().equals(fid)) { 
                                found = true; 
                                break; 
                            }
                        }
                    }
                } catch (Exception ignored) {}

                final boolean isFriendNow = found;
                runOnUiThread(() -> {
                    if (isFriendNow) {
                        dialogView.findViewById(R.id.card_unfriend).setVisibility(View.VISIBLE);
                        dialogView.findViewById(R.id.card_block_user).setVisibility(View.GONE);
                        
                        // Set unfriend click listener
                        dialogView.findViewById(R.id.option_unfriend).setOnClickListener(v -> {
                            dialog.dismiss();
                            confirmUnfriend(chat);
                        });
                    } else {
                        dialogView.findViewById(R.id.card_unfriend).setVisibility(View.GONE);
                        dialogView.findViewById(R.id.card_block_user).setVisibility(View.VISIBLE);
                        
                        // Set block click listener
                        dialogView.findViewById(R.id.option_block_user).setOnClickListener(v -> {
                            dialog.dismiss();
                            confirmBlockUser(chat);
                        });
                    }
                });
            }
        });
    }
    
    private void confirmBlockUser(Chat chat) {
        if (chat.getOtherParticipant() == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String otherUserId = chat.getOtherParticipant().getId();
        String otherUserName = chat.getOtherParticipant().getDisplayName();
        
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            "Block User",
            "Are you sure you want to block " + otherUserName + "? You won't be able to send or receive messages from them.",
            "Block",
            "Cancel",
            () -> blockUser(otherUserId),
            null,
            false
        );
    }
    
    private void blockUser(String userId) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.blockUser(token, userId, "block", new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Failed to block user", Toast.LENGTH_SHORT).show());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(HomeActivity.this, "User blocked successfully", Toast.LENGTH_SHORT).show();
                        // Reload chats to reflect the change
                        loadChats();
                    } else {
                        Toast.makeText(HomeActivity.this, "Failed to block user: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
 
    // CallListAdapter.OnCallClickListener implementation
    @Override
    public void onCallItemClick(Call call) {
        // Show call details dialog
        showCallDetails(call);
    }
    
    @Override
    public void onCallActionClick(Call call) {
        // Initiate a new call to the same chat
        initiateNewCall(call);
    }
    
    private void showCallDetails(Call call) {
        String callType = call.isVideoCall() ? "Video Call" : "Audio Call";
        String duration = call.getDuration() > 0 ? call.getFormattedDuration() : "No duration";
        String status = call.getStatusText();
        String time = call.getFormattedTime() + " - " + call.getFormattedDate();
        
        String message = "Call Type: " + callType + "\n" +
                        "Duration: " + duration + "\n" +
                        "Status: " + status + "\n" +
                        "Time: " + time;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Call Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNeutralButton("Call Again", (dialog, which) -> {
                    initiateNewCall(call);
                })
                .show();
    }
    
    private void initiateNewCall(Call call) {
        // Find the chat for this call
        Chat targetChat = null;
        for (Chat chat : chatList) {
            if (chat.getId().equals(call.getChatId())) {
                targetChat = chat;
                break;
            }
        }
        
        if (targetChat == null) {
            Toast.makeText(this, "Chat not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Start ringing activity for outgoing call
        Intent intent = new Intent(this, com.example.chatappjava.ui.call.RingingActivity.class);
        intent.putExtra("isIncomingCall", false);
        intent.putExtra("callType", call.getType());
        intent.putExtra("chatId", call.getChatId());
        intent.putExtra("chatName", call.getDisplayName());
        
        try {
            intent.putExtra("chat", targetChat.toJson().toString());
            if (targetChat.isPrivateChat() && targetChat.getOtherParticipant() != null) {
                intent.putExtra("user", targetChat.getOtherParticipant().toJson().toString());
            }
            startActivity(intent);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initiating call", Toast.LENGTH_SHORT).show();
        }
    }

}
