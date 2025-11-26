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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
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
    
    // UI Components - Top Action Bar
    private ImageButton ivClose;
    private CircleImageView ivUserAvatar;
    private TextView tvUsername;
    private Button btnPost;
    
    // UI Components - Content Area
    private EditText etPostContent;
    private HorizontalScrollView hsvMediaPreview;
    private LinearLayout llMediaContainer;
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
    private String privacySetting = "Public"; // Public, Friends, Only Me
    
    // State
    private boolean hasContent = false;
    private ProgressDialog progressDialog;
    
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
        
        selectedMedia = new ArrayList<>();
        taggedUserIds = new ArrayList<>();
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
                        Uri uri = data.getData();
                        if (uri != null) {
                            addMediaItem(uri, "image");
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
        hasContent = !content.isEmpty() || !selectedMedia.isEmpty();
        btnPost.setEnabled(hasContent);
        
        // Update button appearance
        if (hasContent) {
            btnPost.setAlpha(1.0f);
        } else {
            btnPost.setAlpha(0.5f);
        }
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
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(1080, 1080)
            .createIntent(intent -> {
                imagePickerLauncher.launch(intent);
                return null;
            });
    }
    
    private void addMediaItem(Uri uri, String type) {
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
    }
    
    private void updateMediaPreviewVisibility() {
        if (selectedMedia.isEmpty()) {
            hsvMediaPreview.setVisibility(View.GONE);
        } else {
            hsvMediaPreview.setVisibility(View.VISIBLE);
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
        // TODO: Open friend selection dialog
        Toast.makeText(this, "Tag people feature coming soon", Toast.LENGTH_SHORT).show();
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
                tvTaggedUsers.setText(taggedUserIds.size() + " people tagged");
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
                        if (response.isSuccessful()) {
                            try {
                                String responseBody = response.body().string();
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                
                                if (jsonResponse.optBoolean("success", false)) {
                                    String imageUrl = jsonResponse.optString("imageUrl", "");
                                    if (!imageUrl.isEmpty()) {
                                        uploadedImageUrls.add(imageUrl);
                                    }
                                }
                                
                                uploadCount[0]++;
                                
                                // If all uploads are done, create post
                                if (uploadCount[0] == totalMedia) {
                                    runOnUiThread(() -> {
                                        createPostWithData(token, content, uploadedImageUrls);
                                    });
                                }
                                
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing upload response: " + e.getMessage());
                                runOnUiThread(() -> {
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                    Toast.makeText(CreatePostActivity.this, "Error processing upload response", Toast.LENGTH_SHORT).show();
                                    btnPost.setEnabled(true);
                                });
                            }
                        } else {
                            runOnUiThread(() -> {
                                if (progressDialog != null && progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }
                                Log.e(TAG, "Upload failed with code: " + response.code());
                                Toast.makeText(CreatePostActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
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
        try {
            JSONObject postData = new JSONObject();
            
            // Add content if not empty
            if (content != null && !content.trim().isEmpty()) {
                postData.put("content", content);
            }
            
            // Add images array
            if (!imageUrls.isEmpty()) {
                JSONArray imagesArray = new JSONArray();
                for (String url : imageUrls) {
                    imagesArray.put(url);
                }
                postData.put("images", imagesArray);
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
                    runOnUiThread(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        
                        if (response.isSuccessful()) {
                            try {
                                String responseBody = response.body().string();
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                
                                if (jsonResponse.optBoolean("success", false)) {
                                    Toast.makeText(CreatePostActivity.this, "Post published successfully", Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    String message = jsonResponse.optString("message", "Failed to publish post");
                                    Toast.makeText(CreatePostActivity.this, message, Toast.LENGTH_SHORT).show();
                                    btnPost.setEnabled(true);
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing post response: " + e.getMessage());
                                Toast.makeText(CreatePostActivity.this, "Post published successfully", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            try {
                                String responseBody = response.body().string();
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                String message = jsonResponse.optString("message", "Failed to publish post");
                                Toast.makeText(CreatePostActivity.this, message, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(CreatePostActivity.this, "Failed to publish post (Code: " + response.code() + ")", Toast.LENGTH_SHORT).show();
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

