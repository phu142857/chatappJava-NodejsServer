package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.PostAdapter;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class PostFeedActivity extends AppCompatActivity implements PostAdapter.OnPostClickListener {
    
    private static final String TAG = "PostFeedActivity";
    
    // UI Components - Top Nav
    private TextView tvAppTitle;
    private ImageButton ivSearch;
    private ImageButton ivNotifications;
    
    // UI Components - Action Bar
    private CircleImageView ivProfileThumbnail;
    private TextView tvCreatePostHint;
    private LinearLayout llLive;
    private LinearLayout llPhoto;
    private LinearLayout llVideo;
    
    // UI Components - Posts
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private List<Post> postList;
    
    // UI Components - Bottom Nav
    private LinearLayout llTabFeed;
    private LinearLayout llTabGroups;
    private LinearLayout llTabWatch;
    private LinearLayout llTabProfile;
    
    // Data and Services
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private AvatarManager avatarManager;
    private boolean isLoading = false;
    private int currentPage = 0;
    private boolean hasMorePosts = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_feed);
        
        // Check if user is logged in
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }
        
        initializeViews();
        initializeServices();
        setupClickListeners();
        setupRecyclerView();
        loadUserProfile();
        loadPosts(true);
    }
    
    private void initializeViews() {
        // Top Nav
        tvAppTitle = findViewById(R.id.tv_app_title);
        ivSearch = findViewById(R.id.iv_search);
        ivNotifications = findViewById(R.id.iv_notifications);
        
        // Action Bar
        ivProfileThumbnail = findViewById(R.id.iv_profile_thumbnail);
        tvCreatePostHint = findViewById(R.id.tv_create_post_hint);
        llLive = findViewById(R.id.ll_live);
        llPhoto = findViewById(R.id.ll_photo);
        llVideo = findViewById(R.id.ll_video);
        
        // Posts
        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvPosts = findViewById(R.id.rv_posts);
        
        // Bottom Nav
        llTabFeed = findViewById(R.id.ll_tab_feed);
        llTabGroups = findViewById(R.id.ll_tab_groups);
        llTabWatch = findViewById(R.id.ll_tab_watch);
        llTabProfile = findViewById(R.id.ll_tab_profile);
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        postList = new ArrayList<>();
    }
    
    private void setupClickListeners() {
        // Top Nav
        ivSearch.setOnClickListener(v -> {
            Intent intent = new Intent(PostFeedActivity.this, SearchActivity.class);
            startActivity(intent);
        });
        
        ivNotifications.setOnClickListener(v -> {
            Toast.makeText(this, "Notifications coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Action Bar
        tvCreatePostHint.setOnClickListener(v -> {
            Intent intent = new Intent(PostFeedActivity.this, CreatePostActivity.class);
            startActivityForResult(intent, 1001);
        });
        
        ivProfileThumbnail.setOnClickListener(v -> {
            Intent intent = new Intent(PostFeedActivity.this, ProfileActivity.class);
            startActivity(intent);
        });
        
        llLive.setOnClickListener(v -> {
            Toast.makeText(this, "Live streaming coming soon", Toast.LENGTH_SHORT).show();
        });
        
        llPhoto.setOnClickListener(v -> {
            // TODO: Open photo picker and create post
            Toast.makeText(this, "Create photo post coming soon", Toast.LENGTH_SHORT).show();
        });
        
        llVideo.setOnClickListener(v -> {
            // TODO: Open video picker and create post
            Toast.makeText(this, "Create video post coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Bottom Nav
        llTabFeed.setOnClickListener(v -> {
            // Already on feed tab
        });
        
        llTabGroups.setOnClickListener(v -> {
            Intent intent = new Intent(PostFeedActivity.this, HomeActivity.class);
            intent.putExtra("tab", 1); // Groups tab
            startActivity(intent);
            finish();
        });
        
        llTabWatch.setOnClickListener(v -> {
            Toast.makeText(this, "Watch tab coming soon", Toast.LENGTH_SHORT).show();
        });
        
        llTabProfile.setOnClickListener(v -> {
            Intent intent = new Intent(PostFeedActivity.this, ProfileActivity.class);
            startActivity(intent);
        });
        
        // Swipe to refresh
        swipeRefresh.setOnRefreshListener(() -> {
            loadPosts(true);
        });
    }
    
    private void setupRecyclerView() {
        postAdapter = new PostAdapter(this, postList, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvPosts.setLayoutManager(layoutManager);
        rvPosts.setAdapter(postAdapter);
        
        // Infinite scroll
        rvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if (!isLoading && hasMorePosts) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0) {
                            loadPosts(false);
                        }
                    }
                }
            }
        });
    }
    
    private void loadUserProfile() {
        String userId = databaseManager.getUserId();
        String username = databaseManager.getUserName();
        String avatar = databaseManager.getUserAvatar();
        
        if (avatarManager != null && avatar != null && !avatar.isEmpty()) {
            String avatarUrl = avatar;
            // Construct full URL if needed
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
            }
            avatarManager.loadAvatar(avatarUrl, ivProfileThumbnail, R.drawable.ic_profile_placeholder);
        } else {
            ivProfileThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }
    
    private void loadPosts(boolean refresh) {
        if (isLoading) {
            return;
        }
        
        isLoading = true;
        
        if (refresh) {
            currentPage = 0;
            hasMorePosts = true;
            swipeRefresh.setRefreshing(true);
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            isLoading = false;
            swipeRefresh.setRefreshing(false);
            return;
        }
        
        // Call API to get feed posts
        apiClient.getFeedPosts(token, currentPage + 1, 20, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load posts: " + e.getMessage());
                    Toast.makeText(PostFeedActivity.this, "Error loading posts", Toast.LENGTH_SHORT).show();
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                JSONArray postsArray = data.getJSONArray("posts");
                                
                                String currentUserId = databaseManager.getUserId();
                                List<Post> newPosts = new ArrayList<>();
                                for (int i = 0; i < postsArray.length(); i++) {
                                    try {
                                        JSONObject postJson = postsArray.getJSONObject(i);
                                        
                                        // Check if current user liked this post
                                        JSONArray likesArray = postJson.optJSONArray("likes");
                                        if (likesArray != null && currentUserId != null) {
                                            boolean isLiked = false;
                                            for (int j = 0; j < likesArray.length(); j++) {
                                                JSONObject likeObj = likesArray.optJSONObject(j);
                                                if (likeObj != null) {
                                                    JSONObject userObj = likeObj.optJSONObject("user");
                                                    if (userObj != null) {
                                                        String likeUserId = userObj.optString("_id", "");
                                                        if (currentUserId.equals(likeUserId)) {
                                                            isLiked = true;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            postJson.put("isLiked", isLiked);
                                        }
                                        
                                        Post post = Post.fromJson(postJson);
                                        newPosts.add(post);
                                    } catch (JSONException e) {
                                        Log.e(TAG, "Error parsing post: " + e.getMessage());
                                    }
                                }
                                
                                if (refresh) {
                                    postList.clear();
                                }
                                postList.addAll(newPosts);
                                postAdapter.setPosts(postList);
                                
                                // Check if there are more posts
                                JSONObject pagination = data.optJSONObject("pagination");
                                if (pagination != null) {
                                    int total = pagination.optInt("total", 0);
                                    hasMorePosts = postList.size() < total;
                                } else {
                                    hasMorePosts = postsArray.length() >= 20; // Assume more if we got full page
                                }
                                
                                currentPage++;
                            } else {
                                String message = jsonResponse.optString("message", "Failed to load posts");
                                Toast.makeText(PostFeedActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } else if (response.code() == 401) {
                            databaseManager.clearLoginInfo();
                            redirectToLogin();
                        } else {
                            Toast.makeText(PostFeedActivity.this, "Error loading posts (Code: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing posts response: " + e.getMessage());
                        Toast.makeText(PostFeedActivity.this, "Error parsing posts", Toast.LENGTH_SHORT).show();
                    } finally {
                        isLoading = false;
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        });
    }
    
    private List<Post> generateMockPosts() {
        List<Post> posts = new ArrayList<>();
        String userId = databaseManager.getUserId();
        String username = databaseManager.getUserName();
        
        for (int i = 0; i < 5; i++) {
            Post post = new Post();
            post.setId("post_" + (currentPage * 5 + i));
            post.setAuthorId(userId);
            post.setAuthorUsername(username + " " + (i + 1));
            post.setContent("This is a sample post #" + (currentPage * 5 + i + 1) + ". " +
                    "It demonstrates the post feed functionality with multiple lines of text content.");
            post.setTimestamp(System.currentTimeMillis() - (i * 3600000)); // Different timestamps
            post.setLikesCount(10 + i * 5);
            post.setCommentsCount(2 + i);
            post.setSharesCount(i);
            post.setLiked(i % 2 == 0);
            posts.add(post);
        }
        
        return posts;
    }
    
    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    // PostAdapter.OnPostClickListener implementation
    @Override
    public void onPostClick(Post post) {
        // TODO: Open post detail view
        Toast.makeText(this, "Post clicked: " + post.getId(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onLikeClick(Post post, int position) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Optimistically update UI
        boolean newLikedState = !post.isLiked();
        post.setLiked(newLikedState);
        
        if (newLikedState) {
            post.setLikesCount(post.getLikesCount() + 1);
        } else {
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        }
        
        postAdapter.updatePost(position, post);
        
        // Make API call to like/unlike post
        apiClient.toggleLikePost(token, post.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    // Revert optimistic update on failure
                    post.setLiked(!newLikedState);
                    if (newLikedState) {
                        post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
                    } else {
                        post.setLikesCount(post.getLikesCount() + 1);
                    }
                    postAdapter.updatePost(position, post);
                    Toast.makeText(PostFeedActivity.this, "Failed to update like", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        // Revert optimistic update on failure
                        post.setLiked(!newLikedState);
                        if (newLikedState) {
                            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
                        } else {
                            post.setLikesCount(post.getLikesCount() + 1);
                        }
                        postAdapter.updatePost(position, post);
                        
                        if (response.code() == 401) {
                            databaseManager.clearLoginInfo();
                            redirectToLogin();
                        } else {
                            Toast.makeText(PostFeedActivity.this, "Failed to update like", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.optBoolean("success", false)) {
                            JSONObject data = jsonResponse.optJSONObject("data");
                            if (data != null) {
                                boolean liked = data.optBoolean("liked", newLikedState);
                                int likesCount = data.optInt("likesCount", post.getLikesCount());
                                
                                runOnUiThread(() -> {
                                    post.setLiked(liked);
                                    post.setLikesCount(likesCount);
                                    postAdapter.updatePost(position, post);
                                });
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing like response: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    @Override
    public void onCommentClick(Post post) {
        // TODO: Open comment thread
        Toast.makeText(this, "Comments for post: " + post.getId(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onShareClick(Post post) {
        // TODO: Open share dialog
        Toast.makeText(this, "Share post: " + post.getId(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onPostMenuClick(Post post) {
        // TODO: Show post options menu (edit, delete, report, etc.)
        Toast.makeText(this, "Post menu: " + post.getId(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onAuthorClick(Post post) {
        // TODO: Open author profile
        Toast.makeText(this, "Author: " + post.getAuthorUsername(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onMediaClick(Post post, int mediaIndex) {
        // TODO: Open media viewer
        Toast.makeText(this, "Media clicked: " + mediaIndex, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Post was created successfully, refresh the feed
            loadPosts(true);
        }
    }
}

