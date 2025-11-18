package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.FriendRequestAdapter;
import com.example.chatappjava.models.FriendRequest;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.DatabaseManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class FriendRequestActivity extends AppCompatActivity implements FriendRequestAdapter.OnFriendRequestActionListener {

    private RecyclerView rvFriendRequests;
    private ProgressBar progressBar;
    private LinearLayout tvNoRequests;
    private EditText etSearch;
    private View tabRequests;
    private View tabFriends;
    private View containerRequests;
    private View containerFriends;

    private ApiClient apiClient;
    private DatabaseManager databaseManager;
    private FriendRequestAdapter adapter;
    private List<FriendRequest> friendRequests;
    private List<FriendRequest> allFriendRequests;
    private RecyclerView rvMyFriends;
    private TextView tvFriendsTitle;
    private com.example.chatappjava.adapters.FriendsAdapter friendsAdapter;
    private final List<User> myFriends = new ArrayList<>();
    private final List<User> allMyFriends = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_requests);

        initializeViews();
        initializeServices();
        setupRecyclerView();
        loadFriendRequests();
        loadMyFriends();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh friend requests when returning to this activity
        loadFriendRequests();
        loadMyFriends();
    }

    private void initializeViews() {
        rvFriendRequests = findViewById(R.id.rv_friend_requests);
        progressBar = findViewById(R.id.progress_bar);
        tvNoRequests = findViewById(R.id.tv_no_requests);
        // Set up back button
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        etSearch = findViewById(R.id.et_search);
        // new views
        rvMyFriends = findViewById(R.id.rv_my_friends);
        tvFriendsTitle = findViewById(R.id.tv_friends_title);
        tabRequests = findViewById(R.id.tab_requests);
        tabFriends = findViewById(R.id.tab_friends);
        containerRequests = findViewById(R.id.container_requests);
        containerFriends = findViewById(R.id.container_friends);


    }

    private void initializeServices() {
        apiClient = new ApiClient();
        databaseManager = new DatabaseManager(this);
        friendRequests = new ArrayList<>();
        allFriendRequests = new ArrayList<>();
    }

    private void setupRecyclerView() {
        String currentUserId = databaseManager.getUserId();
        System.out.println("FriendRequestActivity: Setting up adapter with currentUserId: '" + currentUserId + "'");
        System.out.println("FriendRequestActivity: Current User ID length: " + (currentUserId != null ? currentUserId.length() : "null"));
        System.out.println("FriendRequestActivity: Current User ID isEmpty: " + (currentUserId != null ? currentUserId.isEmpty() : "null"));
        adapter = new FriendRequestAdapter(friendRequests, currentUserId, this);
        rvFriendRequests.setLayoutManager(new LinearLayoutManager(this));
        rvFriendRequests.setAdapter(adapter);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String q = s != null ? s.toString() : "";
                    filterRequests(q);
                    filterFriendsList(q);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (rvMyFriends != null) {
            rvMyFriends.setLayoutManager(new LinearLayoutManager(this));
            friendsAdapter = new com.example.chatappjava.adapters.FriendsAdapter(user -> {
                try {
                    android.content.Intent intent = new android.content.Intent(FriendRequestActivity.this, com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                    intent.putExtra("user", user.toJson().toString());
                    startActivity(intent);
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            });
            rvMyFriends.setAdapter(friendsAdapter);
        }

        if (tabRequests != null && tabFriends != null && containerRequests != null && containerFriends != null) {
            tabRequests.setOnClickListener(v -> showRequestsTab());
            tabFriends.setOnClickListener(v -> showFriendsTab());
            // default: show requests first
            showRequestsTab();
        }
    }

    private void showRequestsTab() {
        containerRequests.setVisibility(View.VISIBLE);
        containerFriends.setVisibility(View.GONE);
        if (tabRequests != null) tabRequests.setSelected(true);
        if (tabFriends != null) tabFriends.setSelected(false);
    }

    private void showFriendsTab() {
        containerRequests.setVisibility(View.GONE);
        containerFriends.setVisibility(View.VISIBLE);
        if (tabRequests != null) tabRequests.setSelected(false);
        if (tabFriends != null) tabFriends.setSelected(true);
    }

    private void loadFriendRequests() {
        showLoading(true);
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiClient.getFriendRequests(token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(FriendRequestActivity.this, "Failed to load friend requests: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    showLoading(false);
                    handleFriendRequestsResponse(response.code(), responseBody);
                });
            }
        });
    }

    private void loadMyFriends() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty() || rvMyFriends == null) return;
        apiClient.authenticatedGet("/api/users/friends", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // no-op
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                try {
                    JSONObject json = new JSONObject(responseBody);
                    if (response.isSuccessful()) {
                        JSONObject data = json.getJSONObject("data");
                        JSONArray arr = data.getJSONArray("friends");
                        List<User> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject u = arr.getJSONObject(i);
                            list.add(User.fromJson(u));
                        }
                        runOnUiThread(() -> {
                            myFriends.clear();
                            myFriends.addAll(list);
                            allMyFriends.clear();
                            allMyFriends.addAll(list);
                            if (friendsAdapter != null) friendsAdapter.setItems(myFriends);
                            if (tvFriendsTitle != null) tvFriendsTitle.setText("Friends (" + myFriends.size() + ")");
                        });
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void handleFriendRequestsResponse(int statusCode, String responseBody) {
        try {
            // Log response for debugging
            System.out.println("Friend Requests Response Code: " + statusCode);
            System.out.println("Friend Requests Response Body: " + responseBody);

            JSONObject jsonResponse = new JSONObject(responseBody);

            if (statusCode == 200) {
                JSONObject data = jsonResponse.getJSONObject("data");
                JSONArray requestsArray = data.getJSONArray("requests");

                friendRequests.clear();
                allFriendRequests.clear();
                System.out.println("Found " + requestsArray.length() + " friend requests");

                for (int i = 0; i < requestsArray.length(); i++) {
                    JSONObject requestJson = requestsArray.getJSONObject(i);
                    System.out.println("Processing request JSON: " + requestJson.toString());

                    try {
                        FriendRequest request = FriendRequest.fromJson(requestJson);
                        friendRequests.add(request);
                        allFriendRequests.add(request);
                        System.out.println("Added request: " + request.getStatus() + " from " +
                            (request.getSender() != null ? request.getSender().getDisplayName() : "Unknown"));
                    } catch (Exception e) {
                        System.out.println("Error parsing request at index " + i + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                System.out.println("Total friend requests in list: " + friendRequests.size());
                adapter.updateRequests(friendRequests);
                updateUI();

            } else if (statusCode == 401) {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                databaseManager.clearLoginInfo();
                finish();
            } else {
                String message = jsonResponse.optString("message", "Failed to load friend requests");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing friend requests", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterRequests(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        friendRequests.clear();
        if (q.isEmpty()) {
            friendRequests.addAll(allFriendRequests);
        } else {
            for (FriendRequest r : allFriendRequests) {
                User sender = r.getSender();
                User receiver = r.getReceiver();
                String senderName = sender != null && sender.getDisplayName() != null ? sender.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
                String senderUsername = sender != null && sender.getUsername() != null ? sender.getUsername().toLowerCase(java.util.Locale.ROOT) : "";
                String receiverName = receiver != null && receiver.getDisplayName() != null ? receiver.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
                String receiverUsername = receiver != null && receiver.getUsername() != null ? receiver.getUsername().toLowerCase(java.util.Locale.ROOT) : "";
                if (senderName.contains(q) || senderUsername.contains(q) || receiverName.contains(q) || receiverUsername.contains(q)) {
                    friendRequests.add(r);
                }
            }
        }
        adapter.updateRequests(friendRequests);
        updateUI();
    }

    private void updateUI() {
        boolean hasRequests = !friendRequests.isEmpty();
        if (tvNoRequests != null) tvNoRequests.setVisibility(hasRequests ? View.GONE : View.VISIBLE);
        if (rvFriendRequests != null) rvFriendRequests.setVisibility(hasRequests ? View.VISIBLE : View.GONE);
        // leave tab visibility managed by tab handlers
    }

    private void filterFriendsList(String query) {
        if (friendsAdapter == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<User> filtered = new ArrayList<>();
        if (q.isEmpty()) {
            filtered.addAll(allMyFriends);
        } else {
            for (User u : allMyFriends) {
                String displayName = u.getDisplayName() != null ? u.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
                String username = u.getUsername() != null ? u.getUsername().toLowerCase(java.util.Locale.ROOT) : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase(java.util.Locale.ROOT) : "";
                String phone = u.getPhoneNumber() != null ? u.getPhoneNumber().toLowerCase(java.util.Locale.ROOT) : "";
                if (displayName.contains(q) || username.contains(q) || email.contains(q) || phone.contains(q)) {
                    filtered.add(u);
                }
            }
        }
        friendsAdapter.setItems(filtered);
        if (tvFriendsTitle != null) tvFriendsTitle.setText("Friends (" + filtered.size() + ")");
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // FriendRequestAdapter.OnFriendRequestActionListener implementations
    @Override
    public void onAcceptRequest(FriendRequest request) {
        respondToFriendRequest(request, "accept");
    }

    @Override
    public void onRejectRequest(FriendRequest request) {
        respondToFriendRequest(request, "reject");
    }

    @Override
    public void onCancelRequest(FriendRequest request) {
        cancelFriendRequest(request);
    }

    @Override
    public void onUserClick(User user) {
        // TODO: Open user profile
        Toast.makeText(this, "User profile: " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
    }

    private void respondToFriendRequest(FriendRequest request, String action) {
        showLoading(true);
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.respondToFriendRequest(token, request.getId(), action, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(FriendRequestActivity.this, "Failed to " + action + " request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    showLoading(false);
                    handleResponseToRequest(response.code(), responseBody, request, action);
                });
            }
        });
    }

    private void handleResponseToRequest(int statusCode, String responseBody, FriendRequest request, String action) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200) {
                String message = action.equals("accept") ? "Friend request accepted" : "Friend request rejected";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                
                // If accepted, create a new chat
                if (action.equals("accept")) {
                    createChatAfterAccept(request);
                }
                
                // Remove the request from the list
                adapter.removeRequest(request);
                updateUI();
                
                // Notify HomeActivity to refresh friend request count
                setResult(RESULT_OK);
                
            } else {
                String message = jsonResponse.optString("message", "Failed to " + action + " request");
                
                // If request was already responded to, refresh the list instead of showing error
                if (message.contains("already been responded to") || message.contains("already exists")) {
                    Toast.makeText(this, "Request was already processed, refreshing...", Toast.LENGTH_SHORT).show();
                    loadFriendRequests(); // Refresh the list
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }

    private void createChatAfterAccept(FriendRequest request) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Get the other user (sender) to create chat with
            User otherUser = request.getSender();
            if (otherUser == null) {
                Toast.makeText(this, "Error: Cannot find user info", Toast.LENGTH_SHORT).show();
                return;
            }
            
            JSONObject chatData = new JSONObject();
            chatData.put("participantId", otherUser.getId());
            
            System.out.println("Creating chat with user: " + otherUser.getDisplayName() + " (ID: " + otherUser.getId() + ")");
            System.out.println("Chat data: " + chatData);
            System.out.println("API endpoint: /api/chats/private");
            
            apiClient.createChat(token, chatData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        System.out.println("Failed to create chat: " + e.getMessage());
                        Toast.makeText(FriendRequestActivity.this, "Friend added but failed to create chat", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> handleCreateChatResponse(response.code(), responseBody, otherUser));
                }
            });
            
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating chat data", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleCreateChatResponse(int statusCode, String responseBody, User otherUser) {
        try {
            System.out.println("=== CREATE CHAT RESPONSE ===");
            System.out.println("Create Chat Response Code: " + statusCode);
            System.out.println("Create Chat Response Body: " + responseBody);
            System.out.println("Other User: " + otherUser.getDisplayName() + " (ID: " + otherUser.getId() + ")");
            
            if (statusCode == 200 || statusCode == 201) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                if (jsonResponse.optBoolean("success", false)) {
                    System.out.println("✅ Chat created successfully with " + otherUser.getDisplayName());
                    Toast.makeText(this, "Chat created with " + otherUser.getDisplayName(), Toast.LENGTH_SHORT).show();
                } else {
                    System.out.println("❌ Chat creation failed: " + jsonResponse.optString("message", "Unknown error"));
                    Toast.makeText(this, "Failed to create chat: " + jsonResponse.optString("message", "Unknown error"), Toast.LENGTH_SHORT).show();
                }
            } else {
                System.out.println("❌ Chat creation failed with status: " + statusCode);
                Toast.makeText(this, "Failed to create chat (Status: " + statusCode + ")", Toast.LENGTH_SHORT).show();
            }
            System.out.println("=== END CREATE CHAT RESPONSE ===");
        } catch (JSONException e) {
            e.printStackTrace();
            System.out.println("❌ Error parsing chat creation response: " + e.getMessage());
            Toast.makeText(this, "Error parsing chat creation response", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelFriendRequest(FriendRequest request) {
        showLoading(true);
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.cancelFriendRequest(token, request.getId(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(FriendRequestActivity.this, "Failed to cancel request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    showLoading(false);
                    handleCancelRequestResponse(response.code(), responseBody, request);
                });
            }
        });
    }

    private void handleCancelRequestResponse(int statusCode, String responseBody, FriendRequest request) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200) {
                Toast.makeText(this, "Friend request cancelled", Toast.LENGTH_SHORT).show();
                
                // Remove the request from the list
                adapter.removeRequest(request);
                updateUI();
                
                // Notify HomeActivity to refresh friend request count
                setResult(RESULT_OK);
                
            } else {
                String message = jsonResponse.optString("message", "Failed to cancel request");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }
}
