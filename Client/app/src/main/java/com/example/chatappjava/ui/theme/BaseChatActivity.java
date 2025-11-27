package com.example.chatappjava.ui.theme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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
import android.util.Log;
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

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.MessageRepository;
import com.example.chatappjava.utils.OfflineMessageSyncManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public abstract class BaseChatActivity extends AppCompatActivity implements MessageAdapter.OnVoiceMessageClickListener {

    // ActivityResultLaunchers for modern Activity Result API
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> callLauncher;

    // Common UI elements
    protected ImageView ivBack;
    protected CircleImageView ivProfile;
    protected TextView tvChatName;
    protected ImageView ivMore, ivSend, ivAttachment, ivEmoji, ivGallery, ivVideoCall, ivRecordAudio;
    protected EditText etMessage;
    protected RecyclerView rvMessages;
    protected ProgressBar progressBar;
    // Reply UI state
    protected android.view.View replyBar;
    protected TextView tvReplyAuthor;
    protected TextView tvReplyContent;
    protected ImageView ivReplyClose;
    // Offline indicator
    protected android.view.View offlineIndicator;
    // Summarize indicator
    protected android.view.View summarizeIndicator;
    protected String replyingToMessageId;
    protected String replyingToAuthor;
    protected String replyingToContent;
    protected String replyingToImageThumb;

    // Common data
    protected Chat currentChat;
    protected User otherUser;
    protected List<Message> messages;
    protected MessageAdapter messageAdapter;
    protected DatabaseManager databaseManager;
    protected MessageRepository messageRepository;
    protected OfflineMessageSyncManager syncManager;
    protected ApiClient apiClient;
    protected AvatarManager avatarManager;
    protected AlertDialog emojiDialog;
    protected AlertDialog imageSelectDialog;
    protected SocketManager socketManager;
    protected AlertDialog currentDialog;
    // Forwarding support
    protected String pendingForwardContent;
    protected String pendingForwardMessageRaw; // JSON string with type/content/attachments
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
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1003;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1004;
    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 1005;

    // Camera capture state
    private android.net.Uri cameraPhotoUri;
    private java.io.File cameraPhotoFile;
    
    // Voice recording state
    private android.media.MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private boolean recordingStarted = false; // Track if recording actually started
    private java.io.File audioFile;
    private Handler recordingHandler;
    private TextView recordingTimerView;
    private Dialog recordingDialog;
    private ImageView ivRecordingIndicator;
    private View vPulseRing1;
    private View vPulseRing2;
    private android.view.animation.Animation pulseRing1Animation;
    private android.view.animation.Animation pulseRing2Animation;
    
    // Voice playback state
    private android.media.MediaPlayer currentVoicePlayer;
    private String currentlyPlayingMessageId;
    private Handler playbackHandler;
    private Runnable playbackUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResource());

        // Register ActivityResultLaunchers
        registerActivityResultLaunchers();

        initViews();
        initData();
        setupSocketManager();
        setupNetworkListener(); // Setup network listener for auto-sync
        setupClickListeners();
        setupRecyclerView();
        loadChatData();
        startPolling();
        setupMentionSupport();
    }

    // Register ActivityResultLaunchers for modern Activity Result API
    private void registerActivityResultLaunchers() {
        // Camera launcher
        cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (cameraPhotoFile != null && cameraPhotoFile.exists()) {
                        uploadImageToServer(cameraPhotoFile, cameraPhotoUri);
                    } else if (data != null) {
                        android.graphics.Bitmap cameraBitmap = data.getParcelableExtra("data", android.graphics.Bitmap.class);
                        if (cameraBitmap != null) {
                            handleSelectedBitmap(cameraBitmap);
                        } else {
                            Toast.makeText(this, "No photo captured", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "No photo captured", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );

        // Gallery launcher
        galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data == null) return;
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        handleSelectedImage(selectedImageUri);
                    }
                }
            }
        );

        // File picker launcher
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data == null) return;
                    Uri selectedFileUri = data.getData();
                    if (selectedFileUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(selectedFileUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException ignored) { }
                        handleSelectedFile(selectedFileUri);
                    }
                }
            }
        );

        // Call launcher
        callLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Handle call result if needed
                // Currently no specific handling required for call results
            }
        );
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
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Initiating " + callType + " call...", Toast.LENGTH_SHORT).show();
        
        apiClient.initiateCall(token, currentChat.getId(), callType, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Failed to initiate call: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
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

                                callLauncher.launch(intent);
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
        currentUser.setId(databaseManager.getUserId());
        currentUser.setUsername(databaseManager.getUserName());
        currentUser.setAvatar(databaseManager.getUserAvatar());
        return currentUser;
    }
    
    // Gallery and attachment handling
    protected void showAttachmentOptions() {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attachment_select, null);
        
        // Get views
        LinearLayout cameraOption = dialogView.findViewById(R.id.option_camera);
        LinearLayout galleryOption = dialogView.findViewById(R.id.option_gallery);
        LinearLayout fileOption = dialogView.findViewById(R.id.option_file);

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
        
        fileOption.setOnClickListener(v -> {
            openFilePicker();
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
                boolean created = cameraPhotoFile.createNewFile();
                if (!created) {
                    Toast.makeText(this, "Failed to create camera file", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            cameraPhotoUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".provider",
                cameraPhotoFile
            );
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraLauncher.launch(cameraIntent);
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
        galleryLauncher.launch(galleryIntent);
    }
    
    protected void openFilePicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES};
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_STORAGE_PERMISSION);
            return;
        }
        
        // Use SAF and allow all file types
        Intent fileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        fileIntent.setType("*/*");
        // No EXTRA_MIME_TYPES to avoid restricting; let user pick any
        // Persistable read permission so we can access later if needed
        fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        filePickerLauncher.launch(fileIntent);
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
        } else if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Microphone permission required to record voice messages", Toast.LENGTH_SHORT).show();
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
    
    protected void handleSelectedFile(Uri fileUri) {
        if (fileUri == null) {
            Toast.makeText(this, "Cannot load file", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading message
        Toast.makeText(this, "Uploading file to server...", Toast.LENGTH_SHORT).show();
        
        try {
            // Get file info
            android.content.ContentResolver contentResolver = getContentResolver();
            android.database.Cursor cursor = contentResolver.query(fileUri, null, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                String fileName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME));
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                long fileSize = sizeIndex >= 0 ? cursor.getLong(sizeIndex) : 0;
                cursor.close();
                
                // Get MIME type (fallback to octet-stream)
                String mimeType = contentResolver.getType(fileUri);
                if (mimeType == null || mimeType.isEmpty()) {
                    mimeType = guessMimeFromName(fileName);
                    if (mimeType.isEmpty()) mimeType = "application/octet-stream";
                }
                
                // Check file size limit (50MB)
                if (fileSize > 50 * 1024 * 1024) {
                    Toast.makeText(this, "File size too large. Maximum 50MB allowed.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Accept all file types
                
                // Upload file to server
                uploadFileToServer(fileUri, fileName, mimeType, fileSize);
                
            } else {
                Toast.makeText(this, "Cannot read file information", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing file", Toast.LENGTH_SHORT).show();
        }
    }
    
    // No MIME white-listing; we accept all files. Keep helper to guess MIME when missing.

    protected void uploadImageToServer(java.io.File imageFile, android.net.Uri localUri) {
        android.util.Log.d("BaseChatActivity", "uploadImageToServer: Called with file: " + imageFile.getAbsolutePath() + ", size: " + imageFile.length());
        
        if (currentChat == null) {
            android.util.Log.e("BaseChatActivity", "uploadImageToServer: currentChat is null");
            Toast.makeText(this, "Chat not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            android.util.Log.e("BaseChatActivity", "uploadImageToServer: token is null or empty");
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String chatId = currentChat.getId();
        android.util.Log.d("BaseChatActivity", "uploadImageToServer: Calling uploadChatImage with chatId: " + chatId);
        
        // Upload image to server
        apiClient.uploadChatImage(token, imageFile, chatId, new okhttp3.Callback() {
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
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    protected void uploadFileToServer(Uri fileUri, String fileName, String mimeType, long fileSize) {
        if (currentChat == null) {
            Toast.makeText(this, "Chat not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Resolve content URI to a temp file to avoid ENOENT on content:// URIs
        java.io.File uploadTempFile = null;
        try {
            android.content.ContentResolver cr = getContentResolver();
            java.io.InputStream in = cr.openInputStream(fileUri);
            if (in != null) {
                uploadTempFile = new java.io.File(getCacheDir(), "upload_" + System.currentTimeMillis());
                java.io.FileOutputStream out = new java.io.FileOutputStream(uploadTempFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                in.close();
                out.flush();
                out.close();
            }
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Failed to copy content URI to temp file", e);
        }

        final java.io.File fileToUpload = (uploadTempFile != null && uploadTempFile.exists()) ? uploadTempFile : null;

        if (fileToUpload == null) {
            Toast.makeText(this, "Unable to access selected file. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Upload file to server (prefer File-based path)
        okhttp3.Callback callback = new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                android.util.Log.d("BaseChatActivity", "File upload response: " + response.code() + " - " + responseBody);
                
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                // Get file URL from server response
                                String fileUrl = jsonResponse.optString("fileUrl", "");
                                String serverFileName = jsonResponse.optString("fileName", "");
                                String originalName = jsonResponse.optString("originalName", fileName);
                                long serverFileSize = jsonResponse.optLong("fileSize", fileSize);
                                String serverMimeType = jsonResponse.optString("mimeType", mimeType);
                                
                                if (!fileUrl.isEmpty()) {
                                    // Send file message
                                    sendFileMessage(fileUrl, serverFileName, originalName, serverMimeType, serverFileSize);
                                } else {
                                    Toast.makeText(BaseChatActivity.this, "Failed to receive file URL from server", Toast.LENGTH_SHORT).show();
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
                android.util.Log.e("BaseChatActivity", "File upload failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        };

        apiClient.uploadChatFile(token, fileToUpload, fileName, mimeType, fileSize, currentChat.getId(), callback);
    }

    protected void sendFileMessage(String fileUrl, String fileName, String originalName, String mimeType, long fileSize) {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();
        
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create message object
        Message message = new Message();
        message.setContent(fileUrl);
        message.setSenderId(senderId);
        message.setChatId(currentChat.getId());
        message.setType("file");
        message.setChatType(currentChat.isGroupChat() ? "group" : "private");
        message.setTimestamp(System.currentTimeMillis());
        String clientNonce = java.util.UUID.randomUUID().toString();
        message.setClientNonce(clientNonce);
        // Assign a temporary local id to dedupe when server echoes back
        try { message.setId("local-" + message.getTimestamp()); } catch (Exception ignored) {}
        
        // Add to local list immediately for better UX
        message.setLocalSignature(buildLocalSignature(message));
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        // Always scroll to bottom when user sends a file
        forceScrollToBottom();
        
        // Send to server
        try {
            // Create JSON object that matches server expectations
            org.json.JSONObject messageJson = new org.json.JSONObject();
            messageJson.put("chatId", currentChat.getId());
            messageJson.put("type", "file");
            messageJson.put("timestamp", System.currentTimeMillis());
            messageJson.put("clientNonce", clientNonce);
            if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
                messageJson.put("replyTo", replyingToMessageId);
            }
            
            // Create attachments array for file message
            org.json.JSONArray attachments = new org.json.JSONArray();
            org.json.JSONObject attachment = new org.json.JSONObject();
            attachment.put("filename", fileName);
            attachment.put("originalName", originalName);
            attachment.put("mimeType", mimeType);
            attachment.put("size", fileSize);

            // Convert relative path to full URL for server validation
            String fullFileUrl = fileUrl;
            if (!fileUrl.startsWith("http")) {
                fullFileUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() +
                              ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + fileUrl;
            }
            attachment.put("url", fullFileUrl);
            attachments.put(attachment);
            messageJson.put("attachments", attachments);
            
            android.util.Log.d("BaseChatActivity", "Sending file message: " + messageJson);
            
            apiClient.sendMessage(token, messageJson, new okhttp3.Callback() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String responseBody = response.body().string();
                    android.util.Log.d("BaseChatActivity", "Send file message response: " + response.code() + " - " + responseBody);
                    
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    // Clear reply state after successful send
                                    clearReplyState();
                                } else {
                                    String errorMsg = jsonResponse.optString("message", "Failed to send file message");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(BaseChatActivity.this, "Error processing server response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(BaseChatActivity.this, "Failed to send file message: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send file message failed: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
            
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating file message", Toast.LENGTH_SHORT).show();
        }
    }

    protected void sendImageMessage(String imageUrl, android.net.Uri localUri) {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();
        
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
            JSONArray attachments = getJsonArray(imageUrl);
            messageJson.put("attachments", attachments);
            
            android.util.Log.d("BaseChatActivity", "Sending image message: " + messageJson);
            
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

    @NonNull
    private static JSONArray getJsonArray(String imageUrl) throws JSONException {
        JSONArray attachments = new JSONArray();
        JSONObject attachment = new JSONObject();
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
        return attachments;
    }

    // Voice recording methods
    private void startRecording() {
        if (isRecording) {
            return;
        }
        
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                REQUEST_CODE_RECORD_AUDIO_PERMISSION);
            return;
        }
        
        try {
            // Initialize handler for timer
            if (recordingHandler == null) {
                recordingHandler = new Handler(Looper.getMainLooper());
            }
            
            // Create audio file
            audioFile = new java.io.File(getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");
            
            // Initialize MediaRecorder
            mediaRecorder = new android.media.MediaRecorder();
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(96000);
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            isRecording = true;
            recordingStarted = true;
            
            // Show recording dialog
            showRecordingDialog();
            
        } catch (Exception e) {
            Log.e("BaseChatActivity", "Failed to start recording", e);
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
            recordingStarted = false;
            releaseRecorder();
        }
    }
    
    private void stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            // If recording never started, just clean up
            if (!recordingStarted) {
                hideRecordingDialog();
                if (audioFile != null && audioFile.exists()) {
                    audioFile.delete();
                    audioFile = null;
                }
            }
            return;
        }
        
        try {
            // Only try to stop if recording actually started
            if (recordingStarted) {
                mediaRecorder.stop();
            }
            releaseRecorder();
            
            // Hide recording dialog
            hideRecordingDialog();
            
            isRecording = false;
            recordingStarted = false;
            
            // Check if recording is long enough (at least 0.5 seconds)
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                long duration = audioFile.length() / 1000; // Rough estimate
                if (audioFile.length() < 1000) { // Less than ~1KB is too short
                    Toast.makeText(this, "Recording too short", Toast.LENGTH_SHORT).show();
                    if (audioFile.delete()) {
                        audioFile = null;
                    }
                    return;
                }
                
                // Upload and send voice message
                uploadAndSendVoiceMessage(audioFile);
            } else {
                if (recordingStarted) {
                    Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show();
                }
                // Clean up file if it exists but is invalid
                if (audioFile != null && audioFile.exists()) {
                    audioFile.delete();
                    audioFile = null;
                }
            }
            
        } catch (IllegalStateException e) {
            // MediaRecorder is in wrong state (likely never started or already stopped)
            Log.w("BaseChatActivity", "MediaRecorder in wrong state, cleaning up", e);
            releaseRecorder();
            isRecording = false;
            recordingStarted = false;
            hideRecordingDialog();
            
            // Clean up file
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
                audioFile = null;
            }
        } catch (Exception e) {
            Log.e("BaseChatActivity", "Failed to stop recording", e);
            Toast.makeText(this, "Failed to stop recording", Toast.LENGTH_SHORT).show();
            releaseRecorder();
            isRecording = false;
            recordingStarted = false;
            hideRecordingDialog();
            
            // Clean up file
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
                audioFile = null;
            }
        }
    }
    
    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e("BaseChatActivity", "Error releasing recorder", e);
            }
            mediaRecorder = null;
        }
    }
    
    private void showRecordingDialog() {
        if (recordingDialog != null && recordingDialog.isShowing()) {
            return;
        }
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_voice_recording, null);
        recordingTimerView = dialogView.findViewById(R.id.tv_recording_timer);
        
        // Get animation views
        ivRecordingIndicator = dialogView.findViewById(R.id.iv_recording_indicator);
        vPulseRing1 = dialogView.findViewById(R.id.v_pulse_ring_1);
        vPulseRing2 = dialogView.findViewById(R.id.v_pulse_ring_2);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);
        recordingDialog = builder.create();
        
        if (recordingDialog.getWindow() != null) {
            Window w = recordingDialog.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        recordingDialog.show();
        
        // Start animations
        startRecordingAnimations();
        
        // Start timer
        startRecordingTimer();
    }
    
    private void hideRecordingDialog() {
        if (recordingDialog != null && recordingDialog.isShowing()) {
            recordingDialog.dismiss();
        }
        stopRecordingAnimations();
        stopRecordingTimer();
    }
    
    private void startRecordingAnimations() {
        // Animate recording indicator
        if (ivRecordingIndicator != null) {
            android.view.animation.Animation indicatorAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.recording_pulse);
            if (indicatorAnimation != null) {
                ivRecordingIndicator.startAnimation(indicatorAnimation);
            }
        }
        
        // Animate pulse rings
        if (vPulseRing1 != null) {
            pulseRing1Animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.recording_pulse);
            if (pulseRing1Animation != null) {
                // Delay ring 1 slightly
                pulseRing1Animation.setStartOffset(200);
                vPulseRing1.startAnimation(pulseRing1Animation);
            }
        }
        
        if (vPulseRing2 != null) {
            pulseRing2Animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.recording_pulse);
            if (pulseRing2Animation != null) {
                // Delay ring 2 more
                pulseRing2Animation.setStartOffset(400);
                vPulseRing2.startAnimation(pulseRing2Animation);
            }
        }
    }
    
    private void stopRecordingAnimations() {
        // Stop indicator animation
        if (ivRecordingIndicator != null) {
            ivRecordingIndicator.clearAnimation();
        }
        
        // Stop pulse ring animations
        if (vPulseRing1 != null && pulseRing1Animation != null) {
            vPulseRing1.clearAnimation();
            pulseRing1Animation = null;
        }
        
        if (vPulseRing2 != null && pulseRing2Animation != null) {
            vPulseRing2.clearAnimation();
            pulseRing2Animation = null;
        }
    }
    
    private Runnable timerRunnable;
    private int recordingSeconds = 0;
    
    private void startRecordingTimer() {
        recordingSeconds = 0;
        if (timerRunnable != null && recordingHandler != null) {
            recordingHandler.removeCallbacks(timerRunnable);
        }
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording && recordingTimerView != null) {
                    int minutes = recordingSeconds / 60;
                    int seconds = recordingSeconds % 60;
                    recordingTimerView.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                    recordingSeconds++;
                    if (recordingHandler != null) {
                        recordingHandler.postDelayed(this, 1000);
                    }
                }
            }
        };
        
        if (recordingHandler != null) {
            recordingHandler.post(timerRunnable);
        }
    }
    
    private void stopRecordingTimer() {
        if (timerRunnable != null && recordingHandler != null) {
            recordingHandler.removeCallbacks(timerRunnable);
        }
        recordingSeconds = 0;
    }
    
    private void uploadAndSendVoiceMessage(java.io.File audioFile) {
        if (currentChat == null || audioFile == null || !audioFile.exists()) {
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Uploading voice message...", Toast.LENGTH_SHORT).show();
        
        String fileName = "voice_" + System.currentTimeMillis() + ".m4a";
        String mimeType = "audio/mp4";
        long fileSize = audioFile.length();
        String originalName = audioFile.getName();
        
        // Upload file to server using File-based method (recommended)
        apiClient.uploadChatFile(token, audioFile, originalName, mimeType, fileSize, currentChat.getId(), 
            new okhttp3.Callback() {
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            if (response.isSuccessful()) {
                                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    String fileUrl = jsonResponse.optString("fileUrl", "");
                                    String serverFileName = jsonResponse.optString("fileName", fileName);
                                    
                                    // Send voice message
                                    sendVoiceMessage(fileUrl, serverFileName, fileName, mimeType, fileSize);
                                } else {
                                    String errorMsg = jsonResponse.optString("message", "Failed to upload voice message");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(BaseChatActivity.this, "Failed to upload voice message: " + response.code(), 
                                    Toast.LENGTH_SHORT).show();
                            }
                        } catch (org.json.JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, "Error processing upload response", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }
    
    protected void sendVoiceMessage(String fileUrl, String fileName, String originalName, String mimeType, long fileSize) {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();
        
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create message object
        Message message = new Message();
        message.setContent(fileUrl);
        message.setSenderId(senderId);
        message.setChatId(currentChat.getId());
        message.setType("audio"); // Use "audio" to match server expectations
        message.setChatType(currentChat.isGroupChat() ? "group" : "private");
        message.setTimestamp(System.currentTimeMillis());
        String clientNonce = java.util.UUID.randomUUID().toString();
        message.setClientNonce(clientNonce);
        try { message.setId("local-" + message.getTimestamp()); } catch (Exception ignored) {}
        
        // Add to local list immediately for better UX
        message.setLocalSignature(buildLocalSignature(message));
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        forceScrollToBottom();
        
        // Send to server
        try {
            org.json.JSONObject messageJson = new org.json.JSONObject();
            messageJson.put("chatId", currentChat.getId());
            messageJson.put("type", "audio"); // Server expects "audio" not "voice"
            messageJson.put("content", ""); // Empty content for voice messages
            messageJson.put("timestamp", System.currentTimeMillis());
            messageJson.put("clientNonce", clientNonce);
            if (replyingToMessageId != null && !replyingToMessageId.isEmpty()) {
                messageJson.put("replyTo", replyingToMessageId);
            }
            
            // Create attachments array for voice message
            org.json.JSONArray attachments = new org.json.JSONArray();
            org.json.JSONObject attachment = new org.json.JSONObject();
            attachment.put("filename", fileName);
            attachment.put("originalName", originalName);
            attachment.put("mimeType", mimeType);
            attachment.put("size", fileSize);
            
            // Convert relative path to full URL
            String fullFileUrl = fileUrl;
            if (!fileUrl.startsWith("http")) {
                fullFileUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() +
                              ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + fileUrl;
            }
            attachment.put("url", fullFileUrl);
            attachments.put(attachment);
            messageJson.put("attachments", attachments);
            
            android.util.Log.d("BaseChatActivity", "Sending voice message: " + messageJson);
            
            apiClient.sendMessage(token, messageJson, new okhttp3.Callback() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            try {
                                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                                if (jsonResponse.optBoolean("success", false)) {
                                    clearReplyState();
                                } else {
                                    String errorMsg = jsonResponse.optString("message", "Failed to send voice message");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(BaseChatActivity.this, "Error processing server response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(BaseChatActivity.this, "Failed to send voice message: " + response.code(), 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send voice message failed: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Network error: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show());
                }
            });
            
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating voice message", Toast.LENGTH_SHORT).show();
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
        ivMore = findViewById(R.id.iv_more);
        ivVideoCall = findViewById(R.id.iv_video_call);
        ivSend = findViewById(R.id.iv_send);
        ivAttachment = findViewById(R.id.iv_attach);
        ivEmoji = findViewById(R.id.iv_sticker);
        ivGallery = findViewById(R.id.iv_attach);
        ivRecordAudio = findViewById(R.id.record_audio_button);
        etMessage = findViewById(R.id.et_message);
        rvMessages = findViewById(R.id.rv_messages);
        progressBar = findViewById(R.id.progress_bar);
        replyBar = findViewById(R.id.reply_bar);
        tvReplyAuthor = findViewById(R.id.tv_reply_author);
        tvReplyContent = findViewById(R.id.tv_reply_content);
        ivReplyClose = findViewById(R.id.iv_reply_close);
        offlineIndicator = findViewById(R.id.offline_indicator);
        summarizeIndicator = findViewById(R.id.summarize_indicator);
        
        // Log view initialization
        android.util.Log.d("BaseChatActivity", "Views initialized - ivBack: " + (ivBack != null) + 
                          ", ivProfile: " + (ivProfile != null) + 
                          ", tvChatName: " + (tvChatName != null));
        
        // Update offline indicator visibility
        updateOfflineIndicator();
    }

    protected void initData() {
        databaseManager = new DatabaseManager(this);
        messageRepository = new MessageRepository(this);
        syncManager = new OfflineMessageSyncManager(this);
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
                // Load lastSummarizedTimestamp for this chat
                loadLastSummarizedTimestamp();
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
        if (intent.hasExtra("forward_message")) {
            pendingForwardMessageRaw = intent.getStringExtra("forward_message");
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
    
    @SuppressLint("ClickableViewAccessibility")
    protected void setupClickListeners() {
        // Back button
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        if (ivSend != null) {
            ivSend.setOnClickListener(v -> handleSendMessage());
        }
        
        if (ivMore != null) {
            ivMore.setOnClickListener(v -> showChatOptions());
        }

        // Hide/disable call button for group chats
        if (ivVideoCall != null) {
            // Video call button is now available for both private and group chats
            ivVideoCall.setOnClickListener(v -> {
                Animation scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.button_scale);
                v.startAnimation(scaleAnimation);
                new Handler(Looper.getMainLooper()).postDelayed(this::handleVideoCall, 150);
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

        // Summarize button
        if (summarizeIndicator != null) {
            summarizeIndicator.setOnClickListener(v -> showChatSummary());
        }

        // Voice recording button - use touch listener for press/release
        if (ivRecordAudio != null) {
            ivRecordAudio.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startRecording();
                        v.setPressed(true);
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        stopRecording();
                        v.setPressed(false);
                        return true;
                }
                return false;
            });
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

    @SuppressLint("SetTextI18n")
    protected void applyBlockUiState(boolean blockedByMe, boolean blockedMe) {
        // Disable input and show a simple banner in status line
        if (etMessage != null) {
            etMessage.setEnabled(!(blockedByMe || blockedMe));
            etMessage.setHint(blockedMe ? "You cannot message this user" : (blockedByMe ? "You have blocked this user" : "Type a message"));
        }
        if (ivSend != null) ivSend.setEnabled(!(blockedByMe || blockedMe));
    }

    protected void setupRecyclerView() {
        String currentUserId = databaseManager.getUserId();
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

        // If there is a pending forward payload and chat is known, send it now (post to ensure UI ready)
        if (currentChat != null && ( (pendingForwardMessageRaw != null && !pendingForwardMessageRaw.isEmpty()) || (pendingForwardContent != null && !pendingForwardContent.isEmpty()) )) {
            rvMessages.postDelayed(() -> {
                try {
                    if (pendingForwardMessageRaw != null && !pendingForwardMessageRaw.isEmpty()) {
                        try {
                            org.json.JSONObject fwd = new org.json.JSONObject(pendingForwardMessageRaw);
                            String type = fwd.optString("type", "text");
                            if ("image".equals(type)) {
                                // image via attachments[0].url
                                org.json.JSONArray atts = fwd.optJSONArray("attachments");
                                if (atts != null && atts.length() > 0) {
                                    String url = atts.getJSONObject(0).optString("url", "");
                                    if (!url.isEmpty()) {
                                        sendImageMessage(url, null);
                                        return;
                                    }
                                }
                                // fallback to content
                                String c = fwd.optString("content", "");
                                if (!c.isEmpty()) { sendImageMessage(c, null); return; }
                            } else if ("file".equals(type)) {
                                org.json.JSONArray atts = fwd.optJSONArray("attachments");
                                if (atts != null && atts.length() > 0) {
                                    org.json.JSONObject a = atts.getJSONObject(0);
                                    String url = a.optString("url", "");
                                    String filename = a.optString("filename", "");
                                    String originalName = a.optString("originalName", filename);
                                    String mime = a.optString("mimeType", "application/octet-stream");
                                    long size = a.optLong("size", 0);
                                    if (!url.isEmpty()) { sendFileMessage(url, filename, originalName, mime, size); return; }
                                }
                                // fallback to text if no attachment
                                String c = fwd.optString("content", "");
                                if (!c.isEmpty()) { sendMessage(c);
                                }
                            } else {
                                String c = fwd.optString("content", "");
                                if (!c.isEmpty()) { sendMessage(c);
                                }
                            }
                        } catch (Exception ignored) {
                            if (pendingForwardContent != null && !pendingForwardContent.isEmpty()) {
                                sendMessage(pendingForwardContent);
                            }
                        }
                    } else if (pendingForwardContent != null && !pendingForwardContent.isEmpty()) {
                        sendMessage(pendingForwardContent);
                    }
                } finally {
                    pendingForwardContent = null;
                    pendingForwardMessageRaw = null;
                }
            }, 150);
        }
    }

    protected void loadMessages() {
        if (currentChat == null) return;

        // Clear messages list first to avoid duplicates
        messages.clear();
        
        // First, try to load from local database (works offline)
        loadMessagesFromDatabase();
        
        // Then try to load from server if network is available
        if (isNetworkAvailable()) {
            String token = databaseManager.getToken();
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
                                        // Save message to local database
                                        messageRepository.saveMessage(message);
                                        pageOne.add(message);
                                    }
                                    hasMore = messagesArray.length() >= pageSize;
                                }
                                boolean addedNewAtEnd = false;
                                if (!isRefresh) {
                                    // Initial load - clear and replace all messages
                                    messages.clear();
                                    // Deduplicate by ID before adding
                                    java.util.Set<String> seenIds = new java.util.HashSet<>();
                                    for (Message m : pageOne) {
                                        String msgId = m.getId();
                                        if (msgId != null && !msgId.isEmpty() && !seenIds.contains(msgId)) {
                                            messages.add(m);
                                            seenIds.add(msgId);
                                        }
                                    }
                                } else {
                                    // Refresh/polling - merge: upsert by id; if id not found, try replace local placeholder; else append
                                    for (Message m : pageOne) {
                                        String msgId = m.getId();
                                        if (msgId == null || msgId.isEmpty()) continue; // Skip messages without ID
                                        
                                        int existingIdx = indexOfMessageById(msgId);
                                        if (existingIdx >= 0) {
                                            // Update existing message
                                            messages.set(existingIdx, m);
                                            messageAdapter.notifyItemChanged(existingIdx);
                                        } else {
                                            // Check for local placeholder
                                            int localIdx = findLocalPlaceholderIndex(m);
                                            if (localIdx >= 0) {
                                                // Replace local placeholder
                                                messages.set(localIdx, m);
                                                messageAdapter.notifyItemChanged(localIdx);
                                            } else {
                                                // New message - add to end
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
                                
                                // Update summarize indicator
                                updateSummarizeIndicator();

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
                    // If we failed to load from server, show message from database
                    if (messages.isEmpty()) {
                        loadMessagesFromDatabase();
                    }
                    // Only show toast if we don't have any messages from database
                    if (messages.isEmpty()) {
                    }
                });
            }
        });
        } else {
            // No network - just show database messages (already loaded above)
            if (messages.isEmpty()) {
            }
        }
    }
    
    /**
     * Load messages from local database
     */
    private void loadMessagesFromDatabase() {
        if (currentChat == null || messageRepository == null) return;
        
        List<Message> dbMessages = messageRepository.getMessagesForChat(currentChat.getId(), 0);
        if (!dbMessages.isEmpty()) {
            messages.clear();
            messages.addAll(dbMessages);
            messageAdapter.notifyDataSetChanged();
            updateSummarizeIndicator();
            scrollToBottom();
            android.util.Log.d("BaseChatActivity", "Loaded " + dbMessages.size() + " messages from database");
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
    
    /**
     * Setup network listener to auto-sync pending messages when WiFi comes back
     */
    private void setupNetworkListener() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) return;
        
        NetworkRequest request = new NetworkRequest.Builder().build();
        NetworkCallback callback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // Network is available - sync pending messages
                android.util.Log.d("BaseChatActivity", "Network available, syncing pending messages");
                runOnUiThread(() -> {
                    updateOfflineIndicator();
                    if (syncManager != null) {
                        syncManager.syncPendingMessages();
                    }
                });
            }
            
            @Override
            public void onLost(Network network) {
                android.util.Log.d("BaseChatActivity", "Network lost");
                runOnUiThread(() -> updateOfflineIndicator());
            }
        };
        
        connectivityManager.registerNetworkCallback(request, callback);
    }
    
    /**
     * Update offline indicator visibility based on network status
     */
    private void updateOfflineIndicator() {
        if (offlineIndicator == null) return;
        
        boolean isOnline = isNetworkAvailable();
        offlineIndicator.setVisibility(isOnline ? View.GONE : View.VISIBLE);
    }
    
    // Track last summarization timestamp to only count new unread messages
    private long lastSummarizedTimestamp = 0;
    
    // Handler for auto-hiding summarize indicator after 30 seconds
    private Handler autoHideSummarizeHandler = null;
    private Runnable autoHideSummarizeRunnable = null;
    
    /**
     * Get the key for storing lastSummarizedTimestamp in SharedPreferences
     */
    private String getSummarizedTimestampKey() {
        if (currentChat == null) return null;
        return "last_summarized_" + currentChat.getId();
    }
    
    /**
     * Load lastSummarizedTimestamp from SharedPreferences
     */
    private void loadLastSummarizedTimestamp() {
        String key = getSummarizedTimestampKey();
        if (key == null) {
            lastSummarizedTimestamp = 0;
            android.util.Log.d("BaseChatActivity", "loadLastSummarizedTimestamp: key is null, reset to 0");
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        lastSummarizedTimestamp = prefs.getLong(key, 0);
        android.util.Log.d("BaseChatActivity", String.format(
            "loadLastSummarizedTimestamp: key=%s, timestamp=%d", key, lastSummarizedTimestamp
        ));
    }
    
    /**
     * Save lastSummarizedTimestamp to SharedPreferences
     */
    private void saveLastSummarizedTimestamp(long timestamp) {
        String key = getSummarizedTimestampKey();
        if (key == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        prefs.edit().putLong(key, timestamp).apply();
    }
    
    /**
     * Count unread messages (messages not sent by current user and not read)
     * After summarization, only count messages after lastSummarizedTimestamp
     * Also check local database for unread status before server marks them as read
     */
    private int countUnreadMessages() {
        if (messages == null || messages.isEmpty()) {
            android.util.Log.d("BaseChatActivity", "countUnreadMessages: messages is null or empty");
            return 0;
        }
        
        String currentUserId = databaseManager != null ? databaseManager.getUserId() : null;
        if (currentUserId == null) {
            android.util.Log.d("BaseChatActivity", "countUnreadMessages: currentUserId is null");
            return 0;
        }
        
        int count = 0;
        int totalMessages = messages.size();
        int skippedByTimestamp = 0;
        int skippedByReadStatus = 0;
        
        // Get unread message IDs from local database (before server marks them as read)
        java.util.Set<String> unreadMessageIds = new java.util.HashSet<>();
        if (messageRepository != null && currentChat != null) {
            List<Message> dbMessages = messageRepository.getMessagesForChat(currentChat.getId(), 0);
            for (Message dbMsg : dbMessages) {
                if (!dbMsg.isRead() && dbMsg.getSenderId() != null && 
                    !dbMsg.getSenderId().equals(currentUserId)) {
                    unreadMessageIds.add(dbMsg.getId());
                }
            }
        }
        
        for (Message message : messages) {
            // Count messages that are not from current user
            if (message.getSenderId() != null && 
                !message.getSenderId().equals(currentUserId) && 
                !message.isDeleted()) {
                
                // Check if message is unread (either from message.isRead() or from local database)
                boolean isUnread = !message.isRead() || 
                    (message.getId() != null && unreadMessageIds.contains(message.getId()));
                
                if (!isUnread) {
                    skippedByReadStatus++;
                    continue;
                }
                
                // If we've summarized before, only count messages after lastSummarizedTimestamp
                if (lastSummarizedTimestamp > 0) {
                    long messageTimestamp = message.getTimestamp();
                    if (messageTimestamp <= lastSummarizedTimestamp) {
                        skippedByTimestamp++;
                        continue; // Skip messages that were already summarized
                    }
                }
                count++;
            }
        }
        
        android.util.Log.d("BaseChatActivity", String.format(
            "countUnreadMessages: total=%d, unread=%d, lastSummarizedTimestamp=%d, skippedByTimestamp=%d, skippedByReadStatus=%d",
            totalMessages, count, lastSummarizedTimestamp, skippedByTimestamp, skippedByReadStatus
        ));
        
        return count;
    }
    
    /**
     * Update summarize indicator visibility based on unread message count
     */
    private void updateSummarizeIndicator() {
        if (summarizeIndicator == null) {
            android.util.Log.w("BaseChatActivity", "updateSummarizeIndicator: summarizeIndicator is null");
            return;
        }
        
        int unreadCount = countUnreadMessages();
        // Show summarize button when there are 13 or more unread messages
        boolean shouldShow = unreadCount >= 13;
        
        android.util.Log.d("BaseChatActivity", String.format(
            "updateSummarizeIndicator: unreadCount=%d, shouldShow=%s, currentVisibility=%s",
            unreadCount, shouldShow, 
            summarizeIndicator.getVisibility() == View.VISIBLE ? "VISIBLE" : "GONE"
        ));
        
        summarizeIndicator.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        
        // Cancel previous auto-hide timer if exists
        cancelAutoHideSummarizeTimer();
        
        // If showing, start auto-hide timer (30 seconds)
        if (shouldShow) {
            startAutoHideSummarizeTimer();
        }
    }
    
    /**
     * Start auto-hide timer for summarize indicator (30 seconds)
     */
    private void startAutoHideSummarizeTimer() {
        if (autoHideSummarizeHandler == null) {
            autoHideSummarizeHandler = new Handler(Looper.getMainLooper());
        }
        
        // Cancel previous timer if exists
        cancelAutoHideSummarizeTimer();
        
        autoHideSummarizeRunnable = new Runnable() {
            @Override
            public void run() {
                // Auto-hide summarize indicator after 30 seconds
                if (summarizeIndicator != null && summarizeIndicator.getVisibility() == View.VISIBLE) {
                    summarizeIndicator.setVisibility(View.GONE);
                    // Update lastSummarizedTimestamp to current time to prevent showing again
                    lastSummarizedTimestamp = System.currentTimeMillis();
                    saveLastSummarizedTimestamp(lastSummarizedTimestamp);
                }
            }
        };
        
        // Schedule to hide after 30 seconds (30000 milliseconds)
        autoHideSummarizeHandler.postDelayed(autoHideSummarizeRunnable, 30000);
    }
    
    /**
     * Cancel auto-hide timer for summarize indicator
     */
    private void cancelAutoHideSummarizeTimer() {
        if (autoHideSummarizeHandler != null && autoHideSummarizeRunnable != null) {
            autoHideSummarizeHandler.removeCallbacks(autoHideSummarizeRunnable);
            autoHideSummarizeRunnable = null;
        }
    }
    
    /**
     * Show chat summary dialog
     */
    private void showChatSummary() {
        if (currentChat == null) return;
        
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
            .setMessage("Summarizing chat...")
            .setCancelable(false)
            .create();
        loadingDialog.show();
        
        String token = databaseManager.getToken();
        apiClient.summarizeChat(token, currentChat.getId(), new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                String summary = jsonResponse.optJSONObject("data")
                                    .optString("summary", "Unable to generate summary");
                                
                                // Update lastSummarizedTimestamp to current time
                                // This ensures we only count new unread messages after summarization
                                lastSummarizedTimestamp = System.currentTimeMillis();
                                // Save to SharedPreferences to persist across activity restarts
                                saveLastSummarizedTimestamp(lastSummarizedTimestamp);
                                
                                showSummaryDialog(summary);
                                // Hide summarize indicator after successful summarization
                                updateSummarizeIndicator();
                            } else {
                                String errorMsg = jsonResponse.optString("message", "Unable to generate summary");
                                Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, "Error processing response", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Error: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(BaseChatActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Show summary dialog with the chat summary (Meta AI style)
     */
    private void showSummaryDialog(String summary) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chat_summary, null);
        TextView tvSummary = dialogView.findViewById(R.id.tv_summary);
        
        // Format summary text: convert **bold** to SpannableString with bold style
        android.text.SpannableString spannable = formatSummaryText(summary);
        tvSummary.setText(spannable);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();
    }
    
    /**
     * Format summary text: convert **text** to bold and improve formatting
     */
    private android.text.SpannableString formatSummaryText(String text) {
        // First, replace **text** with just text and track positions
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        java.util.List<java.util.Map<String, Object>> boldRanges = new java.util.ArrayList<>();
        StringBuffer result = new StringBuffer();
        int currentPos = 0;
        
        while (matcher.find()) {
            // Add text before the match
            result.append(text.substring(currentPos, matcher.start()));
            
            String boldText = matcher.group(1);
            int boldStart = result.length();
            result.append(boldText);
            int boldEnd = result.length();
            
            // Store the range for later styling
            java.util.Map<String, Object> range = new java.util.HashMap<>();
            range.put("start", boldStart);
            range.put("end", boldEnd);
            boldRanges.add(range);
            
            currentPos = matcher.end();
        }
        
        // Add remaining text
        result.append(text.substring(currentPos));
        
        // Create SpannableString and apply bold styles
        android.text.SpannableString spannable = new android.text.SpannableString(result.toString());
        
        for (java.util.Map<String, Object> range : boldRanges) {
            int start = (Integer) range.get("start");
            int end = (Integer) range.get("end");
            android.text.style.StyleSpan boldSpan = new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
            spannable.setSpan(boldSpan, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        return spannable;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Restart auto-hide timer if summarize indicator is visible
        if (summarizeIndicator != null && summarizeIndicator.getVisibility() == View.VISIBLE) {
            startAutoHideSummarizeTimer();
        }
        
        // Update offline indicator
        updateOfflineIndicator();
        
        // Update summarize indicator
        updateSummarizeIndicator();
        
        // Sync pending messages when activity resumes (in case network came back)
        if (syncManager != null && isNetworkAvailable()) {
            syncManager.syncPendingMessages();
        }
    }

    private void loadMoreMessages() {
        if (currentChat == null || isLoadingMore || !hasMore) return;
        isLoadingMore = true;
        String token = databaseManager.getToken();
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
                                // Save to database
                                if (messageRepository != null) {
                                    messageRepository.saveMessage(m);
                                }
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

        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();

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

        // Save message to database first (with pending status if offline)
        // Don't set ID - MessageRepository will generate temp ID for offline messages
        message.setId(null);
        Message savedMessage = messageRepository.saveMessage(message);
        if (savedMessage != null) {
            message = savedMessage; // Use saved message with temp ID
        }
        
        // Create final reference for use in lambda
        final Message finalMessage = message;
        final String finalMessageId = message.getId();
        final int messagePosition = messages.size();
        
        // Add to local list immediately for better UX
        message.setLocalSignature(buildLocalSignature(message));
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        // Always scroll to bottom when user sends a message
        forceScrollToBottom();

        // Clear input
        etMessage.setText("");

        // Check network availability
        if (!isNetworkAvailable()) {
            // Offline: Message already saved with pending status
            messageAdapter.notifyItemChanged(messages.size() - 1); // Update UI to show pending status
            return;
        }

        // Online: Send to server
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
                                            Message serverMessage = Message.fromJson(m);
                                            
                                            // Update sync status in database
                                            messageRepository.updateSyncStatus(
                                                finalMessageId,
                                                serverMessage.getId(),
                                                "synced",
                                                null
                                            );
                                            
                                            // Replace the last local placeholder that matches signature
                                            int idx = findLocalPlaceholderIndex(serverMessage);
                                            if (idx >= 0) {
                                                messages.set(idx, serverMessage);
                                                messageAdapter.notifyItemChanged(idx);
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                    clearReplyState();
                                } else {
                                    // Save as pending instead of removing
                                    finalMessage.setId(null);
                                    messageRepository.saveMessage(finalMessage);
                                    if (messagePosition < messages.size()) {
                                        messageAdapter.notifyItemChanged(messagePosition);
                                    }
                                    String errorMsg = jsonResponse.optString("message", "Failed to send message");
                                }
                            } catch (JSONException e) {
                                Log.e("BaseActivity", "Error processing response", e);
                                // Save as pending instead of removing
                                finalMessage.setId(null);
                                messageRepository.saveMessage(finalMessage);
                                if (messagePosition < messages.size()) {
                                    messageAdapter.notifyItemChanged(messagePosition);
                                }
                            }
                        } else {
                            // Save as pending instead of removing
                            finalMessage.setId(null);
                            messageRepository.saveMessage(finalMessage);
                            if (messagePosition < messages.size()) {
                                messageAdapter.notifyItemChanged(messagePosition);
                            }
                        }
                    });
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFailure(Call call, IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send message failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        // Save as pending instead of removing
                        finalMessage.setId(null);
                        messageRepository.saveMessage(finalMessage);
                        if (messagePosition < messages.size()) {
                            messageAdapter.notifyItemChanged(messagePosition);
                        }
                        Toast.makeText(BaseChatActivity.this, "No connection. Message will be sent when network is available.", Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e("BaseActivity", "Error preparing message", e);
            // Save as pending
            finalMessage.setId(null);
            messageRepository.saveMessage(finalMessage);
            Toast.makeText(this, "Error preparing message. Will retry later.", Toast.LENGTH_SHORT).show();
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
            String token = databaseManager.getToken();
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
        // Cancel auto-hide timer
        cancelAutoHideSummarizeTimer();
        // Clean up recording resources
        if (isRecording) {
            try {
                stopRecording();
            } catch (Exception e) {
                Log.e("BaseChatActivity", "Error stopping recording in onDestroy", e);
            }
        }
        releaseRecorder();
        hideRecordingDialog();
        if (audioFile != null && audioFile.exists()) {
            audioFile.delete();
            audioFile = null;
        }
        if (timerRunnable != null && recordingHandler != null) {
            recordingHandler.removeCallbacks(timerRunnable);
        }
        isRecording = false;
        recordingStarted = false;
        
        // Stop voice playback
        stopVoicePlayback();
        
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

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onReactClick(Message message, String emoji) {
        String token = databaseManager.getToken();
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
            
            // First check if message already exists by ID (most reliable)
            int idx = indexOfMessageById(incoming.getId());
            if (idx >= 0) {
                // Message already exists, just update it
                messages.set(idx, incoming);
                messageAdapter.notifyItemChanged(idx);
                // Still save to database to ensure sync
                if (messageRepository != null) {
                    messageRepository.saveMessage(incoming);
                }
                return;
            }
            
            // Check for local placeholder (for offline messages that were just synced)
            int localIdx = findLocalPlaceholderIndex(incoming);
            if (localIdx >= 0) {
                // Replace local placeholder with server message
                messages.set(localIdx, incoming);
                messageAdapter.notifyItemChanged(localIdx);
                // Save to database
                if (messageRepository != null) {
                    messageRepository.saveMessage(incoming);
                }
                // Update summarize indicator when new message arrives
                updateSummarizeIndicator();
                // Only scroll to bottom if user is already at the bottom
                scrollToBottomIfAtBottom();
                return;
            }
            
            // New message - add to list and save to database
            // Save to database first
            if (messageRepository != null) {
                messageRepository.saveMessage(incoming);
            }
            // Add to UI list
            messages.add(incoming);
            messageAdapter.notifyItemInserted(messages.size() - 1);
            // Update summarize indicator when new message arrives
            updateSummarizeIndicator();
            // Only scroll to bottom if user is already at the bottom
            scrollToBottomIfAtBottom();
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
            
            // Update local database to mark message as deleted
            if (messageRepository != null && messageId != null && !messageId.isEmpty()) {
                messageRepository.deleteMessage(messageId);
            }
            
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
        String currentUserId = databaseManager != null ? databaseManager.getUserId() : null;
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
            String uid = databaseManager != null ? databaseManager.getUserId() : "";
            String type = m.isImageMessage() ? "image" : (m.isTextMessage() ? "text" : m.getType());
            String contentKey = m.isTextMessage() ? (m.getContent() != null ? m.getContent() : "") : "img";
            return uid + "|" + type + "|" + contentKey;
        } catch (Exception ignored) { return null; }
    }

    protected void showMessageOptions(Message message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_message_options, null);
        
        boolean isOwnMessage = message.getSenderId() != null && message.getSenderId().equals(databaseManager.getUserId());
        boolean canEdit = message.isTextMessage() && isOwnMessage;
        
        // Show/hide edit option based on permissions
        dialogView.findViewById(R.id.option_edit).setVisibility(canEdit ? View.VISIBLE : View.GONE);
        
        // Hide delete for messages from others
        View deleteOption = dialogView.findViewById(R.id.option_delete);
        if (deleteOption != null) deleteOption.setVisibility(isOwnMessage ? View.VISIBLE : View.GONE);

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
            if (isOwnMessage) {
                confirmDeleteForEveryone(message);
                if (currentDialog != null) currentDialog.dismiss();
            }
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
            try {
                // Build a minimal forward payload with type/content/attachments
                org.json.JSONObject fwd = new org.json.JSONObject();
                fwd.put("type", message.isImageMessage() ? "image" : (message.isFileMessage() ? "file" : "text"));
                fwd.put("content", message.getContent() != null ? message.getContent() : "");
                if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                    fwd.put("attachments", new org.json.JSONArray(message.getAttachments()));
                }
                i.putExtra("forward_message", fwd.toString());
            } catch (Exception ignored) {
                String forwardContent = message.getContent();
                i.putExtra("forward_content", forwardContent != null ? forwardContent : "");
            }
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

    @SuppressLint({"NotifyDataSetChanged", "SetTextI18n"})
    protected void showReactionPicker(Message message) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_react_picker, null);

        TextView title = dialogView.findViewById(R.id.tv_title);
        View btnRemove = dialogView.findViewById(R.id.btn_remove_react);
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
                String token = databaseManager.getToken();
                if (token != null && !token.isEmpty()) {
                    // Determine current user's emoji to remove from message.reactionsRaw
                    String myUserId = databaseManager.getUserId();
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
        com.example.chatappjava.utils.DialogUtils.showEditDialog(
            this,
            "Edit Message",
            message.getContent(),
            "Save",
            "Cancel",
            newContent -> {
                if (!newContent.isEmpty() && !newContent.equals(message.getContent())) {
                    performEditMessage(message, newContent);
                }
            },
            null,
            false
        );
    }

    protected void performEditMessage(Message message, String newContent) {
        String token = databaseManager.getToken();
        apiClient.editMessage(token, message.getId(), newContent, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, "Failed to edit message", Toast.LENGTH_SHORT).show());
            }

            @SuppressLint("NotifyDataSetChanged")
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

    protected void confirmDeleteForEveryone(Message message) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            "Delete for everyone",
            "This will delete the message and attachments for all participants. Continue?",
            "Delete",
            "Cancel",
            () -> deleteMessageForEveryone(message),
            null,
            false
        );
    }

    protected void deleteMessageForEveryone(Message message) {
        String token = databaseManager.getToken();
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
                        // Update local database to mark message as deleted
                        if (messageRepository != null) {
                            messageRepository.deleteMessage(message.getId());
                        }
                        
                        // Remove locally to reflect immediate deletion
                        int idx = indexOfMessageById(message.getId());
                        if (idx >= 0) {
                            messages.remove(idx);
                            messageAdapter.notifyItemRemoved(idx);
                        } else {
                            messageAdapter.notifyDataSetChanged();
                        }
                        Toast.makeText(BaseChatActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BaseChatActivity.this, "Failed to delete: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    
    
    @Override
    public void onVoiceMessageClick(String voiceUrl) {
        // Find the message that contains this voice URL
        Message voiceMessage = null;
        for (Message msg : messages) {
            if (msg.isVoiceMessage()) {
                String[] voiceData = parseVoiceDataFromMessage(msg);
                if (voiceData != null && voiceData.length > 0 && voiceData[0].equals(voiceUrl)) {
                    voiceMessage = msg;
                    break;
                }
            }
        }
        
        String messageId = voiceMessage != null ? voiceMessage.getId() : null;
        
        // If already playing this message, pause it (keep progress)
        if (currentVoicePlayer != null && messageId != null && messageId.equals(currentlyPlayingMessageId)) {
            boolean isCurrentlyPlaying = false;
            try {
                isCurrentlyPlaying = currentVoicePlayer.isPlaying();
            } catch (IllegalStateException e) {
                // MediaPlayer might be in invalid state, assume it's playing if player exists
                android.util.Log.w("BaseChatActivity", "MediaPlayer in invalid state, assuming playing", e);
                isCurrentlyPlaying = true;
            } catch (Exception e) {
                android.util.Log.e("BaseChatActivity", "Error checking playback state", e);
            }
            
            if (isCurrentlyPlaying) {
                // Currently playing - pause it
                android.util.Log.d("BaseChatActivity", "Pausing playback for message: " + messageId);
                stopVoicePlayback(false); // Pause - don't clear progress
            } else {
                // Already paused - resume it
                android.util.Log.d("BaseChatActivity", "Resuming playback for message: " + messageId);
                playVoiceMessage(voiceUrl, messageId);
            }
            return;
        }
        
        // Stop any currently playing message (keep its progress for resume)
        if (currentVoicePlayer != null) {
            stopVoicePlayback(false); // Pause old message - keep progress
        }
        
        // Play the new voice message (will reset progress for new message)
        playVoiceMessage(voiceUrl, messageId);
    }
    
    private String[] parseVoiceDataFromMessage(Message message) {
        try {
            if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                org.json.JSONArray attachments = new org.json.JSONArray(message.getAttachments());
                if (attachments.length() > 0) {
                    org.json.JSONObject attachment = attachments.getJSONObject(0);
                    String voiceUrl = attachment.optString("url", "");
                    
                    // Convert relative URL to full URL
                    if (!voiceUrl.startsWith("http")) {
                        voiceUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + ":" + 
                                  com.example.chatappjava.config.ServerConfig.getServerPort() + voiceUrl;
                    }
                    
                    return new String[]{voiceUrl};
                }
            }
            // Fallback to content field
            String content = message.getContent();
            if (content != null && !content.isEmpty()) {
                if (!content.startsWith("http")) {
                    content = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + ":" + 
                             com.example.chatappjava.config.ServerConfig.getServerPort() + content;
                }
                return new String[]{content};
            }
        } catch (Exception e) {
            Log.e("BaseChatActivity", "Error parsing voice data", e);
        }
        return null;
    }
    
    private void playVoiceMessage(String voiceUrl, String messageId) {
        try {
            // Check if we're resuming the same message BEFORE stopping
            String oldMessageId = currentlyPlayingMessageId;
            boolean wasSameMessage = (messageId != null && oldMessageId != null && messageId.equals(oldMessageId));
            
            // Get saved position BEFORE stopping (important - retrieve position before stopping)
            int resumePosition = 0;
            if (messageId != null) {
                resumePosition = com.example.chatappjava.adapters.MessageAdapter.getMessagePosition(messageId);
                android.util.Log.d("BaseChatActivity", "Retrieved saved position for " + messageId + ": " + resumePosition + "ms");
            }
            
            // Stop any existing playback (this will save position if it's currently playing)
            if (currentVoicePlayer != null && currentVoicePlayer.isPlaying()) {
                stopVoicePlayback(false); // Don't clear progress, just stop
                
                // Re-check position after stopping in case it was just saved
                if (messageId != null && wasSameMessage && resumePosition == 0) {
                    resumePosition = com.example.chatappjava.adapters.MessageAdapter.getMessagePosition(messageId);
                    android.util.Log.d("BaseChatActivity", "Re-checked position after stop: " + resumePosition + "ms");
                }
            } else if (currentVoicePlayer != null) {
                // Player exists but not playing, just release it
                try {
                    currentVoicePlayer.release();
                } catch (Exception e) {
                    android.util.Log.w("BaseChatActivity", "Error releasing MediaPlayer", e);
                }
                currentVoicePlayer = null;
            }
            
            // Check if we're resuming based on saved position (not just same message check)
            // A message with saved position > 0 means it was paused and should resume
            boolean isResuming = (messageId != null && resumePosition > 0);
            
            if (isResuming) {
                // Resuming - use the saved position
                android.util.Log.d("BaseChatActivity", "Resuming message " + messageId + " from position: " + resumePosition + "ms");
            } else if (messageId != null) {
                // Starting fresh - ensure no saved position
                if (!wasSameMessage) {
                    com.example.chatappjava.adapters.MessageAdapter.clearMessageProgress(messageId);
                }
                resumePosition = 0; // Ensure we start from beginning
                android.util.Log.d("BaseChatActivity", "Starting " + (wasSameMessage ? "same" : "new") + " message " + messageId + " from beginning");
            }
            
            currentVoicePlayer = new android.media.MediaPlayer();
            
            // Set audio attributes for voice playback
            android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            currentVoicePlayer.setAudioAttributes(audioAttributes);
            
            currentVoicePlayer.setDataSource(voiceUrl);
            currentVoicePlayer.prepareAsync();
            
            final int seekPosition = resumePosition;
            final boolean shouldSeek = isResuming && seekPosition > 0;
            currentVoicePlayer.setOnPreparedListener(mp -> {
                // Seek to saved position if resuming
                if (shouldSeek) {
                    try {
                        int duration = mp.getDuration();
                        if (duration > 0 && seekPosition < duration) {
                            mp.seekTo(seekPosition);
                            android.util.Log.d("BaseChatActivity", "Seeked to position: " + seekPosition + "ms (duration: " + duration + "ms)");
                        } else {
                            android.util.Log.w("BaseChatActivity", "Invalid seek position: " + seekPosition + " (duration: " + duration + "ms)");
                        }
                    } catch (IllegalStateException e) {
                        android.util.Log.e("BaseChatActivity", "MediaPlayer not in valid state for seeking", e);
                    } catch (Exception e) {
                        android.util.Log.e("BaseChatActivity", "Error seeking to position", e);
                    }
                }
                
                try {
                    mp.start();
                    currentlyPlayingMessageId = messageId;
                    updateVoiceMessagePlayState(messageId, true);
                    startPlaybackUpdates(messageId);
                } catch (Exception e) {
                    android.util.Log.e("BaseChatActivity", "Error starting playback", e);
                }
            });
            
            // Release when playback completes
            currentVoicePlayer.setOnCompletionListener(mp -> {
                // Set progress to 100% when playback completes, but DON'T reset position
                // Keep the position at the end so user can see it finished
                if (messageId != null) {
                    com.example.chatappjava.adapters.MessageAdapter.updateMessageProgress(messageId, 100);
                    // Don't reset position - keep it at the end (or full duration) for visual consistency
                    try {
                        int duration = mp.getDuration();
                        if (duration > 0) {
                            com.example.chatappjava.adapters.MessageAdapter.saveMessagePosition(messageId, duration);
                        }
                    } catch (Exception e) {
                        android.util.Log.w("BaseChatActivity", "Could not get duration on completion", e);
                    }
                }
                stopVoicePlayback(false); // Don't clear progress on completion
                if (messageId != null) {
                    updateVoiceMessagePlayState(messageId, false);
                }
            });
            
            // Handle errors
            currentVoicePlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("BaseChatActivity", "MediaPlayer error: what=" + what + ", extra=" + extra);
                stopVoicePlayback(false); // Don't clear progress on error, keep current position
                if (messageId != null) {
                    updateVoiceMessagePlayState(messageId, false);
                }
                runOnUiThread(() -> Toast.makeText(this, "Failed to play voice message", Toast.LENGTH_SHORT).show());
                return true;
            });
            
        } catch (Exception e) {
            Log.e("BaseChatActivity", "Failed to play voice message", e);
            runOnUiThread(() -> Toast.makeText(this, "Failed to play voice message: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            stopVoicePlayback(false);
        }
    }
    
    private void stopVoicePlayback(boolean clearProgress) {
        // Save current progress and position before stopping if we're pausing (not clearing)
        if (!clearProgress && currentVoicePlayer != null && currentlyPlayingMessageId != null) {
            try {
                int currentPosition = 0;
                boolean wasPlaying = false;
                
                try {
                    if (currentVoicePlayer.isPlaying()) {
                        wasPlaying = true;
                    }
                    currentPosition = currentVoicePlayer.getCurrentPosition();
                    android.util.Log.d("BaseChatActivity", "Got current position: " + currentPosition + "ms (wasPlaying: " + wasPlaying + ")");
                } catch (IllegalStateException e) {
                    // MediaPlayer might be in invalid state, try to get position from progress
                    android.util.Log.w("BaseChatActivity", "Could not get current position from MediaPlayer", e);
                    try {
                        int duration = currentVoicePlayer.getDuration();
                        int progress = com.example.chatappjava.adapters.MessageAdapter.getMessageProgress(currentlyPlayingMessageId);
                        if (duration > 0 && progress > 0) {
                            currentPosition = (int) ((progress * duration) / 100L);
                            android.util.Log.d("BaseChatActivity", "Estimated position from progress: " + currentPosition + "ms");
                        } else {
                            android.util.Log.w("BaseChatActivity", "Could not estimate position - duration: " + duration + ", progress: " + progress);
                        }
                    } catch (Exception ex) {
                        android.util.Log.w("BaseChatActivity", "Could not estimate position", ex);
                    }
                }
                
                // Save position even if 0 (as long as it's a valid value)
                // This ensures we can track that we paused, even at the start
                if (currentPosition >= 0) {
                    // Save position for resume
                    com.example.chatappjava.adapters.MessageAdapter.saveMessagePosition(
                        currentlyPlayingMessageId, currentPosition);
                    
                    // Save progress percentage
                    try {
                        int duration = currentVoicePlayer.getDuration();
                        if (duration > 0 && currentPosition > 0) {
                            int progressPercent = (int) ((currentPosition * 100L) / duration);
                            if (progressPercent >= 0 && progressPercent <= 100) {
                                com.example.chatappjava.adapters.MessageAdapter.updateMessageProgress(
                                    currentlyPlayingMessageId, progressPercent);
                                android.util.Log.d("BaseChatActivity", "Saved progress: " + progressPercent + "%");
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.w("BaseChatActivity", "Could not get duration for progress calculation", e);
                    }
                    
                    android.util.Log.d("BaseChatActivity", "Saved position: " + currentPosition + "ms for message: " + currentlyPlayingMessageId);
                } else {
                    android.util.Log.w("BaseChatActivity", "Invalid position value: " + currentPosition + ", not saving");
                }
            } catch (Exception e) {
                android.util.Log.e("BaseChatActivity", "Error saving position", e);
            }
        }
        
        String playingId = currentlyPlayingMessageId;
        
        if (currentVoicePlayer != null) {
            try {
                // Check if playing before trying to stop, handle IllegalStateException
                boolean wasPlaying = false;
                try {
                    wasPlaying = currentVoicePlayer.isPlaying();
                } catch (IllegalStateException e) {
                    // MediaPlayer might be in invalid state (e.g., STOPPED)
                    android.util.Log.w("BaseChatActivity", "MediaPlayer state issue when checking isPlaying", e);
                    // Try to stop anyway if we're in an active state
                    wasPlaying = true;
                }
                
                if (wasPlaying) {
                    try {
                        currentVoicePlayer.stop();
                    } catch (IllegalStateException e) {
                        android.util.Log.w("BaseChatActivity", "MediaPlayer already stopped", e);
                    }
                }
                
                try {
                    currentVoicePlayer.release();
                } catch (Exception e) {
                    android.util.Log.w("BaseChatActivity", "Error releasing MediaPlayer", e);
                }
            } catch (Exception e) {
                Log.e("BaseChatActivity", "Error stopping playback", e);
            } finally {
                currentVoicePlayer = null;
            }
        }
        
        currentlyPlayingMessageId = null;
        stopPlaybackUpdates();
        
        if (playingId != null) {
            // Clear progress only if explicitly requested
            if (clearProgress) {
                com.example.chatappjava.adapters.MessageAdapter.clearMessageProgress(playingId);
            }
            // Ensure UI update happens on UI thread
            runOnUiThread(() -> {
                updateVoiceMessagePlayState(playingId, false);
            });
        }
    }
    
    private void stopVoicePlayback() {
        stopVoicePlayback(false); // Default: don't clear progress (for pause)
    }
    
    private void startPlaybackUpdates(String messageId) {
        if (playbackHandler == null) {
            playbackHandler = new Handler(Looper.getMainLooper());
        }
        
        stopPlaybackUpdates();
        
        playbackUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentVoicePlayer != null && currentVoicePlayer.isPlaying()) {
                    try {
                        int currentPosition = currentVoicePlayer.getCurrentPosition();
                        int duration = currentVoicePlayer.getDuration();
                        if (duration > 0) {
                            updateVoiceMessageProgress(messageId, currentPosition, duration);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                    if (playbackHandler != null) {
                        playbackHandler.postDelayed(this, 100); // Update every 100ms
                    }
                }
            }
        };
        
        playbackHandler.post(playbackUpdateRunnable);
    }
    
    private void stopPlaybackUpdates() {
        if (playbackUpdateRunnable != null && playbackHandler != null) {
            playbackHandler.removeCallbacks(playbackUpdateRunnable);
        }
        playbackUpdateRunnable = null;
    }
    
    private void updateVoiceMessagePlayState(String messageId, boolean isPlaying) {
        if (messageAdapter == null) return;
        
        runOnUiThread(() -> {
            // Update the adapter's currently playing message ID
            if (isPlaying) {
                messageAdapter.setCurrentlyPlayingMessageId(messageId);
            } else {
                messageAdapter.setCurrentlyPlayingMessageId(null);
            }
        });
    }
    
    private void updateVoiceMessageProgress(String messageId, int currentMs, int totalMs) {
        if (messageAdapter == null || messageId == null || totalMs <= 0) return;
        
        int progressPercent = (int) ((currentMs * 100L) / totalMs);
        if (progressPercent < 0) progressPercent = 0;
        if (progressPercent > 100) progressPercent = 100;

        int finalProgressPercent = progressPercent;
        runOnUiThread(() -> {
            // Update progress in adapter
            com.example.chatappjava.adapters.MessageAdapter.updateMessageProgress(messageId, finalProgressPercent);
            
            // Find and update the specific message item
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (msg != null && msg.getId() != null && msg.getId().equals(messageId) && msg.isVoiceMessage()) {
                    messageAdapter.notifyItemChanged(i);
                    break;
                }
            }
        });
    }

    @Override
    public void onFileClick(String fileUrl, String fileName, String originalName, String mimeType, long fileSize) {
        try {
            java.io.File downloaded = getDownloadedFile(originalName);
            if (downloaded != null && downloaded.exists()) {
                // Open local file
                openLocalFile(downloaded, mimeType);
            } else {
                // Download file
                startDownload(fileUrl, originalName != null ? originalName : fileName);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot handle file", Toast.LENGTH_SHORT).show();
        }
    }

    private java.io.File getDownloadedFile(String originalName) {
        java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) return null;
        return new java.io.File(dir, originalName);
    }

    private void startDownload(String relativeOrAbsoluteUrl, String targetName) {
        String baseUrl;
        String fullUrl;
        if (relativeOrAbsoluteUrl != null && relativeOrAbsoluteUrl.startsWith("http")) {
            fullUrl = relativeOrAbsoluteUrl;
        } else {
            baseUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + ":" + com.example.chatappjava.config.ServerConfig.getServerPort();
            fullUrl = baseUrl + relativeOrAbsoluteUrl;
        }

        android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        android.net.Uri uri = android.net.Uri.parse(fullUrl);
        android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(uri);
        req.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI | android.app.DownloadManager.Request.NETWORK_MOBILE);
        req.setTitle(targetName);
        req.setDescription("Downloading file...");
        req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, targetName);

        dm.enqueue(req);
        Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
    }

    private void openLocalFile(java.io.File file, String mimeType) {
        try {
            android.net.Uri contentUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            String resolvedMime = (mimeType != null && !mimeType.isEmpty()) ? mimeType : guessMimeFromName(file.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, resolvedMime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Fallback: generic chooser with */*
                Intent chooser = new Intent(Intent.ACTION_VIEW);
                chooser.setDataAndType(contentUri, "*/*");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(chooser, "Open with"));
            }
        } catch (Exception e) {
            // Fallback: open with generic chooser
            try {
                String resolvedMime = (mimeType != null && !mimeType.isEmpty()) ? mimeType : guessMimeFromName(file.getName());
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(android.net.Uri.fromFile(file), resolvedMime);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "Open with"));
            } catch (Exception ignored) {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String guessMimeFromName(String name) {
        String lower = name != null ? name.toLowerCase() : "";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/octet-stream";
    }
}
