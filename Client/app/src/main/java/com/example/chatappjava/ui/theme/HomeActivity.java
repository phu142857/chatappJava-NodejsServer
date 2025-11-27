package com.example.chatappjava.ui.theme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements ChatListAdapter.OnChatClickListener, CallListAdapter.OnCallClickListener {
    
    private static final String TAG = "HomeActivity";
    
    // UI Components
    // tvMessagesTitle removed - no longer needed
    private ImageView ivSearch, ivMoreVert;
    private View btnNewGroupAction;
    private View efabNewGroup;
    private TextView tvChats, tvGroups, tvCalls, tvPosts;
    private RecyclerView rvChatList;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout llFriendRequests;
    private TextView tvFriendRequestCount;
    private TextView tvFriendRequestsTitle;
    private View userProfileSection; // Top bar with user profile
    
    // Posts Tab - Create Post Bar
    private LinearLayout llCreatePostBar;
    private LinearLayout llCreatePostInput;
    private TextView tvCreatePostHint;
    private de.hdodenhof.circleimageview.CircleImageView ivPostProfileThumbnail;
    private LinearLayout llLivePost;
    private LinearLayout llPhotoPost;
    private LinearLayout llVideoPost;
    
    // User Profile Components
    private de.hdodenhof.circleimageview.CircleImageView ivUserAvatar;
    private TextView tvUserName;
    
    // Data and Services
    private DatabaseManager databaseManager;
    private ConversationRepository conversationRepository;
    private ApiClient apiClient;
    private ChatListAdapter chatAdapter;
    private CallListAdapter callAdapter;
    private com.example.chatappjava.adapters.PostAdapter postAdapter;
    private List<Chat> chatList;
    private List<Call> callList;
    private List<com.example.chatappjava.models.Post> postList;
    private AvatarManager avatarManager;
    private boolean isLoadingChats = false;
    private boolean isLoadingCalls = false;
    private boolean isLoadingPosts = false;
    private boolean isPolling = false;
    private AlertDialog currentDialog;
    private final java.util.Set<String> blockedUserIds = new java.util.HashSet<>();
    private static final long HOME_POLL_INTERVAL_MS = 300000L;
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
    private int currentTab = 0; // 0: Chats, 1: Groups, 2: Calls, 3: Posts
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
        userProfileSection = findViewById(R.id.user_profile_section);
        tvChats = findViewById(R.id.tv_chats);
        tvGroups = findViewById(R.id.tv_groups);
        tvCalls = findViewById(R.id.tv_calls);
        tvPosts = findViewById(R.id.tv_posts);
        rvChatList = findViewById(R.id.rv_chat_list);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        llFriendRequests = findViewById(R.id.ll_friend_requests);
        tvFriendRequestCount = findViewById(R.id.tv_friend_request_count);
        // Initialize friend requests title (different layout ids possible)
        tvFriendRequestsTitle = findViewById(R.id.tv_requests_title);
        // New Group Extended FAB
        efabNewGroup = findViewById(R.id.efab_new_group);
        
        // Posts Tab - Create Post Bar
        llCreatePostBar = findViewById(R.id.ll_create_post_bar);
        llCreatePostInput = findViewById(R.id.ll_create_post_input);
        tvCreatePostHint = findViewById(R.id.tv_create_post_hint);
        ivPostProfileThumbnail = findViewById(R.id.iv_post_profile_thumbnail);
        llLivePost = findViewById(R.id.ll_live_post);
        llPhotoPost = findViewById(R.id.ll_photo_post);
        llVideoPost = findViewById(R.id.ll_video_post);
        
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
        postList = new ArrayList<>();
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
        
        tvPosts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(3);
            }
        });
        
        // Create Post Bar Click Listeners
        if (llCreatePostInput != null) {
            llCreatePostInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(HomeActivity.this, CreatePostActivity.class);
                    startActivityForResult(intent, 1002);
                }
            });
        }
        
        if (llPhotoPost != null) {
            llPhotoPost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(HomeActivity.this, CreatePostActivity.class);
                    startActivityForResult(intent, 1002);
                }
            });
        }
        
        if (llVideoPost != null) {
            llVideoPost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(HomeActivity.this, CreatePostActivity.class);
                    startActivityForResult(intent, 1002);
                }
            });
        }
        
        if (llLivePost != null) {
            llLivePost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(HomeActivity.this, "Live streaming coming soon", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
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
        
        // Setup post adapter
        postAdapter = new com.example.chatappjava.adapters.PostAdapter(this, postList, new com.example.chatappjava.adapters.PostAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(com.example.chatappjava.models.Post post) {
                android.content.Intent intent = new android.content.Intent(HomeActivity.this, com.example.chatappjava.ui.theme.PostDetailActivity.class);
                try {
                    intent.putExtra("post", post.toJson().toString());
                    startActivity(intent);
                } catch (org.json.JSONException e) {
                    android.util.Log.e(TAG, "Error passing post data: " + e.getMessage());
                    android.widget.Toast.makeText(HomeActivity.this, "Error opening post", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onLikeClick(com.example.chatappjava.models.Post post, int position) {
                String token = databaseManager.getToken();
                if (token == null || token.isEmpty()) {
                    Toast.makeText(HomeActivity.this, "Please login again", Toast.LENGTH_SHORT).show();
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
                        });
                    }
                    
                    @Override
                    public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                        if (response.isSuccessful()) {
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
                        } else {
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
                                }
                            });
                        }
                    }
                });
            }
            
            @Override
            public void onCommentClick(com.example.chatappjava.models.Post post) {
                Intent intent = new Intent(HomeActivity.this, PostDetailActivity.class);
                try {
                    intent.putExtra("post", post.toJson().toString());
                    startActivity(intent);
                } catch (JSONException e) {
                    Log.e(TAG, "Error passing post data: " + e.getMessage());
                    Toast.makeText(HomeActivity.this, "Error opening comments", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onShareClick(com.example.chatappjava.models.Post post) {
                android.util.Log.d(TAG, "HomeActivity.onShareClick called for post: " + (post != null ? post.getId() : "null"));
                showShareDialog(post);
            }
            
            private void showShareDialog(com.example.chatappjava.models.Post post) {
                try {
                    android.util.Log.d(TAG, "HomeActivity.showShareDialog called");
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(HomeActivity.this);
                    android.view.View dialogView = getLayoutInflater().inflate(com.example.chatappjava.R.layout.dialog_share_post, null);
                    builder.setView(dialogView);
                    
                    android.app.AlertDialog dialog = builder.create();
                    
                    // Set dialog window properties
                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        window.setBackgroundDrawableResource(android.R.color.transparent);
                    }
                    
                    // Initialize views
                    android.widget.LinearLayout optionShareToFeed = dialogView.findViewById(com.example.chatappjava.R.id.option_share_to_feed);
                    android.widget.LinearLayout optionSendAsMessage = dialogView.findViewById(com.example.chatappjava.R.id.option_send_as_message);
                    android.widget.LinearLayout optionShareToStory = dialogView.findViewById(com.example.chatappjava.R.id.option_share_to_story);
                    android.widget.LinearLayout optionShareToGroup = dialogView.findViewById(com.example.chatappjava.R.id.option_share_to_group);
                    android.widget.LinearLayout optionCopyLink = dialogView.findViewById(com.example.chatappjava.R.id.option_copy_link);
                    android.widget.LinearLayout optionShareExternal = dialogView.findViewById(com.example.chatappjava.R.id.option_share_external);
                    
                    if (optionShareToFeed == null) {
                        android.util.Log.e(TAG, "option_share_to_feed view not found!");
                        android.widget.Toast.makeText(HomeActivity.this, "Error loading share dialog", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Share to Feed
                    optionShareToFeed.setOnClickListener(v -> {
                        android.util.Log.d(TAG, "Share to Feed clicked");
                        dialog.dismiss();
                        shareToFeed(post);
                    });
                    
                    // Send as Message
                    optionSendAsMessage.setOnClickListener(v -> {
                        android.util.Log.d(TAG, "Send as Message clicked");
                        dialog.dismiss();
                        sendAsMessage(post);
                    });
                    
                    // Share to Story (hidden for now)
                    optionShareToStory.setOnClickListener(v -> {
                        dialog.dismiss();
                        android.widget.Toast.makeText(HomeActivity.this, "Share to Story coming soon", android.widget.Toast.LENGTH_SHORT).show();
                    });
                    
                    // Share to Group (hidden for now)
                    optionShareToGroup.setOnClickListener(v -> {
                        dialog.dismiss();
                        android.widget.Toast.makeText(HomeActivity.this, "Share to Group coming soon", android.widget.Toast.LENGTH_SHORT).show();
                    });
                    
                    // Copy Link
                    optionCopyLink.setOnClickListener(v -> {
                        android.util.Log.d(TAG, "Copy Link clicked");
                        dialog.dismiss();
                        copyPostLink(post);
                    });
                    
                    // Share to External Apps
                    optionShareExternal.setOnClickListener(v -> {
                        android.util.Log.d(TAG, "Share to External Apps clicked");
                        dialog.dismiss();
                        shareToExternalApps(post);
                    });
                    
                    dialog.show();
                    android.util.Log.d(TAG, "Share dialog shown");
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error showing share dialog: " + e.getMessage(), e);
                    android.widget.Toast.makeText(HomeActivity.this, "Error showing share options: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            
            private void shareToFeed(com.example.chatappjava.models.Post post) {
                android.util.Log.d(TAG, "shareToFeed called for post: " + post.getId());
                if (post == null || post.getId() == null) {
                    android.widget.Toast.makeText(HomeActivity.this, "Cannot share: Post data is invalid", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Open CreatePostActivity with shared post data
                android.content.Intent intent = new android.content.Intent(HomeActivity.this, com.example.chatappjava.ui.theme.CreatePostActivity.class);
                intent.putExtra("shared_post_id", post.getId());
                intent.putExtra("shared_post_content", post.getContent());
                intent.putExtra("shared_post_author", post.getAuthorUsername());
                intent.putExtra("shared_post_author_id", post.getAuthorId());
                intent.putExtra("shared_post_author_avatar", post.getAuthorAvatar()); // Add avatar
                if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                    intent.putExtra("shared_post_media", post.getMediaUrls().get(0));
                }
                android.util.Log.d(TAG, "Starting CreatePostActivity with shared_post_id: " + post.getId() + ", author: " + post.getAuthorUsername() + ", avatar: " + post.getAuthorAvatar());
                startActivityForResult(intent, 1002);
            }
            
            private void sendAsMessage(com.example.chatappjava.models.Post post) {
                // Open chat selection to send post as message
                android.content.Intent intent = new android.content.Intent(HomeActivity.this, HomeActivity.class);
                intent.putExtra("action", "share_post");
                intent.putExtra("post_id", post.getId());
                startActivity(intent);
            }
            
            private void copyPostLink(com.example.chatappjava.models.Post post) {
                // Generate deep link URL (custom scheme for local app)
                // This will open the app directly when clicked, similar to clicking on a tagged user
                String deepLinkUrl = "chatapp://post/" + post.getId();
                
                // Copy the deep link as plain text (most apps recognize custom schemes in plain text)
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Post Link", deepLinkUrl);
                clipboard.setPrimaryClip(clip);
                
                android.widget.Toast.makeText(HomeActivity.this, "Post link copied. Click to open in app.", android.widget.Toast.LENGTH_SHORT).show();
            }
            
            private void shareToExternalApps(com.example.chatappjava.models.Post post) {
                // Use Android Share Sheet
                String postUrl = com.example.chatappjava.config.ServerConfig.getBaseUrl() + "/posts/" + post.getId();
                String shareText = post.getContent() != null && !post.getContent().isEmpty() 
                    ? post.getContent() + "\n\n" + postUrl 
                    : postUrl;
                
                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out this post");
                
                startActivity(android.content.Intent.createChooser(shareIntent, "Share post via"));
                
                // Update share count (optimistic update)
                post.setSharesCount(post.getSharesCount() + 1);
                int position = postList.indexOf(post);
                if (position >= 0) {
                    postAdapter.updatePost(position, post);
                }
            }
            
            @Override
            public void onPostMenuClick(com.example.chatappjava.models.Post post) {
                showPostOptionsMenu(post);
            }
            
            private void showPostOptionsMenu(com.example.chatappjava.models.Post post) {
                if (post == null) return;
                
                String currentUserId = databaseManager.getUserId();
                boolean isOwnPost = post.getAuthorId() != null && post.getAuthorId().equals(currentUserId);
                
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(HomeActivity.this);
                android.view.View dialogView = getLayoutInflater().inflate(com.example.chatappjava.R.layout.dialog_post_options, null);
                builder.setView(dialogView);
                
                android.app.AlertDialog dialog = builder.create();
                
                // Set dialog window properties
                android.view.Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                }
                
                // Initialize views
                android.widget.LinearLayout optionDeletePost = dialogView.findViewById(com.example.chatappjava.R.id.option_delete_post);
                android.widget.LinearLayout optionHidePost = dialogView.findViewById(com.example.chatappjava.R.id.option_hide_post);
                android.widget.LinearLayout optionCancel = dialogView.findViewById(com.example.chatappjava.R.id.option_cancel);
                
                // Show appropriate options based on ownership
                if (isOwnPost) {
                    optionDeletePost.setVisibility(android.view.View.VISIBLE);
                    optionHidePost.setVisibility(android.view.View.GONE);
                } else {
                    optionDeletePost.setVisibility(android.view.View.GONE);
                    optionHidePost.setVisibility(android.view.View.VISIBLE);
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
            
            private void deletePost(com.example.chatappjava.models.Post post) {
                android.app.AlertDialog.Builder confirmBuilder = new android.app.AlertDialog.Builder(HomeActivity.this);
                confirmBuilder.setTitle("Delete Post");
                confirmBuilder.setMessage("Are you sure you want to delete this post? This action cannot be undone.");
                confirmBuilder.setPositiveButton("Delete", (dialog, which) -> {
                    String token = databaseManager.getToken();
                    if (token == null || token.isEmpty()) {
                        android.widget.Toast.makeText(HomeActivity.this, "Please login again", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    apiClient.deletePost(token, post.getId(), new okhttp3.Callback() {
                        @Override
                        public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            runOnUiThread(() -> {
                                android.widget.Toast.makeText(HomeActivity.this, "Failed to delete post: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                        
                        @Override
                        public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            final String responseBody = response.body().string();
                            runOnUiThread(() -> {
                                try {
                                    org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                    if (jsonResponse.getBoolean("success")) {
                                        android.widget.Toast.makeText(HomeActivity.this, "Post deleted successfully", android.widget.Toast.LENGTH_SHORT).show();
                                        // Reload posts
                                        if (currentTab == 3) {
                                            loadPosts(true);
                                        }
                                    } else {
                                        String message = jsonResponse.optString("message", "Failed to delete post");
                                        android.widget.Toast.makeText(HomeActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                } catch (org.json.JSONException e) {
                                    android.widget.Toast.makeText(HomeActivity.this, "Error processing response", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                });
                confirmBuilder.setNegativeButton("Cancel", null);
                confirmBuilder.show();
            }
            
            private void hidePost(com.example.chatappjava.models.Post post) {
                String token = databaseManager.getToken();
                if (token == null || token.isEmpty()) {
                    android.widget.Toast.makeText(HomeActivity.this, "Please login again", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                apiClient.hidePost(token, post.getId(), new okhttp3.Callback() {
                    @Override
                    public void onFailure(okhttp3.Call call, java.io.IOException e) {
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(HomeActivity.this, "Failed to hide post: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    @Override
                    public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                        final String responseBody = response.body().string();
                        runOnUiThread(() -> {
                            try {
                                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                if (jsonResponse.getBoolean("success")) {
                                    android.widget.Toast.makeText(HomeActivity.this, "Post hidden successfully", android.widget.Toast.LENGTH_SHORT).show();
                                    // Remove post from list
                                    int position = postList.indexOf(post);
                                    if (position >= 0) {
                                        postList.remove(position);
                                        postAdapter.notifyItemRemoved(position);
                                    }
                                } else {
                                    String message = jsonResponse.optString("message", "Failed to hide post");
                                    android.widget.Toast.makeText(HomeActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                android.widget.Toast.makeText(HomeActivity.this, "Error processing response", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
            
            @Override
            public void onAuthorClick(com.example.chatappjava.models.Post post) {
                // TODO: Open author profile
            }
            
            @Override
            public void onMediaClick(com.example.chatappjava.models.Post post, int mediaIndex) {
                // TODO: Open media viewer
            }
            
            @Override
            public void onTaggedUsersClick(com.example.chatappjava.models.Post post) {
                showTaggedUsersDialog(post);
            }
            
            private void showTaggedUsersDialog(com.example.chatappjava.models.Post post) {
                java.util.List<com.example.chatappjava.models.User> taggedUsers = post.getTaggedUsers();
                if (taggedUsers == null || taggedUsers.isEmpty()) {
                    return;
                }
                
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(HomeActivity.this);
                android.view.View dialogView = getLayoutInflater().inflate(com.example.chatappjava.R.layout.dialog_tagged_users, null);
                builder.setView(dialogView);
                
                android.app.AlertDialog dialog = builder.create();
                android.view.Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                }
                
                android.widget.TextView tvTitle = dialogView.findViewById(com.example.chatappjava.R.id.tv_dialog_title);
                androidx.recyclerview.widget.RecyclerView rvTaggedUsers = dialogView.findViewById(com.example.chatappjava.R.id.rv_tagged_users);
                android.widget.Button btnClose = dialogView.findViewById(com.example.chatappjava.R.id.btn_close);
                
                if (tvTitle != null) {
                    tvTitle.setText("Tagged People");
                }
                
                androidx.recyclerview.widget.LinearLayoutManager layoutManager = new androidx.recyclerview.widget.LinearLayoutManager(HomeActivity.this);
                rvTaggedUsers.setLayoutManager(layoutManager);
                
                com.example.chatappjava.adapters.TagUserAdapter adapter = new com.example.chatappjava.adapters.TagUserAdapter(
                    HomeActivity.this,
                    taggedUsers,
                    new java.util.HashSet<>(),
                    new com.example.chatappjava.adapters.TagUserAdapter.OnTagUserClickListener() {
                        @Override
                        public void onUserClick(com.example.chatappjava.models.User user, boolean isSelected) {
                            // Open user profile when clicked
                            android.content.Intent intent = new android.content.Intent(HomeActivity.this, com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                            try {
                                intent.putExtra("user", user.toJson().toString());
                                startActivity(intent);
                                dialog.dismiss();
                            } catch (org.json.JSONException e) {
                                android.widget.Toast.makeText(HomeActivity.this, "Error opening profile", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                );
                rvTaggedUsers.setAdapter(adapter);
                
                btnClose.setOnClickListener(v -> dialog.dismiss());
                
                dialog.show();
            }
        });
        
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(chatAdapter);
        
        // Setup SwipeRefreshLayout
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(true); // Enable for all tabs
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refreshCurrentTab();
                }
            });
        }
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
        
        // Load avatar for post profile thumbnail
        if (ivPostProfileThumbnail != null && avatarManager != null) {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                String fullAvatarUrl = avatarUrl;
                if (!avatarUrl.startsWith("http")) {
                    fullAvatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + avatarUrl;
                }
                avatarManager.loadAvatar(fullAvatarUrl, ivPostProfileThumbnail, R.drawable.ic_profile_placeholder);
            } else {
                ivPostProfileThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
    }
    
    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        
        // Reset all tab states
        tvChats.setSelected(false);
        tvGroups.setSelected(false);
        tvCalls.setSelected(false);
        tvPosts.setSelected(false);
        
        // Show title only on Chats tab
        if (tvFriendRequestsTitle != null) {
            tvFriendRequestsTitle.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        }
        // Show entire Friend Requests layout only on Chats tab
        if (llFriendRequests != null) {
            llFriendRequests.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        }
        // Show Create Post Bar only on Posts tab
        if (llCreatePostBar != null) {
            llCreatePostBar.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);
        }
        
        // Hide top bar (user profile, search, settings) on Posts tab
        boolean isPostsTab = (tabIndex == 3);
        if (userProfileSection != null) {
            userProfileSection.setVisibility(isPostsTab ? View.GONE : View.VISIBLE);
        }
        if (ivSearch != null) {
            ivSearch.setVisibility(isPostsTab ? View.GONE : View.VISIBLE);
        }
        if (ivMoreVert != null) {
            ivMoreVert.setVisibility(isPostsTab ? View.GONE : View.VISIBLE);
        }
        
        // Toggle New Group Extended FAB visibility: only on Groups tab
        if (efabNewGroup != null) {
            efabNewGroup.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        }
        
        // Update RecyclerView constraints and padding for different tabs using ConstraintSet
        View chatListCard = findViewById(R.id.chat_list_card);
        RecyclerView recyclerView = findViewById(R.id.rv_chat_list);
        
        // Ensure RecyclerView is visible
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (chatListCard != null) {
            chatListCard.setVisibility(View.VISIBLE);
            
            ViewParent parent = chatListCard.getParent();
            if (parent instanceof ConstraintLayout) {
                ConstraintLayout rootLayout = (ConstraintLayout) parent;
                ConstraintSet constraintSet = new ConstraintSet();
                constraintSet.clone(rootLayout);
                
                // For Posts tab, connect RecyclerView to create post bar (or top if no create post bar)
                // For other tabs, connect RecyclerView to bottom of friend requests
                constraintSet.clear(R.id.chat_list_card, ConstraintSet.TOP);
                if (tabIndex == 3) {
                    // Posts tab: connect to create post bar, or top if create post bar is hidden
                    if (llCreatePostBar != null && llCreatePostBar.getVisibility() == View.VISIBLE) {
                        constraintSet.connect(R.id.chat_list_card, ConstraintSet.TOP, R.id.ll_create_post_bar, ConstraintSet.BOTTOM, 8);
                    } else {
                        // If create post bar is hidden, connect to top of parent
                        constraintSet.connect(R.id.chat_list_card, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 8);
                    }
                } else {
                    // Other tabs: connect to friend requests
                constraintSet.connect(R.id.chat_list_card, ConstraintSet.TOP, R.id.ll_friend_requests, ConstraintSet.BOTTOM, 16);
                }
                constraintSet.clear(R.id.chat_list_card, ConstraintSet.BOTTOM);
                constraintSet.connect(R.id.chat_list_card, ConstraintSet.BOTTOM, R.id.tab_container, ConstraintSet.TOP, 8);
                // Set margins for tabs
                if (chatListCard != null) {
                    android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) chatListCard.getLayoutParams();
                    if (params != null) {
                        params.leftMargin = 0;
                        params.rightMargin = 0;
                        params.topMargin = 0;
                        params.bottomMargin = 0;
                        chatListCard.setLayoutParams(params);
                    }
                }
                // Remove padding bottom
                if (recyclerView != null) {
                    recyclerView.setPadding(0, 0, 0, 0);
                }
                constraintSet.applyTo(rootLayout);
            }
        }
        
        // SwipeRefreshLayout is enabled for all tabs
        
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
            case 3:
                tvPosts.setSelected(true);
                // Switch to post adapter and load posts
                rvChatList.setAdapter(postAdapter);
                loadPosts();
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
            // Stop refresh spinner if no token
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        System.out.println("HomeActivity: Starting to load call history with token: " + token.substring(0, 10) + "...");
        
        apiClient.getCallHistory(token, 50, 1, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    System.out.println("HomeActivity: Failed to load call history: " + e.getMessage());
                    isLoadingCalls = false;
                    // Stop refresh spinner on error
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
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
                    
                    // Stop refresh spinner
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                } else {
                    System.out.println("HomeActivity: Failed to load call history: " + jsonResponse.optString("message", "Unknown error"));
                    // Stop refresh spinner on error
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }
            } else if (statusCode == 401) {
                System.out.println("HomeActivity: Session expired while loading call history");
                databaseManager.clearLoginInfo();
                // Stop refresh spinner
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                redirectToLogin();
            } else {
                System.out.println("HomeActivity: Failed to load call history with status: " + statusCode);
                // Stop refresh spinner on error
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            System.out.println("HomeActivity: Error parsing call history response: " + e.getMessage());
            // Stop refresh spinner on error
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private void loadPosts() {
        loadPosts(false);
    }
    
    /**
     * Refresh current tab based on which tab is active
     */
    private void refreshCurrentTab() {
        switch (currentTab) {
            case 0: // Chats tab
                refreshChats();
                break;
            case 1: // Groups tab
                refreshGroups();
                break;
            case 2: // Calls tab
                refreshCalls();
                break;
            case 3: // Posts tab
                refreshPosts();
                break;
            default:
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                break;
        }
    }
    
    /**
     * Refresh chats list
     */
    private void refreshChats() {
        if (isLoadingChats) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        // Reload chats from server with force refresh avatars (manual refresh)
        loadChats(true);
        
        // Note: loadChats() will handle stopping the refresh when done
    }
    
    /**
     * Refresh groups list
     */
    private void refreshGroups() {
        if (isLoadingChats) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        // Reload chats (which includes groups) from server with force refresh avatars (manual refresh)
        loadChats(true);
        
        // Note: loadChats() will handle stopping the refresh when done
    }
    
    /**
     * Refresh calls list
     */
    private void refreshCalls() {
        if (isLoadingCalls) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        // Reload call history
        loadCallHistory();
        
        // Note: loadCallHistory() will handle stopping the refresh when done
    }
    
    /**
     * Refresh posts by loading new posts and inserting them at the top
     */
    private void refreshPosts() {
        if (isLoadingPosts || postAdapter == null || postList.isEmpty()) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        isLoadingPosts = true;
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            isLoadingPosts = false;
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        String currentUserId = databaseManager.getUserId();
        
        // Get the ID of the first (newest) post to find posts newer than it
        String newestPostId = postList.get(0).getId();
        
        // Call API to get feed posts (page 1 should have newest posts)
        apiClient.getFeedPosts(token, 1, 20, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to refresh posts: " + e.getMessage());
                    isLoadingPosts = false;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
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
                                
                                List<com.example.chatappjava.models.Post> newPosts = new ArrayList<>();
                                for (int i = 0; i < postsArray.length(); i++) {
                                    try {
                                        JSONObject postJson = postsArray.getJSONObject(i);
                                        String postId = postJson.optString("_id", "");
                                        
                                        // Stop if we reach a post that's already in our list
                                        if (postId.equals(newestPostId)) {
                                            break;
                                        }
                                        
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
                                        
                                        com.example.chatappjava.models.Post post = com.example.chatappjava.models.Post.fromJson(postJson);
                                        newPosts.add(post);
                                    } catch (JSONException e) {
                                        Log.e(TAG, "Error parsing post: " + e.getMessage());
                                    }
                                }
                                
                                // Insert new posts at the top
                                if (!newPosts.isEmpty()) {
                                    postList.addAll(0, newPosts);
                                    postAdapter.setPosts(postList);
                                    
                                    // Show notification
                                    String message = newPosts.size() == 1 
                                        ? "1 bài viết mới" 
                                        : newPosts.size() + " bài viết mới";
                                    Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
                                    
                                    // Smooth scroll to show new content
                                    if (rvChatList.getLayoutManager() != null) {
                                        rvChatList.smoothScrollToPosition(0);
                                    }
                                }
                            }
                        } else if (response.code() == 401) {
                            databaseManager.clearLoginInfo();
                            redirectToLogin();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing posts response: " + e.getMessage());
                    } finally {
                        isLoadingPosts = false;
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    }
                });
            }
        });
    }
    
    private void loadPosts(boolean forceReload) {
        if (isLoadingPosts || postAdapter == null) {
            return;
        }
        
        // If posts are already loaded and not forcing reload, don't reload
        if (!forceReload && !postList.isEmpty()) {
            return;
        }
        
        isLoadingPosts = true;
        
        if (forceReload) {
            postList.clear();
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            isLoadingPosts = false;
            return;
        }
        
        String currentUserId = databaseManager.getUserId();
        
        // Call API to get feed posts
        apiClient.getFeedPosts(token, 1, 20, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to load posts: " + e.getMessage());
                    isLoadingPosts = false;
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
                                
                                List<com.example.chatappjava.models.Post> newPosts = new ArrayList<>();
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
                                        
                                        com.example.chatappjava.models.Post post = com.example.chatappjava.models.Post.fromJson(postJson);
                                        newPosts.add(post);
                                    } catch (JSONException e) {
                                        Log.e(TAG, "Error parsing post: " + e.getMessage());
                                    }
                                }
                                
                                if (forceReload) {
                                    postList.clear();
                                }
                                postList.addAll(newPosts);
                                postAdapter.setPosts(postList);
                            }
                        } else if (response.code() == 401) {
                            databaseManager.clearLoginInfo();
                            redirectToLogin();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing posts response: " + e.getMessage());
                    } finally {
                        isLoadingPosts = false;
                    }
                });
            }
        });
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
                        Log.d(TAG, "Member removed from group: " + chatName + " (chatId: " + chatId + ")");
                        
                        // CRITICAL FIX: Delete from local database immediately
                        if (conversationRepository != null && chatId != null) {
                            conversationRepository.deleteConversation(chatId);
                            // Also delete all messages for this chat
                            try {
                                com.example.chatappjava.utils.MessageRepository messageRepo = 
                                    new com.example.chatappjava.utils.MessageRepository(HomeActivity.this);
                                messageRepo.deleteAllMessagesForChat(chatId);
                            } catch (Exception e) {
                                System.out.println("HomeActivity: Error deleting messages: " + e.getMessage());
                            }
                        }
                        
                        // Remove from current list and refresh UI
                        chatList.removeIf(c -> c.getId().equals(chatId));
                        if (chatAdapter != null) {
                            if (currentTab == 1) {
                                applyGroupsFilter();
                            } else {
                                applyChatsFilter();
                            }
                        }
                        
                        Toast.makeText(HomeActivity.this, "You have been removed from " + chatName, Toast.LENGTH_LONG).show();
                        // Refresh chat list from server
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
        
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            // Post was created successfully, refresh posts if on Posts tab
            if (currentTab == 3) {
                loadPosts(true); // Force reload
            }
        }
        
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
        loadChats(false);
    }
    
    private void loadChats(boolean forceRefreshAvatars) {
        if (isLoadingChats) {
            return;
        }
        isLoadingChats = true;
        System.out.println("HomeActivity: Loading chats...");
        
        // First, try to load from local database (works offline)
        loadChatsFromDatabase();
        
        // Only force refresh avatars on manual refresh, not on polling
        if (forceRefreshAvatars) {
            avatarManager.forceRefreshAvatars();
        }
        
        // Then try to load from server if network is available
        if (!isNetworkAvailable()) {
            System.out.println("HomeActivity: No network, showing chats from database");
            isLoadingChats = false;
            // Stop refresh spinner if no network
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            System.out.println("HomeActivity: No token available for loading chats");
            isLoadingChats = false;
            // Stop refresh spinner if no token
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
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
                    // Stop refresh spinner on error
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
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
     * Only updates adapter if chatList is empty (to show data immediately while loading from server)
     */
    private void loadChatsFromDatabase() {
        if (conversationRepository == null) return;
        
        List<Chat> dbChats = conversationRepository.getAllConversations();
        if (!dbChats.isEmpty()) {
            this.chatList = dbChats;
            // Only update adapter if list is empty (to show cached data immediately)
            // Otherwise, wait for server response to avoid double update and flickering
            if (chatAdapter != null && (chatList.isEmpty() || chatAdapter.getItemCount() == 0)) {
                if (currentTab == 1) {
                    applyGroupsFilter();
                } else {
                    applyChatsFilter();
                }
                System.out.println("HomeActivity: Loaded " + dbChats.size() + " chats from database and updated adapter");
            } else {
                System.out.println("HomeActivity: Loaded " + dbChats.size() + " chats from database (skipped adapter update to prevent flickering)");
            }
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
                    java.util.Set<String> serverChatIds = new java.util.HashSet<>();
                    
                    for (int i = 0; i < chatsArray.length(); i++) {
                        org.json.JSONObject chatJson = chatsArray.getJSONObject(i);
                        System.out.println("HomeActivity: Chat " + i + ": " + chatJson.toString());
                        
                        try {
                            Chat chat = Chat.fromJson(chatJson);
                            chats.add(chat);
                            serverChatIds.add(chat.getId());
                            
                            // Save to database for offline access
                            if (conversationRepository != null) {
                                conversationRepository.saveConversation(chat);
                            }
                        } catch (Exception e) {
                            System.out.println("HomeActivity: Error parsing chat " + i + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    // CRITICAL FIX: Delete chats from local database that are no longer on server
                    // This handles cases where chats/groups were deleted but still exist in local SQLite
                    if (conversationRepository != null) {
                        List<Chat> localChats = conversationRepository.getAllConversations();
                        for (Chat localChat : localChats) {
                            if (!serverChatIds.contains(localChat.getId())) {
                                System.out.println("HomeActivity: Deleting chat from local database: " + localChat.getId() + " (" + localChat.getName() + ")");
                            // Delete conversation from local database
                            conversationRepository.deleteConversation(localChat.getId());
                            
                            // Also delete all messages for this chat
                            try {
                                com.example.chatappjava.utils.MessageRepository messageRepo = 
                                    new com.example.chatappjava.utils.MessageRepository(HomeActivity.this);
                                messageRepo.deleteAllMessagesForChat(localChat.getId());
                            } catch (Exception e) {
                                System.out.println("HomeActivity: Error deleting messages for chat " + localChat.getId() + ": " + e.getMessage());
                            }
                            }
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
                    
                    // Stop refresh spinner
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                } else {
                    System.out.println("HomeActivity: Failed to load chats: " + jsonResponse.optString("message", "Unknown error"));
                    // Stop refresh spinner on error
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }
            } else if (statusCode == 401) {
                System.out.println("HomeActivity: Session expired while loading chats");
                databaseManager.clearLoginInfo();
                // Stop refresh spinner
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                // Redirect to login
                Intent intent = new Intent(HomeActivity.this, com.example.chatappjava.ui.theme.LoginActivity.class);
                startActivity(intent);
                finish();
            } else {
                System.out.println("HomeActivity: Failed to load chats with status: " + statusCode);
                // Stop refresh spinner on error
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            System.out.println("HomeActivity: Error parsing chats response: " + e.getMessage());
            // Stop refresh spinner on error
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
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
                                // Group was deleted - remove from local database immediately
                                if (conversationRepository != null) {
                                    conversationRepository.deleteConversation(chat.getId());
                                    // Also delete all messages for this chat
                                    try {
                                        com.example.chatappjava.utils.MessageRepository messageRepo = 
                                            new com.example.chatappjava.utils.MessageRepository(HomeActivity.this);
                                        messageRepo.deleteAllMessagesForChat(chat.getId());
                                    } catch (Exception e) {
                                        System.out.println("HomeActivity: Error deleting messages: " + e.getMessage());
                                    }
                                }
                                String message = jsonResponse.optString("message", "Group deleted successfully");
                                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                            } else {
                                // Normal leave - remove from local database
                                if (conversationRepository != null) {
                                    conversationRepository.deleteConversation(chat.getId());
                                }
                                Toast.makeText(HomeActivity.this, "Left group successfully", Toast.LENGTH_SHORT).show();
                            }
                            
                            // Remove from current list and refresh UI
                            chatList.removeIf(c -> c.getId().equals(chat.getId()));
                            if (chatAdapter != null) {
                                if (currentTab == 1) {
                                    applyGroupsFilter();
                                } else {
                                    applyChatsFilter();
                                }
                            }
                            
                            // Add small delay to ensure server processing is complete, then reload
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                loadChats(); // Refresh chat list from server
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
                        // Remove from local database immediately
                        if (conversationRepository != null) {
                            conversationRepository.deleteConversation(chat.getId());
                            // Also delete all messages for this chat
                            try {
                                com.example.chatappjava.utils.MessageRepository messageRepo = 
                                    new com.example.chatappjava.utils.MessageRepository(HomeActivity.this);
                                messageRepo.deleteAllMessagesForChat(chat.getId());
                            } catch (Exception e) {
                                System.out.println("HomeActivity: Error deleting messages: " + e.getMessage());
                            }
                        }
                        
                        // Remove from current list and refresh UI
                        chatList.removeIf(c -> c.getId().equals(chat.getId()));
                        if (chatAdapter != null) {
                            if (currentTab == 1) {
                                applyGroupsFilter();
                            } else {
                                applyChatsFilter();
                            }
                        }
                        
                        Toast.makeText(HomeActivity.this, "Group deleted successfully", Toast.LENGTH_SHORT).show();
                        loadChats(); // Refresh chat list from server
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
