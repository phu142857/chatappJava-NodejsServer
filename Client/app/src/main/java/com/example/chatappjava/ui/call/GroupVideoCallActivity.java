package com.example.chatappjava.ui.call;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.content.Context;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.view.Window;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.CustomVideoParticipantAdapter;
import com.example.chatappjava.models.CallParticipant;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.utils.CameraCaptureManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.VideoFrameEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Activity for custom group video calls (without WebRTC)
 * Continuously captures frames from the camera, encodes them and sends them to the server
 */
public class GroupVideoCallActivity extends AppCompatActivity {
    private static final String TAG = "GroupVideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    // CRITICAL: Reduced FPS to minimize encoding load and latency
    // 33ms = ~30 FPS - reduces encoding time significantly
    private static final int FRAME_CAPTURE_INTERVAL_MS = 10;
    
    // UI Components
    private RecyclerView rvVideoGrid;
    private LinearLayout emptyState;
    private LinearLayout loadingOverlay;
    private TextView tvLoadingMessage;
    private TextView tvGroupName;
    private TextView tvParticipantCount;
    private TextView tvCallDuration;
    private ImageButton btnMute;
    private ImageButton btnCameraToggle;
    private ImageButton btnSwitchCamera;
    private ImageButton btnEndCall;
    
    // Data
    private String callId;
    private String chatId;
    private String groupName;
    private String currentUserId;
    private List<CallParticipant> participants;
    private CustomVideoParticipantAdapter adapter;
    
    // Managers
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private SocketManager socketManager;
    private CameraCaptureManager cameraCaptureManager;
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isCallActive = false;
    private boolean isFrontCamera = false; // Track current camera facing
    private AtomicBoolean isSendingFrame = new AtomicBoolean(false);
    private Handler frameCaptureHandler;
    private Runnable frameCaptureRunnable;
    private long callStartTime;
    private Handler callDurationHandler;
    private Runnable callDurationRunnable;
    
    // CRITICAL: Track last frame received time for each participant
    // If no frames received for 2 seconds, assume camera is off
    private Map<String, Long> lastFrameReceivedTime;
    // CRITICAL: Increase timeout to prevent losing video when user is idle
    // User may be still but camera is still capturing, just not moving much
    private static final long VIDEO_FRAME_TIMEOUT_MS = 10000; // 10 seconds (increased from 2s)
    private Handler videoFrameTimeoutHandler;
    private Runnable videoFrameTimeoutRunnable;
    
