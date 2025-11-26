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
import android.widget.ImageButton;
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

public class CommentThreadActivity extends AppCompatActivity implements CommentAdapter.OnCommentClickListener {

    private static final String TAG = "CommentThreadActivity";

    // UI Components
    private ImageButton ivBack;
    private TextView tvTitle;
    private CircleImageView ivPostAuthorAvatar;
    private TextView tvPostAuthorName, tvPostContentPreview;
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
        setContentView(R.layout.activity_comment_thread);

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
        loadPostSummary();
        loadComments();
    }

    private void initializeViews() {
        ivBack = findViewById(R.id.iv_back);
        tvTitle = findViewById(R.id.tv_title);
        ivPostAuthorAvatar = findViewById(R.id.iv_post_author_avatar);
        tvPostAuthorName = findViewById(R.id.tv_post_author_name);
        tvPostContentPreview = findViewById(R.id.tv_post_content_preview);
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

        // Add scroll listener for pagination
        rvComments.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // Load more when scrolled to top (for older comments)
                if (!isLoadingMore && hasMoreComments && firstVisibleItemPosition == 0 && totalItemCount > 0) {
                    loadMoreComments();
                }
            }
        });
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        ivSendComment.setOnClickListener(v -> sendComment());

        ivCancelReply.setOnClickListener(v -> cancelReply());

        // Media attachment
        if (ivAttachMedia != null) {
            ivAttachMedia.setOnClickListener(v -> {
                // TODO: Open media picker
                Toast.makeText(this, "Media attachment coming soon", Toast.LENGTH_SHORT).show();
            });
        }

        // Load more comments
        if (tvLoadMoreComments != null) {
            tvLoadMoreComments.setOnClickListener(v -> loadMoreComments());
        }

        // Request focus on input when activity starts
        etCommentInput.requestFocus();
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

    private void loadPostSummary() {
        tvPostAuthorName.setText(post.getAuthorUsername());
        tvPostContentPreview.setText(post.getContent());

        String avatarUrl = post.getAuthorAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
            }
            avatarManager.loadAvatar(avatarUrl, ivPostAuthorAvatar, R.drawable.ic_profile_placeholder);
        } else {
            ivPostAuthorAvatar.setImageResource(R.drawable.ic_profile_placeholder);
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

        // Get post with comments
        apiClient.getPostById(token, post.getId(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Error loading comments: " + e.getMessage());
                    Toast.makeText(CommentThreadActivity.this, "Failed to load comments", Toast.LENGTH_SHORT).show();
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
                                    
                                    if (isInitialLoad) {
                                        commentAdapter.setComments(commentList);
                                    } else {
                                        commentAdapter.addComments(newComments);
                                    }
                                    
                                    // Update pagination state
                                    // TODO: Get total count from backend to determine hasMoreComments
                                    hasMoreComments = newComments.size() >= 20; // Assume more if we got max items
                                    
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
                                Toast.makeText(CommentThreadActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                                if (isInitialLoad) {
                                    llEmptyState.setVisibility(View.VISIBLE);
                                    rvComments.setVisibility(View.GONE);
                                }
                            }
                        } else {
                            Log.e(TAG, "Server error loading comments: " + response.code());
                            Toast.makeText(CommentThreadActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                            if (isInitialLoad) {
                                llEmptyState.setVisibility(View.VISIBLE);
                                rvComments.setVisibility(View.GONE);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing comments JSON", e);
                        Toast.makeText(CommentThreadActivity.this, "Error parsing data", Toast.LENGTH_SHORT).show();
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
        // For now, just hide the button
        if (tvLoadMoreComments != null) {
            tvLoadMoreComments.setVisibility(View.GONE);
        }
        
        isLoadingMore = false;
        // loadComments(false); // Uncomment when backend supports pagination
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

        // If replying, add @username prefix to content
        String finalContent = content;
        String parentCommentId = null;
        if (replyingToComment != null) {
            // Add @username prefix to indicate it's a reply
            finalContent = "@" + replyingToComment.getUsername() + " " + content;
            parentCommentId = replyingToComment.getId();
        }

        // Disable send button while sending
        ivSendComment.setEnabled(false);

        // Call API with parentCommentId if replying
        apiClient.addComment(token, post.getId(), finalContent, parentCommentId, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    ivSendComment.setEnabled(true);
                    Toast.makeText(CommentThreadActivity.this, "Failed to post comment: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                                
                                // If this is a reply, add it to parent comment's replies
                                if (newComment.getParentCommentId() != null && !newComment.getParentCommentId().isEmpty()) {
                                    // Find parent comment in the list
                                    boolean found = false;
                                    for (int i = 0; i < commentList.size(); i++) {
                                        Comment parent = commentList.get(i);
                                        Log.d(TAG, "Checking parent comment id: " + parent.getId() + " vs " + newComment.getParentCommentId());
                                        if (parent.getId() != null && parent.getId().equals(newComment.getParentCommentId())) {
                                                // Add reply to parent's replies list
                                                if (parent.getReplies() == null) {
                                                    parent.setReplies(new ArrayList<>());
                                                }
                                                parent.getReplies().add(newComment);
                                                parent.setRepliesCount(parent.getRepliesCount() + 1);
                                                
                                                // Expand replies to show the new reply immediately
                                                expandedReplies.add(parent.getId());
                                                
                                                // Update the adapter at parent's position
                                                commentAdapter.updateComment(i, parent);
                                                
                                                // Scroll to parent comment to show the new reply
                                                int finalI = i;
                                                rvComments.post(() -> {
                                                    rvComments.smoothScrollToPosition(finalI);
                                                });
                                                
                                                found = true;
                                                break;
                                            }
                                        }
                                        
                                        if (!found) {
                                            // Parent not found in current list, reload comments
                                            loadComments(true);
                                        }
                                    } else {
                                        // Top-level comment: add to main list
                                        commentList.add(newComment);
                                        commentAdapter.addComment(newComment);
                                        
                                        // Scroll to bottom
                                        rvComments.post(() -> {
                                            rvComments.smoothScrollToPosition(commentList.size() - 1);
                                        });
                                    }
                                    
                                    // Clear input and cancel reply if active
                                    etCommentInput.setText("");
                                    cancelReply();
                                    
                                    // Hide empty state and show RecyclerView
                                    llEmptyState.setVisibility(View.GONE);
                                    rvComments.setVisibility(View.VISIBLE);
                                } else {
                                    String message = jsonResponse.optString("message", "Failed to post comment");
                                    Toast.makeText(CommentThreadActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(CommentThreadActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                            }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing comment response", e);
                        Toast.makeText(CommentThreadActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
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
        // Optional: Expand/collapse replies - can be implemented later
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
                        Toast.makeText(CommentThreadActivity.this, "Failed to like comment", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(CommentThreadActivity.this, "Failed to unlike comment", Toast.LENGTH_SHORT).show();
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
        // TODO: Open author profile
        Toast.makeText(this, "Author: " + comment.getUsername(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReactionLongPress(Comment comment, int position, View view) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show reaction picker dialog
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
                        Toast.makeText(CommentThreadActivity.this, "Failed to add reaction", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(CommentThreadActivity.this, "Reacted with " + reactionType, Toast.LENGTH_SHORT).show();
                        } else {
                            // Revert optimistic update
                            comment.setLiked(false);
                            comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
                            commentAdapter.updateComment(position, comment);
                            Toast.makeText(CommentThreadActivity.this, "Failed to add reaction", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
        
        // Position dialog near the view
        Window window = dialog.getWindow();
        if (window != null) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            WindowManager.LayoutParams params = window.getAttributes();
            params.x = location[0] - 100; // Adjust position
            params.y = location[1] - 150;
            window.setAttributes(params);
        }
        
        dialog.show();
    }

    @Override
    public void onReactionCountClick(Comment comment, int position) {
        showReactionUsersDialog(comment);
    }

    @Override
    public void onCommentMenuClick(Comment comment, int position, View view) {
        // Show edit/delete menu
        String currentUserId = databaseManager.getUserId();
        boolean isOwner = comment.getUserId() != null && comment.getUserId().equals(currentUserId);
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        if (isOwner) {
            String[] options = {"Edit", "Delete"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Edit comment
                    showEditCommentDialog(comment, position);
                } else if (which == 1) {
                    // Delete comment
                    showDeleteCommentDialog(comment, position);
                }
            });
        } else {
            // For non-owners, only show report option
            String[] options = {"Report"};
            builder.setItems(options, (dialog, which) -> {
                Toast.makeText(this, "Report feature coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        builder.show();
    }

    @Override
    public void onViewRepliesClick(Comment comment, int position) {
        // Toggle replies visibility
        String commentId = comment.getId();
        if (expandedReplies.contains(commentId)) {
            expandedReplies.remove(commentId);
        } else {
            expandedReplies.add(commentId);
            // TODO: Load replies if not already loaded
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
                // TODO: Call API to update comment
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
                    Intent intent = new Intent(CommentThreadActivity.this, ProfileActivity.class);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(CommentThreadActivity.this, ProfileViewActivity.class);
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
                                    Toast.makeText(CommentThreadActivity.this, "Failed to delete comment", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                runOnUiThread(() -> {
                                    if (response.isSuccessful()) {
                                        commentList.remove(position);
                                        commentAdapter.removeComment(position);
                                        Toast.makeText(CommentThreadActivity.this, "Comment deleted", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(CommentThreadActivity.this, "Failed to delete comment", Toast.LENGTH_SHORT).show();
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

