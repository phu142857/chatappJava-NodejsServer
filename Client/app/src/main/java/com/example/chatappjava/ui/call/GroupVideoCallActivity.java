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
import com.example.chatappjava.utils.CallMediaPipeline;
import com.example.chatappjava.utils.DatabaseManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Activity for custom group video calls via Socket.IO media relay.
 * Continuously captures frames from the camera, encodes them and sends them to the server
 */
public class GroupVideoCallActivity extends AppCompatActivity {
    private static final String TAG = "GroupVideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    // CRITICAL: Reduced FPS to minimize encoding load and latency
    // 33ms = ~30 FPS - reduces encoding time significantly
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
    private CallMediaPipeline mediaPipeline;
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isCallActive = false;
    private boolean isFrontCamera = false; // Track current camera facing
    // CRITICAL: Track last frame received time for each participant
    // If no frames received for 2 seconds, assume camera is off
    private Map<String, Long> lastFrameReceivedTime;
    // CRITICAL: Increase timeout to prevent losing video when user is idle
    // User may be still but camera is still capturing, just not moving much
    private static final long VIDEO_FRAME_TIMEOUT_MS = 10000; // 10 seconds (increased from 2s)
    private Handler videoFrameTimeoutHandler;
    private Runnable videoFrameTimeoutRunnable;
    
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
            Toast.makeText(this, getString(R.string.error_invalid_call_data), Toast.LENGTH_SHORT).show();
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
        com.example.chatappjava.utils.CallAccessibilityHelper.bindInCallControls(
                btnMute, btnCameraToggle, btnSwitchCamera, btnEndCall, isMuted, isCameraOn);

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
                GroupVideoCallActivity.this.runOnUiThread(runnable);
            }

            @Override
            public void onLocalVideoFrame(String base64Frame, boolean frontCamera) {
                if (adapter != null && currentUserId != null) {
                    adapter.updateVideoFrame(currentUserId, base64Frame, frontCamera);
                }
            }

            @Override
            public void onCameraUnavailable() {
                Toast.makeText(GroupVideoCallActivity.this,
                        getString(R.string.msg_camera_unavailable_continuing_without_video),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAudioCaptureError(String message) {
                Toast.makeText(GroupVideoCallActivity.this,
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
            
            // CRITICAL: Setup listeners BEFORE joining room to avoid missing call_room_joined event
            setupSocketListeners();
            
            // Add local participant first
            addLocalParticipant();
            
            // Join call room via socket (will trigger call_room_joined with participants list)
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
            
            hideLoading();
            isCallActive = true;
            Log.d(TAG, "Call initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Critical error initializing call", e);
            Toast.makeText(this, getString(R.string.error_failed_with_message, e.getMessage()), Toast.LENGTH_LONG).show();
            finish();
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
        
        // Listener for received audio frames
        socketManager.setAudioFrameListener(new SocketManager.AudioFrameListener() {
            @Override
            public void onAudioFrameReceived(String userId, String base64Audio, long timestamp) {
                // Decode and play audio for this participant
                playRemoteAudio(userId, base64Audio);
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
                            
                            // CRITICAL FIX: If avatar is empty, fetch user profile to get avatar
                            // This matches how local participant gets avatar from databaseManager
                            if (avatar == null || avatar.isEmpty() || avatar.trim().isEmpty()) {
                                fetchUserAvatar(userId, participant);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing participant at index " + i, e);
                        }
                    }
                    
                    if (adapter != null && !participants.isEmpty()) {
                        adapter.notifyItemRangeChanged(0, participants.size());
                    }
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
    
    private void addLocalParticipant() {
        CallParticipant localParticipant = new CallParticipant();
        localParticipant.setUserId(currentUserId);
        localParticipant.setUsername(databaseManager.getUserName());
        localParticipant.setAvatar(databaseManager.getUserAvatar());
        localParticipant.setLocal(true);
        localParticipant.setAudioMuted(isMuted);
        localParticipant.setVideoMuted(!isCameraOn);
        
        participants.add(localParticipant);
        if (adapter != null) {
            adapter.notifyItemInserted(participants.size() - 1);
        }
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
        if (adapter != null) {
            adapter.notifyItemInserted(participants.size() - 1);
        }
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
        for (CallParticipant p : participants) {
            if (p.isLocal()) {
                p.setAudioMuted(isMuted);
                adapter.notifyItemChanged(participants.indexOf(p));
                break;
            }
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
            if (adapter != null && currentUserId != null) {
                adapter.clearVideoFrameForUser(currentUserId);
            }
        }
        for (CallParticipant p : participants) {
            if (p.isLocal()) {
                p.setVideoMuted(!isCameraOn);
                adapter.notifyItemChanged(participants.indexOf(p));
                break;
            }
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
            Toast.makeText(this, getString(R.string.msg_no_participants_in_call), Toast.LENGTH_SHORT).show();
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
            tvTitle.setText(getString(R.string.call_participants_title, participants.size()));
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
                statusText.append(" [muted]");
            }
            if (item.isVideoMuted) {
                statusText.append(" [no-vid]");
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

        if (mediaPipeline != null) {
            mediaPipeline.release(true);
        }

        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
            socketManager.removeAudioFrameListener();
            socketManager.off("call_room_joined");
            socketManager.off("user_joined_call");
            socketManager.off("user_left_call");
        }
        
        if (adapter != null) {
            adapter.clearVideoFrames();
        }

        if (videoFrameTimeoutHandler != null && videoFrameTimeoutRunnable != null) {
            videoFrameTimeoutHandler.removeCallbacks(videoFrameTimeoutRunnable);
        }
    }
}
