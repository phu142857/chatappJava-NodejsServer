package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Response;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.PostAdapter;
import com.example.chatappjava.adapters.TagUserAdapter;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;

import java.util.ArrayList;
import java.util.HashSet;
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
        Intent intent = new Intent(PostFeedActivity.this, PostDetailActivity.class);
        try {
            intent.putExtra("post", post.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            Log.e(TAG, "Error passing post data: " + e.getMessage());
            Toast.makeText(this, "Error opening post", Toast.LENGTH_SHORT).show();
        }
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
        Intent intent = new Intent(PostFeedActivity.this, PostDetailActivity.class);
        try {
            intent.putExtra("post", post.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            Log.e(TAG, "Error passing post data: " + e.getMessage());
            Toast.makeText(this, "Error opening comments", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onShareClick(Post post) {
        try {
            Log.d(TAG, "=== onShareClick START ===");
            Log.d(TAG, "onShareClick called for post: " + (post != null ? post.getId() : "null"));
            if (post == null) {
                Log.e(TAG, "Post is null!");
                Toast.makeText(this, "Post data is missing", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "Calling showShareDialog");
            showShareDialog(post);
            Log.d(TAG, "=== onShareClick END ===");
        } catch (Exception e) {
            Log.e(TAG, "Error in onShareClick: " + e.getMessage(), e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showShareDialog(Post post) {
        try {
            Log.d(TAG, "showShareDialog called");
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_share_post, null);
            builder.setView(dialogView);
            
            android.app.AlertDialog dialog = builder.create();
            
            // Set dialog window properties
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            // Initialize views
            LinearLayout optionShareToFeed = dialogView.findViewById(R.id.option_share_to_feed);
            LinearLayout optionSendAsMessage = dialogView.findViewById(R.id.option_send_as_message);
            LinearLayout optionShareToStory = dialogView.findViewById(R.id.option_share_to_story);
            LinearLayout optionShareToGroup = dialogView.findViewById(R.id.option_share_to_group);
            LinearLayout optionCopyLink = dialogView.findViewById(R.id.option_copy_link);
            LinearLayout optionShareExternal = dialogView.findViewById(R.id.option_share_external);
            
            if (optionShareToFeed == null) {
                Log.e(TAG, "option_share_to_feed view not found!");
                Toast.makeText(this, "Error loading share dialog", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Share to Feed
            optionShareToFeed.setOnClickListener(v -> {
                Log.d(TAG, "Share to Feed clicked");
                dialog.dismiss();
                shareToFeed(post);
            });
            
            // Send as Message
            optionSendAsMessage.setOnClickListener(v -> {
                Log.d(TAG, "Send as Message clicked");
                dialog.dismiss();
                sendAsMessage(post);
            });
            
            // Share to Story (hidden for now)
            optionShareToStory.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(this, "Share to Story coming soon", Toast.LENGTH_SHORT).show();
            });
            
            // Share to Group (hidden for now)
            optionShareToGroup.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(this, "Share to Group coming soon", Toast.LENGTH_SHORT).show();
            });
            
            // Copy Link
            optionCopyLink.setOnClickListener(v -> {
                Log.d(TAG, "Copy Link clicked");
                dialog.dismiss();
                copyPostLink(post);
            });
            
            // Share to External Apps
            optionShareExternal.setOnClickListener(v -> {
                Log.d(TAG, "Share to External Apps clicked");
                dialog.dismiss();
                shareToExternalApps(post);
            });
            
            dialog.show();
            Log.d(TAG, "Share dialog shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing share dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error showing share options: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void shareToFeed(Post post) {
        Log.d(TAG, "shareToFeed called for post: " + post.getId());
        if (post == null || post.getId() == null) {
            Toast.makeText(this, "Cannot share: Post data is invalid", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Open CreatePostActivity with shared post data
        Intent intent = new Intent(this, CreatePostActivity.class);
        intent.putExtra("shared_post_id", post.getId());
        intent.putExtra("shared_post_content", post.getContent());
        intent.putExtra("shared_post_author", post.getAuthorUsername());
        intent.putExtra("shared_post_author_id", post.getAuthorId());
        intent.putExtra("shared_post_author_avatar", post.getAuthorAvatar()); // Add avatar
        if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            intent.putExtra("shared_post_media", post.getMediaUrls().get(0));
        }
        Log.d(TAG, "Starting CreatePostActivity with shared_post_id: " + post.getId());
        startActivityForResult(intent, 1002);
    }
    
    private void sendAsMessage(Post post) {
        // Open chat selection to send post as message
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("action", "share_post");
        intent.putExtra("post_id", post.getId());
        startActivity(intent);
    }
    
    private void copyPostLink(Post post) {
        // Generate deep link URL (custom scheme for local app)
        // This will open the app directly when clicked, similar to clicking on a tagged user
        String deepLinkUrl = "chatapp://post/" + post.getId();
        
        // Copy the deep link as plain text (most apps recognize custom schemes in plain text)
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Post Link", deepLinkUrl);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Post link copied. Click to open in app.", Toast.LENGTH_SHORT).show();
    }
    
    private void shareToExternalApps(Post post) {
        // Use Android Share Sheet
        String postUrl = com.example.chatappjava.config.ServerConfig.getBaseUrl() + "/posts/" + post.getId();
        String shareText = post.getContent() != null && !post.getContent().isEmpty() 
            ? post.getContent() + "\n\n" + postUrl 
            : postUrl;
        
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out this post");
        
        startActivity(Intent.createChooser(shareIntent, "Share post via"));
        
        // Update share count (optimistic update)
        post.setSharesCount(post.getSharesCount() + 1);
        int position = postList.indexOf(post);
        if (position >= 0) {
            postAdapter.updatePost(position, post);
        }
    }
    
    private void updateShareCount(Post post) {
        // Call API to get updated share count
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        
        // Find post in list and update share count
        int position = postList.indexOf(post);
        if (position >= 0) {
            // Reload post to get updated share count
            apiClient.getPostById(token, post.getId(), new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    // Silent fail
                }
                
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                JSONObject postJson = data.getJSONObject("post");
                                Post updatedPost = Post.fromJson(postJson);
                                
                                runOnUiThread(() -> {
                                    int pos = postList.indexOf(post);
                                    if (pos >= 0) {
                                        post.setSharesCount(updatedPost.getSharesCount());
                                        postAdapter.updatePost(pos, post);
                                    }
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing post response: " + e.getMessage());
                        }
                    }
                }
            });
        }
    }
    
    @Override
    public void onPostMenuClick(Post post) {
        showPostOptionsMenu(post);
    }
    
    private void showPostOptionsMenu(Post post) {
        if (post == null) return;
        
        String currentUserId = databaseManager.getUserId();
        boolean isOwnPost = post.getAuthorId() != null && post.getAuthorId().equals(currentUserId);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_post_options, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Set dialog window properties
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Initialize views
        LinearLayout optionDeletePost = dialogView.findViewById(R.id.option_delete_post);
        LinearLayout optionHidePost = dialogView.findViewById(R.id.option_hide_post);
        LinearLayout optionCancel = dialogView.findViewById(R.id.option_cancel);
        
        // Show appropriate options based on ownership
        if (isOwnPost) {
            optionDeletePost.setVisibility(View.VISIBLE);
            optionHidePost.setVisibility(View.GONE);
        } else {
            optionDeletePost.setVisibility(View.GONE);
            optionHidePost.setVisibility(View.VISIBLE);
        }
        
        // Delete Post
        optionDeletePost.setOnClickListener(v -> {
            dialog.dismiss();
            deletePost(post);
        });
        
        // Hide Post
        optionHidePost.setOnClickListener(v -> {
            dialog.dismiss();
            hidePost(post);
        });
        
        // Cancel
        optionCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void deletePost(Post post) {
        AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
        confirmBuilder.setTitle("Delete Post");
        confirmBuilder.setMessage("Are you sure you want to delete this post? This action cannot be undone.");
        confirmBuilder.setPositiveButton("Delete", (dialog, which) -> {
            String token = databaseManager.getToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
                return;
            }
            
            apiClient.deletePost(token, post.getId(), new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(PostFeedActivity.this, "Failed to delete post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.getBoolean("success")) {
                                Toast.makeText(PostFeedActivity.this, "Post deleted successfully", Toast.LENGTH_SHORT).show();
                                // Reload posts
                                loadPosts(true);
                            } else {
                                String message = jsonResponse.optString("message", "Failed to delete post");
                                Toast.makeText(PostFeedActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(PostFeedActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
        confirmBuilder.setNegativeButton("Cancel", null);
        confirmBuilder.show();
    }
    
    private void hidePost(Post post) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.hidePost(token, post.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(PostFeedActivity.this, "Failed to hide post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(PostFeedActivity.this, "Post hidden successfully", Toast.LENGTH_SHORT).show();
                            // Remove post from list
                            int position = postList.indexOf(post);
                            if (position >= 0) {
                                postList.remove(position);
                                postAdapter.notifyItemRemoved(position);
                            }
                        } else {
                            String message = jsonResponse.optString("message", "Failed to hide post");
                            Toast.makeText(PostFeedActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(PostFeedActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    @Override
    public void onAuthorClick(Post post) {
        // TODO: Open author profile
        Toast.makeText(this, "Author: " + post.getAuthorUsername(), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onMediaClick(Post post, int mediaIndex) {
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
                        Intent intent = new Intent(PostFeedActivity.this, PostDetailActivity.class);
                        try {
                            intent.putExtra("post", post.toJson().toString());
                            startActivity(intent);
                        } catch (JSONException e) {
                            Toast.makeText(PostFeedActivity.this, "Error opening post", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void onTaggedUsersClick(Post post) {
        showTaggedUsersDialog(post);
    }
    
    private void showTaggedUsersDialog(Post post) {
        List<User> taggedUsers = post.getTaggedUsers();
        if (taggedUsers == null || taggedUsers.isEmpty()) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tagged_users, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        RecyclerView rvTaggedUsers = dialogView.findViewById(R.id.rv_tagged_users);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        if (tvTitle != null) {
            tvTitle.setText("Tagged People");
        }
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvTaggedUsers.setLayoutManager(layoutManager);
        
        TagUserAdapter adapter = new TagUserAdapter(
            this,
            taggedUsers,
            new HashSet<>(),
            new TagUserAdapter.OnTagUserClickListener() {
                @Override
                public void onUserClick(User user, boolean isSelected) {
                    // Open user profile when clicked
                    Intent intent = new Intent(PostFeedActivity.this, ProfileViewActivity.class);
                    try {
                        intent.putExtra("user", user.toJson().toString());
                        startActivity(intent);
                        dialog.dismiss();
                    } catch (JSONException e) {
                        Toast.makeText(PostFeedActivity.this, "Error opening profile", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );
        rvTaggedUsers.setAdapter(adapter);
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == 1001 || requestCode == 1002) && resultCode == RESULT_OK) {
            // Post was created or shared successfully, refresh the feed
            loadPosts(true);
        }
    }
}

