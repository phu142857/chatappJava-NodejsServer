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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.utils.SharedPreferencesManager;
import com.squareup.picasso.Picasso;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class VideoCallActivity extends AppCompatActivity implements SocketManager.WebRTCListener {

    private static final String TAG = "VideoCallActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_MICROPHONE_PERMISSION = 1002;

    // UI Components
    private TextView tvCallStatus;
    private LinearLayout remoteVideoPlaceholder;
    private LinearLayout localVideoPlaceholder;
    private ImageButton btnMute;
    private ImageButton btnEndCall;
    private ImageButton btnCameraToggle;
    private ImageButton btnSwitchCamera;

    private User caller;
    private String callId;
    private String callType;
    private boolean isIncomingCall;
    private boolean isCallActive = false;
    private boolean isMuted = false;
    private boolean isCameraOn = true;

    // Network
    private SocketManager socketManager;

    // WebRTC components
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private EglBase eglBase;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private MediaStream localStream;
    // Remote description and ICE queueing helpers
    private boolean remoteDescriptionSet = false;
    private final java.util.List<IceCandidate> pendingRemoteCandidates = new java.util.ArrayList<>();
    // Server-provided ICE servers waiting for factory init
    private org.json.JSONArray pendingIceServersJson;
    
    // Synchronization state management
    private boolean isWebRTCInitialized = false;
    private boolean isPeerConnectionReady = false;
    private boolean isOfferSent = false;
    private boolean isAnswerSent = false;
    private boolean isEndingCall = false;
    private final Object syncLock = new Object();

    // Call duration
    private Timer callDurationTimer;
    private int callDurationSeconds = 0;

    // Managers
    private SharedPreferencesManager sharedPrefsManager;
    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during call
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_call);

        // Initialize managers
        sharedPrefsManager = new SharedPreferencesManager(this);
        apiClient = new ApiClient();
        socketManager = SocketManager.getInstance();

        // Get call data from intent
        getCallDataFromIntent();

        // Setup SocketManager for call synchronization
        setupSocketManager();

        // Initialize UI
        initializeViews();
        setupClickListeners();

        // Check permissions
        checkPermissions();

        // Setup call based on type
        if (isIncomingCall) {
            setupIncomingCall();
        } else {
            setupOutgoingCall();
        }
    }

    private void getCallDataFromIntent() {
        try {
            String chatJson = getIntent().getStringExtra("chat");
            if (chatJson != null) {
                // We don't need full chat object here, just caller info
                JSONObject chatObj = new JSONObject(chatJson);
                if (chatObj.has("lastMessage") && chatObj.get("lastMessage") instanceof JSONObject) {
                    JSONObject lastMessage = chatObj.getJSONObject("lastMessage");
                    if (lastMessage.has("sender") && lastMessage.get("sender") instanceof JSONObject) {
                        caller = User.fromJson(lastMessage.getJSONObject("sender"));
                    }
                }
            }

            String callerJson = getIntent().getStringExtra("caller");
            if (callerJson != null) {
                caller = User.fromJson(new JSONObject(callerJson));
            }

            callId = getIntent().getStringExtra("callId");
            callType = getIntent().getStringExtra("callType");
            isIncomingCall = getIntent().getBooleanExtra("isIncomingCall", false);
            Log.d(TAG, "Call data - isIncomingCall: " + isIncomingCall + ", callId: " + callId + ", callType: " + callType);

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing call data", e);
            Toast.makeText(this, "Error loading call data", Toast.LENGTH_SHORT).show();
            navigateToHome();
        }
    }

    private void setupSocketManager() {
        if (socketManager != null) {
            // Join call room for real-time communication
            socketManager.joinCallRoom(callId);

            // Set up WebRTC listener
            socketManager.setWebRTCListener(this);
            
            // Receive ICE servers from server with synchronization
            socketManager.setCallRoomListener((joinedCallId, iceServersJson) -> {
                if (callId != null && callId.equals(joinedCallId)) {
                    Log.d(TAG, "Received ICE servers for call: " + joinedCallId);
                    if (iceServersJson != null) {
                        synchronized (syncLock) {
                            if (peerConnectionFactory == null) {
                                pendingIceServersJson = iceServersJson;
                                Log.d(TAG, "Deferred ICE servers usage until factory init");
                            } else if (peerConnection == null) {
                                createPeerConnectionWithIceServers(iceServersJson);
                            }
                        }
                    }
                }
            });

            // Set up call status listener for call ending synchronization
            socketManager.setCallStatusListener(new SocketManager.CallStatusListener() {
                @Override
                public void onCallAccepted(String callId) {
                    // Not needed in VideoCallActivity
                }

                @Override
                public void onCallDeclined(String callId) {
                    // Not needed in VideoCallActivity
                }

                @Override
                public void onCallEnded(String callId) {
                    runOnUiThread(() -> {
                        if (VideoCallActivity.this.callId != null && VideoCallActivity.this.callId.equals(callId)) {
                            Log.d(TAG, "Call ended by other participant");
                            Toast.makeText(VideoCallActivity.this, "Call ended", Toast.LENGTH_SHORT).show();
                            navigateToHome();
                        }
                    });
                }
            });
        }
    }

    @SuppressLint("SetTextI18n")
    private void initializeViews() {
        // Status and info views
        tvCallStatus = findViewById(R.id.tv_call_status);
        CircleImageView ivRemoteAvatar = findViewById(R.id.iv_remote_avatar);
        TextView tvRemoteName = findViewById(R.id.tv_remote_name);

        // Video views
        localVideoView = findViewById(R.id.sv_local_video);
        remoteVideoView = findViewById(R.id.sv_remote_video);
        remoteVideoPlaceholder = findViewById(R.id.remote_video_placeholder);
        localVideoPlaceholder = findViewById(R.id.local_video_placeholder);

        // Control buttons
        btnMute = findViewById(R.id.btn_mute);
        btnEndCall = findViewById(R.id.btn_end_call);
        btnCameraToggle = findViewById(R.id.btn_camera_toggle);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);


        // Initialize WebRTC
        initializeWebRTC();

        // Set caller information
        if (caller != null) {
            tvRemoteName.setText(caller.getUsername());
            tvCallStatus.setText("Connecting...");

            // Load avatar
            String avatarUrl = caller.getFullAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(ivRemoteAvatar);
            }
        }

        // Set call type
        if ("audio".equals(callType)) {
            localVideoView.setVisibility(View.GONE);
            localVideoPlaceholder.setVisibility(View.GONE);
            remoteVideoView.setVisibility(View.GONE);
            btnCameraToggle.setVisibility(View.GONE);
            btnSwitchCamera.setVisibility(View.GONE);
        } else {
            // For video calls, show local video placeholder initially
            isCameraOn = true; // Ensure camera is on for video calls
            localVideoView.setVisibility(View.GONE);
            localVideoPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("SetTextI18n")
    private void initializeWebRTC() {
        synchronized (syncLock) {
            Log.d(TAG, "Initializing WebRTC");

            // Check if already initialized
            if (isWebRTCInitialized) {
                Log.d(TAG, "WebRTC already initialized, skipping");
                return;
            }
        }

        // Initialize EGL base
        eglBase = EglBase.create();

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

        // Initialize video views only if not already initialized
        try {
            localVideoView.init(eglBase.getEglBaseContext(), null);
            localVideoView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            localVideoView.setMirror(true);
        } catch (IllegalStateException e) {
            Log.d(TAG, "Local video view already initialized: " + e.getMessage());
        }

        try {
            remoteVideoView.init(eglBase.getEglBaseContext(), null);
            remoteVideoView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        } catch (IllegalStateException e) {
            Log.d(TAG, "Remote video view already initialized: " + e.getMessage());
        }

        // Start local video capture
        startLocalVideo();

        // Prefer server ICE servers if already received
        if (peerConnection == null && pendingIceServersJson != null) {
            createPeerConnectionWithIceServers(pendingIceServersJson);
            pendingIceServersJson = null;
        }
        // Fallback to default STUN if still no peerConnection
        if (peerConnection == null) {
            createPeerConnection();
        }

        // Start WebRTC signaling based on call type
        if (isIncomingCall) {
            // Callee: wait for offer, but also request offer if missed
            Log.d(TAG, "Waiting for offer from caller");
            runOnUiThread(() -> tvCallStatus.setText("Waiting for offer..."));

            // Request offer from caller in case it was already sent
            new Handler(Looper.getMainLooper()).postDelayed(this::requestOfferFromCaller, 2000); // Wait 2 seconds then request offer
        } else {
            // Caller: create offer
            Log.d(TAG, "Creating offer as caller");
            runOnUiThread(() -> tvCallStatus.setText("Initiating call..."));
            new Handler(Looper.getMainLooper()).postDelayed(this::createOffer, 1000); // Small delay to ensure peer connection is ready
        }

        Log.d(TAG, "WebRTC initialized successfully");
        
        synchronized (syncLock) {
            isWebRTCInitialized = true;
        }
    }

    @SuppressLint("SetTextI18n")
    private void requestOfferFromCaller() {
        Log.d(TAG, "Requesting offer from caller");
        runOnUiThread(() -> tvCallStatus.setText("Requesting offer from caller..."));

        // Send request to caller via Socket.io
        if (socketManager != null) {
            try {
                JSONObject request = new JSONObject();
                request.put("callId", callId);
                request.put("type", "request_offer");

                // Use existing WebRTC offer method to send request
                socketManager.sendWebRTCOffer(callId, request);
                Log.d(TAG, "Sent offer request to caller");
            } catch (JSONException e) {
                Log.e(TAG, "Error sending offer request", e);
            }
        }
    }

    private void startLocalVideo() {
        Log.d(TAG, "Starting local video capture");

        // Check if already started
        if (videoCapturer != null) {
            Log.d(TAG, "Local video already started, skipping");
            return;
        }

        // Create video capturer
        CameraEnumerator enumerator = new Camera2Enumerator(this);
        String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
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
                        // Show local video when first frame is available
                        runOnUiThread(() -> {
                            if (localVideoView != null && localVideoPlaceholder != null) {
                                localVideoView.setVisibility(View.VISIBLE);
                                localVideoPlaceholder.setVisibility(View.GONE);
                                Log.d(TAG, "Local video now visible");
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

        if (videoCapturer == null) {
            Log.e(TAG, "No front camera found");
            return;
        }

        // Create video source
        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());

        // Create video track
        localVideoTrack = peerConnectionFactory.createVideoTrack("local_video", videoSource);
        localVideoTrack.addSink(localVideoView);

        // Start capture
        videoCapturer.initialize(org.webrtc.SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext()),
                this, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);

        // Create audio source and track
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio", audioSource);

        // Create local stream
        localStream = peerConnectionFactory.createLocalMediaStream("local_stream");
        localStream.addTrack(localVideoTrack);
        localStream.addTrack(localAudioTrack);

        Log.d(TAG, "Local video started successfully");

        // Ensure local video is visible after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isCameraOn && localVideoView != null && localVideoPlaceholder != null) {
                // Check if video track is enabled
                if (localVideoTrack != null && localVideoTrack.enabled()) {
                    localVideoView.setVisibility(View.VISIBLE);
                    localVideoPlaceholder.setVisibility(View.GONE);
                    Log.d(TAG, "Local video made visible after delay");
                }
            }
        }, 1000); // Wait 1 second for camera to be ready
    }

    private void createPeerConnection() {
        Log.d(TAG, "Creating peer connection");

        // Check if already created
        if (peerConnection != null) {
            Log.d(TAG, "Peer connection already created, skipping");
            return;
        }

        // ICE servers configuration
        java.util.List<PeerConnection.IceServer> iceServers = new java.util.ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        // PeerConnection configuration
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Use default (ALL) here; RELAY will be enforced when TURN is available from server

        // Create peer connection
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "Signaling state changed: " + signalingState);
                runOnUiThread(() -> {
                    if (signalingState == PeerConnection.SignalingState.STABLE) {
                        Log.d(TAG, "Signaling state is STABLE - connection should be ready");
                    } else if (signalingState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                        Log.d(TAG, "Signaling state is HAVE_LOCAL_OFFER - waiting for answer");
                    } else if (signalingState == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                        Log.d(TAG, "Signaling state is HAVE_REMOTE_OFFER - will create answer");
                    } else if (signalingState == PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER) {
                        Log.d(TAG, "Signaling state is HAVE_LOCAL_ANSWER - received answer");
                    } else if (signalingState == PeerConnection.SignalingState.HAVE_REMOTE_PRANSWER) {
                        Log.d(TAG, "Signaling state is HAVE_REMOTE_ANSWER - sent answer");
                    }
                });
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "ICE connection state changed: " + iceConnectionState);
                runOnUiThread(() -> {
                    if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                        tvCallStatus.setText("Connected - waiting for remote stream...");
                        Log.d(TAG, "ICE connection established - should receive remote stream soon");
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        tvCallStatus.setText("Disconnected");
                        Log.w(TAG, "ICE connection disconnected");
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.CHECKING) {
                        tvCallStatus.setText("Connecting...");
                        Log.d(TAG, "ICE connection checking...");
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                        tvCallStatus.setText("Connection failed");
                        Log.e(TAG, "ICE connection failed");
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.NEW) {
                        Log.d(TAG, "ICE connection new");
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                        Log.d(TAG, "ICE connection closed");
                    }
                });
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "ICE connection receiving change: " + receiving);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "ICE gathering state changed: " + iceGatheringState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "ICE candidate: " + iceCandidate);
                // Send ICE candidate to remote peer via Socket.io
                sendIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "ICE candidates removed");
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "Remote stream added - video tracks: " + mediaStream.videoTracks.size() + ", audio tracks: " + mediaStream.audioTracks.size());
                runOnUiThread(() -> {
                    if (!mediaStream.videoTracks.isEmpty()) {
                        VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                        remoteVideoTrack.addSink(remoteVideoView);
                        remoteVideoPlaceholder.setVisibility(View.GONE);
                        tvCallStatus.setText("Remote video connected!");
                        Log.d(TAG, "Remote video track added to view");
                    } else {
                        Log.w(TAG, "No video tracks in remote stream");
                    }
                });
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "Remote stream removed");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "Data channel created");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed");
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "Track added");
            }
        });

        // Add local stream to peer connection
        if (localStream != null) {
            assert peerConnection != null;
            peerConnection.addStream(localStream);
        }

        Log.d(TAG, "Peer connection created successfully");
    }

    private void createPeerConnectionWithIceServers(org.json.JSONArray iceServersJson) {
        synchronized (syncLock) {
            if (isPeerConnectionReady) {
                Log.d(TAG, "Peer connection already ready, skipping");
                return;
            }
        }
        
        Log.d(TAG, "Creating peer connection with server ICE servers");
        if (peerConnection != null) return;
        if (peerConnectionFactory == null) {
            Log.w(TAG, "PeerConnectionFactory not initialized yet; deferring peer creation");
            pendingIceServersJson = iceServersJson;
            return;
        }
        java.util.List<PeerConnection.IceServer> iceServers = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < iceServersJson.length(); i++) {
                org.json.JSONObject s = iceServersJson.getJSONObject(i);

                // Read and validate fields
                String urls = s.optString("urls", null);
                String username = s.optString("username", null);
                String credential = s.optString("credential", null);

                if (urls == null || urls.isEmpty()) {
                    Log.w(TAG, "Skipping ICE server with empty urls at index " + i);
                    continue;
                }

                PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);

                // Only set auth when both username and credential are present (WebRTC throws on null)
                if (username != null && !username.isEmpty() && credential != null && !credential.isEmpty()) {
                    builder.setUsername(username);
                    builder.setPassword(credential);
                }

                iceServers.add(builder.createIceServer());
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error parsing ICE servers JSON", e);
        }

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "Signaling state changed: " + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "ICE state: " + iceConnectionState);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                sendIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onAddStream(MediaStream mediaStream) {
                runOnUiThread(() -> {
                    if (!mediaStream.videoTracks.isEmpty()) {
                        mediaStream.videoTracks.get(0).addSink(remoteVideoView);
                        remoteVideoPlaceholder.setVisibility(View.GONE);
                        tvCallStatus.setText("Remote video connected!");
                    }
                });
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
            }

            @Override
            public void onDataChannel(org.webrtc.DataChannel dataChannel) {
            }

            @Override
            public void onRenegotiationNeeded() {
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            }
        });

        if (localStream != null) {
            assert peerConnection != null;
            peerConnection.addStream(localStream);
        }

        Log.d(TAG, "Peer connection created with server ICE servers");
        
        synchronized (syncLock) {
            isPeerConnectionReady = true;
        }
    }

    private void sendIceCandidate(IceCandidate iceCandidate) {
        if (socketManager != null) {
            try {
                JSONObject candidateJson = new JSONObject();
                candidateJson.put("sdp", iceCandidate.sdp);
                candidateJson.put("sdpMid", iceCandidate.sdpMid);
                candidateJson.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);

                socketManager.sendICECandidate(callId, candidateJson);
                Log.d(TAG, "Sent ICE candidate via Socket.io");
            } catch (JSONException e) {
                Log.e(TAG, "Error sending ICE candidate", e);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void createOffer() {
        synchronized (syncLock) {
            if (isOfferSent) {
                Log.d(TAG, "Offer already sent, skipping");
                return;
            }
            
            if (!isPeerConnectionReady || peerConnection == null) {
                Log.w(TAG, "Peer connection not ready for offer creation");
                // Retry after a short delay
                new Handler(Looper.getMainLooper()).postDelayed(this::createOffer, 500);
                return;
            }
            
            isOfferSent = true;
        }
        
        Log.d(TAG, "Creating offer");
        runOnUiThread(() -> tvCallStatus.setText("Creating offer..."));
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Offer created successfully");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        // Send offer to remote peer via Socket.io
                        sendOffer(sessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "Failed to set local description: " + error);
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create offer: " + error);
            }

            @Override
            public void onSetFailure(String error) {
            }
        }, constraints);
    }

    @SuppressLint("SetTextI18n")
    private void createAnswer() {
        Log.d(TAG, "Creating answer");
        runOnUiThread(() -> tvCallStatus.setText("Creating answer..."));
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Answer created successfully");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        // Send answer to remote peer via Socket.io
                        sendAnswer(sessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "Failed to set local description: " + error);
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create answer: " + error);
            }

            @Override
            public void onSetFailure(String error) {
            }
        }, constraints);
    }

    @SuppressLint("SetTextI18n")
    private void sendOffer(SessionDescription offer) {
        if (socketManager != null) {
            try {
                JSONObject offerJson = new JSONObject();
                offerJson.put("type", offer.type.canonicalForm());
                offerJson.put("sdp", offer.description);

                socketManager.sendWebRTCOffer(callId, offerJson);
                Log.d(TAG, "Sent offer via Socket.io for callId: " + callId);
                runOnUiThread(() -> tvCallStatus.setText("Offer sent, waiting for answer..."));
            } catch (JSONException e) {
                Log.e(TAG, "Error sending offer", e);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void sendAnswer(SessionDescription answer) {
        if (socketManager != null) {
            try {
                JSONObject answerJson = new JSONObject();
                answerJson.put("type", answer.type.canonicalForm());
                answerJson.put("sdp", answer.description);

                socketManager.sendWebRTCAnswer(callId, answerJson);
                Log.d(TAG, "Sent answer via Socket.io for callId: " + callId);
                runOnUiThread(() -> tvCallStatus.setText("Answer sent, establishing connection..."));
            } catch (JSONException e) {
                Log.e(TAG, "Error sending answer", e);
            }
        }
    }

    // WebRTC signaling handlers
    public void handleWebRTCOffer(JSONObject offer) {
        synchronized (syncLock) {
            if (!isPeerConnectionReady || peerConnection == null) {
                Log.w(TAG, "Peer connection not ready for offer processing");
                return;
            }
        }
        
        try {
            String type = offer.getString("type");
            String sdp = offer.getString("sdp");

            SessionDescription sessionDescription = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), sdp);

            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully");
                    remoteDescriptionSet = true;
                    // Flush any queued ICE candidates
                    if (!pendingRemoteCandidates.isEmpty()) {
                        for (IceCandidate c : pendingRemoteCandidates) {
                            try {
                                peerConnection.addIceCandidate(c);
                            } catch (Exception e) {
                                Log.e(TAG, "Error adding queued ICE candidate", e);
                            }
                        }
                        pendingRemoteCandidates.clear();
                    }
                    createAnswer();
                }

                @Override
                public void onCreateFailure(String error) {
                }

                @Override
                public void onSetFailure(String error) {
                    Log.e(TAG, "Failed to set remote description: " + error);
                }
            }, sessionDescription);

        } catch (JSONException e) {
            Log.e(TAG, "Error handling WebRTC offer", e);
        }
    }

    public void handleWebRTCAnswer(JSONObject answer) {
        synchronized (syncLock) {
            if (isAnswerSent) {
                Log.d(TAG, "Answer already processed, skipping");
                return;
            }
            
            if (!isPeerConnectionReady || peerConnection == null) {
                Log.w(TAG, "Peer connection not ready for answer processing");
                return;
            }
            
            isAnswerSent = true;
        }
        
        try {
            String type = answer.getString("type");
            String sdp = answer.getString("sdp");

            SessionDescription sessionDescription = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), sdp);

            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully");
                    remoteDescriptionSet = true;
                    // Flush any queued ICE candidates
                    if (!pendingRemoteCandidates.isEmpty()) {
                        for (IceCandidate c : pendingRemoteCandidates) {
                            try {
                                peerConnection.addIceCandidate(c);
                            } catch (Exception e) {
                                Log.e(TAG, "Error adding queued ICE candidate", e);
                            }
                        }
                        pendingRemoteCandidates.clear();
                    }
                }

                @Override
                public void onCreateFailure(String error) {
                }

                @Override
                public void onSetFailure(String error) {
                    Log.e(TAG, "Failed to set remote description: " + error);
                }
            }, sessionDescription);

        } catch (JSONException e) {
            Log.e(TAG, "Error handling WebRTC answer", e);
        }
    }

    public void handleICECandidate(JSONObject candidate) {
        try {
            String sdp = candidate.getString("sdp");
            String sdpMid = candidate.getString("sdpMid");
            int sdpMLineIndex = candidate.getInt("sdpMLineIndex");

            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
            if (!remoteDescriptionSet) {
                // Queue until remote description is set
                pendingRemoteCandidates.add(iceCandidate);
                Log.d(TAG, "Queued ICE candidate (remoteDescription not set yet)");
            } else {
                peerConnection.addIceCandidate(iceCandidate);
                Log.d(TAG, "Added ICE candidate");
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error handling ICE candidate", e);
        }
    }

    // WebRTCListener implementation
    @SuppressLint("SetTextI18n")
    @Override
    public void onWebRTCOffer(String callId, JSONObject offer) {
        if (this.callId != null && this.callId.equals(callId)) {
            try {
                // Check if this offer is from another user (not from ourselves)
                String fromUserId = offer.optString("fromUserId", "");
                String currentUserId = sharedPrefsManager.getUserId();

                if (!fromUserId.isEmpty() && !fromUserId.equals(currentUserId)) {
                    Log.d(TAG, "Received WebRTC offer from user: " + fromUserId);

                    // Check if this is an offer request
                    String type = offer.optString("type", "");
                    if ("request_offer".equals(type)) {
                        Log.d(TAG, "Received offer request, resending offer");
                        runOnUiThread(() -> tvCallStatus.setText("Resending offer..."));
                        // Resend offer
                        new Handler(Looper.getMainLooper()).postDelayed(this::createOffer, 500);
                    } else {
                        runOnUiThread(() -> {
                            tvCallStatus.setText("Received offer, creating answer...");
                            handleWebRTCOffer(offer);
                        });
                    }
                } else {
                    Log.d(TAG, "Ignoring WebRTC offer from self");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebRTC offer", e);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onWebRTCAnswer(String callId, JSONObject answer) {
        if (this.callId != null && this.callId.equals(callId)) {
            try {
                // Check if this answer is from another user (not from ourselves)
                String fromUserId = answer.optString("fromUserId", "");
                String currentUserId = sharedPrefsManager.getUserId();

                if (!fromUserId.isEmpty() && !fromUserId.equals(currentUserId)) {
                    Log.d(TAG, "Received WebRTC answer from user: " + fromUserId);
                    runOnUiThread(() -> {
                        tvCallStatus.setText("Received answer, connecting...");
                        handleWebRTCAnswer(answer);
                    });
                } else {
                    Log.d(TAG, "Ignoring WebRTC answer from self");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing WebRTC answer", e);
            }
        }
    }

    @Override
    public void onICECandidate(String callId, JSONObject candidate) {
        if (this.callId != null && this.callId.equals(callId)) {
            try {
                // Check if this ICE candidate is from another user (not from ourselves)
                String fromUserId = candidate.optString("fromUserId", "");
                String currentUserId = sharedPrefsManager.getUserId();

                if (!fromUserId.isEmpty() && !fromUserId.equals(currentUserId)) {
                    Log.d(TAG, "Received ICE candidate from user: " + fromUserId);
                    runOnUiThread(() -> handleICECandidate(candidate));
                } else {
                    Log.d(TAG, "Ignoring ICE candidate from self");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing ICE candidate", e);
            }
        }
    }

    @Override
    public void onCallSettingsUpdate(String callId, JSONObject settings) {
        Log.d(TAG, "Received call settings update for call: " + callId);

        if (this.callId != null && this.callId.equals(callId)) {
            try {
                boolean muteVideo = settings.optBoolean("muteVideo", false);

                runOnUiThread(() -> {
                    // Only update remote video display, don't touch local video
                    if (muteVideo) {
                        // Remote camera is off - show black screen
                        if (remoteVideoView != null) {
                            remoteVideoView.setVisibility(View.GONE);
                        }
                        if (remoteVideoPlaceholder != null) {
                            remoteVideoPlaceholder.setVisibility(View.VISIBLE);
                            // Update placeholder to show black background
                            remoteVideoPlaceholder.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
                        }
                        Log.d(TAG, "Remote camera is off - showing black screen");
                    } else {
                        // Remote camera is on - show video
                        if (remoteVideoView != null) {
                            remoteVideoView.setVisibility(View.VISIBLE);
                        }
                        if (remoteVideoPlaceholder != null) {
                            remoteVideoPlaceholder.setVisibility(View.GONE);
                        }
                        Log.d(TAG, "Remote camera is on - showing video");
                    }

                    // Ensure local video is always visible and properly rendered
                    ensureLocalVideoRendering();

                    // Additional fix: Force local video refresh after remote changes
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        ensureLocalVideoRendering();
                        forceLocalVideoRefresh();
                    }, 500); // Delay 500ms to ensure proper rendering
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing call settings update", e);
            }
        }
    }

    // Add periodic check for remote video track status
    private void startRemoteVideoStatusCheck() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isCallActive && remoteVideoView != null && remoteVideoPlaceholder != null) {
                    // Check if remote video track is enabled
                    boolean hasRemoteVideo = false;
                    if (peerConnection != null) {
                        for (RtpReceiver receiver : peerConnection.getReceivers()) {
                            if (receiver.track() instanceof VideoTrack) {
                                VideoTrack videoTrack = (VideoTrack) receiver.track();
                                assert videoTrack != null;
                                if (videoTrack.enabled()) {
                                    hasRemoteVideo = true;
                                    break;
                                }
                            }
                        }
                    }

                    // Update UI based on video availability
                    if (!hasRemoteVideo) {
                        // No video available - show black screen
                        remoteVideoView.setVisibility(View.GONE);
                        remoteVideoPlaceholder.setVisibility(View.VISIBLE);
                        remoteVideoPlaceholder.setBackgroundColor(ContextCompat.getColor(VideoCallActivity.this, android.R.color.black));
                    } else {
                        // Video available - show video
                        remoteVideoView.setVisibility(View.VISIBLE);
                        remoteVideoPlaceholder.setVisibility(View.GONE);
                    }
                }

                // Ensure local video is properly rendered
                ensureLocalVideoRendering();

                // Schedule next check
                if (isCallActive) {
                    new Handler(Looper.getMainLooper()).postDelayed(this, 2000); // Check every 2 seconds
                }
            }
        }, 2000); // Start checking after 2 seconds
    }

    // Ensure local video is properly rendered
    private void ensureLocalVideoRendering() {
        if (localVideoView != null && localVideoPlaceholder != null) {
            if (isCameraOn && localVideoTrack != null && localVideoTrack.enabled()) {
                // Ensure local video is visible and properly rendered
                localVideoView.setVisibility(View.VISIBLE);
                localVideoPlaceholder.setVisibility(View.GONE);

                // Force refresh the surface view with multiple methods
                if (localVideoView.getVisibility() == View.VISIBLE) {
                    // Method 1: Request layout and invalidate
                    localVideoView.requestLayout();
                    localVideoView.invalidate();

                    // Method 2: Force surface view to refresh
                    localVideoView.post(() -> {
                        localVideoView.requestLayout();
                        localVideoView.invalidate();
                    });

                    // Method 3: Ensure video track is properly attached
                    if (localVideoTrack != null) {
                        // Try to add sink - if already attached, this will be ignored
                        Log.d(TAG, "Ensuring local video track is attached to surface view");
                        localVideoTrack.addSink(localVideoView);
                    }

                    // Method 4: Set alpha to ensure visibility
                    localVideoView.setAlpha(1.0f);

                    // Method 5: Force re-initialization if needed
                    if (localVideoView.getVisibility() == View.VISIBLE && localVideoTrack != null) {
                        // Check if surface view is properly initialized
                        if (eglBase != null) {
                            Log.d(TAG, "Ensuring local video surface view is properly initialized");
                            try {
                                // Try to re-initialize if needed (this will be ignored if already initialized)
                                localVideoView.init(eglBase.getEglBaseContext(), null);
                                localVideoView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                                localVideoView.setMirror(true);
                                localVideoTrack.addSink(localVideoView);
                            } catch (Exception e) {
                                Log.d(TAG, "Local video surface view already initialized: " + e.getMessage());
                            }
                        }
                    }
                }
            } else {
                // Show placeholder when camera is off
                localVideoView.setVisibility(View.GONE);
                localVideoPlaceholder.setVisibility(View.VISIBLE);
            }
        }
    }

    // Force local video refresh - more aggressive approach
    private void forceLocalVideoRefresh() {
        if (localVideoView != null && isCameraOn && localVideoTrack != null) {
            Log.d(TAG, "Force refreshing local video");

            // Temporarily hide and show to force refresh
            localVideoView.setVisibility(View.GONE);
            localVideoView.post(() -> {
                localVideoView.setVisibility(View.VISIBLE);
                localVideoView.requestLayout();
                localVideoView.invalidate();

                // Ensure video track is attached
                localVideoTrack.addSink(localVideoView);
            });
        }
    }

    private void setupClickListeners() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnEndCall.setOnClickListener(v -> endCall());
        btnCameraToggle.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
    }

    private void checkPermissions() {
        boolean cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean microphonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        if (!cameraPermission && "video".equals(callType)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        if (!microphonePermission) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE_PERMISSION);
        }
    }

    @SuppressLint("SetTextI18n")
    private void setupIncomingCall() {
        tvCallStatus.setText("Incoming call...");

        // Auto-accept after 5 seconds for demo
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isCallActive) {
                acceptCall();
            }
        }, 5000);
    }

    @SuppressLint("SetTextI18n")
    private void setupOutgoingCall() {
        tvCallStatus.setText("Calling...");

        // Simulate call connection
        new Handler(Looper.getMainLooper()).postDelayed(this::connectCall, 2000);
    }

    @SuppressLint("SetTextI18n")
    private void acceptCall() {
        tvCallStatus.setText("Connecting...");

        // Call API to accept call
        String token = sharedPrefsManager.getToken();
        apiClient.joinCall(token, callId, new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(VideoCallActivity.this, "Failed to accept call", Toast.LENGTH_SHORT).show();
                    navigateToHome();
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        connectCall();
                    } else {
                        Toast.makeText(VideoCallActivity.this, "Failed to accept call", Toast.LENGTH_SHORT).show();
                        navigateToHome();
                    }
                });
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void connectCall() {
        isCallActive = true;
        tvCallStatus.setText("Connected");

        // Start call duration timer
        startCallDurationTimer();

        // Start remote video status check
        startRemoteVideoStatusCheck();

        // Initialize WebRTC (placeholder)
        initializeWebRTC();

        // Mark active call for de-duplication
        if (socketManager != null) {
            socketManager.setActiveCallId(callId);
        }
    }

    private void startCallDurationTimer() {
        callDurationTimer = new Timer();
        callDurationTimer.schedule(new TimerTask() {
            @SuppressLint("DefaultLocale")
            @Override
            public void run() {
                runOnUiThread(() -> {
                    callDurationSeconds++;
                });
            }
        }, 1000, 1000);
    }


    private void toggleMute() {
        isMuted = !isMuted;

        // Update UI
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);

        // Update call settings
        updateCallSettings();

        Toast.makeText(this, isMuted ? "Microphone muted" : "Microphone unmuted", Toast.LENGTH_SHORT).show();
    }

    private void toggleCamera() {
        if ("audio".equals(callType)) return;

        isCameraOn = !isCameraOn;

        // Update UI
        btnCameraToggle.setImageResource(isCameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);

        if (isCameraOn) {
            // Turn camera on
            if (videoCapturer != null) {
                videoCapturer.startCapture(1280, 720, 30);
            }
            // Enable video track
            if (localVideoTrack != null) {
                localVideoTrack.setEnabled(true);
            }
            // Show local video if available, otherwise show placeholder
            if (localVideoView != null && localVideoPlaceholder != null) {
                // Check if video is actually available
                if (localVideoTrack != null && localVideoTrack.enabled()) {
                    localVideoView.setVisibility(View.VISIBLE);
                    localVideoPlaceholder.setVisibility(View.GONE);
                } else {
                    localVideoView.setVisibility(View.GONE);
                    localVideoPlaceholder.setVisibility(View.VISIBLE);
                }
            }
        } else {
            // Turn camera off
            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error stopping camera capture", e);
                }
            }
            // Disable video track
            if (localVideoTrack != null) {
                localVideoTrack.setEnabled(false);
            }
            // Show placeholder
            if (localVideoView != null && localVideoPlaceholder != null) {
                localVideoView.setVisibility(View.GONE);
                localVideoPlaceholder.setVisibility(View.VISIBLE);
            }
        }

        // Update call settings
        updateCallSettings();

        // Ensure local video is properly rendered
        ensureLocalVideoRendering();

        Toast.makeText(this, isCameraOn ? "Camera on" : "Camera off", Toast.LENGTH_SHORT).show();
    }

    private void switchCamera() {
        if ("audio".equals(callType) || !isCameraOn) return;

        // Switch between front and back camera
        if (videoCapturer != null) {
            try {
                if (videoCapturer instanceof Camera2Capturer) {
                    Camera2Capturer camera2Capturer = (Camera2Capturer) videoCapturer;
                    camera2Capturer.switchCamera(null);
                    Toast.makeText(this, "Switched camera", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error switching camera", e);
                Toast.makeText(this, "Failed to switch camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateCallSettings() {
        // Update call settings via API
        String token = sharedPrefsManager.getToken();
        JSONObject settings = new JSONObject();
        try {
            settings.put("muteAudio", isMuted);
            settings.put("muteVideo", !isCameraOn);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating settings JSON", e);
        }

        apiClient.updateCallSettings(token, callId, settings.toString(), new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.e(TAG, "Failed to update call settings", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                Log.d(TAG, "Call settings updated: " + response.code());
            }
        });

        // Also send via WebSocket for real-time updates
        if (socketManager != null) {
            try {
                JSONObject wsData = new JSONObject();
                wsData.put("callId", callId);
                wsData.put("settings", settings);
                socketManager.sendCallSettingsUpdate(wsData);
                Log.d(TAG, "Sent call settings via WebSocket");
            } catch (JSONException e) {
                Log.e(TAG, "Error sending call settings via WebSocket", e);
            }
        }
    }

    private void endCall() {
        synchronized (syncLock) {
            if (isEndingCall) {
                Log.d(TAG, "Call ending already in progress, ignoring");
                return;
            }
            isEndingCall = true;
        }
        
        Log.d(TAG, "Ending call: " + callId);
        
        if (callDurationTimer != null) {
            callDurationTimer.cancel();
        }

        // Call API to end call
        String token = sharedPrefsManager.getToken();
        apiClient.endCall(token, callId, new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to end call", e);
                    navigateToHome();
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Call ended: " + response.code());
                    navigateToHome();
                });
            }
        });
    }
    
    private void resetSynchronizationState() {
        synchronized (syncLock) {
            isWebRTCInitialized = false;
            isPeerConnectionReady = false;
            isOfferSent = false;
            isAnswerSent = false;
            isEndingCall = false;
            remoteDescriptionSet = false;
            pendingRemoteCandidates.clear();
            pendingIceServersJson = null;
        }
        Log.d(TAG, "Synchronization state reset");
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted");
            } else {
                Toast.makeText(this, "Camera permission required for video calls", Toast.LENGTH_SHORT).show();
                if ("video".equals(callType)) {
                    navigateToHome();
                }
            }
        } else if (requestCode == REQUEST_MICROPHONE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Microphone permission granted");
            } else {
                Toast.makeText(this, "Microphone permission required for calls", Toast.LENGTH_SHORT).show();
                navigateToHome();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (callDurationTimer != null) {
            callDurationTimer.cancel();
        }

        // Leave call room and clean up socket manager
        if (socketManager != null) {
            socketManager.leaveCallRoom(callId);
            socketManager.removeCallStatusListener();
            socketManager.removeWebRTCListener();
            socketManager.clearActiveCallId(callId);
        }

        // Clean up WebRTC resources
        cleanupWebRTC();
        
        // Reset synchronization state
        resetSynchronizationState();
    }

    private void cleanupWebRTC() {
        Log.d(TAG, "Cleaning up WebRTC resources");

        try {
            // Stop video capturer first
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

            // Remove tracks from peer connection before disposing
            if (peerConnection != null && localStream != null) {
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

            // Dispose peer connection first (this will dispose tracks automatically)
            if (peerConnection != null) {
                try {
                    peerConnection.dispose();
                } catch (Exception e) {
                    Log.w(TAG, "Error disposing peer connection: " + e.getMessage());
                }
                peerConnection = null;
            }

            // Dispose video tracks (after peer connection is disposed)
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

            // Dispose video source
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

            // Dispose peer connection factory
            if (peerConnectionFactory != null) {
                peerConnectionFactory.dispose();
                peerConnectionFactory = null;
            }

            // Dispose video views
            if (localVideoView != null) {
                localVideoView.release();
                localVideoView = null;
            }

            if (remoteVideoView != null) {
                remoteVideoView.release();
                remoteVideoView = null;
            }

            // Dispose EGL base
            if (eglBase != null) {
                eglBase.release();
                eglBase = null;
            }

            Log.d(TAG, "WebRTC resources cleaned up successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up WebRTC resources", e);
        }
    }
}