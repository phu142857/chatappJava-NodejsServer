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
import com.example.chatappjava.utils.AvatarSyncCoordinator;
import com.example.chatappjava.utils.ConversationPreviewHelper;
import com.example.chatappjava.utils.ConversationRepository;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.MessageRepository;
import com.example.chatappjava.utils.OfflineMessageSyncManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
    protected TextView tvChatStatus;
    protected View tvMessagesEmpty;
    protected ImageView ivMore, ivSend, ivAttachment, ivEmoji, ivGallery, ivVideoCall, ivRecordAudio;
    protected EditText etMessage;
    protected RecyclerView rvMessages;
    protected View messagesLoadingSkeleton;
    protected ProgressBar progressBar;
    protected ProgressBar sendUploadProgress;
    // Reply UI state
    protected android.view.View replyBar;
    protected TextView tvReplyAuthor;
    protected TextView tvReplyContent;
    protected ImageView ivReplyClose;
    // Offline indicator
    protected android.view.View offlineIndicator;
    // Summarize indicator
    protected android.view.View summarizeIndicator;
    // Scroll to bottom button
    protected android.widget.Button btnScrollToBottom;
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
    protected ConversationRepository conversationRepository;
    protected OfflineMessageSyncManager syncManager; // For offline message sync
    private ConnectivityManager networkConnectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    protected com.example.chatappjava.utils.SyncManager backgroundSyncManager; // For background delta sync
    protected ApiClient apiClient;
    protected AvatarManager avatarManager;
    private AvatarSyncCoordinator.Listener avatarSyncListener;
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
    private static final long APPEND_DEBOUNCE_MS = 150;
    private static final long OFFLINE_TOAST_DEBOUNCE_MS = 3000;
    private static final long PLACEHOLDER_MATCH_WINDOW_MS = 5000L;
    private static final long SEND_TIMEOUT_RECONCILE_MS = 3000L;
    private static final long NETWORK_RESTORE_POST_DELAY_MS = 5000L;
    private final ConcurrentHashMap<String, String> nonceToTempId = new ConcurrentHashMap<>();
    private long lastOfflineSendToastAt = 0;
    private final Handler appendHandler = new Handler(Looper.getMainLooper());
    private Runnable appendFromDbRunnable;
    private SocketManager.MessageListener chatMessageListener;
    private SocketManager.ConnectionListener realtimeConnectionListener;
    private boolean socketWasDisconnected = false;
    protected boolean hasNewMessages = false;
    protected boolean isInitialLoad = true;
    private boolean isMessagesLoading = false;
    // Smart auto-scroll state (like Messenger)
    protected boolean shouldAutoScroll = true; // Auto-scroll enabled by default
    protected int newMessagesCount = 0; // Count of new messages when user is not at bottom
    private boolean isUpdatingMessages = false; // Flag to prevent concurrent updates
    private boolean isUserReadingOldMessages = false; // Flag to track if user is reading old messages (>5 from bottom)
    // Pagination state
    private int currentPage = 1;
    private final int pageSize = 20;
    private final int initialDbLoadLimit = 50; // Load only last 50 messages from DB for faster initial load
    private boolean isLoadingMore = false;
    private boolean hasMore = true;
    private boolean hasMoreInDb = false; // Track if there are more messages in DB
    // Block state for private chats
    protected boolean isBlockedByMe = false;
    protected boolean hasBlockedMe = false;

    // Typing indicator state
    protected View typingIndicator;
    protected CharSequence statusSubtitleDefault;
    private final Map<String, String> remoteTypingUsers = new HashMap<>();
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable emitTypingRunnable;
    private Runnable emitStopTypingRunnable;
    private boolean isLocalTypingActive = false;
    private int activeUploadCount = 0;
    
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
        setupRealtimeConnectionListener();
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
                            Toast.makeText(this, getString(R.string.msg_no_photo_captured), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.msg_no_photo_captured), Toast.LENGTH_SHORT).show();
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

    /** Shared send path for private and group chats (optimistic UI + outbox + REST). */
    protected void handleSendMessage() {
        if (etMessage == null) {
            return;
        }
        String content = etMessage.getText().toString().trim();
        if (!content.isEmpty()) {
            sendMessage(content);
        }
    }

    protected void refreshMessageAdapterMode() {
        if (messageAdapter != null && currentChat != null) {
            messageAdapter.setGroupChat(currentChat.isGroupChat());
        }
    }

    /** Keep Home conversation row aligned while this chat is open. */
    protected void updateConversationPreview(Message message, boolean incrementUnreadWhenIncoming) {
        if (currentChat == null || message == null || conversationRepository == null) {
            return;
        }
        if (message.getChatId() == null || message.getChatId().isEmpty()) {
            message.setChatId(currentChat.getId());
        }
        if (message.getChatType() == null || message.getChatType().isEmpty()) {
            message.setChatType(currentChat.isGroupChat() ? "group" : "private");
        }
        String userId = databaseManager != null ? databaseManager.getUserId() : null;
        ConversationPreviewHelper.applyMessagePreview(
                this, conversationRepository, message, userId, incrementUnreadWhenIncoming);
        if (!incrementUnreadWhenIncoming && currentChat.getId() != null) {
            ConversationPreviewHelper.clearUnread(conversationRepository, currentChat.getId());
        }
    }

    protected void updateConversationPreviewAsync(Message message, boolean incrementUnreadWhenIncoming) {
        if (message == null) {
            return;
        }
        new Thread(() -> updateConversationPreview(message, incrementUnreadWhenIncoming)).start();
    }

    protected void persistMessageAsync(Message message) {
        if (message == null || messageRepository == null) {
            return;
        }
        new Thread(() -> messageRepository.saveMessage(message)).start();
    }
    
    // Video call handling - can be overridden by subclasses
    protected void handleVideoCall() {
        // Video call feature removed - only UI button remains
        Toast.makeText(this, getString(R.string.msg_video_call_feature_is_not_available), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.error_cannot_open_camera), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, getString(R.string.error_failed_to_create_camera_file), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.error_cannot_prepare_camera_file), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, getString(R.string.error_camera_permission_required_to_take_photos), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, getString(R.string.error_storage_permission_required_to_select_images), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, getString(R.string.error_microphone_permission_required_to_record_voice_messages), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    protected void handleSelectedImage(Uri imageUri) {
        if (imageUri == null) {
            Toast.makeText(this, getString(R.string.error_cannot_load_image), Toast.LENGTH_SHORT).show();
            return;
        }
        
        setUploadInProgress(true);
        
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
                setUploadInProgress(false);
                Toast.makeText(this, getString(R.string.error_cannot_read_image_file), Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.error_error_processing_image), Toast.LENGTH_SHORT).show();
        }
    }
    
    protected void handleSelectedBitmap(android.graphics.Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.error_cannot_load_image), Toast.LENGTH_SHORT).show();
            return;
        }
        
        setUploadInProgress(true);
        
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
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.error_error_processing_image), Toast.LENGTH_SHORT).show();
        }
    }
    
    protected void handleSelectedFile(Uri fileUri) {
        if (fileUri == null) {
            Toast.makeText(this, getString(R.string.error_cannot_load_file), Toast.LENGTH_SHORT).show();
            return;
        }
        
        setUploadInProgress(true);
        
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
                    setUploadInProgress(false);
                    Toast.makeText(this, getString(R.string.msg_file_size_too_large_maximum_50mb_allowed), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Accept all file types
                
                // Upload file to server
                uploadFileToServer(fileUri, fileName, mimeType, fileSize);
                
            } else {
                setUploadInProgress(false);
                Toast.makeText(this, getString(R.string.error_cannot_read_file_information), Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.error_error_processing_file), Toast.LENGTH_SHORT).show();
        }
    }
    
    // No MIME white-listing; we accept all files. Keep helper to guess MIME when missing.

    protected void uploadImageToServer(java.io.File imageFile, android.net.Uri localUri) {
        android.util.Log.d("BaseChatActivity", "uploadImageToServer: Called with file: " + imageFile.getAbsolutePath() + ", size: " + imageFile.length());
        
        if (currentChat == null) {
            android.util.Log.e("BaseChatActivity", "uploadImageToServer: currentChat is null");
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.msg_chat_not_available), Toast.LENGTH_SHORT).show();
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            android.util.Log.e("BaseChatActivity", "uploadImageToServer: token is null or empty");
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
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
                    try {
                        if (response.isSuccessful()) {
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                String imageUrl = jsonResponse.optString("imageUrl", "");
                                if (!imageUrl.isEmpty()) {
                                    sendImageMessage(imageUrl, localUri);
                                } else {
                                    Toast.makeText(BaseChatActivity.this, getString(R.string.error_failed_to_receive_image_url_from_server), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                String errorMsg = jsonResponse.optString("message", "Upload failed");
                                Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_upload_failed_code, response.code()), Toast.LENGTH_SHORT).show();
                        }
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                    } finally {
                        setUploadInProgress(false);
                    }
                });
            }
            
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                android.util.Log.e("BaseChatActivity", "Upload failed: " + e.getMessage());
                runOnUiThread(() -> {
                    setUploadInProgress(false);
                    Toast.makeText(BaseChatActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    protected void uploadFileToServer(Uri fileUri, String fileName, String mimeType, long fileSize) {
        if (currentChat == null) {
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.msg_chat_not_available), Toast.LENGTH_SHORT).show();
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
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
            setUploadInProgress(false);
            Toast.makeText(this, getString(R.string.msg_unable_to_access_selected_file_please_try_again), Toast.LENGTH_SHORT).show();
            return;
        }

        // Upload file to server (prefer File-based path)
        okhttp3.Callback callback = new okhttp3.Callback() {
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String responseBody = response.body().string();
                android.util.Log.d("BaseChatActivity", "File upload response: " + response.code() + " - " + responseBody);
                
                runOnUiThread(() -> {
                    try {
                        if (response.isSuccessful()) {
                            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                            if (jsonResponse.optBoolean("success", false)) {
                                String fileUrl = jsonResponse.optString("fileUrl", "");
                                String serverFileName = jsonResponse.optString("fileName", "");
                                String originalName = jsonResponse.optString("originalName", fileName);
                                long serverFileSize = jsonResponse.optLong("fileSize", fileSize);
                                String serverMimeType = jsonResponse.optString("mimeType", mimeType);
                                
                                if (!fileUrl.isEmpty()) {
                                    sendFileMessage(fileUrl, serverFileName, originalName, serverMimeType, serverFileSize);
                                } else {
                                    Toast.makeText(BaseChatActivity.this, getString(R.string.error_failed_to_receive_file_url_from_server), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                String errorMsg = jsonResponse.optString("message", "Upload failed");
                                Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_upload_failed_code, response.code()), Toast.LENGTH_SHORT).show();
                        }
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                    } finally {
                        setUploadInProgress(false);
                    }
                });
            }

            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                android.util.Log.e("BaseChatActivity", "File upload failed: " + e.getMessage());
                runOnUiThread(() -> {
                    setUploadInProgress(false);
                    Toast.makeText(BaseChatActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        };

        apiClient.uploadChatFile(token, fileToUpload, fileName, mimeType, fileSize, currentChat.getId(), callback);
    }

    protected void sendFileMessage(String fileUrl, String fileName, String originalName, String mimeType, long fileSize) {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();
        
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_send_file_code, response.code()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send file message failed: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show());
                }
            });
            
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_creating_file_message), Toast.LENGTH_SHORT).show();
        }
    }

    protected void sendImageMessage(String imageUrl, android.net.Uri localUri) {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();
        
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
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
                                    Toast.makeText(BaseChatActivity.this, getString(R.string.success_image_sent_successfully), Toast.LENGTH_SHORT).show();
                                    clearReplyState();
                                } else {
                                    removeMessageAndNotify(message);
                                    String errorMsg = jsonResponse.optString("message", "Cannot send image");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } catch (org.json.JSONException e) {
                                e.printStackTrace();
                                removeMessageAndNotify(message);
                                Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            removeMessageAndNotify(message);
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send image message failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        removeMessageAndNotify(message);
                        Toast.makeText(BaseChatActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_preparing_message), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.error_start_recording, e.getMessage()), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, getString(R.string.msg_recording_too_short), Toast.LENGTH_SHORT).show();
                    if (audioFile.delete()) {
                        audioFile = null;
                    }
                    return;
                }
                
                // Upload and send voice message
                uploadAndSendVoiceMessage(audioFile);
            } else {
                if (recordingStarted) {
                    Toast.makeText(this, getString(R.string.error_recording_failed), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.error_failed_to_stop_recording), Toast.LENGTH_SHORT).show();
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
        if (com.example.chatappjava.utils.MotionUtils.isMotionReduced(this)) {
            return;
        }
        com.example.chatappjava.utils.MotionUtils.playAnimation(this, ivRecordingIndicator, R.anim.recording_pulse);
        if (vPulseRing1 != null) {
            com.example.chatappjava.utils.MotionUtils.playAnimation(this, vPulseRing1, R.anim.recording_pulse);
        }
        if (vPulseRing2 != null) {
            com.example.chatappjava.utils.MotionUtils.playAnimation(this, vPulseRing2, R.anim.recording_pulse);
        }
    }
    
    private void stopRecordingAnimations() {
        // Stop indicator animation
        if (ivRecordingIndicator != null) {
            ivRecordingIndicator.clearAnimation();
        }
        
        // Stop pulse ring animations
        if (vPulseRing1 != null) {
            vPulseRing1.clearAnimation();
        }

        if (vPulseRing2 != null) {
            vPulseRing2.clearAnimation();
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
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            return;
        }
        
        setUploadInProgress(true);
        
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
                                    sendVoiceMessage(fileUrl, serverFileName, fileName, mimeType, fileSize);
                                } else {
                                    String errorMsg = jsonResponse.optString("message", "Failed to upload voice message");
                                    Toast.makeText(BaseChatActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(BaseChatActivity.this, getString(R.string.error_send_voice_code, response.code()), 
                                    Toast.LENGTH_SHORT).show();
                            }
                        } catch (org.json.JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_error_processing_upload_response), Toast.LENGTH_SHORT).show();
                        } finally {
                            setUploadInProgress(false);
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        setUploadInProgress(false);
                        Toast.makeText(BaseChatActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }
    
    protected void sendVoiceMessage(String fileUrl, String fileName, String originalName, String mimeType, long fileSize) {
        if (currentChat == null) return;
        
        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();
        
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_send_voice_code, response.code()), 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send voice message failed: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, getString(R.string.error_network_detail, e.getMessage()), Toast.LENGTH_SHORT).show());
                }
            });
            
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_error_creating_voice_message), Toast.LENGTH_SHORT).show();
        }
    }

    // Emoji picker handling
    protected void showEmojiPicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_emoji_picker, null);
        wireEmojiAccessibility(dialogView);

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

    private void wireEmojiAccessibility(View dialogView) {
        View scroll = dialogView.findViewById(R.id.emoji_scroll);
        if (scroll != null) {
            scroll.setContentDescription(getString(R.string.emoji_picker_grid_cd));
        }
        View grid = dialogView.findViewById(R.id.emoji_grid);
        if (!(grid instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup viewGroup = (android.view.ViewGroup) grid;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence emoji = ((TextView) child).getText();
                if (emoji != null && emoji.length() > 0) {
                    child.setContentDescription(getString(R.string.emoji_insert_cd, emoji));
                }
            }
        }
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
        tvChatStatus = findViewById(R.id.tv_chat_status);
        tvMessagesEmpty = findViewById(R.id.tv_messages_empty);
        ivMore = findViewById(R.id.iv_more);
        ivVideoCall = findViewById(R.id.iv_video_call);
        ivSend = findViewById(R.id.iv_send);
        ivAttachment = findViewById(R.id.iv_attach);
        ivEmoji = findViewById(R.id.iv_sticker);
        ivGallery = findViewById(R.id.iv_attach);
        ivRecordAudio = findViewById(R.id.record_audio_button);
        etMessage = findViewById(R.id.et_message);
        rvMessages = findViewById(R.id.rv_messages);
        messagesLoadingSkeleton = findViewById(R.id.messages_loading_skeleton);
        progressBar = findViewById(R.id.progress_bar);
        sendUploadProgress = findViewById(R.id.send_upload_progress);
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        replyBar = findViewById(R.id.reply_bar);
        tvReplyAuthor = findViewById(R.id.tv_reply_author);
        tvReplyContent = findViewById(R.id.tv_reply_content);
        ivReplyClose = findViewById(R.id.iv_reply_close);
        offlineIndicator = findViewById(R.id.offline_indicator);
        summarizeIndicator = findViewById(R.id.summarize_indicator);
        btnScrollToBottom = findViewById(R.id.btn_scroll_to_bottom);
        typingIndicator = findViewById(R.id.typing_indicator);
        
        // Setup scroll-to-bottom button click listener
        if (btnScrollToBottom != null) {
            btnScrollToBottom.setOnClickListener(v -> {
                if (messages != null && !messages.isEmpty() && rvMessages != null) {
                    shouldAutoScroll = true;
                    isUserReadingOldMessages = false;
                    hasNewMessages = false;
                    newMessagesCount = 0;
                    updateScrollToBottomButton();
                    scrollToBottomInstant();
                }
            });
        }
        
        // Log view initialization
        android.util.Log.d("BaseChatActivity", "Views initialized - ivBack: " + (ivBack != null) + 
                          ", ivProfile: " + (ivProfile != null) + 
                          ", tvChatName: " + (tvChatName != null));
        
        // Update offline indicator visibility
        updateOfflineIndicator();
        updateMessagesEmptyState();
    }

    protected void updateChatHeaderAccessibility() {
        if (ivProfile == null) {
            return;
        }
        if (tvChatName != null && tvChatName.getText() != null && tvChatName.getText().length() > 0) {
            ivProfile.setContentDescription(getString(R.string.open_profile_description, tvChatName.getText()));
        } else {
            ivProfile.setContentDescription(getString(R.string.open_profile_cd));
        }
    }

    protected void updateChatStatusSubtitle(CharSequence status) {
        if (tvChatStatus == null) {
            return;
        }
        if (!remoteTypingUsers.isEmpty()) {
            statusSubtitleDefault = status;
            refreshRemoteTypingUi();
            return;
        }
        statusSubtitleDefault = status;
        if (status == null || status.length() == 0) {
            tvChatStatus.setVisibility(View.GONE);
        } else {
            tvChatStatus.setText(status);
            tvChatStatus.setVisibility(View.VISIBLE);
        }
    }

    protected void setUploadInProgress(boolean inProgress) {
        if (inProgress) {
            activeUploadCount++;
        } else if (activeUploadCount > 0) {
            activeUploadCount--;
        }
        boolean uploading = activeUploadCount > 0;
        if (sendUploadProgress != null) {
            sendUploadProgress.setVisibility(uploading ? View.VISIBLE : View.GONE);
        }
        if (ivSend != null) {
            ivSend.setEnabled(!uploading);
            ivSend.setAlpha(uploading ? 0.55f : 1f);
            ivSend.setContentDescription(getString(
                    uploading ? R.string.uploading_file : R.string.send_message_description));
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void setupTypingListeners() {
        if (socketManager == null) {
            return;
        }
        socketManager.setTypingListener(new com.example.chatappjava.network.SocketManager.TypingListener() {
            @Override
            public void onUserTyping(String chatId, String userId, String username) {
                runOnUiThread(() -> handleRemoteTyping(chatId, userId, username, true));
            }

            @Override
            public void onUserStopTyping(String chatId, String userId) {
                runOnUiThread(() -> handleRemoteTyping(chatId, userId, null, false));
            }
        });
    }

    private void handleRemoteTyping(String chatId, String userId, String username, boolean isTyping) {
        if (currentChat == null || chatId == null || userId == null) {
            return;
        }
        if (!chatId.equals(currentChat.getId())) {
            return;
        }
        String currentUserId = databaseManager.getUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            return;
        }
        if (isTyping) {
            String label = (username != null && !username.isEmpty()) ? username : getString(R.string.username);
            remoteTypingUsers.put(userId, label);
        } else {
            remoteTypingUsers.remove(userId);
        }
        refreshRemoteTypingUi();
    }

    private void refreshRemoteTypingUi() {
        boolean hasTypers = !remoteTypingUsers.isEmpty();
        if (typingIndicator != null) {
            typingIndicator.setVisibility(hasTypers ? View.VISIBLE : View.GONE);
        }
        if (tvChatStatus == null) {
            return;
        }
        if (!hasTypers) {
            if (statusSubtitleDefault == null || statusSubtitleDefault.length() == 0) {
                tvChatStatus.setVisibility(View.GONE);
            } else {
                tvChatStatus.setText(statusSubtitleDefault);
                tvChatStatus.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (remoteTypingUsers.size() == 1) {
            String name = remoteTypingUsers.values().iterator().next();
            tvChatStatus.setText(getString(R.string.chat_status_typing_user, name));
        } else {
            tvChatStatus.setText(getString(R.string.chat_status_typing_multiple, remoteTypingUsers.size()));
        }
        tvChatStatus.setVisibility(View.VISIBLE);
    }

    private void handleComposerTextChanged(CharSequence text) {
        if (currentChat == null || socketManager == null) {
            return;
        }
        if (emitTypingRunnable != null) {
            typingHandler.removeCallbacks(emitTypingRunnable);
        }
        if (emitStopTypingRunnable != null) {
            typingHandler.removeCallbacks(emitStopTypingRunnable);
        }
        if (text == null || text.length() == 0) {
            emitLocalStopTyping();
            return;
        }
        emitTypingRunnable = () -> {
            if (!isLocalTypingActive) {
                isLocalTypingActive = true;
                socketManager.emitTyping(currentChat.getId());
            }
        };
        emitStopTypingRunnable = () -> emitLocalStopTyping();
        typingHandler.postDelayed(emitTypingRunnable, 350);
        typingHandler.postDelayed(emitStopTypingRunnable, 2800);
    }

    protected void emitLocalStopTyping() {
        if (emitTypingRunnable != null) {
            typingHandler.removeCallbacks(emitTypingRunnable);
        }
        if (emitStopTypingRunnable != null) {
            typingHandler.removeCallbacks(emitStopTypingRunnable);
        }
        if (isLocalTypingActive && socketManager != null && currentChat != null) {
            socketManager.emitStopTyping(currentChat.getId());
        }
        isLocalTypingActive = false;
    }

    private void clearTypingState() {
        emitLocalStopTyping();
        remoteTypingUsers.clear();
        if (typingIndicator != null) {
            typingIndicator.setVisibility(View.GONE);
        }
    }

    protected void updateMessagesEmptyState() {
        if (tvMessagesEmpty == null) {
            return;
        }
        if (isMessagesLoading) {
            tvMessagesEmpty.setVisibility(View.GONE);
            return;
        }
        boolean empty = messages == null || messages.isEmpty();
        tvMessagesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    protected void showMessagesLoading(boolean show) {
        isMessagesLoading = show;
        boolean showSkeleton = show && (messages == null || messages.isEmpty());
        if (messagesLoadingSkeleton != null) {
            messagesLoadingSkeleton.setVisibility(showSkeleton ? View.VISIBLE : View.GONE);
        }
        if (rvMessages != null) {
            rvMessages.setVisibility(showSkeleton ? View.INVISIBLE : View.VISIBLE);
        }
        updateMessagesEmptyState();
    }

    private void finishInitialMessagesLoad() {
        showMessagesLoading(false);
        isInitialLoad = false;
        updateMessagesEmptyState();
    }

    protected void initData() {
        databaseManager = new DatabaseManager(this);
        messageRepository = new MessageRepository(this);
        conversationRepository = new ConversationRepository(this);
        messageRepository.setOnTempMessageRemovedListener((tempId, realId) ->
                runOnUiThread(() -> onTempMessageRemovedFromDb(tempId, realId)));
        syncManager = OfflineMessageSyncManager.getInstance(this); // App-wide offline outbox sync
        backgroundSyncManager = com.example.chatappjava.utils.SyncManager.getInstance(this); // For background delta sync
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
                Toast.makeText(this, getString(R.string.error_load_chat), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Initialize other user data for private chats
        if (intent.hasExtra("user")) {
            try {
                String userJson = intent.getStringExtra("user");
                JSONObject userJsonObj = new JSONObject(userJson);
                otherUser = User.fromJsonStatic(userJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        // If otherUser is not set but currentChat has otherParticipant, use it
        if (otherUser == null && currentChat != null && currentChat.isPrivateChat()) {
            otherUser = currentChat.getOtherParticipant();
            android.util.Log.d("BaseChatActivity", "Initialized otherUser from currentChat.getOtherParticipant(): " + 
                (otherUser != null ? otherUser.getId() : "null"));
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
        if (socketManager != null) {
            if (chatMessageListener == null) {
                chatMessageListener = new SocketManager.MessageListener() {
                    @Override
                    public void onPrivateMessage(org.json.JSONObject messageJson) {
                        dispatchIncomingMessage(messageJson);
                    }

                    @Override
                    public void onGroupMessage(org.json.JSONObject messageJson) {
                        dispatchIncomingMessage(messageJson);
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
                        dispatchReactionUpdated(reactionJson);
                    }
                };
            }
            socketManager.addMessageListener(chatMessageListener);
        }
        setupTypingListeners();
    }

    /**
     * Industry pattern: socket = realtime; HTTP delta only on reconnect / resume (gap-fill).
     */
    private void setupRealtimeConnectionListener() {
        if (socketManager == null) return;
        if (realtimeConnectionListener == null) {
            realtimeConnectionListener = new SocketManager.ConnectionListener() {
                @Override
                public void onConnected() {
                    if (!socketWasDisconnected) {
                        return;
                    }
                    socketWasDisconnected = false;
                    runOnUiThread(() -> catchUpAfterDisconnect());
                }

                @Override
                public void onDisconnected() {
                    socketWasDisconnected = true;
                }
            };
        }
        socketManager.addConnectionListener(realtimeConnectionListener);
    }

    /**
     * HTTP catch-up only after real disconnect (socket down, network lost, send timeout).
     * While socket is connected, incoming messages come solely from socket events.
     */
    private void catchUpAfterDisconnect() {
        if (currentChat == null) return;
        reconcileOutboxFromDb();
        String token = databaseManager != null ? databaseManager.getToken() : null;
        if (token == null || token.isEmpty() || backgroundSyncManager == null) {
            return;
        }
        final com.example.chatappjava.utils.SyncManager.SyncListener once =
                new com.example.chatappjava.utils.SyncManager.SyncListener() {
                    @Override
                    public void onSyncComplete(String resourceType, boolean success, int itemsUpdated) {
                        if (!"messages".equals(resourceType)) {
                            return;
                        }
                        backgroundSyncManager.removeSyncListener(this);
                        runOnUiThread(() -> scheduleAppendNewMessagesFromDb(false));
                    }

                    @Override
                    public void onSyncError(String resourceType, String error) {
                        if ("messages".equals(resourceType)) {
                            backgroundSyncManager.removeSyncListener(this);
                            runOnUiThread(() -> scheduleAppendNewMessagesFromDb(false));
                        }
                    }
                };
        backgroundSyncManager.addSyncListener(once);
        backgroundSyncManager.syncMessagesNow(token);
    }

    /** Socket event → UI immediately (front of main queue). */
    private void dispatchIncomingMessage(org.json.JSONObject messageJson) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handleIncomingMessage(messageJson);
        } else {
            appendHandler.postAtFrontOfQueue(() -> handleIncomingMessage(messageJson));
        }
    }

    /** Socket reaction event → apply payload directly (same pattern as messages). */
    private void dispatchReactionUpdated(org.json.JSONObject reactionJson) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handleReactionUpdated(reactionJson);
        } else {
            appendHandler.postAtFrontOfQueue(() -> handleReactionUpdated(reactionJson));
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
                com.example.chatappjava.utils.MotionUtils.playPressScale(this, v);
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
                    handleComposerTextChanged(s);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    @Override
    protected void onPause() {
        emitLocalStopTyping();
        unregisterAvatarSyncListener();
        if (syncManager != null) {
            syncManager.setPendingSyncListener(null);
        }
        super.onPause();
    }

    private void registerAvatarSyncListener() {
        if (avatarSyncListener == null) {
            avatarSyncListener = new AvatarSyncCoordinator.Listener() {
                @Override
                public void onUserAvatarChanged(String userId, String avatarPath) {
                    handleUserAvatarSynced(userId, avatarPath);
                }

                @Override
                public void onGroupAvatarChanged(String chatId, String avatarPath) {
                    handleGroupAvatarSynced(chatId, avatarPath);
                }
            };
        }
        AvatarSyncCoordinator.getInstance(this).addListener(avatarSyncListener);
    }

    private void unregisterAvatarSyncListener() {
        if (avatarSyncListener != null) {
            AvatarSyncCoordinator.getInstance(this).removeListener(avatarSyncListener);
        }
    }

    protected void handleUserAvatarSynced(String userId, String avatarPath) {
        if (messageAdapter != null) {
            messageAdapter.applyUserAvatarChange(userId, avatarPath);
        }
    }

    protected void handleGroupAvatarSynced(String chatId, String avatarPath) {
        if (currentChat != null && chatId.equals(currentChat.getId())) {
            currentChat.setAvatar(avatarPath != null ? avatarPath : "");
            updateUI();
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
        refreshMessageAdapterMode();
        android.util.Log.d("BaseChatActivity", "AvatarManager set: " + (avatarManager != null) + ", currentUserId: " + currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);   // Set true to stack from bottom. But it can cause issues when have few messages.
        // Disable prefetch to prevent IndexOutOfBoundsException during list updates
        layoutManager.setItemPrefetchEnabled(false);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);
        messageAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateMessagesEmptyState();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateMessagesEmptyState();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateMessagesEmptyState();
            }
        });
        
        // Performance optimizations for RecyclerView
        rvMessages.setHasFixedSize(false); // Messages have variable sizes
        rvMessages.setItemViewCacheSize(20); // Cache more views for smoother scrolling (default is 2)
        // Reduce overdraw by enabling clipToPadding
        rvMessages.setClipToPadding(false);
        // Enable nested scrolling for better performance
        rvMessages.setNestedScrollingEnabled(true);
        
        // Add scroll listener to detect when user reaches bottom (Smart Auto-scroll like Messenger)
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                if (lm == null) return;
                
                int lastVisiblePosition = lm.findLastVisibleItemPosition();
                int totalItemCount = lm.getItemCount();
                
                // Calculate distance from bottom
                int distanceFromBottom = totalItemCount > 0 ? totalItemCount - 1 - lastVisiblePosition : 0;
                
                // CRITICAL: Always check position first, then scroll direction
                // If user is more than 5 messages from bottom, ALWAYS disable auto-scroll regardless of scroll direction
                if (distanceFromBottom > 5) {
                    shouldAutoScroll = false;
                    isUserReadingOldMessages = true;
                    android.util.Log.d("BaseChatActivity", "POSITION CHECK: distanceFromBottom=" + distanceFromBottom + " > 5, isUserReadingOldMessages=true, shouldAutoScroll=false, dy=" + dy);
                } else if (distanceFromBottom <= 2) {
                    // User is near bottom - enable auto-scroll only if scrolling down
                    if (dy > 0) {
                        shouldAutoScroll = true;
                        isUserReadingOldMessages = false;
                        android.util.Log.d("BaseChatActivity", "POSITION CHECK: distanceFromBottom=" + distanceFromBottom + " <= 2, scrolling down, isUserReadingOldMessages=false, shouldAutoScroll=true");
                    } else if (dy < 0) {
                        // User is scrolling up even though near bottom - disable auto-scroll
                        shouldAutoScroll = false;
                        android.util.Log.d("BaseChatActivity", "POSITION CHECK: distanceFromBottom=" + distanceFromBottom + " <= 2, scrolling up, shouldAutoScroll=false");
                    }
                } else {
                    // Between 3-5 messages from bottom
                    if (dy < 0) {
                        // Scrolling up - disable auto-scroll
                        shouldAutoScroll = false;
                        android.util.Log.d("BaseChatActivity", "POSITION CHECK: distanceFromBottom=" + distanceFromBottom + " (3-5), scrolling up, shouldAutoScroll=false");
                    } else if (dy > 0 && distanceFromBottom <= 2) {
                        // Scrolling down and reached near bottom
                        shouldAutoScroll = true;
                        isUserReadingOldMessages = false;
                        android.util.Log.d("BaseChatActivity", "POSITION CHECK: distanceFromBottom=" + distanceFromBottom + ", scrolling down to bottom, isUserReadingOldMessages=false, shouldAutoScroll=true");
                    }
                }
                
                if (shouldAutoScroll) {
                    // User is at/near bottom - clear new messages indicator
                    hasNewMessages = false;
                    newMessagesCount = 0;
                    updateScrollToBottomButton();
                }
                
                // Infinite scroll upwards: when first visible close to top, load previous page
                // IMPORTANT: Always allow loadMoreMessages even if user is reading old messages
                // This is different from refresh - we want to load more old messages when scrolling up
                // Allow loading when IDLE or SETTLING (not when actively dragging)
                int scrollState = recyclerView.getScrollState();
                boolean canLoadMore = (scrollState == RecyclerView.SCROLL_STATE_IDLE || scrollState == RecyclerView.SCROLL_STATE_SETTLING);
                
                // Only load older messages when user scrolls up (not while sitting at bottom)
                if (!isLoadingMore && hasMore && canLoadMore && dy < 0 && !isAtBottom()) {
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    if (firstVisible <= 3) {
                        recyclerView.postDelayed(() -> {
                            if (!isLoadingMore && hasMore && !isAtBottom()) {
                                loadMoreMessages();
                            }
                        }, 200);
                    }
                }
            }
            
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // When user stops scrolling, check if they're at bottom
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                    if (lm != null) {
                        int lastVisiblePosition = lm.findLastVisibleItemPosition();
                        int totalItemCount = lm.getItemCount();
                        int distanceFromBottom = totalItemCount - 1 - lastVisiblePosition;
                        
                        // Only enable auto-scroll if user is within 2 messages from bottom
                        // If user is more than 5 messages from bottom, keep auto-scroll disabled
                        if (distanceFromBottom <= 2) {
                            hasNewMessages = false;
                            newMessagesCount = 0;
                            // If user manually scrolled to bottom, enable auto-scroll for new messages
                            shouldAutoScroll = true;
                            isUserReadingOldMessages = false;
                            enableAutoScrollForNewMessages();
                            android.util.Log.d("BaseChatActivity", "SCROLL STATE IDLE AT BOTTOM: distanceFromBottom=" + distanceFromBottom + ", isUserReadingOldMessages=false, shouldAutoScroll=true");
                        } else if (distanceFromBottom > 5) {
                            // User is reading old messages (more than 5 from bottom) - disable auto-scroll
                            shouldAutoScroll = false;
                            isUserReadingOldMessages = true;
                            updateScrollToBottomButton();
                            android.util.Log.d("BaseChatActivity", "SCROLL STATE IDLE READING OLD: distanceFromBottom=" + distanceFromBottom + ", isUserReadingOldMessages=true, shouldAutoScroll=false");
                        } else {
                            // Between 3-5 messages from bottom - keep current state
                            // Don't change shouldAutoScroll
                            updateScrollToBottomButton();
                            android.util.Log.d("BaseChatActivity", "SCROLL STATE IDLE MIDDLE: distanceFromBottom=" + distanceFromBottom + ", isUserReadingOldMessages=" + isUserReadingOldMessages + ", shouldAutoScroll=" + shouldAutoScroll);
                        }
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // User started dragging - check if scrolling up
                    // We'll handle this in onScrolled based on dy value
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

        if (!isInitialLoad) {
            return;
        }

        // Initial load — offline-first: show SQLite cache immediately, then refresh from server when online
        messages.clear();
        boolean networkAvailable = isNetworkAvailable();
        loadMessagesFromDatabase(!networkAvailable);

        if (networkAvailable) {
            showMessagesLoading(true);
            String token = databaseManager.getToken();
            if (token == null || token.isEmpty()) {
                loadMessagesFromDatabase(true);
                return;
            }

            currentPage = 1;
            hasMore = true;

            // Initial load: use full API call
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
                                        pageOne.add(message);
                                    }
                                    hasMore = messagesArray.length() >= pageSize;
                                }
                                // Sync SQLite with server truth (drops messages before leftAt)
                                if (messageRepository != null) {
                                    messageRepository.replaceMessagesForChat(currentChat.getId(), pageOne);
                                }
                                // Initial load — replace list from server page
                                List<Message> previousMessages = new ArrayList<>(messages);
                                messages.clear();
                                java.util.Set<String> seenIds = new java.util.HashSet<>();
                                for (Message m : pageOne) {
                                    String msgId = m.getId();
                                    if (msgId != null && !msgId.isEmpty() && !seenIds.contains(msgId)) {
                                        messages.add(m);
                                        seenIds.add(msgId);
                                    }
                                }
                                notifyMessageListReplaced(previousMessages);
                                
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
                                
                                updateSummarizeIndicator();

                                shouldAutoScroll = true;
                                scrollToBottom();
                                finishInitialMessagesLoad();
                            } else {
                                String errorMessage = jsonResponse.optString("message", "Failed to load messages");
                                Toast.makeText(BaseChatActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                finishInitialMessagesLoad();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_error_parsing_messages), Toast.LENGTH_SHORT).show();
                            if (messages.isEmpty()) {
                                loadMessagesFromDatabase(true);
                            } else {
                                finishInitialMessagesLoad();
                            }
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, getString(R.string.error_load_messages_code, response.code()), Toast.LENGTH_SHORT).show();
                        if (messages.isEmpty()) {
                            loadMessagesFromDatabase(true);
                        } else {
                            finishInitialMessagesLoad();
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    if (messages.isEmpty()) {
                        loadMessagesFromDatabase(true);
                    } else {
                        finishInitialMessagesLoad();
                    }
                });
            }
        });
        }
    }
    
    private void loadMessagesFromDatabase() {
        loadMessagesFromDatabase(true);
    }

    /**
     * Load messages from local database (optimized - only last N messages for fast initial load)
     * @param finalizeLoad when true, ends initial loading state (offline or API fallback)
     */
    private void loadMessagesFromDatabase(boolean finalizeLoad) {
        if (currentChat == null || messageRepository == null) return;
        
        // Load asynchronously to avoid blocking UI thread
        new Thread(() -> {
            List<Message> dbMessages = messageRepository.getMessagesForChat(
                currentChat.getId(), 
                initialDbLoadLimit
            );
            
            // Check if there are more messages in DB
            int totalCount = messageRepository.getMessagesCountForChat(currentChat.getId());
            hasMoreInDb = totalCount > initialDbLoadLimit;
            // IMPORTANT: Set hasMore based on whether there are more messages in DB or server
            // This ensures loadMoreMessages can be called when scrolling up
            boolean shouldHaveMore = hasMoreInDb || totalCount > dbMessages.size();

            runOnUiThread(() -> {
                if (!dbMessages.isEmpty()) {
                    List<Message> previousMessages = new ArrayList<>(messages);
                    messages.clear();
                    messages.addAll(dbMessages);
                    hasMore = shouldHaveMore;
                    android.util.Log.d("BaseChatActivity", "Loaded " + dbMessages.size() +
                            " messages from database (total: " + totalCount + "), hasMore=" + hasMore + ", hasMoreInDb=" + hasMoreInDb);
                    notifyMessageListReplaced(previousMessages);
                    updateSummarizeIndicator();
                    shouldAutoScroll = true;
                    scrollToBottom();
                    if (!finalizeLoad) {
                        showMessagesLoading(false);
                    }
                }
                if (finalizeLoad && (isInitialLoad || isMessagesLoading)) {
                    finishInitialMessagesLoad();
                } else if (!finalizeLoad && !dbMessages.isEmpty()) {
                    updateMessagesEmptyState();
                } else if (finalizeLoad) {
                    updateMessagesEmptyState();
                }
            });
        }).start();
    }
    
    /**
     * Safely update messages list and restore scroll position
     * @param shouldAutoScrollNow Current state of shouldAutoScroll (not the old wasAtBottom)
     */
    private void notifyMessageListReplaced(List<Message> previousMessages) {
        if (messageAdapter == null) {
            return;
        }
        int previousSize = previousMessages != null ? previousMessages.size() : 0;
        int newSize = messages.size();
        if (previousSize == 0 && newSize > 0) {
            messageAdapter.notifyItemRangeInserted(0, newSize);
        } else if (newSize == 0 && previousSize > 0) {
            messageAdapter.notifyItemRangeRemoved(0, previousSize);
        } else if (previousMessages != null && !previousMessages.isEmpty() && newSize > 0) {
            messageAdapter.applyDiff(previousMessages);
        } else if (newSize == previousSize && newSize > 0) {
            messageAdapter.notifyItemRangeChanged(0, newSize);
        } else {
            messageAdapter.notifyDataSetChanged();
        }
    }

    private void updateMessagesListSafely(List<Message> dbMessages, boolean shouldAutoScrollNow, 
                                         boolean hasNewMessagesAtEnd, int firstVisiblePosition, 
                                         int topOffset, LinearLayoutManager lm) {
        // Save RecyclerView state if not already saved
        android.os.Parcelable recyclerViewState = null;
        if (lm != null && rvMessages != null) {
            recyclerViewState = lm.onSaveInstanceState();
        }
        // Create final variable for use in lambda
        final android.os.Parcelable savedState = recyclerViewState;
        
        if (isUpdatingMessages) {
            // Another update in progress - skip this one
            android.util.Log.d("BaseChatActivity", "Skipping update - another update in progress");
            return;
        }
        
        waitForRecyclerViewIdle(() -> {
            if (isUpdatingMessages) return; // Another update started
            isUpdatingMessages = true;
            
            try {
                // Stop scroll to prevent inconsistency
                if (rvMessages != null) {
                    rvMessages.stopScroll();
                }
                
                // SOLUTION: Save RecyclerView state BEFORE updating data (if not already saved)
                // This is the recommended approach to prevent auto-scroll
                // Use the state saved at the beginning of the method, or save it now if not saved
                android.os.Parcelable stateToUse = savedState;
                if (stateToUse == null && lm != null && rvMessages != null) {
                    stateToUse = lm.onSaveInstanceState();
                }
                final android.os.Parcelable finalState = stateToUse;
                
                // Calculate if user is reading old messages
                // Use the flag isUserReadingOldMessages which is more reliable
                final boolean userIsReadingOldMessages = isUserReadingOldMessages || !shouldAutoScrollNow || 
                    (firstVisiblePosition >= 0 && messages.size() > 0 && firstVisiblePosition < messages.size() - 3);
                
                // CRITICAL: If user is reading old messages, DON'T update the list at all
                // This prevents any scrolling issues
                // Check current state again before updating (user might have scrolled during load)
                boolean currentIsReadingOld = isUserReadingOldMessages || !shouldAutoScroll;
                if (userIsReadingOldMessages || currentIsReadingOld) {
                    android.util.Log.d("BaseChatActivity", "BLOCKED list update - user reading old messages (userIsReadingOldMessages=" + userIsReadingOldMessages + ", isUserReadingOldMessages=" + isUserReadingOldMessages + ", shouldAutoScroll=" + shouldAutoScroll + "), skipping notifyDataSetChanged()");
                    // Don't update messages list at all - just return
                    isUpdatingMessages = false;
                    return;
                }
                
                // Update messages list only if user is NOT reading old messages
                List<Message> previousMessages = new ArrayList<>(messages);
                messages.clear();
                messages.addAll(dbMessages);
                notifyMessageListReplaced(previousMessages);
                updateSummarizeIndicator();
                
                // SOLUTION: Restore RecyclerView state AFTER notifyDataSetChanged
                // This prevents RecyclerView from auto-scrolling
                // CRITICAL: ALWAYS restore if user is reading old messages OR shouldAutoScroll is false
                // This is the key to preventing unwanted scrolling
                if (finalState != null && lm != null && rvMessages != null) {
                    // Save current shouldAutoScroll state before restore
                    final boolean wasReadingOldMessages = userIsReadingOldMessages || !shouldAutoScroll || isUserReadingOldMessages;
                    
                    // Restore state immediately after layout - use multiple posts to ensure it happens
                    rvMessages.post(() -> {
                        rvMessages.post(() -> {
                            rvMessages.post(() -> {
                                if (lm != null && rvMessages != null) {
                                    try {
                                        // ALWAYS restore if user was reading old messages
                                        // This is critical to prevent auto-scroll
                                        // Also check current state - user might have scrolled during update
                                        boolean stillReadingOld = isUserReadingOldMessages || !shouldAutoScroll;
                                        if (wasReadingOldMessages || stillReadingOld) {
                                            lm.onRestoreInstanceState(finalState);
                                            // Force layout to ensure position is restored
                                            rvMessages.requestLayout();
                                            android.util.Log.d("BaseChatActivity", "FORCE RESTORED RecyclerView state (wasReadingOldMessages=" + wasReadingOldMessages + ", stillReadingOld=" + stillReadingOld + ", isUserReadingOldMessages=" + isUserReadingOldMessages + ", shouldAutoScroll=" + shouldAutoScroll + ")");
                                        } else {
                                            android.util.Log.d("BaseChatActivity", "SKIPPED restore - user at bottom (wasReadingOldMessages=" + wasReadingOldMessages + ", stillReadingOld=" + stillReadingOld + ")");
                                        }
                                    } catch (Exception e) {
                                        android.util.Log.e("BaseChatActivity", "Error restoring RecyclerView state: " + e.getMessage());
                                    }
                                }
                            });
                        });
                    });
                }
                
                // Only check for auto-scroll if user is NOT reading old messages AND at bottom
                if (!userIsReadingOldMessages && hasNewMessagesAtEnd) {
                    rvMessages.post(() -> {
                        if (lm != null && rvMessages != null && !messages.isEmpty()) {
                            try {
                                // Double-check current state
                                boolean isCurrentlyAtBottom = isAtBottom();
                                boolean currentShouldAutoScrollState = shouldAutoScroll;
                                
                                // Only scroll to bottom if user is at bottom and shouldAutoScroll is true
                                if (isCurrentlyAtBottom && currentShouldAutoScrollState) {
                                    scrollToBottomIfAtBottom();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("BaseChatActivity", "Error checking auto-scroll: " + e.getMessage());
                            }
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("BaseChatActivity", "Error updating messages list: " + e.getMessage());
            } finally {
                isUpdatingMessages = false;
            }
        });
    }
    
    private void scheduleAppendNewMessagesFromDb(boolean allowInitialBatch) {
        if (appendFromDbRunnable != null) {
            appendHandler.removeCallbacks(appendFromDbRunnable);
        }
        appendFromDbRunnable = () -> appendNewMessagesFromDb(allowInitialBatch);
        appendHandler.postDelayed(appendFromDbRunnable, APPEND_DEBOUNCE_MS);
    }

    private void appendNewMessagesFromDb(boolean allowInitialBatch) {
        if (currentChat == null || messageRepository == null || messages == null) return;
        if (isUpdatingMessages || isLoadingMore) return;

        String lastMessageId = messages.isEmpty() ? null : messages.get(messages.size() - 1).getId();
        long lastTimestamp = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getTimestamp();
        final boolean lastWasPlaceholder = isPlaceholderId(lastMessageId);
        if (lastWasPlaceholder) {
            lastMessageId = null;
        }

        final String currentUserId = databaseManager != null ? databaseManager.getUserId() : null;
        final String chatId = currentChat.getId();
        long minPlaceholderTs = Long.MAX_VALUE;
        boolean hasOutgoingPlaceholders = false;
        if (currentUserId != null) {
            for (Message m : messages) {
                if (!isPlaceholderId(m.getId())) continue;
                if (!currentUserId.equals(m.getSenderId())) continue;
                if (!chatId.equals(m.getChatId())) continue;
                hasOutgoingPlaceholders = true;
                if (m.getTimestamp() < minPlaceholderTs) {
                    minPlaceholderTs = m.getTimestamp();
                }
            }
        }

        final String queryAfterId = lastMessageId;
        final long queryAfterTimestamp = lastTimestamp;
        final boolean reconcilePlaceholders = hasOutgoingPlaceholders && minPlaceholderTs < Long.MAX_VALUE;
        final long placeholderSinceTs = minPlaceholderTs;
        final boolean wasAtBottom = isAtBottom();

        new Thread(() -> {
            List<Message> newMessages;
            if (reconcilePlaceholders) {
                newMessages = messageRepository.getSyncedMessagesSince(chatId, placeholderSinceTs);
            } else if (queryAfterId != null) {
                newMessages = messageRepository.getMessagesAfterId(chatId, queryAfterId);
            } else if (queryAfterTimestamp > 0) {
                if (lastWasPlaceholder) {
                    newMessages = messageRepository.getSyncedMessagesSince(chatId, queryAfterTimestamp);
                } else {
                    newMessages = messageRepository.getMessagesAfter(chatId, queryAfterTimestamp);
                }
            } else if (allowInitialBatch) {
                newMessages = messageRepository.getMessagesForChat(chatId, initialDbLoadLimit);
            } else {
                return;
            }

            if (newMessages == null || newMessages.isEmpty()) {
                if (reconcilePlaceholders) {
                    runOnUiThread(this::sweepOrphanPlaceholders);
                }
                return;
            }

            runOnUiThread(() -> applyAppendedMessages(newMessages, wasAtBottom));
        }).start();
    }

    private void applyAppendedMessages(List<Message> newMessages, boolean wasAtBottom) {
        if (messageAdapter == null || isUpdatingMessages || newMessages == null) return;

        int inserted = 0;
        for (Message msg : newMessages) {
            if (upsertChatMessage(msg, false)) {
                inserted++;
            }
        }
        sweepOrphanPlaceholders();
        if (inserted <= 0) return;

        updateSummarizeIndicator();
        if (wasAtBottom && shouldAutoScroll && !isUserReadingOldMessages) {
            scrollToBottomInstant();
        } else {
            newMessagesCount += inserted;
            hasNewMessages = true;
            updateScrollToBottomButton();
        }
    }

    
    /**
     * Check if network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) {
            return false;
        }
        // LAN server: do not require NET_CAPABILITY_VALIDATED (no public internet needed).
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
    }
    
    /**
     * Setup network listener to auto-sync pending messages when WiFi comes back
     */
    private void setupNetworkListener() {
        networkConnectivityManager =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (networkConnectivityManager == null) return;
        
        NetworkRequest request = new NetworkRequest.Builder().build();
        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                android.util.Log.d("BaseChatActivity", "Network available, reconciling messages");
                runOnUiThread(() -> {
                    updateOfflineIndicator();
                    reconcileAfterNetworkRestore();
                });
            }
            
            @Override
            public void onLost(Network network) {
                android.util.Log.d("BaseChatActivity", "Network lost");
                runOnUiThread(() -> updateOfflineIndicator());
            }
        };
        
        networkConnectivityManager.registerNetworkCallback(request, networkCallback);
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
        
        for (Message message : messages) {
            // Count messages that are not from current user
            if (message.getSenderId() != null && 
                !message.getSenderId().equals(currentUserId) && 
                !message.isDeleted()) {
                
                boolean isUnread = !message.isRead();
                
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
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, getString(R.string.error_code, response.code()), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(BaseChatActivity.this, getString(R.string.error_connection_detail, e.getMessage()), Toast.LENGTH_SHORT).show();
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
    
    private void registerPendingSyncListener() {
        if (syncManager == null) {
            return;
        }
        syncManager.setPendingSyncListener(new OfflineMessageSyncManager.PendingSyncListener() {
            @Override
            public void onMessageSynced(String tempId, Message serverMessage) {
                runOnUiThread(() -> {
                    if (serverMessage != null) {
                        acknowledgeSentMessage(tempId, serverMessage);
                    }
                });
            }

            @Override
            public void onMessageSyncFailed(String tempId, String error) {
                runOnUiThread(() -> {
                    int idx = indexOfMessageById(tempId);
                    if (idx >= 0) {
                        Message message = messages.get(idx);
                        message.setSyncStatus(Message.SYNC_FAILED);
                        if (messageAdapter != null) {
                            messageAdapter.notifyItemChanged(idx,
                                    com.example.chatappjava.adapters.MessageAdapter.PAYLOAD_SEND_STATUS);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerAvatarSyncListener();
        registerPendingSyncListener();
        // Restart auto-hide timer if summarize indicator is visible
        if (summarizeIndicator != null && summarizeIndicator.getVisibility() == View.VISIBLE) {
            startAutoHideSummarizeTimer();
        }
        
        reconcileOutboxFromDb();
        if (socketManager == null || !socketManager.isConnected()) {
            catchUpAfterDisconnect();
        }

        updateOfflineIndicator();
        updateSummarizeIndicator();

        // Note: Sync pending messages is handled by network callback (setupNetworkListener)
        // No need to sync here to avoid duplicate syncs when network comes back
        // Network callback will handle it automatically
    }

    private void loadMoreMessages() {
        android.util.Log.d("BaseChatActivity", "loadMoreMessages called: currentChat=" + (currentChat != null ? currentChat.getId() : "null") + ", isLoadingMore=" + isLoadingMore + ", hasMore=" + hasMore);
        if (currentChat == null || isLoadingMore || !hasMore) {
            android.util.Log.d("BaseChatActivity", "loadMoreMessages blocked: currentChat=" + (currentChat != null) + ", isLoadingMore=" + isLoadingMore + ", hasMore=" + hasMore);
            return;
        }
        isLoadingMore = true;
        android.util.Log.d("BaseChatActivity", "loadMoreMessages starting: currentPage=" + currentPage + ", pageSize=" + pageSize);
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
                                older.add(m);
                            }
                            
                            // Batch save messages for better performance
                            if (!older.isEmpty() && messageRepository != null) {
                                messageRepository.saveMessagesBatch(older);
                            }
                            
                            // Wait for RecyclerView to be idle before modifying list
                            waitForRecyclerViewIdle(() -> {
                                if (isUpdatingMessages) {
                                    isLoadingMore = false;
                                    return;
                                }
                                isUpdatingMessages = true;
                                
                                try {
                                    // Stop scroll to prevent inconsistency
                                    if (rvMessages != null) {
                                        rvMessages.stopScroll();
                                    }
                                    
                                    // Check if list hasn't been cleared by another operation
                                    if (messages.isEmpty() && !older.isEmpty()) {
                                        // List was cleared, can't restore position - just add messages
                                        messages.addAll(older);
                                        currentPage += 1;
                                        hasMore = arr.length() >= pageSize;
                                        if (messageAdapter != null) {
                                            messageAdapter.notifyItemRangeInserted(0, older.size());
                                        }
                                        return;
                                    }
                                    
                                    // Add older messages at the beginning
                                    messages.addAll(0, older);
                                    currentPage += 1;
                                    hasMore = arr.length() >= pageSize;
                                    
                                    // Notify adapter after list modification
                                    if (messageAdapter != null) {
                                        messageAdapter.notifyItemRangeInserted(0, older.size());
                                    }
                                    
                                    // Restore previous viewport after adapter has been notified
                                    rvMessages.post(() -> {
                                        rvMessages.post(() -> {
                                            LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                                            if (lm != null && !messages.isEmpty()) {
                                                try {
                                                    int newFirstVisible = firstVisibleBefore + older.size();
                                                    if (newFirstVisible >= 0 && newFirstVisible < messages.size()) {
                                                        lm.scrollToPositionWithOffset(newFirstVisible, topOffsetBefore);
                                                    }
                                                } catch (Exception e) {
                                                    android.util.Log.e("BaseChatActivity", "Error restoring scroll in loadMoreMessages: " + e.getMessage());
                                                }
                                            }
                                        });
                                    });
                                } catch (Exception e) {
                                    android.util.Log.e("BaseChatActivity", "Error in loadMoreMessages: " + e.getMessage());
                                } finally {
                                    isLoadingMore = false;
                                    isUpdatingMessages = false;
                                }
                            });
                        } else {
                            hasMore = false;
                        }
                    } catch (Exception e) {
                        android.util.Log.e("BaseChatActivity", "Error parsing loadMoreMessages response: " + e.getMessage());
                    }
                });
            }
            @Override public void onFailure(Call call, IOException e) { 
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    android.util.Log.e("BaseChatActivity", "Failed to load more messages: " + e.getMessage());
                });
            }
        });
    }

    protected void sendMessage(String content) {
        if (TextUtils.isEmpty(content.trim())) return;

        String token = databaseManager.getToken();
        String senderId = databaseManager.getUserId();

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentChat == null) {
            Toast.makeText(this, getString(R.string.msg_chat_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent sending if blocked in private chat
        if (!currentChat.isGroupChat()) {
            if (isBlockedByMe) {
                Toast.makeText(this, getString(R.string.msg_you_blocked_this_user_unblock_to_continue), Toast.LENGTH_SHORT).show();
                return;
            }
            if (hasBlockedMe) {
                Toast.makeText(this, getString(R.string.msg_you_cannot_message_this_user), Toast.LENGTH_SHORT).show();
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

        // Optimistic UI first — persist to SQLite on a worker thread
        String tempId = "temp_" + java.util.UUID.randomUUID().toString();
        message.setId(tempId);
        message.setSyncStatus(Message.SYNC_PENDING);

        final Message finalMessage = message;
        final String finalMessageId = tempId;
        final int messagePosition = messages.size();

        registerOutgoingNonce(clientNonce, finalMessageId);
        upsertChatMessage(message, false);
        updateConversationPreviewAsync(message, false);
        persistMessageAsync(message);
        forceScrollToBottom();

        // Clear input
        etMessage.setText("");

        // Check network availability
        if (!isNetworkAvailable()) {
            notifySendStatusChanged(finalMessageId);
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
                                            if (serverMessage.getClientNonce() == null
                                                    || serverMessage.getClientNonce().isEmpty()) {
                                                serverMessage.setClientNonce(clientNonce);
                                            }

                                            acknowledgeSentMessage(finalMessageId, serverMessage);
                                        }
                                    } catch (Exception ignored) {}
                                    clearReplyState();
                                } else {
                                    markSendFailed(finalMessageId, messagePosition,
                                        jsonResponse.optString("message", "Failed to send message"));
                                }
                            } catch (JSONException e) {
                                Log.e("BaseActivity", "Error processing response", e);
                                markSendFailed(finalMessageId, messagePosition, e.getMessage());
                            }
                        } else {
                            markSendFailed(finalMessageId, messagePosition, "HTTP " + response.code());
                        }
                    });
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onFailure(Call call, IOException e) {
                    android.util.Log.e("BaseChatActivity", "Send message failed: " + e.getMessage());
                    runOnUiThread(() -> markSendFailed(finalMessageId, messagePosition, e.getMessage()));
                }
            });
        } catch (JSONException e) {
            Log.e("BaseActivity", "Error preparing message", e);
            markSendFailed(finalMessageId, messagePosition, e.getMessage());
            Toast.makeText(this, getString(R.string.error_error_preparing_message_will_retry_later), Toast.LENGTH_SHORT).show();
        }
    }

    private void markSendFailed(String messageId, int messagePosition, String error) {
        int idx = messagePosition >= 0 && messagePosition < messages.size()
                ? messagePosition
                : indexOfMessageById(messageId);
        if (idx >= 0) {
            Message message = messages.get(idx);
            if (isTimeoutError(error)) {
                message.setSyncStatus(Message.SYNC_PENDING);
                if (messageRepository != null && messageId != null && !messageId.isEmpty()) {
                    messageRepository.markMessageAsPending(messageId, error);
                }
            } else {
                message.setSyncStatus(Message.SYNC_FAILED);
                if (messageRepository != null && messageId != null && !messageId.isEmpty()) {
                    messageRepository.markMessageAsFailed(messageId, error);
                }
            }
            if (messageAdapter != null) {
                messageAdapter.notifyItemChanged(idx, com.example.chatappjava.adapters.MessageAdapter.PAYLOAD_SEND_STATUS);
            }
        } else if (messageRepository != null && messageId != null && !messageId.isEmpty()) {
            if (isTimeoutError(error)) {
                messageRepository.markMessageAsPending(messageId, error);
            } else {
                messageRepository.markMessageAsFailed(messageId, error);
            }
        }
        if (isTimeoutError(error)) {
            if (syncManager != null) {
                syncManager.markSendTimeout(messageId);
            }
            scheduleReconcileAfterSendTimeout();
            if (isNetworkAvailable()) {
                showSendDelayedToastOnce();
            } else {
                showOfflineSendToastOnce();
            }
            return;
        }
        if (!isNetworkAvailable()) {
            showOfflineSendToastOnce();
        } else if (error != null && !error.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.error_network_detail, error),
                Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isTimeoutError(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase(Locale.US);
        return lower.contains("timeout") || lower.contains("timed out");
    }

    /** After HTTP timeout: delta sync + DB append may find the server copy before POST retry. */
    private void scheduleReconcileAfterSendTimeout() {
        appendHandler.postDelayed(this::reconcilePendingFromDbAndServer, SEND_TIMEOUT_RECONCILE_MS);
    }

    private void reconcileAfterNetworkRestore() {
        reconcilePendingFromDbAndServer();
        appendHandler.postDelayed(() -> {
            if (syncManager != null) {
                syncManager.syncPendingMessages();
            }
        }, NETWORK_RESTORE_POST_DELAY_MS);
    }

    private void reconcilePendingFromDbAndServer() {
        catchUpAfterDisconnect();
    }

    /** Close pending outbox rows when a synced server copy already exists in DB. */
    private void reconcileOutboxFromDb() {
        if (currentChat == null || messageRepository == null) {
            return;
        }
        List<Message> pending = messageRepository.getPendingMessagesForChat(currentChat.getId());
        for (Message pendingMsg : pending) {
            Message existing = messageRepository.findMatchingServerMessage(pendingMsg);
            if (existing != null) {
                messageRepository.resolvePendingWithServerMessage(pendingMsg.getId(), existing);
                final String tempId = pendingMsg.getId();
                final Message serverCopy = existing;
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    acknowledgeSentMessage(tempId, serverCopy);
                } else {
                    runOnUiThread(() -> acknowledgeSentMessage(tempId, serverCopy));
                }
            }
        }
    }

    private void showOfflineSendToastOnce() {
        long now = System.currentTimeMillis();
        if (now - lastOfflineSendToastAt < OFFLINE_TOAST_DEBOUNCE_MS) {
            return;
        }
        lastOfflineSendToastAt = now;
        Toast.makeText(this,
            getString(R.string.msg_no_connection_message_will_be_sent_when_network_is_available),
            Toast.LENGTH_SHORT).show();
    }

    private void showSendDelayedToastOnce() {
        long now = System.currentTimeMillis();
        if (now - lastOfflineSendToastAt < OFFLINE_TOAST_DEBOUNCE_MS) {
            return;
        }
        lastOfflineSendToastAt = now;
        Toast.makeText(this,
            getString(R.string.msg_send_delayed_retrying),
            Toast.LENGTH_SHORT).show();
    }

    protected void scrollToBottom() {
        if (!messages.isEmpty() && shouldAutoScroll && rvMessages != null) {
            scrollToBottomInstant();
        }
    }

    private void scrollToBottomInstant() {
        if (rvMessages == null || messages.isEmpty()) return;
        int lastPosition = messages.size() - 1;
        rvMessages.stopScroll();
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm != null) {
            lm.scrollToPositionWithOffset(lastPosition, 0);
        } else {
            rvMessages.scrollToPosition(lastPosition);
        }
    }

    protected boolean isAtBottom() {
        if (messages.isEmpty()) return true;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (layoutManager == null) return true;
        
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        int totalItemCount = layoutManager.getItemCount();
        
        // Consider at bottom if user is within 2 messages from the end (tighter threshold for smart auto-scroll)
        int distanceFromBottom = totalItemCount - 1 - lastVisiblePosition;
        return distanceFromBottom <= 2;
    }
    
    /**
     * Check if user is reading old messages (more than 5 messages from bottom)
     */
    protected boolean isReadingOldMessages() {
        if (messages.isEmpty()) return false;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (layoutManager == null) return false;
        
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        int totalItemCount = layoutManager.getItemCount();
        int distanceFromBottom = totalItemCount - 1 - lastVisiblePosition;
        
        // User is reading old messages if more than 5 messages from bottom
        return distanceFromBottom > 5;
    }

    protected void scrollToBottomIfAtBottom() {
        // CRITICAL: Only auto-scroll if shouldAutoScroll is true
        // If shouldAutoScroll is false, user has scrolled up and we MUST NOT scroll down
        // This is the most important check to prevent unwanted scrolling
        if (!shouldAutoScroll) {
            // User is reading older messages - don't scroll, but mark that there are new messages
            hasNewMessages = true;
            newMessagesCount++;
            return; // Exit early - never scroll if shouldAutoScroll is false
        }
        
        // Double-check: Only auto-scroll if user is actually at bottom
        // This prevents unwanted scrolling when user is reading old messages
        if (isAtBottom()) {
            scrollToBottom();
            hasNewMessages = false;
            newMessagesCount = 0;
        } else {
            // User is reading older messages - don't scroll, but mark that there are new messages
            hasNewMessages = true;
            newMessagesCount++;
        }
    }

    protected void forceScrollToBottom() {
        // When user sends a message, always scroll and re-enable auto-scroll
        shouldAutoScroll = true;
        scrollToBottom();
        hasNewMessages = false;
        newMessagesCount = 0;
    }

    protected void enableAutoScrollForNewMessages() {
        // Reset the flag to enable auto-scroll for new messages
        shouldAutoScroll = true;
        hasNewMessages = false;
        newMessagesCount = 0;
        updateScrollToBottomButton();
    }
    
    private void mergeRecentMessageUpdatesFromDB() {
        if (currentChat == null || messageRepository == null || messages == null || messages.isEmpty()) {
            return;
        }
        if (isUpdatingMessages || isLoadingMore) {
            return;
        }

        new Thread(() -> {
            int loadLimit = Math.max(messages.size(), initialDbLoadLimit);
            List<Message> dbMessages = messageRepository.getMessagesForChat(currentChat.getId(), loadLimit);
            if (dbMessages == null || dbMessages.isEmpty()) {
                return;
            }

            java.util.Map<String, Message> dbById = new java.util.HashMap<>();
            for (Message m : dbMessages) {
                if (m.getId() != null && !m.getId().isEmpty()) {
                    dbById.put(m.getId(), m);
                }
            }

            runOnUiThread(() -> {
                if (messageAdapter == null) {
                    return;
                }
                java.util.List<Integer> reactionOnlyIndices = new java.util.ArrayList<>();
                java.util.List<Integer> fullUpdateIndices = new java.util.ArrayList<>();
                for (int i = 0; i < messages.size(); i++) {
                    Message local = messages.get(i);
                    if (local == null || local.getId() == null) {
                        continue;
                    }
                    Message fromDb = dbById.get(local.getId());
                    if (fromDb == null) {
                        continue;
                    }
                    if (fromDb.isEdited() && fromDb.getEditedAt() <= 0) {
                        fromDb.setEdited(false);
                    }
                    fromDb.ensureReactionSummaryFromRaw();
                    local.ensureReactionSummaryFromRaw();

                    if (!hasNonReactionVisualChanges(local, fromDb)) {
                        if (!Message.reactionsVisuallyEqual(local, fromDb)) {
                            local.copyReactionDataFrom(fromDb);
                            reactionOnlyIndices.add(i);
                        }
                        continue;
                    }
                    preserveSenderIfMissing(fromDb, local);
                    messages.set(i, fromDb);
                    fullUpdateIndices.add(i);
                }
                for (int idx : reactionOnlyIndices) {
                    messageAdapter.notifyItemChanged(idx, com.example.chatappjava.adapters.MessageAdapter.PAYLOAD_REACTION);
                }
                for (int idx : fullUpdateIndices) {
                    messageAdapter.notifyItemChanged(idx);
                }
            });
        }).start();
    }

    private static void preserveSenderIfMissing(Message target, Message fallback) {
        if (target == null || fallback == null) {
            return;
        }
        if (target.getSenderId() == null || target.getSenderId().isEmpty()) {
            target.setSenderId(fallback.getSenderId());
        }
        if (target.getSenderDisplayName() == null || target.getSenderDisplayName().isEmpty()) {
            target.setSenderDisplayName(fallback.getSenderDisplayName());
        }
        if (target.getSenderAvatarUrl() == null || target.getSenderAvatarUrl().isEmpty()) {
            target.setSenderAvatarUrl(fallback.getSenderAvatarUrl());
        }
    }

    private static boolean hasNonReactionVisualChanges(Message local, Message fromDb) {
        if (!java.util.Objects.equals(local.getContent(), fromDb.getContent())) {
            return true;
        }
        if (local.shouldShowEditedLabel() != fromDb.shouldShowEditedLabel()) {
            return true;
        }
        return false;
    }

    
    /**
     * Update scroll-to-bottom button visibility and text
     * Show button when user is not at bottom and there are new messages
     */
    protected void updateScrollToBottomButton() {
        if (btnScrollToBottom == null) return;
        
        boolean isAtBottomNow = isAtBottom();
        boolean shouldShow = !isAtBottomNow && (hasNewMessages || newMessagesCount > 0);
        
        if (shouldShow) {
            btnScrollToBottom.setVisibility(android.view.View.VISIBLE);
            if (newMessagesCount > 0) {
                btnScrollToBottom.setText(getString(R.string.scroll_to_new_messages_count, newMessagesCount));
            } else {
                btnScrollToBottom.setText(getString(R.string.scroll_to_new_messages));
            }
        } else {
            btnScrollToBottom.setVisibility(android.view.View.GONE);
        }
    }
    
    /**
     * Safely add a new message to the list and notify adapter
     * This prevents IndexOutOfBoundsException by ensuring updates happen when RecyclerView is ready
     * Follows Messenger-style pattern: only update when RecyclerView is idle
     */
    private void safeAddMessage(Message message, boolean shouldScroll) {
        if (message == null || messageAdapter == null) return;
        boolean inserted = upsertChatMessage(message, false);
        if (inserted && shouldScroll && shouldAutoScroll && !isUserReadingOldMessages) {
            scrollToBottomInstant();
        }
    }

    /**
     * Server message acknowledged in DB first, then reflected in UI.
     * Returns true when a new row was inserted in the list.
     */
    private boolean onServerMessageArrived(Message serverMessage, boolean scrollIfAtBottom) {
        if (serverMessage == null) {
            return false;
        }
        if (messageRepository != null) {
            messageRepository.resolvePendingWithServerMessage(serverMessage);
        }
        return upsertChatMessage(serverMessage, scrollIfAtBottom);
    }

    /**
     * Insert or update a message in the list. Returns true when a new row was inserted.
     */
    private boolean upsertChatMessage(Message incoming, boolean scrollIfAtBottom) {
        if (incoming == null || messageAdapter == null) return false;

        // Level 1: match by server/local id
        if (incoming.getId() != null && !incoming.getId().isEmpty()) {
            int existing = indexOfMessageById(incoming.getId());
            if (existing >= 0) {
                mergeMessageAt(existing, incoming);
                removeOrphanedPlaceholdersMatchedBy(incoming);
                return false;
            }
        }

        // Level 2: match by clientNonce on list
        String nonce = incoming.getClientNonce();
        if (nonce != null && !nonce.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message local = messages.get(i);
                if (nonce.equals(local.getClientNonce())) {
                    closeOutboxForPlaceholder(local.getId(), incoming);
                    mergeMessageAt(i, incoming);
                    clearOutgoingNonce(nonce);
                    removeOrphanedPlaceholdersMatchedBy(incoming);
                    return false;
                }
            }
        }

        // Level 3–5: nonce registry then fuzzy placeholder match
        int placeholder = resolvePlaceholderIndex(incoming);
        if (placeholder >= 0) {
            closeOutboxForPlaceholder(messages.get(placeholder).getId(), incoming);
            mergeMessageAt(placeholder, incoming);
            if (nonce != null && !nonce.isEmpty()) {
                clearOutgoingNonce(nonce);
            }
            removeOrphanedPlaceholdersMatchedBy(incoming);
            return false;
        }

        messages.add(incoming);
        int pos = messages.size() - 1;
        messageAdapter.notifyItemInserted(pos);
        updateMessagesEmptyState();

        if (scrollIfAtBottom && shouldAutoScroll && !isUserReadingOldMessages && isAtBottom()) {
            scrollToBottomInstant();
        }
        return true;
    }

    /**
     * Acknowledge a sent message: replace temp with server copy, or drop temp if real already shown.
     */
    private void acknowledgeSentMessage(String tempId, Message serverMessage) {
        if (serverMessage == null || messageAdapter == null) return;
        serverMessage.setSyncStatus(Message.SYNC_SYNCED);

        if (messageRepository != null) {
            if (tempId != null && !tempId.isEmpty()) {
                messageRepository.resolvePendingWithServerMessage(tempId, serverMessage);
            } else {
                messageRepository.resolvePendingWithServerMessage(serverMessage);
            }
        }

        String clientNonce = serverMessage.getClientNonce();
        if ((clientNonce == null || clientNonce.isEmpty()) && tempId != null) {
            for (Map.Entry<String, String> entry : nonceToTempId.entrySet()) {
                if (tempId.equals(entry.getValue())) {
                    clientNonce = entry.getKey();
                    serverMessage.setClientNonce(clientNonce);
                    break;
                }
            }
        }

        int tempIdx = tempId != null ? indexOfMessageById(tempId) : -1;
        if (tempIdx < 0 && clientNonce != null && !clientNonce.isEmpty()) {
            String mappedTempId = nonceToTempId.get(clientNonce);
            if (mappedTempId != null) {
                tempIdx = indexOfMessageById(mappedTempId);
                if (tempIdx >= 0) {
                    tempId = mappedTempId;
                }
            }
        }

        String realId = serverMessage.getId();
        int existingRealIdx = (realId != null && !realId.isEmpty()) ? indexOfMessageById(realId) : -1;

        if (tempIdx >= 0) {
            Message local = messages.get(tempIdx);
            preserveSenderIfMissing(serverMessage, local);
            if (serverMessage.getClientNonce() == null || serverMessage.getClientNonce().isEmpty()) {
                serverMessage.setClientNonce(local.getClientNonce());
            }

            if (existingRealIdx >= 0 && existingRealIdx != tempIdx) {
                messages.remove(tempIdx);
                messageAdapter.notifyItemRemoved(tempIdx);
                mergeMessageAt(existingRealIdx, serverMessage);
            } else {
                mergeMessageAt(tempIdx, serverMessage);
            }
        } else if (existingRealIdx >= 0) {
            mergeMessageAt(existingRealIdx, serverMessage);
        } else {
            upsertChatMessage(serverMessage, false);
        }

        if (clientNonce != null && !clientNonce.isEmpty()) {
            clearOutgoingNonce(clientNonce);
        }
        if (syncManager != null && tempId != null) {
            syncManager.clearSendTimeoutGrace(tempId);
        }
        updateConversationPreviewAsync(serverMessage, false);
    }

    private void onTempMessageRemovedFromDb(String tempId, String realId) {
        if (tempId == null || messageAdapter == null) return;

        int tempIdx = indexOfMessageById(tempId);
        if (tempIdx < 0) {
            clearNonceForTempId(tempId);
            return;
        }

        int realIdx = (realId != null && !realId.isEmpty()) ? indexOfMessageById(realId) : -1;
        messages.remove(tempIdx);
        messageAdapter.notifyItemRemoved(tempIdx);
        clearNonceForTempId(tempId);
        if (syncManager != null) {
            syncManager.clearSendTimeoutGrace(tempId);
        }

        if (realIdx < 0) {
            scheduleAppendNewMessagesFromDb(false);
        }
    }

    private void mergeMessageAt(int index, Message incoming) {
        preserveSenderIfMissing(incoming, messages.get(index));
        if (!isPlaceholderId(incoming.getId())) {
            incoming.setSyncStatus(Message.SYNC_SYNCED);
        }
        messages.set(index, incoming);
        messageAdapter.notifyItemChanged(index);
    }

    private void notifySendStatusChanged(String messageId) {
        if (messageAdapter == null || messageId == null) {
            return;
        }
        int idx = indexOfMessageById(messageId);
        if (idx >= 0) {
            messageAdapter.notifyItemChanged(idx, com.example.chatappjava.adapters.MessageAdapter.PAYLOAD_SEND_STATUS);
        }
    }

    protected void retryFailedMessage(Message message) {
        if (message == null || message.getId() == null || message.getId().isEmpty()) {
            return;
        }
        message.setSyncStatus(Message.SYNC_PENDING);
        if (messageRepository != null) {
            messageRepository.markMessageAsPending(message.getId(), null);
        }
        notifySendStatusChanged(message.getId());
        Toast.makeText(this, getString(R.string.message_status_retry_toast), Toast.LENGTH_SHORT).show();
        if (syncManager != null) {
            syncManager.syncPendingMessages();
        }
    }

    /** Scan list for temp rows that already have a synced counterpart in the UI. */
    private void sweepOrphanPlaceholders() {
        if (messageAdapter == null || messages == null) return;

        String userId = databaseManager != null ? databaseManager.getUserId() : null;
        String chatId = currentChat != null ? currentChat.getId() : null;
        if (userId == null || chatId == null) return;

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message placeholder = messages.get(i);
            if (!isPlaceholderId(placeholder.getId())) continue;
            if (!userId.equals(placeholder.getSenderId())) continue;
            if (!chatId.equals(placeholder.getChatId())) continue;

            for (int j = 0; j < messages.size(); j++) {
                if (i == j) continue;
                Message synced = messages.get(j);
                if (isPlaceholderId(synced.getId())) continue;
                if (!userId.equals(synced.getSenderId())) continue;
                if (!placeholderMatchesSynced(placeholder, synced)) continue;

                String tempId = placeholder.getId();
                closeOutboxForPlaceholder(tempId, synced);
                messages.remove(i);
                messageAdapter.notifyItemRemoved(i);
                clearNonceForTempId(tempId);
                if (syncManager != null) {
                    syncManager.clearSendTimeoutGrace(tempId);
                }
                break;
            }
        }
    }

    private boolean placeholderMatchesSynced(Message placeholder, Message synced) {
        if (placeholder == null || synced == null) return false;

        String nonce = synced.getClientNonce();
        if (nonce != null && !nonce.isEmpty() && nonce.equals(placeholder.getClientNonce())) {
            return true;
        }

        String syncedType = synced.getType() != null ? synced.getType() : "text";
        String placeholderType = placeholder.getType() != null ? placeholder.getType() : "text";
        if (!syncedType.equals(placeholderType)) return false;
        if (!normalizeMessageContent(synced).equals(normalizeMessageContent(placeholder))) return false;
        return Math.abs(synced.getTimestamp() - placeholder.getTimestamp()) <= PLACEHOLDER_MATCH_WINDOW_MS;
    }

    /**
     * When a synced message is already in the list, drop matching temp rows elsewhere
     * (e.g. real at end, orphan temp in the middle after timeout).
     */
    private void removeOrphanedPlaceholdersMatchedBy(Message incoming) {
        if (incoming == null || isPlaceholderId(incoming.getId()) || messageAdapter == null) {
            return;
        }

        String userId = databaseManager != null ? databaseManager.getUserId() : null;
        String chatId = currentChat != null ? currentChat.getId() : null;
        if (userId == null || chatId == null) {
            return;
        }

        String incomingId = incoming.getId();

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message local = messages.get(i);
            if (!isPlaceholderId(local.getId())) continue;
            if (incomingId != null && incomingId.equals(local.getId())) continue;
            if (!userId.equals(local.getSenderId())) continue;
            if (!chatId.equals(local.getChatId())) continue;

            if (!placeholderMatchesSynced(local, incoming)) continue;

            String tempId = local.getId();
            closeOutboxForPlaceholder(tempId, incoming);
            messages.remove(i);
            messageAdapter.notifyItemRemoved(i);
            clearNonceForTempId(tempId);
            if (syncManager != null) {
                syncManager.clearSendTimeoutGrace(tempId);
            }
        }
    }

    private void closeOutboxForPlaceholder(String tempId, Message serverMessage) {
        if (messageRepository == null || tempId == null || serverMessage == null) {
            return;
        }
        if (!isPlaceholderId(tempId)) {
            return;
        }
        messageRepository.resolvePendingWithServerMessage(tempId, serverMessage);
    }

    private void registerOutgoingNonce(String clientNonce, String tempId) {
        if (clientNonce != null && !clientNonce.isEmpty() && tempId != null && !tempId.isEmpty()) {
            nonceToTempId.put(clientNonce, tempId);
        }
    }

    private void clearOutgoingNonce(String clientNonce) {
        if (clientNonce != null && !clientNonce.isEmpty()) {
            nonceToTempId.remove(clientNonce);
        }
    }

    private void clearNonceForTempId(String tempId) {
        if (tempId == null) return;
        nonceToTempId.entrySet().removeIf(entry -> tempId.equals(entry.getValue()));
    }

    private static boolean isPlaceholderId(String id) {
        return id != null && (id.startsWith("temp_") || id.startsWith("local-"));
    }

    private static String normalizeMessageContent(Message message) {
        if (message == null) return "";
        String content = message.getContent();
        return content != null ? content.trim() : "";
    }

    /**
     * Level 3: nonce registry. Level 4–5: fuzzy match with closest timestamp.
     */
    private int resolvePlaceholderIndex(Message incoming) {
        if (incoming == null) return -1;

        String nonce = incoming.getClientNonce();
        if (nonce != null && !nonce.isEmpty()) {
            String tempId = nonceToTempId.get(nonce);
            if (tempId != null) {
                int idx = indexOfMessageById(tempId);
                if (idx >= 0) return idx;
            }
        }

        String currentUserId = databaseManager != null ? databaseManager.getUserId() : null;
        String chatId = currentChat != null ? currentChat.getId() : null;
        if (currentUserId == null || chatId == null) return -1;

        int bestIdx = -1;
        long bestDelta = Long.MAX_VALUE;
        long incomingTs = incoming.getTimestamp();
        String incomingType = incoming.getType() != null ? incoming.getType() : "text";
        String incomingContent = normalizeMessageContent(incoming);

        for (int i = 0; i < messages.size(); i++) {
            Message local = messages.get(i);
            if (!isPlaceholderId(local.getId())) continue;
            if (!currentUserId.equals(local.getSenderId())) continue;
            if (!chatId.equals(local.getChatId())) continue;

            String localType = local.getType() != null ? local.getType() : "text";
            if (!incomingType.equals(localType)) continue;
            if (!incomingContent.equals(normalizeMessageContent(local))) continue;

            long delta = Math.abs(incomingTs - local.getTimestamp());
            if (delta > PLACEHOLDER_MATCH_WINDOW_MS) continue;

            if (delta < bestDelta) {
                bestDelta = delta;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
    
    /**
     * Wait for RecyclerView to be completely idle before executing action
     * This prevents IndexOutOfBoundsException during prefetch/layout
     */
    private void waitForRecyclerViewIdle(Runnable action) {
        if (rvMessages == null) {
            action.run();
            return;
        }
        
        // Stop any ongoing scroll first
        rvMessages.stopScroll();
        
        // Wait a bit to ensure RecyclerView has stopped all operations
        rvMessages.postDelayed(() -> {
            int scrollState = rvMessages.getScrollState();
            boolean isComputing = rvMessages.isComputingLayout();
            
            if (scrollState == RecyclerView.SCROLL_STATE_IDLE && !isComputing) {
                // RecyclerView is idle - execute immediately
                action.run();
            } else {
                // Still not idle - wait more and retry
                rvMessages.postDelayed(() -> {
                    if (rvMessages.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && !rvMessages.isComputingLayout()) {
                        action.run();
                    } else {
                        // Force execute after maximum wait time (300ms total)
                        rvMessages.postDelayed(action, 200);
                    }
                }, 150);
            }
        }, 50);
    }

    protected void scrollToMessage(String messageId) {
        if (messageId == null || messageId.isEmpty()) return;
        
        int position = indexOfMessageById(messageId);
        if (position >= 0) {
            rvMessages.smoothScrollToPosition(position);
            // Highlight the message briefly
            highlightMessage(position);
        } else {
            Toast.makeText(this, getString(R.string.error_message_not_found), Toast.LENGTH_SHORT).show();
        }
    }

    protected void highlightMessage(int position) {
        if (position < 0 || position >= messages.size()) return;
        
        // Get the view holder for the message
        RecyclerView.ViewHolder holder = rvMessages.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View messageView = holder.itemView;
            
            com.example.chatappjava.utils.MotionUtils.playAnimation(this, messageView, R.anim.message_highlight);
            new Handler(Looper.getMainLooper()).postDelayed(messageView::clearAnimation, 1000);
        }
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
                            if (mentionAdapter != null) {
                                mentionAdapter.notifyDataSetChanged();
                            }
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
        
        appendHandler.removeCallbacksAndMessages(null);
        nonceToTempId.clear();
        if (messageRepository != null) {
            messageRepository.setOnTempMessageRemovedListener(null);
        }
        if (networkConnectivityManager != null && networkCallback != null) {
            try {
                networkConnectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e("BaseChatActivity", "Error unregistering network callback", e);
            }
            networkCallback = null;
            networkConnectivityManager = null;
        }
        super.onDestroy();
        clearTypingState();
        if (socketManager != null) {
            if (chatMessageListener != null) {
                socketManager.removeMessageListener(chatMessageListener);
            }
            if (realtimeConnectionListener != null) {
                socketManager.removeConnectionListener(realtimeConnectionListener);
            }
            socketManager.removeTypingListener();
        }
    }

    // MessageAdapter.OnMessageClickListener implementation
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
    public void onFailedMessageRetry(Message message) {
        retryFailedMessage(message);
    }

    @Override
    public void onReactionRemove(Message message) {
        removeReactionFromMessage(message);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onReactClick(Message message, String emoji) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            return;
        }
        if (message == null || message.getId() == null || emoji == null || emoji.isEmpty()) {
            return;
        }

        String userId = databaseManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            return;
        }

        final Message reactionSnapshot = snapshotReactions(message);
        try {
            message.upsertUserReaction(userId, emoji);
            notifyReactionChanged(message.getId());
            persistReactionAsync(message);
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Optimistic reaction failed", e);
            return;
        }

        apiClient.addReaction(token, message.getId(), emoji, createReactionCallback(
                message,
                reactionSnapshot,
                R.string.error_add_reaction
        ));
    }

    private void removeReactionFromMessage(Message message) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            return;
        }
        if (message == null || message.getId() == null) {
            return;
        }

        String myUserId = databaseManager.getUserId();
        if (myUserId == null || myUserId.isEmpty()) {
            return;
        }

        String emojiToRemove = message.findReactionEmojiForUser(myUserId);
        if (emojiToRemove == null || emojiToRemove.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_reaction_to_remove), Toast.LENGTH_SHORT).show();
            return;
        }

        final Message reactionSnapshot = snapshotReactions(message);
        String removedEmoji = message.removeUserReactionForUser(myUserId);
        if (removedEmoji == null) {
            Toast.makeText(this, getString(R.string.error_no_reaction_to_remove), Toast.LENGTH_SHORT).show();
            return;
        }
        notifyReactionChanged(message.getId());
        persistReactionAsync(message);

        apiClient.removeReaction(token, message.getId(), emojiToRemove, createReactionCallback(
                message,
                reactionSnapshot,
                R.string.error_remove_reaction
        ));
    }

    private void persistReactionAsync(Message message) {
        if (message == null || message.getId() == null || messageRepository == null) {
            return;
        }
        final String messageId = message.getId();
        final String reactionsRaw = message.getReactionsRaw() != null ? message.getReactionsRaw() : "[]";
        new Thread(() -> messageRepository.updateMessageReactions(messageId, reactionsRaw)).start();
    }

    private Message snapshotReactions(Message message) {
        Message snapshot = new Message();
        message.ensureReactionSummaryFromRaw();
        snapshot.copyReactionDataFrom(message);
        return snapshot;
    }

    private void revertReactionUi(Message message, Message snapshot) {
        if (message == null || snapshot == null) {
            return;
        }
        message.copyReactionDataFrom(snapshot);
        notifyReactionChanged(message.getId());
    }

    private void notifyReactionChanged(String messageId) {
        if (messageAdapter == null || messageId == null || messageId.isEmpty()) {
            return;
        }
        int idx = indexOfMessageById(messageId);
        if (idx >= 0) {
            messageAdapter.notifyItemChanged(idx, com.example.chatappjava.adapters.MessageAdapter.PAYLOAD_REACTION);
        }
    }

    private okhttp3.Callback createReactionCallback(
            Message message,
            Message reactionSnapshot,
            int errorMessageResId
    ) {
        return new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    revertReactionUi(message, reactionSnapshot);
                    Toast.makeText(
                            BaseChatActivity.this,
                            getString(R.string.error_network_detail, e.getMessage()),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                boolean success = false;
                String errorDetail = null;
                String successBody = null;
                try {
                    success = response.isSuccessful();
                    if (success) {
                        if (response.body() != null) {
                            successBody = response.body().string();
                        }
                    } else {
                        errorDetail = "HTTP " + response.code();
                        if (response.body() != null) {
                            String body = response.body().string();
                            if (body != null && !body.isEmpty()) {
                                try {
                                    org.json.JSONObject json = new org.json.JSONObject(body);
                                    String serverMessage = json.optString("message", "");
                                    if (!serverMessage.isEmpty()) {
                                        errorDetail = serverMessage;
                                    }
                                } catch (org.json.JSONException ignored) {
                                    // keep HTTP code fallback
                                }
                            }
                        }
                    }
                } catch (java.io.IOException e) {
                    errorDetail = e.getMessage();
                } finally {
                    response.close();
                }

                if (success) {
                    final String body = successBody;
                    runOnUiThread(() -> {
                        applyReactionsFromApiResponse(message, body);
                        notifyReactionChanged(message.getId());
                    });
                    return;
                }

                final String detail = errorDetail;
                runOnUiThread(() -> {
                    revertReactionUi(message, reactionSnapshot);
                    if (detail != null && !detail.isEmpty()) {
                        Toast.makeText(
                                BaseChatActivity.this,
                                getString(errorMessageResId) + ": " + detail,
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                BaseChatActivity.this,
                                getString(errorMessageResId),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        };
    }

    private void applyReactionsFromApiResponse(Message message, String responseBody) {
        if (message == null || responseBody == null || responseBody.isEmpty()) {
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(responseBody);
            org.json.JSONObject data = json.optJSONObject("data");
            if (data == null || !data.has("reactions")) {
                return;
            }
            message.applyReactions(data.optJSONArray("reactions"));
            persistReactionAsync(message);
        } catch (org.json.JSONException e) {
            android.util.Log.w("BaseChatActivity", "Could not parse reaction API response", e);
        }
    }

    @Override
    public void onReplyClick(String replyToMessageId) {
        scrollToMessage(replyToMessageId);
    }

    protected void showImageZoomDialog(String imageUrl, String localImageUri) {
        showImageZoomDialog(imageUrl, localImageUri, null, 0);
    }
    
    protected void showImageZoomDialog(String imageUrl, String localImageUri, List<String> imageUrls, int currentIndex) {
        // If multiple images, use gallery dialog
        if (imageUrls != null && imageUrls.size() > 1) {
            showImageGalleryDialog(imageUrls, currentIndex);
            return;
        }
        
        // Single image - use original dialog
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
    
    protected void showImageGalleryDialog(List<String> imageUrls, int currentIndex) {
        // Create custom dialog
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_gallery);
        
        // Make dialog full screen
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }
        
        // Get views
        androidx.viewpager2.widget.ViewPager2 viewPager = dialog.findViewById(R.id.viewpager_images);
        ImageView ivClose = dialog.findViewById(R.id.iv_close);
        TextView tvImageCounter = dialog.findViewById(R.id.tv_image_counter);
        
        // Setup ViewPager2
        if (viewPager != null && imageUrls != null && !imageUrls.isEmpty()) {
            com.example.chatappjava.adapters.ImagePagerAdapter adapter = 
                new com.example.chatappjava.adapters.ImagePagerAdapter(imageUrls);
            viewPager.setAdapter(adapter);
            
            // Set initial position
            if (currentIndex >= 0 && currentIndex < imageUrls.size()) {
                viewPager.setCurrentItem(currentIndex, false);
            }
            
            // Show counter if more than 1 image
            if (imageUrls.size() > 1 && tvImageCounter != null) {
                tvImageCounter.setVisibility(View.VISIBLE);
                updateImageCounter(tvImageCounter, currentIndex + 1, imageUrls.size());
                
                // Update counter when page changes
                viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        updateImageCounter(tvImageCounter, position + 1, imageUrls.size());
                    }
                });
            } else if (tvImageCounter != null) {
                tvImageCounter.setVisibility(View.GONE);
            }
        }
        
        // Close button click
        if (ivClose != null) {
            ivClose.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Click outside to close (but not on ViewPager)
        View container = dialog.findViewById(R.id.dialog_container);
        if (container != null) {
            container.setOnClickListener(v -> {
                // Only dismiss if click is not on ViewPager
                if (viewPager != null && viewPager.getParent() != v) {
                    dialog.dismiss();
                }
            });
        }
        
        dialog.show();
    }
    
    private void updateImageCounter(TextView tvCounter, int current, int total) {
        if (tvCounter != null) {
            tvCounter.setText(current + " / " + total);
        }
    }

    private static String resolveIncomingChatId(org.json.JSONObject messageJson, Message incoming) {
        if (incoming != null && incoming.getChatId() != null && !incoming.getChatId().isEmpty()) {
            return incoming.getChatId().trim();
        }
        if (messageJson == null) {
            return "";
        }
        String chatId = messageJson.optString("chatId", "").trim();
        if (!chatId.isEmpty()) {
            return chatId;
        }
        return messageJson.optString("chat", "").trim();
    }

    private static boolean chatIdsMatch(String incomingChatId, String currentChatId) {
        if (incomingChatId == null || currentChatId == null) {
            return false;
        }
        return incomingChatId.equals(currentChatId);
    }

    // ===== Realtime message handlers =====
    protected void handleIncomingMessage(org.json.JSONObject messageJson) {
        try {
            Message incoming = Message.fromJson(messageJson);
            String chatId = resolveIncomingChatId(messageJson, incoming);
            if (incoming.getChatId() == null || incoming.getChatId().isEmpty()) {
                incoming.setChatId(chatId);
            }
            if (currentChat == null || chatId.isEmpty()) {
                return;
            }
            if (!chatIdsMatch(chatId, currentChat.getId())) {
                android.util.Log.d("BaseChatActivity",
                        "handleIncomingMessage dropped: chatId=" + chatId
                                + " current=" + currentChat.getId());
                return;
            }
            android.util.Log.d("BaseChatActivity",
                    "handleIncomingMessage chatId=" + chatId + " msgId=" + incoming.getId());

            boolean wasAtBottom = isAtBottom();
            boolean shouldScroll = wasAtBottom && shouldAutoScroll && !isUserReadingOldMessages;
            boolean inserted = upsertChatMessage(incoming, shouldScroll);

            if (inserted) {
                if (shouldScroll) {
                    scrollToBottomInstant();
                } else {
                    newMessagesCount++;
                    hasNewMessages = true;
                    updateScrollToBottomButton();
                }
            }

            final Message toPersist = incoming;
            persistMessageAsync(toPersist);
            updateConversationPreviewAsync(toPersist, false);
            appendHandler.post(this::updateSummarizeIndicator);
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Failed to handle incoming message: " + e.getMessage());
        }
    }

    protected void handleEditedMessage(org.json.JSONObject messageJson) {
        try {
            String chatId = messageJson.optString("chat");
            if (currentChat == null || !chatId.equals(currentChat.getId())) return;
            Message edited = Message.fromJson(messageJson);
            if (messageRepository != null) {
                messageRepository.saveMessage(edited);
            }
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

    protected void handleReactionUpdated(org.json.JSONObject reactionJson) {
        if (reactionJson == null) {
            return;
        }
        try {
            String chatId = reactionJson.optString("chatId", "");
            String messageId = reactionJson.optString("messageId", reactionJson.optString("_id", ""));
            if (messageId == null || messageId.isEmpty()) {
                return;
            }

            org.json.JSONArray reactions = reactionJson.optJSONArray("reactions");
            final String reactionsRaw = reactions != null ? reactions.toString() : "[]";

            new Thread(() -> {
                if (messageRepository != null) {
                    messageRepository.updateMessageReactions(messageId, reactionsRaw);
                }
            }).start();

            if (currentChat == null || chatId.isEmpty() || !chatIdsMatch(chatId, currentChat.getId())) {
                return;
            }

            int idx = indexOfMessageById(messageId);
            if (idx < 0 || messageAdapter == null) {
                return;
            }

            Message message = messages.get(idx);
            message.applyReactions(reactions);
            messageAdapter.notifyItemChanged(idx, com.example.chatappjava.adapters.MessageAdapter.PAYLOAD_REACTION);
        } catch (Exception e) {
            android.util.Log.e("BaseChatActivity", "Failed to handle reaction update: " + e.getMessage());
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

    private void removeMessageAndNotify(Message message) {
        int idx = messages.indexOf(message);
        if (idx >= 0) {
            messages.remove(idx);
            if (messageAdapter != null) {
                messageAdapter.notifyItemRemoved(idx);
            }
        }
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
            Toast.makeText(this, getString(R.string.msg_message_copied), Toast.LENGTH_SHORT).show();
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
        
        // View Info - Show sender's profile
        View optionViewInfo = dialogView.findViewById(R.id.option_view_info);
        if (optionViewInfo != null) {
            optionViewInfo.setVisibility(View.VISIBLE);
            optionViewInfo.setOnClickListener(v -> {
                // Get sender ID from message
                String senderId = message.getSenderId();
                String currentUserId = databaseManager.getUserId();
                
                android.util.Log.d("BaseChatActivity", "View Info clicked - senderId: " + senderId + ", currentUserId: " + currentUserId);
                
                if (senderId != null && !senderId.isEmpty()) {
                    // Always open ProfileViewActivity with sender's ID (even if it's current user)
                    // ProfileViewActivity will handle displaying the correct profile
                    Intent intent = new Intent(this, com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                    intent.putExtra("user", senderId); // Pass userId as string
                    android.util.Log.d("BaseChatActivity", "Opening ProfileViewActivity with senderId: " + senderId);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, getString(R.string.msg_sender_information_not_available), Toast.LENGTH_SHORT).show();
                }
                if (currentDialog != null) currentDialog.dismiss();
            });
        }
        
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

        if (title != null) title.setText(R.string.reaction_picker_title);
        int[] reactionCd = new int[]{
                R.string.reaction_like_cd,
                R.string.reaction_love_cd,
                R.string.reaction_haha_cd,
                R.string.reaction_wow_cd,
                R.string.reaction_sad_cd,
                R.string.reaction_fire_cd
        };
        for (int i = 0; i < emojiIds.length; i++) {
            View v = dialogView.findViewById(emojiIds[i]);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                tv.setContentDescription(getString(reactionCd[i]));
                tv.setText(com.example.chatappjava.utils.ReactionEmojis.MESSAGE_PICKER[i]);
                v.setOnClickListener(x -> {
                    try {
                        CharSequence emoji = tv.getText();
                        if (emoji != null) onReactClick(message, emoji.toString());
                    } catch (Exception ignored) {}
                    dlg.dismiss();
                });
            }
        }
        if (btnRemove != null) {
            String myUserId = databaseManager.getUserId();
            String myReaction = message.findReactionEmojiForUser(myUserId);
            btnRemove.setVisibility(
                    myReaction != null && !myReaction.isEmpty()
                            ? View.VISIBLE
                            : View.GONE
            );
            btnRemove.setOnClickListener(v -> {
                removeReactionFromMessage(message);
                dlg.dismiss();
            });
        }
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
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, getString(R.string.message_edit_failed), Toast.LENGTH_SHORT).show());
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
                                if (messageRepository != null) {
                                    messageRepository.saveMessage(message);
                                }
                                int idx = indexOfMessageById(message.getId());
                                if (idx >= 0) {
                                    messageAdapter.notifyItemChanged(idx);
                                }
                                Toast.makeText(BaseChatActivity.this, getString(R.string.message_edit_success), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(BaseChatActivity.this, json.optString("message", getString(R.string.message_edit_failed)), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            Toast.makeText(BaseChatActivity.this, getString(R.string.error_request_failed), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BaseChatActivity.this, getString(R.string.message_edit_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    protected void confirmDeleteForEveryone(Message message) {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
            this,
            getString(R.string.dialog_delete_message_title),
            getString(R.string.dialog_delete_message_body),
            getString(R.string.dialog_option_delete),
            getString(R.string.action_cancel),
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
                runOnUiThread(() -> Toast.makeText(BaseChatActivity.this, getString(R.string.message_delete_failed), Toast.LENGTH_SHORT).show());
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
                        }
                        Toast.makeText(BaseChatActivity.this, getString(R.string.message_delete_success), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BaseChatActivity.this, getString(R.string.message_delete_failed), Toast.LENGTH_SHORT).show();
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
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_failed_to_play_voice_message), Toast.LENGTH_SHORT).show());
                return true;
            });
            
        } catch (Exception e) {
            Log.e("BaseChatActivity", "Failed to play voice message", e);
            runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_play_voice, e.getMessage()), Toast.LENGTH_SHORT).show());
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
            Toast.makeText(this, getString(R.string.error_cannot_handle_file), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, getString(R.string.status_downloading), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, getString(R.string.msg_no_app_found_to_open_this_file), Toast.LENGTH_SHORT).show();
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

