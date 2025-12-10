package com.example.chatappjava.ui.call;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final int FRAME_CAPTURE_INTERVAL_MS = 100; // 10 FPS
    
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
    private AtomicBoolean isSendingFrame = new AtomicBoolean(false);
    private Handler frameCaptureHandler;
    private Runnable frameCaptureRunnable;
    private long callStartTime;
    private Handler callDurationHandler;
    private Runnable callDurationRunnable;
    
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
        
        // Setup click listeners
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnCameraToggle.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnEndCall.setOnClickListener(v -> endCall());
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
        
        // CRITICAL: Setup listeners BEFORE joining room to avoid missing call_room_joined event
        setupSocketListeners();
        
        // Add local participant first
        addLocalParticipant();
        
        // Join call room via socket (will trigger call_room_joined with participants list)
        socketManager.joinCallRoom(callId);
        
        // Start video capture
        startVideoCapture();
        
        // Start call duration timer
        startCallDurationTimer();
        
        hideLoading();
        isCallActive = true;
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
                    } else {
                        Log.w(TAG, "Adapter is null, cannot update video frame");
                    }
                });
            }
        });
        
        // CRITICAL: Listener for call_room_joined - load existing participants list
        socketManager.on("call_room_joined", args -> {
            JSONObject data = (JSONObject) args[0];
            JSONArray participantsArray = data.optJSONArray("participants");

            if (participantsArray != null) {
                Log.d(TAG, "Received call_room_joined with " + participantsArray.length() + " participants");
                runOnUiThread(() -> {
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

                            // Add participant (already filtered on server - only those who have joined)
                            addParticipant(userId, username, avatar);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing participant at index " + i, e);
                        }
                    }
                    
                    // Update adapter after loading is complete
                    adapter.notifyDataSetChanged();
                    updateParticipantCount();
                });
            }
        });
        
        // Listener for participants who join
        socketManager.on("user_joined_call", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");
                String username = data.getString("username");
                String avatar = data.optString("avatar", "");
                
                runOnUiThread(() -> {
                    addParticipant(userId, username, avatar);
                });
            } catch (JSONException e) {
                Log.e(TAG, "Error receiving user_joined_call", e);
            }
        });
        
        // Listener for participants who leave
        socketManager.on("user_left_call", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");
                
                runOnUiThread(() -> {
                    removeParticipant(userId);
                });
            } catch (JSONException e) {
                Log.e(TAG, "Error receiving user_left_call", e);
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
                return; // Already present
            }
        }
        
        CallParticipant participant = new CallParticipant();
        participant.setUserId(userId);
        participant.setUsername(username);
        participant.setAvatar(avatar);
        participant.setLocal(false);
        
        participants.add(participant);
        adapter.notifyDataSetChanged();
        updateParticipantCount();
        
        if (emptyState.getVisibility() == View.VISIBLE) {
            emptyState.setVisibility(View.GONE);
        }
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
                    // Capture is continuous, send frames periodically
                    frameCaptureHandler.postDelayed(this, FRAME_CAPTURE_INTERVAL_MS);
                }
            }
        };
        frameCaptureHandler.post(frameCaptureRunnable);
    }
    
    private void sendVideoFrame(byte[] frameData) {
        // Avoid sending multiple frames at the same time
        if (!isSendingFrame.compareAndSet(false, true)) {
            return;
        }
        
        new Thread(() -> {
            try {
                // Encode frame to base64
                String base64Frame = VideoFrameEncoder.encodeFrame(frameData);
                
                if (base64Frame != null) {
                    // CRITICAL: Update local participant video frame immediately
                    // This ensures the user sees their own video
                    runOnUiThread(() -> {
                        if (adapter != null && currentUserId != null) {
                            adapter.updateVideoFrame(currentUserId, base64Frame);
                        }
                    });
                    
                    // Send to server to forward to other participants
                    if (socketManager != null) {
                        Log.d(TAG, "Sending video frame to server for callId: " + callId);
                        socketManager.sendVideoFrame(callId, base64Frame);
                    } else {
                        Log.w(TAG, "SocketManager is null, cannot send video frame");
                    }
                } else {
                    Log.w(TAG, "Failed to encode video frame");
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
        
        // Clean up resources
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        
        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
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
    }
}
