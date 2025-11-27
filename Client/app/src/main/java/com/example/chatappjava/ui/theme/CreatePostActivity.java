package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class CreatePostActivity extends AppCompatActivity {
    
    private static final String TAG = "CreatePostActivity";
    private static final int MAX_IMAGES = 20;
    
    // UI Components - Top Action Bar
    private ImageButton ivClose;
    private CircleImageView ivUserAvatar;
    private TextView tvUsername;
    private Button btnPost;
    
    // UI Components - Content Area
    private EditText etPostContent;
    private HorizontalScrollView hsvMediaPreview;
    private LinearLayout llMediaContainer;
    private TextView tvImageCount;
    private TextView tvPrivacy;
    private LinearLayout llPrivacySettings;
    private LinearLayout llLocationTags;
    private LinearLayout llLocation;
    private TextView tvLocation;
    private ImageButton ivRemoveLocation;
    private LinearLayout llTaggedUsers;
    private TextView tvTaggedUsers;
    private ImageButton ivRemoveTags;
    
    // UI Components - Bottom Toolbar
    private ImageButton ivPhotoVideo;
    private ImageButton ivTagPeople;
    private ImageButton ivLocation;
    private ImageButton ivEmoji;
    private ImageButton ivMoreOptions;
    
    // Data
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private AvatarManager avatarManager;
    private List<MediaItem> selectedMedia;
    private String selectedLocation;
    private List<String> taggedUserIds;
    private List<User> taggedUsers; // Store User objects for display
    private String privacySetting = "Public"; // Public, Friends, Only Me
    
    // State
    private boolean hasContent = false;
    private ProgressDialog progressDialog;
    private String sharedPostId; // ID of the post being shared
    private Post originalPost; // Original post being shared
    
    // Embedded Post Bar Views
    private View embeddedPostCard;
    private de.hdodenhof.circleimageview.CircleImageView ivEmbeddedAvatar;
    private TextView tvEmbeddedUsername;
    private TextView tvEmbeddedContent;
    
    // Image picker launcher
    private androidx.activity.result.ActivityResultLauncher<Intent> imagePickerLauncher;
    
    private class MediaItem {
        Uri uri;
        String type; // "image" or "video"
        File file;
        
        MediaItem(Uri uri, String type, File file) {
            this.uri = uri;
            this.type = type;
            this.file = file;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);
        
        // Show keyboard automatically
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        
        initializeViews();
        initializeServices();
        setupImagePicker();
        setupClickListeners();
        setupTextWatchers();
        loadUserProfile();
        
        // Handle shared post after views are initialized
        // Use post() to ensure layout is ready
        findViewById(android.R.id.content).post(() -> {
            Log.d(TAG, "About to call handleSharedPost()");
            handleSharedPost();
            Log.d(TAG, "handleSharedPost() completed");
        });
        
        // Auto-focus on text input
        etPostContent.requestFocus();
        
        // Handle back button with discard confirmation
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleClose();
            }
        });
    }
    
    private void initializeViews() {
        // Top Action Bar
        ivClose = findViewById(R.id.iv_close);
        ivUserAvatar = findViewById(R.id.iv_user_avatar);
        tvUsername = findViewById(R.id.tv_username);
        btnPost = findViewById(R.id.btn_post);
        
        // Content Area
        etPostContent = findViewById(R.id.et_post_content);
        hsvMediaPreview = findViewById(R.id.hsv_media_preview);
        llMediaContainer = findViewById(R.id.ll_media_container);
        tvImageCount = findViewById(R.id.tv_image_count);
        tvPrivacy = findViewById(R.id.tv_privacy);
        llPrivacySettings = findViewById(R.id.ll_privacy_settings);
        llLocationTags = findViewById(R.id.ll_location_tags);
        llLocation = findViewById(R.id.ll_location);
        tvLocation = findViewById(R.id.tv_location);
        ivRemoveLocation = findViewById(R.id.iv_remove_location);
        llTaggedUsers = findViewById(R.id.ll_tagged_users);
        tvTaggedUsers = findViewById(R.id.tv_tagged_users);
        ivRemoveTags = findViewById(R.id.iv_remove_tags);
        
        // Bottom Toolbar
        ivPhotoVideo = findViewById(R.id.iv_photo_video);
        ivTagPeople = findViewById(R.id.iv_tag_people);
        ivLocation = findViewById(R.id.iv_location);
        ivEmoji = findViewById(R.id.iv_emoji);
        ivMoreOptions = findViewById(R.id.iv_more_options);
        
        // Embedded Post Bar
        embeddedPostCard = findViewById(R.id.embedded_post_card);
        Log.d(TAG, "initializeViews - embeddedPostCard: " + (embeddedPostCard != null ? "found" : "NULL"));
        if (embeddedPostCard != null) {
            ivEmbeddedAvatar = embeddedPostCard.findViewById(R.id.iv_embedded_avatar);
            tvEmbeddedUsername = embeddedPostCard.findViewById(R.id.tv_embedded_username);
            tvEmbeddedContent = embeddedPostCard.findViewById(R.id.tv_embedded_content);
            Log.d(TAG, "initializeViews - Child views - avatar: " + (ivEmbeddedAvatar != null ? "found" : "null") + 
                  ", username: " + (tvEmbeddedUsername != null ? "found" : "null") + 
                  ", content: " + (tvEmbeddedContent != null ? "found" : "null"));
        } else {
            Log.e(TAG, "ERROR in initializeViews: embeddedPostCard is NULL! Trying alternative method...");
            // Try to find the view in the included layout
            View rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                embeddedPostCard = rootView.findViewById(R.id.embedded_post_card);
                Log.d(TAG, "Alternative find - embeddedPostCard: " + (embeddedPostCard != null ? "found" : "still NULL"));
            }
        }
        
        selectedMedia = new ArrayList<>();
        taggedUserIds = new ArrayList<>();
        taggedUsers = new ArrayList<>();
    }
    
    private void initializeServices() {
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
    }
    
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        // Check if multiple images selected
                        if (data.getClipData() != null) {
                            // Multiple images selected
                            android.content.ClipData clipData = data.getClipData();
                            int count = clipData.getItemCount();
                            int addedCount = 0;
                            int skippedCount = 0;
                            
                            for (int i = 0; i < count; i++) {
                                // Check if we've reached the limit
                                if (selectedMedia.size() >= MAX_IMAGES) {
                                    skippedCount = count - i;
                                    break;
                                }
                                
                                Uri uri = clipData.getItemAt(i).getUri();
                                if (uri != null) {
                                    if (addMediaItem(uri, "image")) {
                                        addedCount++;
                                    }
                                }
                            }
                            
                            // Show appropriate message
                            if (skippedCount > 0) {
                                Toast.makeText(this, 
                                    addedCount + " images added. Maximum " + MAX_IMAGES + " images allowed. " + 
                                    skippedCount + " images skipped.", 
                                    Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, addedCount + " images selected", Toast.LENGTH_SHORT).show();
                            }
                        } else if (data.getData() != null) {
                            // Single image selected
                            Uri uri = data.getData();
                            if (selectedMedia.size() >= MAX_IMAGES) {
                                Toast.makeText(this, "Maximum " + MAX_IMAGES + " images allowed. Please remove some images first.", 
                                    Toast.LENGTH_LONG).show();
                            } else {
                                addMediaItem(uri, "image");
                            }
                        }
                    }
                } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                    Toast.makeText(this, ImagePicker.getError(result.getData()), Toast.LENGTH_SHORT).show();
                }
            }
        );
    }
    
    private void setupClickListeners() {
        // Close button
        ivClose.setOnClickListener(v -> handleClose());
        
        // Post button
        btnPost.setOnClickListener(v -> publishPost());
        
        // Privacy settings
        llPrivacySettings.setOnClickListener(v -> showPrivacyDialog());
        
        // Remove location
        ivRemoveLocation.setOnClickListener(v -> {
            selectedLocation = null;
            updateLocationTagsVisibility();
        });
        
        // Remove tags
        ivRemoveTags.setOnClickListener(v -> {
            taggedUserIds.clear();
            updateLocationTagsVisibility();
        });
        
        // Bottom toolbar buttons
        ivPhotoVideo.setOnClickListener(v -> openImagePicker());
        ivTagPeople.setOnClickListener(v -> openTagPeopleDialog());
        ivLocation.setOnClickListener(v -> openLocationPicker());
        ivEmoji.setOnClickListener(v -> {
            // TODO: Open emoji picker
            Toast.makeText(this, "Emoji picker coming soon", Toast.LENGTH_SHORT).show();
        });
        ivMoreOptions.setOnClickListener(v -> {
            // TODO: Show more options (Poll, GIF, etc.)
            Toast.makeText(this, "More options coming soon", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void setupTextWatchers() {
        etPostContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkContentAndUpdateButton();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void checkContentAndUpdateButton() {
        String content = etPostContent.getText().toString().trim();
        // If sharing a post, button is always enabled (content is optional)
        if (sharedPostId != null && !sharedPostId.isEmpty()) {
            hasContent = true; // Sharing always counts as content
            btnPost.setEnabled(true);
            btnPost.setAlpha(1.0f);
        } else {
            hasContent = !content.isEmpty() || !selectedMedia.isEmpty();
            btnPost.setEnabled(hasContent);
            
            // Update button appearance
            if (hasContent) {
                btnPost.setAlpha(1.0f);
            } else {
                btnPost.setAlpha(0.5f);
            }
        }
    }
    
    private void handleSharedPost() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("shared_post_id")) {
            sharedPostId = intent.getStringExtra("shared_post_id");
            String sharedPostContent = intent.getStringExtra("shared_post_content");
            String sharedPostAuthor = intent.getStringExtra("shared_post_author");
            String sharedPostAuthorId = intent.getStringExtra("shared_post_author_id");
            String sharedPostAuthorAvatar = intent.getStringExtra("shared_post_author_avatar");
            String sharedPostMedia = intent.getStringExtra("shared_post_media");
            
            Log.d(TAG, "Handling shared post - ID: " + sharedPostId + ", Author: " + sharedPostAuthor);
            Log.d(TAG, "Embedded post card view before retry: " + (embeddedPostCard != null ? "found" : "NULL"));
            
            // Always try to find the view again in case it wasn't ready during initializeViews
            embeddedPostCard = findViewById(R.id.embedded_post_card);
            if (embeddedPostCard != null) {
                // Re-initialize child views
                ivEmbeddedAvatar = embeddedPostCard.findViewById(R.id.iv_embedded_avatar);
                tvEmbeddedUsername = embeddedPostCard.findViewById(R.id.tv_embedded_username);
                tvEmbeddedContent = embeddedPostCard.findViewById(R.id.tv_embedded_content);
                Log.d(TAG, "Embedded post card view after retry: FOUND");
                Log.d(TAG, "Child views - avatar: " + (ivEmbeddedAvatar != null ? "found" : "null") + 
                      ", username: " + (tvEmbeddedUsername != null ? "found" : "null") + 
                      ", content: " + (tvEmbeddedContent != null ? "found" : "null"));
            } else {
                Log.e(TAG, "ERROR: Cannot find embeddedPostCard even after retry!");
                return; // Exit early if view is not found
            }
            
            // Create a Post object for the original post
            originalPost = new Post();
            originalPost.setId(sharedPostId);
            originalPost.setAuthorUsername(sharedPostAuthor);
            originalPost.setAuthorId(sharedPostAuthorId);
            originalPost.setAuthorAvatar(sharedPostAuthorAvatar); // Set avatar
            originalPost.setContent(sharedPostContent);
            if (sharedPostMedia != null && !sharedPostMedia.isEmpty()) {
                List<String> mediaUrls = new ArrayList<>();
                mediaUrls.add(sharedPostMedia);
                originalPost.setMediaUrls(mediaUrls);
                originalPost.setMediaType("image");
            }
            
            // Change hint text
            etPostContent.setHint("Write something about this post...");
            
            // Show embedded post bar
            if (embeddedPostCard != null) {
                Log.d(TAG, "Setting embedded post card visibility to VISIBLE");
                
                // Load data first
                loadEmbeddedPostCard(originalPost);
                
                // Set visibility immediately
                embeddedPostCard.setVisibility(View.VISIBLE);
                
                // Force the view to be measured and laid out
                embeddedPostCard.post(() -> {
                    // Request layout on the view and its parent
                    View parent = (View) embeddedPostCard.getParent();
                    if (parent != null) {
                        parent.requestLayout();
                    }
                    embeddedPostCard.requestLayout();
                    
                    // Also request layout on the scroll view to ensure it recalculates
                    androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scroll_content);
                    if (scrollView != null) {
                        scrollView.requestLayout();
                    }
                    
                    // Use ViewTreeObserver to verify layout
                    embeddedPostCard.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            embeddedPostCard.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            
                            int height = embeddedPostCard.getHeight();
                            int width = embeddedPostCard.getWidth();
                            int visibility = embeddedPostCard.getVisibility();
                            
                            Log.d(TAG, "After layout - visibility: " + visibility + ", height: " + height + ", width: " + width);
                            
                            if (height == 0 || width == 0) {
                                Log.e(TAG, "WARNING: View still has zero dimensions! Parent: " + (parent != null ? parent.getClass().getSimpleName() : "null"));
                            } else {
                                Log.d(TAG, "SUCCESS: Embedded post bar is visible with dimensions!");
                            }
                        }
                    });
                });
            } else {
                Log.e(TAG, "ERROR: embeddedPostCard is NULL!");
            }
            
            // Update button state (sharing doesn't require content, but we enable it by default)
            btnPost.setEnabled(true);
            btnPost.setAlpha(1.0f);
        } else {
            Log.d(TAG, "No shared_post_id in intent");
            if (embeddedPostCard != null) {
                embeddedPostCard.setVisibility(View.GONE);
            }
        }
    }
    
    private void loadEmbeddedPostCard(Post post) {
        if (post == null || embeddedPostCard == null) {
            Log.e(TAG, "loadEmbeddedPostCard: post or embeddedPostCard is null");
            return;
        }
        
        Log.d(TAG, "Loading embedded post card for: " + post.getAuthorUsername());
        
        // Set author info
        if (tvEmbeddedUsername != null) {
            String username = post.getAuthorUsername();
            tvEmbeddedUsername.setText(username != null && !username.isEmpty() ? username : "Unknown User");
            Log.d(TAG, "Set username: " + username);
        } else {
            Log.e(TAG, "tvEmbeddedUsername is null!");
        }
        
        // Load avatar
        if (ivEmbeddedAvatar != null) {
            String avatarUrl = post.getAuthorAvatar();
            Log.d(TAG, "Avatar URL: " + avatarUrl);
            
            if (avatarManager != null && avatarUrl != null && !avatarUrl.isEmpty()) {
                if (!avatarUrl.startsWith("http")) {
                    if (!avatarUrl.startsWith("/")) {
                        avatarUrl = "/" + avatarUrl;
                    }
                    avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
                }
                Log.d(TAG, "Loading avatar from: " + avatarUrl);
                avatarManager.loadAvatar(avatarUrl, ivEmbeddedAvatar, R.drawable.ic_profile_placeholder);
            } else {
                Log.d(TAG, "Using placeholder avatar");
                ivEmbeddedAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } else {
            Log.e(TAG, "ivEmbeddedAvatar is null!");
        }
        
        // Set content preview (1-2 lines only)
        if (tvEmbeddedContent != null) {
            String content = post.getContent();
            if (content != null && !content.isEmpty()) {
                tvEmbeddedContent.setText(content);
                tvEmbeddedContent.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set content preview: " + (content.length() > 50 ? content.substring(0, 50) + "..." : content));
            } else {
                tvEmbeddedContent.setText("Shared a post");
                tvEmbeddedContent.setVisibility(View.VISIBLE);
                Log.d(TAG, "Content is empty, showing default text");
            }
        } else {
            Log.e(TAG, "tvEmbeddedContent is null!");
        }
        
        // Ensure all child views are visible
        if (tvEmbeddedUsername != null) {
            tvEmbeddedUsername.setVisibility(View.VISIBLE);
        }
        if (tvEmbeddedContent != null) {
            tvEmbeddedContent.setVisibility(View.VISIBLE);
        }
        if (ivEmbeddedAvatar != null) {
            ivEmbeddedAvatar.setVisibility(View.VISIBLE);
        }
        
        // Make bar clickable to view original post
        embeddedPostCard.setOnClickListener(v -> {
            Log.d(TAG, "Embedded post bar clicked, opening original post");
            Intent intent = new Intent(CreatePostActivity.this, PostDetailActivity.class);
            try {
                intent.putExtra("post", post.toJson().toString());
                startActivity(intent);
            } catch (JSONException e) {
                Log.e(TAG, "Error opening original post: " + e.getMessage());
            }
        });
        
        Log.d(TAG, "Embedded post card loaded successfully");
    }
    
    private void loadUserProfile() {
        String username = databaseManager.getUserName();
        String avatar = databaseManager.getUserAvatar();
        
        tvUsername.setText(username != null && !username.isEmpty() ? username : "User");
        
        if (avatarManager != null && avatar != null && !avatar.isEmpty()) {
            String avatarUrl = avatar;
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + ServerConfig.getServerIp() + 
                           ":" + ServerConfig.getServerPort() + avatarUrl;
            }
            avatarManager.loadAvatar(avatarUrl, ivUserAvatar, R.drawable.ic_profile_placeholder);
        } else {
            ivUserAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }
    
    private void handleClose() {
        if (hasContent) {
            showDiscardDialog();
        } else {
            finish();
        }
    }
    
    private void showDiscardDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Discard Post?")
            .setMessage("Are you sure you want to discard this post? All unsaved changes will be lost.")
            .setPositiveButton("Discard", (dialog, which) -> finish())
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void openImagePicker() {
        // Show dialog to choose single or multiple images
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Images");
        builder.setItems(new String[]{"Select Single Image", "Select Multiple Images"}, (dialog, which) -> {
            if (which == 0) {
                // Single image with crop
                ImagePicker.with(this)
                    .crop()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .createIntent(intent -> {
                        imagePickerLauncher.launch(intent);
                        return null;
                    });
            } else {
                // Multiple images
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                imagePickerLauncher.launch(intent);
            }
        });
        builder.show();
    }
    
    private boolean addMediaItem(Uri uri, String type) {
        // Check limit
        if (selectedMedia.size() >= MAX_IMAGES) {
            return false;
        }
        
        File file = new File(uri.getPath());
        MediaItem item = new MediaItem(uri, type, file);
        selectedMedia.add(item);
        
        // Add media preview item
        View mediaView = LayoutInflater.from(this).inflate(R.layout.item_media_preview, llMediaContainer, false);
        ImageView ivThumbnail = mediaView.findViewById(R.id.iv_media_thumbnail);
        ImageView ivVideoIndicator = mediaView.findViewById(R.id.iv_video_indicator);
        ImageButton ivRemove = mediaView.findViewById(R.id.iv_remove_media);
        
        // Load image
        Picasso.get()
            .load(uri)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .into(ivThumbnail);
        
        // Show video indicator if video
        if ("video".equals(type)) {
            ivVideoIndicator.setVisibility(View.VISIBLE);
        } else {
            ivVideoIndicator.setVisibility(View.GONE);
        }
        
        // Remove button
        ivRemove.setOnClickListener(v -> {
            selectedMedia.remove(item);
            llMediaContainer.removeView(mediaView);
            updateMediaPreviewVisibility();
            checkContentAndUpdateButton();
        });
        
        llMediaContainer.addView(mediaView);
        updateMediaPreviewVisibility();
        checkContentAndUpdateButton();
        return true;
    }
    
    private void updateMediaPreviewVisibility() {
        LinearLayout llMediaPreviewContainer = findViewById(R.id.ll_media_preview_container);
        if (selectedMedia.isEmpty()) {
            if (llMediaPreviewContainer != null) {
                llMediaPreviewContainer.setVisibility(View.GONE);
            } else if (hsvMediaPreview != null) {
                hsvMediaPreview.setVisibility(View.GONE);
            }
        } else {
            if (llMediaPreviewContainer != null) {
                llMediaPreviewContainer.setVisibility(View.VISIBLE);
            } else if (hsvMediaPreview != null) {
                hsvMediaPreview.setVisibility(View.VISIBLE);
            }
            // Update image count
            if (tvImageCount != null) {
                tvImageCount.setText(selectedMedia.size() + " / " + MAX_IMAGES + " images");
                if (selectedMedia.size() >= MAX_IMAGES) {
                    tvImageCount.setTextColor(getResources().getColor(R.color.icon_like));
                } else {
                    tvImageCount.setTextColor(getResources().getColor(R.color.text_secondary));
                }
            }
        }
    }
    
    private void showPrivacyDialog() {
        String[] privacyOptions = {"Public", "Friends", "Only Me"};
        int currentSelection = 0;
        for (int i = 0; i < privacyOptions.length; i++) {
            if (privacyOptions[i].equals(privacySetting)) {
                currentSelection = i;
                break;
            }
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Privacy")
            .setSingleChoiceItems(privacyOptions, currentSelection, (dialog, which) -> {
                privacySetting = privacyOptions[which];
                tvPrivacy.setText(privacySetting);
                dialog.dismiss();
            })
            .show();
    }
    
    private void openTagPeopleDialog() {
        // Create dialog with friend list
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
        java.util.Set<String> selectedUserIds = new java.util.HashSet<>(taggedUserIds);
        
        // Adapter for friend list - use TagUserAdapter for tag selection
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvFriends.setLayoutManager(layoutManager);
        
        // Create TagUserAdapter
        final com.example.chatappjava.adapters.TagUserAdapter[] adapterRef = new com.example.chatappjava.adapters.TagUserAdapter[1];
        adapterRef[0] = new com.example.chatappjava.adapters.TagUserAdapter(
            this,
            filteredFriends,
            selectedUserIds,
            new com.example.chatappjava.adapters.TagUserAdapter.OnTagUserClickListener() {
                @Override
                public void onUserClick(User user, boolean isSelected) {
                    // Selection is already handled in adapter
                    // Just update the adapter if needed
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
            taggedUserIds.clear();
            taggedUsers.clear();
            for (String userId : selectedUserIds) {
                taggedUserIds.add(userId);
                // Find user object
                for (User friend : friends) {
                    if (friend.getId().equals(userId)) {
                        taggedUsers.add(friend);
                        break;
                    }
                }
            }
            updateLocationTagsVisibility();
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
                    Toast.makeText(CreatePostActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(CreatePostActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(CreatePostActivity.this, "Error parsing friends", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        
        dialog.show();
    }
    
    private void openLocationPicker() {
        // TODO: Open location picker
        Toast.makeText(this, "Location picker coming soon", Toast.LENGTH_SHORT).show();
        // For now, set a mock location
        selectedLocation = "Current Location";
        updateLocationTagsVisibility();
    }
    
    private void updateLocationTagsVisibility() {
        boolean hasLocation = selectedLocation != null;
        boolean hasTags = !taggedUserIds.isEmpty();
        
        if (hasLocation || hasTags) {
            llLocationTags.setVisibility(View.VISIBLE);
            llLocation.setVisibility(hasLocation ? View.VISIBLE : View.GONE);
            llTaggedUsers.setVisibility(hasTags ? View.VISIBLE : View.GONE);
            
            if (hasLocation) {
                tvLocation.setText(selectedLocation);
            }
            if (hasTags) {
                if (taggedUsers != null && !taggedUsers.isEmpty()) {
                    StringBuilder names = new StringBuilder();
                    for (int i = 0; i < Math.min(taggedUsers.size(), 3); i++) {
                        if (i > 0) names.append(", ");
                        String displayName = taggedUsers.get(i).getUsername();
                        if (taggedUsers.get(i).getFirstName() != null && !taggedUsers.get(i).getFirstName().isEmpty()) {
                            displayName = taggedUsers.get(i).getFirstName();
                        }
                        names.append(displayName);
                    }
                    if (taggedUsers.size() > 3) {
                        names.append(" and ").append(taggedUsers.size() - 3).append(" more");
                    }
                    tvTaggedUsers.setText(names.toString());
                } else {
                    tvTaggedUsers.setText(taggedUserIds.size() + " people tagged");
                }
            }
        } else {
            llLocationTags.setVisibility(View.GONE);
        }
    }
    
    private void publishPost() {
        if (!hasContent) {
            Toast.makeText(this, "Please add some content", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Disable post button
        btnPost.setEnabled(false);
        
        // Show progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Publishing Post...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        String content = etPostContent.getText().toString().trim();
        String userId = databaseManager.getUserId();
        
        // If there are media files, upload them first
        if (!selectedMedia.isEmpty()) {
            uploadMediaFiles(token, userId, content);
        } else {
            // No media, create post directly
            createPostWithData(token, content, new ArrayList<>());
        }
    }
    
    private void uploadMediaFiles(String token, String userId, String content) {
        List<String> uploadedImageUrls = new ArrayList<>();
        final int[] uploadCount = {0};
        final int totalMedia = selectedMedia.size();
        
        for (int i = 0; i < selectedMedia.size(); i++) {
            MediaItem item = selectedMedia.get(i);
            
            // Convert URI to File
            File imageFile = null;
            try {
                if (item.file != null && item.file.exists()) {
                    imageFile = item.file;
                } else if (item.uri != null) {
                    // Try to get file from URI
                    android.content.ContentResolver resolver = getContentResolver();
                    java.io.InputStream inputStream = resolver.openInputStream(item.uri);
                    if (inputStream != null) {
                        // Create temp file
                        File tempFile = new File(getCacheDir(), "post_image_" + System.currentTimeMillis() + ".jpg");
                        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.close();
                        inputStream.close();
                        imageFile = tempFile;
                    }
                }
                
                if (imageFile == null || !imageFile.exists()) {
                    runOnUiThread(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(CreatePostActivity.this, "Failed to process image file", Toast.LENGTH_SHORT).show();
                        btnPost.setEnabled(true);
                    });
                    return;
                }
                
                final File finalImageFile = imageFile;
                final int index = i;
                
                // Upload image
                apiClient.uploadPostImage(token, finalImageFile, userId, new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            Log.e(TAG, "Failed to upload image: " + e.getMessage());
                            Toast.makeText(CreatePostActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                            btnPost.setEnabled(true);
                        });
                    }
                    
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                Log.d(TAG, "Upload response for image " + index + ": " + jsonResponse.toString());
                                
                                if (jsonResponse.optBoolean("success", false)) {
                                    String imageUrl = jsonResponse.optString("imageUrl", "");
                                    if (!imageUrl.isEmpty()) {
                                        uploadedImageUrls.add(imageUrl);
                                        Log.d(TAG, "Image " + index + " uploaded successfully: " + imageUrl);
                                    } else {
                                        Log.e(TAG, "Image " + index + " upload response missing imageUrl");
                                    }
                                } else {
                                    String message = jsonResponse.optString("message", "Unknown error");
                                    Log.e(TAG, "Image " + index + " upload failed: " + message);
                                }
                                
                                uploadCount[0]++;
                                Log.d(TAG, "Upload progress: " + uploadCount[0] + "/" + totalMedia);
                                
                                // If all uploads are done, create post
                                if (uploadCount[0] == totalMedia) {
                                    Log.d(TAG, "All uploads completed. Total images: " + uploadedImageUrls.size());
                                    runOnUiThread(() -> {
                                        if (uploadedImageUrls.isEmpty()) {
                                            if (progressDialog != null && progressDialog.isShowing()) {
                                                progressDialog.dismiss();
                                            }
                                            Toast.makeText(CreatePostActivity.this, "Failed to upload images", Toast.LENGTH_SHORT).show();
                                            btnPost.setEnabled(true);
                                        } else {
                                            createPostWithData(token, content, uploadedImageUrls);
                                        }
                                    });
                                }
                                
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing upload response: " + e.getMessage() + ", body: " + responseBody);
                                runOnUiThread(() -> {
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                    Toast.makeText(CreatePostActivity.this, "Error processing upload response", Toast.LENGTH_SHORT).show();
                                    btnPost.setEnabled(true);
                                });
                            }
                        } else {
                            Log.e(TAG, "Upload failed with code: " + response.code() + ", body: " + responseBody);
                            runOnUiThread(() -> {
                                if (progressDialog != null && progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }
                                Toast.makeText(CreatePostActivity.this, "Failed to upload image (Code: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                                btnPost.setEnabled(true);
                            });
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error preparing image file: " + e.getMessage());
                runOnUiThread(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(CreatePostActivity.this, "Error processing image", Toast.LENGTH_SHORT).show();
                    btnPost.setEnabled(true);
                });
                return;
            }
        }
    }
    
    private void createPostWithData(String token, String content, List<String> imageUrls) {
        // If sharing a post, include sharedPostId in the post data
        // This will create a new post that references the original post
        try {
            JSONObject postData = new JSONObject();
            
            // Add content if not empty
            if (content != null && !content.trim().isEmpty()) {
                postData.put("content", content);
            }
            
            // Add images array (limit to MAX_IMAGES)
            if (!imageUrls.isEmpty()) {
                JSONArray imagesArray = new JSONArray();
                int maxImages = Math.min(imageUrls.size(), MAX_IMAGES);
                for (int i = 0; i < maxImages; i++) {
                    imagesArray.put(imageUrls.get(i));
                }
                postData.put("images", imagesArray);
                if (imageUrls.size() > MAX_IMAGES) {
                    Log.w(TAG, "Limiting images to " + MAX_IMAGES + " (had " + imageUrls.size() + " images)");
                }
            }
            
            // Add privacy setting (convert to backend format)
            String backendPrivacy = "public";
            if ("Friends".equals(privacySetting)) {
                backendPrivacy = "friends";
            } else if ("Only Me".equals(privacySetting)) {
                backendPrivacy = "only_me";
            }
            postData.put("privacySetting", backendPrivacy);
            
            // Add location if set
            if (selectedLocation != null && !selectedLocation.isEmpty()) {
                postData.put("location", selectedLocation);
            }
            
            // Add tags if any
            if (!taggedUserIds.isEmpty()) {
                JSONArray tagsArray = new JSONArray();
                for (String userId : taggedUserIds) {
                    tagsArray.put(userId);
                }
                postData.put("tags", tagsArray);
            }
            
            // Add sharedPostId if sharing a post
            if (sharedPostId != null && !sharedPostId.isEmpty()) {
                postData.put("sharedPostId", sharedPostId);
                Log.d(TAG, "Including sharedPostId in post data: " + sharedPostId);
            }
            
            Log.d(TAG, "Creating post with data: " + postData.toString());
            Log.d(TAG, "Number of images: " + (imageUrls != null ? imageUrls.size() : 0));
            
            // Create post
            apiClient.createPost(token, postData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Log.e(TAG, "Failed to create post: " + e.getMessage());
                        Toast.makeText(CreatePostActivity.this, "Failed to publish post: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnPost.setEnabled(true);
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // Read response body on background thread BEFORE switching to UI thread
                    String responseBody = null;
                    try {
                        if (response.body() != null) {
                            responseBody = response.body().string();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading response body: " + e.getMessage());
                    }
                    
                    final String finalResponseBody = responseBody;
                    final boolean isSuccessful = response.isSuccessful();
                    final int responseCode = response.code();
                    
                    runOnUiThread(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        
                        if (isSuccessful) {
                            try {
                                if (finalResponseBody != null) {
                                    JSONObject jsonResponse = new JSONObject(finalResponseBody);
                                    
                                    if (jsonResponse.optBoolean("success", false)) {
                                        Toast.makeText(CreatePostActivity.this, "Post published successfully", Toast.LENGTH_SHORT).show();
                                        setResult(RESULT_OK);
                                        finish();
                                    } else {
                                        String message = jsonResponse.optString("message", "Failed to publish post");
                                        Log.e(TAG, "Post creation failed: " + message);
                                        Toast.makeText(CreatePostActivity.this, message, Toast.LENGTH_SHORT).show();
                                        btnPost.setEnabled(true);
                                    }
                                } else {
                                    Toast.makeText(CreatePostActivity.this, "Post published successfully", Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing post response: " + e.getMessage());
                                Toast.makeText(CreatePostActivity.this, "Post published successfully", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            }
                        } else {
                            try {
                                if (finalResponseBody != null) {
                                    JSONObject jsonResponse = new JSONObject(finalResponseBody);
                                    String message = jsonResponse.optString("message", "Failed to publish post");
                                    JSONArray errors = jsonResponse.optJSONArray("errors");
                                    if (errors != null && errors.length() > 0) {
                                        StringBuilder errorMsg = new StringBuilder(message);
                                        errorMsg.append("\n");
                                        for (int i = 0; i < errors.length(); i++) {
                                            JSONObject error = errors.getJSONObject(i);
                                            errorMsg.append(error.optString("msg", ""));
                                            if (i < errors.length() - 1) errorMsg.append("\n");
                                        }
                                        Log.e(TAG, "Post creation validation errors: " + errorMsg.toString());
                                        Toast.makeText(CreatePostActivity.this, errorMsg.toString(), Toast.LENGTH_LONG).show();
                                    } else {
                                        Log.e(TAG, "Post creation failed: " + message + ", body: " + finalResponseBody);
                                        Toast.makeText(CreatePostActivity.this, message, Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Log.e(TAG, "Post creation failed with code: " + responseCode + ", no response body");
                                    Toast.makeText(CreatePostActivity.this, "Failed to publish post (Code: " + responseCode + ")", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing error response: " + e.getMessage() + ", body: " + finalResponseBody);
                                Toast.makeText(CreatePostActivity.this, "Failed to publish post (Code: " + responseCode + ")", Toast.LENGTH_SHORT).show();
                            }
                            btnPost.setEnabled(true);
                        }
                    });
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating post data: " + e.getMessage());
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            Toast.makeText(this, "Error preparing post data", Toast.LENGTH_SHORT).show();
            btnPost.setEnabled(true);
        }
    }
}

