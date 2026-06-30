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
import com.example.chatappjava.utils.CallMediaPipeline;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.VideoFrameEncoder;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;

/**
 * Activity for private video calls (1-1) via Socket.IO media relay.
 * Continuously captures frames from the camera, encodes them and sends them to the server
 * Layout: Remote user full screen, local user small overlay at top right
 */
public class PrivateVideoCallActivity extends AppCompatActivity {
    private static final String TAG = "PrivateVideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
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
    private CallMediaPipeline mediaPipeline;
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isCallActive = false;
    private boolean isFrontCamera = false;
    
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
            Toast.makeText(this, getString(R.string.error_invalid_call_data), Toast.LENGTH_SHORT).show();
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
        com.example.chatappjava.utils.CallAccessibilityHelper.bindInCallControls(
                btnMute, btnCameraToggle, btnSwitchCamera, btnEndCall, isMuted, isCameraOn);

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
                Toast.makeText(this, getString(R.string.error_permissions_are_required_for_video_call), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void initMediaPipeline() {
        if (mediaPipeline != null) {
            return;
        }
        mediaPipeline = new CallMediaPipeline(TAG, socketManager, new CallMediaPipeline.Host() {
            @Override
            public boolean isCallActive() {
                return isCallActive;
            }

            @Override
            public boolean isMuted() {
                return isMuted;
            }

            @Override
            public boolean isCameraOn() {
                return isCameraOn;
            }

            @Override
            public boolean isFrontCamera() {
                return isFrontCamera;
            }

            @Override
            public void setFrontCamera(boolean frontCamera) {
                isFrontCamera = frontCamera;
            }

            @Override
            public String getCallId() {
                return callId;
            }

            @Override
            public void runOnUiThread(Runnable runnable) {
                PrivateVideoCallActivity.this.runOnUiThread(runnable);
            }

            @Override
            public void onLocalVideoFrame(String base64Frame, boolean frontCamera) {
                updateLocalVideoFrame(base64Frame, frontCamera);
            }

            @Override
            public void onCameraUnavailable() {
                Toast.makeText(PrivateVideoCallActivity.this,
                        getString(R.string.msg_camera_unavailable_continuing_without_video),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAudioCaptureError(String message) {
                Toast.makeText(PrivateVideoCallActivity.this,
                        getString(R.string.error_failed_with_message, message),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeCall() {
        showLoading("Connecting to call...");
        
        try {
            initMediaPipeline();
            mediaPipeline.setupAudioMode(this);
            
            // Setup listeners BEFORE joining room
            setupSocketListeners();
            
            // Initialize participants
            initializeParticipants();
            
            // Join call room via socket
            if (socketManager != null && callId != null) {
                socketManager.joinCallRoom(callId);
            } else {
                Log.e(TAG, "Cannot join call room: socketManager=" + (socketManager != null) + ", callId=" + callId);
                Toast.makeText(this, getString(R.string.error_failed_to_connect_to_call), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            try {
                mediaPipeline.startVideoCapture(this);
            } catch (Exception e) {
                Log.e(TAG, "Error starting video capture", e);
            }

            try {
                mediaPipeline.ensurePlaybackReady();
                mediaPipeline.startAudioCapture();
            } catch (Exception e) {
                Log.e(TAG, "Error starting audio capture", e);
            }

            mediaPipeline.startCallDurationTimer(tvCallDuration);
            
            // Start video frame timeout checker
            startVideoFrameTimeoutChecker();
            
            hideLoading();
            isCallActive = true;
            Log.d(TAG, "Call initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Critical error initializing call", e);
            Toast.makeText(this, getString(R.string.error_failed_with_message, e.getMessage()), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeParticipants() {
        localParticipant = new CallParticipant();
        localParticipant.setUserId(currentUserId);
        localParticipant.setUsername(databaseManager.getUserName());
        localParticipant.setAvatar(databaseManager.getUserAvatar());
        localParticipant.setLocal(true);
        localParticipant.setAudioMuted(isMuted);
        localParticipant.setVideoMuted(!isCameraOn);

        remoteParticipant = new CallParticipant();
        remoteParticipant.setUserId(remoteUserId);
        remoteParticipant.setUsername(remoteUserName);
        remoteParticipant.setAvatar(remoteUserAvatar);
        remoteParticipant.setLocal(false);
    }

    private void setupSocketListeners() {
        socketManager.setVideoFrameListener((userId, base64Frame, timestamp) -> runOnUiThread(() -> {
            if (remoteParticipant == null || remoteParticipant.getUserId() == null) {
                return;
            }
            if (!remoteParticipant.getUserId().equals(userId)) {
                return;
            }
            lastRemoteFrameReceivedTime = System.currentTimeMillis();
            updateRemoteVideoFrame(base64Frame);
            if (remoteParticipant.isVideoMuted()) {
                remoteParticipant.setVideoMuted(false);
            }
        }));

        socketManager.setAudioFrameListener((userId, base64Audio, timestamp) -> {
            if (remoteUserId != null && remoteUserId.equals(userId)) {
                playRemoteAudio(userId, base64Audio);
            }
        });

        socketManager.on("call_room_joined", args -> Log.d(TAG, "Joined call room"));

        socketManager.on("user_left_call", args -> {
            try {
                if (args == null || args.length == 0) {
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String userId = data.optString("userId", "");
                if (remoteUserId != null && remoteUserId.equals(userId)) {
                    runOnUiThread(() -> {
                        if (isCallActive) {
                            endCall();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in user_left_call listener", e);
            }
        });

        socketManager.on("call_declined", args -> runOnUiThread(() -> {
            if (isCallActive) {
                Toast.makeText(this, getString(R.string.msg_call_declined), Toast.LENGTH_SHORT).show();
                endCall();
            }
        }));

        socketManager.on("call_ended", args -> runOnUiThread(() -> {
            if (isCallActive) {
                endCall();
            }
        }));
    }

    private void updateLocalVideoFrame(String base64Frame, boolean frontCamera) {
        if (!isCallActive || !isCameraOn) {
            return;
        }
        new Thread(() -> {
            android.graphics.Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
            if (bitmap == null) {
                return;
            }
            if (frontCamera) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postScale(-1, 1);
                android.graphics.Bitmap mirrored = android.graphics.Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (mirrored != bitmap) {
                    bitmap.recycle();
                }
                bitmap = mirrored;
            }
            android.graphics.Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (!isCallActive) {
                    return;
                }
                if (localVideoBitmap != null && !localVideoBitmap.isRecycled()) {
                    localVideoBitmap.recycle();
                }
                localVideoBitmap = finalBitmap;
                ivLocalVideoFrame.setImageBitmap(finalBitmap);
                ivLocalVideoFrame.setVisibility(View.VISIBLE);
                llLocalPlaceholder.setVisibility(View.GONE);
            });
        }).start();
    }

    private void updateRemoteVideoFrame(String base64Frame) {
        if (!isCallActive) {
            return;
        }
        new Thread(() -> {
            android.graphics.Bitmap bitmap = VideoFrameEncoder.decodeFrame(base64Frame);
            if (bitmap == null) {
                return;
            }
            runOnUiThread(() -> {
                if (!isCallActive) {
                    return;
                }
                if (remoteVideoBitmap != null && !remoteVideoBitmap.isRecycled()) {
                    remoteVideoBitmap.recycle();
                }
                remoteVideoBitmap = bitmap;
                ivRemoteVideoFrame.setImageBitmap(bitmap);
                ivRemoteVideoFrame.setVisibility(View.VISIBLE);
                llRemotePlaceholder.setVisibility(View.GONE);
            });
        }).start();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        com.example.chatappjava.utils.CallAccessibilityHelper.announceMuteToggle(
                getResources(), btnMute, isMuted);
        if (isMuted) {
            mediaPipeline.stopAudioCapture();
        } else {
            mediaPipeline.startAudioCapture();
        }
        if (localParticipant != null) {
            localParticipant.setAudioMuted(isMuted);
        }
        updateMediaState();
    }

    private void toggleCamera() {
        isCameraOn = !isCameraOn;
        btnCameraToggle.setImageResource(isCameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);
        com.example.chatappjava.utils.CallAccessibilityHelper.announceCameraToggle(
                getResources(), btnCameraToggle, isCameraOn);
        if (isCameraOn) {
            mediaPipeline.startVideoCapture(this);
        } else {
            mediaPipeline.stopVideoCapture();
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
        updateMediaState();
    }

    private void switchCamera() {
        if (mediaPipeline != null) {
            mediaPipeline.switchCamera();
        }
    }

    private void playRemoteAudio(String userId, String base64Audio) {
        if (mediaPipeline != null) {
            mediaPipeline.playRemoteAudio(userId, base64Audio);
        }
    }

    private void updateMediaState() {
        // Server media-state REST is optional for this client build.
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
        
        if (mediaPipeline != null) {
            mediaPipeline.release(false);
            mediaPipeline.resetAudioMode(this);
        }
        
        // Leave call room
        if (socketManager != null) {
            socketManager.leaveCallRoom(callId);
            socketManager.removeVideoFrameListener();
            socketManager.removeAudioFrameListener();
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
        
        if (mediaPipeline != null) {
            mediaPipeline.release(true);
        }

        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
            socketManager.removeAudioFrameListener();
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
        
        if (videoFrameTimeoutHandler != null && videoFrameTimeoutRunnable != null) {
            videoFrameTimeoutHandler.removeCallbacks(videoFrameTimeoutRunnable);
        }
    }
}
