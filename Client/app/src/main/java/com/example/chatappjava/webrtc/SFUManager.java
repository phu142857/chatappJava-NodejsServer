package com.example.chatappjava.webrtc;

import android.util.Log;
import com.example.chatappjava.network.SocketManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.*;

import java.util.HashMap;
import java.util.Map;

/**
 * SFUManager manages SFU (Selective Forwarding Unit) connections for group video calls
 * Instead of creating N-1 peer connections (mesh), we only need 2 transports:
 * - Send transport: to send local audio/video
 * - Receive transport: to receive remote audio/video from all participants
 */
public class SFUManager {
    private static final String TAG = "SFUManager";

    private String roomId;
    private String peerId; // Current user ID
    private SocketManager socketManager;
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase eglBase;

    // SFU Transports
    private PeerConnection sendTransport;
    private PeerConnection recvTransport;
    
    // Transport info (for creating PeerConnections)
    private JSONObject sendTransportInfo;
    private JSONObject recvTransportInfo;
    
    // Map producerId -> userId (for track assignment)
    private Map<String, String> producerIdToUserId = new HashMap<>();
    
    // Producers (sending media)
    private Map<String, RtpSender> producers = new HashMap<>(); // kind -> RtpSender
    
    // Consumers (receiving media)
    private Map<String, Consumer> consumers = new HashMap<>(); // producerId -> Consumer
    
    // Local tracks
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    
    // Callbacks
    public interface SFUListener {
        void onTransportCreated(String direction, JSONObject transport);
        void onTransportConnected(String direction);
        void onProducerCreated(String kind, String producerId);
        void onConsumerCreated(String producerId, String consumerId, JSONObject rtpParameters);
        void onNewProducer(String producerPeerId, String producerId, String kind);
        void onRemoteTrack(String producerId, MediaStreamTrack track);
        void onError(String message);
    }
    
    private SFUListener listener;

    public static class Consumer {
        public String id;
        public String producerId;
        public String kind;
        public RtpReceiver receiver;
        public MediaStreamTrack track;
        public RtpTransceiver transceiver; // Store transceiver for direct track access
    }

    public SFUManager(String roomId, String peerId, SocketManager socketManager, 
                     PeerConnectionFactory peerConnectionFactory, EglBase eglBase) {
        this.roomId = roomId;
        this.peerId = peerId;
        this.socketManager = socketManager;
        this.peerConnectionFactory = peerConnectionFactory;
        this.eglBase = eglBase;
    }

    public void setListener(SFUListener listener) {
        this.listener = listener;
    }
    
    /**
     * Set mapping: userId -> producerId
     */
    public void setProducerIdMapping(String userId, String producerId) {
        producerIdToUserId.put(producerId, userId);
        Log.d(TAG, "Mapped producerId " + producerId + " to userId " + userId);
    }
    
    /**
     * Get userId for producerId
     */
    public String getUserIdForProducerId(String producerId) {
        return producerIdToUserId.get(producerId);
    }

    /**
     * Initialize SFU connection
     * 1. Create SFU room
     * 2. Get router capabilities
     * 3. Create send and receive transports
     */
    public void initialize() {
        Log.d(TAG, "Initializing SFU for room: " + roomId);
        
        // Setup socket listeners
        setupSocketListeners();
        
        // Create SFU room
        socketManager.createSFURoom(roomId);
    }

