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
import com.example.chatappjava.utils.CameraCaptureManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.VideoFrameEncoder;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
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
    private static final int FRAME_CAPTURE_INTERVAL_MS = 100; // 10 FPS
    private static final long VIDEO_FRAME_TIMEOUT_MS = 2000; // 2 seconds
    
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
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isCallActive = false;
    private boolean isFrontCamera = false;
    private AtomicBoolean isSendingFrame = new AtomicBoolean(false);
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
        
        // Setup listeners BEFORE joining room
        setupSocketListeners();
        
        // Initialize participants
        initializeParticipants();
        
        // Join call room via socket
        socketManager.joinCallRoom(callId);
        
        // Start video capture
        startVideoCapture();
        
        // Start call duration timer
        startCallDurationTimer();
        
        // Start video frame timeout checker
        startVideoFrameTimeoutChecker();
        
        hideLoading();
        isCallActive = true;
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
        
        // Listener for call_room_joined - get remote participant info
        socketManager.on("call_room_joined", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                JSONArray participantsArray = data.optJSONArray("participants");
                
                if (participantsArray != null) {
                    Log.d(TAG, "Received call_room_joined with " + participantsArray.length() + " participants");
                    runOnUiThread(() -> {
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
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in call_room_joined listener", e);
            }
        });
        
        // Listener for user left call
        socketManager.on("user_left_call", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");
                
                runOnUiThread(() -> {
                    if (remoteParticipant != null && remoteParticipant.getUserId() != null && 
                        remoteParticipant.getUserId().equals(userId)) {
                        // Remote user left - end call
                        Toast.makeText(this, "Other user left the call", Toast.LENGTH_SHORT).show();
                        endCall();
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "Error receiving user_left_call", e);
            }
        });
        
        // Listener for call declined event
        socketManager.on("call_declined", args -> {
            JSONObject data = (JSONObject) args[0];
            String declinedCallId = data.optString("callId", "");

            runOnUiThread(() -> {
                // Check if this is the current call
                if (callId != null && callId.equals(declinedCallId)) {
                    Log.d(TAG, "Call declined by remote user");
                    Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show();
                    endCall();
                }
            });
        });
        
        // Listener for call ended event
        socketManager.on("call_ended", args -> {
            JSONObject data = (JSONObject) args[0];
            String endedCallId = data.optString("callId", "");

            runOnUiThread(() -> {
                // Check if this is the current call
                if (callId != null && callId.equals(endedCallId)) {
                    Log.d(TAG, "Call ended");
                    Toast.makeText(this, "Call ended", Toast.LENGTH_SHORT).show();
                    endCall();
                }
            });
        });
    }
    
    private void updateLocalVideoFrame(String base64Frame, boolean isFrontCamera) {
        // CRITICAL: Only update if call is active and camera is on
        if (!isCallActive || !isCameraOn) {
            return;
        }
        
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
            
            // Release old bitmap
            if (localVideoBitmap != null && !localVideoBitmap.isRecycled()) {
                localVideoBitmap.recycle();
            }
            
            localVideoBitmap = bitmap;
            
            // Update UI - only show if camera is on
            if (isCameraOn) {
                ivLocalVideoFrame.setImageBitmap(bitmap);
                ivLocalVideoFrame.setVisibility(View.VISIBLE);
                llLocalPlaceholder.setVisibility(View.GONE);
            }
        }
    }
    
    private void updateRemoteVideoFrame(String base64Frame) {
        // CRITICAL: Only update if call is active
        if (!isCallActive) {
            return;
        }
        
        android.graphics.Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
        if (bitmap != null) {
            // Release old bitmap
            if (remoteVideoBitmap != null && !remoteVideoBitmap.isRecycled()) {
                remoteVideoBitmap.recycle();
            }
            
            remoteVideoBitmap = bitmap;
            
            // Update UI - always show if remote video is on
            if (remoteParticipant != null && !remoteParticipant.isVideoMuted()) {
                ivRemoteVideoFrame.setImageBitmap(bitmap);
                ivRemoteVideoFrame.setVisibility(View.VISIBLE);
                llRemotePlaceholder.setVisibility(View.GONE);
            }
        }
    }
    
    private void startVideoCapture() {
        if (!isCameraOn) {
            return;
        }
        
        // Reset isFrontCamera flag
        isFrontCamera = false;
        
        cameraCaptureManager = new CameraCaptureManager(this);
        cameraCaptureManager.startCapture(new CameraCaptureManager.FrameCaptureCallback() {
            @Override
            public void onFrameCaptured(byte[] frameData, int width, int height) {
                if (frameData != null && isCallActive && isCameraOn) {
                    sendVideoFrame(frameData);
                }
            }
        });
        
        // Start periodic capture
        frameCaptureHandler = new Handler(Looper.getMainLooper());
        frameCaptureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallActive && isCameraOn && cameraCaptureManager != null && cameraCaptureManager.isCapturing()) {
                    frameCaptureHandler.postDelayed(this, FRAME_CAPTURE_INTERVAL_MS);
                }
            }
        };
        frameCaptureHandler.post(frameCaptureRunnable);
    }
    
    private void sendVideoFrame(byte[] frameData) {
        if (!isSendingFrame.compareAndSet(false, true)) {
            return;
        }
        
        new Thread(() -> {
            try {
                String base64Frame = VideoFrameEncoder.encodeFrame(frameData);
                
                if (base64Frame != null) {
                    // Update local video frame immediately
                    final boolean frontCamera = isFrontCamera;
                    runOnUiThread(() -> {
                        updateLocalVideoFrame(base64Frame, frontCamera);
                    });
                    
                    // Send to server to forward to remote user
                    if (socketManager != null) {
                        Log.d(TAG, "Sending video frame to server for callId: " + callId);
                        socketManager.sendVideoFrame(callId, base64Frame);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending video frame", e);
            } finally {
                isSendingFrame.set(false);
            }
        }).start();
    }
    
    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        
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
                        
                        // If timeout exceeded and remote is not marked as video muted
                        if (timeSinceLastFrame > VIDEO_FRAME_TIMEOUT_MS && !remoteParticipant.isVideoMuted()) {
                            Log.d(TAG, "Remote participant stopped sending frames, marking video as muted");
                            remoteParticipant.setVideoMuted(true);
                            
                            // Clear remote video frame and show avatar
                            if (remoteVideoBitmap != null && !remoteVideoBitmap.isRecycled()) {
                                remoteVideoBitmap.recycle();
                                remoteVideoBitmap = null;
                            }
                            ivRemoteVideoFrame.setImageBitmap(null);
                            ivRemoteVideoFrame.setVisibility(View.GONE);
                            llRemotePlaceholder.setVisibility(View.VISIBLE);
                        }
                    }
                    
                    // Check again in 500ms
                    videoFrameTimeoutHandler.postDelayed(this, 500);
                }
            }
        };
        videoFrameTimeoutHandler.post(videoFrameTimeoutRunnable);
    }
    
    private void endCall() {
        isCallActive = false;
        
        // Stop video capture
        stopVideoCapture();
        
        // Leave call room
        if (socketManager != null) {
            socketManager.leaveCallRoom(callId);
            socketManager.removeVideoFrameListener();
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
        
        // Clean up resources
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        
        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
            socketManager.off("call_room_joined");
            socketManager.off("user_left_call");
            socketManager.off("call_declined");
            socketManager.off("call_ended");
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