    // CRITICAL: Thread pool for audio/video processing to avoid thread exhaustion
    private ExecutorService videoProcessingExecutor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_video_call);
        
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
        groupName = getIntent().getStringExtra("groupName");
        
        if (callId == null || chatId == null) {
            Toast.makeText(this, "Invalid call data", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initViews() {
        rvVideoGrid = findViewById(R.id.rv_video_grid);
        emptyState = findViewById(R.id.empty_state);
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvLoadingMessage = findViewById(R.id.tv_loading_message);
        tvGroupName = findViewById(R.id.tv_group_name);
        tvParticipantCount = findViewById(R.id.tv_participant_count);
        tvCallDuration = findViewById(R.id.tv_call_duration);
        btnMute = findViewById(R.id.btn_mute);
        btnCameraToggle = findViewById(R.id.btn_camera_toggle);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnEndCall = findViewById(R.id.btn_end_call);
        
        if (groupName != null) {
            tvGroupName.setText(groupName);
        }
        
        // Configure RecyclerView with GridLayoutManager
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        rvVideoGrid.setLayoutManager(layoutManager);
        
        participants = new ArrayList<>();
        adapter = new CustomVideoParticipantAdapter(this, participants);
        rvVideoGrid.setAdapter(adapter);
        
        // Initialize last frame received time tracking
        lastFrameReceivedTime = new HashMap<>();
        
        // Setup click listeners
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnCameraToggle.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnEndCall.setOnClickListener(v -> endCall());
        
        // CRITICAL FIX: Add click listener for participant count to show participants list
        tvParticipantCount.setOnClickListener(v -> showParticipantsListDialog());
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
            
            // CRITICAL: Setup listeners BEFORE joining room to avoid missing call_room_joined event
            setupSocketListeners();
            
            // Add local participant first
            addLocalParticipant();
            
            // Join call room via socket (will trigger call_room_joined with participants list)
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
            
            // Start call duration timer
            startCallDurationTimer();
            
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
                // Set mode to IN_COMMUNICATION for voice calls
                audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                
                // CRITICAL: Set speakerphone on for group calls (better for multiple participants)
                audioManager.setSpeakerphoneOn(true);
                
                // CRITICAL: Adjust stream volume to ensure audio is audible
                int maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL);
                int currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
                if (currentVolume < maxVolume * 0.7) {
                    // Set volume to at least 70% of max for better audio
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, 
                                                 (int)(maxVolume * 0.7), 0);
                    Log.d(TAG, "Adjusted voice call volume to " + (int)(maxVolume * 0.7) + " (max: " + maxVolume + ")");
                }
                
                Log.d(TAG, "Audio mode set to MODE_IN_COMMUNICATION, speakerphone: true, volume: " + currentVolume + "/" + maxVolume);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting audio mode", e);
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
    
    private void setupSocketListeners() {
        // Listener for received video frames
        socketManager.setVideoFrameListener(new SocketManager.VideoFrameListener() {
            @Override
            public void onVideoFrameReceived(String userId, String base64Frame, long timestamp) {
                Log.d(TAG, "Received video frame from user: " + userId);
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.updateVideoFrame(userId, base64Frame);
                        
                        // CRITICAL FIX: Track last frame received time
                        lastFrameReceivedTime.put(userId, System.currentTimeMillis());
                        
                        // CRITICAL FIX: If we receive video frames, the user's camera must be on
                        // Update participant's videoMuted state to false
                        for (CallParticipant p : participants) {
                            if (p.getUserId() != null && p.getUserId().equals(userId) && !p.isLocal()) {
                                // Only update for remote participants (not local)
                                if (p.isVideoMuted()) {
                                    Log.d(TAG, "Updating participant " + userId + " videoMuted state to false (received video frame)");
                                    p.setVideoMuted(false);
                                    // Find index and notify change
                                    int index = participants.indexOf(p);
                                    if (index >= 0) {
                                        adapter.notifyItemChanged(index);
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        Log.w(TAG, "Adapter is null, cannot update video frame");
                    }
                });
            }
        });
        
        // CRITICAL FIX: Start timeout checker to detect when participants stop sending frames
        startVideoFrameTimeoutChecker();
        
        // CRITICAL: Listener for call_room_joined - load existing participants list
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
                    // CRITICAL: Remove all old participants (except local participant) before loading new list
                    // This ensures only people actually in the call are displayed
                    List<CallParticipant> toRemove = new ArrayList<>();
                    for (CallParticipant p : participants) {
                        if (!p.isLocal() && p.getUserId() != null && !p.getUserId().equals(currentUserId)) {
                            toRemove.add(p);
                        }
                    }
                    for (CallParticipant p : toRemove) {
                        participants.remove(p);
                    }
                    
                    // Load all existing participants from server (only those who have joined room)
                    for (int i = 0; i < participantsArray.length(); i++) {
                        try {
                            JSONObject participantObj = participantsArray.getJSONObject(i);
                            String userId;

                            // Handle both ObjectId and populated object
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

                            // Skip local participant (already added in addLocalParticipant)
                            if (userId != null && userId.equals(currentUserId)) {
                                continue;
                            }

                            String username = participantObj.optString("username", "");
                            String avatar = participantObj.optString("avatar", "");
                            
                            // CRITICAL FIX: Check if participant has video muted state from server
                            // If not explicitly set, default to false (camera is on) if they're sending frames
                            boolean isVideoMuted = participantObj.optBoolean("isVideoMuted", false);
                            // Note: We'll update this automatically when receiving video frames

                            // Add participant (already filtered on server - only those who have joined)
                            CallParticipant participant = new CallParticipant();
                            participant.setUserId(userId);
                            participant.setUsername(username);
                            participant.setAvatar(avatar);
                            participant.setLocal(false);
                            participant.setVideoMuted(isVideoMuted);
                            
                            participants.add(participant);
                            adapter.notifyDataSetChanged();
                            updateParticipantCount();
                            
                            // CRITICAL FIX: If avatar is empty, fetch user profile to get avatar
                            // This matches how local participant gets avatar from databaseManager
                            if (avatar == null || avatar.isEmpty() || avatar.trim().isEmpty()) {
                                fetchUserAvatar(userId, participant);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing participant at index " + i, e);
                        }
                    }
                    
                    // Update adapter after loading is complete
                    adapter.notifyDataSetChanged();
                    updateParticipantCount();
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
        
        // Listener for participants who join
        socketManager.on("user_joined_call", args -> {
            try {
                if (args == null || args.length == 0) {
                    Log.w(TAG, "user_joined_call: empty args, ignoring");
                    return;
                }
                
                JSONObject data = (JSONObject) args[0];
                String userId = data.optString("userId", "");
                String username = data.optString("username", "");
                String avatar = data.optString("avatar", "");
                
                // CRITICAL: Validate userId before processing
                if (userId == null || userId.isEmpty()) {
                    Log.w(TAG, "user_joined_call: invalid userId, ignoring");
                    return;
                }
                
                runOnUiThread(() -> {
                    if (isCallActive) {
                        addParticipant(userId, username, avatar);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in user_joined_call listener", e);
                // Don't end call on unexpected error
            }
        });
        
        // Listener for participants who leave
        socketManager.on("user_left_call", args -> {
            try {
                if (args == null || args.length == 0) {
                    Log.w(TAG, "user_left_call: empty args, ignoring");
                    return;
                }
                
                JSONObject data = (JSONObject) args[0];
                String userId = data.optString("userId", "");
                
                // CRITICAL: Validate userId before processing
                if (userId == null || userId.isEmpty()) {
                    Log.w(TAG, "user_left_call: invalid userId, ignoring");
                    return;
                }
                
                runOnUiThread(() -> {
                    if (isCallActive) {
                        removeParticipant(userId);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in user_left_call listener", e);
                // Don't end call on unexpected error
            }
        });
    }
    
    // This method is no longer needed because participants are loaded from call_room_joined event
    // Kept for compatibility if other code calls it
    private void loadParticipants() {
        // Participants will be loaded from call_room_joined event
        // Only add local participant if not already present
        boolean hasLocal = false;
        for (CallParticipant p : participants) {
            if (p.isLocal() && p.getUserId() != null && p.getUserId().equals(currentUserId)) {
                hasLocal = true;
                break;
            }
        }
        if (!hasLocal) {
            addLocalParticipant();
        }
    }
    
    private void addLocalParticipant() {
        CallParticipant localParticipant = new CallParticipant();
        localParticipant.setUserId(currentUserId);
        localParticipant.setUsername(databaseManager.getUserName());
        localParticipant.setAvatar(databaseManager.getUserAvatar());
        localParticipant.setLocal(true);
        localParticipant.setAudioMuted(isMuted);
        localParticipant.setVideoMuted(!isCameraOn);
        
        participants.add(localParticipant);
        adapter.notifyDataSetChanged();
        updateParticipantCount();
    }
    
    private void addParticipant(String userId, String username, String avatar) {
        // Check if participant already exists
        for (CallParticipant p : participants) {
            if (p.getUserId() != null && p.getUserId().equals(userId)) {
                // Update avatar if it's empty and we have a new one
                if ((p.getAvatar() == null || p.getAvatar().isEmpty()) && avatar != null && !avatar.isEmpty()) {
                    p.setAvatar(avatar);
                    int index = participants.indexOf(p);
                    if (index >= 0) {
                        adapter.notifyItemChanged(index);
                    }
                }
                return; // Already present
            }
        }
        
        CallParticipant participant = new CallParticipant();
        participant.setUserId(userId);
        participant.setUsername(username);
        participant.setAvatar(avatar);
        participant.setLocal(false);
        // CRITICAL FIX: Default to video NOT muted (camera on) when adding participant
        // This will be updated automatically when we receive video frames or media state updates
        participant.setVideoMuted(false);
        
        participants.add(participant);
        adapter.notifyDataSetChanged();
        updateParticipantCount();
        
        // CRITICAL FIX: If avatar is empty, fetch user profile to get avatar
        // This matches how local participant gets avatar from databaseManager
        if (avatar == null || avatar.isEmpty() || avatar.trim().isEmpty()) {
            fetchUserAvatar(userId, participant);
        }
        
        if (emptyState.getVisibility() == View.VISIBLE) {
            emptyState.setVisibility(View.GONE);
        }
    }
    
    /**
     * Fetch user avatar from server if not available
     */
    private void fetchUserAvatar(String userId, CallParticipant participant) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        
        apiClient.authenticatedGet("/api/users/" + userId, token, new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                Log.e(TAG, "Failed to fetch user avatar for " + userId, e);
            }
            
            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.optBoolean("success", false)) {
                            JSONObject userData = jsonResponse.optJSONObject("data");
                            if (userData != null) {
                                String avatar = userData.optString("avatar", "");
                                runOnUiThread(() -> {
                                    // Update participant avatar
                                    participant.setAvatar(avatar);
                                    // Find and notify adapter
                                    int index = participants.indexOf(participant);
                                    if (index >= 0) {
                                        adapter.notifyItemChanged(index);
                                    }
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
    
    private void removeParticipant(String userId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId() != null && 
                participants.get(i).getUserId().equals(userId)) {
                participants.remove(i);
                adapter.notifyItemRemoved(i);
                updateParticipantCount();
                
                if (participants.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                }
                break;
            }
        }
    }
    
    private void updateParticipantCount() {
        int count = participants.size();
        tvParticipantCount.setText(count + " participant" + (count > 1 ? "s" : ""));
    }
    
    private void startVideoCapture() {
        if (!isCameraOn) {
            return;
        }
        
        try {
            // CRITICAL FIX: Reset isFrontCamera flag when starting capture
            // Default is back camera (false)
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
                            // Capture is continuous, send frames periodically
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
    
    private void sendVideoFrame(byte[] frameData) {
        // CRITICAL: Always send frames, even if previous send is in progress
        // This ensures frames are sent periodically even when user is idle
        // Use tryLock to allow skipping if encoding is slow to maintain frame rate
        if (!isSendingFrame.compareAndSet(false, true)) {
            // If previous frame is still being encoded, skip this one to maintain frame rate
            // This prevents queue buildup and ensures timely delivery
            return;
        }
        
        // CRITICAL: Use thread pool instead of creating new thread for each frame
        // This prevents thread exhaustion and process kill
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
                
                // Encode frame to base64
                String base64Frame = VideoFrameEncoder.encodeFrame(frameData);
                
                if (base64Frame != null && isCallActive) {
                    // CRITICAL: Update local participant video frame immediately
                    // This ensures the user sees their own video
                    // CRITICAL FIX: Pass isFrontCamera flag to mirror front camera correctly
                    final boolean frontCamera = isFrontCamera;
                    runOnUiThread(() -> {
                        if (isCallActive && adapter != null && currentUserId != null) {
                            adapter.updateVideoFrame(currentUserId, base64Frame, frontCamera);
                        }
                    });
                    
                    // Send to server to forward to other participants
                    if (socketManager != null && isCallActive) {
                        socketManager.sendVideoFrame(callId, base64Frame);
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
        
        // Update local participant
        for (CallParticipant p : participants) {
            if (p.isLocal()) {
                p.setAudioMuted(isMuted);
                adapter.notifyItemChanged(participants.indexOf(p));
                break;
            }
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
            // CRITICAL FIX: Clear local participant video frame when camera is turned off
            // This ensures avatar is shown instead of last frame
            if (adapter != null && currentUserId != null) {
                adapter.clearVideoFrameForUser(currentUserId);
            }
        }
        
        // Update local participant
        for (CallParticipant p : participants) {
            if (p.isLocal()) {
                p.setVideoMuted(!isCameraOn);
                adapter.notifyItemChanged(participants.indexOf(p));
                break;
            }
        }
        
        // Send update to server
        updateMediaState();
    }
    
    private void switchCamera() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.switchCamera();
            // CRITICAL FIX: Update isFrontCamera flag when switching camera
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
     * If a participant hasn't sent frames for VIDEO_FRAME_TIMEOUT_MS, assume camera is off
     */
    private void startVideoFrameTimeoutChecker() {
        videoFrameTimeoutHandler = new Handler(Looper.getMainLooper());
        videoFrameTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallActive) {
                    long currentTime = System.currentTimeMillis();
                    
                    // Check all remote participants
                    for (CallParticipant p : participants) {
                        if (!p.isLocal() && p.getUserId() != null) {
                            String userId = p.getUserId();
                            Long lastFrameTime = lastFrameReceivedTime.get(userId);
                            
                            // If we have received frames before but haven't received any recently
                            if (lastFrameTime != null) {
                                long timeSinceLastFrame = currentTime - lastFrameTime;
                                
                                // If timeout exceeded and participant is not marked as video muted
                                if (timeSinceLastFrame > VIDEO_FRAME_TIMEOUT_MS && !p.isVideoMuted()) {
                                    Log.d(TAG, "Participant " + userId + " stopped sending frames, marking video as muted");
                                    p.setVideoMuted(true);
                                    
                                    // Clear video frame from cache
                                    if (adapter != null) {
                                        adapter.clearVideoFrameForUser(userId);
                                    }
                                    
                                    // CRITICAL: Force notify adapter to reload avatar
                                    // Use notifyItemChanged with payload to force rebind
                                    int index = participants.indexOf(p);
                                    if (index >= 0) {
                                        adapter.notifyItemChanged(index, "video_muted");
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check again in 1 second (less frequent to reduce overhead)
                    videoFrameTimeoutHandler.postDelayed(this, 1000);
                }
            }
        };
        videoFrameTimeoutHandler.post(videoFrameTimeoutRunnable);
    }
    
    private void showParticipantsListDialog() {
        if (participants == null || participants.isEmpty()) {
            Toast.makeText(this, "No participants in call", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tagged_users, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        RecyclerView rvParticipants = dialogView.findViewById(R.id.rv_tagged_users);
        android.widget.Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        if (tvTitle != null) {
            tvTitle.setText("Participants (" + participants.size() + ")");
        }
        
        // Create adapter for participants list
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvParticipants.setLayoutManager(layoutManager);
        
        // Convert CallParticipant to simple display format
        List<ParticipantDisplayItem> displayItems = new ArrayList<>();
        for (CallParticipant p : participants) {
            ParticipantDisplayItem item = new ParticipantDisplayItem();
            item.userId = p.getUserId();
            item.username = p.getUsername();
            item.avatar = p.getAvatar();
            item.isAudioMuted = p.isAudioMuted();
            item.isVideoMuted = p.isVideoMuted();
            item.isLocal = p.isLocal();
            displayItems.add(item);
        }
        
        ParticipantsListAdapter adapter = new ParticipantsListAdapter(this, displayItems);
        rvParticipants.setAdapter(adapter);
        
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        
        dialog.show();
    }
    
    // Simple class to display participant info in dialog
    private static class ParticipantDisplayItem {
        String userId;
        String username;
        String avatar;
        boolean isAudioMuted;
        boolean isVideoMuted;
        boolean isLocal;
    }
    
    // Simple adapter for participants list in dialog
    private static class ParticipantsListAdapter extends RecyclerView.Adapter<ParticipantsListAdapter.ViewHolder> {
        private Context context;
        private List<ParticipantDisplayItem> items;
        
        public ParticipantsListAdapter(Context context, List<ParticipantDisplayItem> items) {
            this.context = context;
            this.items = items;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_group_member, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParticipantDisplayItem item = items.get(position);
            
            // Set name with status indicators
            String displayName = item.username;
            if (item.isLocal) {
                displayName = item.username + " (You)";
            }
            
            // Add mute/video status to name
            StringBuilder statusText = new StringBuilder();
            if (item.isAudioMuted) {
                statusText.append(" 🔇");
            }
            if (item.isVideoMuted) {
                statusText.append(" 📹");
            }
            
            holder.tvMemberName.setText(displayName + statusText.toString());
            
            // Hide role text (not needed for call participants)
            if (holder.tvMemberRole != null) {
                holder.tvMemberRole.setVisibility(View.GONE);
            }
            
            // Load avatar
            if (item.avatar != null && !item.avatar.isEmpty()) {
                String avatarUrl = item.avatar;
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + 
                               (avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl);
                }
                com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(holder.ivMemberAvatar);
            } else {
                holder.ivMemberAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView ivMemberAvatar;
            TextView tvMemberName;
            TextView tvMemberRole;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivMemberAvatar = itemView.findViewById(R.id.iv_member_avatar);
                tvMemberName = itemView.findViewById(R.id.tv_member_name);
                tvMemberRole = itemView.findViewById(R.id.tv_member_role);
            }
        }
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
        
        // Reset audio mode
        resetAudioMode();
        
        // Leave call room
        if (socketManager != null) {
            socketManager.leaveCallRoom(callId);
            socketManager.removeVideoFrameListener();
        }
        
        // Stop call duration timer
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
        
        // Call API to leave call
        String token = databaseManager.getToken();
        if (token != null && callId != null) {
            // Note: You may need to create this method in ApiClient
            // apiClient.leaveGroupCall(token, callId, ...);
        }
        
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
        
        // Stop audio capture and playback
        
        // Reset audio mode
        resetAudioMode();
        
        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
            socketManager.off("call_room_joined");
            socketManager.off("user_joined_call");
            socketManager.off("user_left_call");
        }
        
        if (adapter != null) {
            adapter.clearVideoFrames();
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
