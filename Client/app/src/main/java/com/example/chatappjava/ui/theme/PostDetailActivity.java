package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.CommentAdapter;
import com.example.chatappjava.adapters.ReactionUserAdapter;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Comment;
import com.example.chatappjava.models.Post;
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
    private TextView tvPostUsername, tvPostTimestamp;
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
    private TextView tvLoadMoreComments;

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

        // Get post from intent
        String postJson = getIntent().getStringExtra("post");
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
        initializeServices();
        setupRecyclerView();
        setupClickListeners();
        setupTextWatcher();
        loadUserProfile();
        loadFullPost();
        loadComments();
    }

    private void initializeViews() {
        // Post views
        ivBack = findViewById(R.id.iv_back);
        tvTitle = findViewById(R.id.tv_title);
        ivPostAvatar = findViewById(R.id.iv_post_avatar);
        tvPostUsername = findViewById(R.id.tv_post_username);
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

        ivSendComment.setOnClickListener(v -> sendComment());
        ivCancelReply.setOnClickListener(v -> cancelReply());

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
    }

    private void setupTextWatcher() {
        etCommentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
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

    private void loadFullPost() {
        // Set author info
        tvPostUsername.setText(post.getAuthorUsername());
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

        // Set content
        if (post.getContent() != null && !post.getContent().isEmpty()) {
            tvPostContent.setText(post.getContent());
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
                    Picasso.get()
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(ivPostImage);
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
                            } else {
                                String message = jsonResponse.optString("message", "Unknown error");
                                Toast.makeText(PostDetailActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                                if (isInitialLoad) {
                                    llEmptyState.setVisibility(View.VISIBLE);
                                    rvComments.setVisibility(View.GONE);
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
        // Generate post URL and copy to clipboard
        String postUrl = ServerConfig.getBaseUrl() + "/posts/" + post.getId();
        
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Post Link", postUrl);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
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
        
        // Update share count (optimistic update)
        post.setSharesCount(post.getSharesCount() + 1);
        updateShareCountUI();
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
}

