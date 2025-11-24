package com.example.chatappjava.ui.call;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.VideoParticipantAdapter;
import com.example.chatappjava.models.CallParticipant;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.utils.DatabaseManager;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class GroupVideoCallActivity extends AppCompatActivity {

    private static final String TAG = "GroupVideoCall";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_MICROPHONE_PERMISSION = 1002;

    // UI Components
    private TextView tvGroupName;
    private TextView tvParticipantCount;
    private TextView tvCallDuration;
    private RecyclerView rvVideoGrid;
    private LinearLayout emptyState;
    private LinearLayout loadingOverlay;
    private TextView tvLoadingMessage;
    private ImageButton btnMute;
    private ImageButton btnCameraToggle;
    private ImageButton btnSwitchCamera;
    private ImageButton btnParticipants;
    private ImageButton btnEndCall;

    // Data
    private String chatId;
    private String callId;
    private String groupName;
    private String currentUserId;
    private boolean isCaller = false;
    private boolean isMuted = false;
    private boolean isCameraOn = true; // Video ON by default (like private call)
    private boolean isFrontCamera = true;

    // WebRTC components
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase eglBase;
    private Map<String, PeerConnection> peerConnections = new HashMap<>();
    private Map<String, SurfaceViewRenderer> remoteVideoViews = new HashMap<>();
    // Map to queue ICE candidates per participant (in case they arrive before remote description is set)
    private Map<String, List<IceCandidate>> pendingIceCandidates = new HashMap<>();
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private MediaStream localStream;
    private List<PeerConnection.IceServer> iceServers = new ArrayList<>();

    // Participants
    private VideoParticipantAdapter participantAdapter;
    private List<CallParticipant> participants = new ArrayList<>();
    private final java.util.Set<String> addedParticipantIds = new java.util.HashSet<>(); // Track added participant IDs to prevent duplicates

    // Network
    private SocketManager socketManager;
    private DatabaseManager databaseManager;
    private ApiClient apiClient;

    // Call duration timer
    private Timer callTimer;
    private int callDuration = 0;
    
    // Cleanup flag to prevent operations during cleanup
    private volatile boolean isCleaningUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_video_call);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get intent data
        chatId = getIntent().getStringExtra("chatId");
        callId = getIntent().getStringExtra("callId");
        groupName = getIntent().getStringExtra("groupName");
        isCaller = getIntent().getBooleanExtra("isCaller", false);
        
        // CRITICAL: Log the received values to debug join flow
        Log.d(TAG, "GroupVideoCallActivity onCreate - callId: " + callId + ", isCaller: " + isCaller + ", chatId: " + chatId);

        if (chatId == null) {
            Toast.makeText(this, "Invalid group call data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize managers
        socketManager = SocketManager.getInstance();
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        currentUserId = databaseManager.getUserId();
        Log.d(TAG, "Current user ID: " + currentUserId);
        
        // Initialize tracking set with local user ID to prevent adding self
        synchronized (addedParticipantIds) {
            addedParticipantIds.clear();
            if (currentUserId != null) {
                addedParticipantIds.add(currentUserId);
            }
        }

        // Initialize EGL base FIRST (needed for adapter)
        eglBase = EglBase.create();
        Log.d(TAG, "EGL base created early for adapter");

        // Initialize UI (adapter needs eglBase)
        initializeUI();

        // Request permissions
        if (checkPermissions()) {
            initializeGroupCall();
        } else {
            requestPermissions();
        }
    }

    private void initializeUI() {
        tvGroupName = findViewById(R.id.tv_group_name);
        tvParticipantCount = findViewById(R.id.tv_participant_count);
        tvCallDuration = findViewById(R.id.tv_call_duration);
        rvVideoGrid = findViewById(R.id.rv_video_grid);
        emptyState = findViewById(R.id.empty_state);
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvLoadingMessage = findViewById(R.id.tv_loading_message);
        btnMute = findViewById(R.id.btn_mute);
        btnCameraToggle = findViewById(R.id.btn_camera_toggle);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnParticipants = findViewById(R.id.btn_participants);
        btnEndCall = findViewById(R.id.btn_end_call);

        // Set group name
        tvGroupName.setText(groupName != null ? groupName : "Group Call");

        // Setup grid layout (2 columns for up to 4 participants, 3 for more)
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        rvVideoGrid.setLayoutManager(gridLayoutManager);

        // Setup adapter
        participantAdapter = new VideoParticipantAdapter(this, participants, eglBase);
        Log.d(TAG, "Adapter created with eglBase: " + (eglBase != null ? "exists" : "NULL"));
        rvVideoGrid.setAdapter(participantAdapter);
        
        // Ensure adapter has eglBase reference
        if (eglBase != null && participantAdapter != null) {
            participantAdapter.setEglBase(eglBase);
        }

        // Update grid columns based on participant count
        participantAdapter.setOnParticipantCountChangeListener(count -> {
            int columns = count <= 2 ? 1 : (count <= 4 ? 2 : 3);
            gridLayoutManager.setSpanCount(columns);
        });

        // Button listeners
        btnMute.setOnClickListener(v -> toggleMute());
        btnCameraToggle.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnParticipants.setOnClickListener(v -> showParticipantsList());
        btnEndCall.setOnClickListener(v -> leaveCall());

        // Initial button states
        updateMuteButton();
        updateCameraButton();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeGroupCall();
            } else {
                Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initializeGroupCall() {
        showLoading("Initializing call...");

        // Initialize WebRTC
        initializeWebRTC();

        // CRITICAL FIX: Always check for active call first (like private calls do)
        // This ensures only one call exists per group, even if multiple users click call button simultaneously
        Log.d(TAG, "initializeGroupCall - callId: " + callId + ", isCaller: " + isCaller + ", chatId: " + chatId);
        
        if (callId != null && !callId.isEmpty()) {
            // We have a callId, join the existing call
            Log.d(TAG, "callId provided, joining call: " + callId);
            joinGroupCall();
        } else {
            // No callId provided - ALWAYS check for active call first
            // This handles both cases:
            // 1. isCaller=true: Check if someone else already started a call
            // 2. isCaller=false: Check if there's an active call to join
            Log.d(TAG, "No callId provided, checking for active call first");
            checkForActiveCall();
        }

        // Setup socket listeners
        setupSocketListeners();

        // Start call duration timer
        startCallTimer();
    }

    private void initializeWebRTC() {
        Log.d(TAG, "Initializing WebRTC");

        // Initialize EGL base (if not already created)
        if (eglBase == null) {
            eglBase = EglBase.create();
            Log.d(TAG, "EGL base created in initializeWebRTC");
        } else {
            Log.d(TAG, "EGL base already exists");
        }
        
        // Update adapter with eglBase if it was just created
        if (participantAdapter != null && eglBase != null) {
            participantAdapter.setEglBase(eglBase);
            Log.d(TAG, "Updated adapter with eglBase reference");
        }

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .setFieldTrials("")
                        .createInitializationOptions()
        );

        // Create PeerConnectionFactory
        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        // Create local audio and video tracks
        createLocalMediaTracks();
        
        // Start local video automatically (like private call)
        if ("video".equals(getIntent().getStringExtra("callType"))) {
            startLocalVideo();
        }

        Log.d(TAG, "WebRTC initialized successfully");
    }

    private void createLocalMediaTracks() {
        // Create audio track
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio", audioSource);
        localAudioTrack.setEnabled(!isMuted);

        // Create video capturer and source (camera will be started in startLocalVideo)
        if ("video".equals(getIntent().getStringExtra("callType"))) {
            CameraEnumerator enumerator = new Camera2Enumerator(this);
            String[] deviceNames = enumerator.getDeviceNames();

            for (String deviceName : deviceNames) {
                if (isFrontCamera && enumerator.isFrontFacing(deviceName)) {
                    videoCapturer = enumerator.createCapturer(deviceName, new CameraVideoCapturer.CameraEventsHandler() {
                        @Override
                        public void onCameraError(String errorDescription) {
                            Log.e(TAG, "Camera error: " + errorDescription);
                        }

                        @Override
                        public void onCameraDisconnected() {
                            Log.d(TAG, "Camera disconnected");
                        }

                        @Override
                        public void onCameraFreezed(String errorDescription) {
                            Log.e(TAG, "Camera frozen: " + errorDescription);
                        }

                        @Override
                        public void onCameraOpening(String cameraName) {
                            Log.d(TAG, "Camera opening: " + cameraName);
                        }

                        @Override
                        public void onFirstFrameAvailable() {
                            Log.d(TAG, "First frame available");
                            runOnUiThread(() -> {
                                // Update adapter when first frame is available
                                if (!participants.isEmpty()) {
                                    participantAdapter.notifyItemChanged(0);
                                }
                            });
                        }

                        @Override
                        public void onCameraClosed() {
                            Log.d(TAG, "Camera closed");
                        }
                    });
                    break;
                } else if (!isFrontCamera && enumerator.isBackFacing(deviceName)) {
                    videoCapturer = enumerator.createCapturer(deviceName, new CameraVideoCapturer.CameraEventsHandler() {
                        @Override
                        public void onCameraError(String errorDescription) {
                            Log.e(TAG, "Camera error: " + errorDescription);
                        }

                        @Override
                        public void onCameraDisconnected() {
                            Log.d(TAG, "Camera disconnected");
                        }

                        @Override
                        public void onCameraFreezed(String errorDescription) {
                            Log.e(TAG, "Camera frozen: " + errorDescription);
                        }

                        @Override
                        public void onCameraOpening(String cameraName) {
                            Log.d(TAG, "Camera opening: " + cameraName);
                        }

                        @Override
                        public void onFirstFrameAvailable() {
                            Log.d(TAG, "First frame available");
                            runOnUiThread(() -> {
                                if (!participants.isEmpty()) {
                                    participantAdapter.notifyItemChanged(0);
                                }
                            });
                        }

                        @Override
                        public void onCameraClosed() {
                            Log.d(TAG, "Camera closed");
                        }
                    });
                    break;
                }
            }

            if (videoCapturer != null) {
                videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
                localVideoTrack = peerConnectionFactory.createVideoTrack("local_video", videoSource);
                localVideoTrack.setEnabled(isCameraOn); // ON by default
            }
        }

        // Create local media stream
        localStream = peerConnectionFactory.createLocalMediaStream("local_stream");
        localStream.addTrack(localAudioTrack);
        if (localVideoTrack != null) {
            localStream.addTrack(localVideoTrack);
        }

        // Add local user as first participant
        addLocalParticipant();
    }
    
    private void startLocalVideo() {
        Log.d(TAG, "Starting local video capture (like private call)");
        
        // Don't start if we're cleaning up
        if (isCleaningUp) {
            Log.w(TAG, "Cannot start camera - cleanup in progress");
            return;
        }
        
        if (videoCapturer == null || videoSource == null || localVideoTrack == null) {
            Log.w(TAG, "Video components not ready, cannot start camera");
            return;
        }
        
        try {
            // Initialize and start camera (exactly like private call does)
            videoCapturer.initialize(
                    org.webrtc.SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext()),
                    this,
                    videoSource.getCapturerObserver()
            );
            videoCapturer.startCapture(1280, 720, 30);
            
            // Update UI
            if (localVideoTrack != null) {
                localVideoTrack.setEnabled(true);
                Log.d(TAG, "Local video track enabled: " + localVideoTrack.enabled());
            }
            isCameraOn = true;
            updateLocalParticipantVideoState();
            updateCameraButton();
            sendMediaUpdate();
            participantAdapter.notifyItemChanged(0);
            participantAdapter.notifyDataSetChanged();
            Log.d(TAG, "Local video started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera", e);
            Toast.makeText(this, "Failed to start camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isCameraOn = false;
            updateCameraButton();
        }
    }

    private void addLocalParticipant() {
        // CRITICAL: Check if local participant already exists to avoid duplicates
        for (CallParticipant p : participants) {
            if (p.isLocal() && p.getUserId().equals(currentUserId)) {
                Log.d(TAG, "Local participant already exists, skipping add");
                return;
            }
        }
        
        CallParticipant localParticipant = new CallParticipant();
        localParticipant.setUserId(currentUserId);
        localParticipant.setUsername("You");
        localParticipant.setAudioMuted(isMuted);
        // Sync video muted state with actual track state
        boolean actuallyMuted = localVideoTrack == null || !localVideoTrack.enabled() || !isCameraOn;
        localParticipant.setVideoMuted(actuallyMuted);
        localParticipant.setLocal(true);
        // Always set the video track reference
        localParticipant.setVideoTrack(localVideoTrack);

        // CRITICAL FIX: All list modifications must happen on UI thread
        runOnUiThread(() -> {
            synchronized (addedParticipantIds) {
                // Add local participant ID to tracking set
                addedParticipantIds.add(currentUserId);
                participants.add(0, localParticipant);
            }
            Log.d(TAG, "Adding local participant - videoTrack: " + (localVideoTrack != null ? "exists" : "null") + 
                  ", enabled: " + (localVideoTrack != null && localVideoTrack.enabled()) + 
                  ", isCameraOn: " + isCameraOn +
                  ", videoMuted: " + localParticipant.isVideoMuted());
            participantAdapter.notifyDataSetChanged();
            updateParticipantCount();
            emptyState.setVisibility(View.GONE);
        });
    }
    
    private void updateLocalParticipantVideoState() {
        if (!participants.isEmpty()) {
            CallParticipant localParticipant = participants.get(0);
            boolean actuallyMuted = localVideoTrack == null || !localVideoTrack.enabled() || !isCameraOn;
            localParticipant.setVideoMuted(actuallyMuted);
            localParticipant.setVideoTrack(localVideoTrack);
            Log.d(TAG, "Updated local participant video state - enabled: " + (localVideoTrack != null && localVideoTrack.enabled()) + 
                  ", isCameraOn: " + isCameraOn + ", muted: " + actuallyMuted);
        }
    }

    private void startGroupCall() {
        Log.d(TAG, "Starting group call");
        showLoading("Starting group call...");

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("chatId", chatId);
            requestBody.put("type", "video");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String token = databaseManager.getToken();
        apiClient.authenticatedPost("/api/group-calls", token, requestBody, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(GroupVideoCallActivity.this, "Failed to start call", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        String serverCallId = json.getString("callId");
                        
                        // CRITICAL: Always use the callId from server response (never use a local one)
                        if (serverCallId != null && !serverCallId.isEmpty()) {
                            callId = serverCallId;
                            Log.d(TAG, "Using callId from server: " + callId);
                        } else {
                            Log.e(TAG, "Server returned null or empty callId!");
                            runOnUiThread(() -> {
                                hideLoading();
                                Toast.makeText(GroupVideoCallActivity.this, "Invalid call ID from server", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                            return;
                        }
                        
                        JSONObject callData = json.getJSONObject("data");
                        
                        // CRITICAL FIX: Check if this is an existing call (someone else started it first)
                        boolean isExisting = json.optBoolean("isExisting", false);
                        
                        Log.d(TAG, "Server response - callId: " + callId + ", isExisting: " + isExisting + ", chatId: " + chatId);
                        
                        if (isExisting) {
                            Log.d(TAG, "Found existing call, joining instead of creating new one - callId: " + callId);
                            // Update isCaller flag since we're joining, not creating
                            isCaller = false;
                            
                            // CRITICAL FIX: Don't add participants from API response here
                            // They will be added when socket room_state event is received
                            // This prevents duplicates and race conditions
                            if (callData.has("participants")) {
                                JSONArray participantsArray = callData.getJSONArray("participants");
                                Log.d(TAG, "Found " + participantsArray.length() + " participants in existing call (will be added via socket room_state)");
                                // Participants will be added when joinCallRoom() receives room_state event
                            }
                            
                            // Extract ICE servers
                            if (callData.has("webrtcData")) {
                                extractIceServers(callData);
                            }
                            
                            runOnUiThread(() -> {
                                hideLoading();
                                Log.d(TAG, "Joined existing group call - callId: " + callId);
                                Toast.makeText(GroupVideoCallActivity.this, "Joined group call", Toast.LENGTH_SHORT).show();
                                
                                // CRITICAL: Ensure local participant is added
                                if (participants.isEmpty()) {
                                    Log.w(TAG, "No local participant found, adding now");
                                    addLocalParticipant();
                                } else {
                                    Log.d(TAG, "Local participant already exists");
                                }
                                
                                // Join socket room for real-time updates
                                joinCallRoom();
                            });
                        } else {
                            // New call created
                            // Extract ICE servers
                            extractIceServers(callData);

                            runOnUiThread(() -> {
                                hideLoading();
                                Log.d(TAG, "Group call started successfully - callId: " + callId);
                                Toast.makeText(GroupVideoCallActivity.this, "Group call started", Toast.LENGTH_SHORT).show();
                                
                                // CRITICAL: Ensure local participant is added (might already be added in initializeWebRTC)
                                if (participants.isEmpty()) {
                                    Log.w(TAG, "No local participant found, adding now");
                                    addLocalParticipant();
                                } else {
                                    Log.d(TAG, "Local participant already exists");
                                }
                                
                                // Join socket room for real-time updates
                                joinCallRoom();
                            });
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(GroupVideoCallActivity.this, "Failed to parse response", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(GroupVideoCallActivity.this, "Failed to start call", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }
        });
    }

    private void joinGroupCall() {
        Log.d(TAG, "Joining group call: " + callId);
        
        // Validate callId before attempting to join
        if (callId == null || callId.isEmpty()) {
            Log.e(TAG, "Cannot join call: callId is null or empty");
            Toast.makeText(this, "Invalid call ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        showLoading("Joining call...");

        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Cannot join call: token is null or empty");
            runOnUiThread(() -> {
                hideLoading();
                Toast.makeText(GroupVideoCallActivity.this, "Authentication required", Toast.LENGTH_SHORT).show();
                finish();
            });
            return;
        }
        
        Log.d(TAG, "Making API call to join group call with callId: " + callId);
        apiClient.authenticatedPost("/api/group-calls/" + callId + "/join", token, new JSONObject(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to join call - Network error", e);
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(GroupVideoCallActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Don't finish immediately - give user option to retry
                    new android.os.Handler().postDelayed(() -> finish(), 3000);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "Join call response code: " + response.code());
                Log.d(TAG, "Join call response body: " + responseBody);
                
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        
                        if (!json.optBoolean("success", false)) {
                            String errorMsg = json.optString("message", "Unknown error");
                            Log.e(TAG, "Join call failed: " + errorMsg);
                            runOnUiThread(() -> {
                                hideLoading();
                                Toast.makeText(GroupVideoCallActivity.this, "Failed to join: " + errorMsg, Toast.LENGTH_LONG).show();
                                new android.os.Handler().postDelayed(() -> finish(), 3000);
                            });
                            return;
                        }
                        
                        JSONObject data = json.getJSONObject("data");
                        
                        // Update callId if it changed
                        String returnedCallId = data.optString("callId", callId);
                        if (!returnedCallId.equals(callId)) {
                            Log.d(TAG, "CallId updated from " + callId + " to " + returnedCallId);
                            callId = returnedCallId;
                        }
                        
                        // Extract call details
                        JSONObject callData = data.optJSONObject("call");
                        if (callData != null) {
                            // Extract ICE servers if available
                            if (callData.has("webrtcData")) {
                                extractIceServers(callData);
                            }

                            // Extract participants
                            // CRITICAL FIX: Don't add participants from API response here
                            // They will be added when socket room_state event is received
                            // This prevents duplicates and race conditions
                            if (callData.has("participants")) {
                                JSONArray participantsArray = callData.getJSONArray("participants");
                                Log.d(TAG, "Found " + participantsArray.length() + " participants in call (will be added via socket room_state)");
                                // Participants will be added when joinCallRoom() receives room_state event
                            }
                        }

                        runOnUiThread(() -> {
                            hideLoading();
                            Log.d(TAG, "Successfully joined call: " + callId);
                            Toast.makeText(GroupVideoCallActivity.this, "Joined call successfully", Toast.LENGTH_SHORT).show();
                            
                            // Join socket room for real-time updates
                            joinCallRoom();
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse join call response", e);
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(GroupVideoCallActivity.this, "Failed to parse response: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            new android.os.Handler().postDelayed(() -> finish(), 3000);
                        });
                    }
                } else {
                    Log.e(TAG, "Join call HTTP error: " + response.code() + " - " + responseBody);
                    String errorMessage = "Failed to join call (HTTP " + response.code() + ")";
                    
                    // Try to extract error message from response
                    try {
                        JSONObject errorJson = new JSONObject(responseBody);
                        String serverMessage = errorJson.optString("message", "");
                        if (!serverMessage.isEmpty()) {
                            errorMessage = serverMessage;
                        }
                    } catch (JSONException ignored) {}
                    
                    String finalErrorMessage = errorMessage;
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(GroupVideoCallActivity.this, finalErrorMessage, Toast.LENGTH_LONG).show();
                        new android.os.Handler().postDelayed(() -> finish(), 3000);
                    });
                }
            }
        });
    }

    private void checkForActiveCall() {
        Log.d(TAG, "Checking for active call in chat: " + chatId + ", isCaller: " + isCaller);
        String token = databaseManager.getToken();
        apiClient.authenticatedGet("/api/group-calls/chat/" + chatId + "/active", token, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to check for active call", e);
                // If check fails and we're the caller, start new call
                // Otherwise, show error and finish
                if (isCaller) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Check failed, starting new call as caller");
                        startGroupCall();
                    });
                } else {
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(GroupVideoCallActivity.this, "Failed to check for active call", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        
                        // Check if there's an active call
                        if (json.has("data") && !json.isNull("data")) {
                            JSONObject callData = json.getJSONObject("data");
                            String existingCallId = callData.getString("callId");
                            String status = callData.optString("status", "");
                            
                            // If call is active, notified, or ringing, join it
                            if ("active".equals(status) || "notified".equals(status) || "ringing".equals(status)) {
                                Log.d(TAG, "Found active call: " + existingCallId + ", joining instead of creating new one");
                                callId = existingCallId;
                                isCaller = false; // We're joining, not creating
                                runOnUiThread(() -> joinGroupCall());
                                return;
                            }
                        }
                        
                        // No active call found - if we're the caller, start new call
                        // Otherwise, show error (shouldn't happen if called correctly)
                        if (isCaller) {
                            Log.d(TAG, "No active call found, starting new call as caller");
                            runOnUiThread(() -> startGroupCall());
                        } else {
                            Log.w(TAG, "No active call found but isCaller=false, finishing");
                            runOnUiThread(() -> {
                                hideLoading();
                                Toast.makeText(GroupVideoCallActivity.this, "No active call found", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing active call response", e);
                        e.printStackTrace();
                        // If we're the caller and parsing fails, try to start new call
                        if (isCaller) {
                            runOnUiThread(() -> startGroupCall());
                        } else {
                            runOnUiThread(() -> {
                                hideLoading();
                                Toast.makeText(GroupVideoCallActivity.this, "Error checking for active call", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    }
                } else {
                    // HTTP error - if we're the caller, try to start new call
                    Log.e(TAG, "HTTP error checking for active call: " + response.code());
                    if (isCaller) {
                        runOnUiThread(() -> startGroupCall());
                    } else {
                        runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(GroupVideoCallActivity.this, "Failed to check for active call", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }
            }
        });
    }

    private void joinCallRoom() {
        if (callId == null || callId.isEmpty()) {
            Log.e(TAG, "Cannot join call room: callId is null or empty");
            return;
        }
        
        if (socketManager == null) {
            Log.e(TAG, "Cannot join call room: socketManager is null");
            Toast.makeText(this, "Socket connection not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // CRITICAL: Verify callId is set before joining room
        if (callId == null || callId.isEmpty()) {
            Log.e(TAG, "Cannot join call room: callId is null or empty!");
            Toast.makeText(this, "Invalid call ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // CRITICAL: Log the callId being used to join room (for debugging)
        Log.d(TAG, "Joining call room via socket - callId: " + callId + ", chatId: " + chatId);
        socketManager.joinGroupCallRoom(callId);
    }

    private void extractIceServers(JSONObject callData) {
        try {
            JSONObject webrtcData = callData.getJSONObject("webrtcData");
            JSONArray iceServersArray = webrtcData.getJSONArray("iceServers");

            iceServers.clear();
            for (int i = 0; i < iceServersArray.length(); i++) {
                JSONObject iceServer = iceServersArray.getJSONObject(i);
                String urls = iceServer.getString("urls");

                PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);

                if (iceServer.has("username") && !iceServer.isNull("username")) {
                    builder.setUsername(iceServer.getString("username"));
                }
                if (iceServer.has("credential") && !iceServer.isNull("credential")) {
                    builder.setPassword(iceServer.getString("credential"));
                }

                iceServers.add(builder.createIceServer());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            // Add default STUN server
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        }
    }

    private void addRemoteParticipant(JSONObject participantJson) {
        try {
            String userId = participantJson.getString("userId");
            String username = participantJson.getString("username");
            String status = participantJson.getString("status");

            // CRITICAL FIX: Never add local user as remote participant
            // Use equalsIgnoreCase and trim to handle any format differences
            if (userId == null || userId.isEmpty()) {
                Log.w(TAG, "Attempted to add participant with null or empty userId");
                return;
            }
            
            if (currentUserId != null && userId.trim().equalsIgnoreCase(currentUserId.trim())) {
                Log.w(TAG, "Attempted to add local user as remote participant, ignoring: " + userId + " (currentUserId: " + currentUserId + ")");
                return;
            }
            
            // CRITICAL FIX: Also check if this userId is already in the tracking set (might be local user with different format)
            synchronized (addedParticipantIds) {
                for (String addedId : addedParticipantIds) {
                    if (addedId != null && addedId.trim().equalsIgnoreCase(userId.trim())) {
                        Log.w(TAG, "Attempted to add participant that matches existing ID (likely local user), ignoring: " + userId);
                        return;
                    }
                }
            }

            // CRITICAL FIX: Thread-safe duplicate check using synchronized set
            synchronized (addedParticipantIds) {
                if (addedParticipantIds.contains(userId)) {
                    Log.d(TAG, "Participant " + userId + " already in added set, updating instead of adding duplicate");
                    // Update existing participant info on UI thread
                    runOnUiThread(() -> {
                        for (CallParticipant p : participants) {
                            if (p.getUserId().equals(userId)) {
                                p.setUsername(username);
                                p.setAudioMuted(participantJson.optBoolean("audioMuted", false));
                                p.setVideoMuted(participantJson.optBoolean("videoMuted", true));
                                participantAdapter.notifyDataSetChanged();
                                updateParticipantCount();
                                break;
                            }
                        }
                    });
                    // If peer connection doesn't exist, create it
                    if (!peerConnections.containsKey(userId)) {
                        createPeerConnection(userId);
                    }
                    return;
                }
            }

            if ("connected".equals(status)) {
                CallParticipant participant = new CallParticipant();
                participant.setUserId(userId);
                participant.setUsername(username);
                participant.setAudioMuted(false);
                participant.setVideoMuted(true);
                participant.setLocal(false);

                // CRITICAL FIX: All list modifications must happen on UI thread with atomic add
                runOnUiThread(() -> {
                    synchronized (addedParticipantIds) {
                        // Double-check after moving to UI thread
                        if (addedParticipantIds.contains(userId)) {
                            Log.w(TAG, "Participant " + userId + " was added while waiting for UI thread, skipping");
                            return;
                        }
                        addedParticipantIds.add(userId);
                        participants.add(participant);
                        
                        // CRITICAL FIX: Safely notify adapter - check if adapter and RecyclerView are ready
                        if (participantAdapter != null && rvVideoGrid != null) {
                            try {
                                participantAdapter.notifyDataSetChanged();
                                updateParticipantCount();
                                Log.d(TAG, "Added remote participant: " + userId + " (" + username + ")");
                            } catch (Exception e) {
                                Log.e(TAG, "Error notifying adapter: " + e.getMessage(), e);
                                // Fallback: try again after a short delay
                                new android.os.Handler().postDelayed(() -> {
                                    if (participantAdapter != null) {
                                        try {
                                            participantAdapter.notifyDataSetChanged();
                                            updateParticipantCount();
                                        } catch (Exception e2) {
                                            Log.e(TAG, "Error in delayed adapter notification: " + e2.getMessage());
                                        }
                                    }
                                }, 100);
                            }
                        } else {
                            Log.w(TAG, "Adapter or RecyclerView not ready, participant added but UI not updated");
                        }
                    }
                });

                // CRITICAL: Create peer connection for this participant
                // This will create the peer connection and automatically send an offer
                Log.d(TAG, "About to create peer connection for: " + userId);
                createPeerConnection(userId);
                Log.d(TAG, "createPeerConnection called for: " + userId);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void createPeerConnection(String userId) {
        createPeerConnection(userId, true);
    }
    
    private void createPeerConnection(String userId, boolean createOffer) {
        // CRITICAL: Clean up any existing peer connection first to prevent ghost peers
        if (peerConnections.containsKey(userId)) {
            Log.w(TAG, "⚠️ Ghost peer connection detected for " + userId + ", cleaning up first");
            PeerConnection oldPc = peerConnections.remove(userId);
            if (oldPc != null) {
                try {
                    oldPc.close();
                    Log.d(TAG, "Closed ghost peer connection for " + userId);
                } catch (Exception e) {
                    Log.w(TAG, "Error closing ghost peer connection: " + e.getMessage());
                }
            }
            // Also clear any pending ICE candidates for this user
            pendingIceCandidates.remove(userId);
        }

        // CRITICAL: Ensure local stream exists before creating peer connection
        if (localStream == null) {
            Log.e(TAG, "Cannot create peer connection: localStream is null!");
            return;
        }

        Log.d(TAG, "Creating peer connection for user: " + userId + ", localStream tracks: " + localStream.videoTracks.size() + " video, " + localStream.audioTracks.size() + " audio, createOffer: " + createOffer);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "Signaling state for " + userId + ": " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "ICE connection state for " + userId + ": " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    Log.d(TAG, "✓✓✓ ICE CONNECTED with " + userId + " - video should start flowing");
                    // Log current state of all participants
                    logAllPeerConnectionStates();
                    // After ICE connected, check if we have video tracks
                    runOnUiThread(() -> {
                        CallParticipant participant = null;
                        for (CallParticipant p : participants) {
                            if (p.getUserId() != null && p.getUserId().trim().equalsIgnoreCase(userId.trim())) {
                                participant = p;
                                break;
                            }
                        }
                        if (participant != null) {
                            Log.d(TAG, "After ICE CONNECTED - participant " + userId + " has video track: " + 
                                  (participant.getVideoTrack() != null) + 
                                  ", track enabled: " + (participant.getVideoTrack() != null && participant.getVideoTrack().enabled()));
                        } else {
                            Log.w(TAG, "Participant " + userId + " not found after ICE CONNECTED");
                        }
                    });
                } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED || 
                          iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    Log.e(TAG, "✗✗✗ ICE connection failed/disconnected with " + userId + ": " + iceConnectionState);
                } else if (iceConnectionState == PeerConnection.IceConnectionState.CHECKING) {
                    Log.d(TAG, "→ ICE CHECKING with " + userId);
                } else if (iceConnectionState == PeerConnection.IceConnectionState.COMPLETED) {
                    Log.d(TAG, "✓ ICE COMPLETED with " + userId);
                    // After ICE completed, check if we have video tracks
                    runOnUiThread(() -> {
                        CallParticipant participant = null;
                        for (CallParticipant p : participants) {
                            if (p.getUserId() != null && p.getUserId().trim().equalsIgnoreCase(userId.trim())) {
                                participant = p;
                                break;
                            }
                        }
                        if (participant != null) {
                            Log.d(TAG, "After ICE COMPLETED - participant " + userId + " has video track: " + 
                                  (participant.getVideoTrack() != null) + 
                                  ", track enabled: " + (participant.getVideoTrack() != null && participant.getVideoTrack().enabled()));
                        }
                    });
                } else if (iceConnectionState == PeerConnection.IceConnectionState.NEW) {
                    Log.d(TAG, "→ ICE NEW with " + userId);
                } else if (iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                    Log.d(TAG, "→ ICE CLOSED with " + userId);
                }
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "ICE gathering state for " + userId + ": " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                sendIceCandidate(userId, iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {
                runOnUiThread(() -> handleRemoteStream(userId, mediaStream));
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onDataChannel(org.webrtc.DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed for " + userId);
            }

            @Override
            public void onAddTrack(org.webrtc.RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "✓✓✓ onAddTrack called for " + userId + " - Unified Plan SDP");
                Log.d(TAG, "RtpReceiver: " + (rtpReceiver != null ? "not null" : "null") + 
                      ", mediaStreams: " + (mediaStreams != null ? mediaStreams.length + " streams" : "null"));
                
                // CRITICAL: With Unified Plan SDP, get track directly from RtpReceiver
                org.webrtc.MediaStreamTrack track = rtpReceiver.track();
                
                if (track != null) {
                    Log.d(TAG, "✓ Track found for " + userId + " - type: " + track.kind() + 
                          ", id: " + track.id() + ", enabled: " + track.enabled() + ", state: " + track.state());
                    
                    if (track.kind().equals("video")) {
                        // Handle video track directly
                        org.webrtc.VideoTrack videoTrack = (org.webrtc.VideoTrack) track;
                        Log.d(TAG, "✓✓✓ Processing VIDEO track for " + userId);
                        runOnUiThread(() -> handleRemoteVideoTrack(userId, videoTrack));
                    } else if (track.kind().equals("audio")) {
                        Log.d(TAG, "Audio track received for " + userId);
                        // Audio tracks are handled automatically by WebRTC
                    } else {
                        Log.w(TAG, "Unknown track kind: " + track.kind() + " for " + userId);
                    }
                } else if (mediaStreams != null && mediaStreams.length > 0) {
                    // Fallback: try to get from MediaStream (for compatibility)
                    Log.d(TAG, "Track is null, trying MediaStream fallback for " + userId);
                    MediaStream mediaStream = mediaStreams[0];
                    Log.d(TAG, "MediaStream fallback - video tracks: " + mediaStream.videoTracks.size() + 
                          ", audio tracks: " + mediaStream.audioTracks.size());
                    runOnUiThread(() -> handleRemoteStream(userId, mediaStream));
                } else {
                    Log.e(TAG, "✗✗✗ No track or mediaStream found for " + userId + " - this is a problem!");
                }
            }
        });

        if (peerConnection != null) {
            // CRITICAL FIX: Use addTrack instead of addStream for Unified Plan SDP
            // addStream is deprecated and causes crashes with Unified Plan semantics
            // Add audio track
            if (localAudioTrack != null) {
                peerConnection.addTrack(localAudioTrack, java.util.Collections.singletonList(localStream.getId()));
                Log.d(TAG, "Added local audio track to peer connection for " + userId);
            }
            // Add video track
            if (localVideoTrack != null) {
                peerConnection.addTrack(localVideoTrack, java.util.Collections.singletonList(localStream.getId()));
                Log.d(TAG, "Added local video track to peer connection for " + userId);
            }
            
            peerConnections.put(userId, peerConnection);
            
            // CRITICAL: Flush any queued ICE candidates if they exist (in case they arrived before peer connection was created)
            flushIceCandidates(userId, peerConnection);

            // Create and send offer - this will initiate the WebRTC handshake
            // Only if createOffer is true (default behavior)
            if (createOffer) {
                Log.d(TAG, "Creating and sending offer to " + userId);
                createAndSendOffer(userId, peerConnection);
            } else {
                Log.d(TAG, "Peer connection created for " + userId + " without creating offer (will handle remote offer)");
            }
        } else {
            Log.e(TAG, "Failed to create peer connection for " + userId);
        }
    }

    private void createAndSendOffer(String userId, PeerConnection peerConnection) {
        Log.d(TAG, "Creating offer for " + userId);
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully, sending offer to " + userId);
                        sendOffer(userId, sessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set local description: " + s);
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create offer: " + s);
            }

            @Override
            public void onSetFailure(String s) {}
        }, constraints);
    }

    private void sendOffer(String toUserId, SessionDescription sessionDescription) {
        try {
            JSONObject offer = new JSONObject();
            offer.put("type", sessionDescription.type.canonicalForm());
            offer.put("sdp", sessionDescription.description);
            Log.d(TAG, "Sending offer to " + toUserId + " for callId: " + callId);
            socketManager.sendGroupCallOffer(callId, toUserId, offer);
            Log.d(TAG, "Offer sent successfully to " + toUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending offer to " + toUserId, e);
            e.printStackTrace();
        }
    }

    private void sendAnswer(String toUserId, SessionDescription sessionDescription) {
        try {
            JSONObject answer = new JSONObject();
            answer.put("type", sessionDescription.type.canonicalForm());
            answer.put("sdp", sessionDescription.description);
            Log.d(TAG, "Sending answer to " + toUserId + " for callId: " + callId);
            socketManager.sendGroupCallAnswer(callId, toUserId, answer);
            Log.d(TAG, "Answer sent successfully to " + toUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending answer to " + toUserId, e);
            e.printStackTrace();
        }
    }

    private void sendIceCandidate(String toUserId, IceCandidate iceCandidate) {
        try {
            JSONObject candidate = new JSONObject();
            candidate.put("sdpMid", iceCandidate.sdpMid);
            candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            candidate.put("candidate", iceCandidate.sdp);
            socketManager.sendGroupCallIceCandidate(callId, toUserId, candidate);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleParticipantJoined(String joinedUserId, JSONObject userObj) {
        if (userObj == null) {
            Log.w(TAG, "handleParticipantJoined: userObj is null for userId: " + joinedUserId);
            return;
        }
        
        // CRITICAL FIX: Only add if it's not the current user
        if (joinedUserId == null || joinedUserId.isEmpty()) {
            Log.w(TAG, "handleParticipantJoined: joinedUserId is null or empty");
            return;
        }
        
        // Use case-insensitive comparison
        if (currentUserId != null && joinedUserId.trim().equalsIgnoreCase(currentUserId.trim())) {
            Log.d(TAG, "Ignoring own join event - userId: " + joinedUserId + ", currentUserId: " + currentUserId);
            return;
        }
        
        // CRITICAL FIX: Thread-safe duplicate check using synchronized set
        boolean isRejoin = false;
        synchronized (addedParticipantIds) {
            // Check both exact match and case-insensitive match
            for (String addedId : addedParticipantIds) {
                if (addedId != null && addedId.trim().equalsIgnoreCase(joinedUserId.trim())) {
                    isRejoin = true;
                    break;
                }
            }
        }
        
        // Create participant JSON from user object
        try {
            JSONObject participantJson = new JSONObject();
            participantJson.put("userId", joinedUserId);
            participantJson.put("username", userObj.optString("username", "Unknown"));
            participantJson.put("status", "connected");
            
            if (isRejoin) {
                Log.d(TAG, "Participant rejoined: " + joinedUserId + ", updating and recreating peer connection");
                // Remove old peer connection if exists
                PeerConnection oldPc = peerConnections.remove(joinedUserId);
                if (oldPc != null) {
                    oldPc.close();
                }
            } else {
                Log.d(TAG, "New participant joined: " + joinedUserId + ", creating peer connection and sending offer");
            }
            
            // CRITICAL: Add participant and create peer connection
            // This will trigger createPeerConnection which will create and send offer
            Log.d(TAG, "Calling addRemoteParticipant for: " + joinedUserId);
            addRemoteParticipant(participantJson);
            Log.d(TAG, "addRemoteParticipant completed for: " + joinedUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating participant JSON: " + e.getMessage(), e);
        }
    }

    private void handleRemoteVideoTrack(String userId, org.webrtc.VideoTrack videoTrack) {
        Log.d(TAG, "✓✓✓✓✓ handleRemoteVideoTrack called for " + userId + " - track: " + (videoTrack != null) + 
              ", enabled: " + (videoTrack != null ? videoTrack.enabled() : "N/A"));
        
        if (videoTrack == null) {
            Log.e(TAG, "✗✗✗ Video track is NULL for " + userId);
            return;
        }
        
        // Find participant and update video track
        int participantIndex = -1;
        CallParticipant targetParticipant = null;
        
        for (int i = 0; i < participants.size(); i++) {
            CallParticipant participant = participants.get(i);
            if (participant.getUserId() != null && participant.getUserId().trim().equalsIgnoreCase(userId.trim())) {
                participantIndex = i;
                targetParticipant = participant;
                break;
            }
        }
        
        if (targetParticipant == null) {
            Log.e(TAG, "✗✗✗ Participant not found for userId: " + userId + " (total participants: " + participants.size() + ")");
            // Log all participant IDs for debugging
            for (int i = 0; i < participants.size(); i++) {
                Log.d(TAG, "  Participant[" + i + "]: userId=" + participants.get(i).getUserId() + 
                      ", username=" + participants.get(i).getUsername());
            }
            return;
        }
        
        Log.d(TAG, "✓ Found participant " + userId + " at index " + participantIndex + ", setting video track");
        
        // Remove old track if exists
        VideoTrack oldTrack = targetParticipant.getVideoTrack();
        if (oldTrack != null && oldTrack != videoTrack) {
            try {
                // Remove sink from all surface views before disposing
                oldTrack.dispose();
                Log.d(TAG, "Disposed old video track for " + userId);
            } catch (Exception e) {
                Log.w(TAG, "Error disposing old track: " + e.getMessage());
            }
        }
        
        // Set new video track
        targetParticipant.setVideoTrack(videoTrack);
        targetParticipant.setVideoMuted(false);
        
        // CRITICAL: Ensure video track is enabled (like private call does)
        if (!videoTrack.enabled()) {
            videoTrack.setEnabled(true);
            Log.d(TAG, "✓ Enabled video track for " + userId);
        }
        
        Log.d(TAG, "✓✓✓ Video track set for " + userId + 
              ", track enabled: " + videoTrack.enabled() + 
              ", track id: " + videoTrack.id() +
              ", track state: " + videoTrack.state() +
              ", participant has track: " + (targetParticipant.getVideoTrack() != null));
        
        // CRITICAL: Like private call, let the adapter handle adding the sink
        // The adapter's onBindViewHolder will add the sink when the view is bound
        // This is simpler and more reliable than trying to add it directly here
        if (participantAdapter != null) {
            // Notify adapter to rebind this participant's view
            // The adapter will add the sink in onBindViewHolder (like private call does in onAddStream)
            if (participantIndex >= 0 && participantIndex < participants.size()) {
                participantAdapter.notifyItemChanged(participantIndex);
                Log.d(TAG, "✓ Notified adapter to rebind participant at index " + participantIndex);
            } else {
                // If index is invalid, use updateParticipantVideoTrack which will find the index
                participantAdapter.updateParticipantVideoTrack(userId);
                Log.d(TAG, "✓ Called updateParticipantVideoTrack for " + userId);
            }
        } else {
            Log.w(TAG, "Adapter is null, cannot notify of video track update");
        }
        
        // FINAL CHECK: Verify video track is stored and log state
        if (targetParticipant != null) {
            VideoTrack storedTrack = targetParticipant.getVideoTrack();
            Log.d(TAG, "✓✓✓✓✓ Final state for " + userId + 
                  ": stored track=" + (storedTrack != null) + 
                  ", enabled=" + (storedTrack != null ? storedTrack.enabled() : "N/A") +
                  ", videoMuted=" + targetParticipant.isVideoMuted() +
                  ", participantIndex=" + participantIndex);
        }
    }
    
    private void handleRemoteStream(String userId, MediaStream mediaStream) {
        Log.d(TAG, "Handling remote stream for " + userId + " (fallback method)");
        
        // Find participant and update video track
        for (CallParticipant participant : participants) {
            if (participant.getUserId() != null && participant.getUserId().trim().equalsIgnoreCase(userId.trim())) {
                if (!mediaStream.videoTracks.isEmpty()) {
                    VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                    participant.setVideoTrack(remoteVideoTrack);
                    participant.setVideoMuted(false);
                    Log.d(TAG, "Video track set from MediaStream for " + userId);
                }
                
                // Notify adapter to update UI
                if (participantAdapter != null) {
                    try {
                        participantAdapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying adapter: " + e.getMessage(), e);
                    }
                }
                break;
            }
        }
    }

    private void setupSocketListeners() {
        // Listen for room state (sent when joining - contains existing participants)
        socketManager.on("group_call_room_state", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String roomCallId = data.optString("callId", "");
                    String roomChatId = data.optString("chatId", "");
                    JSONArray participantsArray = data.optJSONArray("participants");
                    
                    Log.d(TAG, "Received room state - callId: " + roomCallId + ", chatId: " + roomChatId + ", participants: " + (participantsArray != null ? participantsArray.length() : 0));
                    
                    // CRITICAL: Update callId if it's different (shouldn't happen, but handle it)
                    if (roomCallId != null && !roomCallId.isEmpty() && !roomCallId.equals(callId)) {
                        Log.w(TAG, "Room state has different callId: " + roomCallId + " vs " + callId + ", updating to match room");
                        callId = roomCallId;
                    }
                    
                    // Create peer connections for all existing participants
                    if (participantsArray != null) {
                        for (int i = 0; i < participantsArray.length(); i++) {
                            JSONObject participantJson = participantsArray.getJSONObject(i);
                            String participantUserId = participantJson.optString("userId", "");
                            
                            // CRITICAL FIX: Skip self (never add local participant as remote)
                            // Use trim and equalsIgnoreCase to handle format differences
                            if (participantUserId.isEmpty() || 
                                (currentUserId != null && participantUserId.trim().equalsIgnoreCase(currentUserId.trim()))) {
                                Log.d(TAG, "Skipping self or invalid userId in room state: " + participantUserId + " (currentUserId: " + currentUserId + ")");
                                continue;
                            }
                            
                            // CRITICAL FIX: Thread-safe duplicate check using synchronized set
                            synchronized (addedParticipantIds) {
                                // Check both exact match and case-insensitive match
                                boolean alreadyAdded = false;
                                for (String addedId : addedParticipantIds) {
                                    if (addedId != null && addedId.trim().equalsIgnoreCase(participantUserId.trim())) {
                                        alreadyAdded = true;
                                        break;
                                    }
                                }
                                if (alreadyAdded) {
                                    Log.d(TAG, "Participant " + participantUserId + " already in added set, skipping duplicate from room state");
                                    continue;
                                }
                            }
                            
                            Log.d(TAG, "Adding existing participant from room state: " + participantUserId);
                            addRemoteParticipant(participantJson);
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error handling room state", e);
                    e.printStackTrace();
                }
            }
        });

        // Listen for new participants joining (from API join)
        socketManager.on("group_call_participant_joined", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                JSONObject data = (JSONObject) args[0];

                // CRITICAL: Server sends userId in user.id, not directly as userId
                String joinedUserId = "";
                JSONObject userObj = data.optJSONObject("user");

                if (userObj != null) {
                    // Try to get userId from user.id (server format)
                    joinedUserId = userObj.optString("id", "");
                    // If user.id is not found, try userId directly (fallback)
                    if (joinedUserId.isEmpty()) {
                        joinedUserId = data.optString("userId", "");
                    }
                } else {
                    // Fallback: try userId directly
                    joinedUserId = data.optString("userId", "");
                }

                Log.d(TAG, "Received group_call_participant_joined event - userId: " + joinedUserId +
                      ", hasUserObj: " + (userObj != null));

                if (joinedUserId.isEmpty()) {
                    Log.w(TAG, "group_call_participant_joined event has empty userId, data: " + data.toString());
                    return;
                }

                handleParticipantJoined(joinedUserId, userObj);
            }
        });
        
        // CRITICAL: Also listen for group_call_user_joined_room (from socket room join)
        // This ensures we catch participants even if they join via socket before API
        socketManager.on("group_call_user_joined_room", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String joinedUserId = data.optString("userId", "");
                    String username = data.optString("username", "Unknown");
                    
                    Log.d(TAG, "Received group_call_user_joined_room event for: " + joinedUserId);
                    
                    // Create user object from the data
                    JSONObject userObj = new JSONObject();
                    userObj.put("username", username);
                    userObj.put("avatar", data.optString("avatar", ""));
                    
                    handleParticipantJoined(joinedUserId, userObj);
                } catch (JSONException e) {
                    Log.e(TAG, "Error handling user joined room", e);
                    e.printStackTrace();
                }
            }
        });

        // Listen for participants leaving
        socketManager.on("group_call_participant_left", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                JSONObject data = (JSONObject) args[0];
                String userId = data.optString("userId", "");
                String reason = data.optString("reason", "unknown");
                
                Log.d(TAG, "Received group_call_participant_left event - userId: " + userId + ", reason: " + reason);
                
                // CRITICAL: Only remove if userId is valid and not empty
                if (userId == null || userId.isEmpty()) {
                    Log.w(TAG, "group_call_participant_left event has empty userId, ignoring");
                    return;
                }
                
                // CRITICAL: Never remove local user
                if (currentUserId != null && userId.trim().equalsIgnoreCase(currentUserId.trim())) {
                    Log.w(TAG, "Attempted to remove local user via participant_left event, ignoring: " + userId);
                    return;
                }
                
                removeParticipant(userId);
            }
        });

        // Listen for WebRTC offers
        socketManager.on("group_call_webrtc_offer", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String fromUserId = data.getString("fromUserId");
                    JSONObject offer = data.getJSONObject("offer");
                    handleRemoteOffer(fromUserId, offer);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // Listen for WebRTC answers
        socketManager.on("group_call_webrtc_answer", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String fromUserId = data.getString("fromUserId");
                    JSONObject answer = data.getJSONObject("answer");
                    handleRemoteAnswer(fromUserId, answer);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // Listen for ICE candidates
        socketManager.on("group_call_ice_candidate", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String fromUserId = data.getString("fromUserId");
                    JSONObject candidateJson = data.getJSONObject("candidate");
                    handleRemoteIceCandidate(fromUserId, candidateJson);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // Listen for media updates
        socketManager.on("participant_media_updated", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String userId = data.getString("userId");
                    boolean audioMuted = data.getBoolean("audioMuted");
                    boolean videoMuted = data.getBoolean("videoMuted");
                    updateParticipantMedia(userId, audioMuted, videoMuted);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // Listen for call ended
        socketManager.on("call_ended", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String endedCallId = data.getString("callId");
                    
                    if (callId != null && callId.equals(endedCallId)) {
                        runOnUiThread(() -> finish());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void handleRemoteOffer(String fromUserId, JSONObject offerJson) {
        try {
            String type = offerJson.getString("type");
            String sdp = offerJson.getString("sdp");
            SessionDescription offer = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);

            PeerConnection peerConnection = peerConnections.get(fromUserId);
            if (peerConnection == null) {
                Log.d(TAG, "Peer connection not found for " + fromUserId + ", creating new one");
                createPeerConnection(fromUserId);
                peerConnection = peerConnections.get(fromUserId);
            }

            if (peerConnection != null) {
                // CRITICAL: Ensure local stream exists and is added to peer connection
                // This is required for answer creation
                if (localStream == null) {
                    Log.e(TAG, "Cannot handle offer: localStream is null!");
                    return;
                }
                
                // CRITICAL: Check signaling state to handle offer/offer collision
                // If we already have a local offer, we need to roll back or handle collision
                PeerConnection.SignalingState currentState = peerConnection.signalingState();
                Log.d(TAG, "Current signaling state for " + fromUserId + ": " + currentState);
                
                if (currentState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    Log.w(TAG, "Offer/offer collision detected for " + fromUserId + 
                          " - we have local offer, remote also sent offer. Recreating peer connection WITHOUT creating offer.");
                    
                    // CRITICAL: WebRTC doesn't support setting local description to null
                    // Instead, close and recreate the peer connection WITHOUT creating an offer
                    // Then we'll set the remote offer and create an answer
                    try {
                        peerConnection.close();
                        Log.d(TAG, "Closed existing peer connection for " + fromUserId + " due to offer/offer collision");
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing peer connection: " + e.getMessage(), e);
                    }
                    
                    // Remove from map
                    peerConnections.remove(fromUserId);
                    
                    // Create new peer connection WITHOUT creating an offer (we'll handle the remote offer instead)
                    createPeerConnection(fromUserId, false);
                    peerConnection = peerConnections.get(fromUserId);
                    
                    if (peerConnection == null) {
                        Log.e(TAG, "Failed to recreate peer connection for " + fromUserId);
                        return;
                    }
                    
                    Log.d(TAG, "Recreated peer connection for " + fromUserId + " (without offer), now handling remote offer");
                    // Continue to handle the offer with the new peer connection (which is in STABLE state)
                }
                
                // CRITICAL: Double-check signaling state before setting remote offer
                // After collision resolution, we should be in STABLE state
                PeerConnection.SignalingState finalState = peerConnection.signalingState();
                Log.d(TAG, "Final signaling state before setting remote offer for " + fromUserId + ": " + finalState);
                
                if (finalState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    Log.e(TAG, "✗✗✗ ERROR: Still in HAVE_LOCAL_OFFER state after collision resolution for " + fromUserId + 
                          " - cannot set remote offer. This should not happen!");
                    // Try to wait a bit and check again, or just return
                    return;
                }
                
                PeerConnection finalPeerConnection = peerConnection;
                Log.d(TAG, "✓ Setting remote offer for " + fromUserId + " (state: " + finalState + ")");
                peerConnection.setRemoteDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "✓✓✓ Remote offer set successfully for " + fromUserId + ", creating answer");
                        
                        // CRITICAL: Flush any queued ICE candidates now that remote description is set
                        flushIceCandidates(fromUserId, finalPeerConnection);
                        
                        // Create answer
                        MediaConstraints constraints = new MediaConstraints();
                        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
                        Log.d(TAG, "Creating answer for " + fromUserId + " with constraints");
                        finalPeerConnection.createAnswer(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                Log.d(TAG, "✓✓✓ Answer created successfully for " + fromUserId + 
                                      ", type: " + sessionDescription.type + ", sdp length: " + sessionDescription.description.length());
                                finalPeerConnection.setLocalDescription(new SdpObserver() {
                                    @Override
                                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                                    @Override
                                    public void onSetSuccess() {
                                        Log.d(TAG, "✓✓✓ Local description set successfully for answer, sending to " + fromUserId);
                                        sendAnswer(fromUserId, sessionDescription);
                                        Log.d(TAG, "✓✓✓ Answer sent to " + fromUserId + " - waiting for onAddTrack callback");
                                    }

                                    @Override
                                    public void onCreateFailure(String s) {
                                        Log.e(TAG, "Failed to create local description for answer: " + s);
                                    }

                                    @Override
                                    public void onSetFailure(String s) {
                                        Log.e(TAG, "Failed to set local description for answer: " + s);
                                    }
                                }, sessionDescription);
                            }

                            @Override
                            public void onSetSuccess() {}

                            @Override
                            public void onCreateFailure(String s) {
                                Log.e(TAG, "✗✗✗ Failed to create answer for " + fromUserId + ": " + s);
                            }

                            @Override
                            public void onSetFailure(String s) {
                                Log.e(TAG, "✗✗✗ Failed to set answer for " + fromUserId + ": " + s);
                            }
                        }, constraints);
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "Failed to create remote description for " + fromUserId + ": " + s);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set remote offer for " + fromUserId + ": " + s);
                    }
                }, offer);
            } else {
                Log.e(TAG, "Peer connection is null for " + fromUserId + ", cannot handle offer");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing offer JSON for " + fromUserId, e);
            e.printStackTrace();
        }
    }
    

    private void handleRemoteAnswer(String fromUserId, JSONObject answerJson) {
        try {
            String type = answerJson.getString("type");
            String sdp = answerJson.getString("sdp");
            SessionDescription answer = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);

            PeerConnection peerConnection = peerConnections.get(fromUserId);
            if (peerConnection != null) {
                peerConnection.setRemoteDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully for " + fromUserId + " - WebRTC connection should be established");
                
                // CRITICAL: Flush any queued ICE candidates now that remote description is set
                if (peerConnection != null) {
                    flushIceCandidates(fromUserId, peerConnection);
                }
                
                // After answer is set, tracks should be received via onAddTrack callback
            }

                    @Override
                    public void onCreateFailure(String s) {}

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set remote answer: " + s);
                    }
                }, answer);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleRemoteIceCandidate(String fromUserId, JSONObject candidateJson) {
        try {
            String sdpMid = candidateJson.getString("sdpMid");
            int sdpMLineIndex = candidateJson.getInt("sdpMLineIndex");
            String sdp = candidateJson.getString("candidate");
            IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);

            PeerConnection peerConnection = peerConnections.get(fromUserId);
            if (peerConnection != null) {
                // CRITICAL: Check if remote description is set before adding ICE candidate
                SessionDescription remoteDesc = peerConnection.getRemoteDescription();
                if (remoteDesc != null) {
                    // Remote description is set, add candidate immediately
                    try {
                        peerConnection.addIceCandidate(candidate);
                        Log.d(TAG, "✓ Added ICE candidate for " + fromUserId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error adding ICE candidate for " + fromUserId + ": " + e.getMessage());
                    }
                } else {
                    // Remote description not set yet, queue the candidate
                    Log.d(TAG, "Remote description not set for " + fromUserId + ", queueing ICE candidate");
                    pendingIceCandidates.putIfAbsent(fromUserId, new java.util.ArrayList<>());
                    pendingIceCandidates.get(fromUserId).add(candidate);
                }
            } else {
                Log.w(TAG, "Peer connection not found for " + fromUserId + ", queueing ICE candidate");
                // Queue candidate even if peer connection doesn't exist yet
                pendingIceCandidates.putIfAbsent(fromUserId, new java.util.ArrayList<>());
                pendingIceCandidates.get(fromUserId).add(candidate);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing ICE candidate JSON for " + fromUserId, e);
            e.printStackTrace();
        }
    }
    
    /**
     * Flush queued ICE candidates for a participant after remote description is set
     */
    private void flushIceCandidates(String userId, PeerConnection peerConnection) {
        List<IceCandidate> queued = pendingIceCandidates.remove(userId);
        if (queued != null && !queued.isEmpty()) {
            Log.d(TAG, "Flushing " + queued.size() + " queued ICE candidates for " + userId);
            for (IceCandidate candidate : queued) {
                try {
                    peerConnection.addIceCandidate(candidate);
                    Log.d(TAG, "✓ Added queued ICE candidate for " + userId);
                } catch (Exception e) {
                    Log.e(TAG, "Error adding queued ICE candidate for " + userId + ": " + e.getMessage());
                }
            }
        }
    }

    private void removeParticipant(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "removeParticipant called with null or empty userId");
            return;
        }
        
        // CRITICAL: Never remove local user
        if (currentUserId != null && userId.trim().equalsIgnoreCase(currentUserId.trim())) {
            Log.w(TAG, "Attempted to remove local user, ignoring: " + userId);
            return;
        }
        
        Log.d(TAG, "Removing participant: " + userId + " (current participants: " + participants.size() + ")");
        
        // CRITICAL FIX: Close peer connection FIRST (before UI updates)
        // This ensures the video stream stops immediately
        PeerConnection peerConnection = peerConnections.remove(userId);
        if (peerConnection != null) {
            Log.d(TAG, "Closing peer connection for participant: " + userId);
            try {
                peerConnection.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing peer connection: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "No peer connection found for participant: " + userId);
        }
        
        // CRITICAL FIX: All list modifications must happen on UI thread to prevent race conditions
        runOnUiThread(() -> {
            // Find and dispose video track before removing participant
            CallParticipant participantToRemove = null;
            int indexToRemove = -1;
            
            for (int i = 0; i < participants.size(); i++) {
                CallParticipant participant = participants.get(i);
                // CRITICAL: Use case-insensitive comparison and handle null
                if (participant.getUserId() != null && 
                    participant.getUserId().trim().equalsIgnoreCase(userId.trim())) {
                    participantToRemove = participant;
                    indexToRemove = i;
                    Log.d(TAG, "Found participant to remove at index " + i + ": " + participant.getUsername() + " (userId: " + participant.getUserId() + ")");
                    break;
                }
            }
            
            if (participantToRemove != null && indexToRemove >= 0) {
                // Dispose video track
                VideoTrack videoTrack = participantToRemove.getVideoTrack();
                if (videoTrack != null) {
                    Log.d(TAG, "Disposing video track for participant: " + userId);
                    try {
                        videoTrack.dispose();
                    } catch (Exception e) {
                        Log.e(TAG, "Error disposing video track: " + e.getMessage(), e);
                    }
                    participantToRemove.setVideoTrack(null);
                }
                
                // Remove from list and tracking set atomically
                synchronized (addedParticipantIds) {
                    addedParticipantIds.remove(userId);
                    participants.remove(indexToRemove);
                }
                
                // CRITICAL FIX: Safely notify adapter with error handling
                if (participantAdapter != null && rvVideoGrid != null) {
                    try {
                        // Use notifyDataSetChanged for removal to avoid index issues
                        participantAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Adapter notified of participant removal: " + userId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying adapter of removal: " + e.getMessage(), e);
                        // Try again after a short delay
                        new android.os.Handler().postDelayed(() -> {
                            if (participantAdapter != null) {
                                try {
                                    participantAdapter.notifyDataSetChanged();
                                } catch (Exception e2) {
                                    Log.e(TAG, "Error in delayed adapter notification: " + e2.getMessage());
                                }
                            }
                        }, 100);
                    }
                }
                
                updateParticipantCount();
                Log.d(TAG, "Participant removed from UI: " + userId);
            } else {
                Log.w(TAG, "Participant not found in list: " + userId);
            }
        });
    }

    private void updateParticipantMedia(String userId, boolean audioMuted, boolean videoMuted) {
        for (CallParticipant participant : participants) {
            if (participant.getUserId().equals(userId)) {
                participant.setAudioMuted(audioMuted);
                participant.setVideoMuted(videoMuted);
                runOnUiThread(() -> participantAdapter.notifyDataSetChanged());
                break;
            }
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!isMuted);
        }

        // Update local participant
        if (!participants.isEmpty()) {
            participants.get(0).setAudioMuted(isMuted);
        }

        updateMuteButton();
        sendMediaUpdate();
        participantAdapter.notifyDataSetChanged();
    }

    private void toggleCamera() {
        // Don't toggle if we're cleaning up
        if (isCleaningUp) {
            Log.w(TAG, "Cannot toggle camera - cleanup in progress");
            return;
        }
        
        isCameraOn = !isCameraOn;

        if (isCameraOn && videoCapturer != null && videoSource != null) {
            // Start camera (like private call)
            try {
                videoCapturer.initialize(
                        org.webrtc.SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext()),
                        this,
                        videoSource.getCapturerObserver()
                );
                videoCapturer.startCapture(1280, 720, 30);
                
                // Update UI
                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(true);
                }
                if (!participants.isEmpty()) {
                    CallParticipant localParticipant = participants.get(0);
                    localParticipant.setVideoMuted(false);
                    if (localVideoTrack != null) {
                        localParticipant.setVideoTrack(localVideoTrack);
                    }
                }
                updateCameraButton();
                sendMediaUpdate();
                participantAdapter.notifyItemChanged(0);
                participantAdapter.notifyDataSetChanged();
            } catch (Exception e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Failed to start camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                isCameraOn = false;
                updateCameraButton();
            }
        } else if (!isCameraOn && videoCapturer != null) {
            // Stop camera
            try {
                videoCapturer.stopCapture();
                
                // Update UI
                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(false);
                }
                if (!participants.isEmpty()) {
                    CallParticipant localParticipant = participants.get(0);
                    localParticipant.setVideoMuted(true);
                    if (localVideoTrack != null) {
                        localParticipant.setVideoTrack(localVideoTrack);
                    }
                }
                updateCameraButton();
                sendMediaUpdate();
                participantAdapter.notifyItemChanged(0);
                participantAdapter.notifyDataSetChanged();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping camera", e);
                Toast.makeText(this, "Failed to stop camera", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping camera", e);
                Toast.makeText(this, "Failed to stop camera", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Update UI immediately if no camera operations needed
            if (!participants.isEmpty()) {
                participants.get(0).setVideoMuted(!isCameraOn);
            }
            updateCameraButton();
            sendMediaUpdate();
            participantAdapter.notifyDataSetChanged();
        }
    }

    private void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            isFrontCamera = !isFrontCamera;
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
        }
    }

    private void sendMediaUpdate() {
        // CRITICAL: Don't send media update if callId is not set yet
        if (callId == null || callId.isEmpty()) {
            Log.w(TAG, "Cannot send media update: callId is null or empty");
            return;
        }
        
        JSONObject data = new JSONObject();
        try {
            data.put("audioMuted", isMuted);
            data.put("videoMuted", !isCameraOn);
            data.put("screenSharing", false);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        String token = databaseManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "Cannot send media update: token is null or empty");
            return;
        }
        
        apiClient.authenticatedPatch("/api/group-calls/" + callId + "/media", token, data, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to update media state", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Media state updated successfully");
                } else {
                    Log.e(TAG, "Failed to update media state: " + response.code());
                }
            }
        });
    }

    private void leaveCall() {
        String token = databaseManager.getToken();
        apiClient.authenticatedPost("/api/group-calls/" + callId + "/leave", token, new JSONObject(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> finish());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> finish());
            }
        });
    }

    private void showParticipantsList() {
        // TODO: Implement participants list dialog
        Toast.makeText(this, "Participants: " + participants.size(), Toast.LENGTH_SHORT).show();
    }

    private void startCallTimer() {
        callTimer = new Timer();
        callTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                callDuration++;
                runOnUiThread(() -> updateCallDuration());
            }
        }, 1000, 1000);
    }

    @SuppressLint("DefaultLocale")
    private void updateCallDuration() {
        int minutes = callDuration / 60;
        int seconds = callDuration % 60;
        tvCallDuration.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void updateParticipantCount() {
        int count = participants.size();
        tvParticipantCount.setText(count + (count == 1 ? " participant" : " participants"));
    }

    private void updateMuteButton() {
        if (isMuted) {
            btnMute.setImageResource(R.drawable.ic_mic_off);
        } else {
            btnMute.setImageResource(R.drawable.ic_mic);
        }
    }

    private void updateCameraButton() {
        if (isCameraOn) {
            btnCameraToggle.setImageResource(R.drawable.ic_video_call);
        } else {
            btnCameraToggle.setImageResource(R.drawable.ic_video_off);
        }
    }

    private void showLoading(String message) {
        loadingOverlay.setVisibility(View.VISIBLE);
        tvLoadingMessage.setText(message);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Stopping camera");
        
        // Stop camera when activity is paused to prevent crashes
        if (videoCapturer != null && isCameraOn) {
            try {
                videoCapturer.stopCapture();
                isCameraOn = false;
                if (localVideoTrack != null) {
                    localVideoTrack.setEnabled(false);
                }
                Log.d(TAG, "Camera stopped in onPause");
            } catch (Exception e) {
                Log.w(TAG, "Error stopping camera in onPause: " + e.getMessage());
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy: Cleaning up resources");

        // Stop call timer
        if (callTimer != null) {
            callTimer.cancel();
            callTimer = null;
        }

        // Clean up WebRTC resources (exactly like private call does)
        cleanupWebRTC();

        // Remove socket listeners
        if (socketManager != null) {
            socketManager.off("group_call_room_state");
            socketManager.off("group_call_participant_joined");
            socketManager.off("group_call_user_joined_room");
            socketManager.off("group_call_participant_left");
            socketManager.off("group_call_webrtc_offer");
            socketManager.off("group_call_webrtc_answer");
            socketManager.off("group_call_ice_candidate");
            socketManager.off("participant_media_updated");
            socketManager.off("call_ended");
        }
        
        // Clear tracking set
        synchronized (addedParticipantIds) {
            addedParticipantIds.clear();
        }
        
        Log.d(TAG, "onDestroy: Cleanup complete");
    }
    
    private void cleanupWebRTC() {
        Log.d(TAG, "Cleaning up WebRTC resources");
        
        // Set cleanup flag to prevent any new operations
        isCleaningUp = true;
        
        // Clear adapter to prevent RecyclerView from accessing views during cleanup
        if (participantAdapter != null && rvVideoGrid != null) {
            try {
                rvVideoGrid.setAdapter(null);
                Log.d(TAG, "Adapter cleared");
            } catch (Exception e) {
                Log.w(TAG, "Error clearing adapter: " + e.getMessage());
            }
        }

        try {
            // STEP 1: Stop video capturer first (exactly like VideoCallActivity)
            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture();
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping video capturer: " + e.getMessage());
                }
                try {
                    videoCapturer.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing video capturer: " + e.getMessage());
                }
                videoCapturer = null;
            }

            // STEP 2: Remove tracks from peer connections before disposing (like VideoCallActivity)
            // For group calls, we need to remove tracks from all peer connections
            for (PeerConnection pc : peerConnections.values()) {
                if (pc != null && localStream != null) {
                    try {
                        if (localVideoTrack != null) {
                            localStream.removeTrack(localVideoTrack);
                        }
                        if (localAudioTrack != null) {
                            localStream.removeTrack(localAudioTrack);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error removing tracks from stream: " + e.getMessage());
                    }
                }
            }

            // STEP 3: Dispose peer connections first (like VideoCallActivity)
            // This will dispose tracks automatically
            for (PeerConnection pc : peerConnections.values()) {
                try {
                    pc.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing peer connection: " + e.getMessage());
                }
            }
            peerConnections.clear();
            
            // Clear pending ICE candidates
            pendingIceCandidates.clear();

            // STEP 4: Dispose video tracks (after peer connections are disposed)
            if (localVideoTrack != null) {
                try {
                    localVideoTrack.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing local video track: " + e.getMessage());
                }
                localVideoTrack = null;
            }

            if (localAudioTrack != null) {
                try {
                    localAudioTrack.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing local audio track: " + e.getMessage());
                }
                localAudioTrack = null;
            }

            // STEP 5: Dispose video source (like VideoCallActivity)
            if (videoSource != null) {
                try {
                    videoSource.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing video source: " + e.getMessage());
                }
                videoSource = null;
            }

            if (audioSource != null) {
                try {
                    audioSource.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing audio source: " + e.getMessage());
                }
                audioSource = null;
            }

            // STEP 6: Dispose peer connection factory (like VideoCallActivity)
            if (peerConnectionFactory != null) {
                try {
                    peerConnectionFactory.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing PeerConnectionFactory: " + e.getMessage());
                }
                peerConnectionFactory = null;
            }

            // STEP 7: Release video views (like VideoCallActivity)
            // For RecyclerView, release all visible surface views
            if (rvVideoGrid != null) {
                try {
                    for (int i = 0; i < rvVideoGrid.getChildCount(); i++) {
                        android.view.View child = rvVideoGrid.getChildAt(i);
                        if (child != null) {
                            org.webrtc.SurfaceViewRenderer surfaceView = child.findViewById(R.id.sv_participant_video);
                            if (surfaceView != null) {
                                try {
                                    surfaceView.release();
                                } catch (Exception e) {
                                    Log.w(TAG, "Error releasing surface view: " + e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing surface views: " + e.getMessage());
                }
            }

            // STEP 8: Dispose EGL base (like VideoCallActivity)
            if (eglBase != null) {
                try {
                    eglBase.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing EGL base: " + e.getMessage());
                }
                eglBase = null;
            }

            Log.d(TAG, "WebRTC resources cleaned up successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up WebRTC resources", e);
        }
    }
    
    /**
     * Log the state of all peer connections for debugging
     */
    private void logAllPeerConnectionStates() {
        Log.d(TAG, "=== PEER CONNECTION STATES ===");
        Log.d(TAG, "Total peer connections: " + peerConnections.size());
        Log.d(TAG, "Total participants: " + participants.size());
        
        for (String userId : peerConnections.keySet()) {
            PeerConnection pc = peerConnections.get(userId);
            if (pc != null) {
                Log.d(TAG, "  User: " + userId);
                Log.d(TAG, "    - Signaling State: " + pc.signalingState());
                Log.d(TAG, "    - ICE Connection State: " + pc.iceConnectionState());
                Log.d(TAG, "    - ICE Gathering State: " + pc.iceGatheringState());
                
                // Check if participant exists and has video track
                for (CallParticipant participant : participants) {
                    if (participant.getUserId() != null && participant.getUserId().trim().equalsIgnoreCase(userId.trim())) {
                        VideoTrack videoTrack = participant.getVideoTrack();
                        Log.d(TAG, "    - Has Video Track: " + (videoTrack != null));
                        if (videoTrack != null) {
                            Log.d(TAG, "    - Video Track Enabled: " + videoTrack.enabled());
                            Log.d(TAG, "    - Video Track State: " + videoTrack.state());
                            Log.d(TAG, "    - Video Muted: " + participant.isVideoMuted());
                        }
                        break;
                    }
                }
            }
        }
        Log.d(TAG, "=== END PEER CONNECTION STATES ===");
    }
}

