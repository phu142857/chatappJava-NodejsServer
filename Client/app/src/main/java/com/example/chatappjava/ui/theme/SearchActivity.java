package com.example.chatappjava.ui.theme;

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
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.FriendRequest;
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

public class SearchActivity extends AppCompatActivity implements UserSearchAdapter.OnUserClickListener {
    
    private EditText etSearch;
    private ImageView ivBack, ivClear;
    private RecyclerView rvSearchResults;
    private ProgressBar progressBar;
    private LinearLayout tvNoResults, tvSearchHint;
    private TextView tvNoResultsTitle;
    
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefsManager;
    private UserSearchAdapter userAdapter;
    private List<User> searchResults;
    
    private String mode; // "add_members" or null for normal search
    private Chat currentChat; // For add_members mode
    private List<String> currentGroupMemberIds; // Track current group members
    
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
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
        sharedPrefsManager = new SharedPreferencesManager(this);
        searchResults = new ArrayList<>();
        currentGroupMemberIds = new ArrayList<>();
        
        // Get mode and chat data from intent
        Intent intent = getIntent();
        mode = intent.getStringExtra("mode");
        if ("add_members".equals(mode) && intent.hasExtra("chat")) {
            try {
                String chatJson = intent.getStringExtra("chat");
                JSONObject chatJsonObj = new JSONObject(chatJson);
                currentChat = Chat.fromJson(chatJsonObj);
                // Load current group members to avoid showing add button for existing members
                loadCurrentGroupMembers();
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading chat data", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
    }
    
    private void loadCurrentGroupMembers() {
        if (currentChat == null) return;
        
        String token = sharedPrefsManager.getToken();
        if (token == null) return;
        
        // Get group members from the chat participant IDs
        if (currentChat.getParticipantIds() != null) {
            currentGroupMemberIds.clear();
            currentGroupMemberIds.addAll(currentChat.getParticipantIds());
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
                
                if (s.length() == 0) {
                    clearSearchResults();
                    return;
                }
                
                // Create new search with delay
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        performSearch(s.toString().trim());
                    }
                };
                
                etSearch.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void setupRecyclerView() {
        userAdapter = new UserSearchAdapter(this, searchResults, this, mode, currentGroupMemberIds);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(userAdapter);
    }
    
    private void setupClickListeners() {
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        ivClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etSearch.setText("");
                etSearch.requestFocus();
            }
        });
        
        // Focus on search field when activity starts
        etSearch.requestFocus();
    }
    
    private void performSearch(String query) {
        if (query.length() < 2) {
            clearSearchResults();
            return;
        }
        
        showLoading(true);
        
        String token = sharedPrefsManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
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
    
    private void clearSearchResults() {
        searchResults.clear();
        userAdapter.notifyDataSetChanged();
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
    
    private void updateResultsVisibility() {
        if (searchResults.isEmpty()) {
            String searchQuery = etSearch.getText().toString().trim();
            if (searchQuery.isEmpty()) {
                tvSearchHint.setVisibility(View.VISIBLE);
                tvNoResults.setVisibility(View.GONE);
            } else {
                tvSearchHint.setVisibility(View.GONE);
                tvNoResults.setVisibility(View.VISIBLE);
                tvNoResultsTitle.setText("No users found for \"" + searchQuery + "\"");
            }
            rvSearchResults.setVisibility(View.GONE);
        } else {
            tvSearchHint.setVisibility(View.GONE);
            tvNoResults.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    // UserSearchAdapter.OnUserClickListener implementations
    @Override
    public void onUserClick(User user) {
        if ("add_members".equals(mode)) {
            addMemberToGroup(user);
        } else {
            createChatWithUser(user);
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
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
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
    
    private void handleAddMemberResponse(int statusCode, String responseBody, User user) {
        try {
            // Log response for debugging
            System.out.println("Add Member Response Code: " + statusCode);
            System.out.println("Add Member Response Body: " + responseBody);
            
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200 || statusCode == 201) {
                Toast.makeText(this, user.getDisplayName() + " added to group successfully", Toast.LENGTH_SHORT).show();
                // Remove user from search results to prevent duplicate additions
                searchResults.remove(user);
                userAdapter.notifyDataSetChanged();
                updateResultsVisibility();
            } else {
                String message = jsonResponse.optString("message", "Failed to add member to group");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }
}