    private void setupSocketListeners() {
        // SFU room created
        socketManager.on("sfu-room-created", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject rtpCapabilities = data.optJSONObject("rtpCapabilities");
                    Log.d(TAG, "SFU room created, rtpCapabilities: " + (rtpCapabilities != null));
                    
                    // Create transports after room is created
                    createTransports();
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-room-created", e);
                }
            }
        });

        // SFU router capabilities
        socketManager.on("sfu-router-capabilities", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject rtpCapabilities = data.optJSONObject("rtpCapabilities");
                    Log.d(TAG, "Received SFU router capabilities");
                    // Router capabilities are used when creating consumers
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-router-capabilities", e);
                }
            }
        });

        // Transport created
        socketManager.on("sfu-transport-created", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject transport = data.getJSONObject("transport");
                    String direction = data.getString("direction");
                    Log.d(TAG, "SFU transport created: " + direction + ", transportId: " + transport.optString("id", "unknown"));
                    
                    // CRITICAL: Store transport info FIRST before creating PeerConnection
                    if ("send".equals(direction)) {
                        sendTransportInfo = transport;
                        Log.d(TAG, "sendTransportInfo stored: " + (sendTransportInfo != null ? "SUCCESS" : "FAILED"));
                    } else if ("receive".equals(direction)) {
                        recvTransportInfo = transport;
                        Log.d(TAG, "recvTransportInfo stored: " + (recvTransportInfo != null ? "SUCCESS" : "FAILED"));
                    }
                    
                    if (listener != null) {
                        listener.onTransportCreated(direction, transport);
                    }
                    
                    // Create PeerConnection for transport (this will also set sendTransport/recvTransport)
                    createTransportPeerConnection(direction, transport);
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-transport-created", e);
                    e.printStackTrace();
                }
            }
        });

        // Transport connected
        socketManager.on("sfu-transport-connected", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String transportId = data.getString("transportId");
                    Log.d(TAG, "SFU transport connected event received: " + transportId);
                    Log.d(TAG, "sendTransportInfo: " + (sendTransportInfo != null ? "exists" : "null"));
                    Log.d(TAG, "recvTransportInfo: " + (recvTransportInfo != null ? "exists" : "null"));
                    
                    // Determine direction from transport ID by comparing with stored transport info
                    String direction = null;
                    if (sendTransportInfo != null) {
                        try {
                            String sendTransportId = sendTransportInfo.getString("id");
                            Log.d(TAG, "Comparing transportId " + transportId + " with sendTransportId " + sendTransportId);
                            if (sendTransportId.equals(transportId)) {
                                direction = "send";
                                Log.d(TAG, "Matched to send transport");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error comparing with sendTransportInfo", e);
                        }
                    }
                    if (direction == null && recvTransportInfo != null) {
                        try {
                            String recvTransportId = recvTransportInfo.getString("id");
                            Log.d(TAG, "Comparing transportId " + transportId + " with recvTransportId " + recvTransportId);
                            if (recvTransportId.equals(transportId)) {
                                direction = "receive";
                                Log.d(TAG, "Matched to receive transport");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error comparing with recvTransportInfo", e);
                        }
                    }
                    
                    if (direction == null) {
                        Log.w(TAG, "Could not determine transport direction for transportId: " + transportId);
                        Log.w(TAG, "sendTransport exists: " + (sendTransport != null));
                        Log.w(TAG, "recvTransport exists: " + (recvTransport != null));
                        // Fallback: assume send if sendTransport exists, otherwise receive
                        direction = (sendTransport != null) ? "send" : "receive";
                        Log.w(TAG, "Using fallback direction: " + direction);
                    }
                    
                    Log.d(TAG, "Transport direction determined: " + direction + " for transportId: " + transportId);
                    
                    if (listener != null) {
                        Log.d(TAG, "Calling listener.onTransportConnected with direction: " + direction);
                        listener.onTransportConnected(direction);
                    } else {
                        Log.e(TAG, "Listener is null! Cannot notify transport connected");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-transport-connected", e);
                    e.printStackTrace();
                }
            } else {
                Log.w(TAG, "sfu-transport-connected event received but args is empty or invalid");
            }
        });

        // Producer created
        socketManager.on("sfu-producer-created", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject producer = data.getJSONObject("producer");
                    String producerId = producer.getString("id");
                    String kind = producer.getString("kind");
                    Log.d(TAG, "SFU producer created: " + producerId + ", kind: " + kind);
                    
                    if (listener != null) {
                        listener.onProducerCreated(kind, producerId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-producer-created", e);
                }
            }
        });

        // Consumer created
        socketManager.on("sfu-consumer-created", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject consumer = data.getJSONObject("consumer");
                    String consumerId = consumer.getString("id");
                    String producerId = consumer.getString("producerId");
                    JSONObject rtpParameters = consumer.getJSONObject("rtpParameters");
                    String kind = consumer.getString("kind");
                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                    Log.d(TAG, "🎉🎉🎉 SFU consumer created: consumerId=" + consumerId + ", producerId=" + producerId + ", kind=" + kind);
                    
                    // Create consumer object
                    Consumer consumerObj = new Consumer();
                    consumerObj.id = consumerId;
                    consumerObj.producerId = producerId;
                    consumerObj.kind = kind;
                    consumers.put(producerId, consumerObj);
                    Log.d(TAG, "✅ Consumer stored in map. Total consumers: " + consumers.size());
                    
                    // CRITICAL: Add transceiver to receive transport to receive track
                    // With Mediasoup, we need to add transceiver and configure with RTP parameters
                    if (recvTransport != null) {
                        Log.d(TAG, "✅ Receive transport ready, adding transceiver for consumer");
                        addConsumerTransceiver(producerId, rtpParameters, kind);
                    } else {
                        Log.e(TAG, "✗✗✗ Receive transport is null, cannot add consumer transceiver");
                        Log.e(TAG, "Will retry when receive transport is ready...");
                        // Retry when transport is ready
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (recvTransport != null) {
                                Log.d(TAG, "Retrying addConsumerTransceiver after transport ready");
                                addConsumerTransceiver(producerId, rtpParameters, kind);
                            }
                        }, 1000);
                    }
                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                    
                    if (listener != null) {
                        listener.onConsumerCreated(producerId, consumerId, rtpParameters);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-consumer-created", e);
                }
            }
        });

        // New producer (from other participant)
        socketManager.on("sfu-new-producer", args -> {
            Log.d(TAG, "═══════════════════════════════════════════════════════════");
            Log.d(TAG, "🎬🎬🎬 sfu-new-producer EVENT RECEIVED - args length: " + (args != null ? args.length : 0));
            if (args != null && args.length > 0) {
                Log.d(TAG, "Args[0] type: " + (args[0] != null ? args[0].getClass().getName() : "null"));
            }
            
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String producerPeerId = data.getString("producerPeerId");
                    String producerId = data.getString("producerId");
                    String kind = data.getString("kind");
                    Log.d(TAG, "✅✅✅ New SFU producer: producerId=" + producerId + ", producerPeerId=" + producerPeerId + ", kind=" + kind);
                    Log.d(TAG, "recvTransport: " + (recvTransport != null ? "exists" : "NULL"));
                    Log.d(TAG, "recvTransportInfo: " + (recvTransportInfo != null ? "exists" : "NULL"));
                    
                    if (listener != null) {
                        Log.d(TAG, "✅ Calling listener.onNewProducer");
                        listener.onNewProducer(producerPeerId, producerId, kind);
                    } else {
                        Log.e(TAG, "✗✗✗ listener is NULL, cannot notify!");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "✗✗✗ Error handling sfu-new-producer", e);
                    e.printStackTrace();
                }
            } else {
                Log.e(TAG, "✗✗✗ Invalid args for sfu-new-producer: " + (args != null && args.length > 0 ? args[0].getClass().getName() : "null"));
            }
            Log.d(TAG, "═══════════════════════════════════════════════════════════");
        });

        // SFU error
        socketManager.on("sfu-error", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String message = data.getString("message");
                    Log.e(TAG, "SFU error: " + message);
                    
                    if (listener != null) {
                        listener.onError(message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling sfu-error", e);
                }
            }
        });
    }

    /**
     * Create send and receive transports
     */
    private void createTransports() {
        Log.d(TAG, "Creating SFU transports");
        
        // Create send transport
        socketManager.createSFUTransport(roomId, peerId, "send");
        
        // Create receive transport
        socketManager.createSFUTransport(roomId, peerId, "receive");
    }

    /**
     * Create PeerConnection for transport (send or receive)
     * With Mediasoup, we need to create PeerConnection with ICE parameters from transport
     */
    private void createTransportPeerConnection(String direction, JSONObject transport) {
        try {
            Log.d(TAG, "Creating PeerConnection for " + direction + " transport");
            
            // Check peerConnectionFactory
            if (peerConnectionFactory == null) {
                Log.e(TAG, "peerConnectionFactory is NULL! Cannot create PeerConnection");
                return;
            }
            Log.d(TAG, "peerConnectionFactory exists: " + (peerConnectionFactory != null));
            
            // Extract ICE parameters from transport
            // Note: With Mediasoup, we don't use ICE candidates as ICE servers
            // Mediasoup handles ICE negotiation internally
            // We just need basic STUN servers for initial connection
            java.util.List<PeerConnection.IceServer> iceServers = new java.util.ArrayList<>();
            
            // Add default STUN servers (same as VideoCallActivity)
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            
            Log.d(TAG, "Using " + iceServers.size() + " default ICE servers (Mediasoup handles ICE internally)");
            
            // Create RTC configuration (same as VideoCallActivity)
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
            rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
            rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            Log.d(TAG, "RTC configuration created with " + iceServers.size() + " ICE servers");
            
            // Create PeerConnection with observer
            final String finalDirection = direction; // Make effectively final for lambda
            PeerConnection.Observer observer = new PeerConnection.Observer() {
                @Override
                public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                    Log.d(TAG, finalDirection + " transport signaling state: " + signalingState);
                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    Log.d(TAG, finalDirection + " transport ICE state: " + iceConnectionState);
                }

                @Override
                public void onIceConnectionReceivingChange(boolean receiving) {}

                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    Log.d(TAG, finalDirection + " transport ICE gathering: " + iceGatheringState);
                }

                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    // ICE candidates are handled by Mediasoup transport
                    Log.d(TAG, finalDirection + " transport ICE candidate");
                }

                @Override
                public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

                @Override
                public void onAddStream(MediaStream mediaStream) {}

                @Override
                public void onRemoveStream(MediaStream mediaStream) {}

                @Override
                public void onDataChannel(org.webrtc.DataChannel dataChannel) {}

                @Override
                public void onRenegotiationNeeded() {}

                @Override
                public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                    // CRITICAL: Extract track from RTP receiver
                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                    Log.d(TAG, "🎯🎯🎯 onAddTrack CALLED for " + finalDirection + " transport");
                    Log.d(TAG, "MediaStreams count: " + (mediaStreams != null ? mediaStreams.length : 0));
                    
                    MediaStreamTrack track = rtpReceiver.track();
                    if (track != null) {
                        Log.d(TAG, "✅ Track received: kind=" + track.kind() + ", id=" + track.id() + ", enabled=" + track.enabled() + ", state=" + track.state());
                        Log.d(TAG, "Current consumers count: " + consumers.size());
                        for (Map.Entry<String, Consumer> entry : consumers.entrySet()) {
                            Log.d(TAG, "  Consumer: producerId=" + entry.getKey() + ", kind=" + entry.getValue().kind + ", hasTrack=" + (entry.getValue().track != null) + ", consumerId=" + entry.getValue().id);
                        }
                        
                        // Match track to producerId from consumer map
                        String producerId = null;
                        for (Map.Entry<String, Consumer> entry : consumers.entrySet()) {
                            Consumer consumer = entry.getValue();
                            // Match by kind - if track kind matches consumer kind
                            if (consumer.kind.equals(track.kind())) {
                                // Prefer consumer without track, but also update if track is null
                                if (consumer.track == null) {
                                    consumer.receiver = rtpReceiver;
                                    consumer.track = track;
                                    producerId = entry.getKey();
                                    Log.d(TAG, "✅✅✅ Matched track to producerId: " + producerId + " (kind: " + track.kind() + ")");
                                    break;
                                } else {
                                    // Track already exists, but this might be a new track for the same consumer
                                    Log.d(TAG, "⚠️ Consumer " + entry.getKey() + " already has a track, updating it");
                                    consumer.receiver = rtpReceiver;
                                    consumer.track = track;
                                    producerId = entry.getKey();
                                    break;
                                }
                            }
                        }
                        
                        if (producerId != null && listener != null) {
                            Log.d(TAG, "✅✅✅ Calling listener.onRemoteTrack for producerId: " + producerId + ", track kind: " + track.kind());
                            listener.onRemoteTrack(producerId, track);
                        } else {
                            Log.e(TAG, "✗✗✗ Cannot find producerId for track: " + track.id() + ", kind: " + track.kind());
                            Log.e(TAG, "Available consumers: " + consumers.keySet());
                            // Try to find any consumer with matching kind, even if it has a track
                            for (Map.Entry<String, Consumer> entry : consumers.entrySet()) {
                                Consumer consumer = entry.getValue();
                                if (consumer.kind.equals(track.kind())) {
                                    Log.w(TAG, "Found consumer with matching kind but track already exists: " + entry.getKey());
                                    // Update anyway and notify
                                    consumer.receiver = rtpReceiver;
                                    consumer.track = track;
                                    if (listener != null) {
                                        Log.w(TAG, "Calling onRemoteTrack anyway for producerId: " + entry.getKey());
                                        listener.onRemoteTrack(entry.getKey(), track);
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "✗✗✗ onAddTrack called but track is null!");
                    }
                    Log.d(TAG, "═══════════════════════════════════════════════════════════");
                }
            };
            
            Log.d(TAG, "Calling peerConnectionFactory.createPeerConnection()...");
            Log.d(TAG, "RTC Config details - iceServers: " + iceServers.size() + ", sdpSemantics: " + rtcConfig.sdpSemantics);
            
            PeerConnection pc = null;
            try {
                pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer);
                Log.d(TAG, "createPeerConnection call completed, result: " + (pc != null ? "SUCCESS" : "NULL"));
            } catch (Exception e) {
                Log.e(TAG, "Exception in createPeerConnection!", e);
                e.printStackTrace();
                return;
            }
            
            if (pc == null) {
                Log.e(TAG, "createPeerConnection returned NULL! This is a critical error.");
                Log.e(TAG, "peerConnectionFactory: " + (peerConnectionFactory != null ? "exists" : "NULL"));
                Log.e(TAG, "rtcConfig: " + (rtcConfig != null ? "exists" : "NULL"));
                Log.e(TAG, "iceServers count: " + iceServers.size());
                Log.e(TAG, "observer: " + (observer != null ? "exists" : "NULL"));
                
                // Try with minimal config as fallback
                Log.w(TAG, "Trying fallback: create PeerConnection with minimal config");
                try {
                    PeerConnection.RTCConfiguration minimalConfig = new PeerConnection.RTCConfiguration(
                        java.util.Collections.singletonList(
                            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                        )
                    );
                    minimalConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
                    pc = peerConnectionFactory.createPeerConnection(minimalConfig, observer);
                    Log.d(TAG, "Fallback createPeerConnection result: " + (pc != null ? "SUCCESS" : "NULL"));
                } catch (Exception e2) {
                    Log.e(TAG, "Fallback also failed!", e2);
                    e2.printStackTrace();
                    return;
                }
                
                if (pc == null) {
                    Log.e(TAG, "Both normal and fallback createPeerConnection returned NULL. Cannot proceed.");
                    return;
                }
            }
            
            Log.d(TAG, "PeerConnection created successfully: " + (pc != null ? "SUCCESS" : "FAILED"));
            
            // CRITICAL: Set transport BEFORE connecting to avoid race conditions
            if ("send".equals(direction)) {
                sendTransport = pc;
                Log.d(TAG, "sendTransport PeerConnection set: " + (sendTransport != null ? "SUCCESS" : "FAILED"));
                Log.d(TAG, "sendTransportInfo exists: " + (sendTransportInfo != null));
            } else if ("receive".equals(direction)) {
                recvTransport = pc;
                Log.d(TAG, "recvTransport PeerConnection set: " + (recvTransport != null ? "SUCCESS" : "FAILED"));
                Log.d(TAG, "recvTransportInfo exists: " + (recvTransportInfo != null));
            }
            
            Log.d(TAG, "PeerConnection created for " + direction + " transport - sendTransport: " + (sendTransport != null) + ", recvTransport: " + (recvTransport != null));
            
            // Connect transport using DTLS parameters
            connectTransport(direction, transport);
        } catch (Exception e) {
            Log.e(TAG, "Error creating transport PeerConnection", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Find producerId for a track (from consumer map)
     */
    private String findProducerIdForTrack(MediaStreamTrack track) {
        // Try to find producerId from consumer map
        for (Map.Entry<String, Consumer> entry : consumers.entrySet()) {
            if (entry.getValue().track == track || 
                (entry.getValue().track != null && entry.getValue().track.id().equals(track.id()))) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Connect transport using DTLS parameters
     */
    private void connectTransport(String direction, JSONObject transport) {
        try {
            Log.d(TAG, "Connecting " + direction + " transport");
            
            // Extract DTLS parameters from transport
            JSONObject dtlsParameters = transport.getJSONObject("dtlsParameters");
            
            // Connect transport via socket
            socketManager.connectSFUTransport(roomId, peerId, transport.getString("id"), dtlsParameters);
        } catch (Exception e) {
            Log.e(TAG, "Error connecting transport", e);
        }
    }

    /**
     * Produce (send) local audio track
     * Create SDP offer first, then extract RTP parameters
     */
    public void produceAudio(AudioTrack audioTrack) {
        Log.d(TAG, "produceAudio called - sendTransport: " + (sendTransport != null ? "exists" : "NULL") + ", sendTransportInfo: " + (sendTransportInfo != null ? "exists" : "NULL"));
        if (sendTransport == null || sendTransportInfo == null) {
            Log.e(TAG, "Send transport not ready - sendTransport: " + (sendTransport != null) + ", sendTransportInfo: " + (sendTransportInfo != null));
            // Retry after a short delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (sendTransport != null && sendTransportInfo != null) {
                    Log.d(TAG, "Retrying produceAudio after delay");
                    produceAudio(audioTrack);
                } else {
                    Log.e(TAG, "Send transport still not ready after delay");
                }
            }, 500);
            return;
        }
        
        try {
            this.localAudioTrack = audioTrack;
            
            // Add track to send transport
            RtpSender sender = sendTransport.addTrack(audioTrack);
            producers.put("audio", sender);
            
            // Create SDP offer to get RTP parameters
            sendTransport.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    try {
                        sendTransport.setLocalDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sdp) {}
                            @Override
                            public void onSetSuccess() {
                                // Extract RTP parameters from sender after SDP is set
                                JSONObject rtpParameters = getRtpParametersFromSender(sender, "audio");
                                try {
                                    socketManager.createSFUProducer(roomId, peerId, sendTransportInfo.getString("id"), rtpParameters, "audio");
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                Log.d(TAG, "Producing audio - SDP offer created");
                            }
                            @Override
                            public void onCreateFailure(String error) {}
                            @Override
                            public void onSetFailure(String error) {
                                Log.e(TAG, "Error setting local description for audio: " + error);
                            }
                        }, sdp);
                    } catch (Exception e) {
                        Log.e(TAG, "Error creating audio offer", e);
                    }
                }
                @Override
                public void onSetSuccess() {}
                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "Error creating audio offer: " + error);
                }
                @Override
                public void onSetFailure(String error) {}
            }, new MediaConstraints());
        } catch (Exception e) {
            Log.e(TAG, "Error producing audio", e);
        }
    }

    /**
     * Produce (send) local video track
     * Create SDP offer first, then extract RTP parameters
     */
    public void produceVideo(VideoTrack videoTrack) {
        Log.d(TAG, "produceVideo called - sendTransport: " + (sendTransport != null ? "exists" : "NULL") + ", sendTransportInfo: " + (sendTransportInfo != null ? "exists" : "NULL"));
        if (sendTransport == null || sendTransportInfo == null) {
            Log.e(TAG, "Send transport not ready - sendTransport: " + (sendTransport != null) + ", sendTransportInfo: " + (sendTransportInfo != null));
            // Retry after a short delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (sendTransport != null && sendTransportInfo != null) {
                    Log.d(TAG, "Retrying produceVideo after delay");
                    produceVideo(videoTrack);
                } else {
                    Log.e(TAG, "Send transport still not ready after delay");
                }
            }, 500);
            return;
        }
        
        try {
            this.localVideoTrack = videoTrack;
            
            // Add track to send transport
            RtpSender sender = sendTransport.addTrack(videoTrack);
            producers.put("video", sender);
            
            // Create SDP offer to get RTP parameters
            sendTransport.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    try {
                        sendTransport.setLocalDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sdp) {}
                            @Override
                            public void onSetSuccess() {
                                // Extract RTP parameters from sender after SDP is set
                                JSONObject rtpParameters = getRtpParametersFromSender(sender, "video");
                                try {
                                    socketManager.createSFUProducer(roomId, peerId, sendTransportInfo.getString("id"), rtpParameters, "video");
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                                Log.d(TAG, "Producing video - SDP offer created");
                            }
                            @Override
                            public void onCreateFailure(String error) {}
                            @Override
                            public void onSetFailure(String error) {
                                Log.e(TAG, "Error setting local description for video: " + error);
                            }
                        }, sdp);
                    } catch (Exception e) {
                        Log.e(TAG, "Error creating video offer", e);
                    }
                }
                @Override
                public void onSetSuccess() {}
                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "Error creating video offer: " + error);
                }
                @Override
                public void onSetFailure(String error) {}
            }, new MediaConstraints());
        } catch (Exception e) {
            Log.e(TAG, "Error producing video", e);
        }
    }

    /**
     * Consume (receive) media from a producer
     */
    public void consume(String producerId, JSONObject rtpCapabilities) {
        Log.d(TAG, "═══════════════════════════════════════════════════════════");
        Log.d(TAG, "consume() called for producerId: " + producerId);
        Log.d(TAG, "recvTransport: " + (recvTransport != null ? "exists" : "NULL"));
        Log.d(TAG, "recvTransportInfo: " + (recvTransportInfo != null ? "exists" : "NULL"));
        
        if (recvTransport == null || recvTransportInfo == null) {
            Log.w(TAG, "⚠️ Receive transport not ready, will retry in 500ms");
            // Retry after a delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (recvTransport != null && recvTransportInfo != null) {
                    Log.d(TAG, "Retrying consume for producerId: " + producerId);
                    consume(producerId, rtpCapabilities);
                } else {
                    Log.e(TAG, "✗ Receive transport still not ready after retry for producerId: " + producerId);
                }
            }, 500);
            return;
        }
        
        try {
            String transportId = recvTransportInfo.getString("id");
            Log.d(TAG, "Creating consumer with transportId: " + transportId + ", producerId: " + producerId);
            // Request consumer from server with transport ID
            socketManager.createSFUConsumer(roomId, peerId, transportId, producerId, rtpCapabilities);
            Log.d(TAG, "✓ Consumer creation request sent for producerId: " + producerId);
        } catch (Exception e) {
            Log.e(TAG, "✗ Error consuming producer: " + producerId, e);
            e.printStackTrace();
        }
        Log.d(TAG, "═══════════════════════════════════════════════════════════");
    }

    /**
     * Get RTP parameters from RtpSender
     * Extract actual parameters from SDP after offer is created
     */
    private JSONObject getRtpParametersFromSender(RtpSender sender, String kind) {
        JSONObject rtpParameters = new JSONObject();
        try {
            // CRITICAL: Parse SDP from local description to extract actual codecs
            SessionDescription localDesc = sendTransport.getLocalDescription();
            if (localDesc == null || localDesc.description == null) {
                Log.e(TAG, "Local description is null, cannot extract RTP parameters");
                return getSimplifiedRtpParameters(kind);
            }
            
            String sdp = localDesc.description;
            Log.d(TAG, "Parsing SDP for " + kind + " to extract RTP parameters");
            
            org.json.JSONArray codecs = new org.json.JSONArray();
            org.json.JSONArray headerExtensions = new org.json.JSONArray();
            org.json.JSONArray encodings = new org.json.JSONArray();
            
            // Parse SDP to extract codecs
            String[] lines = sdp.split("\r\n");
            String mediaType = kind.equals("audio") ? "audio" : "video";
            boolean inMediaSection = false;
            long ssrc = 0;
            
            for (String line : lines) {
                if (line.startsWith("m=" + mediaType)) {
                    inMediaSection = true;
                    // Parse payload types from m= line: m=audio 9 UDP/TLS/RTP/SAVPF 111 103 104 9 0 8 106 105 13 110 112 113 126
                    String[] parts = line.split(" ");
                    if (parts.length > 3) {
                        for (int i = 3; i < parts.length; i++) {
                            try {
                                int payloadType = Integer.parseInt(parts[i]);
                                // Find codec info for this payload type
                                JSONObject codec = findCodecInSDP(sdp, payloadType, kind);
                                if (codec != null && isCodecSupported(codec.optString("mimeType", ""))) {
                                    codecs.put(codec);
                                    Log.d(TAG, "Added supported codec: " + codec.optString("mimeType") + " (payloadType: " + payloadType + ")");
                                } else {
                                    Log.d(TAG, "Skipped unsupported codec for payloadType: " + payloadType);
                                }
                            } catch (NumberFormatException e) {
                                // Skip non-numeric parts
                            }
                        }
                    }
                } else if (line.startsWith("m=")) {
                    inMediaSection = false;
                } else if (inMediaSection) {
                    // Extract SSRC
                    if (line.startsWith("a=ssrc:")) {
                        String[] parts = line.split(" ");
                        if (parts.length > 0) {
                            String ssrcStr = parts[0].substring(7); // Remove "a=ssrc:"
                            try {
                                ssrc = Long.parseLong(ssrcStr);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                    }
                    // Extract header extensions (only supported ones)
                    if (line.startsWith("a=extmap:")) {
                        // Format: a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level
                        String[] parts = line.substring(9).split(" ");
                        if (parts.length >= 2) {
                            try {
                                String uri = parts[1];
                                // Only add if supported by Mediasoup
                                if (isHeaderExtensionSupported(uri)) {
                                    JSONObject ext = new JSONObject();
                                    ext.put("id", Integer.parseInt(parts[0]));
                                    ext.put("uri", uri);
                                    headerExtensions.put(ext);
                                } else {
                                    Log.d(TAG, "Skipping unsupported header extension: " + uri);
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                    }
                }
            }
            
            // If no codecs found, use fallback
            if (codecs.length() == 0) {
                Log.w(TAG, "No codecs found in SDP, using fallback");
                return getSimplifiedRtpParameters(kind);
            }
            
            // Create encoding with SSRC
            JSONObject encoding = new JSONObject();
            if (ssrc > 0) {
                encoding.put("ssrc", ssrc);
            } else {
                // Get SSRC from sender parameters as fallback
                RtpParameters params = sender.getParameters();
                if (params.encodings != null && params.encodings.size() > 0 && params.encodings.get(0).ssrc != null) {
                    encoding.put("ssrc", params.encodings.get(0).ssrc);
                } else {
                    // Generate random SSRC as last resort
                    encoding.put("ssrc", (int)(Math.random() * 1000000));
                }
            }
            encodings.put(encoding);
            
            rtpParameters.put("codecs", codecs);
            rtpParameters.put("headerExtensions", headerExtensions);
            rtpParameters.put("encodings", encodings);
            rtpParameters.put("rtcp", new JSONObject());
            
            Log.d(TAG, "Extracted RTP parameters: " + codecs.length() + " codecs, " + encodings.length() + " encodings");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting RTP parameters from sender", e);
            e.printStackTrace();
            // Fallback to simplified version
            return getSimplifiedRtpParameters(kind);
        }
        return rtpParameters;
    }
    
    /**
     * Find codec information in SDP for a given payload type
     * Only returns codecs supported by Mediasoup
     */
    private JSONObject findCodecInSDP(String sdp, int payloadType, String kind) {
        try {
            String[] lines = sdp.split("\r\n");
            String mediaType = kind.equals("audio") ? "audio" : "video";
            boolean inMediaSection = false;
            
            JSONObject codec = new JSONObject();
            codec.put("payloadType", payloadType);
            
            for (String line : lines) {
                if (line.startsWith("m=" + mediaType)) {
                    inMediaSection = true;
                } else if (line.startsWith("m=")) {
                    inMediaSection = false;
                } else if (inMediaSection && line.startsWith("a=rtpmap:" + payloadType + " ")) {
                    // Format: a=rtpmap:111 opus/48000/2
                    String[] parts = line.substring(10).split(" ");
                    if (parts.length >= 2) {
                        String[] codecParts = parts[1].split("/");
                        if (codecParts.length >= 2) {
                            String mimeType = codecParts[0].toLowerCase();
                            int clockRate = Integer.parseInt(codecParts[1]);
                            String fullMimeType = kind + "/" + mimeType;
                            
                            // CRITICAL: Only include codecs supported by Mediasoup
                            if (!isCodecSupported(fullMimeType)) {
                                Log.d(TAG, "Skipping unsupported codec: " + fullMimeType);
                                return null;
                            }
                            
                            codec.put("mimeType", fullMimeType);
                            codec.put("clockRate", clockRate);
                            
                            if (codecParts.length >= 3) {
                                codec.put("channels", Integer.parseInt(codecParts[2]));
                            } else if (kind.equals("audio")) {
                                // Default to 2 channels for audio if not specified
                                codec.put("channels", 2);
                            }
                            
                            return codec;
                        }
                    }
                }
            }
            
            // Fallback: create default codec (only if supported)
            if (kind.equals("audio")) {
                codec.put("mimeType", "audio/opus");
                codec.put("clockRate", 48000);
                codec.put("channels", 2);
            } else {
                codec.put("mimeType", "video/VP8");
                codec.put("clockRate", 90000);
            }
            
            return codec;
        } catch (Exception e) {
            Log.e(TAG, "Error finding codec in SDP", e);
            return null;
        }
    }
    
    /**
     * Check if a codec is supported by Mediasoup
     * Mediasoup supports:
     * Audio: opus (ONLY opus is supported for audio)
     * Video: VP8, VP9, H264, AV1
     * Note: PCMA, PCMU, G722 are NOT supported by Mediasoup
     */
    private boolean isCodecSupported(String mimeType) {
        if (mimeType == null) return false;
        
        String lower = mimeType.toLowerCase();
        
        // Audio codecs - ONLY opus is supported
        if (lower.startsWith("audio/")) {
            String codec = lower.substring(6);
            return codec.equals("opus");
        }
        
        // Video codecs
        if (lower.startsWith("video/")) {
            String codec = lower.substring(6);
            return codec.equals("vp8") || 
                   codec.equals("vp9") || 
                   codec.equals("h264") || 
                   codec.equals("av1");
        }
        
        return false;
    }
    
    /**
     * Check if a header extension URI is supported by Mediasoup
     * Mediasoup supports standard RTP header extensions
     */
    private boolean isHeaderExtensionSupported(String uri) {
        if (uri == null) return false;
        
        String lower = uri.toLowerCase();
        
        // Standard RTP header extensions supported by Mediasoup
        return lower.contains("urn:ietf:params:rtp-hdrext") ||
               lower.contains("urn:3gpp:video-orientation") ||
               lower.contains("urn:ietf:params:rtp-hdrext:toffset") ||
               lower.contains("urn:ietf:params:rtp-hdrext:abs-send-time") ||
               lower.contains("urn:ietf:params:rtp-hdrext:transport-cc") ||
               lower.contains("urn:ietf:params:rtp-hdrext:playout-delay") ||
               lower.contains("urn:ietf:params:rtp-hdrext:rtp-stream-id") ||
               lower.contains("urn:ietf:params:rtp-hdrext:repaired-rtp-stream-id") ||
               lower.contains("urn:ietf:params:rtp-hdrext:mid") ||
               lower.contains("urn:ietf:params:rtp-hdrext:rtp-stream-id") ||
               lower.contains("urn:ietf:params:rtp-hdrext:frame-marking") ||
               lower.contains("urn:ietf:params:rtp-hdrext:ssrc-audio-level") ||
               lower.contains("urn:ietf:params:rtp-hdrext:video-orientation") ||
               lower.contains("urn:ietf:params:rtp-hdrext:color-space");
    }
    
    /**
     * Simplified RTP parameters (fallback)
     */
    private JSONObject getSimplifiedRtpParameters(String kind) {
        JSONObject rtpParameters = new JSONObject();
        try {
            org.json.JSONArray codecs = new org.json.JSONArray();
            org.json.JSONArray encodings = new org.json.JSONArray();
            
            JSONObject codec = new JSONObject();
            if ("audio".equals(kind)) {
                codec.put("mimeType", "audio/opus");
                codec.put("payloadType", 111);
                codec.put("clockRate", 48000);
                codec.put("channels", 2);
            } else if ("video".equals(kind)) {
                codec.put("mimeType", "video/VP8");
                codec.put("payloadType", 96);
                codec.put("clockRate", 90000);
            }
            codecs.put(codec);
            
            JSONObject encoding = new JSONObject();
            encoding.put("ssrc", (int)(Math.random() * 1000000));
            encodings.put(encoding);
            
            rtpParameters.put("codecs", codecs);
            rtpParameters.put("headerExtensions", new org.json.JSONArray());
            rtpParameters.put("encodings", encodings);
            rtpParameters.put("rtcp", new JSONObject());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating simplified RTP parameters", e);
        }
        return rtpParameters;
    }
    
    /**
     * Add transceiver to receive transport for consumer
     * With Mediasoup, we add transceiver and configure with RTP parameters
     */
    private void addConsumerTransceiver(String producerId, JSONObject rtpParameters, String kind) {
        if (recvTransport == null) {
            Log.e(TAG, "Receive transport not ready for consumer");
            return;
        }
        
        try {
            Log.d(TAG, "Adding transceiver for consumer: producerId=" + producerId + ", kind=" + kind);
            
            // Add transceiver with RECVONLY direction
            RtpTransceiver transceiver = recvTransport.addTransceiver(
                kind.equals("audio") ? 
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO : 
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            );
            
            Log.d(TAG, "Transceiver added: " + (transceiver != null ? "SUCCESS" : "FAILED"));
            
            // Store transceiver in consumer for later use
            Consumer consumer = consumers.get(producerId);
            if (consumer != null) {
                consumer.transceiver = transceiver;
            }
            
            // Create SDP offer to trigger negotiation
            recvTransport.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription offer) {
                    try {
                        Log.d(TAG, "Consumer SDP offer created, setting local description");
                        // Set local description (offer)
                        recvTransport.setLocalDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sdp) {}
                            @Override
                            public void onSetSuccess() {
                                Log.d(TAG, "Consumer local description set, creating answer from RTP parameters");
                                Log.d(TAG, "Offer SDP length: " + (offer.description != null ? offer.description.length() : 0));
                                Log.d(TAG, "RTP parameters: " + rtpParameters.toString());
                                
                                // With Mediasoup, we use the offer as answer with minimal modifications
                                // The RTP parameters are used by Mediasoup server-side, not for SDP creation
                                String sdpAnswer = createSimpleSdpAnswer(offer.description, kind, rtpParameters);
                                
                                if (sdpAnswer != null && !sdpAnswer.isEmpty()) {
                                    Log.d(TAG, "SDP answer created successfully, length: " + sdpAnswer.length());
                                    try {
                                        SessionDescription answer = new SessionDescription(
                                            SessionDescription.Type.ANSWER, sdpAnswer
                                        );
                                        Log.d(TAG, "SessionDescription created, setting remote description");
                                        recvTransport.setRemoteDescription(new SdpObserver() {
                                            @Override
                                            public void onCreateSuccess(SessionDescription sdp) {}
                                            @Override
                                            public void onSetSuccess() {
                                                Log.d(TAG, "Consumer SDP answer set successfully, track should come via onAddTrack");
                                                
                                                // CRITICAL: Resume consumer after SDP is set
                                                // This tells Mediasoup server that client is ready to receive media
                                                Consumer consumer = consumers.get(producerId);
                                                if (consumer != null && consumer.id != null) {
                                                    Log.d(TAG, "Resuming consumer: " + consumer.id + " for producerId: " + producerId);
                                                    try {
                                                        socketManager.resumeSFUConsumer(roomId, peerId, consumer.id);
                                                        Log.d(TAG, "Resume consumer request sent successfully");
                                                    } catch (Exception e) {
                                                        Log.e(TAG, "Error resuming consumer", e);
                                                        e.printStackTrace();
                                                    }
                                                } else {
                                                    Log.w(TAG, "Cannot resume consumer - consumer not found for producerId: " + producerId);
                                                    Log.w(TAG, "Available consumers: " + consumers.keySet());
                                                    if (consumer == null) {
                                                        Log.w(TAG, "Consumer object is null");
                                                    } else if (consumer.id == null) {
                                                        Log.w(TAG, "Consumer.id is null");
                                                    }
                                                }
                                                
                                                // Try to get track from transceiver immediately (might be available already)
                                                try {
                                                    if (recvTransport != null && transceiver != null) {
                                                        RtpReceiver receiver = transceiver.getReceiver();
                                                        if (receiver != null) {
                                                            MediaStreamTrack track = receiver.track();
                                                            if (track != null) {
                                                                Log.d(TAG, "Track found in transceiver immediately: kind=" + track.kind() + ", id=" + track.id());
                                                                if (consumer != null) {
                                                                    consumer.receiver = receiver;
                                                                    consumer.track = track;
                                                                    if (listener != null) {
                                                                        Log.d(TAG, "Calling onRemoteTrack immediately for producerId: " + producerId);
                                                                        listener.onRemoteTrack(producerId, track);
                                                                    }
                                                                }
                                                            } else {
                                                                Log.d(TAG, "Track not yet available in transceiver, waiting for onAddTrack");
                                                            }
                                                        } else {
                                                            Log.d(TAG, "Receiver not yet available in transceiver");
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Error checking transceiver for track", e);
                                                }
                                                
                                                // Also check if track is already available (might have been added before SDP was set)
                                                if (consumer != null && consumer.track != null) {
                                                    Log.d(TAG, "Track already available for consumer, calling onRemoteTrack");
                                                    if (listener != null) {
                                                        listener.onRemoteTrack(producerId, consumer.track);
                                                    }
                                                } else {
                                                    Log.d(TAG, "Track not yet available, waiting for onAddTrack callback");
                                                }
                                            }
                                            @Override
                                            public void onCreateFailure(String error) {}
                                            @Override
                                            public void onSetFailure(String error) {
                                                Log.e(TAG, "Error setting remote description for consumer: " + error);
                                            }
                                        }, answer);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error creating SessionDescription from SDP answer", e);
                                        e.printStackTrace();
                                    }
                                } else {
                                    Log.e(TAG, "Failed to create SDP answer");
                                    Log.e(TAG, "Offer SDP: " + (offer.description != null ? offer.description.substring(0, Math.min(500, offer.description.length())) : "NULL"));
                                }
                            }
                            @Override
                            public void onCreateFailure(String error) {}
                            @Override
                            public void onSetFailure(String error) {
                                Log.e(TAG, "Error setting local description for consumer: " + error);
                            }
                        }, offer);
                    } catch (Exception e) {
                        Log.e(TAG, "Error creating consumer SDP offer", e);
                        e.printStackTrace();
                    }
                }
                @Override
                public void onSetSuccess() {}
                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "Error creating consumer SDP offer: " + error);
                }
                @Override
                public void onSetFailure(String error) {}
            }, new MediaConstraints());
        } catch (Exception e) {
            Log.e(TAG, "Error adding consumer transceiver", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Create SDP answer from RTP parameters by modifying the offer SDP
     * This ensures BUNDLE compatibility
     */
    private String createSdpAnswerFromRtpParameters(JSONObject rtpParameters, String kind, String offerSdp) {
        try {
            if (offerSdp == null || offerSdp.isEmpty()) {
                Log.e(TAG, "Offer SDP is null or empty");
                return null;
            }
            
            // Extract codec info from RTP parameters
            org.json.JSONArray codecs = rtpParameters.getJSONArray("codecs");
            if (codecs.length() == 0) {
                Log.w(TAG, "No codecs in RTP parameters");
                return null;
            }
            
            JSONObject codec = codecs.getJSONObject(0);
            String mimeType = codec.getString("mimeType");
            int payloadType = codec.getInt("payloadType");
            int clockRate = codec.getInt("clockRate");
            
            Log.d(TAG, "Creating SDP answer: kind=" + kind + ", mimeType=" + mimeType + ", payloadType=" + payloadType);
            
            // Extract SSRC from encodings
            org.json.JSONArray encodings = rtpParameters.getJSONArray("encodings");
            long ssrc = 0;
            if (encodings.length() > 0) {
                JSONObject encoding = encodings.getJSONObject(0);
                if (encoding.has("ssrc")) {
                    ssrc = encoding.getLong("ssrc");
                }
            }
            
            // Parse offer SDP and modify it to create answer
            String[] lines = offerSdp.split("\r\n");
            StringBuilder answerSdp = new StringBuilder();
            String mediaType = kind.equals("audio") ? "audio" : "video";
            boolean inMediaSection = false;
            boolean mediaSectionProcessed = false;
            int mediaSectionIndex = -1;
            
            // First pass: find media section and build answer
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.startsWith("m=" + mediaType)) {
                    inMediaSection = true;
                    mediaSectionProcessed = true;
                    mediaSectionIndex = i;
                    // Modify m= line to include only the payload type from RTP parameters
                    answerSdp.append("m=").append(mediaType).append(" 9 UDP/TLS/RTP/SAVPF ").append(payloadType).append("\r\n");
                } else if (line.startsWith("m=")) {
                    inMediaSection = false;
                    answerSdp.append(line).append("\r\n");
                } else if (inMediaSection) {
                    // Skip existing rtpmap and ssrc lines, we'll add new ones
                    if (line.startsWith("a=rtpmap:") || line.startsWith("a=ssrc:")) {
                        continue;
                    }
                    // Keep other attributes (ice-ufrag, ice-pwd, fingerprint, setup, mid, etc.)
                    answerSdp.append(line).append("\r\n");
                } else {
                    // Keep all non-media section lines
                    answerSdp.append(line).append("\r\n");
                }
            }
            
            // If media section wasn't found, we need to add it
            if (!mediaSectionProcessed) {
                Log.w(TAG, "Media section for " + mediaType + " not found in offer, adding it");
                // Find the last m= line to insert after it
                int lastMediaIndex = answerSdp.lastIndexOf("m=");
                if (lastMediaIndex >= 0) {
                    int insertIndex = answerSdp.indexOf("\r\n", lastMediaIndex) + 2;
                    String mediaSection = "m=" + mediaType + " 9 UDP/TLS/RTP/SAVPF " + payloadType + "\r\n" +
                                        "c=IN IP4 0.0.0.0\r\n" +
                                        "a=rtcp:9 IN IP4 0.0.0.0\r\n";
                    answerSdp.insert(insertIndex, mediaSection);
                } else {
                    // No media sections, add at end
                    answerSdp.append("m=").append(mediaType).append(" 9 UDP/TLS/RTP/SAVPF ").append(payloadType).append("\r\n");
                    answerSdp.append("c=IN IP4 0.0.0.0\r\n");
                    answerSdp.append("a=rtcp:9 IN IP4 0.0.0.0\r\n");
                }
            } else {
                // Add rtpmap for the codec in the correct position (after m= line)
                String codecName = mimeType.split("/")[1];
                String rtpmapLine = "a=rtpmap:" + payloadType + " " + codecName + "/" + clockRate;
                if (kind.equals("audio") && codec.has("channels")) {
                    rtpmapLine += "/" + codec.getInt("channels");
                }
                rtpmapLine += "\r\n";
                
                // Find where to insert rtpmap (after m= line and c= line)
                int mLineIndex = answerSdp.indexOf("m=" + mediaType);
                if (mLineIndex >= 0) {
                    int insertIndex = answerSdp.indexOf("\r\n", mLineIndex);
                    if (insertIndex >= 0) {
                        insertIndex = answerSdp.indexOf("\r\n", insertIndex + 2); // After c= line
                        if (insertIndex >= 0) {
                            answerSdp.insert(insertIndex + 2, rtpmapLine);
                        } else {
                            answerSdp.append(rtpmapLine);
                        }
                    } else {
                        answerSdp.append(rtpmapLine);
                    }
                } else {
                    answerSdp.append(rtpmapLine);
                }
                
                // Add SSRC if available
                if (ssrc > 0) {
                    answerSdp.append("a=ssrc:").append(ssrc).append(" cname:mediasoup\r\n");
                }
            }
            
            // Change type from offer to answer in the o= line
            String answerSdpStr = answerSdp.toString();
            answerSdpStr = answerSdpStr.replaceFirst("o=- \\d+ \\d+ IN", "o=- 0 0 IN");
            
            Log.d(TAG, "Created SDP answer from RTP parameters: " + answerSdpStr.length() + " bytes");
            Log.d(TAG, "SDP answer preview: " + answerSdpStr.substring(0, Math.min(200, answerSdpStr.length())));
            return answerSdpStr;
        } catch (Exception e) {
            Log.e(TAG, "Error creating SDP answer from RTP parameters", e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Create simple SDP answer
     * Based on Mediasoup architecture: use offer as base and modify minimally for answer
     * This is the correct approach - Mediasoup handles negotiation server-side
     */
    private String createSimpleSdpAnswer(String offerSdp, String kind, JSONObject rtpParameters) {
        try {
            if (offerSdp == null || offerSdp.isEmpty()) {
                Log.e(TAG, "Offer SDP is null or empty");
                return null;
            }
            
            Log.d(TAG, "Creating SDP answer from offer (Mediasoup approach)");
            
            // Simply change offer to answer by modifying o= line
            // Change session version from random to 0 for answer
            String answerSdp = offerSdp.replaceFirst("o=- \\d+ \\d+ IN", "o=- 0 0 IN");
            
            // Change direction from sendrecv/sendonly to recvonly for the media section
            String mediaType = kind.equals("audio") ? "audio" : "video";
            
            // Replace all direction attributes in the media section
            String[] lines = answerSdp.split("\r\n");
            StringBuilder newSdp = new StringBuilder();
            boolean inMediaSection = false;
            
            for (String line : lines) {
                if (line.startsWith("m=" + mediaType)) {
                    inMediaSection = true;
                    newSdp.append(line).append("\r\n");
                } else if (line.startsWith("m=")) {
                    inMediaSection = false;
                    newSdp.append(line).append("\r\n");
                } else if (inMediaSection) {
                    // Change direction attributes
                    if (line.startsWith("a=sendrecv") || line.startsWith("a=sendonly")) {
                        newSdp.append("a=recvonly\r\n");
                    } 
                    // CRITICAL: Fix setup attribute for DTLS negotiation
                    // If offer has a=setup:actpass, answer must be a=setup:active or a=setup:passive
                    // If offer has a=setup:active, answer must be a=setup:passive
                    // If offer has a=setup:passive, answer must be a=setup:active
                    else if (line.startsWith("a=setup:")) {
                        String setupValue = line.substring(8); // Remove "a=setup:"
                        if (setupValue.equals("actpass")) {
                            // Answerer should use 'active' when offerer uses 'actpass'
                            newSdp.append("a=setup:active\r\n");
                        } else if (setupValue.equals("active")) {
                            // Answerer should use 'passive' when offerer uses 'active'
                            newSdp.append("a=setup:passive\r\n");
                        } else if (setupValue.equals("passive")) {
                            // Answerer should use 'active' when offerer uses 'passive'
                            newSdp.append("a=setup:active\r\n");
                        } else {
                            // Fallback: use active
                            newSdp.append("a=setup:active\r\n");
                        }
                    } else {
                        newSdp.append(line).append("\r\n");
                    }
                } else {
                    // Handle setup attribute outside media section (shouldn't happen, but just in case)
                    if (line.startsWith("a=setup:")) {
                        String setupValue = line.substring(8);
                        if (setupValue.equals("actpass")) {
                            newSdp.append("a=setup:active\r\n");
                        } else if (setupValue.equals("active")) {
                            newSdp.append("a=setup:passive\r\n");
                        } else if (setupValue.equals("passive")) {
                            newSdp.append("a=setup:active\r\n");
                        } else {
                            newSdp.append("a=setup:active\r\n");
                        }
                    } else {
                        newSdp.append(line).append("\r\n");
                    }
                }
            }
            
            answerSdp = newSdp.toString();
            
            Log.d(TAG, "SDP answer created: " + answerSdp.length() + " bytes");
            Log.d(TAG, "SDP answer preview (first 300 chars): " + answerSdp.substring(0, Math.min(300, answerSdp.length())));
            return answerSdp;
        } catch (Exception e) {
            Log.e(TAG, "Error creating simple SDP answer", e);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Cleanup SFU resources
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up SFU resources");
        
        // Close producers
        for (RtpSender sender : producers.values()) {
            try {
                sendTransport.removeTrack(sender);
            } catch (Exception e) {
                Log.e(TAG, "Error removing producer", e);
            }
        }
        producers.clear();
        
        // Close consumers
        consumers.clear();
        
        // Close transports
        if (sendTransport != null) {
            sendTransport.close();
            sendTransport = null;
        }
        if (recvTransport != null) {
            recvTransport.close();
            recvTransport = null;
        }
        
        // Remove peer from SFU room
        socketManager.removeSFUPeer(roomId, peerId);
        
        // Remove socket listeners
        socketManager.off("sfu-room-created");
        socketManager.off("sfu-router-capabilities");
        socketManager.off("sfu-transport-created");
        socketManager.off("sfu-transport-connected");
        socketManager.off("sfu-producer-created");
        socketManager.off("sfu-consumer-created");
        socketManager.off("sfu-new-producer");
        socketManager.off("sfu-error");
    }
}

