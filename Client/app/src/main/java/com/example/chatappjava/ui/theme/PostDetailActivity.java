package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.CommentAdapter;
import com.example.chatappjava.adapters.MentionSuggestionAdapter;
import com.example.chatappjava.adapters.ReactionUserAdapter;
import com.example.chatappjava.adapters.TagUserAdapter;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Comment;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.ui.dialogs.ReactionPickerDialog;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class PostDetailActivity extends AppCompatActivity implements CommentAdapter.OnCommentClickListener {

    private static final String TAG = "PostDetailActivity";

    // UI Components - Post
    private ImageButton ivBack;
    private TextView tvTitle;
    private CircleImageView ivPostAvatar;
    private TextView tvPostUsername, tvPostTaggedUsers, tvPostTimestamp;
    private ImageButton ivPostMenu;
    private TextView tvPostContent;
    private FrameLayout flPostMedia;
    private ImageView ivPostImage;
    private RecyclerView rvPostGallery;
    private FrameLayout flPostVideo;
    private ImageView ivPostVideoThumbnail;
    private ImageButton ivPostVideoPlay;
    private TextView tvLikesCount, tvCommentsCount, tvSharesCount;
    private LinearLayout llLikesSummary, llLikeButton, llCommentButton, llShareButton;
    private ImageView ivLikeIcon;
    private TextView tvLikeText;
    private SwipeRefreshLayout swipeRefreshLayout;

    // UI Components - Comments
    private TextView tvCommentsHeader;
    private RecyclerView rvComments;
    private ProgressBar progressLoading;
    private LinearLayout llEmptyState;
    private LinearLayout llReplyIndicator;
    private TextView tvReplyingTo, tvReplyTargetComment;
    private ImageButton ivCancelReply;
    private CircleImageView ivCommentAvatar;
    private EditText etCommentInput;
    private ImageButton ivSendComment;
    private ImageButton ivAttachMedia;
    private ImageButton ivTagPeopleComment;
    
    // Tagged users for comment
    private List<String> taggedUserIdsForComment;
    private List<User> taggedUsersForComment;
    private TextView tvLoadMoreComments;
    
    // @mention autocomplete
    private List<User> friendsListForMention;
    private android.widget.PopupWindow mentionPopup;
    private RecyclerView rvMentionSuggestions;
    private boolean isMentionMode = false;
    private int mentionStartPosition = -1;

    // Data
    private Post post;
    private List<Comment> commentList;
    private CommentAdapter commentAdapter;
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private AvatarManager avatarManager;
    private String currentUserId;
    private String currentUserAvatar;

    // Reply state
    private Comment replyingToComment = null;

    // Pagination
    private boolean isLoadingMore = false;
    private boolean hasMoreComments = true;
    private int currentPage = 1;

    // Expanded replies tracking
    private java.util.Set<String> expandedReplies = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        // Adjust window for keyboard
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Initialize essential services early (needed for deep links)
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        currentUserId = databaseManager.getUserId();
        currentUserAvatar = databaseManager.getUserAvatar();

        // Handle deep link (from URL)
        Intent intent = getIntent();
        android.net.Uri data = intent.getData();
        
        Log.d(TAG, "onCreate - Intent data: " + (data != null ? data.toString() : "null"));
        Log.d(TAG, "onCreate - Intent action: " + intent.getAction());
        
        if (data != null) {
            // Deep link: chatapp://post/:id or https://domain.com/post/:id
            String postId = extractPostIdFromUri(data);
            Log.d(TAG, "Extracted post ID: " + postId);
            if (postId != null && !postId.isEmpty()) {
                // Load post by ID from API
                loadPostById(postId);
                return;
            } else {
                Log.e(TAG, "Failed to extract post ID from deep link: " + data.toString());
                Toast.makeText(this, "Invalid post link", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Get post from intent (normal navigation)
        String postJson = intent.getStringExtra("post");
        if (postJson == null) {
            Toast.makeText(this, "Post data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            post = Post.fromJson(new JSONObject(postJson));
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing post JSON", e);
            Toast.makeText(this, "Error loading post", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        // databaseManager and apiClient already initialized above, but need to initialize commentList
        if (commentList == null) {
            commentList = new ArrayList<>();
        }
        setupRecyclerView();
        setupClickListeners();
        setupTextWatcher();
        loadUserProfile();
        loadFullPost();
        loadComments();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Handle deep link when activity is already open
        android.net.Uri data = intent.getData();
        Log.d(TAG, "onNewIntent - Intent data: " + (data != null ? data.toString() : "null"));
        
        if (data != null) {
            String postId = extractPostIdFromUri(data);
            Log.d(TAG, "onNewIntent - Extracted post ID: " + postId);
            if (postId != null && !postId.isEmpty()) {
                loadPostById(postId);
            } else {
                Log.e(TAG, "Failed to extract post ID from deep link in onNewIntent: " + data.toString());
                Toast.makeText(this, "Invalid post link", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private String extractPostIdFromUri(android.net.Uri uri) {
        if (uri == null) return null;
        
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();
        String port = uri.getPort() != -1 ? String.valueOf(uri.getPort()) : null;
        
        Log.d(TAG, "Deep link - scheme: " + scheme + ", host: " + host + ", port: " + port + ", path: " + path);
        
        // Handle custom scheme: chatapp://post/:id
        if ("chatapp".equals(scheme) && "post".equals(host)) {
            // Path should be /:id
            if (path != null && path.startsWith("/")) {
                String postId = path.substring(1); // Remove leading /
                Log.d(TAG, "Extracted post ID from custom scheme: " + postId);
                return postId;
            }
        }
        
        // Handle HTTP/HTTPS: http://192.168.2.36:49664/post/:id
        if (("http".equals(scheme) || "https".equals(scheme)) && path != null) {
            // Extract post ID from path /post/:id
            if (path.startsWith("/post/")) {
                String[] segments = path.split("/");
                if (segments.length >= 3) {
                    String postId = segments[2]; // post ID is the third segment
                    Log.d(TAG, "Extracted post ID from HTTP/HTTPS: " + postId);
                    return postId;
                }
            }
        }
        
        Log.w(TAG, "Could not extract post ID from URI: " + uri);
        return null;
    }
    
    private void loadPostById(String postId) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login to view post", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize views (databaseManager and apiClient already initialized in onCreate)
        initializeViews();
        // Initialize commentList if not already done
        if (commentList == null) {
            commentList = new ArrayList<>();
        }
        setupRecyclerView();
        setupClickListeners();
        setupTextWatcher();
        loadUserProfile();
        
        // Show loading
        progressLoading.setVisibility(View.VISIBLE);
        rvComments.setVisibility(View.GONE);
        llEmptyState.setVisibility(View.GONE);
        
        apiClient.getPostById(token, postId, new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    llEmptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(PostDetailActivity.this, "Failed to load post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Stop refresh on error
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.getBoolean("success")) {
                            JSONObject postData = jsonResponse.getJSONObject("data").getJSONObject("post");
                            post = Post.fromJson(postData);
                            
                            loadFullPost();
                            loadComments();
                        } else {
                            String message = jsonResponse.optString("message", "Failed to load post");
                            Toast.makeText(PostDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                            llEmptyState.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing post response", e);
                        Toast.makeText(PostDetailActivity.this, "Error loading post", Toast.LENGTH_SHORT).show();
                        llEmptyState.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void initializeViews() {
        // Post views
        ivBack = findViewById(R.id.iv_back);
        tvTitle = findViewById(R.id.tv_title);
        ivPostAvatar = findViewById(R.id.iv_post_avatar);
        tvPostUsername = findViewById(R.id.tv_post_username);
        tvPostTaggedUsers = findViewById(R.id.tv_post_tagged_users);
        tvPostTimestamp = findViewById(R.id.tv_post_timestamp);
        ivPostMenu = findViewById(R.id.iv_post_menu);
        tvPostContent = findViewById(R.id.tv_post_content);
        flPostMedia = findViewById(R.id.fl_post_media);
        ivPostImage = findViewById(R.id.iv_post_image);
        rvPostGallery = findViewById(R.id.rv_post_gallery);
        flPostVideo = findViewById(R.id.fl_post_video);
        ivPostVideoThumbnail = findViewById(R.id.iv_post_video_thumbnail);
        ivPostVideoPlay = findViewById(R.id.iv_post_video_play);
        tvLikesCount = findViewById(R.id.tv_likes_count);
        tvCommentsCount = findViewById(R.id.tv_comments_count);
        tvSharesCount = findViewById(R.id.tv_shares_count);
        llLikesSummary = findViewById(R.id.ll_likes_summary);
        llLikeButton = findViewById(R.id.ll_like_button);
        llCommentButton = findViewById(R.id.ll_comment_button);
        llShareButton = findViewById(R.id.ll_share_button);
        ivLikeIcon = findViewById(R.id.iv_like_icon);
        tvLikeText = findViewById(R.id.tv_like_text);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        // Comments views
        tvCommentsHeader = findViewById(R.id.tv_comments_header);
        rvComments = findViewById(R.id.rv_comments);
        progressLoading = findViewById(R.id.progress_loading);
        llEmptyState = findViewById(R.id.ll_empty_state);
        llReplyIndicator = findViewById(R.id.ll_reply_indicator);
        tvReplyingTo = findViewById(R.id.tv_replying_to);
        tvReplyTargetComment = findViewById(R.id.tv_reply_target_comment);
        ivCancelReply = findViewById(R.id.iv_cancel_reply);
        ivCommentAvatar = findViewById(R.id.iv_comment_avatar);
        etCommentInput = findViewById(R.id.et_comment_input);
        ivSendComment = findViewById(R.id.iv_send_comment);
        ivAttachMedia = findViewById(R.id.iv_attach_media);
        tvLoadMoreComments = findViewById(R.id.tv_load_more_comments);
    }

    private void initializeServices() {
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        currentUserId = databaseManager.getUserId();
        currentUserAvatar = databaseManager.getUserAvatar();
        commentList = new ArrayList<>();
        taggedUserIdsForComment = new ArrayList<>();
        taggedUsersForComment = new ArrayList<>();
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvComments.setLayoutManager(layoutManager);
        commentAdapter = new CommentAdapter(this, this, currentUserId);
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false); // Important for NestedScrollView
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        // Post menu
        ivPostMenu.setOnClickListener(v -> showPostOptionsMenu(post));

        ivSendComment.setOnClickListener(v -> sendComment());
        ivCancelReply.setOnClickListener(v -> cancelReply());
        if (ivTagPeopleComment != null) {
            ivTagPeopleComment.setOnClickListener(v -> openTagPeopleDialogForComment());
        }

        // Post interactions
        llLikeButton.setOnClickListener(v -> toggleLike());
        llCommentButton.setOnClickListener(v -> {
            etCommentInput.requestFocus();
            // Scroll to input bar
        });
        llShareButton.setOnClickListener(v -> {
            showShareDialog(post);
        });

        // Media attachment
        if (ivAttachMedia != null) {
            ivAttachMedia.setOnClickListener(v -> {
                Toast.makeText(this, "Media attachment coming soon", Toast.LENGTH_SHORT).show();
            });
        }

        // Load more comments
        if (tvLoadMoreComments != null) {
            tvLoadMoreComments.setOnClickListener(v -> loadMoreComments());
        }
        
        // Setup SwipeRefreshLayout
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refreshPostAndComments();
                }
            });
        }
    }

    private void setupTextWatcher() {
        etCommentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Check if deleting @ symbol or space after mention
                if (isMentionMode && count > 0 && start < s.length()) {
                    char deletedChar = s.charAt(start);
                    if (deletedChar == '@') {
                        hideMentionPopup();
                    } else if (deletedChar == ' ' && start > mentionStartPosition) {
                        // User deleted space after mention, might want to continue typing
                        // Keep mention mode active
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Check for @ mention trigger
                if (count > 0 && start < s.length()) {
                    char lastChar = s.charAt(start);
                    if (lastChar == '@') {
                        // User typed @, show mention suggestions
                        mentionStartPosition = start;
                        isMentionMode = true;
                        showMentionSuggestions("");
                    } else if (isMentionMode && start > mentionStartPosition) {
                        // User is typing after @, filter suggestions
                        String query = s.subSequence(mentionStartPosition + 1, start + 1).toString();
                        showMentionSuggestions(query);
                    } else if (isMentionMode && (start < mentionStartPosition || 
                            (start == mentionStartPosition && s.length() > 0 && s.charAt(start) != '@'))) {
                        // User moved cursor or deleted @, hide popup
                        hideMentionPopup();
                    }
                } else if (isMentionMode && count == 0 && before > 0) {
                    // User deleted text, check if still in mention mode
                    if (start <= mentionStartPosition || (start < s.length() && s.charAt(start) != '@')) {
                        hideMentionPopup();
                    } else if (start > mentionStartPosition) {
                        String query = s.subSequence(mentionStartPosition + 1, start).toString();
                        showMentionSuggestions(query);
                    }
                }
                boolean hasText = s.length() > 0;
                ivSendComment.setEnabled(hasText);
                ivSendComment.setAlpha(hasText ? 1.0f : 0.5f);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUserProfile() {
        if (currentUserAvatar != null && !currentUserAvatar.isEmpty()) {
            String fullAvatarUrl = currentUserAvatar;
            if (!currentUserAvatar.startsWith("http")) {
                fullAvatarUrl = ServerConfig.getBaseUrl() + currentUserAvatar;
            }
            avatarManager.loadAvatar(fullAvatarUrl, ivCommentAvatar, R.drawable.ic_profile_placeholder);
        } else {
            ivCommentAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    /**
     * Refresh post and comments when user pulls down
     */
    private void refreshPostAndComments() {
        if (post == null || post.getId() == null) {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }
        
        // Reload post to get updated data (likes, shares, comments count)
        loadFullPost();
        
        // Reload comments to get latest comments
        loadComments(true);
    }
    
    private void loadFullPost() {
        // Set author info
        tvPostUsername.setText(post.getAuthorUsername());
        
        // Set tagged users display: "User with A, B, ..."
        List<User> taggedUsers = post.getTaggedUsers();
        if (taggedUsers != null && !taggedUsers.isEmpty()) {
            StringBuilder tagsText = new StringBuilder();
            tagsText.append("with "); // "with" in English
            for (int i = 0; i < taggedUsers.size(); i++) {
                if (i > 0) {
                    if (i == taggedUsers.size() - 1) {
                        tagsText.append(" and "); // "and" in English
                    } else {
                        tagsText.append(", ");
                    }
                }
                String displayName = taggedUsers.get(i).getUsername();
                if (taggedUsers.get(i).getFirstName() != null && !taggedUsers.get(i).getFirstName().isEmpty()) {
                    displayName = taggedUsers.get(i).getFirstName();
                }
                tagsText.append(displayName);
            }
            tvPostTaggedUsers.setText(tagsText.toString());
            tvPostTaggedUsers.setVisibility(View.VISIBLE);
            
            // Make tagged users clickable
            tvPostTaggedUsers.setOnClickListener(v -> showTaggedUsersDialog(post));
        } else {
            tvPostTaggedUsers.setVisibility(View.GONE);
        }
        
        tvPostTimestamp.setText(post.getFormattedTimestamp());

        // Load avatar
        String avatarUrl = post.getAuthorAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (!avatarUrl.startsWith("http")) {
                if (!avatarUrl.startsWith("/")) {
                    avatarUrl = "/" + avatarUrl;
                }
                avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
            }
            avatarManager.loadAvatar(avatarUrl, ivPostAvatar, R.drawable.ic_profile_placeholder);
        } else {
            ivPostAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Set content with mention styling
        if (post.getContent() != null && !post.getContent().isEmpty()) {
            applyMentionStyling(tvPostContent, post.getContent());
            tvPostContent.setVisibility(View.VISIBLE);
        } else {
            tvPostContent.setVisibility(View.GONE);
        }

        // Handle media
        flPostMedia.setVisibility(View.GONE);
        ivPostImage.setVisibility(View.GONE);
        flPostVideo.setVisibility(View.GONE);

        if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            flPostMedia.setVisibility(View.VISIBLE);
            String mediaType = post.getMediaType();

            if ("video".equals(mediaType)) {
                flPostVideo.setVisibility(View.VISIBLE);
                String videoUrl = post.getMediaUrls().get(0);
                if (videoUrl != null && !videoUrl.isEmpty()) {
                    ivPostVideoThumbnail.setImageResource(R.drawable.ic_profile_placeholder);
                }
                ivPostVideoPlay.setOnClickListener(v -> {
                    // TODO: Play video
                    Toast.makeText(this, "Video playback coming soon", Toast.LENGTH_SHORT).show();
                });
            } else if ("image".equals(mediaType) || "gallery".equals(mediaType)) {
                ivPostImage.setVisibility(View.VISIBLE);
                String imageUrl = post.getMediaUrls().get(0);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    // Construct full URL if needed
                    if (!imageUrl.startsWith("http")) {
                        imageUrl = ServerConfig.getBaseUrl() + (imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl);
                    }
                    final String finalImageUrl = imageUrl; // Create final variable for lambda
                    Picasso.get()
                            .load(finalImageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(ivPostImage);
                    
                    // Add click listener to show image in full screen
                    ivPostImage.setOnClickListener(v -> {
                        showImageZoomDialog(finalImageUrl, null);
                    });
                }
            }
        }

        // Set interaction counts
        tvLikesCount.setText(formatCount(post.getLikesCount()));
        tvCommentsCount.setText(formatCount(post.getCommentsCount()) + " comments");
        updateShareCountUI();

        // Update like button state
        if (post.isLiked()) {
            ivLikeIcon.setImageResource(android.R.drawable.btn_star_big_on);
            ivLikeIcon.setColorFilter(getResources().getColor(R.color.icon_like));
            tvLikeText.setText("Liked");
        } else {
            ivLikeIcon.setImageResource(android.R.drawable.btn_star_big_off);
            ivLikeIcon.setColorFilter(getResources().getColor(R.color.icon_like));
            tvLikeText.setText("Like");
        }
    }

    private void showPostOptionsMenu(Post post) {
        if (post == null) return;
        
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
        LinearLayout optionEditPost = dialogView.findViewById(R.id.option_edit_post);
        LinearLayout optionDeletePost = dialogView.findViewById(R.id.option_delete_post);
        LinearLayout optionHidePost = dialogView.findViewById(R.id.option_hide_post);
        LinearLayout optionCancel = dialogView.findViewById(R.id.option_cancel);
        
        // Show appropriate options based on ownership
        if (isOwnPost) {
            optionEditPost.setVisibility(View.VISIBLE);
            optionDeletePost.setVisibility(View.VISIBLE);
            optionHidePost.setVisibility(View.GONE);
        } else {
            optionEditPost.setVisibility(View.GONE);
            optionDeletePost.setVisibility(View.GONE);
            optionHidePost.setVisibility(View.VISIBLE);
        }
        
        // Edit Post
        optionEditPost.setOnClickListener(v -> {
            dialog.dismiss();
            showEditPostDialog(post);
        });
        
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
                        Toast.makeText(PostDetailActivity.this, "Failed to delete post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.getBoolean("success")) {
                                Toast.makeText(PostDetailActivity.this, "Post deleted successfully", Toast.LENGTH_SHORT).show();
                                // Close activity and return to previous screen
                                finish();
                            } else {
                                String message = jsonResponse.optString("message", "Failed to delete post");
                                Toast.makeText(PostDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(PostDetailActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(PostDetailActivity.this, "Failed to hide post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.getBoolean("success")) {
                            Toast.makeText(PostDetailActivity.this, "Post hidden successfully", Toast.LENGTH_SHORT).show();
                            // Close activity and return to previous screen
                            finish();
                        } else {
                            String message = jsonResponse.optString("message", "Failed to hide post");
                            Toast.makeText(PostDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(PostDetailActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void showEditPostDialog(Post post) {
        if (post == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_post, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Set dialog window properties
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Initialize views
        EditText etEditContent = dialogView.findViewById(R.id.et_edit_content);
        TextView tvEditPrivacy = dialogView.findViewById(R.id.tv_edit_privacy);
        LinearLayout llEditPrivacySettings = dialogView.findViewById(R.id.ll_edit_privacy_settings);
        LinearLayout btnSave = dialogView.findViewById(R.id.btn_save);
        LinearLayout btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        // Set current content
        if (etEditContent != null && post.getContent() != null) {
            etEditContent.setText(post.getContent());
        }
        
        // Get current privacy setting from post (need to check backend response)
        // For now, default to "Public" - we'll need to add privacy field to Post model if not exists
        final String[] currentPrivacy = {"Public"}; // Default, should be retrieved from post if available
        if (tvEditPrivacy != null) {
            tvEditPrivacy.setText(currentPrivacy[0]);
        }
        
        // Privacy settings click
        if (llEditPrivacySettings != null) {
            llEditPrivacySettings.setOnClickListener(v -> {
                String[] privacyOptions = {"Public", "Friends", "Only Me"};
                int currentSelection = 0;
                for (int i = 0; i < privacyOptions.length; i++) {
                    if (privacyOptions[i].equals(currentPrivacy[0])) {
                        currentSelection = i;
                        break;
                    }
                }
                
                new AlertDialog.Builder(PostDetailActivity.this)
                    .setTitle("Privacy")
                    .setSingleChoiceItems(privacyOptions, currentSelection, (d, which) -> {
                        currentPrivacy[0] = privacyOptions[which];
                        if (tvEditPrivacy != null) {
                            tvEditPrivacy.setText(currentPrivacy[0]);
                        }
                        d.dismiss();
                    })
                    .show();
            });
        }
        
        // Save button
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String newContent = etEditContent != null ? etEditContent.getText().toString().trim() : "";
                if (newContent.isEmpty()) {
                    Toast.makeText(PostDetailActivity.this, "Content cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                dialog.dismiss();
                updatePost(post, newContent, currentPrivacy[0]);
            });
        }
        
        // Cancel button
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        
        dialog.show();
    }
    
    private void updatePost(Post post, String newContent, String privacySetting) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            JSONObject postData = new JSONObject();
            postData.put("content", newContent);
            
            // Convert privacy setting to backend format
            String backendPrivacy = "public";
            if ("Friends".equals(privacySetting)) {
                backendPrivacy = "friends";
            } else if ("Only Me".equals(privacySetting)) {
                backendPrivacy = "only_me";
            }
            postData.put("privacySetting", backendPrivacy);
            
            apiClient.updatePost(token, post.getId(), postData, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(PostDetailActivity.this, "Failed to update post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.getBoolean("success")) {
                                // Update local post object
                                post.setContent(newContent);
                                
                                // Reload post to get updated data
                                loadFullPost();
                                
                                Toast.makeText(PostDetailActivity.this, "Post updated successfully", Toast.LENGTH_SHORT).show();
                            } else {
                                String message = jsonResponse.optString("message", "Failed to update post");
                                Toast.makeText(PostDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(PostDetailActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException e) {
            Toast.makeText(this, "Error preparing update", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String formatCount(int count) {
        if (count >= 1000000) {
            return String.format("%.1fM", count / 1000000.0);
        } else if (count >= 1000) {
            return String.format("%.1fK", count / 1000.0);
        } else {
            return String.valueOf(count);
        }
    }

    private void toggleLike() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            return;
        }

        // Optimistic update
        boolean newLikedState = !post.isLiked();
        post.setLiked(newLikedState);
        if (newLikedState) {
            post.setLikesCount(post.getLikesCount() + 1);
        } else {
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        }
        updateLikeButton();

        apiClient.toggleLikePost(token, post.getId(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    // Revert optimistic update
                    post.setLiked(!newLikedState);
                    if (newLikedState) {
                        post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
                    } else {
                        post.setLikesCount(post.getLikesCount() + 1);
                    }
                    updateLikeButton();
                    Toast.makeText(PostDetailActivity.this, "Failed to like post", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // Success - UI already updated
            }
        });
    }

    private void updateLikeButton() {
        tvLikesCount.setText(formatCount(post.getLikesCount()));
        if (post.isLiked()) {
            ivLikeIcon.setImageResource(android.R.drawable.btn_star_big_on);
            tvLikeText.setText("Liked");
        } else {
            ivLikeIcon.setImageResource(android.R.drawable.btn_star_big_off);
            tvLikeText.setText("Like");
        }
    }

    private void loadComments() {
        loadComments(true);
    }

    private void loadComments(boolean isInitialLoad) {
        if (isInitialLoad) {
            currentPage = 1;
            hasMoreComments = true;
            progressLoading.setVisibility(View.VISIBLE);
            llEmptyState.setVisibility(View.GONE);
            rvComments.setVisibility(View.GONE);
        }

        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiClient.getPostById(token, post.getId(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Error loading comments: " + e.getMessage());
                    Toast.makeText(PostDetailActivity.this, "Failed to load comments", Toast.LENGTH_SHORT).show();
                    if (isInitialLoad) {
                        progressLoading.setVisibility(View.GONE);
                        llEmptyState.setVisibility(View.VISIBLE);
                        rvComments.setVisibility(View.GONE);
                    }
                    isLoadingMore = false;
                    // Stop refresh on error
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (isInitialLoad) {
                        progressLoading.setVisibility(View.GONE);
                    }
                    isLoadingMore = false;
                    try {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                JSONObject postData = data.getJSONObject("post");
                                
                                // Update post data (for share count, likes count, etc.)
                                Post updatedPost = Post.fromJson(postData);
                                if (updatedPost != null) {
                                    post.setSharesCount(updatedPost.getSharesCount());
                                    post.setLikesCount(updatedPost.getLikesCount());
                                    post.setCommentsCount(updatedPost.getCommentsCount());
                                    post.setLiked(updatedPost.isLiked());
                                    updateShareCountUI();
                                    updateLikeButton();
                                    tvCommentsCount.setText(formatCount(post.getCommentsCount()) + " comments");
                                }

                                // Parse comments
                                JSONArray commentsArray = postData.optJSONArray("comments");
                                if (commentsArray != null) {
                                    List<Comment> newComments = new ArrayList<>();
                                    for (int i = 0; i < commentsArray.length(); i++) {
                                        try {
                                            Comment comment = Comment.fromJson(commentsArray.getJSONObject(i), currentUserId);
                                            newComments.add(comment);
                                        } catch (JSONException e) {
                                            Log.e(TAG, "Error parsing comment: " + e.getMessage());
                                        }
                                    }

                                    if (isInitialLoad) {
                                        commentList.clear();
                                    }
                                    commentList.addAll(newComments);
                                    commentAdapter.setComments(commentList);

                                    // Update pagination state
                                    hasMoreComments = newComments.size() >= 20;

                                    if (commentList.isEmpty()) {
                                        llEmptyState.setVisibility(View.VISIBLE);
                                        rvComments.setVisibility(View.GONE);
                                        if (tvLoadMoreComments != null) tvLoadMoreComments.setVisibility(View.GONE);
                                    } else {
                                        llEmptyState.setVisibility(View.GONE);
                                        rvComments.setVisibility(View.VISIBLE);
                                        if (tvLoadMoreComments != null) {
                                            tvLoadMoreComments.setVisibility(hasMoreComments ? View.VISIBLE : View.GONE);
                                        }
                                    }
                                } else {
                                    if (isInitialLoad) {
                                        llEmptyState.setVisibility(View.VISIBLE);
                                        rvComments.setVisibility(View.GONE);
                                    }
                                    if (tvLoadMoreComments != null) tvLoadMoreComments.setVisibility(View.GONE);
                                }
                                
                                // Stop refresh if this was triggered by pull-to-refresh
                                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            } else {
                                String message = jsonResponse.optString("message", "Unknown error");
                                Toast.makeText(PostDetailActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                                if (isInitialLoad) {
                                    llEmptyState.setVisibility(View.VISIBLE);
                                    rvComments.setVisibility(View.GONE);
                                }
                                // Stop refresh on error
                                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            }
                        } else {
                            Log.e(TAG, "Server error loading comments: " + response.code());
                            Toast.makeText(PostDetailActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                            if (isInitialLoad) {
                                llEmptyState.setVisibility(View.VISIBLE);
                                rvComments.setVisibility(View.GONE);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing comments JSON", e);
                        Toast.makeText(PostDetailActivity.this, "Error parsing data", Toast.LENGTH_SHORT).show();
                        if (isInitialLoad) {
                            llEmptyState.setVisibility(View.VISIBLE);
                            rvComments.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }

    private void loadMoreComments() {
        if (isLoadingMore || !hasMoreComments) {
            return;
        }

        isLoadingMore = true;
        currentPage++;
        // TODO: Implement pagination API call when backend supports it
        if (tvLoadMoreComments != null) {
            tvLoadMoreComments.setVisibility(View.GONE);
        }
        isLoadingMore = false;
    }

    private void sendComment() {
        String content = etCommentInput.getText().toString().trim();
        if (content.isEmpty()) {
            return;
        }

        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalContent = content;
        String parentCommentId = null;
        if (replyingToComment != null) {
            finalContent = "@" + replyingToComment.getUsername() + " " + content;
            parentCommentId = replyingToComment.getId();
        }
        
        // Add tagged users mentions to content only if they're not already in the content
        // (When user types @ and selects, mention is already inserted into EditText)
        if (taggedUsersForComment != null && !taggedUsersForComment.isEmpty()) {
            StringBuilder mentions = new StringBuilder();
            for (User taggedUser : taggedUsersForComment) {
                String mentionText = "@" + taggedUser.getUsername();
                // Check if mention is already in the content
                if (!finalContent.contains(mentionText + " ") && !finalContent.contains(mentionText + "\n") && 
                    !finalContent.endsWith(mentionText)) {
                    mentions.append(mentionText).append(" ");
                }
            }
            if (mentions.length() > 0) {
                finalContent = mentions.toString() + finalContent;
            }
        }

        ivSendComment.setEnabled(false);

        apiClient.addComment(token, post.getId(), finalContent, parentCommentId, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    ivSendComment.setEnabled(true);
                    Toast.makeText(PostDetailActivity.this, "Failed to post comment: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    ivSendComment.setEnabled(true);
                    try {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                JSONObject commentJson = data.getJSONObject("comment");

                                Comment newComment = Comment.fromJson(commentJson, currentUserId);

                                // Debug log
                                Log.d(TAG, "New comment parentCommentId: " + newComment.getParentCommentId());
                                Log.d(TAG, "New comment id: " + newComment.getId());

                                if (newComment.getParentCommentId() != null && !newComment.getParentCommentId().isEmpty()) {
                                    boolean found = false;
                                    for (int i = 0; i < commentList.size(); i++) {
                                        Comment parent = commentList.get(i);
                                        Log.d(TAG, "Checking parent comment id: " + parent.getId() + " vs " + newComment.getParentCommentId());
                                        if (parent.getId() != null && parent.getId().equals(newComment.getParentCommentId())) {
                                            if (parent.getReplies() == null) {
                                                parent.setReplies(new ArrayList<>());
                                            }
                                            parent.getReplies().add(newComment);
                                            parent.setRepliesCount(parent.getRepliesCount() + 1);
                                            expandedReplies.add(parent.getId());
                                            commentAdapter.updateComment(i, parent);
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        loadComments(true);
                                    }
                                } else {
                                    commentList.add(newComment);
                                    commentAdapter.addComment(newComment);
                                }

                                etCommentInput.setText("");
                                cancelReply();
                                
                                // Clear tagged users for next comment
                                if (taggedUsersForComment != null) {
                                    taggedUsersForComment.clear();
                                }
                                if (taggedUserIdsForComment != null) {
                                    taggedUserIdsForComment.clear();
                                }
                                
                                llEmptyState.setVisibility(View.GONE);
                                rvComments.setVisibility(View.VISIBLE);

                                // Update comment count
                                post.setCommentsCount(post.getCommentsCount() + 1);
                                tvCommentsCount.setText(formatCount(post.getCommentsCount()) + " comments");
                            } else {
                                String message = jsonResponse.optString("message", "Failed to post comment");
                                Toast.makeText(PostDetailActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(PostDetailActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing comment response", e);
                        Toast.makeText(PostDetailActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void cancelReply() {
        replyingToComment = null;
        llReplyIndicator.setVisibility(View.GONE);
        etCommentInput.setHint("Write a comment...");
    }

    private void setReplyMode(Comment comment) {
        replyingToComment = comment;
        llReplyIndicator.setVisibility(View.VISIBLE);
        tvReplyTargetComment.setText(comment.getUsername());
        etCommentInput.setHint("Replying to " + comment.getUsername() + "...");
        etCommentInput.requestFocus();
    }

    // CommentAdapter.OnCommentClickListener implementation
    @Override
    public void onCommentClick(Comment comment) {
        // Optional: Expand/collapse replies
    }

    @Override
    public void onLikeClick(Comment comment, int position) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean newLikedState = !comment.isLiked();
        
        // Optimistic update
        comment.setLiked(newLikedState);
        comment.setLikesCount(newLikedState ? comment.getLikesCount() + 1 : comment.getLikesCount() - 1);
        commentAdapter.updateComment(position, comment);

        // Call API
        if (newLikedState) {
            // Add reaction (default to "like")
            apiClient.addReactionToComment(token, post.getId(), comment.getId(), "like", new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        // Revert optimistic update
                        comment.setLiked(false);
                        comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
                        commentAdapter.updateComment(position, comment);
                        Toast.makeText(PostDetailActivity.this, "Failed to like comment", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    // Success - UI already updated
                }
            });
        } else {
            // Remove reaction
            apiClient.removeReactionFromComment(token, post.getId(), comment.getId(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        // Revert optimistic update
                        comment.setLiked(true);
                        comment.setLikesCount(comment.getLikesCount() + 1);
                        commentAdapter.updateComment(position, comment);
                        Toast.makeText(PostDetailActivity.this, "Failed to unlike comment", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    // Success - UI already updated
                }
            });
        }
    }

    @Override
    public void onReplyClick(Comment comment, int position) {
        setReplyMode(comment);
    }

    @Override
    public void onAuthorClick(Comment comment) {
        Toast.makeText(this, "Author: " + comment.getUsername(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReactionLongPress(Comment comment, int position, View view) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            return;
        }

        ReactionPickerDialog dialog = new ReactionPickerDialog(this, reactionType -> {
            // Optimistic update
            comment.setLiked(true);
            comment.setLikesCount(comment.getLikesCount() + 1);
            commentAdapter.updateComment(position, comment);

            // Call API to add reaction
            apiClient.addReactionToComment(token, post.getId(), comment.getId(), reactionType, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        // Revert optimistic update
                        comment.setLiked(false);
                        comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
                        commentAdapter.updateComment(position, comment);
                        Toast.makeText(PostDetailActivity.this, "Failed to add reaction", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(PostDetailActivity.this, "Reacted with " + reactionType, Toast.LENGTH_SHORT).show();
                        } else {
                            // Revert optimistic update
                            comment.setLiked(false);
                            comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
                            commentAdapter.updateComment(position, comment);
                            Toast.makeText(PostDetailActivity.this, "Failed to add reaction", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
        dialog.show();
    }

    @Override
    public void onReactionCountClick(Comment comment, int position) {
        showReactionUsersDialog(comment);
    }

    @Override
    public void onCommentMenuClick(Comment comment, int position, View view) {
        String currentUserId = databaseManager.getUserId();
        boolean isOwner = comment.getUserId() != null && comment.getUserId().equals(currentUserId);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        if (isOwner) {
            String[] options = {"Edit", "Delete"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showEditCommentDialog(comment, position);
                } else if (which == 1) {
                    showDeleteCommentDialog(comment, position);
                }
            });
        } else {
            String[] options = {"Report"};
            builder.setItems(options, (dialog, which) -> {
                Toast.makeText(this, "Report feature coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        builder.show();
    }

    @Override
    public void onViewRepliesClick(Comment comment, int position) {
        String commentId = comment.getId();
        if (expandedReplies.contains(commentId)) {
            expandedReplies.remove(commentId);
        } else {
            expandedReplies.add(commentId);
        }
        commentAdapter.notifyItemChanged(position);
    }

    private void showEditCommentDialog(Comment comment, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Comment");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(comment.getContent());
        input.setMinHeight(120);
        input.setMaxLines(5);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newContent = input.getText().toString().trim();
            if (!newContent.isEmpty()) {
                comment.setContent(newContent);
                commentAdapter.updateComment(position, comment);
                Toast.makeText(this, "Comment updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showReactionUsersDialog(Comment comment) {
        if (comment.getReactions() == null || comment.getReactions().isEmpty()) {
            Toast.makeText(this, "No reactions yet", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reaction_users, null);
        builder.setView(dialogView);

        android.app.AlertDialog dialog = builder.create();
        
        // Set dialog window properties to match other dialogs
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Initialize views
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        RecyclerView rvReactionUsers = dialogView.findViewById(R.id.rv_reaction_users);
        LinearLayout llEmptyState = dialogView.findViewById(R.id.ll_empty_state);

        // Set title with count
        int reactionCount = comment.getReactions().size();
        tvDialogTitle.setText(reactionCount + " Reaction" + (reactionCount != 1 ? "s" : ""));

        // Setup RecyclerView
        rvReactionUsers.setLayoutManager(new LinearLayoutManager(this));
        
        // Create adapter
        ReactionUserAdapter adapter = new ReactionUserAdapter(
            comment.getReactions(),
            avatarManager,
            userId -> {
                // Open user profile
                if (userId.equals(databaseManager.getUserId())) {
                    Intent intent = new Intent(PostDetailActivity.this, ProfileActivity.class);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(PostDetailActivity.this, ProfileViewActivity.class);
                    intent.putExtra("userId", userId);
                    startActivity(intent);
                }
                dialog.dismiss();
            }
        );
        rvReactionUsers.setAdapter(adapter);

        // Show/hide empty state
        if (comment.getReactions().isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            rvReactionUsers.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvReactionUsers.setVisibility(View.VISIBLE);
        }

        dialog.show();
    }

    private void showShareDialog(Post post) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_share_post, null);
        builder.setView(dialogView);
        
        android.app.AlertDialog dialog = builder.create();
        
        // Set dialog window properties
        Window window = dialog.getWindow();
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
        
        // Share to Feed
        optionShareToFeed.setOnClickListener(v -> {
            dialog.dismiss();
            shareToFeed(post);
        });
        
        // Send as Message
        optionSendAsMessage.setOnClickListener(v -> {
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
            dialog.dismiss();
            copyPostLink(post);
        });
        
        // Share to External Apps
        optionShareExternal.setOnClickListener(v -> {
            dialog.dismiss();
            shareToExternalApps(post);
        });
        
        dialog.show();
    }
    
    private void shareToFeed(Post post) {
        // Open CreatePostActivity with shared post data
        Intent intent = new Intent(this, CreatePostActivity.class);
        intent.putExtra("shared_post_id", post.getId());
        intent.putExtra("shared_post_content", post.getContent());
        intent.putExtra("shared_post_author", post.getAuthorUsername());
        intent.putExtra("shared_post_author_id", post.getAuthorId());
        if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            intent.putExtra("shared_post_media", post.getMediaUrls().get(0));
        }
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
        String postUrl = ServerConfig.getBaseUrl() + "/posts/" + post.getId();
        String shareText = post.getContent() != null && !post.getContent().isEmpty() 
            ? post.getContent() + "\n\n" + postUrl 
            : postUrl;
        
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out this post");
        
        startActivity(Intent.createChooser(shareIntent, "Share post via"));
        
        // Reload post to get updated share count from server
        loadFullPost();
    }
    
    private void updateShareCountUI() {
        tvSharesCount.setText(formatCount(post.getSharesCount()) + " shares");
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            // Post was shared successfully, reload post to get updated share count
            loadFullPost();
        }
    }

    private void showDeleteCommentDialog(Comment comment, int position) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Comment")
                .setMessage("Are you sure you want to delete this comment?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    String token = databaseManager.getToken();
                    if (token != null && !token.isEmpty()) {
                        apiClient.deleteComment(token, post.getId(), comment.getId(), new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                runOnUiThread(() -> {
                                    Toast.makeText(PostDetailActivity.this, "Failed to delete comment", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                runOnUiThread(() -> {
                                    if (response.isSuccessful()) {
                                        commentList.remove(position);
                                        commentAdapter.removeComment(position);
                                        post.setCommentsCount(Math.max(0, post.getCommentsCount() - 1));
                                        tvCommentsCount.setText(formatCount(post.getCommentsCount()) + " comments");
                                        Toast.makeText(PostDetailActivity.this, "Comment deleted", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(PostDetailActivity.this, "Failed to delete comment", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        android.widget.Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        if (tvTitle != null) {
            tvTitle.setText("Tagged People");
        }
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvTaggedUsers.setLayoutManager(layoutManager);
        
        com.example.chatappjava.adapters.TagUserAdapter adapter = new com.example.chatappjava.adapters.TagUserAdapter(
            this,
            taggedUsers,
            new java.util.HashSet<>(),
            new com.example.chatappjava.adapters.TagUserAdapter.OnTagUserClickListener() {
                @Override
                public void onUserClick(com.example.chatappjava.models.User user, boolean isSelected) {
                    // Open user profile when clicked
                    Intent intent = new Intent(PostDetailActivity.this, ProfileViewActivity.class);
                    try {
                        intent.putExtra("user", user.toJson().toString());
                        startActivity(intent);
                        dialog.dismiss();
                    } catch (JSONException e) {
                        Toast.makeText(PostDetailActivity.this, "Error opening profile", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );
        rvTaggedUsers.setAdapter(adapter);
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    // Pattern for @mentions
    private static final java.util.regex.Pattern MENTION_PATTERN = java.util.regex.Pattern.compile("@([A-Za-z0-9_]+)");
    
    // Apply mention styling to TextView
    private static void applyMentionStyling(TextView textView, String content) {
        if (content == null) {
            textView.setText("");
            return;
        }
        android.text.SpannableString spannable = new android.text.SpannableString(content);
        
        // Apply mention styling
        java.util.regex.Matcher mentionMatcher = MENTION_PATTERN.matcher(content);
        while (mentionMatcher.find()) {
            int start = mentionMatcher.start();
            int end = mentionMatcher.end();
            
            // Style: blue color and bold
            android.text.style.StyleSpan styleSpan = new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
            android.text.style.ForegroundColorSpan colorSpan = new android.text.style.ForegroundColorSpan(0xFF2D6BB3); // Primary blue color
            
            spannable.setSpan(colorSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(styleSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // Clickable span to open profile
            android.text.style.ClickableSpan clickableSpan = getMentionClickableSpan(content, start, end);
            spannable.setSpan(clickableSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        textView.setText(spannable);
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }
    
    @androidx.annotation.NonNull
    private static android.text.style.ClickableSpan getMentionClickableSpan(String content, int start, int end) {
        final String username = content.substring(start + 1, end);
        return new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@androidx.annotation.NonNull View widget) {
                android.content.Context ctx = widget.getContext();
                android.content.Intent intent = new android.content.Intent(ctx, ProfileViewActivity.class);
                intent.putExtra("username", username);
                ctx.startActivity(intent);
            }
            
            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };
    }
    
    private void openTagPeopleDialogForComment() {
        // Create dialog with friend list (similar to CreatePostActivity)
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tag_people, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        RecyclerView rvFriends = dialogView.findViewById(R.id.rv_friends);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        TextView tvNoFriends = dialogView.findViewById(R.id.tv_no_friends);
        EditText etSearch = dialogView.findViewById(R.id.et_search);
        Button btnDone = dialogView.findViewById(R.id.btn_done);
        
        List<User> friends = new ArrayList<>();
        List<User> filteredFriends = new ArrayList<>();
        java.util.Set<String> selectedUserIds = new java.util.HashSet<>();
        if (taggedUserIdsForComment != null) {
            selectedUserIds.addAll(taggedUserIdsForComment);
        }
        
        // Adapter for friend list
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvFriends.setLayoutManager(layoutManager);
        
        final TagUserAdapter[] adapterRef = new TagUserAdapter[1];
        adapterRef[0] = new TagUserAdapter(
            this,
            filteredFriends,
            selectedUserIds,
            new TagUserAdapter.OnTagUserClickListener() {
                @Override
                public void onUserClick(User user, boolean isSelected) {
                    // Selection is already handled in adapter
                }
            }
        );
        rvFriends.setAdapter(adapterRef[0]);
        
        // Search filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase();
                filteredFriends.clear();
                for (User friend : friends) {
                    String username = friend.getUsername() != null ? friend.getUsername().toLowerCase() : "";
                    String firstName = friend.getFirstName() != null ? friend.getFirstName().toLowerCase() : "";
                    String lastName = friend.getLastName() != null ? friend.getLastName().toLowerCase() : "";
                    if (username.contains(query) || firstName.contains(query) || lastName.contains(query)) {
                        filteredFriends.add(friend);
                    }
                }
                if (adapterRef[0] != null) {
                    adapterRef[0].updateSelection(selectedUserIds);
                }
                tvNoFriends.setVisibility(filteredFriends.isEmpty() ? View.VISIBLE : View.GONE);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Done button
        btnDone.setOnClickListener(v -> {
            // Update tagged users
            taggedUserIdsForComment.clear();
            taggedUsersForComment.clear();
            for (String userId : selectedUserIds) {
                taggedUserIdsForComment.add(userId);
                // Find user object
                for (User friend : friends) {
                    if (friend.getId().equals(userId)) {
                        taggedUsersForComment.add(friend);
                        break;
                    }
                }
            }
            // Update comment input to show tagged users
            if (!taggedUsersForComment.isEmpty()) {
                StringBuilder mentions = new StringBuilder();
                for (User taggedUser : taggedUsersForComment) {
                    mentions.append("@").append(taggedUser.getUsername()).append(" ");
                }
                String currentText = etCommentInput.getText().toString();
                if (!currentText.startsWith(mentions.toString().trim())) {
                    etCommentInput.setText(mentions.toString() + currentText);
                    etCommentInput.setSelection(etCommentInput.getText().length());
                }
            }
            dialog.dismiss();
        });
        
        // Load friends
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        apiClient.authenticatedGet("/api/users/friends", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(PostDetailActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.isSuccessful() && json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray arr = data.getJSONArray("friends");
                            friends.clear();
                            filteredFriends.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                User user = User.fromJson(arr.getJSONObject(i));
                                friends.add(user);
                                filteredFriends.add(user);
                            }
                            if (adapterRef[0] != null) {
                                adapterRef[0].updateSelection(selectedUserIds);
                            }
                            tvNoFriends.setVisibility(filteredFriends.isEmpty() ? View.VISIBLE : View.GONE);
                        } else {
                            Toast.makeText(PostDetailActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(PostDetailActivity.this, "Error parsing friends", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        
        dialog.show();
    }
    
    private void loadFriendsForMention() {
        if (friendsListForMention != null && !friendsListForMention.isEmpty()) {
            return; // Already loaded
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        
        friendsListForMention = new ArrayList<>();
        apiClient.authenticatedGet("/api/users/friends", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Silently fail - mention feature will just not work
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.isSuccessful() && json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray arr = data.getJSONArray("friends");
                            friendsListForMention.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                User user = User.fromJson(arr.getJSONObject(i));
                                friendsListForMention.add(user);
                            }
                        }
                    } catch (JSONException e) {
                        // Silently fail
                    }
                });
            }
        });
    }
    
    private void showMentionSuggestions(String query) {
        if (friendsListForMention == null || friendsListForMention.isEmpty()) {
            loadFriendsForMention();
            return;
        }
        
        // Filter friends based on query
        List<User> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (User friend : friendsListForMention) {
            String username = friend.getUsername() != null ? friend.getUsername().toLowerCase() : "";
            String firstName = friend.getFirstName() != null ? friend.getFirstName().toLowerCase() : "";
            String lastName = friend.getLastName() != null ? friend.getLastName().toLowerCase() : "";
            if (username.contains(lowerQuery) || firstName.contains(lowerQuery) || lastName.contains(lowerQuery)) {
                filtered.add(friend);
            }
        }
        
        if (filtered.isEmpty()) {
            hideMentionPopup();
            return;
        }
        
        // Create or update popup
        if (mentionPopup == null) {
            createMentionPopup();
        }
        
        // Update adapter with filtered list
        if (rvMentionSuggestions != null) {
            com.example.chatappjava.adapters.MentionSuggestionAdapter adapter = 
                new com.example.chatappjava.adapters.MentionSuggestionAdapter(this, filtered, user -> {
                    insertMention(user);
                });
            rvMentionSuggestions.setAdapter(adapter);
        }
        
        // Show popup
        if (mentionPopup != null) {
            if (!mentionPopup.isShowing()) {
                // Position popup above the EditText
                int[] location = new int[2];
                etCommentInput.getLocationOnScreen(location);
                int x = location[0];
                int y = location[1] - (int)(400 * getResources().getDisplayMetrics().density);
                
                // Ensure popup doesn't go off screen
                if (y < 0) {
                    y = location[1] + etCommentInput.getHeight();
                }
                
                mentionPopup.showAtLocation(etCommentInput, android.view.Gravity.NO_GRAVITY, x, y);
            }
        } else {
            // Create popup if not exists
            createMentionPopup();
            // Retry showing after creation
            etCommentInput.post(() -> showMentionSuggestions(query));
        }
    }
    
    private void createMentionPopup() {
        android.view.View popupView = getLayoutInflater().inflate(R.layout.popup_mention_suggestions, null);
        rvMentionSuggestions = popupView.findViewById(R.id.rv_mention_suggestions);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMentionSuggestions.setLayoutManager(layoutManager);
        
        // Calculate popup width (match EditText width)
        etCommentInput.post(() -> {
            int width = etCommentInput.getWidth();
            int height = (int) (400 * getResources().getDisplayMetrics().density); // 400dp in pixels
            
            mentionPopup = new android.widget.PopupWindow(
                popupView,
                width,
                height,
                true // Focusable
            );
            mentionPopup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.dialog_holo_light_frame));
            mentionPopup.setOutsideTouchable(true);
            mentionPopup.setTouchable(true);
            
            // Dismiss when clicking outside
            popupView.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
                    hideMentionPopup();
                    return true;
                }
                return false;
            });
        });
    }
    
    private void hideMentionPopup() {
        if (mentionPopup != null && mentionPopup.isShowing()) {
            mentionPopup.dismiss();
        }
        isMentionMode = false;
        mentionStartPosition = -1;
    }
    
    private void insertMention(User user) {
        if (mentionStartPosition < 0) {
            return;
        }
        
        String username = user.getUsername();
        Editable editable = etCommentInput.getText();
        
        // Replace @query with @username
        int endPosition = etCommentInput.getSelectionStart();
        if (endPosition > mentionStartPosition) {
            // Get current text to find where @mention ends (space or end of text)
            String currentText = editable.toString();
            int actualEnd = endPosition;
            
            // Find the end of the current mention (space, newline, or end of text)
            for (int i = endPosition; i < currentText.length(); i++) {
                char c = currentText.charAt(i);
                if (c == ' ' || c == '\n' || c == '@') {
                    actualEnd = i;
                    break;
                }
                if (i == currentText.length() - 1) {
                    actualEnd = currentText.length();
                }
            }
            
            // Replace the mention
            editable.replace(mentionStartPosition, actualEnd, "@" + username + " ");
            
            // Add to tagged users list
            if (taggedUserIdsForComment == null) {
                taggedUserIdsForComment = new ArrayList<>();
            }
            if (taggedUsersForComment == null) {
                taggedUsersForComment = new ArrayList<>();
            }
            if (!taggedUserIdsForComment.contains(user.getId())) {
                taggedUserIdsForComment.add(user.getId());
                taggedUsersForComment.add(user);
            }
            
            // Move cursor to end of inserted mention
            int newCursorPosition = mentionStartPosition + username.length() + 2; // @ + username + space
            etCommentInput.setSelection(newCursorPosition);
        }
        
        hideMentionPopup();
    }
    
    private void showImageZoomDialog(String imageUrl, String localImageUri) {
        // Create custom dialog
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_zoom);
        
        // Make dialog full screen
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }
        
        // Get views
        com.github.chrisbanes.photoview.PhotoView ivZoomImage = dialog.findViewById(R.id.iv_zoom_image);
        ImageView ivClose = dialog.findViewById(R.id.iv_close);
        
        if (ivZoomImage != null) {
            // Load image with Picasso - prefer local URI if available
            String imageToLoad = (localImageUri != null && !localImageUri.isEmpty()) ? localImageUri : imageUrl;
            
            Picasso.get()
                .load(imageToLoad)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(ivZoomImage);
        }
        
        // Close button click
        if (ivClose != null) {
            ivClose.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Click outside to close
        dialog.findViewById(R.id.dialog_container).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
}

