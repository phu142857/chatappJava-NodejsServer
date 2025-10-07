package com.example.chatappjava.ui.theme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.MessageAdapter;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.Message;
import com.example.chatappjava.models.User;
import com.example.chatappjava.ChatApplication;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.ui.call.RingingActivity;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public abstract class BaseChatActivity extends AppCompatActivity implements MessageAdapter.OnMessageClickListener {

    // Request codes
    private static final int CALL_REQUEST_CODE = 1001;

    // Common UI elements
    protected ImageView ivBack;
    protected CircleImageView ivProfile;
    protected TextView tvChatName, tvStatus;
    protected ImageView ivMore, ivSend, ivAttachment, ivEmoji, ivGallery, ivVideoCall;
    protected EditText etMessage;
    protected RecyclerView rvMessages;
    protected ProgressBar progressBar;
    // Reply UI state
    protected android.view.View replyBar;
    protected TextView tvReplyAuthor;
    protected TextView tvReplyContent;
    protected ImageView ivReplyClose;
    protected String replyingToMessageId;
    protected String replyingToAuthor;
    protected String replyingToContent;
    protected String replyingToImageThumb;

    // Common data
    protected Chat currentChat;
    protected User otherUser;
    protected List<Message> messages;
    protected MessageAdapter messageAdapter;
    protected SharedPreferencesManager sharedPrefsManager;
    protected ApiClient apiClient;
    protected AvatarManager avatarManager;
    protected AlertDialog emojiDialog;
    protected AlertDialog imageSelectDialog;
    protected SocketManager socketManager;
    protected AlertDialog currentDialog;
    // Forwarding support
    protected String pendingForwardContent;
    // Mentions
    protected ListPopupWindow mentionPopup;
    protected java.util.List<String> mentionCandidates = new java.util.ArrayList<>();
    protected ArrayAdapter<String> mentionAdapter;
    protected int mentionStart = -1;

    // Common state
    protected boolean isPolling = false;
    protected Handler pollHandler = new Handler(Looper.getMainLooper());
    protected Runnable pollRunnable;
    protected boolean hasNewMessages = false;
    protected boolean isInitialLoad = true;
    // Pagination state
    private int currentPage = 1;
    private final int pageSize = 20;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;
    // Block state for private chats
    protected boolean isBlockedByMe = false;
    protected boolean hasBlockedMe = false;
    
    // Gallery and camera constants
    private static final int REQUEST_CODE_GALLERY = 1001;
    private static final int REQUEST_CODE_CAMERA = 1002;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1003;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1004;

    // Camera capture state
    private android.net.Uri cameraPhotoUri;
    private java.io.File cameraPhotoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());

        initViews();
        initData();
        setupSocketManager();
        setupClickListeners();
        setupRecyclerView();
        loadChatData();
        startPolling();
        setupMentionSupport();
    }

    // Abstract methods to be implemented by subclasses
    protected abstract int getLayoutResource();
    protected abstract void loadChatData();
    protected abstract void updateUI();
    protected abstract void showChatOptions();
    protected abstract void handleSendMessage();
    
    // Video call handling - can be overridden by subclasses
    protected void handleVideoCall() {
        if (currentChat == null) {
            Toast.makeText(this, "Chat not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show call type selection dialog
        showCallTypeSelectionDialog();
    }
    
    private void showCallTypeSelectionDialog() {
        String[] options = {"Video Call", "Audio Call"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Call Type")
                .setItems(options, (dialog, which) -> {
                    String callType = (which == 0) ? "video" : "audio";
                    initiateCall(callType);
                })
                .setNegativeButton("Cancel", null);
        AlertDialog dlg = builder.create();
        if (dlg.getWindow() != null) {
            android.view.Window w = dlg.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dlg.show();
    }
    
    private void initiateCall(String callType) {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Initiating " + callType + " call...", Toast.LENGTH_SHORT).show();
        
        apiClient.initiateCall(token, currentChat.getId(), callType, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(BaseChatActivity.this, "Failed to initiate call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            String responseBody = response.body().string();
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            
                            if (jsonResponse.optBoolean("success", false)) {
                                org.json.JSONObject callData = jsonResponse.getJSONObject("data");
                                String callId = callData.getString("callId");
                                
                                // Open ringing activity for outgoing call
                                Intent intent = new Intent(BaseChatActivity.this, RingingActivity.class);
                                intent.putExtra("chat", currentChat.toJson().toString());
                                intent.putExtra("caller", getCurrentUser().toJson().toString());
                                intent.putExtra("callId", callId);
                                intent.putExtra("callType", callType);
                                intent.putExtra("isIncomingCall", false);
                                
                                startActivityForResult(intent, CALL_REQUEST_CODE);
                            } else {
                                String errorMsg = jsonResponse.optString("message", "Failed to initiate call");
                                Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, "Error processing call response", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Failed to initiate call: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private User getCurrentUser() {
        // Create a simple user object for current user
        User currentUser = new User();
        currentUser.setId(sharedPrefsManager.getUserId());
        currentUser.setUsername(sharedPrefsManager.getUserName());
        currentUser.setAvatar(sharedPrefsManager.getUserAvatar());
        return currentUser;
    }
    
    // Gallery and attachment handling
    protected void showAttachmentOptions() {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_image_select, null);
        
        // Get views
        LinearLayout cameraOption = dialogView.findViewById(R.id.option_camera);
        LinearLayout galleryOption = dialogView.findViewById(R.id.option_gallery);
        LinearLayout cancelButton = dialogView.findViewById(R.id.btn_cancel);
        
        // Set click listeners
        cameraOption.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                openCamera();
            } else {
                requestCameraPermission();
            }
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        galleryOption.setOnClickListener(v -> {
            openGallery();
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        cancelButton.setOnClickListener(v -> {
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);
        
        imageSelectDialog = builder.create();
        
        // Set transparent background to avoid white area issues
        if (imageSelectDialog.getWindow() != null) {
            android.view.Window w = imageSelectDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        imageSelectDialog.show();
    }
    
    protected boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    protected void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
    }
    
    protected void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
            return;
        }
        
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Prefer external app-specific pictures dir; fallback to cache
            java.io.File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir == null) picturesDir = getCacheDir();
            cameraPhotoFile = new java.io.File(picturesDir, "chat_photo_" + System.currentTimeMillis() + ".jpg");
            if (!cameraPhotoFile.exists()) {
                cameraPhotoFile.createNewFile();
            }
            cameraPhotoUri = FileProvider.getUriForFile(
                this,
                "com.example.chatappjava.fileprovider",
                cameraPhotoFile
            );
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(cameraIntent, REQUEST_CODE_CAMERA);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Cannot prepare camera file", Toast.LENGTH_SHORT).show();
        }
    }
    
    @SuppressLint("IntentReset")
    protected void openGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES};
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_STORAGE_PERMISSION);
            return;
        }
        
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, REQUEST_CODE_GALLERY);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_GALLERY && data != null) {
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    handleSelectedImage(selectedImageUri);
                }
            } else if (requestCode == REQUEST_CODE_CAMERA) {
                // With EXTRA_OUTPUT, most cameras return null data; use saved file/uri
                if (cameraPhotoFile != null && cameraPhotoFile.exists()) {
                    uploadImageToServer(cameraPhotoFile, cameraPhotoUri);
                } else if (data != null && data.getExtras() != null && data.getExtras().get("data") instanceof android.graphics.Bitmap) {
                    // Fallback for devices returning thumbnail bitmap if EXTRA_OUTPUT ignored
                    android.graphics.Bitmap cameraBitmap = (android.graphics.Bitmap) data.getExtras().get("data");
                    if (cameraBitmap != null) {
                        handleSelectedBitmap(cameraBitmap);
                    }
                } else {
                    Toast.makeText(this, "No photo captured", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission required to take photos", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Storage permission required to select images", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    protected void handleSelectedImage(Uri imageUri) {
        if (imageUri == null) {
            Toast.makeText(this, "Cannot load image", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading message
        Toast.makeText(this, "Uploading image to server...", Toast.LENGTH_SHORT).show();
        
        try {
            // Convert URI to File
            java.io.File imageFile = new java.io.File(Objects.requireNonNull(imageUri.getPath()));
            if (!imageFile.exists()) {
                // Try to get file from content resolver
                android.content.ContentResolver contentResolver = getContentResolver();
                java.io.InputStream inputStream = contentResolver.openInputStream(imageUri);
                if (inputStream != null) {
                    // Create temporary file
                    imageFile = new java.io.File(getCacheDir(), "temp_gallery_image_" + System.currentTimeMillis() + ".jpg");
                    java.io.FileOutputStream outputStream = new java.io.FileOutputStream(imageFile);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    inputStream.close();
                    outputStream.close();
                }
            }
            
            if (imageFile.exists()) {
                // Pass original gallery Uri so UI can display instantly without flicker
                uploadImageToServer(imageFile, imageUri);
            } else {
                Toast.makeText(this, "Cannot read image file", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }
    
    protected void handleSelectedBitmap(android.graphics.Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "Cannot load image", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading message
        Toast.makeText(this, "Uploading image to server...", Toast.LENGTH_SHORT).show();
        
        // Convert Bitmap to a temporary file for upload
        try {
            // Create a temporary file to store the bitmap
            java.io.File tempFile = new java.io.File(getCacheDir(), "temp_camera_image_" + System.currentTimeMillis() + ".jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            
            // Store the temp file URI for zoom functionality
            android.net.Uri tempUri = android.net.Uri.fromFile(tempFile);
            
            // Upload to server with temp URI for zoom
            uploadImageToServer(tempFile, tempUri);
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }
    
    protected void uploadImageToServer(java.io.File imageFile) {
        uploadImageToServer(imageFile, null);
    }
    
    protected void uploadImageToServer(java.io.File imageFile, android.net.Uri localUri) {
        if (currentChat == null) {
            Toast.makeText(this, "Chat not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Upload image to server
        apiClient.uploadChatImage(token, imageFile, currentChat.getId(), new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                android.util.Log.d("BaseChatActivity", "Upload response: " + response.code() + " - " + responseBody);
                
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                // Get image URL from server response
                                String imageUrl = jsonResponse.optString("imageUrl", "");
                                if (!imageUrl.isEmpty()) {
                                    // Send image message with local URI for zoom
                                    sendImageMessage(imageUrl, localUri);
                                } else {
                                    Toast.makeText(BaseChatActivity.this, "Failed to receive image URL from server", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                String errorMsg = jsonResponse.optString("message", "Upload failed");
                                Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (org.json.JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, "Error processing server response", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                android.util.Log.e("BaseChatActivity", "Upload failed: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    protected void sendImageMessage(String imageUrl, android.net.Uri localUri) {
        if (currentChat == null) return;
        
        String token = sharedPrefsManager.getToken();
        String senderId = sharedPrefsManager.getUserId();
        
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create message object
        Message message = new Message();
        message.setContent(imageUrl);
        message.setSenderId(senderId);
        message.setChatId(currentChat.getId());
        message.setType("image");
        message.setChatType(currentChat.isGroupChat() ? "group" : "private");
        message.setTimestamp(System.currentTimeMillis());
        String clientNonce = java.util.UUID.randomUUID().toString();
        message.setClientNonce(clientNonce);
        // Assign a temporary local id to dedupe when server echoes back
        try { message.setId("local-" + message.getTimestamp()); } catch (Exception ignored) {}
        
        // Store local URI for zoom functionality if available
        if (localUri != null) {
            // Store local URI in message object for zoom
            message.setLocalImageUri(localUri.toString());
        }
        
        // Add to local list immediately for better UX
        message.setLocalSignature(buildLocalSignature(message));
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        // Always scroll to bottom when user sends an image
        forceScrollToBottom();
        
        // Send to server
        try {
            // Create JSON object that matches server expectations
            org.json.JSONObject messageJson = new org.json.JSONObject();
            messageJson.put("chatId", currentChat.getId());
            messageJson.put("type", "image");
            messageJson.put("timestamp", System.currentTimeMillis());
            messageJson.put("clientNonce", clientNonce);
            if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
                messageJson.put("replyTo", replyingToMessageId);
            }
            
            // Create attachments array for image message
            org.json.JSONArray attachments = new org.json.JSONArray();
            org.json.JSONObject attachment = new org.json.JSONObject();
            attachment.put("filename", imageUrl.substring(imageUrl.lastIndexOf("/") + 1));
            attachment.put("originalName", imageUrl.substring(imageUrl.lastIndexOf("/") + 1));
            attachment.put("mimeType", "image/jpeg");
            attachment.put("size", 0); // We don't have size info from URL

            // Convert relative path to full URL for server validation
            String fullImageUrl = imageUrl;
            if (!imageUrl.startsWith("http")) {
                fullImageUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() +
                              ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + imageUrl;
            }
            attachment.put("url", fullImageUrl);
            attachments.put(attachment);
            messageJson.put("attachments", attachments);
            
            android.util.Log.d("BaseChatActivity", "Sending image message: " + messageJson.toString());
            
            apiClient.sendMessage(token, messageJson, new okhttp3.Callback() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String responseBody = response.body().string();
                    android.util.Log.d("BaseChatActivity", "Send image message response: " + response.code() + " - " + responseBody);
                    
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    android.util.Log.d("BaseChatActivity", "Image message sent successfully");
                                    Toast.makeText(BaseChatActivity.this, "Image sent successfully", Toast.LENGTH_SHORT).show();
                                    clearReplyState();
                                } else {
                                    // Remove from local list if failed
                                    messages.remove(message);
                                    messageAdapter.notifyDataSetChanged();
                                    String errorMsg = jsonResponse.optString("message", "Cannot send image");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                e.printStackTrace();
                                // Remove from local list if failed
                                messages.remove(message);
                                messageAdapter.notifyDataSetChanged();
                                Toast.makeText(BaseChatActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // Remove from local list if failed
                            messages.remove(message);
                            messageAdapter.notifyDataSetChanged();
                            Toast.makeText(BaseChatActivity.this, "Cannot send image: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send image message failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        // Remove from local list if failed
                        messages.remove(message);
                        messageAdapter.notifyDataSetChanged();
                        Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing message", Toast.LENGTH_SHORT).show();
        }
    }

    // Emoji picker handling
    protected void showEmojiPicker() {
        // Inflate custom dialog layout (layout defines design completely)
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_emoji_picker, null);

        // Wire up static emoji clicks declared via android:onClick in XML
        dialogView.findViewsWithText(new java.util.ArrayList<>(), "", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);

        // Cancel button
        LinearLayout cancelButton = dialogView.findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(v -> {
            if (emojiDialog != null) {
                emojiDialog.dismiss();
            }
        });


        emojiDialog = builder.create();
        if (emojiDialog.getWindow() != null) {
            android.view.Window w = emojiDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        emojiDialog.show();
    }

    public void onEmojiClicked(View v) {
        if (!(v instanceof TextView)) return;
        CharSequence emoji = ((TextView) v).getText();
        if (emoji != null) insertEmoji(emoji.toString());
        // Do not dismiss here to allow selecting multiple emojis
    }
    
    protected void insertEmoji(String emoji) {
        if (etMessage != null) {
            String currentText = etMessage.getText().toString();
            int cursorPosition = etMessage.getSelectionStart();
            String newText = currentText.substring(0, cursorPosition) + emoji + currentText.substring(cursorPosition);
            etMessage.setText(newText);
            etMessage.setSelection(cursorPosition + emoji.length());
        }
    }

    @SuppressLint("CutPasteId")
    protected void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivProfile = findViewById(R.id.iv_profile);
        tvChatName = findViewById(R.id.tv_chat_name);
        tvStatus = findViewById(R.id.tv_status);
        ivMore = findViewById(R.id.iv_more);
        ivVideoCall = findViewById(R.id.iv_video_call);
        ivSend = findViewById(R.id.iv_send);
        ivAttachment = findViewById(R.id.iv_attach);
        ivEmoji = findViewById(R.id.iv_sticker);
        ivGallery = findViewById(R.id.iv_attach);
        etMessage = findViewById(R.id.et_message);
        rvMessages = findViewById(R.id.rv_messages);
        progressBar = findViewById(R.id.progress_bar);
        replyBar = findViewById(R.id.reply_bar);
        tvReplyAuthor = findViewById(R.id.tv_reply_author);
        tvReplyContent = findViewById(R.id.tv_reply_content);
        ivReplyClose = findViewById(R.id.iv_reply_close);
        
        // Log view initialization
        android.util.Log.d("BaseChatActivity", "Views initialized - ivBack: " + (ivBack != null) + 
                          ", ivProfile: " + (ivProfile != null) + 
                          ", tvChatName: " + (tvChatName != null));
    }

    protected void initData() {
        sharedPrefsManager = new SharedPreferencesManager(this);
        apiClient = new ApiClient();
        avatarManager = AvatarManager.getInstance(this);
        socketManager = ChatApplication.getInstance().getSocketManager();
        messages = new ArrayList<>();

        // Initialize chat data from intent
        Intent intent = getIntent();
        if (intent.hasExtra("chat")) {
            try {
                String chatJson = intent.getStringExtra("chat");
                JSONObject chatJsonObj = new JSONObject(chatJson);
                currentChat = Chat.fromJson(chatJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading chat data", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Initialize other user data for private chats
        if (intent.hasExtra("user")) {
            try {
                String userJson = intent.getStringExtra("user");
                JSONObject userJsonObj = new JSONObject(userJson);
                otherUser = User.fromJson(userJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Forwarding: capture pending content to send when chat is ready
        if (intent.hasExtra("forward_content")) {
            pendingForwardContent = intent.getStringExtra("forward_content");
        }

        // Preload group members for mentions
        if (currentChat != null && currentChat.isGroupChat()) {
            fetchGroupMembersForMentions();
        }
    }
    
    protected void setupSocketManager() {
        // SocketManager is already setup in Application class
        if (socketManager != null) {
            socketManager.setMessageListener(new com.example.chatappjava.network.SocketManager.MessageListener() {
                @Override
                public void onPrivateMessage(org.json.JSONObject messageJson) {
                    runOnUiThread(() -> handleIncomingMessage(messageJson));
                }

                @Override
                public void onGroupMessage(org.json.JSONObject messageJson) {
                    runOnUiThread(() -> handleIncomingMessage(messageJson));
                }

                @Override
                public void onMessageEdited(org.json.JSONObject messageJson) {
                    runOnUiThread(() -> handleEditedMessage(messageJson));
                }

                @Override
                public void onMessageDeleted(org.json.JSONObject messageMetaJson) {
                    runOnUiThread(() -> handleDeletedMessage(messageMetaJson));
                }

                @Override
                public void onReactionUpdated(org.json.JSONObject reactionJson) {
                    // Optional: update reactions UI later
                }
            });

            // If user scroll up, stopPolling to avoid conflicts
            stopPolling();
        }
    }
    
    protected void setupClickListeners() {
        // Back button
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> {
                finish();
            });
        }

        if (ivSend != null) {
            ivSend.setOnClickListener(v -> handleSendMessage());
        }
        
        if (ivMore != null) {
            ivMore.setOnClickListener(v -> showChatOptions());
        }

        // Hide/disable call button for group chats
        if (ivVideoCall != null) {
            if (currentChat != null && currentChat.isGroupChat()) {
                ivVideoCall.setVisibility(View.GONE);
            } else {
                ivVideoCall.setOnClickListener(v -> {
                    Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);
                    v.startAnimation(scaleAnimation);
                    new Handler(Looper.getMainLooper()).postDelayed(this::handleVideoCall, 150);
                });
            }
        }

        // Common click listeners
        if (ivAttachment != null) {
            ivAttachment.setOnClickListener(v -> showAttachmentOptions());
        }

        if (ivEmoji != null) {
            ivEmoji.setOnClickListener(v -> showEmojiPicker());
        }

        if (ivGallery != null) {
            ivGallery.setOnClickListener(v -> showAttachmentOptions());
        }

        if (ivReplyClose != null && replyBar != null) {
            ivReplyClose.setOnClickListener(v -> clearReplyState());
        }

        // Text watcher for mentions
        if (etMessage != null) {
            etMessage.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    handleMentionParsing(s);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    protected void applyBlockUiState(boolean blockedByMe, boolean blockedMe) {
        // Disable input and show a simple banner in status line
        if (etMessage != null) {
            etMessage.setEnabled(!(blockedByMe || blockedMe));
            etMessage.setHint(blockedMe ? "You cannot message this user" : (blockedByMe ? "You have blocked this user" : "Type a message"));
        }
        if (ivSend != null) ivSend.setEnabled(!(blockedByMe || blockedMe));
        if (tvStatus != null && currentChat != null && !currentChat.isGroupChat()) {
            if (blockedMe) {
                tvStatus.setText("You cannot message this user");
            } else if (blockedByMe) {
                tvStatus.setText("You blocked this user - Unblock to continue");
            }
        }
    }

    protected void setupRecyclerView() {
        String currentUserId = sharedPrefsManager.getUserId();
        messageAdapter = new MessageAdapter(messages, currentUserId);
        messageAdapter.setOnMessageClickListener(this);
        messageAdapter.setAvatarManager(avatarManager);
        android.util.Log.d("BaseChatActivity", "AvatarManager set: " + (avatarManager != null) + ", currentUserId: " + currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);   // Set true to stack from bottom. But it can cause issues when have few messages.
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);
        
        // Add scroll listener to detect when user reaches bottom
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (isAtBottom()) {
                    hasNewMessages = false;
                }
                // Infinite scroll upwards: when first visible close to top, load previous page
                LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                if (lm != null && !isLoadingMore && hasMore) {
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    if (firstVisible <= 3) {
                        loadMoreMessages();
                    }
                }
            }
            
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // When user stops scrolling, check if they're at bottom
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (isAtBottom()) {
                        hasNewMessages = false;
                        // If user manually scrolled to bottom, enable auto-scroll for new messages
                        enableAutoScrollForNewMessages();
                    }
                }
            }
        });

        // If there is a pending forward content and chat is known, send it now (post to ensure UI ready)
        if (pendingForwardContent != null && !pendingForwardContent.isEmpty() && currentChat != null) {
            rvMessages.postDelayed(() -> {
                try {
                    sendMessage(pendingForwardContent);
                } finally {
                    pendingForwardContent = null;
                }
            }, 150);
        }
    }

    protected void loadMessages() {
        if (currentChat == null) return;

        String token = sharedPrefsManager.getToken();
        boolean isRefresh = !isInitialLoad; // polling refresh vs first load
        if (!isRefresh) {
            currentPage = 1;
            hasMore = true;
        }
        apiClient.getMessages(token, currentChat.getId(), 1, pageSize, new Callback() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                // Parse messages
                                int previousMessageCount = messages.size();
                                java.util.List<Message> pageOne = new java.util.ArrayList<>();
                                if (data.has("messages") && data.get("messages") instanceof org.json.JSONArray) {
                                    org.json.JSONArray messagesArray = data.getJSONArray("messages");
                                    android.util.Log.d("BaseChatActivity", "Found " + messagesArray.length() + " messages");
                                    for (int i = 0; i < messagesArray.length(); i++) {
                                        JSONObject messageJson = messagesArray.getJSONObject(i);
                                        android.util.Log.d("BaseChatActivity", "Message " + i + ": " + messageJson.toString());
                                        Message message = Message.fromJson(messageJson);
                                        android.util.Log.d("BaseChatActivity", "Parsed message - chatType: " + message.getChatType() + 
                                                ", senderUsername: " + message.getSenderUsername() + 
                                                ", senderAvatar: " + message.getSenderAvatar());
                                        pageOne.add(message);
                                    }
                                    hasMore = messagesArray.length() >= pageSize;
                                }
                                boolean addedNewAtEnd = false;
                                if (!isRefresh) {
                                    messages.clear();
                                    messages.addAll(pageOne);
                                } else {
                                    // Merge: upsert by id; if id not found, try replace local placeholder; else append
                                    for (Message m : pageOne) {
                                        int existingIdx = indexOfMessageById(m.getId());
                                        if (existingIdx >= 0) {
                                            messages.set(existingIdx, m);
                                            messageAdapter.notifyItemChanged(existingIdx);
                                        } else {
                                            int localIdx = findLocalPlaceholderIndex(m);
                                            if (localIdx >= 0) {
                                                messages.set(localIdx, m);
                                                messageAdapter.notifyItemChanged(localIdx);
                                            } else {
                                                messages.add(m);
                                                addedNewAtEnd = true;
                                            }
                                        }
                                    }
                                }
                                
                                // Update chat info if available
                                if (data.has("chatInfo")) {
                                    JSONObject chatInfo = data.getJSONObject("chatInfo");
                                    if ("private".equalsIgnoreCase(chatInfo.optString("type"))) {
                                        isBlockedByMe = chatInfo.optBoolean("isBlockedByMe", false);
                                        hasBlockedMe = chatInfo.optBoolean("hasBlockedMe", false);
                                        applyBlockUiState(isBlockedByMe, hasBlockedMe);
                                    } else {
                                        isBlockedByMe = false;
                                        hasBlockedMe = false;
                                        applyBlockUiState(false, false);
                                    }
                                }

                                messageAdapter.notifyDataSetChanged();

                                // Handle scrolling based on context
                                if (!isRefresh) {
                                    // Always scroll to bottom on initial load
                                    scrollToBottom();
                             
                                    isInitialLoad = false;
                                } else if (addedNewAtEnd) {
                                    // Only scroll if there are new messages and user is at bottom
                                    scrollToBottomIfAtBottom();
                                }
                            } else {
                                String errorMessage = jsonResponse.optString("message", "Failed to load messages");
                                Toast.makeText(BaseChatActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, "Error parsing messages", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Failed to load messages: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(BaseChatActivity.this, "Failed to load messages: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadMoreMessages() {
        if (currentChat == null || isLoadingMore || !hasMore) return;
        isLoadingMore = true;
        String token = sharedPrefsManager.getToken();
        // Capture current top item and offset to restore after prepend
        LinearLayoutManager lmBefore = (LinearLayoutManager) rvMessages.getLayoutManager();
        int firstVisibleBefore = lmBefore != null ? lmBefore.findFirstVisibleItemPosition() : 0;
        View firstViewBefore = rvMessages.getChildAt(0);
        int topOffsetBefore = firstViewBefore != null ? (firstViewBefore.getTop() - rvMessages.getPaddingTop()) : 0;
        apiClient.getMessages(token, currentChat.getId(), currentPage + 1, pageSize, new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    if (!response.isSuccessful()) return;
                    try {
                        JSONObject json = new JSONObject(body);
                        if (!json.optBoolean("success", false)) return;
                        JSONObject data = json.getJSONObject("data");
                        org.json.JSONArray arr = data.optJSONArray("messages");
                        if (arr != null && arr.length() > 0) {
                            java.util.List<Message> older = new java.util.ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                Message m = Message.fromJson(arr.getJSONObject(i));
                                older.add(m);
                            }
                            messages.addAll(0, older);
                            currentPage += 1;
                            hasMore = arr.length() >= pageSize;
                            messageAdapter.notifyItemRangeInserted(0, older.size());
                            // Restore previous viewport so it doesn't jump to bottom
                            LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                            if (lm != null) {
                                lm.scrollToPositionWithOffset(firstVisibleBefore + older.size(), topOffsetBefore);
                            }
                        } else {
                            hasMore = false;
                        }
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onFailure(Call call, IOException e) { runOnUiThread(() -> isLoadingMore = false); }
        });
    }

    protected void sendMessage(String content) {
        if (TextUtils.isEmpty(content.trim())) return;

        String token = sharedPrefsManager.getToken();
        String senderId = sharedPrefsManager.getUserId();

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentChat == null) {
            Toast.makeText(this, "Chat not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent sending if blocked in private chat
        if (!currentChat.isGroupChat()) {
            if (isBlockedByMe) {
                Toast.makeText(this, "You blocked this user. Unblock to continue.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (hasBlockedMe) {
                Toast.makeText(this, "You cannot message this user", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Create message object
        Message message = new Message();
        message.setContent(content);
        message.setSenderId(senderId);
        message.setChatId(currentChat.getId());
        message.setType("text"); // Set message type
        message.setChatType(currentChat.isGroupChat() ? "group" : "private"); // Set chat type
        message.setTimestamp(System.currentTimeMillis());
        // Generate client nonce for dedupe across socket/poll
        String clientNonce = java.util.UUID.randomUUID().toString();
        message.setClientNonce(clientNonce);
        // Assign a temporary local id to dedupe when server echoes back
        try { message.setId("local-" + message.getTimestamp()); } catch (Exception ignored) {}
        if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
            message.setReplyToMessageId(replyingToMessageId);
            message.setReplyToSenderName(replyingToAuthor);
            message.setReplyToContent(replyingToContent);
            if (replyingToImageThumb != null && !replyingToImageThumb.isEmpty()) {
                message.setReplyToImageThumb(replyingToImageThumb);
            }
        }

        // Add to local list immediately for better UX
        message.setLocalSignature(buildLocalSignature(message));
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        // Always scroll to bottom when user sends a message
        forceScrollToBottom();

        // Clear input
        etMessage.setText("");

        // Send to server
        try {
            // Create JSON object that matches server expectations
            JSONObject messageJson = new JSONObject();
            messageJson.put("chatId", currentChat.getId());
            messageJson.put("content", content);
            messageJson.put("type", "text");
            messageJson.put("timestamp", System.currentTimeMillis());
            messageJson.put("clientNonce", clientNonce);
            if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
                messageJson.put("replyTo", replyingToMessageId);
            }
            
            android.util.Log.d("BaseChatActivity", "Sending message: " + messageJson);
            
        apiClient.sendMessage(token, messageJson, new Callback() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    android.util.Log.d("BaseChatActivity", "Send message response: " + response.code() + " - " + responseBody);
                    
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    // If server returns message with id, update placeholder id for stronger dedupe
                                    try {
                                        JSONObject data = jsonResponse.optJSONObject("data");
                                        if (data != null && data.has("message")) {
                                            JSONObject m = data.getJSONObject("message");
                                            String serverId = m.optString("_id", null);
                                            if (serverId != null) {
                                                // Replace the last local placeholder that matches signature
                                                int idx = findLocalPlaceholderIndex(Message.fromJson(m));
                                                if (idx >= 0) {
                                                    messages.set(idx, Message.fromJson(m));
                                                    messageAdapter.notifyItemChanged(idx);
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                    clearReplyState();
                                } else {
                                    // Remove from local list if failed
                                    messages.remove(message);
                                    messageAdapter.notifyDataSetChanged();
                                    String errorMsg = jsonResponse.optString("message", "Failed to send message");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                // Remove from local list if failed
                                messages.remove(message);
                                messageAdapter.notifyDataSetChanged();
                                Toast.makeText(BaseChatActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // Remove from local list if failed
                            messages.remove(message);
                            messageAdapter.notifyDataSetChanged();
                            Toast.makeText(BaseChatActivity.this, "Failed to send message: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFailure(Call call, IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send message failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        // Remove from local list if failed
                        messages.remove(message);
                        messageAdapter.notifyDataSetChanged();
                        Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing message", Toast.LENGTH_SHORT).show();
        }
    }

    protected void scrollToBottom() {
        if (!messages.isEmpty()) {
            rvMessages.smoothScrollToPosition(messages.size() - 1);
        }
    }

    protected boolean isAtBottom() {
        if (messages.isEmpty()) return true;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (layoutManager == null) return true;
        
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        int totalItemCount = layoutManager.getItemCount();
        
        // Consider at bottom if user is within 7 messages from the end
        return lastVisiblePosition >= totalItemCount - 7;
    }

    protected void scrollToBottomIfAtBottom() {
        if (isAtBottom()) {
            scrollToBottom();
            hasNewMessages = false;
        } else {
            hasNewMessages = true;
        }
    }

    protected void forceScrollToBottom() {
        scrollToBottom();
        hasNewMessages = false;
    }

    protected void enableAutoScrollForNewMessages() {
        // Reset the flag to enable auto-scroll for new messages
        hasNewMessages = false;
    }

    protected void scrollToMessage(String messageId) {
        if (messageId == null || messageId.isEmpty()) return;
        
        int position = indexOfMessageById(messageId);
        if (position >= 0) {
            rvMessages.smoothScrollToPosition(position);
            // Highlight the message briefly
            highlightMessage(position);
        } else {
            Toast.makeText(this, "Message not found", Toast.LENGTH_SHORT).show();
        }
    }

    protected void highlightMessage(int position) {
        if (position < 0 || position >= messages.size()) return;
        
        // Get the view holder for the message
        RecyclerView.ViewHolder holder = rvMessages.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View messageView = holder.itemView;
            
            // Create highlight animation
            Animation highlightAnimation = AnimationUtils.loadAnimation(this, R.anim.message_highlight);
            messageView.startAnimation(highlightAnimation);
            
            // Remove highlight after animation
            new Handler(Looper.getMainLooper()).postDelayed(messageView::clearAnimation, 1000);
        }
    }

    protected void startPolling() {
        if (isPolling) return;
        isPolling = true;

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                loadMessages();
                pollHandler.postDelayed(this, 3000); // Poll every 3 seconds
            }
        };
        pollHandler.post(pollRunnable);
    }

    protected void stopPolling() {
        if (!isPolling) return;
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    // ===== Mentions support =====
    protected void setupMentionSupport() {
        if (etMessage == null) return;
        mentionPopup = new ListPopupWindow(this);
        mentionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mentionCandidates);
        mentionPopup.setAdapter(mentionAdapter);
        mentionPopup.setAnchorView(etMessage);
        mentionPopup.setOnItemClickListener((parent, view, position, id) -> {
            String username = mentionCandidates.get(position);
            commitMention(username);
            mentionPopup.dismiss();
        });
    }

    protected void fetchGroupMembersForMentions() {
        try {
            String token = sharedPrefsManager.getToken();
            if (token == null || token.isEmpty() || currentChat == null) return;
            apiClient.getGroupMembers(token, currentChat.getId(), new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    // ignore
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                    if (!response.isSuccessful()) return;
                    try {
                        String body = response.body().string();
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        java.util.List<String> users = new java.util.ArrayList<>();
                        // Try various response shapes: data.members[] or members[]
                        org.json.JSONArray arr = null;
                        if (json.has("data") && json.get("data") instanceof org.json.JSONObject) {
                            org.json.JSONObject data = json.getJSONObject("data");
                            if (data.has("members") && data.get("members") instanceof org.json.JSONArray) {
                                arr = data.getJSONArray("members");
                            }
                        }
                        if (arr == null && json.has("members") && json.get("members") instanceof org.json.JSONArray) {
                            arr = json.getJSONArray("members");
                        }
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                org.json.JSONObject m = arr.getJSONObject(i);
                                // member may be object with username or nested user
                                String username = m.optString("username",
                                        m.optJSONObject("user") != null ? Objects.requireNonNull(m.optJSONObject("user")).optString("username", "") : "");
                                if (!username.isEmpty() && !users.contains(username)) {
                                    users.add(username);
                                }
                            }
                        }
                        runOnUiThread(() -> {
                            mentionCandidates.clear();
                            mentionCandidates.addAll(users);
                            if (mentionAdapter != null) mentionAdapter.notifyDataSetChanged();
                        });
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    protected void handleMentionParsing(CharSequence s) {
        if (currentChat == null || !currentChat.isGroupChat()) return;
        int cursor = etMessage.getSelectionStart();
        if (cursor < 0) return;
        // Find the '@' word start before cursor
        int i = cursor - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == '@') {
                mentionStart = i;
                break;
            }
            if (Character.isWhitespace(c)) {
                break;
            }
            i--;
        }
        if (mentionStart >= 0) {
            String query = s.subSequence(mentionStart + 1, cursor).toString();
            showMentionSuggestions(query);
        } else {
            if (mentionPopup != null) mentionPopup.dismiss();
        }
    }

    protected void showMentionSuggestions(String query) {
        if (mentionAdapter == null || mentionCandidates.isEmpty()) {
            if (mentionPopup != null) mentionPopup.dismiss();
            return;
        }
        java.util.List<String> filtered = new java.util.ArrayList<>();
        String q = query == null ? "" : query.toLowerCase();
        for (String u : mentionCandidates) {
            if (q.isEmpty() || u.toLowerCase().contains(q)) filtered.add(u);
        }
        if (filtered.isEmpty()) {
            mentionPopup.dismiss();
            return;
        }
        mentionPopup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, filtered));
        mentionPopup.setAnchorView(etMessage);
        mentionPopup.setModal(true);
        mentionPopup.show();
    }

    protected void commitMention(String username) {
        if (mentionStart < 0) return;
        Editable text = etMessage.getText();
        int cursor = etMessage.getSelectionStart();
        if (cursor < 0) cursor = text.length();
        // Replace from '@' to cursor with @username + space
        text.replace(mentionStart, cursor, "@" + username + " ");
        // Move cursor to end
        etMessage.setSelection(mentionStart + username.length() + 2);
        mentionStart = -1;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        
        // SocketManager is managed globally, no cleanup needed here
        android.util.Log.d("BaseChatActivity", "Activity destroyed - SocketManager remains global");
        if (socketManager != null) {
            socketManager.removeMessageListener();
        }
    }

    // MessageAdapter.OnMessageClickListener implementation
    @Override
    public void onMessageClick(Message message) {
        // Common message click handling
        Toast.makeText(this, "Message clicked: " + message.getContent(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMessageLongClick(Message message) {
        // Common message long click handling
        showMessageOptions(message);
    }

    @Override
    public void onImageClick(String imageUrl, String localImageUri) {
        // Show image in full screen dialog
        showImageZoomDialog(imageUrl, localImageUri);
    }

    @Override
    public void onReactClick(Message message, String emoji) {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) return;
        // Optimistic UI update: update local message to show reaction immediately
        try {
            message.incrementReaction(emoji);
            int idx = indexOfMessageById(message.getId());
            if (idx >= 0) {
                messageAdapter.notifyItemChanged(idx);
            } else {
                messageAdapter.notifyDataSetChanged();
            }
        } catch (Exception ignored) {}

        apiClient.addReaction(token, message.getId(), emoji, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                // Optionally revert on failure
            }
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) { /* backend will broadcast reaction_updated */ }
        });
    }

    private String findUserReactionEmoji(Message message, String userId) {
        try {
            String raw = message.getReactionsRaw();
            if (raw == null || raw.isEmpty() || userId == null || userId.isEmpty()) return null;
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject r = arr.getJSONObject(i);
                String emoji = r.optString("emoji", null);
                org.json.JSONObject u = r.optJSONObject("user");
                String uid = u != null ? u.optString("_id", u.optString("id", null)) : null;
                if (uid != null && uid.equals(userId)) {
                    return emoji;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void onReplyClick(String replyToMessageId) {
        scrollToMessage(replyToMessageId);
    }

    protected void showImageZoomDialog(String imageUrl, String localImageUri) {
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
            
            com.squareup.picasso.Picasso.get()
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

    // ===== Realtime message handlers =====
    protected void handleIncomingMessage(org.json.JSONObject messageJson) {
        try {
            // Filter by current chat
            String chatId = messageJson.optString("chat");
            if (currentChat == null || !chatId.equals(currentChat.getId())) return;

            Message incoming = Message.fromJson(messageJson);
            // Upsert by id
            int idx = indexOfMessageById(incoming.getId());
            if (idx >= 0) {
                messages.set(idx, incoming);
                messageAdapter.notifyItemChanged(idx);
            } else {
        // De-duplicate: if there is a local placeholder with same signature, replace it
        int localIdx = findLocalPlaceholderIndex(incoming);
                if (localIdx >= 0) {
                    messages.set(localIdx, incoming);
                    messageAdapter.notifyItemChanged(localIdx);
                } else {
                    messages.add(incoming);
                    messageAdapter.notifyItemInserted(messages.size() - 1);
                }
                // Only scroll to bottom if user is already at the bottom
                scrollToBottomIfAtBottom();
            }
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Failed to handle incoming message: " + e.getMessage());
        }
    }

    protected void handleEditedMessage(org.json.JSONObject messageJson) {
        try {
            String chatId = messageJson.optString("chat");
            if (currentChat == null || !chatId.equals(currentChat.getId())) return;
            Message edited = Message.fromJson(messageJson);
            int idx = indexOfMessageById(edited.getId());
            if (idx >= 0) {
                messages.set(idx, edited);
                messageAdapter.notifyItemChanged(idx);
            }
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Failed to handle edited message: " + e.getMessage());
        }
    }

    protected void handleDeletedMessage(org.json.JSONObject metaJson) {
        try {
            String messageId = metaJson.optString("id", metaJson.optString("_id", ""));
            String chatId = metaJson.optString("chat");
            if (currentChat == null) return;
            if (!chatId.isEmpty() && !chatId.equals(currentChat.getId())) return;
            int idx = indexOfMessageById(messageId);
            if (idx >= 0) {
                Message m = messages.get(idx);
                m.setDeleted(true);
                m.setContent("This message was deleted");
                messageAdapter.notifyItemChanged(idx);
            }
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Failed to handle deleted message: " + e.getMessage());
        }
    }

    protected int indexOfMessageById(String id) {
        if (id == null) return -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (id.equals(m.getId())) return i;
        }
        return -1;
    }

    private int findLocalPlaceholderIndex(Message incoming) {
        if (incoming == null) return -1;
        String currentUserId = sharedPrefsManager != null ? sharedPrefsManager.getUserId() : null;
        if (currentUserId == null) return -1;
        // If server includes clientNonce, prefer matching by nonce
        String incomingNonce = null;
        try { incomingNonce = (String) Message.class.getMethod("getClientNonce").invoke(incoming); } catch (Exception ignored) {}
        String expectedSig = buildLocalSignature(incoming);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.getId() != null && m.getId().startsWith("local-") && currentUserId.equals(m.getSenderId())) {
                String sig = m.getLocalSignature();
                boolean nonceMatch = false;
                if (incomingNonce != null) {
                    try {
                        String localNonce = (String) Message.class.getMethod("getClientNonce").invoke(m);
                        nonceMatch = incomingNonce.equals(localNonce);
                    } catch (Exception ignored) {}
                }
                if (nonceMatch || (sig != null && sig.equals(expectedSig))) return i;
            }
        }
        return -1;
    }

    private String buildLocalSignature(Message m) {
        try {
            String uid = sharedPrefsManager != null ? sharedPrefsManager.getUserId() : "";
            String type = m.isImageMessage() ? "image" : (m.isTextMessage() ? "text" : m.getType());
            String contentKey = m.isTextMessage() ? (m.getContent() != null ? m.getContent() : "") : "img";
            return uid + "|" + type + "|" + contentKey;
        } catch (Exception ignored) { return null; }
    }

    protected void showMessageOptions(Message message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_message_options, null);
        
        boolean canEdit = message.isTextMessage() && message.getSenderId() != null && message.getSenderId().equals(sharedPrefsManager.getUserId());
        
        // Show/hide edit option based on permissions
        dialogView.findViewById(R.id.option_edit).setVisibility(canEdit ? View.VISIBLE : View.GONE);
        
        // Set click listeners for each option
        dialogView.findViewById(R.id.option_react).setOnClickListener(v -> {
            showReactionPicker(message);
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_reply).setOnClickListener(v -> {
            setReplyState(message);
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_edit).setOnClickListener(v -> {
            if (canEdit) {
                promptEditMessage(message);
                if (currentDialog != null) currentDialog.dismiss();
            }
        });
        
        dialogView.findViewById(R.id.option_delete).setOnClickListener(v -> {
            confirmDeleteMessage(message);
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_copy).setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("message", message.getContent());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_forward).setOnClickListener(v -> {
            // Start SearchActivity in forward mode
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra("mode", "forward");
            // Pack content to forward (text only for now)
            String forwardContent = message.isImageMessage() ? (message.getContent() != null ? message.getContent() : "") : message.getContent();
            i.putExtra("forward_content", forwardContent != null ? forwardContent : "");
            startActivity(i);
            if (currentDialog != null) currentDialog.dismiss();
        });
        
        builder.setView(dialogView);
        currentDialog = builder.create();
        if (currentDialog.getWindow() != null) {
            android.view.Window w = currentDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        currentDialog.show();
    }

    protected void showReactionPicker(Message message) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_react_picker, null);

        TextView title = dialogView.findViewById(R.id.tv_title);
        TextView btnRemove = dialogView.findViewById(R.id.btn_remove_react);
        int[] emojiIds = new int[]{
            R.id.emoji_1,
            R.id.emoji_2,
            R.id.emoji_3,
            R.id.emoji_4,
            R.id.emoji_5,
            R.id.emoji_6
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);
        AlertDialog dlg = builder.create();

        if (title != null) title.setText("React");
        for (int id : emojiIds) {
            View v = dialogView.findViewById(id);
            if (v instanceof TextView) {
                v.setOnClickListener(x -> {
                    try {
                        CharSequence emoji = ((TextView) v).getText();
                        if (emoji != null) onReactClick(message, emoji.toString());
                    } catch (Exception ignored) {}
                    dlg.dismiss();
                });
            }
        }
        if (btnRemove != null) btnRemove.setOnClickListener(v -> {
            try {
                String token = sharedPrefsManager.getToken();
                if (token != null && !token.isEmpty()) {
                    // Determine current user's emoji to remove from message.reactionsRaw
                    String myUserId = sharedPrefsManager.getUserId();
                    String emojiToRemove = findUserReactionEmoji(message, myUserId);
                    if (emojiToRemove == null || emojiToRemove.isEmpty()) {
                        dlg.dismiss();
                        return;
                    }

                    // Optimistic UI update
                    message.decrementReaction(emojiToRemove);
                    int idx = indexOfMessageById(message.getId());
                    if (idx >= 0) messageAdapter.notifyItemChanged(idx); else messageAdapter.notifyDataSetChanged();

                    apiClient.removeReaction(token, message.getId(), emojiToRemove, new okhttp3.Callback() {
                        @Override public void onFailure(okhttp3.Call call, java.io.IOException e) { }
                        @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) { }
                    });
                }
            } catch (Exception ignored) { }
            dlg.dismiss();
        });
        if (dlg.getWindow() != null) {
            Window w = dlg.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dlg.show();
    }

    protected void setReplyState(Message message) {
        replyingToMessageId = message.getId();
        replyingToAuthor = message.getSenderUsername();
        replyingToContent = message.getContent();
        // Capture image thumb if replying to an image
        if (message.isImageMessage()) {
            String thumb = null;
            try {
                String attachments = message.getAttachments();
                if (attachments != null && !attachments.isEmpty()) {
                    org.json.JSONArray arr = new org.json.JSONArray(attachments);
                    if (arr.length() > 0) {
                        org.json.JSONObject att = arr.getJSONObject(0);
                        thumb = att.optString("url", null);
                    }
                }
            } catch (Exception ignored) {}
            if ((thumb == null || thumb.isEmpty())) {
                thumb = message.getLocalImageUri() != null && !message.getLocalImageUri().isEmpty()
                        ? message.getLocalImageUri()
                        : message.getContent();
            }
            replyingToImageThumb = thumb;
        } else {
            replyingToImageThumb = null;
        }
        if (replyBar != null) replyBar.setVisibility(android.view.View.VISIBLE);
        if (tvReplyAuthor != null) tvReplyAuthor.setText(replyingToAuthor != null ? replyingToAuthor : "Reply");
        if (tvReplyContent != null) tvReplyContent.setText(replyingToContent != null ? replyingToContent : "");

        // Cache for adapter rebind stability
        if (replyingToMessageId != null && replyingToImageThumb != null && !replyingToImageThumb.isEmpty()) {
            com.example.chatappjava.utils.ReplyPreviewCache.put(replyingToMessageId, replyingToImageThumb);
        }
    }

    protected void clearReplyState() {
        replyingToMessageId = null;
        replyingToAuthor = null;
        replyingToContent = null;
        replyingToImageThumb = null;
        if (replyBar != null) replyBar.setVisibility(android.view.View.GONE);
    }

    protected void promptEditMessage(Message message) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(message.getContent());
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            "Edit Message",
            null,
            "Save",
            "Cancel",
            () -> {
                String newContent = input.getText().toString().trim();
                if (!newContent.isEmpty()) {
                    performEditMessage(message, newContent);
                }
            },
            null,
            false
        );
    }

    protected void performEditMessage(Message message, String newContent) {
        String token = sharedPrefsManager.getToken();
        apiClient.editMessage(token, message.getId(), newContent, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Failed to edit message", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            if (json.optBoolean("success", false)) {
                                message.setContent(newContent);
                                message.setEdited(true);
                                messageAdapter.notifyDataSetChanged();
                                Toast.makeText(BaseChatActivity.this, "Message edited", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(BaseChatActivity.this, json.optString("message", "Failed to edit"), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            Toast.makeText(BaseChatActivity.this, "Edited but parse failed", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Failed to edit message: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    protected void confirmDeleteMessage(Message message) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Delete this message for everyone?")
            .setPositiveButton("Delete", (d, w) -> performDeleteMessage(message))
            .setNegativeButton("Cancel", null)
            .show();
    }

    protected void performDeleteMessage(Message message) {
        String token = sharedPrefsManager.getToken();
        apiClient.deleteMessage(token, message.getId(), new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Failed to delete message", Toast.LENGTH_SHORT).show());
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        message.setDeleted(true);
                        message.setContent("This message was deleted");
                        messageAdapter.notifyDataSetChanged();
                        Toast.makeText(BaseChatActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Failed to delete: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    protected abstract void resetCallButtonState();
}
