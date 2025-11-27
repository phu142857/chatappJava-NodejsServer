package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.adapters.FriendsAdapter;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;

import org.json.JSONException;
import org.json.JSONObject;

import de.hdodenhof.circleimageview.CircleImageView;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.List;

public class ProfileViewActivity extends AppCompatActivity {
    
    private CircleImageView civAvatar;
    private TextView tvUsername;
    private TextView tvFirstName;
    private TextView tvLastName;
    private TextView tvPhoneNumber;
    private TextView tvBio;
    private ImageView ivBack;
    private ImageView ivMore;
    private RecyclerView rvFriends;
    private RecyclerView rvPosts;
    private TextView tvNoFriends;
    private TextView tvNoPosts;
    private FriendsAdapter friendsAdapter;
    private com.example.chatappjava.adapters.PostAdapter postAdapter;
    private java.util.List<com.example.chatappjava.models.Post> postsList;
    
    private User otherUser;
    private AvatarManager avatarManager;
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private AlertDialog currentDialog;
    private boolean isBlockedInSession; // local flag to reflect block state in this session
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_view);
        
        initViews();
        initData();
        setupClickListeners();
        loadUserData();
    }
    
    private void initViews() {
        civAvatar = findViewById(R.id.civ_avatar);
        tvUsername = findViewById(R.id.tv_username);
        tvFirstName = findViewById(R.id.tv_first_name);
        tvLastName = findViewById(R.id.tv_last_name);
        tvPhoneNumber = findViewById(R.id.tv_phone_number);
        tvBio = findViewById(R.id.tv_bio);
        ivBack = findViewById(R.id.iv_back);
        ivMore = findViewById(R.id.iv_more);
        rvFriends = findViewById(R.id.rv_friends);
        tvNoFriends = findViewById(R.id.tv_no_friends);
        rvPosts = findViewById(R.id.rv_posts);
        tvNoPosts = findViewById(R.id.tv_no_posts);
        
        postsList = new java.util.ArrayList<>();

        if (rvFriends != null) {
            rvFriends.setLayoutManager(new LinearLayoutManager(this));
            friendsAdapter = new FriendsAdapter(user -> {
                try {
                    Intent intent = new Intent(ProfileViewActivity.this, ProfileViewActivity.class);
                    intent.putExtra("user", user.toJson().toString());
                    startActivity(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
            rvFriends.setAdapter(friendsAdapter);
        }
        
        if (rvPosts != null) {
            rvPosts.setLayoutManager(new LinearLayoutManager(this));
            postAdapter = new com.example.chatappjava.adapters.PostAdapter(this, postsList, new com.example.chatappjava.adapters.PostAdapter.OnPostClickListener() {
                @Override
                public void onPostClick(com.example.chatappjava.models.Post post) {
                    Intent intent = new Intent(ProfileViewActivity.this, PostDetailActivity.class);
                    try {
                        intent.putExtra("post", post.toJson().toString());
                        startActivity(intent);
                    } catch (JSONException e) {
                        Toast.makeText(ProfileViewActivity.this, "Error opening post", Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onLikeClick(com.example.chatappjava.models.Post post, int position) {
                    String token = databaseManager.getToken();
                    if (token == null || token.isEmpty()) {
                        Toast.makeText(ProfileViewActivity.this, "Please login again", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    apiClient.toggleLikePost(token, post.getId(), new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            runOnUiThread(() -> Toast.makeText(ProfileViewActivity.this, "Failed to like post", Toast.LENGTH_SHORT).show());
                        }
                        
                        @Override
                        public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            runOnUiThread(() -> {
                                if (response.isSuccessful()) {
                                    post.setLiked(!post.isLiked());
                                    if (post.isLiked()) {
                                        post.setLikesCount(post.getLikesCount() + 1);
                                    } else {
                                        post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
                                    }
                                    postAdapter.updatePost(position, post);
                                }
                            });
                        }
                    });
                }
                
                @Override
                public void onCommentClick(com.example.chatappjava.models.Post post) {
                    Intent intent = new Intent(ProfileViewActivity.this, PostDetailActivity.class);
                    try {
                        intent.putExtra("post", post.toJson().toString());
                        startActivity(intent);
                    } catch (JSONException e) {
                        Toast.makeText(ProfileViewActivity.this, "Error opening post", Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onShareClick(com.example.chatappjava.models.Post post) {
                    Toast.makeText(ProfileViewActivity.this, "Share feature", Toast.LENGTH_SHORT).show();
                }
                
                @Override
                public void onPostMenuClick(com.example.chatappjava.models.Post post) {
                    Toast.makeText(ProfileViewActivity.this, "Post options", Toast.LENGTH_SHORT).show();
                }
                
                @Override
                public void onAuthorClick(com.example.chatappjava.models.Post post) {
                    // Open author profile
                    try {
                        Intent intent = new Intent(ProfileViewActivity.this, ProfileViewActivity.class);
                        intent.putExtra("user", post.getAuthorId());
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(ProfileViewActivity.this, "Error opening profile", Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onMediaClick(com.example.chatappjava.models.Post post, int mediaIndex) {
                    // Navigate to post detail when media is clicked
                    if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                        String mediaType = post.getMediaType();
                        if ("image".equals(mediaType) || "gallery".equals(mediaType)) {
                            // Show image viewer for images
                            if (post.getMediaUrls() != null && mediaIndex < post.getMediaUrls().size()) {
                                List<String> imageUrls = post.getMediaUrls();
                                String imageUrl = imageUrls.get(mediaIndex);
                                if (imageUrl != null && !imageUrl.isEmpty()) {
                                    // Construct full URLs for all images
                                    List<String> fullImageUrls = new ArrayList<>();
                                    for (String url : imageUrls) {
                                        if (url != null && !url.isEmpty()) {
                                            if (!url.startsWith("http")) {
                                                url = com.example.chatappjava.config.ServerConfig.getBaseUrl() + 
                                                      (url.startsWith("/") ? url : "/" + url);
                                            }
                                            fullImageUrls.add(url);
                                        }
                                    }
                                    
                                    // Open PostDetailActivity to view images
                                    Intent intent = new Intent(ProfileViewActivity.this, PostDetailActivity.class);
                                    try {
                                        intent.putExtra("post", post.toJson().toString());
                                        startActivity(intent);
                                    } catch (JSONException e) {
                                        Toast.makeText(ProfileViewActivity.this, "Error opening post", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        }
                    }
                }
                
                @Override
                public void onTaggedUsersClick(com.example.chatappjava.models.Post post) {
                    Toast.makeText(ProfileViewActivity.this, "Tagged users", Toast.LENGTH_SHORT).show();
                }
            });
            rvPosts.setAdapter(postAdapter);
        }
    }
    
    private void initData() {
        avatarManager = AvatarManager.getInstance(this);
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        
        Intent intent = getIntent();
        if (intent.hasExtra("user")) {
            try {
                String userJson = intent.getStringExtra("user");
                JSONObject userJsonObj = new JSONObject(userJson);
                otherUser = User.fromJson(userJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else if (intent.hasExtra("username")) {
            // Fallback: fetch by username from server
            String username = intent.getStringExtra("username");
            fetchUserByUsername(username);
        } else {
            Toast.makeText(this, "No user data provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }
    
    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
        
        civAvatar.setOnClickListener(v -> showAvatarZoom());
        
        ivMore.setOnClickListener(v -> showMoreOptions());
    }

    private void performBlockUser() {
        if (otherUser == null) {
            Toast.makeText(this, "No user to block", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        String action = isBlockedInSession ? "unblock" : "block";
        apiClient.blockUser(token, otherUser.getId(), action, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(ProfileViewActivity.this, "Network error", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        isBlockedInSession = !isBlockedInSession;
                        Toast.makeText(ProfileViewActivity.this, (isBlockedInSession ? "Blocked" : "Unblocked") + " successfully", Toast.LENGTH_SHORT).show();
                        // Optionally finish or refresh UI
                    } else {
                        String message = "Action failed";
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            message = json.optString("message", message);
                        } catch (Exception ignored) {}
                        Toast.makeText(ProfileViewActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void loadUserData() {
        if (otherUser == null) return;
        
        // Load avatar - handle URL like ProfileActivity
        if (otherUser.getAvatar() != null && !otherUser.getAvatar().isEmpty()) {
            String avatarUrl = otherUser.getAvatar();
            android.util.Log.d("ProfileViewActivity", "Original avatar URL: " + avatarUrl);
            
            // If it's a relative URL, prepend the server base URL
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                android.util.Log.d("ProfileViewActivity", "Constructed full URL: " + avatarUrl);
            }
            
            try {
                // Try AvatarManager first
                avatarManager.loadAvatar(avatarUrl, civAvatar, R.drawable.ic_profile_placeholder);
                
                // Backup: Also try Picasso directly (like ProfileActivity)
                com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(civAvatar);
                        
            } catch (Exception e) {
                android.util.Log.e("ProfileViewActivity", "Error loading avatar: " + e.getMessage());
                // Fallback to direct Picasso load
                try {
                    com.squareup.picasso.Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(civAvatar);
                } catch (Exception e2) {
                    android.util.Log.e("ProfileViewActivity", "Picasso also failed: " + e2.getMessage());
                    civAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        } else {
            civAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // Set user information
        tvUsername.setText(otherUser.getUsername() != null ? otherUser.getUsername() : "N/A");
        tvFirstName.setText(otherUser.getFirstName() != null ? otherUser.getFirstName() : "N/A");
        tvLastName.setText(otherUser.getLastName() != null ? otherUser.getLastName() : "N/A");
        tvPhoneNumber.setText(otherUser.getPhoneNumber() != null ? otherUser.getPhoneNumber() : "N/A");
        tvBio.setText(otherUser.getBio() != null ? otherUser.getBio() : "No bio available");

        // Load friends for this user
        loadFriends();
        
        // Load posts for this user
        loadPosts();
    }
    
    private void loadPosts() {
        if (rvPosts == null || otherUser == null) return;
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        apiClient.getUserPosts(token, otherUser.getId(), 1, 20, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    if (tvNoPosts != null) {
                        tvNoPosts.setVisibility(View.VISIBLE);
                        tvNoPosts.setText("Failed to load posts");
                    }
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    android.util.Log.d("ProfileViewActivity", "Get user posts response: " + response.code() + " - " + body);
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            if (tvNoPosts != null) {
                                tvNoPosts.setVisibility(View.VISIBLE);
                                tvNoPosts.setText("No posts to show");
                            }
                        });
                        return;
                    }
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    if (json.optBoolean("success", false)) {
                        org.json.JSONObject data = json.getJSONObject("data");
                        org.json.JSONArray postsArray = data.optJSONArray("posts");
                        if (postsArray == null) {
                            android.util.Log.e("ProfileViewActivity", "Posts array is null in response");
                            runOnUiThread(() -> {
                                if (tvNoPosts != null) {
                                    tvNoPosts.setVisibility(View.VISIBLE);
                                    tvNoPosts.setText("No posts to show");
                                }
                            });
                            return;
                        }
                        java.util.List<com.example.chatappjava.models.Post> list = new java.util.ArrayList<>();
                        for (int i = 0; i < postsArray.length(); i++) {
                            try {
                                org.json.JSONObject postJson = postsArray.getJSONObject(i);
                                com.example.chatappjava.models.Post post = com.example.chatappjava.models.Post.fromJson(postJson);
                                list.add(post);
                            } catch (Exception e) {
                                android.util.Log.e("ProfileViewActivity", "Error parsing post " + i + ": " + e.getMessage());
                            }
                        }
                        android.util.Log.d("ProfileViewActivity", "Loaded " + list.size() + " posts");
                        runOnUiThread(() -> {
                            postsList.clear();
                            postsList.addAll(list);
                            if (postAdapter != null) postAdapter.setPosts(list);
                            if (tvNoPosts != null) tvNoPosts.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                        });
                    } else {
                        android.util.Log.e("ProfileViewActivity", "Response success is false: " + json.optString("message", "Unknown error"));
                        runOnUiThread(() -> {
                            if (tvNoPosts != null) {
                                tvNoPosts.setVisibility(View.VISIBLE);
                                tvNoPosts.setText("No posts to show");
                            }
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.e("ProfileViewActivity", "Error loading posts: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        if (tvNoPosts != null) {
                            tvNoPosts.setVisibility(View.VISIBLE);
                            tvNoPosts.setText("Failed to parse posts: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void loadFriends() {
        if (rvFriends == null || otherUser == null) return;
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;

        apiClient.getUserFriendsById(token, otherUser.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    if (tvNoFriends != null) {
                        tvNoFriends.setVisibility(View.VISIBLE);
                        tvNoFriends.setText("Failed to load friends");
                    }
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            if (tvNoFriends != null) {
                                tvNoFriends.setVisibility(View.VISIBLE);
                                tvNoFriends.setText("No friends to show");
                            }
                        });
                        return;
                    }
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    org.json.JSONObject data = json.getJSONObject("data");
                    org.json.JSONArray arr = data.getJSONArray("friends");
                    java.util.List<com.example.chatappjava.models.User> list = new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject u = arr.getJSONObject(i);
                        com.example.chatappjava.models.User friend = com.example.chatappjava.models.User.fromJson(u);
                        list.add(friend);
                    }
                    runOnUiThread(() -> {
                        if (friendsAdapter != null) friendsAdapter.setItems(list);
                        if (tvNoFriends != null) tvNoFriends.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (tvNoFriends != null) {
                            tvNoFriends.setVisibility(View.VISIBLE);
                            tvNoFriends.setText("Failed to parse friends");
                        }
                    });
                }
            }
        });
    }
    
    private void showAvatarZoom() {
        if (otherUser == null || otherUser.getAvatar() == null || otherUser.getAvatar().isEmpty()) {
            Toast.makeText(this, "No avatar to display", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_image_zoom, null);
        
        PhotoView zoomImage = dialogView.findViewById(R.id.iv_zoom_image);
        ImageView ivClose = dialogView.findViewById(R.id.iv_close);
        
        // Load the same avatar in zoomed view - handle URL like ProfileActivity
        String avatarUrl = otherUser.getAvatar();
        // If it's a relative URL, prepend the server base URL
        if (!avatarUrl.startsWith("http")) {
            avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                       ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
        }
        
        try {
            // Prefer AvatarManager cache
            avatarManager.loadAvatar(avatarUrl, zoomImage, R.drawable.ic_profile_placeholder);
        } catch (Exception e) {
            android.util.Log.e("ProfileViewActivity", "AvatarManager load failed: " + e.getMessage());
        }
        // Ensure Picasso loads as well (fallback)
        try {
            com.squareup.picasso.Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(zoomImage);
        } catch (Exception e2) {
            android.util.Log.e("ProfileViewActivity", "Picasso failed: " + e2.getMessage());
            zoomImage.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        builder.setView(dialogView);
        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            android.view.Window w = currentDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        if (ivClose != null) {
            ivClose.setOnClickListener(v -> {
                if (currentDialog != null) currentDialog.dismiss();
            });
        }
        currentDialog.show();
    }
    
    private void showMoreOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_profile_options, null);
        
        // Toggle Add Friend / Unfriend based on friendship status (resolved dynamically)
        View addFriendOption = dialogView.findViewById(R.id.option_add_friend);
        View unfriendOption = dialogView.findViewById(R.id.option_unfriend);
        if (addFriendOption != null) addFriendOption.setVisibility(View.GONE);
        if (unfriendOption != null) unfriendOption.setVisibility(View.GONE);
        resolveFriendshipAndToggleOptions(addFriendOption, unfriendOption);

        // Add Friend handler
        if (addFriendOption != null) {
            addFriendOption.setOnClickListener(v -> {
                sendFriendRequest();
                if (currentDialog != null) currentDialog.dismiss();
            });
        }

        // Unfriend handler (with confirmation)
        if (unfriendOption != null) {
            unfriendOption.setOnClickListener(v -> {
                if (otherUser == null) return;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Unfriend")
                        .setMessage("Are you sure you want to unfriend " + otherUser.getDisplayName() + "?")
                        .setPositiveButton("Unfriend", (d, w) -> {
                            performUnfriend();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // Report handler -> open input dialog up to 100 words
        View reportOption = dialogView.findViewById(R.id.option_report);
        if (reportOption != null) {
            reportOption.setOnClickListener(v -> {
                if (currentDialog != null) currentDialog.dismiss();
                showReportDialog();
            });
        }

        // Set click listeners for each option
        dialogView.findViewById(R.id.option_send_message).setOnClickListener(v -> {
            navigateToPrivateChat();
            if (currentDialog != null) currentDialog.dismiss();
        });

        dialogView.findViewById(R.id.option_block_user).setOnClickListener(v -> {
            if (currentDialog != null) currentDialog.dismiss();
            performBlockUser();
        });
        
        builder.setView(dialogView);
        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            android.view.Window w = currentDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        currentDialog.show();
    }

    private void showReportDialog() {
        if (otherUser == null) {
            Toast.makeText(this, "No user to report", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Report user");

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Describe the issue (max 100 words)");
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        input.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String content = input.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "Please enter report content", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isWithinWordLimit(content, 100)) {
                Toast.makeText(this, "Report must be at most 100 words", Toast.LENGTH_SHORT).show();
                return;
            }
            submitReport(content);
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dlg = builder.create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
        }
        dlg.show();
    }

    private boolean isWithinWordLimit(String text, int maxWords) {
        String[] words = text.trim().split("\\s+");
        return words.length <= maxWords;
    }

    private void submitReport(String content) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.reportUser(token, otherUser.getId(), content, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(ProfileViewActivity.this, "Failed to send report", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(ProfileViewActivity.this, "Report sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileViewActivity.this, "Report failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void resolveFriendshipAndToggleOptions(View addFriendOption, View unfriendOption) {
        boolean localIsFriend = otherUser != null && otherUser.isFriend();
        if (localIsFriend) {
            if (addFriendOption != null) addFriendOption.setVisibility(View.GONE);
            if (unfriendOption != null) unfriendOption.setVisibility(View.VISIBLE);
            return;
        }

        String token = databaseManager.getToken();
        if (token == null || token.isEmpty() || otherUser == null) {
            if (addFriendOption != null) addFriendOption.setVisibility(View.VISIBLE);
            if (unfriendOption != null) unfriendOption.setVisibility(View.GONE);
            return;
        }

        apiClient.authenticatedGet("/api/users/friends", token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    if (addFriendOption != null) addFriendOption.setVisibility(View.VISIBLE);
                    if (unfriendOption != null) unfriendOption.setVisibility(View.GONE);
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
                            if (otherUser.getId().equals(fid)) { found = true; break; }
                        }
                    }
                } catch (Exception ignored) {}

                final boolean isFriendNow = found;
                runOnUiThread(() -> {
                    if (isFriendNow) {
                        if (addFriendOption != null) addFriendOption.setVisibility(View.GONE);
                        if (unfriendOption != null) unfriendOption.setVisibility(View.VISIBLE);
                        if (otherUser != null) otherUser.setFriend(true);
                    } else {
                        if (addFriendOption != null) addFriendOption.setVisibility(View.VISIBLE);
                        if (unfriendOption != null) unfriendOption.setVisibility(View.GONE);
                        // Not friends: check if there is a pending friend request that I sent
                        checkPendingFriendRequest(addFriendOption);
                    }
                });
            }
        });
    }

    private void checkPendingFriendRequest(View addFriendOption) {
        if (addFriendOption == null) return;
        String token = databaseManager.getToken();
        String currentUserId = databaseManager.getUserId();
        if (token == null || token.isEmpty() || currentUserId == null || currentUserId.isEmpty() || otherUser == null) {
            return;
        }
        apiClient.getFriendRequests(token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) { /* no-op */ }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "";
                boolean hasPending = false;
                try {
                    if (response.isSuccessful()) {
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        org.json.JSONObject data = json.has("data") ? json.getJSONObject("data") : json;
                        // Server may return arrays: sentRequests, receivedRequests, or a unified list
                        if (data.has("sentRequests")) {
                            org.json.JSONArray arr = data.getJSONArray("sentRequests");
                            for (int i = 0; i < arr.length(); i++) {
                                org.json.JSONObject fr = arr.getJSONObject(i);
                                String receiverId = fr.optString("receiver", fr.optString("receiverId", ""));
                                String status = fr.optString("status", fr.optString("state", ""));
                                if (otherUser.getId().equals(receiverId) && "pending".equalsIgnoreCase(status)) { hasPending = true; break; }
                            }
                        } else if (data.has("requests")) {
                            org.json.JSONArray arr = data.getJSONArray("requests");
                            for (int i = 0; i < arr.length(); i++) {
                                org.json.JSONObject fr = arr.getJSONObject(i);
                                String senderId = fr.optString("sender", fr.optString("senderId", ""));
                                String receiverId = fr.optString("receiver", fr.optString("receiverId", ""));
                                String status = fr.optString("status", fr.optString("state", ""));
                                if (currentUserId.equals(senderId) && otherUser.getId().equals(receiverId) && "pending".equalsIgnoreCase(status)) { hasPending = true; break; }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                final boolean pendingNow = hasPending;
                runOnUiThread(() -> {
                    if (pendingNow) {
                        setAddFriendPending(addFriendOption);
                    }
                });
            }
        });
    }

    private void setAddFriendPending(View addFriendOption) {
        try {
            // Structure: CardView > LinearLayout(option_add_friend) with children [ImageView, TextView]
            if (addFriendOption instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) addFriendOption;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof android.widget.TextView) {
                        android.widget.TextView tv = (android.widget.TextView) child;
                        tv.setText("Pending");
                        break;
                    }
                }
            }
            addFriendOption.setEnabled(false);
            addFriendOption.setAlpha(0.6f);
        } catch (Exception ignored) {}
    }

    private void sendFriendRequest() {
        try {
            String token = databaseManager.getToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
                return;
            }
            if (otherUser == null) return;

            org.json.JSONObject requestData = new org.json.JSONObject();
            requestData.put("receiverId", otherUser.getId());

            apiClient.sendFriendRequest(token, requestData, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileViewActivity.this, "Failed to send friend request", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(ProfileViewActivity.this, "Friend request sent", Toast.LENGTH_SHORT).show();
                        } else {
                            String message = "Unable to send request";
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(body);
                                message = json.optString("message", message);
                            } catch (Exception ignored) {}
                            Toast.makeText(ProfileViewActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void performUnfriend() {
        if (otherUser == null) return;
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.unfriendUser(token, otherUser.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(ProfileViewActivity.this, "Failed to unfriend", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(ProfileViewActivity.this, "Unfriended successfully", Toast.LENGTH_SHORT).show();
                        // Update UI state locally
                        if (otherUser != null) otherUser.setFriend(false);
                    } else {
                        Toast.makeText(ProfileViewActivity.this, "Failed to unfriend", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void navigateToPrivateChat() {
        if (otherUser == null) return;
        
        // First, try to find existing chat with this user
        String token = databaseManager.getToken();
        if (token != null && !token.isEmpty()) {
            apiClient.getChats(token, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileViewActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            
                            if (jsonResponse.optBoolean("success", false)) {
                                org.json.JSONArray chatsArray = jsonResponse.getJSONObject("data").getJSONArray("chats");
                                
                                // Look for existing private chat with this user
                                for (int i = 0; i < chatsArray.length(); i++) {
                                    org.json.JSONObject chatJson = chatsArray.getJSONObject(i);
                                    String chatType = chatJson.optString("type", "");
                                    
                                    if ("private".equals(chatType)) {
                                        // Check if this chat involves the other user
                                        org.json.JSONArray participants = chatJson.getJSONArray("participants");
                                        for (int j = 0; j < participants.length(); j++) {
                                            org.json.JSONObject participant = participants.getJSONObject(j);
                                            String userId = participant.optString("user", "");
                                            if (otherUser.getId().equals(userId)) {
                                                // Found existing chat, open it
                                                runOnUiThread(() -> {
                                                    Intent intent = new Intent(ProfileViewActivity.this, PrivateChatActivity.class);
                                                    intent.putExtra("chat", chatJson.toString());
                                                    try {
                                                        intent.putExtra("user", otherUser.toJson().toString());
                                                    } catch (JSONException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    startActivity(intent);
                                                });
                                                return;
                                            }
                                        }
                                    }
                                }
                                
                                // No existing chat found, create new one
                                runOnUiThread(() -> createNewPrivateChat());
                            } else {
                                runOnUiThread(() -> createNewPrivateChat());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> createNewPrivateChat());
                        }
                    } else {
                        runOnUiThread(() -> createNewPrivateChat());
                    }
                }
            });
        } else {
            createNewPrivateChat();
        }
    }
    
    private void createNewPrivateChat() {
        // Create a new private chat on the server
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create chat with the other user
        apiClient.createPrivateChat(token, otherUser.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileViewActivity.this, "Failed to create chat", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        
                        if (jsonResponse.optBoolean("success", false)) {
                            org.json.JSONObject chatData = jsonResponse.getJSONObject("data").getJSONObject("chat");
                            
                            runOnUiThread(() -> {
                                Intent intent = new Intent(ProfileViewActivity.this, PrivateChatActivity.class);
                                intent.putExtra("chat", chatData.toString());
                                try {
                                    intent.putExtra("user", otherUser.toJson().toString());
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                startActivity(intent);
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(ProfileViewActivity.this, "Failed to create chat", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            Toast.makeText(ProfileViewActivity.this, "Error creating chat", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileViewActivity.this, "Failed to create chat", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reload user data when returning to activity
        // This ensures profile changes are reflected immediately
        if (otherUser != null) {
            loadOtherUserData();
        }
    }
    
    private void loadOtherUserData() {
        if (otherUser == null) return;
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        // Use ApiClient to get updated user data
        com.example.chatappjava.network.ApiClient apiClient = new com.example.chatappjava.network.ApiClient();
        apiClient.authenticatedGet("/api/users/" + otherUser.getId(), token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                android.util.Log.e("ProfileViewActivity", "Failed to reload user data: " + e.getMessage());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        org.json.JSONObject userData = jsonResponse.getJSONObject("data").getJSONObject("user");
                        
                        runOnUiThread(() -> {
                            try {
                                otherUser = User.fromJson(userData);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            loadUserData(); // Refresh the UI with updated data
                        });
                    } catch (Exception e) {
                        android.util.Log.e("ProfileViewActivity", "Error parsing user data: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        com.example.chatappjava.network.ApiClient apiClient = new com.example.chatappjava.network.ApiClient();
        // Use search API, then pick exact match (case-insensitive)
        apiClient.authenticatedGet("/api/users/search?q=" + username, token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileViewActivity.this, "Failed to load user", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileViewActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                try {
                    String body = response.body().string();
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    org.json.JSONArray arr = null;
                    if (json.has("data") && json.get("data") instanceof org.json.JSONObject) {
                        org.json.JSONObject data = json.getJSONObject("data");
                        if (data.has("users")) arr = data.getJSONArray("users");
                    }
                    if (arr == null && json.has("users")) {
                        arr = json.getJSONArray("users");
                    }
                    if (arr == null || arr.length() == 0) {
                        runOnUiThread(() -> {
                            Toast.makeText(ProfileViewActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                        return;
                    }
                    // Pick best match
                    org.json.JSONObject userObj = null;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject u = arr.getJSONObject(i);
                        String uname = u.optString("username", "");
                        if (uname.equalsIgnoreCase(username)) { userObj = u; break; }
                    }
                    if (userObj == null) userObj = arr.getJSONObject(0);
                    User fetched = User.fromJson(userObj);
                    otherUser = fetched;
                    runOnUiThread(() -> loadUserData());
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileViewActivity.this, "Error parsing user", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }
        });
    }
}
