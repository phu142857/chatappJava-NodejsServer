package com.example.chatappjava.ui.call;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chatappjava.R;
import com.example.chatappjava.models.CallParticipant;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.utils.AudioCaptureManager;
import com.example.chatappjava.utils.AudioFrameEncoder;
import com.example.chatappjava.utils.AudioPlaybackManager;
import com.example.chatappjava.utils.CameraCaptureManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.VideoFrameEncoder;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;

/**
 * Activity for private video calls (1-1 call, without WebRTC)
 * Continuously captures frames from the camera, encodes them and sends them to the server
 * Layout: Remote user full screen, local user small overlay at top right
 */
public class PrivateVideoCallActivity extends AppCompatActivity {
    private static final String TAG = "PrivateVideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    // CRITICAL: Reduced FPS to minimize encoding load and latency
    // 33ms = ~30 FPS - reduces encoding time significantly
    // Lower FPS = less encoding overhead = much lower latency
    private static final int FRAME_CAPTURE_INTERVAL_MS = 10; 
    // CRITICAL: Increase timeout to prevent losing video when user is idle
    // User may be still but camera is still capturing, just not moving much
    private static final long VIDEO_FRAME_TIMEOUT_MS = 10000; // 10 seconds (increased from 2s)
    
    // UI Components
    private FrameLayout flRemoteVideo;
    private FrameLayout flLocalVideo;
    private ImageView ivRemoteVideoFrame;
    private ImageView ivLocalVideoFrame;
    private LinearLayout llRemotePlaceholder;
    private LinearLayout llLocalPlaceholder;
    private CircleImageView ivRemoteAvatar;
    private CircleImageView ivLocalAvatar;
    private LinearLayout loadingOverlay;
    private TextView tvLoadingMessage;
    private TextView tvCallDuration;
    private ImageButton btnMute;
    private ImageButton btnCameraToggle;
    private ImageButton btnSwitchCamera;
    private ImageButton btnEndCall;
    
    // Data
    private String callId;
    private String chatId;
    private String remoteUserId;
    private String remoteUserName;
    private String remoteUserAvatar;
    private String currentUserId;
    private CallParticipant localParticipant;
    private CallParticipant remoteParticipant;
    
    // Managers
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private SocketManager socketManager;
    private CameraCaptureManager cameraCaptureManager;
    private AudioCaptureManager audioCaptureManager;
    private AudioPlaybackManager audioPlaybackManager;
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isCallActive = false;
    private boolean isFrontCamera = false;
    private AtomicBoolean isSendingFrame = new AtomicBoolean(false);
    private AtomicBoolean isSendingAudio = new AtomicBoolean(false);
    private Handler frameCaptureHandler;
    private Runnable frameCaptureRunnable;
    private long callStartTime;
    private Handler callDurationHandler;
    private Runnable callDurationRunnable;
    
    // Video frame cache
    private android.graphics.Bitmap localVideoBitmap;
    private android.graphics.Bitmap remoteVideoBitmap;
    
    // CRITICAL: Track last frame received time for remote participant
    private Long lastRemoteFrameReceivedTime;
    private Handler videoFrameTimeoutHandler;
    private Runnable videoFrameTimeoutRunnable;
    
