package com.example.chatappjava.ui.theme;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public abstract class BaseChatActivity extends AppCompatActivity implements MessageAdapter.OnMessageClickListener {

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

    // Common data
    protected Chat currentChat;
    protected User otherUser;
    protected List<Message> messages;
    protected MessageAdapter messageAdapter;
    protected SharedPreferencesManager sharedPrefsManager;
    protected ApiClient apiClient;
    protected AvatarManager avatarManager;
    protected SocketManager socketManager;

    // Common state
    protected boolean isPolling = false;
    protected Handler pollHandler = new Handler(Looper.getMainLooper());
    protected Runnable pollRunnable;
    
    // Gallery and camera constants
    private static final int REQUEST_CODE_GALLERY = 1001;
    private static final int REQUEST_CODE_CAMERA = 1002;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1003;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1004;

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
                .setNegativeButton("Cancel", null)
                .show();
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
                                
                                startActivity(intent);
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
        String[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openCamera();
                            break;
                        case 1:
                            openGallery();
                            break;
                        case 2:
                            dialog.dismiss();
                            break;
                    }
                });
        builder.show();
    }
    
    protected void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
            return;
        }
        
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, REQUEST_CODE_CAMERA);
        } else {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }
    
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
            } else if (requestCode == REQUEST_CODE_CAMERA && data != null) {
                // Camera returns a Bitmap in extras, not a Uri
                android.graphics.Bitmap cameraBitmap = (android.graphics.Bitmap) data.getExtras().get("data");
                if (cameraBitmap != null) {
                    handleSelectedBitmap(cameraBitmap);
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
            java.io.File imageFile = new java.io.File(imageUri.getPath());
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
                uploadImageToServer(imageFile);
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
    
    protected void sendImageMessage(String imageUrl) {
        sendImageMessage(imageUrl, null);
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
        
        // Store local URI for zoom functionality if available
        if (localUri != null) {
            // Store local URI in message object for zoom
            message.setLocalImageUri(localUri.toString());
        }
        
        // Add to local list immediately for better UX
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();
        
        // Send to server
        try {
            // Create JSON object that matches server expectations
            org.json.JSONObject messageJson = new org.json.JSONObject();
            messageJson.put("chatId", currentChat.getId());
            messageJson.put("type", "image");
            messageJson.put("timestamp", System.currentTimeMillis());
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
        String[] emojis = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
            "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
            "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
            "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨",
            "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥",
            "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
            "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
            "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑",
            "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻",
            "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸",
            "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
        };
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose emoji");
        
        // Create a grid layout for emojis
        android.widget.GridLayout gridLayout = new android.widget.GridLayout(this);
        gridLayout.setColumnCount(8);
        gridLayout.setPadding(16, 16, 16, 16);
        
        for (String emoji : emojis) {
            TextView emojiView = new TextView(this);
            emojiView.setText(emoji);
            emojiView.setTextSize(24);
            emojiView.setPadding(8, 8, 8, 8);
            emojiView.setGravity(android.view.Gravity.CENTER);
            emojiView.setBackground(android.graphics.drawable.ColorDrawable.createFromPath("@android:color/transparent"));
            emojiView.setOnClickListener(v -> {
                insertEmoji(emoji);
                builder.create().dismiss();
            });
            
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 80;
            params.height = 80;
            emojiView.setLayoutParams(params);
            
            gridLayout.addView(emojiView);
        }
        
        builder.setView(gridLayout);
        builder.setNegativeButton("Cancel", null);
        builder.show();
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
        ivGallery = findViewById(R.id.iv_attach); // Reuse attach button for gallery
        etMessage = findViewById(R.id.et_message);
        rvMessages = findViewById(R.id.rv_messages);
        progressBar = findViewById(R.id.progress_bar);
        // Optional reply bar elements (may not exist in all layouts)
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
    }
    
    protected void setupSocketManager() {
        // SocketManager is now managed globally by ChatApplication
        // No need to setup connection or global listeners here
        android.util.Log.d("BaseChatActivity", "SocketManager setup - using global instance");
    }
    
    protected void setupClickListeners() {
        // Back button
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> {
                android.util.Log.d("BaseChatActivity", "Back button clicked");
                finish();
            });
        } else {
            android.util.Log.w("BaseChatActivity", "Back button not found in layout");
        }
        
        if (ivSend != null) {
            ivSend.setOnClickListener(v -> handleSendMessage());
        }
        
        if (ivMore != null) {
            ivMore.setOnClickListener(v -> showChatOptions());
        }

        if (ivVideoCall != null) {
            ivVideoCall.setOnClickListener(v -> {
                // Add button animation
                Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);
                v.startAnimation(scaleAnimation);
                
                // Handle video call after animation
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    handleVideoCall();
                }, 150);
            });
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
    }

    protected void loadMessages() {
        if (currentChat == null) return;

        String token = sharedPrefsManager.getToken();
        apiClient.getMessages(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                
                                // Clear existing messages
                                messages.clear();
                                
                                // Parse messages array
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
                                        messages.add(message);
                                    }
                                }
                                
                                // Update chat info if available
                                if (data.has("chatInfo")) {
                                    JSONObject chatInfo = data.getJSONObject("chatInfo");
                                    // Update current chat with new info if needed
                                }

                                messageAdapter.notifyDataSetChanged();
                                scrollToBottom();
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

        // Create message object
        Message message = new Message();
        message.setContent(content);
        message.setSenderId(senderId);
        message.setChatId(currentChat.getId());
        message.setType("text"); // Set message type
        message.setChatType(currentChat.isGroupChat() ? "group" : "private"); // Set chat type
        message.setTimestamp(System.currentTimeMillis());
        if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
            message.setReplyToMessageId(replyingToMessageId);
            message.setReplyToSenderName(replyingToAuthor);
            message.setReplyToContent(replyingToContent);
        }

        // Add to local list immediately for better UX
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

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
            if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
                messageJson.put("replyTo", replyingToMessageId);
            }
            
            android.util.Log.d("BaseChatActivity", "Sending message: " + messageJson.toString());
            
            apiClient.sendMessage(token, messageJson, new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    android.util.Log.d("BaseChatActivity", "Send message response: " + response.code() + " - " + responseBody);
                    
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    android.util.Log.d("BaseChatActivity", "Message sent successfully");
                                    // Message sent successfully, keep it in the list
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
        if (messages.size() > 0) {
            rvMessages.smoothScrollToPosition(messages.size() - 1);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        
        // SocketManager is managed globally, no cleanup needed here
        android.util.Log.d("BaseChatActivity", "Activity destroyed - SocketManager remains global");
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

    protected void showImageZoomDialog(String imageUrl) {
        showImageZoomDialog(imageUrl, null);
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

    protected void showMessageOptions(Message message) {
        java.util.List<String> actions = new java.util.ArrayList<>();
        actions.add("Reply");
        boolean canEdit = message.isTextMessage() && message.getSenderId() != null && message.getSenderId().equals(sharedPrefsManager.getUserId());
        if (canEdit) actions.add("Edit");
        actions.add("Delete");
        actions.add("Copy");
        actions.add("Forward");
        String[] options = actions.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Message Options")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if ("Reply".equals(selected)) {
                        setReplyState(message);
                    } else if ("Edit".equals(selected)) {
                        promptEditMessage(message);
                    } else if ("Delete".equals(selected)) {
                        confirmDeleteMessage(message);
                    } else if ("Copy".equals(selected)) {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("message", message.getContent());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
                    } else if ("Forward".equals(selected)) {
                        Toast.makeText(this, "Forward message feature coming soon", Toast.LENGTH_SHORT).show();
                    }
                });
        builder.show();
    }

    protected void setReplyState(Message message) {
        replyingToMessageId = message.getId();
        replyingToAuthor = message.getSenderUsername();
        replyingToContent = message.getContent();
        if (replyBar != null) replyBar.setVisibility(android.view.View.VISIBLE);
        if (tvReplyAuthor != null) tvReplyAuthor.setText(replyingToAuthor != null ? replyingToAuthor : "Reply");
        if (tvReplyContent != null) tvReplyContent.setText(replyingToContent != null ? replyingToContent : "");
    }

    protected void clearReplyState() {
        replyingToMessageId = null;
        replyingToAuthor = null;
        replyingToContent = null;
        if (replyBar != null) replyBar.setVisibility(android.view.View.GONE);
    }

    protected void promptEditMessage(Message message) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(message.getContent());
        new AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String newContent = input.getText().toString().trim();
                if (!newContent.isEmpty()) {
                    performEditMessage(message, newContent);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
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

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
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
}
