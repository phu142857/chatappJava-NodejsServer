package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.adapters.UserSearchAdapter;
import com.example.chatappjava.adapters.GroupSearchAdapter;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
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

public class SearchActivity extends AppCompatActivity implements UserSearchAdapter.OnUserClickListener, GroupSearchAdapter.OnGroupClickListener {
    
    private EditText etSearch;
    private ImageView ivBack, ivClear;
    private RecyclerView rvSearchResults;
    private ProgressBar progressBar;
    private LinearLayout tvNoResults, tvSearchHint;
    private TextView tvNoResultsTitle;
    private TextView tabUsers, tabGroups, tabDiscover;
    
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefsManager;
    private UserSearchAdapter userAdapter;
    private GroupSearchAdapter groupAdapter;
    private List<User> searchResults;
    private List<Chat> groupResults;
    
    private String mode; // "add_members", "forward" or null for normal search
    private String forwardContent; // for forward mode (text fallback)
    private String forwardMessageRaw; // JSON: type/content/attachments
    private Chat currentChat; // For add_members mode
    private List<String> currentGroupMemberIds; // Track current group members
    private boolean isSearchingGroups = false; // Track current search mode
    private boolean isDiscoverGroups = false; // Discover public groups user not in
    
    private static final int SEARCH_DELAY_MS = 500; // Delay before making API call
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        
        initializeViews();
        initializeServices();
        setupSearchFunctionality();
        setupRecyclerView();
        setupClickListeners();
        