    private ExecutorService videoProcessingExecutor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_video_call);
        
        // Get intent data
        getIntentData();
        
        // Initialize managers
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        socketManager = SocketManager.getInstance();
        currentUserId = databaseManager.getUserId();
        
        // Initialize views
        initViews();
        
        // Check permissions
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }
    
    private void getIntentData() {
        callId = getIntent().getStringExtra("callId");
        chatId = getIntent().getStringExtra("chatId");
        remoteUserId = getIntent().getStringExtra("remoteUserId");
        remoteUserName = getIntent().getStringExtra("remoteUserName");
        remoteUserAvatar = getIntent().getStringExtra("remoteUserAvatar");
        
        if (callId == null || chatId == null) {
            Toast.makeText(this, "Invalid call data", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initViews() {
        flRemoteVideo = findViewById(R.id.fl_remote_video);
        flLocalVideo = findViewById(R.id.fl_local_video);
        ivRemoteVideoFrame = findViewById(R.id.iv_remote_video_frame);
        ivLocalVideoFrame = findViewById(R.id.iv_local_video_frame);
        llRemotePlaceholder = findViewById(R.id.ll_remote_placeholder);
        llLocalPlaceholder = findViewById(R.id.ll_local_placeholder);
        ivRemoteAvatar = findViewById(R.id.iv_remote_avatar);
        ivLocalAvatar = findViewById(R.id.iv_local_avatar);
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvLoadingMessage = findViewById(R.id.tv_loading_message);
        tvCallDuration = findViewById(R.id.tv_call_duration);
        btnMute = findViewById(R.id.btn_mute);
        btnCameraToggle = findViewById(R.id.btn_camera_toggle);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnEndCall = findViewById(R.id.btn_end_call);
        
        // Load local avatar
        String localAvatar = databaseManager.getUserAvatar();
        loadAvatar(ivLocalAvatar, localAvatar);
        
        // Load remote avatar
        if (remoteUserAvatar != null && !remoteUserAvatar.isEmpty()) {
            loadAvatar(ivRemoteAvatar, remoteUserAvatar);
        } else if (remoteUserId != null) {
            // Fetch remote user avatar if not provided
            fetchRemoteUserAvatar();
        }
        
        // Setup click listeners
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnCameraToggle.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnEndCall.setOnClickListener(v -> endCall());
    }
    
    private void loadAvatar(CircleImageView imageView, String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.trim().isEmpty()) {
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + 
                           (avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl);
            }
            Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }
    
    private void fetchRemoteUserAvatar() {
        if (remoteUserId == null || remoteUserId.isEmpty()) {
            return;
        }
        
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        
        apiClient.authenticatedGet("/api/users/" + remoteUserId, token, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch remote user avatar", e);
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.optBoolean("success", false)) {
                            JSONObject userData = jsonResponse.optJSONObject("data");
                            if (userData != null) {
                                String avatar = userData.optString("avatar", "");
                                String username = userData.optString("username", "");
                                
                                runOnUiThread(() -> {
                                    remoteUserAvatar = avatar;
                                    if (username != null && !username.isEmpty()) {
                                        remoteUserName = username;
                                    }
                                    loadAvatar(ivRemoteAvatar, avatar);
                                });
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing user data for avatar", e);
                    }
                }
            }
        });
    }
    
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                initializeCall();
            } else {
                Toast.makeText(this, "Permissions are required for video call", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void initializeCall() {
        showLoading("Connecting to call...");
        
        try {
            // Setup audio mode for voice call
            setupAudioMode();
            
            // Setup listeners BEFORE joining room
            setupSocketListeners();
            
            // Initialize participants
            initializeParticipants();
            
            // Join call room via socket
            if (socketManager != null && callId != null) {
                socketManager.joinCallRoom(callId);
            } else {
                Log.e(TAG, "Cannot join call room: socketManager=" + (socketManager != null) + ", callId=" + callId);
                Toast.makeText(this, "Failed to connect to call", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // Start video capture (with error handling)
            try {
                startVideoCapture();
            } catch (Exception e) {
                Log.e(TAG, "Error starting video capture", e);
                // Don't end call - continue without video
            }
            
            // Start audio capture (with error handling)
            try {
                startAudioCapture();
            } catch (Exception e) {
                Log.e(TAG, "Error starting audio capture", e);
                // Don't end call - continue without audio
            }
            
            // Start call duration timer
            startCallDurationTimer();
            
            // Start video frame timeout checker
            startVideoFrameTimeoutChecker();
            
            hideLoading();
            isCallActive = true;
            Log.d(TAG, "Call initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Critical error initializing call", e);
            Toast.makeText(this, "Failed to initialize call: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void setupAudioMode() {
        try {
            android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // CRITICAL: Request audio focus for voice calls
                // This ensures our app has priority for audio playback
                int focusRequest = audioManager.requestAudioFocus(
                    null, // AudioFocusChangeListener (null for one-time request)
                    android.media.AudioManager.STREAM_MUSIC, // Stream type
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT // Request transient focus
                );
                Log.d(TAG, "Audio focus request result: " + focusRequest + " (AUDIOFOCUS_REQUEST_GRANTED=" + android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED + ")");
                
                // Set mode to IN_COMMUNICATION for voice calls
                audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                
                // CRITICAL: Set speakerphone on for better audio output
                // This ensures audio is routed to speaker/earpiece properly
                // For private calls, try speakerphone first to ensure audio is audible
                audioManager.setSpeakerphoneOn(true); // Use speaker for better audio output
                
                // CRITICAL: Since we're using USAGE_MEDIA in AudioTrack, set MUSIC stream volume
                // USAGE_MEDIA maps to STREAM_MUSIC, so we need to set MUSIC volume
                int maxMusicVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                int currentMusicVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                
                Log.d(TAG, "Current MUSIC stream volume: " + currentMusicVolume + "/" + maxMusicVolume);
                
                // CRITICAL: Set MUSIC stream volume to maximum to ensure audio is clearly audible
                // AudioTrack uses USAGE_MEDIA which maps to STREAM_MUSIC
                if (currentMusicVolume < maxMusicVolume) {
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 
                                                 maxMusicVolume, 0);
                    Log.d(TAG, "Set MUSIC stream volume to maximum: " + maxMusicVolume);
                } else {
                    Log.d(TAG, "MUSIC stream volume already at maximum: " + currentMusicVolume);
                }
                
                // Also check VOICE_CALL stream volume for reference
                int maxVoiceVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL);
                int currentVoiceVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
                Log.d(TAG, "VOICE_CALL stream volume: " + currentVoiceVolume + "/" + maxVoiceVolume);
                
                // Verify final volume
                int finalMusicVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                Log.d(TAG, "Audio mode set to MODE_IN_COMMUNICATION, speakerphone: true, MUSIC volume: " + finalMusicVolume + "/" + maxMusicVolume);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting audio mode", e);
        }
    }
    
    private void initializeParticipants() {
        // Local participant
        localParticipant = new CallParticipant();
        localParticipant.setUserId(currentUserId);
        localParticipant.setUsername(databaseManager.getUserName());
        localParticipant.setAvatar(databaseManager.getUserAvatar());
        localParticipant.setLocal(true);
        localParticipant.setAudioMuted(isMuted);
        localParticipant.setVideoMuted(!isCameraOn);
        
        // Remote participant (will be updated when they join)
        if (remoteUserId != null) {
            remoteParticipant = new CallParticipant();
            remoteParticipant.setUserId(remoteUserId);
            remoteParticipant.setUsername(remoteUserName != null ? remoteUserName : "Unknown");
            remoteParticipant.setAvatar(remoteUserAvatar);
            remoteParticipant.setLocal(false);
            remoteParticipant.setVideoMuted(false); // Default to camera on
        }
    }
    
    private void setupSocketListeners() {
        // Listener for received video frames
        socketManager.setVideoFrameListener(new SocketManager.VideoFrameListener() {
            @Override
            public void onVideoFrameReceived(String userId, String base64Frame, long timestamp) {
                Log.d(TAG, "Received video frame from user: " + userId);
                runOnUiThread(() -> {
                    // Only update if it's from remote user
                    if (remoteParticipant != null && remoteParticipant.getUserId() != null && 
                        remoteParticipant.getUserId().equals(userId)) {
                        // Track last frame received time
                        lastRemoteFrameReceivedTime = System.currentTimeMillis();
                        
                        // Update remote video frame
                        updateRemoteVideoFrame(base64Frame);
                        
                        // If remote was marked as video muted, update state
                        if (remoteParticipant.isVideoMuted()) {
                            Log.d(TAG, "Updating remote participant videoMuted state to false (received video frame)");
                            remoteParticipant.setVideoMuted(false);
                        }
                    }
                });
            }
        });
        
        // Listener for received audio frames
        socketManager.setAudioFrameListener(new SocketManager.AudioFrameListener() {
            @Override
            public void onAudioFrameReceived(String userId, String base64Audio, long timestamp) {
                // Only process if it's from remote user
                if (remoteParticipant != null && remoteParticipant.getUserId() != null && 
                    remoteParticipant.getUserId().equals(userId)) {
                    // Decode and play audio
                    playRemoteAudio(userId, base64Audio);
                }
            }
        });
        
        // Listener for call_room_joined - get remote participant info
        socketManager.on("call_room_joined", args -> {
            try {
                if (args == null || args.length == 0) {
                    Log.w(TAG, "call_room_joined: empty args, ignoring");
                    return;
                }
                
                JSONObject data = (JSONObject) args[0];
                JSONArray participantsArray = data.optJSONArray("participants");
                
                if (participantsArray != null) {
                    Log.d(TAG, "Received call_room_joined with " + participantsArray.length() + " participants");
                    runOnUiThread(() -> {
                        try {
                            // Find remote participant
                            for (int i = 0; i < participantsArray.length(); i++) {
                                try {
                                    JSONObject participantObj = participantsArray.getJSONObject(i);
                                    String userId;
                                    
                                    if (participantObj.has("userId")) {
                                        Object userIdObj = participantObj.get("userId");
                                        if (userIdObj instanceof JSONObject) {
                                            JSONObject userIdJson = (JSONObject) userIdObj;
                                            userId = userIdJson.optString("_id", userIdJson.optString("id", ""));
                                        } else {
                                            userId = userIdObj.toString();
                                        }
                                    } else {
                                        continue;
                                    }
                                    
                                    // Skip local participant
                                    if (userId != null && userId.equals(currentUserId)) {
                                        continue;
                                    }
                                    
                                    // Update remote participant
                                    String username = participantObj.optString("username", "");
                                    String avatar = participantObj.optString("avatar", "");
                                    
                                    if (remoteParticipant == null) {
                                        remoteParticipant = new CallParticipant();
                                    }
                                    remoteParticipant.setUserId(userId);
                                    remoteParticipant.setUsername(username);
                                    remoteParticipant.setAvatar(avatar);
                                    remoteParticipant.setLocal(false);
                                    
                                    // Update UI
                                    if (username != null && !username.isEmpty()) {
                                        remoteUserName = username;
                                    }
                                    if (avatar != null && !avatar.isEmpty()) {
                                        remoteUserAvatar = avatar;
                                        loadAvatar(ivRemoteAvatar, avatar);
                                    } else if (remoteUserId != null) {
                                        // Fetch avatar if empty
                                        fetchRemoteUserAvatar();
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error parsing participant at index " + i, e);
                                    // Continue with next participant
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing call_room_joined", e);
                            // Don't end call on parse error
                        }
                    });
                } else {
                    Log.w(TAG, "call_room_joined: no participants array");
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in call_room_joined listener", e);
                // Don't end call on unexpected error
            }
        });
        
        // Listener for user left call
        socketManager.on("user_left_call", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.optString("userId", "");
                
                // CRITICAL: Validate data before processing
                if (userId == null || userId.isEmpty()) {
                    Log.w(TAG, "user_left_call: invalid userId, ignoring");
                    return;
                }
                
                runOnUiThread(() -> {
                    // CRITICAL: Only end call if it's actually the remote user and call is active
                    if (isCallActive && remoteParticipant != null && remoteParticipant.getUserId() != null && 
                        remoteParticipant.getUserId().equals(userId)) {
                        Log.d(TAG, "Remote user left call: " + userId);
                        Toast.makeText(this, "Other user left the call", Toast.LENGTH_SHORT).show();
                        endCall();
                    } else {
                        Log.d(TAG, "user_left_call: ignoring (not remote user or call not active)");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in user_left_call listener", e);
                // Don't end call on unexpected error
            }
        });
        
        // Listener for call declined event
        socketManager.on("call_declined", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String declinedCallId = data.optString("callId", "");

                // CRITICAL: Validate callId before processing
                if (declinedCallId == null || declinedCallId.isEmpty()) {
                    Log.w(TAG, "call_declined: invalid callId, ignoring");
                    return;
                }

                runOnUiThread(() -> {
                    // CRITICAL: Only end call if it's actually the current call and call is active
                    if (isCallActive && callId != null && callId.equals(declinedCallId)) {
                        Log.d(TAG, "Call declined by remote user: " + declinedCallId);
                        Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show();
                        endCall();
                    } else {
                        Log.d(TAG, "call_declined: ignoring (not current call or call not active)");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in call_declined listener", e);
                // Don't end call on unexpected error
            }
        });
        
        // Listener for call ended event
        socketManager.on("call_ended", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String endedCallId = data.optString("callId", "");

                // CRITICAL: Validate callId before processing
                if (endedCallId == null || endedCallId.isEmpty()) {
                    Log.w(TAG, "call_ended: invalid callId, ignoring");
                    return;
                }

                runOnUiThread(() -> {
                    // CRITICAL: Only end call if it's actually the current call and call is active
                    if (isCallActive && callId != null && callId.equals(endedCallId)) {
                        Log.d(TAG, "Call ended: " + endedCallId);
                        Toast.makeText(this, "Call ended", Toast.LENGTH_SHORT).show();
                        endCall();
                    } else {
                        Log.d(TAG, "call_ended: ignoring (not current call or call not active)");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in call_ended listener", e);
                // Don't end call on unexpected error
            }
        });
    }
    
    private void updateLocalVideoFrame(String base64Frame, boolean isFrontCamera) {
        // CRITICAL: Only update if call is active and camera is on
        if (!isCallActive || !isCameraOn) {
            return;
        }
        
        // CRITICAL: Render just-in-time - decode and render immediately
        // No frame buffering - decode only when needed for display
        new Thread(() -> {
            try {
                android.graphics.Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
                if (bitmap != null) {
                    // Mirror bitmap for front camera
                    if (isFrontCamera) {
                        android.graphics.Matrix matrix = new android.graphics.Matrix();
                        matrix.postScale(-1, 1); // Mirror horizontally
                        android.graphics.Bitmap mirroredBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, 
                                                                      bitmap.getWidth(), bitmap.getHeight(), 
                                                                      matrix, true);
                        if (mirroredBitmap != bitmap) {
                            bitmap.recycle();
                        }
                        bitmap = mirroredBitmap;
                    }
                    
                    // CRITICAL: Update UI immediately - no buffering
                    final android.graphics.Bitmap finalBitmap = bitmap;
                    runOnUiThread(() -> {
                        try {
                            if (!isCallActive || !isCameraOn) {
                                finalBitmap.recycle();
                                return;
                            }
                            
                            // CRITICAL: Release old bitmap BEFORE setting new one
                            android.graphics.Bitmap oldBitmap = localVideoBitmap;
                            localVideoBitmap = finalBitmap;
                            
                            // Update UI immediately - just-in-time rendering
                            if (isCameraOn) {
                                ivLocalVideoFrame.setImageBitmap(finalBitmap);
                                ivLocalVideoFrame.setVisibility(View.VISIBLE);
                                llLocalPlaceholder.setVisibility(View.GONE);
                            }
                            
                            // Recycle old bitmap AFTER UI update
                            if (oldBitmap != null && !oldBitmap.isRecycled()) {
                                oldBitmap.recycle();
                            }
                        } catch (Exception e) {
                            // CRITICAL: Catch exceptions to prevent call from ending
                            Log.e(TAG, "Error updating local video frame UI", e);
                            if (finalBitmap != null && !finalBitmap.isRecycled()) {
                                finalBitmap.recycle();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                // CRITICAL: Catch all exceptions to prevent call from ending
                Log.e(TAG, "Error decoding local video frame", e);
            }
        }).start();
    }
    
    private void updateRemoteVideoFrame(String base64Frame) {
        // CRITICAL: Only update if call is active
        if (!isCallActive) {
            return;
        }
        
        // CRITICAL: Render just-in-time - decode and render immediately
        // No frame buffering - decode only when needed for display
        new Thread(() -> {
            try {
                android.graphics.Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
                if (bitmap != null) {
                    // CRITICAL: Update UI immediately - no buffering
                    final android.graphics.Bitmap finalBitmap = bitmap;
                    runOnUiThread(() -> {
                        try {
                            if (!isCallActive) {
                                finalBitmap.recycle();
                                return;
                            }
                            
                            // CRITICAL: Release old bitmap BEFORE setting new one
                            android.graphics.Bitmap oldBitmap = remoteVideoBitmap;
                            remoteVideoBitmap = finalBitmap;
                            
                            // Update UI immediately - just-in-time rendering
                            if (remoteParticipant != null && !remoteParticipant.isVideoMuted()) {
                                ivRemoteVideoFrame.setImageBitmap(finalBitmap);
                                ivRemoteVideoFrame.setVisibility(View.VISIBLE);
                                llRemotePlaceholder.setVisibility(View.GONE);
                            }
                            
                            // Recycle old bitmap AFTER UI update
                            if (oldBitmap != null && !oldBitmap.isRecycled()) {
                                oldBitmap.recycle();
                            }
                        } catch (Exception e) {
                            // CRITICAL: Catch exceptions to prevent call from ending
                            Log.e(TAG, "Error updating remote video frame UI", e);
                            if (finalBitmap != null && !finalBitmap.isRecycled()) {
                                finalBitmap.recycle();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                // CRITICAL: Catch all exceptions to prevent call from ending
                Log.e(TAG, "Error decoding remote video frame", e);
            }
        }).start();
    }
    
    private void startVideoCapture() {
        if (!isCameraOn) {
            return;
        }
        
        try {
            // Reset isFrontCamera flag
            isFrontCamera = false;
            
            cameraCaptureManager = new CameraCaptureManager(this);
            cameraCaptureManager.startCapture(new CameraCaptureManager.FrameCaptureCallback() {
                @Override
                public void onFrameCaptured(byte[] frameData, int width, int height) {
                    try {
                        if (frameData != null && isCallActive && isCameraOn) {
                            sendVideoFrame(frameData);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in video frame callback", e);
                        // Don't end call on frame processing error
                    }
                }
            });
            
            // Start periodic capture
            frameCaptureHandler = new Handler(Looper.getMainLooper());
            frameCaptureRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (isCallActive && isCameraOn && cameraCaptureManager != null && cameraCaptureManager.isCapturing()) {
                            frameCaptureHandler.postDelayed(this, FRAME_CAPTURE_INTERVAL_MS);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in frame capture runnable", e);
                        // Don't end call - just stop the runnable
                    }
                }
            };
            frameCaptureHandler.post(frameCaptureRunnable);
        } catch (Exception e) {
            Log.e(TAG, "Error starting video capture", e);
            // Don't end call - continue without video
            Toast.makeText(this, "Camera unavailable, continuing without video", Toast.LENGTH_SHORT).show();
        }
    }
    
    
    private void resetAudioMode() {
        try {
            android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(android.media.AudioManager.MODE_NORMAL);
                Log.d(TAG, "Audio mode reset to MODE_NORMAL");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting audio mode", e);
        }
    }
    
    private void sendVideoFrame(byte[] frameData) {
        // CRITICAL: Send frames immediately for lowest latency
        // Skip if previous encoding is still in progress to avoid queue buildup
        if (!isSendingFrame.compareAndSet(false, true)) {
            // Previous frame still encoding - skip this one to maintain low latency
            return;
        }
        
        // CRITICAL: Use thread pool instead of creating new thread for each frame
        if (videoProcessingExecutor == null || videoProcessingExecutor.isShutdown()) {
            videoProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "VideoProcessor");
                    t.setPriority(Thread.MAX_PRIORITY);
                    return t;
                }
            });
        }

        videoProcessingExecutor.execute(() -> {
            try {
                if (!isCallActive) {
                    isSendingFrame.set(false);
                    return;
                }
                
                String base64Frame = VideoFrameEncoder.encodeFrame(frameData);
                
                if (base64Frame != null && isCallActive) {
                    // CRITICAL: Send to server FIRST for lowest latency
                    if (socketManager != null && isCallActive) {
                        socketManager.sendVideoFrame(callId, base64Frame);
                    }
                    
                    // Update local video frame AFTER sending (non-blocking)
                    if (isCallActive) {
                        final boolean frontCamera = isFrontCamera;
                        runOnUiThread(() -> {
                            if (isCallActive) {
                                updateLocalVideoFrame(base64Frame, frontCamera);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // CRITICAL: Catch all exceptions to prevent call from ending
                Log.e(TAG, "Error sending video frame", e);
                // Don't end call on encoding error - just skip this frame
            } finally {
                isSendingFrame.set(false);
            }
        });
    }
    
    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        
        // Start or stop audio capture based on mute state
        if (isMuted) {
            stopAudioCapture();
        } else {
            startAudioCapture();
        }
        
        if (localParticipant != null) {
            localParticipant.setAudioMuted(isMuted);
        }
        
        // Send update to server
        updateMediaState();
    }
    
    private void toggleCamera() {
        isCameraOn = !isCameraOn;
        btnCameraToggle.setImageResource(isCameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);
        
        if (isCameraOn) {
            startVideoCapture();
        } else {
            stopVideoCapture();
            // Clear local video frame and show avatar
            if (localVideoBitmap != null && !localVideoBitmap.isRecycled()) {
                localVideoBitmap.recycle();
                localVideoBitmap = null;
            }
            ivLocalVideoFrame.setImageBitmap(null);
            ivLocalVideoFrame.setVisibility(View.GONE);
            llLocalPlaceholder.setVisibility(View.VISIBLE);
        }
        
        if (localParticipant != null) {
            localParticipant.setVideoMuted(!isCameraOn);
        }
        
        // Send update to server
        updateMediaState();
    }
    
    private void switchCamera() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.switchCamera();
            isFrontCamera = !isFrontCamera;
            Log.d(TAG, "Camera switched, isFrontCamera=" + isFrontCamera);
        }
    }
    
    private void stopVideoCapture() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        
        if (frameCaptureHandler != null && frameCaptureRunnable != null) {
            frameCaptureHandler.removeCallbacks(frameCaptureRunnable);
        }
    }
    
    private void startAudioCapture() {
        if (isMuted || !isCallActive) {
            return;
        }
        
        try {
            if (audioCaptureManager == null) {
                audioCaptureManager = new AudioCaptureManager();
            }
            
            audioCaptureManager.startCapture(new AudioCaptureManager.AudioCaptureCallback() {
                @Override
                public void onAudioCaptured(byte[] audioData, int sampleRate) {
                    try {
                        if (audioData != null && isCallActive && !isMuted) {
                            sendAudioFrame(audioData);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in audio capture callback", e);
                    }
                }
            });
            
            Log.d(TAG, "Audio capture started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
        }
    }
    
    private void stopAudioCapture() {
        if (audioCaptureManager != null) {
            audioCaptureManager.stopCapture();
        }
    }
    
    private void sendAudioFrame(byte[] audioData) {
        // Skip if previous encoding is still in progress
        if (!isSendingAudio.compareAndSet(false, true)) {
            return;
        }
        
        // Use thread pool for audio processing
        if (videoProcessingExecutor == null || videoProcessingExecutor.isShutdown()) {
            videoProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AudioProcessor");
                    t.setPriority(Thread.MAX_PRIORITY);
                    return t;
                }
            });
        }
        
        videoProcessingExecutor.execute(() -> {
            try {
                if (!isCallActive || isMuted) {
                    isSendingAudio.set(false);
                    return;
                }
                
                // Encode audio to base64
                String base64Audio = AudioFrameEncoder.encodeFrame(audioData);
                
                if (base64Audio != null && isCallActive && !isMuted) {
                    // Send to server
                    if (socketManager != null && isCallActive) {
                        socketManager.sendAudioFrame(callId, base64Audio);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending audio frame", e);
            } finally {
                isSendingAudio.set(false);
            }
        });
    }
    
    private void playRemoteAudio(String userId, String base64Audio) {
        if (!isCallActive || base64Audio == null || base64Audio.isEmpty()) {
            return;
        }
        
        // Decode audio in background thread
        new Thread(() -> {
            try {
                byte[] audioData = AudioFrameEncoder.decodeFrame(base64Audio);
                if (audioData != null && isCallActive) {
                    // Initialize playback manager if needed
                    if (audioPlaybackManager == null) {
                        audioPlaybackManager = new AudioPlaybackManager();
                    }
                    
                    // Start playback if not already playing
                    if (!audioPlaybackManager.isPlaying(userId)) {
                        audioPlaybackManager.startPlayback(userId, 16000); // 16kHz sample rate
                    }
                    
                    // Play audio data
                    audioPlaybackManager.playAudio(userId, audioData);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playing remote audio", e);
            }
        }).start();
    }
    
    
    private void updateMediaState() {
        // Send media state update to server
        // Note: You may need to create this method in ApiClient
    }
    
    private void startCallDurationTimer() {
        callStartTime = System.currentTimeMillis();
        callDurationHandler = new Handler(Looper.getMainLooper());
        callDurationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallActive) {
                    long duration = System.currentTimeMillis() - callStartTime;
                    long seconds = duration / 1000;
                    long minutes = seconds / 60;
                    seconds = seconds % 60;
                    
                    tvCallDuration.setText(String.format("%02d:%02d", minutes, seconds));
                    callDurationHandler.postDelayed(this, 1000);
                }
            }
        };
        callDurationHandler.post(callDurationRunnable);
    }
    
    /**
     * Start checking for video frame timeouts
     * If remote participant hasn't sent frames for VIDEO_FRAME_TIMEOUT_MS, assume camera is off
     */
    private void startVideoFrameTimeoutChecker() {
        videoFrameTimeoutHandler = new Handler(Looper.getMainLooper());
        videoFrameTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallActive && remoteParticipant != null) {
                    long currentTime = System.currentTimeMillis();
                    
                    // If we have received frames before but haven't received any recently
                    if (lastRemoteFrameReceivedTime != null) {
                        long timeSinceLastFrame = currentTime - lastRemoteFrameReceivedTime;
                        
                        // CRITICAL: Only mark as muted if timeout exceeded AND we haven't received frames for a long time
                        // This prevents losing video when user is idle but camera is still on
                        if (timeSinceLastFrame > VIDEO_FRAME_TIMEOUT_MS && !remoteParticipant.isVideoMuted()) {
                            Log.d(TAG, "Remote participant stopped sending frames for " + (timeSinceLastFrame / 1000) + "s, marking video as muted");
                            remoteParticipant.setVideoMuted(true);
                            
                            // Clear remote video frame and show avatar
                            if (remoteVideoBitmap != null && !remoteVideoBitmap.isRecycled()) {
                                remoteVideoBitmap.recycle();
                                remoteVideoBitmap = null;
                            }
                            ivRemoteVideoFrame.setImageBitmap(null);
                            ivRemoteVideoFrame.setVisibility(View.GONE);
                            llRemotePlaceholder.setVisibility(View.VISIBLE);
                        } else if (timeSinceLastFrame <= VIDEO_FRAME_TIMEOUT_MS && remoteParticipant.isVideoMuted()) {
                            // If we start receiving frames again, mark as video on
                            Log.d(TAG, "Remote participant resumed sending frames, marking video as on");
                            remoteParticipant.setVideoMuted(false);
                            // Note: Video frame will be updated when next frame arrives
                        }
                    } else if (lastRemoteFrameReceivedTime == null && remoteParticipant != null && !remoteParticipant.isVideoMuted()) {
                        // If we never received frames but participant is not marked as muted, wait longer
                        // This handles the case where call just started
                        Log.d(TAG, "Waiting for first video frame from remote participant");
                    }
                    
                    // Check again in 1 second (less frequent to reduce overhead)
                    videoFrameTimeoutHandler.postDelayed(this, 1000);
                }
            }
        };
        videoFrameTimeoutHandler.post(videoFrameTimeoutRunnable);
    }
    
    private void endCall() {
        isCallActive = false;
        
        // CRITICAL: Shutdown thread pools to prevent memory leaks
        if (videoProcessingExecutor != null && !videoProcessingExecutor.isShutdown()) {
            videoProcessingExecutor.shutdown();
            videoProcessingExecutor = null;
        }
        
        // Stop video capture
        stopVideoCapture();
        
        // Stop audio capture and playback
        stopAudioCapture();
        if (audioPlaybackManager != null) {
            audioPlaybackManager.stopAllPlayback();
        }
        
        // Reset audio mode
        resetAudioMode();
        
        // Leave call room
        if (socketManager != null) {
            socketManager.leaveCallRoom(callId);
            socketManager.removeVideoFrameListener();
            socketManager.removeAudioFrameListener();
        }
        
        // Stop call duration timer
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
        
        // Stop video frame timeout checker
        if (videoFrameTimeoutHandler != null && videoFrameTimeoutRunnable != null) {
            videoFrameTimeoutHandler.removeCallbacks(videoFrameTimeoutRunnable);
        }
        
        // CRITICAL: Clear ImageViews BEFORE recycling bitmaps to avoid "recycled bitmap" crash
        if (ivLocalVideoFrame != null) {
            ivLocalVideoFrame.setImageBitmap(null);
        }
        if (ivRemoteVideoFrame != null) {
            ivRemoteVideoFrame.setImageBitmap(null);
        }
        
        // Clean up bitmaps after clearing ImageViews
        if (localVideoBitmap != null && !localVideoBitmap.isRecycled()) {
            localVideoBitmap.recycle();
            localVideoBitmap = null;
        }
        if (remoteVideoBitmap != null && !remoteVideoBitmap.isRecycled()) {
            remoteVideoBitmap.recycle();
            remoteVideoBitmap = null;
        }
        
        // Navigate to home
        navigateToHome();
    }
    
    private void navigateToHome() {
        Log.d(TAG, "Navigating back to HomeActivity");
        
        // Create intent to go back to HomeActivity
        Intent intent = new Intent(this, com.example.chatappjava.ui.theme.HomeActivity.class);
        
        // Clear the activity stack and start fresh from HomeActivity
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        startActivity(intent);
        finish();
    }
    
    private void showLoading(String message) {
        loadingOverlay.setVisibility(View.VISIBLE);
        if (message != null) {
            tvLoadingMessage.setText(message);
        }
    }
    
    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // CRITICAL: Shutdown thread pools to prevent memory leaks
        if (videoProcessingExecutor != null && !videoProcessingExecutor.isShutdown()) {
            videoProcessingExecutor.shutdownNow();
            videoProcessingExecutor = null;
        }
        
        // Clean up resources
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        
        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
            socketManager.removeAudioFrameListener();
            socketManager.off("call_room_joined");
            socketManager.off("user_left_call");
            socketManager.off("call_declined");
            socketManager.off("call_ended");
        }
        
        // Stop audio capture and playback
        stopAudioCapture();
        if (audioPlaybackManager != null) {
            audioPlaybackManager.stopAllPlayback();
        }
        
        // CRITICAL: Clear ImageViews BEFORE recycling bitmaps to avoid "recycled bitmap" crash
        if (ivLocalVideoFrame != null) {
            ivLocalVideoFrame.setImageBitmap(null);
        }
        if (ivRemoteVideoFrame != null) {
            ivRemoteVideoFrame.setImageBitmap(null);
        }
        
        // Clean up bitmaps after clearing ImageViews
        if (localVideoBitmap != null && !localVideoBitmap.isRecycled()) {
            localVideoBitmap.recycle();
            localVideoBitmap = null;
        }
        if (remoteVideoBitmap != null && !remoteVideoBitmap.isRecycled()) {
            remoteVideoBitmap.recycle();
            remoteVideoBitmap = null;
        }
        
        if (frameCaptureHandler != null && frameCaptureRunnable != null) {
            frameCaptureHandler.removeCallbacks(frameCaptureRunnable);
        }
        
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
        
        if (videoFrameTimeoutHandler != null && videoFrameTimeoutRunnable != null) {
            videoFrameTimeoutHandler.removeCallbacks(videoFrameTimeoutRunnable);
        }
    }
}
