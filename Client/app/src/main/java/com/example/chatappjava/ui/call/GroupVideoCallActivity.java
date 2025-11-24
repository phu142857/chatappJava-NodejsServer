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
import com.example.chatappjava.webrtc.SFUManager;

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
import org.webrtc.MediaStreamTrack;

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
    private String sessionId; // CRITICAL: Session ID for this join to prevent stale signalling messages
    // REMOVED: isCaller - everyone joins the same way
    private boolean isMuted = false;
    private boolean isCameraOn = true; // Video ON by default (like private call)
    private boolean isFrontCamera = true;
    private boolean isJoining = false; // Flag to prevent multiple simultaneous joins
    private boolean isLeaving = false; // Flag to prevent operations during leave

    // WebRTC components
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase eglBase;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private MediaStream localStream;
    private List<PeerConnection.IceServer> iceServers = new ArrayList<>();
    
    // SFU components
    private SFUManager sfuManager;
    private String sfuRoomId;
    private JSONObject sfuRtpCapabilities;

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
        // REMOVED: isCaller - everyone joins the same way
        
        // CRITICAL: Log the received values to debug join flow
        Log.d(TAG, "GroupVideoCallActivity onCreate - callId: " + callId + ", chatId: " + chatId);

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
            // Check ALL permissions were granted (both CAMERA and RECORD_AUDIO)
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                    break;
                }
            }
            
            if (allGranted && grantResults.length > 0) {
                // Double-check permissions are actually granted
                if (checkPermissions()) {
                    initializeGroupCall();
                } else {
                    Log.e(TAG, "Permissions reported granted but checkPermissions() returned false");
                    Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initializeGroupCall() {
        // CRITICAL: Don't initialize if we're leaving or already joining
        if (isLeaving) {
            Log.w(TAG, "Cannot initialize - currently leaving");
            return;
        }
        
        showLoading("Initializing call...");
        
        // Initialize WebRTC (this will create local tracks if they don't exist)
        initializeWebRTC();

        // CRITICAL: Everyone joins the same way - check for active call first
        // If no active call exists, create one (server will handle it)
        Log.d(TAG, "initializeGroupCall - callId: " + callId + ", chatId: " + chatId);
        
        if (callId != null && !callId.isEmpty()) {
            // We have a callId, join the existing call
            Log.d(TAG, "callId provided, joining call: " + callId);
            joinGroupCall();
        } else {
            // No callId provided - check for active call first
            // If no active call exists, we'll create one via joinGroupCall
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
        
        // CRITICAL: Always start local video for group video calls
        // This ensures the caller produces video from the start
        startLocalVideo();

        Log.d(TAG, "WebRTC initialized successfully");
    }

    private void createLocalMediaTracks() {
        // Create audio track
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio", audioSource);
        localAudioTrack.setEnabled(!isMuted);

        // Create video capturer and source (camera will be started in startLocalVideo)
        if ("video".equals(getIntent().getStringExtra("callType"))) {
            // CRITICAL: Check camera permission before creating camera capturer
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Cannot create camera capturer - permission not granted");
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                return;
            }
            
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
        
        // CRITICAL: Check permissions before accessing camera
        if (!checkPermissions()) {
            Log.e(TAG, "Cannot start camera - permissions not granted");
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            return;
        }
        
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

    /**
     * Join or create group call - everyone uses the same flow
     * Server will create call if it doesn't exist, or return existing call
     */
    private void startGroupCall() {
        Log.d(TAG, "Joining/Creating group call (unified flow)");
        showLoading("Joining call...");

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
                        
                        // CRITICAL: Everyone follows the same flow - whether call exists or is new
                        // Extract ICE servers
                        extractIceServers(callData);
                        
                        // CRITICAL: Extract SFU info from response (if available)
                        if (json.has("sfu")) {
                            JSONObject sfuData = json.getJSONObject("sfu");
                            sfuRtpCapabilities = sfuData.optJSONObject("rtpCapabilities");
                            Log.d(TAG, "SFU RTP capabilities from response: " + (sfuRtpCapabilities != null));
                        }
                        
                        // Extract SFU room ID from webrtcData
                        if (callData.has("webrtcData")) {
                            JSONObject webrtcData = callData.getJSONObject("webrtcData");
                            sfuRoomId = webrtcData.optString("roomId", null);
                            if (sfuRoomId == null) {
                                String cleanChatId = chatId;
                                if (chatId != null && chatId.startsWith("{")) {
                                    try {
                                        JSONObject chatObj = new JSONObject(chatId);
                                        cleanChatId = chatObj.optString("_id", chatObj.optString("id", chatId));
                                    } catch (Exception e) {
                                        Log.w(TAG, "Could not parse chatId as JSON, using as-is: " + chatId);
                                    }
                                }
                                sfuRoomId = "room_" + cleanChatId;
                            }
                            Log.d(TAG, "SFU room ID: " + sfuRoomId);
                        }
                        
                        // CRITICAL FIX: Don't add participants from API response here
                        // They will be added when socket room_state event is received
                        // This prevents duplicates and race conditions
                        if (callData.has("participants")) {
                            JSONArray participantsArray = callData.getJSONArray("participants");
                            Log.d(TAG, "Found " + participantsArray.length() + " participants in call (will be added via socket room_state)");
                            // Participants will be added when joinCallRoom() receives room_state event
                        }

                        runOnUiThread(() -> {
                            hideLoading();
                            if (isExisting) {
                                Log.d(TAG, "Joined existing group call - callId: " + callId);
                                Toast.makeText(GroupVideoCallActivity.this, "Joined group call", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d(TAG, "Group call created/joined - callId: " + callId);
                                Toast.makeText(GroupVideoCallActivity.this, "Joined call", Toast.LENGTH_SHORT).show();
                            }
                            
                            // CRITICAL: Ensure local participant is added
                            if (participants.isEmpty()) {
                                Log.w(TAG, "No local participant found, adding now");
                                addLocalParticipant();
                            } else {
                                Log.d(TAG, "Local participant already exists");
                            }
                            
                            // CRITICAL: Initialize SFU immediately if we have RTP capabilities
                            // This ensures everyone produces video as soon as possible
                            if (sfuRtpCapabilities != null && sfuRoomId != null) {
                                Log.d(TAG, "Initializing SFU immediately after joining call");
                                initializeSFU();
                            } else {
                                Log.w(TAG, "SFU RTP capabilities or roomId not available yet, will initialize when room_state is received");
                            }
                            
                            // Join socket room for real-time updates
                            joinCallRoom();
                        });

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
        
        // CRITICAL: Prevent multiple simultaneous joins
        if (isJoining) {
            Log.w(TAG, "Already joining call, ignoring duplicate request");
            return;
        }
        
        if (isLeaving) {
            Log.w(TAG, "Currently leaving call, cannot join");
            return;
        }
        
        // Validate callId before attempting to join
        if (callId == null || callId.isEmpty()) {
            Log.e(TAG, "Cannot join call: callId is null or empty");
            Toast.makeText(this, "Invalid call ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        isJoining = true;
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
                isJoining = false; // Reset flag on failure
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
                        
                        // CRITICAL: Extract sessionId and chatId from response
                        sessionId = data.optString("sessionId", null);
                        String returnedChatId = data.optString("chatId", chatId);
                        if (returnedChatId != null && !returnedChatId.isEmpty()) {
                            chatId = returnedChatId;
                        }
                        
                        Log.d(TAG, "Join response - sessionId: " + sessionId + ", chatId: " + chatId);
                        
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
                                
                                // Extract SFU room ID
                                JSONObject webrtcData = callData.getJSONObject("webrtcData");
                                sfuRoomId = webrtcData.optString("roomId", null);
                                if (sfuRoomId == null) {
                                    // Ensure chatId is a string, not an object
                                    String cleanChatId = chatId;
                                    if (chatId != null && chatId.startsWith("{")) {
                                        // chatId is a JSON string, try to parse it
                                        try {
                                            JSONObject chatObj = new JSONObject(chatId);
                                            cleanChatId = chatObj.optString("_id", chatObj.optString("id", chatId));
                                        } catch (Exception e) {
                                            Log.w(TAG, "Could not parse chatId as JSON, using as-is: " + chatId);
                                        }
                                    }
                                    sfuRoomId = "room_" + cleanChatId;
                                }
                                Log.d(TAG, "SFU room ID: " + sfuRoomId + " (from chatId: " + chatId + ")");
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
                        
                        // Extract SFU info if available (from response root, not callData)
                        if (data.has("sfu")) {
                            JSONObject sfuData = data.getJSONObject("sfu");
                            sfuRtpCapabilities = sfuData.optJSONObject("rtpCapabilities");
                            Log.d(TAG, "SFU RTP capabilities received: " + (sfuRtpCapabilities != null));
                        }

                        runOnUiThread(() -> {
                            hideLoading();
                            Log.d(TAG, "Successfully joined call: " + callId + ", sessionId: " + sessionId);
                            Toast.makeText(GroupVideoCallActivity.this, "Joined call successfully", Toast.LENGTH_SHORT).show();
                            
                            // CRITICAL: Initialize SFU immediately if we have RTP capabilities
                            // This ensures B produces video as soon as possible
                            if (sfuRtpCapabilities != null && sfuRoomId != null) {
                                Log.d(TAG, "Initializing SFU immediately after joining call (B - joiner)");
                                initializeSFU();
                            } else {
                                Log.w(TAG, "SFU RTP capabilities or roomId not available yet, will initialize when room_state is received");
                            }
                            
                            // Join socket room for real-time updates
                            joinCallRoom();
                            isJoining = false; // Reset flag after join completes
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
        Log.d(TAG, "Checking for active call in chat: " + chatId);
        String token = databaseManager.getToken();
        apiClient.authenticatedGet("/api/group-calls/chat/" + chatId + "/active", token, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to check for active call", e);
                // If check fails and we're the caller, start new call
                // Otherwise, show error and finish
                // On failure, try to create/join call (server will handle it)
                runOnUiThread(() -> {
                    Log.d(TAG, "Check failed, creating/joining call");
                    startGroupCall();
                });
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
                                Log.d(TAG, "Found active call: " + existingCallId + ", joining");
                                callId = existingCallId;
                                runOnUiThread(() -> joinGroupCall());
                                return;
                            }
                        }
                        
                        // No active call found - create/join one (server will handle creation)
                        Log.d(TAG, "No active call found, creating/joining call");
                        runOnUiThread(() -> startGroupCall());
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing active call response", e);
                        e.printStackTrace();
                        // On error, try to create/join call (server will handle it)
                        runOnUiThread(() -> startGroupCall());
                    }
                } else {
                    // HTTP error - try to create/join call (server will handle it)
                    Log.e(TAG, "HTTP error checking for active call: " + response.code());
                    runOnUiThread(() -> startGroupCall());
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
        Log.d(TAG, "Joining call room via socket - callId: " + callId + ", chatId: " + chatId + ", sessionId: " + sessionId);
        
        // CRITICAL FIX: Join both the call room and the chat-based room
        // This ensures we receive all signalling messages regardless of which room they're sent to
        socketManager.joinGroupCallRoom(callId);
        
        // Also join the chat-based room (group_call_${chatId}) for proper routing
        if (chatId != null && !chatId.isEmpty()) {
            // Ensure chatId is a string, not an object
            String cleanChatId = chatId;
            if (chatId.startsWith("{")) {
                // chatId is a JSON string, try to parse it
                try {
                    JSONObject chatObj = new JSONObject(chatId);
                    cleanChatId = chatObj.optString("_id", chatObj.optString("id", chatId));
                    Log.d(TAG, "Extracted chatId from JSON object: " + cleanChatId);
                } catch (Exception e) {
                    Log.w(TAG, "Could not parse chatId as JSON, using as-is: " + chatId);
                }
            }
            socketManager.joinGroupCall(cleanChatId, callId, sessionId);
            Log.d(TAG, "✓ Joined chat-based socket room: group_call_" + cleanChatId);
        } else {
            Log.w(TAG, "⚠️ Cannot join chat-based room: chatId is null or empty");
        }
        
        // CRITICAL: Log all peer connections state after joining
        Log.d(TAG, "After joining rooms - SFU will handle connections" + 
              ", participants count: " + participants.size());
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

    // All mesh-related methods removed - SFU only

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
        
        // Create participant JSON from user object
        try {
            JSONObject participantJson = new JSONObject();
            participantJson.put("userId", joinedUserId);
            participantJson.put("username", userObj.optString("username", "Unknown"));
            participantJson.put("status", "connected");
            
            Log.d(TAG, "New participant joined: " + joinedUserId + " - SFU will handle media");
            
            // Add participant to list (SFU will handle media via producers/consumers)
            addRemoteParticipantForSFU(participantJson);
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
        
        // CRITICAL: Force immediate update of video track in adapter
        // Use multiple strategies to ensure video is rendered
        // CRITICAL: Create final variables for use in lambda expressions
        final int finalParticipantIndex = participantIndex;
        final CallParticipant finalTargetParticipant = targetParticipant;
        final String finalUserId = userId;
        
        if (participantAdapter != null) {
            // Strategy 1: Notify adapter to rebind this participant's view
            if (finalParticipantIndex >= 0 && finalParticipantIndex < participants.size()) {
                participantAdapter.notifyItemChanged(finalParticipantIndex);
                Log.d(TAG, "✓ Notified adapter to rebind participant at index " + finalParticipantIndex);
            } else {
                // If index is invalid, use updateParticipantVideoTrack which will find the index
                participantAdapter.updateParticipantVideoTrack(finalUserId);
                Log.d(TAG, "✓ Called updateParticipantVideoTrack for " + finalUserId);
            }
            
            // Strategy 2: Force update after a short delay to handle timing issues
            // This ensures video track is added even if surface wasn't ready initially
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (participantAdapter != null && finalTargetParticipant != null && 
                    finalTargetParticipant.getVideoTrack() != null && 
                    finalTargetParticipant.getVideoTrack().enabled()) {
                    // Try to update again after delay
                    if (finalParticipantIndex >= 0 && finalParticipantIndex < participants.size()) {
                        participantAdapter.notifyItemChanged(finalParticipantIndex);
                        Log.d(TAG, "✓ Delayed rebind for participant at index " + finalParticipantIndex);
                    } else {
                        participantAdapter.updateParticipantVideoTrack(finalUserId);
                        Log.d(TAG, "✓ Delayed updateParticipantVideoTrack for " + finalUserId);
                    }
                }
            }, 300); // 300ms delay to ensure surface is ready
            
            // Strategy 3: Additional retry after longer delay (for slow devices)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (participantAdapter != null && finalTargetParticipant != null && 
                    finalTargetParticipant.getVideoTrack() != null && 
                    finalTargetParticipant.getVideoTrack().enabled()) {
                    if (finalParticipantIndex >= 0 && finalParticipantIndex < participants.size()) {
                        participantAdapter.notifyItemChanged(finalParticipantIndex);
                        Log.d(TAG, "✓ Second delayed rebind for participant at index " + finalParticipantIndex);
                    }
                }
            }, 800); // 800ms delay for slow devices
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

    /**
     * Initialize SFU connection
     */
    private void initializeSFU() {
        if (sfuManager != null) {
            Log.w(TAG, "SFU already initialized");
            return;
        }
        
        if (sfuRoomId == null) {
            // Ensure chatId is a string, not an object
            String cleanChatId = chatId;
            if (chatId != null && chatId.startsWith("{")) {
                // chatId is a JSON string, try to parse it
                try {
                    JSONObject chatObj = new JSONObject(chatId);
                    cleanChatId = chatObj.optString("_id", chatObj.optString("id", chatId));
                } catch (Exception e) {
                    Log.w(TAG, "Could not parse chatId as JSON, using as-is: " + chatId);
                }
            }
            sfuRoomId = "room_" + cleanChatId;
            Log.d(TAG, "Created SFU room ID: " + sfuRoomId + " (from chatId: " + chatId + ")");
        }
        
        if (peerConnectionFactory == null || eglBase == null) {
            Log.e(TAG, "Cannot initialize SFU - WebRTC not initialized");
            return;
        }
        
        Log.d(TAG, "Initializing SFU for room: " + sfuRoomId);
        
        sfuManager = new SFUManager(sfuRoomId, currentUserId, socketManager, peerConnectionFactory, eglBase);
        sfuManager.setListener(new SFUManager.SFUListener() {
            @Override
            public void onTransportCreated(String direction, JSONObject transport) {
                Log.d(TAG, "SFU transport created: " + direction);
                // Handle transport creation - will be used to create PeerConnection
            }

            @Override
            public void onTransportConnected(String direction) {
                Log.d(TAG, "SFU transport connected callback received: " + direction);
                
                // Once send transport is connected, produce local tracks
                if ("send".equals(direction)) {
                    Log.d(TAG, "Send transport connected! Checking local tracks...");
                    Log.d(TAG, "localAudioTrack: " + (localAudioTrack != null ? "exists" : "NULL"));
                    Log.d(TAG, "localVideoTrack: " + (localVideoTrack != null ? "exists" : "NULL"));
                    Log.d(TAG, "isCameraOn: " + isCameraOn);
                    
                    // Add a small delay to ensure sendTransport is fully set
                    runOnUiThread(() -> {
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            Log.d(TAG, "Send transport connected, producing media. Audio: " + (localAudioTrack != null) + ", Video: " + (localVideoTrack != null && isCameraOn));
                            
                            // CRITICAL: Produce VIDEO first, then audio
                            // This ensures video producer is created first
                            if (localVideoTrack != null) {
                                // Ensure video track is enabled if camera is on
                                if (isCameraOn && !localVideoTrack.enabled()) {
                                    localVideoTrack.setEnabled(true);
                                    Log.d(TAG, "Enabled video track before producing");
                                }
                                
                                Log.d(TAG, "🎥 Producing VIDEO track FIRST... (isCameraOn: " + isCameraOn + ", enabled: " + localVideoTrack.enabled() + ")");
                                try {
                                    sfuManager.produceVideo(localVideoTrack);
                                    Log.d(TAG, "✓ Video track produced successfully");
                                } catch (Exception e) {
                                    Log.e(TAG, "Error producing video", e);
                                    e.printStackTrace();
                                }
                            } else {
                                Log.w(TAG, "Cannot produce video - localVideoTrack is null");
                                // Try to start camera and create video track if it doesn't exist
                                if (videoCapturer == null || videoSource == null) {
                                    Log.w(TAG, "Video components not ready, attempting to create them...");
                                    createLocalMediaTracks();
                                    // Retry producing after a delay
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        if (localVideoTrack != null && sfuManager != null) {
                                            try {
                                                Log.d(TAG, "Retrying video production after creating tracks");
                                                sfuManager.produceVideo(localVideoTrack);
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error retrying video production", e);
                                            }
                                        }
                                    }, 500);
                                }
                            }
                            
                            // Produce audio AFTER video
                            if (localAudioTrack != null) {
                                Log.d(TAG, "🔊 Producing audio track...");
                                try {
                                    sfuManager.produceAudio(localAudioTrack);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error producing audio", e);
                                    e.printStackTrace();
                                }
                            } else {
                                Log.w(TAG, "Cannot produce audio - localAudioTrack is null");
                            }
                        }, 200); // 200ms delay to ensure sendTransport is set
                    });
                } else {
                    Log.d(TAG, "Receive transport connected (direction: " + direction + ")");
                }
            }

            @Override
            public void onProducerCreated(String kind, String producerId) {
                Log.d(TAG, "SFU producer created: " + kind + ", id: " + producerId);
            }

            @Override
            public void onConsumerCreated(String producerId, String consumerId, JSONObject rtpParameters) {
                Log.d(TAG, "SFU consumer created: producerId=" + producerId + ", consumerId=" + consumerId);
                
                // CRITICAL: Extract track from consumer and assign to participant
                // The track should be available from the receive transport's onAddTrack callback
                // For now, we'll wait for the track to be received via onAddTrack
                // The track will be associated with the producerId, which maps to a participant
                
                // Store consumer info for later track assignment
                // The actual track will come via onAddTrack callback from receive transport
                Log.d(TAG, "Consumer created - waiting for track via onAddTrack callback");
            }

            @Override
            public void onNewProducer(String producerPeerId, String producerId, String kind) {
                Log.d(TAG, "═══════════════════════════════════════════════════════════");
                Log.d(TAG, "🎬 onNewProducer CALLED - peerId=" + producerPeerId + ", producerId=" + producerId + ", kind=" + kind);
                Log.d(TAG, "Current state - sfuManager: " + (sfuManager != null) + ", sfuRtpCapabilities: " + (sfuRtpCapabilities != null));
                
                // CRITICAL: Only consume VIDEO producers for now
                // Audio will be handled automatically by WebRTC
                if (!"video".equals(kind)) {
                    Log.d(TAG, "⏭️ Skipping non-video producer (kind: " + kind + ") - only consuming video");
                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                    return;
                }
                
                // Store mapping: producerPeerId (userId) -> producerId
                // This will be used to assign tracks to participants
                // CRITICAL: Only map video producers
                if (sfuManager != null) {
                    sfuManager.setProducerIdMapping(producerPeerId, producerId);
                    Log.d(TAG, "✓ Mapped video producerId " + producerId + " to userId " + producerPeerId);
                } else {
                    Log.e(TAG, "✗ SFU manager is null, cannot set mapping - will retry when SFU is initialized");
                    // Queue this producer to consume later when SFU is ready
                    // This can happen if producer is received before SFU initialization completes
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (sfuManager != null && sfuRtpCapabilities != null) {
                            Log.d(TAG, "Retrying onNewProducer after SFU initialization - producerId: " + producerId);
                            sfuManager.setProducerIdMapping(producerPeerId, producerId);
                            sfuManager.consume(producerId, sfuRtpCapabilities);
                        } else {
                            Log.e(TAG, "✗ SFU still not ready after retry for producerId: " + producerId);
                        }
                    }, 1000);
                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                    return;
                }
                
                // Consume this VIDEO producer
                if (sfuRtpCapabilities != null && sfuManager != null) {
                    Log.d(TAG, "✅ Consuming VIDEO producer: " + producerId);
                    sfuManager.consume(producerId, sfuRtpCapabilities);
                } else {
                    Log.w(TAG, "⚠️ Cannot consume producer - RTP capabilities: " + (sfuRtpCapabilities != null) + ", SFU manager: " + (sfuManager != null));
                    // Retry after a delay if RTP capabilities become available
                    if (sfuManager != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (sfuRtpCapabilities != null) {
                                Log.d(TAG, "Retrying consume after RTP capabilities available - producerId: " + producerId);
                                sfuManager.consume(producerId, sfuRtpCapabilities);
                            }
                        }, 1000);
                    }
                }
                Log.d(TAG, "═══════════════════════════════════════════════════════════");
            }

            @Override
            public void onRemoteTrack(String producerId, MediaStreamTrack track) {
                Log.d(TAG, "═══════════════════════════════════════════════════════════");
                Log.d(TAG, "SFU remote track received: producerId=" + producerId + ", kind=" + (track != null ? track.kind() : "null"));
                
                if (track == null) {
                    Log.e(TAG, "Received null track from producer: " + producerId);
                    return;
                }
                
                // Map producerId to userId
                String userId = sfuManager != null ? sfuManager.getUserIdForProducerId(producerId) : null;
                Log.d(TAG, "ProducerId to userId mapping: producerId=" + producerId + " -> userId=" + userId);
                
                if (userId == null) {
                    Log.w(TAG, "⚠️ Cannot find userId for producerId: " + producerId);
                    Log.w(TAG, "Available participants: " + participants.size());
                    for (CallParticipant p : participants) {
                        Log.w(TAG, "  - Participant: userId=" + p.getUserId() + ", hasVideoTrack=" + (p.getVideoTrack() != null));
                    }
                }
                
                if (track.kind().equals("video")) {
                    VideoTrack videoTrack = (VideoTrack) track;
                    Log.d(TAG, "Video track details: enabled=" + videoTrack.enabled() + ", state=" + videoTrack.state() + ", id=" + videoTrack.id());
                    
                    runOnUiThread(() -> {
                        Log.d(TAG, "Processing video track on UI thread - producerId: " + producerId + ", userId: " + userId);
                        
                        // CRITICAL: Check if this track is already assigned to avoid duplicate processing
                        if (userId != null) {
                            // Find participant
                            CallParticipant targetParticipant = null;
                            for (CallParticipant p : participants) {
                                if (p.getUserId() != null && p.getUserId().equals(userId)) {
                                    targetParticipant = p;
                                    break;
                                }
                            }
                            
                            // Only process if participant doesn't have this exact track already
                            if (targetParticipant != null) {
                                VideoTrack existingTrack = targetParticipant.getVideoTrack();
                                if (existingTrack != null && existingTrack.id().equals(videoTrack.id())) {
                                    Log.d(TAG, "⚠️ Video track already assigned to participant " + userId + ", skipping duplicate assignment");
                                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                                    return;
                                }
                            }
                        }
                        
                        // Find participant and assign video track
                        if (userId != null) {
                            Log.d(TAG, "✓ Calling handleRemoteVideoTrack with userId: " + userId);
                            handleRemoteVideoTrack(userId, videoTrack);
                        } else {
                            Log.w(TAG, "⚠️ userId is null, trying fallback assignment");
                            // Fallback: try to find participant by checking all remote participants
                            boolean assigned = false;
                            for (CallParticipant participant : participants) {
                                if (participant.getUserId() != null && 
                                    !participant.getUserId().equals(currentUserId) &&
                                    participant.getVideoTrack() == null) {
                                    Log.d(TAG, "✓ Fallback: Assigning video track to participant: " + participant.getUserId());
                                    handleRemoteVideoTrack(participant.getUserId(), videoTrack);
                                    assigned = true;
                                    break;
                                }
                            }
                            if (!assigned) {
                                Log.e(TAG, "✗✗✗ Failed to assign video track - no suitable participant found");
                            }
                        }
                        Log.d(TAG, "═══════════════════════════════════════════════════════════");
                    });
                } else if (track.kind().equals("audio")) {
                    Log.d(TAG, "Audio track received from producer: " + producerId);
                    // Audio tracks are handled automatically by WebRTC
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "SFU error: " + message);
                runOnUiThread(() -> {
                    Toast.makeText(GroupVideoCallActivity.this, "SFU error: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
        
        sfuManager.initialize();
    }

    /**
     * Add remote participant for SFU (just add to list, don't create peer connection)
     */
    private void addRemoteParticipantForSFU(JSONObject participantJson) {
        try {
            String userId = participantJson.getString("userId");
            String username = participantJson.optString("username", "Unknown");
            String avatar = participantJson.optString("avatar", "");
            
            // Skip if already added
            synchronized (addedParticipantIds) {
                if (addedParticipantIds.contains(userId)) {
                    Log.d(TAG, "Participant " + userId + " already added for SFU");
                    return;
                }
                addedParticipantIds.add(userId);
            }
            
            // Create participant object
            CallParticipant participant = new CallParticipant();
            participant.setUserId(userId);
            participant.setUsername(username);
            participant.setAvatar(avatar);
            participant.setLocal(false);
            participant.setVideoMuted(true);
            participant.setAudioMuted(false);
            
            // Add to participants list
            runOnUiThread(() -> {
                participants.add(participant);
                
                // Update UI
                if (participantAdapter != null) {
                    participantAdapter.notifyItemInserted(participants.size() - 1);
                    updateParticipantCount();
                }
            });
            
            Log.d(TAG, "Added remote participant for SFU: " + username + " (" + userId + ")");
        } catch (JSONException e) {
            Log.e(TAG, "Error adding remote participant for SFU", e);
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
                    
                    Log.d(TAG, "Received room state - callId: " + roomCallId + ", chatId: " + roomChatId + 
                          ", participants: " + (participantsArray != null ? participantsArray.length() : 0));
                    
                    // Extract SFU info if available
                    if (data.has("sfu")) {
                        JSONObject sfuData = data.getJSONObject("sfu");
                        sfuRtpCapabilities = sfuData.optJSONObject("rtpCapabilities");
                        Log.d(TAG, "SFU RTP capabilities from room state: " + (sfuRtpCapabilities != null));
                    }
                    
                    // CRITICAL: Update callId if it's different (shouldn't happen, but handle it)
                    if (roomCallId != null && !roomCallId.isEmpty() && !roomCallId.equals(callId)) {
                        Log.w(TAG, "Room state has different callId: " + roomCallId + " vs " + callId + ", updating to match room");
                        callId = roomCallId;
                    }
                    
                    // CRITICAL: Initialize SFU (if not already initialized)
                    // This handles the case where SFU wasn't initialized from API response
                    if (sfuManager == null) {
                        Log.d(TAG, "Initializing SFU from room_state event");
                        initializeSFU();
                    } else {
                        Log.d(TAG, "SFU already initialized, skipping");
                    }
                    
                    // Add all existing participants (SFU will handle media via producers/consumers)
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
                            
                            // Add participant to list (SFU will handle media)
                            addRemoteParticipantForSFU(participantJson);
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
            // SFU will handle new producers via sfu-new-producer event
            // Just add participant to list
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

        // SFU handles all signaling - no need for mesh offer/answer/ice handlers

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

    // All mesh methods removed - SFU only

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
        
        // SFU handles connection cleanup automatically
        
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
        
        // CRITICAL: Check camera permission before accessing camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot toggle camera - permission not granted");
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            requestPermissions();
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
        // CRITICAL: Check camera permission before switching camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot switch camera - permission not granted");
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            return;
        }
        
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
        // CRITICAL: Prevent operations during leave
        if (isLeaving) {
            Log.w(TAG, "Already leaving call, ignoring duplicate request");
            return;
        }
        
        isLeaving = true;
        isJoining = false; // Cancel any pending join
        
        Log.d(TAG, "Leaving call - cleaning up resources");
        
        // Cleanup SFU resources
        if (sfuManager != null) {
            sfuManager.cleanup();
            sfuManager = null;
        }
        
        // Small delay to ensure cleanup completes
        new android.os.Handler().postDelayed(() -> {
            // CRITICAL: Leave socket room before leaving call
            if (socketManager != null && chatId != null && !chatId.isEmpty()) {
                socketManager.leaveGroupCall(chatId);
            }
            
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
        }, 300); // 300ms delay to ensure cleanup completes
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
        Log.d(TAG, "onPause: Disabling video track (camera will be stopped in onDestroy)");
        
        // CRITICAL: Don't stop camera capture in onPause() - it causes thread errors
        // Just disable the video track to stop rendering
        // Camera will be properly stopped in onDestroy()
        if (localVideoTrack != null && isCameraOn) {
            try {
                localVideoTrack.setEnabled(false);
                Log.d(TAG, "Video track disabled in onPause");
            } catch (Exception e) {
                Log.w(TAG, "Error disabling video track in onPause: " + e.getMessage());
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy: Cleaning up resources");
        
        // CRITICAL: Leave socket room when activity is destroyed
        if (socketManager != null && chatId != null && !chatId.isEmpty()) {
            socketManager.leaveGroupCall(chatId);
        }

        // Stop call timer
        if (callTimer != null) {
            callTimer.cancel();
            callTimer = null;
        }

        // Clean up WebRTC resources (exactly like private call does)
        cleanupWebRTC();
        
        // Clean up SFU resources
        if (sfuManager != null) {
            sfuManager.cleanup();
            sfuManager = null;
        }

        // Remove socket listeners
        if (socketManager != null) {
            socketManager.off("group_call_room_state");
            socketManager.off("group_call_participant_joined");
            socketManager.off("group_call_user_joined_room");
            socketManager.off("group_call_participant_left");
            // Mesh socket listeners removed - SFU only
            socketManager.off("participant_media_updated");
            socketManager.off("call_ended");
        }
        
        // Clear tracking set
        synchronized (addedParticipantIds) {
            addedParticipantIds.clear();
        }
        
        Log.d(TAG, "onDestroy: Cleanup complete");
    }
    
    // cleanupAllPeerConnections removed - SFU handles cleanup automatically
    
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

            // STEP 2: SFU handles connection cleanup
            // No need to manually close peer connections for mesh

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
    
    // logAllPeerConnectionStates removed - SFU only, no mesh peer connections
}