        // Set default tab to users
        switchToUserSearch();
    }
    
    private void initializeViews() {
        etSearch = findViewById(R.id.et_search);
        ivBack = findViewById(R.id.iv_back);
        ivClear = findViewById(R.id.iv_clear);
        rvSearchResults = findViewById(R.id.rv_search_results);
        progressBar = findViewById(R.id.progress_bar);
        tvNoResults = findViewById(R.id.tv_no_results);
        tvSearchHint = findViewById(R.id.tv_search_hint);
        tvNoResultsTitle = findViewById(R.id.tv_no_results_title);
        tabUsers = findViewById(R.id.tab_users);
        tabGroups = findViewById(R.id.tab_groups);
        tabDiscover = findViewById(R.id.tab_discover);
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
        sharedPrefsManager = new SharedPreferencesManager(this);
        searchResults = new ArrayList<>();
        groupResults = new ArrayList<>();
        currentGroupMemberIds = new ArrayList<>();
        
        // Get mode and chat data from intent
        Intent intent = getIntent();
        mode = intent.getStringExtra("mode");
        if ("forward".equals(mode)) {
            forwardContent = intent.getStringExtra("forward_content");
            forwardMessageRaw = intent.getStringExtra("forward_message");
        }
        if ("add_members".equals(mode) && intent.hasExtra("chat")) {
            try {
                LinearLayout searchTabs = findViewById(R.id.search_tabs);
                String chatJson = intent.getStringExtra("chat");
                JSONObject chatJsonObj = new JSONObject(chatJson);
                currentChat = Chat.fromJson(chatJsonObj);
                searchTabs.setVisibility(View.GONE);
                // Load current group members to avoid showing add button for existing members
                loadCurrentGroupMembers();
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading chat data", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void loadCurrentGroupMembers() {
        if (currentChat == null) return;
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) return;

        // Debug logging
        android.util.Log.d("SearchActivity", "Loading current group members for chat: " + currentChat.getName());
        android.util.Log.d("SearchActivity", "Current chat participantIds: " + currentChat.getParticipantIds());
        android.util.Log.d("SearchActivity", "Current chat participant count: " + currentChat.getParticipantCount());

        // Use same logic as GroupMembersActivity - fetch members from server
        apiClient.getGroupMembers(token, currentChat.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("SearchActivity", "Failed to load members: " + e.getMessage());
                    // Fallback to currentChat participantIds if available
                    if (currentChat.getParticipantIds() != null && !currentChat.getParticipantIds().isEmpty()) {
                        currentGroupMemberIds.clear();
                        currentGroupMemberIds.addAll(currentChat.getParticipantIds());
                        android.util.Log.d("SearchActivity", "Using fallback participantIds: " + currentGroupMemberIds);
                        if (userAdapter != null) userAdapter.notifyDataSetChanged();
                    }
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
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
                    
                    java.util.List<String> ids = new java.util.ArrayList<>();
                    for (int i = 0; i < membersArray.length(); i++) {
                        JSONObject memberJson = membersArray.getJSONObject(i);
                        android.util.Log.d("SearchActivity", "Parsing member " + i + ": " + memberJson.toString());
                        
                        // Extract user ID from member object (same as GroupMembersActivity)
                        String userId = null;
                        if (memberJson.has("user") && memberJson.get("user") instanceof JSONObject) {
                            JSONObject userObj = memberJson.getJSONObject("user");
                            userId = userObj.optString("_id", userObj.optString("id", ""));
                        } else if (memberJson.has("_id")) {
                            userId = memberJson.optString("_id", "");
                        } else if (memberJson.has("id")) {
                            userId = memberJson.optString("id", "");
                        }
                        
                        if (userId != null && !userId.isEmpty()) {
                            ids.add(userId);
                            android.util.Log.d("SearchActivity", "Added member ID: " + userId);
                        }
                    }
                    
                    android.util.Log.d("SearchActivity", "Parsed " + ids.size() + " member IDs from server");
                    
                    currentGroupMemberIds.clear();
                    currentGroupMemberIds.addAll(ids);
                    android.util.Log.d("SearchActivity", "Final currentGroupMemberIds: " + currentGroupMemberIds);
                    
                    if (userAdapter != null) userAdapter.notifyDataSetChanged();
                    
                } else {
                    String message = jsonResponse.optString("message", "Failed to load members");
                    android.util.Log.e("SearchActivity", message);
                    // Fallback to currentChat participantIds
                    if (currentChat.getParticipantIds() != null && !currentChat.getParticipantIds().isEmpty()) {
                        currentGroupMemberIds.clear();
                        currentGroupMemberIds.addAll(currentChat.getParticipantIds());
                        android.util.Log.d("SearchActivity", "Using fallback participantIds: " + currentGroupMemberIds);
                        if (userAdapter != null) userAdapter.notifyDataSetChanged();
                    }
                }
            } else {
                android.util.Log.e("SearchActivity", "Failed to load members: " + statusCode);
                // Fallback to currentChat participantIds
                if (currentChat.getParticipantIds() != null && !currentChat.getParticipantIds().isEmpty()) {
                    currentGroupMemberIds.clear();
                    currentGroupMemberIds.addAll(currentChat.getParticipantIds());
                    android.util.Log.d("SearchActivity", "Using fallback participantIds: " + currentGroupMemberIds);
                    if (userAdapter != null) userAdapter.notifyDataSetChanged();
                }
            }
        } catch (JSONException e) {
            android.util.Log.e("SearchActivity", "Error parsing members data: " + e.getMessage());
            // Fallback to currentChat participantIds
            if (currentChat.getParticipantIds() != null && !currentChat.getParticipantIds().isEmpty()) {
                currentGroupMemberIds.clear();
                currentGroupMemberIds.addAll(currentChat.getParticipantIds());
                android.util.Log.d("SearchActivity", "Using fallback participantIds: " + currentGroupMemberIds);
                if (userAdapter != null) userAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh members list when returning to screen to avoid stale "Already a Member" status
        if ("add_members".equals(mode)) {
            loadCurrentGroupMembers();

        }
    }
    
    private void setupSearchFunctionality() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel previous search
                if (searchRunnable != null) {
                    etSearch.removeCallbacks(searchRunnable);
                }
                
                // Update clear button visibility
                ivClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                
                // For group tabs: show immediately (no min length on client; server requires >=2)
                if (isSearchingGroups) {
                    searchRunnable = () -> performSearch(s.toString().trim());
                    etSearch.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                    return;
                }
                
                if (s.length() == 0) {
                    clearSearchResults();
                    return;
                }
                
                // Users: require minimal length
                searchRunnable = () -> performSearch(s.toString().trim());
                etSearch.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void setupRecyclerView() {
        userAdapter = new UserSearchAdapter(this, searchResults, this, mode, currentGroupMemberIds);
        groupAdapter = new GroupSearchAdapter(this, groupResults);
        groupAdapter.setOnGroupClickListener(this);
        
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(userAdapter); // Start with user adapter
        // Forward mode: ensure groups tab shows Forward button
        groupAdapter.setForwardMode("forward".equals(mode));
    }
    
    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
        
        ivClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.requestFocus();
            if (isSearchingGroups) {
                // Reset list to all groups when clearing query
                groupAdapter.updateGroups(groupResults);
                updateResultsVisibility();
            }
        });
        
        // Tab click listeners
        tabUsers.setOnClickListener(v -> switchToUserSearch());
        
        tabGroups.setOnClickListener(v -> switchToGroupSearch());
        
        if (tabDiscover != null) {
            tabDiscover.setOnClickListener(v -> switchToDiscoverGroups());
        }
        
        // Focus on search field when activity starts
        etSearch.requestFocus();
    }
    
    private void switchToUserSearch() {
        isSearchingGroups = false;
        isDiscoverGroups = false;
        tabUsers.setSelected(true);
        tabGroups.setSelected(false);
        if (tabDiscover != null) tabDiscover.setSelected(false);
        rvSearchResults.setAdapter(userAdapter);
        clearSearchResults();
        etSearch.setHint("Search users...");
    }
    
    private void switchToGroupSearch() {
        isSearchingGroups = true;
        isDiscoverGroups = false;
        tabUsers.setSelected(false);
        tabGroups.setSelected(true);
        if (tabDiscover != null) tabDiscover.setSelected(false);
        rvSearchResults.setAdapter(groupAdapter);
        groupAdapter.setDiscoverMode(false);
        groupAdapter.setForwardMode("forward".equals(mode));
        // Do not clear results after load finishes; show loading then populate
        etSearch.setHint("Search groups...");
        loadUserGroups(); // Load user's groups when switching to group search
    }

    private void switchToDiscoverGroups() {
        isSearchingGroups = true;
        isDiscoverGroups = true;
        tabUsers.setSelected(false);
        tabGroups.setSelected(false);
        if (tabDiscover != null) tabDiscover.setSelected(true);
        rvSearchResults.setAdapter(groupAdapter);
        groupAdapter.setDiscoverMode(true);
        groupAdapter.setForwardMode(false);
        etSearch.setHint("Discover public groups...");
        // Do not load or show groups until user types a search
        groupResults.clear();
        groupAdapter.updateGroups(groupResults);
        updateResultsVisibility();
    }

    private String getCurrentQuery() {
        return etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
    }

    private void applyGroupFilterWithCurrentQuery() {
        String q = getCurrentQuery();
        if (q.isEmpty()) {
            groupAdapter.updateGroups(groupResults);
        } else {
            List<Chat> filtered = new ArrayList<>();
            for (Chat g : groupResults) {
                if (g != null && g.getName() != null && g.getName().toLowerCase().contains(q.toLowerCase())) {
                    filtered.add(g);
                }
            }
            groupAdapter.updateGroups(filtered);
        }
        updateResultsVisibility();
    }
    
    private void performSearch(String query) {
        // Users tab: enforce min length; Groups tab: allow empty (show all) and avoid server calls for <2 chars
        if (!isSearchingGroups && query.length() < 2) {
            clearSearchResults();
            return;
        }
        
        if (isSearchingGroups) {
            // My Groups: always filter locally on loaded list
            if (!isDiscoverGroups) {
                applyGroupFilterWithCurrentQuery();
                return;
            }

            // Discover: require minimum query length before showing anything
            if (query.length() < 2) {
                // Hide list and show hint until user types enough
                groupAdapter.updateGroups(new ArrayList<>());
                updateResultsVisibility();
                return;
            }

            // If not yet loaded public groups, load then filter by current query
            if (groupResults == null || groupResults.isEmpty()) {
                loadDiscoverGroups();
            } else {
                applyGroupFilterWithCurrentQuery();
            }
            return;
        }
        
        showLoading(true);
        
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Search users
        searchUsers(token, query);
    }
    
    private void searchUsers(String token, String query) {
        // Create search URL with query parameter
        String searchUrl = "/api/users/search?q=" + query;
        
        apiClient.authenticatedGet(searchUrl, token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoading(false);
                        Toast.makeText(SearchActivity.this, "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoading(false);
                        handleSearchResponse(response.code(), responseBody);
                    }
                });
            }
        });
    }

    // Search groups on server (both public and non-public), then filter by membership
    private void searchGroupsServer(String token, String query) {
        showLoading(true);
        String endpoint = "/api/groups/search?q=" + query;
        apiClient.authenticatedGet(endpoint, token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(SearchActivity.this, "Failed to search groups: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> handleGroupSearchResponse(response.code(), responseBody));
            }
        });
    }

    private void handleGroupSearchResponse(int statusCode, String responseBody) {
        showLoading(false);
        try {
            if (statusCode == 200) {
                JSONObject json = new JSONObject(responseBody);
                if (json.optBoolean("success", false)) {
                    // Response might be {data: {groups: [...]}} or {data: {chats: [...]}}
                    JSONObject data = json.getJSONObject("data");
                    JSONArray arr = null;
                    if (data.has("groups")) arr = data.getJSONArray("groups");
                    if (arr == null && data.has("chats")) arr = data.getJSONArray("chats");
                    List<Chat> results = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject g = arr.getJSONObject(i);
                            Chat chat = Chat.fromJson(g);
                            if (!chat.isGroupChat()) continue;
                            String myId = sharedPrefsManager.getUserId();
                            boolean isMember = chat.getParticipantIds().contains(myId);
                            if (isDiscoverGroups) {
                                if (!isMember) results.add(chat);
                            } else {
                                if (isMember) results.add(chat);
                            }
                        }
                    }
                    groupResults.clear();
                    groupResults.addAll(results);
                    applyGroupFilterWithCurrentQuery();
                } else {
                    Toast.makeText(this, "Group search failed", Toast.LENGTH_SHORT).show();
                }
            } else if (statusCode == 401) {
                Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
                sharedPrefsManager.clearLoginInfo();
                finish();
            } else {
                Toast.makeText(this, "Failed to search groups", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing search results", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void handleSearchResponse(int statusCode, String responseBody) {
        try {
            // Log response for debugging
            System.out.println("Search Response Code: " + statusCode);
            System.out.println("Search Response Body: " + responseBody);
            
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200) {
                // Server returns: { "success": true, "data": { "users": [...] } }
                JSONObject data = jsonResponse.getJSONObject("data");
                JSONArray usersArray = data.getJSONArray("users");
                searchResults.clear();
                
                for (int i = 0; i < usersArray.length(); i++) {
                    JSONObject userJson = usersArray.getJSONObject(i);
                    User user = User.fromJson(userJson);
                    
                    // Don't include current user in search results
                    if (!user.getId().equals(sharedPrefsManager.getUserId())) {
                        searchResults.add(user);
                    }
                }
                
                userAdapter.notifyDataSetChanged();
                updateResultsVisibility();
                
            } else if (statusCode == 401) {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                sharedPrefsManager.clearLoginInfo();
                // Redirect to login
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                
            } else {
                String message = jsonResponse.optString("message", "Search failed");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing search results", Toast.LENGTH_SHORT).show();
        }
    }

    private void createChatWithUser(User user) {
        showLoading(true);
        
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        try {
            JSONObject chatData = new JSONObject();
            chatData.put("participantId", user.getId());
            chatData.put("type", "private"); // private chat
            
            apiClient.createChat(token, chatData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(SearchActivity.this, "Failed to create chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            handleCreateChatResponse(response.code(), responseBody, user);
                        }
                    });
                }
            });
            
        } catch (JSONException e) {
            showLoading(false);
            Toast.makeText(this, "Error creating chat request", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleCreateChatResponse(int statusCode, String responseBody, User user) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200 || statusCode == 201) {
                Toast.makeText(this, "Chat created with " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                
                // Open private chat activity
                Intent intent = new Intent(this, PrivateChatActivity.class);
                intent.putExtra("user", user.toJson().toString());
                
                // Add chat data if available
                JSONObject data = jsonResponse.getJSONObject("data");
                if (data.has("chat")) {
                    intent.putExtra("chat", data.getJSONObject("chat").toString());
                }
                
                startActivity(intent);
                finish();
                
            } else {
                String message = jsonResponse.optString("message", "Failed to create chat");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            
        } catch (JSONException e) {
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showUserProfile(User user) {
        // TODO: Implement user profile view
        Toast.makeText(this, "Profile: " + user.getDisplayName() + "\nEmail: " + user.getEmail(), Toast.LENGTH_LONG).show();
    }
    
    @SuppressLint("NotifyDataSetChanged")
    private void clearSearchResults() {
        if (isSearchingGroups) {
            groupResults.clear();
            groupAdapter.notifyDataSetChanged();
        } else {
            searchResults.clear();
            userAdapter.notifyDataSetChanged();
        }
        updateResultsVisibility();
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            tvNoResults.setVisibility(View.GONE);
            tvSearchHint.setVisibility(View.GONE);
        } else {
            updateResultsVisibility();
        }
    }
    
    @SuppressLint("SetTextI18n")
    private void updateResultsVisibility() {
        int itemCount = 0;
        RecyclerView.Adapter<?> adapter = rvSearchResults.getAdapter();
        if (adapter != null) {
            itemCount = adapter.getItemCount();
        }
        boolean hasResults = itemCount > 0;
        
        if (!hasResults) {
            String searchQuery = etSearch.getText().toString().trim();
            if (searchQuery.isEmpty()) {
                tvSearchHint.setVisibility(View.VISIBLE);
                tvNoResults.setVisibility(View.GONE);
            } else {
                tvSearchHint.setVisibility(View.GONE);
                tvNoResults.setVisibility(View.VISIBLE);
                String searchType = isSearchingGroups ? (isDiscoverGroups ? "public groups" : "your groups") : "users";
                tvNoResultsTitle.setText("No " + searchType + " found for \"" + searchQuery + "\"");
            }
            rvSearchResults.setVisibility(View.GONE);
        } else {
            tvSearchHint.setVisibility(View.GONE);
            tvNoResults.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.VISIBLE);
        }
    }

    // UserSearchAdapter.OnUserClickListener implementations
    @Override
    public void onUserClick(User user) {
        if ("add_members".equals(mode)) {
            addMemberToGroup(user);
        } else if ("forward".equals(mode)) {
            // Create/open chat like normal, but pass forward_content so BaseChatActivity can send it
            createChatWithUserForward(user);
        } else {
            createChatWithUser(user);
        }
    }

    private void createChatWithUserForward(User user) {
        showLoading(true);
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            JSONObject chatData = new JSONObject();
            chatData.put("participantId", user.getId());
            chatData.put("type", "private");

            apiClient.createChat(token, chatData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(SearchActivity.this, "Failed to create chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        showLoading(false);
                        handleCreateChatResponseForward(response.code(), responseBody, user);
                    });
                }
            });

        } catch (JSONException e) {
            showLoading(false);
            Toast.makeText(this, "Error creating chat request", Toast.LENGTH_SHORT).show();
        }
    }


    private void handleCreateChatResponseForward(int statusCode, String responseBody, User user) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            if (statusCode == 200 || statusCode == 201) {
                Intent intent = new Intent(this, PrivateChatActivity.class);
                intent.putExtra("user", user.toJson().toString());
                JSONObject data = jsonResponse.getJSONObject("data");
                if (data.has("chat")) {
                    intent.putExtra("chat", data.getJSONObject("chat").toString());
                }
                if (forwardMessageRaw != null) intent.putExtra("forward_message", forwardMessageRaw);
                else if (forwardContent != null) intent.putExtra("forward_content", forwardContent);
                startActivity(intent);
                finish();
            } else {
                String message = jsonResponse.optString("message", "Failed to create chat");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUserLongClick(User user) {
        showUserProfile(user);
    }

    @Override
    public void onAddFriendClick(User user) {
        sendFriendRequest(user);
    }

    @Override
    public void onStartChatClick(User user) {
        createChatWithUser(user);
    }

    @Override
    public void onRespondFriendRequest(User user, boolean accept) {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        // We need friendRequestId, included from search response
        String requestId = user.getFriendRequestId();
        if (requestId == null || requestId.isEmpty()) {
            Toast.makeText(this, "No friend request found", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("action", accept ? "accept" : "reject");
            apiClient.respondToFriendRequest(token, requestId, accept ? "accept" : "reject", new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(SearchActivity.this, "Failed to respond request", Toast.LENGTH_SHORT).show());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                    runOnUiThread(() -> {
                        if (response.code() == 200) {
                            Toast.makeText(SearchActivity.this, accept ? "Accepted" : "Rejected", Toast.LENGTH_SHORT).show();
                            // Refresh current results to reflect state
                            String q = etSearch.getText().toString().trim();
                            if (q.length() >= 2) performSearch(q);
                        } else {
                            Toast.makeText(SearchActivity.this, "Action failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void sendFriendRequest(User user) {
        try {
            String token = sharedPrefsManager.getToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject requestData = new JSONObject();
            requestData.put("receiverId", user.getId());

            apiClient.sendFriendRequest(token, requestData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SearchActivity.this, "Failed to send friend request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        handleFriendRequestResponse(response.code(), responseBody, user);
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating friend request", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleFriendRequestResponse(int statusCode, String responseBody, User user) {
        try {
            // Log response for debugging
            System.out.println("Send Friend Request Response Code: " + statusCode);
            System.out.println("Send Friend Request Response Body: " + responseBody);
            
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200 || statusCode == 201) {
                Toast.makeText(this, "Friend request sent to " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
            } else {
                String message = jsonResponse.optString("message", "Failed to send friend request");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void addMemberToGroup(User user) {
        if (currentChat == null) {
            Toast.makeText(this, "Error: No group selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Adding " + user.getDisplayName() + " to group...", Toast.LENGTH_SHORT).show();
        
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        try {
            JSONObject memberData = new JSONObject();
            JSONArray userIds = new JSONArray();
            userIds.put(user.getId());
            memberData.put("userIds", userIds);
            
            apiClient.addMembers(token, currentChat.getId(), memberData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(SearchActivity.this, "Failed to add member: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        handleAddMemberResponse(response.code(), responseBody, user);
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing member data", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void handleAddMemberResponse(int statusCode, String responseBody, User user) {
        try {
            if (statusCode == 200 || statusCode == 201) {
                Toast.makeText(this, user.getDisplayName() + " added to group successfully", Toast.LENGTH_SHORT).show();
                // Remove user from current results to avoid duplicate
                if (searchResults != null) {
                    searchResults.remove(user);
                    if (userAdapter != null) userAdapter.notifyDataSetChanged();
                }
                updateResultsVisibility();
            } else if (statusCode == 401) {
                Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
                sharedPrefsManager.clearLoginInfo();
                finish();
            } else {
                // Try to parse server message
                String message = "Failed to add member to group";
                try {
                    JSONObject json = new JSONObject(responseBody);
                    message = json.optString("message", message);
                } catch (Exception ignored) {}
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error processing add member response", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void searchGroups(String token, String query) {
        // When query empty, show all loaded groups
        if (query == null || query.isEmpty()) {
            groupAdapter.updateGroups(groupResults);
            showLoading(false);
            updateResultsVisibility();
            return;
        }
        // Filter groups by query (local)
        List<Chat> filteredGroups = new ArrayList<>();
        for (Chat group : groupResults) {
            if (group != null && group.getName() != null && group.getName().toLowerCase().contains(query.toLowerCase())) {
                filteredGroups.add(group);
            }
        }
        
        runOnUiThread(() -> {
            groupAdapter.updateGroups(filteredGroups);
            showLoading(false);
            updateResultsVisibility();
        });
    }
    
    private void loadUserGroups() {
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        showLoading(true);
        
        // Get user's groups from chats API
        apiClient.getChats(token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(SearchActivity.this, "Failed to load groups: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> handleGroupsResponse(response.code(), responseBody));
            }
        });
    }
    
    private void handleGroupsResponse(int statusCode, String responseBody) {
        showLoading(false);
        
        try {
            if (statusCode == 200) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    JSONObject data = jsonResponse.getJSONObject("data");
                    JSONArray chatsArray = data.getJSONArray("chats");
                    
                    groupResults.clear();
                    for (int i = 0; i < chatsArray.length(); i++) {
                        JSONObject chatJson = chatsArray.getJSONObject(i);
                        Chat chat = Chat.fromJson(chatJson);
                        
                        // Only add group chats
                        if (chat.isGroupChat()) {
                            groupResults.add(chat);
                        }
                    }
                    
                    // Apply current query filter after data loaded
                    applyGroupFilterWithCurrentQuery();
                } else {
                    String message = jsonResponse.optString("message", "Failed to load groups");
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            } else if (statusCode == 401) {
                Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
                sharedPrefsManager.clearLoginInfo();
                finish();
            } else {
                Toast.makeText(this, "Failed to load groups", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing groups response", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadDiscoverGroups() {
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        showLoading(true);
        apiClient.authenticatedGet("/api/groups/public", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(SearchActivity.this, "Failed to load discover groups: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> handleDiscoverGroupsResponse(response.code(), responseBody));
            }
        });
    }

    private void handleDiscoverGroupsResponse(int statusCode, String responseBody) {
        showLoading(false);
        try {
            if (statusCode == 200) {
                JSONObject json = new JSONObject(responseBody);
                if (json.optBoolean("success", false)) {
                    JSONArray arr = json.getJSONObject("data").optJSONArray("groups");
                    List<Chat> discover = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject g = arr.getJSONObject(i);
                            // Exclude if server says user is already member
                            boolean isMemberServer = g.optBoolean("isMember", false);
                            if (isMemberServer) continue;

                            Chat chat = Chat.fromJson(g);
                            // Carry over joinRequestStatus if provided
                            String jrs = g.optString("joinRequestStatus", "");
                            android.util.Log.d("SearchActivity", "Group " + i + " joinRequestStatus from server: " + jrs);
                            if (!jrs.isEmpty()) {
                                chat.setJoinRequestStatus(jrs);
                                android.util.Log.d("SearchActivity", "Set joinRequestStatus to: " + jrs);
                            } else {
                                chat.setJoinRequestStatus(null);
                                android.util.Log.d("SearchActivity", "Set joinRequestStatus to null (no status)");
                            }
                            discover.add(chat);
                        }
                    }
                    groupResults.clear();
                    groupResults.addAll(discover);
                    // Apply current query filter after data loaded
                    applyGroupFilterWithCurrentQuery();
                } else {
                    Toast.makeText(this, "Failed to load groups", Toast.LENGTH_SHORT).show();
                }
            } else if (statusCode == 401) {
                Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
                sharedPrefsManager.clearLoginInfo();
                finish();
            } else {
                Toast.makeText(this, "Failed to load groups", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing groups", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onGroupClick(Chat group) {
        if (isDiscoverGroups) {
            String token = sharedPrefsManager.getToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check if this is a cancel request action
            String joinStatus = group.getJoinRequestStatus();
            if (joinStatus != null && joinStatus.equals("pending")) {
                cancelJoinRequest(group, token);
                return;
            }
            
            if (group.isPublicGroup()) {
                // Join directly
                apiClient.joinGroup(token, group.getId(), new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                        runOnUiThread(() -> Toast.makeText(SearchActivity.this, "Join failed", Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                        String body = response.body().string();
                        runOnUiThread(() -> {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(body);
                                if (response.code() == 200 && json.optBoolean("success", false)) {
                                    Toast.makeText(SearchActivity.this, "Joined group", Toast.LENGTH_SHORT).show();
                                    // Move group from discover to hidden (no longer eligible)
                                    groupResults.remove(group);
                                    groupAdapter.updateGroups(groupResults);
                                    updateResultsVisibility();
                                } else {
                                    Toast.makeText(SearchActivity.this, json.optString("message", "Join failed"), Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                Toast.makeText(SearchActivity.this, "Join failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            } else {
                // Request to join
                apiClient.requestJoinGroup(token, group.getId(), new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                        runOnUiThread(() -> Toast.makeText(SearchActivity.this, "Request failed", Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                        String body = response.body().string();
                        runOnUiThread(() -> {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(body);
                                if ((response.code() == 200 || response.code() == 201) && json.optBoolean("success", false)) {
                                    Toast.makeText(SearchActivity.this, "Requested to join", Toast.LENGTH_SHORT).show();
                                    // Update the object's joinRequestStatus so adapter shows "Cancel Request"
                                    group.setJoinRequestStatus("pending");
                                    applyGroupFilterWithCurrentQuery();
                                } else {
                                    Toast.makeText(SearchActivity.this, json.optString("message", "Request failed"), Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                Toast.makeText(SearchActivity.this, "Request failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
            return;
        }
        // Open group chat (support forward mode)
        Intent intent = new Intent(this, GroupChatActivity.class);
        try {
            intent.putExtra("chat", group.toJson().toString());
            if ("forward".equals(mode)) {
                if (forwardMessageRaw != null) intent.putExtra("forward_message", forwardMessageRaw);
                else if (forwardContent != null) intent.putExtra("forward_content", forwardContent);
                finish();
            }
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening group chat", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void cancelJoinRequest(Chat group, String token) {
        Toast.makeText(this, "Cancelling join request...", Toast.LENGTH_SHORT).show();
        
        // Call API to cancel join request
        apiClient.cancelJoinRequest(token, group.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    android.util.Log.e("SearchActivity", "Cancel request failed: " + e.getMessage());
                    Toast.makeText(SearchActivity.this, "Failed to cancel request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body().string();
                android.util.Log.d("SearchActivity", "Cancel request response: " + response.code() + " - " + body);
                
                runOnUiThread(() -> {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        if (response.code() == 200 && json.optBoolean("success", false)) {
                            Toast.makeText(SearchActivity.this, "Join request cancelled", Toast.LENGTH_SHORT).show();
                            // Update the object's joinRequestStatus to remove pending status
                            group.setJoinRequestStatus(null); // Clear the status completely
                            // Refresh the adapter to update UI
                            groupAdapter.notifyDataSetChanged();
                            updateResultsVisibility();
                        } else if (response.code() == 404 || response.code() == 405) {
                            // Server doesn't support DELETE for join requests, try alternative approach
                            android.util.Log.d("SearchActivity", "Server doesn't support DELETE, trying alternative");
                            Toast.makeText(SearchActivity.this, "Server doesn't support cancel request yet", Toast.LENGTH_SHORT).show();
                            // For now, just update UI locally
                            group.setJoinRequestStatus(null);
                            groupAdapter.notifyDataSetChanged();
                            updateResultsVisibility();
                        } else {
                            String message = json.optString("message", "Failed to cancel request");
                            android.util.Log.e("SearchActivity", "Cancel request failed: " + message);
                            Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (org.json.JSONException e) {
                        android.util.Log.e("SearchActivity", "Error parsing cancel response: " + e.getMessage());
                        Toast.makeText(SearchActivity.this, "Failed to cancel request", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
