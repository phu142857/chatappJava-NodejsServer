package com.example.chatappjava.network;

import android.util.Log;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.ui.call.RingingActivity;
import org.json.JSONException;
import org.json.JSONObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java.net.URISyntaxException;

/**
 * SocketManager handles Socket.io connections and real-time events
 */
public class SocketManager {
    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private boolean isConnected = false;
    private String currentToken;
    private String currentUserId;
    private android.content.Context appContext; // Store context for sync operations
    // Active call guard to prevent duplicate ringing/offer handling across screens
    private String activeCallId;
    
    // Callback interfaces
    public interface IncomingCallListener {
        void onIncomingCall(String callId, User caller, String chatId, String callType);
    }
    
    public interface CallStatusListener {
        void onCallAccepted(String callId);
        void onCallDeclined(String callId);
        void onCallEnded(String callId);
    }
    

    public interface CallRoomListener {
        void onCallRoomJoined(String callId, org.json.JSONArray iceServers);
    }
    
    public interface MemberRemovedListener {
        void onMemberRemoved(String chatId, String chatName);
        void onMemberRemovedFromGroup(String chatId, String removedUserId, int totalMembers);
    }
    
    public interface MessageListener {
        void onPrivateMessage(org.json.JSONObject messageJson);
        void onGroupMessage(org.json.JSONObject messageJson);
        void onMessageEdited(org.json.JSONObject messageJson);
        void onMessageDeleted(org.json.JSONObject messageMetaJson);
        void onReactionUpdated(org.json.JSONObject reactionJson);
    }
    
    // Realtime sync event listeners
    public interface RealtimeSyncListener {
        void onNewPost(org.json.JSONObject postJson);
        void onAvatarChanged(String userId, String newAvatarUrl);
    }
    
    private RealtimeSyncListener realtimeSyncListener;
    
    private IncomingCallListener incomingCallListener;
    private CallStatusListener callStatusListener;
    private CallRoomListener callRoomListener;
    private MemberRemovedListener memberRemovedListener;
    private MessageListener messageListener; // legacy single-listener (kept for backward compatibility)

    // New: support multiple listeners for message to avoid clobbering between screens
    private final java.util.List<MessageListener> messageListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    private SocketManager() {
        // Private constructor for singleton
    }
    
    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }
    
    /**
     * Connect to Socket.io server
     */
    public void connect(String token, String userId, android.content.Context context) {
        if (isConnected && token.equals(currentToken)) {
            Log.d(TAG, "Already connected with same token");
            return;
        }
        
        disconnect(); // Disconnect previous connection if any
        
        currentToken = token;
        currentUserId = userId;
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
        
        try {
            IO.Options options = new IO.Options();
            java.util.Map<String, String> auth = new java.util.HashMap<>();
            auth.put("token", token);
            options.auth = auth;
            options.forceNew = true;
            options.timeout = 10000;
            
            String serverUrl = ServerConfig.getWebSocketUrl();
            Log.d(TAG, "Connecting to Socket.io server: " + serverUrl);
            
            socket = IO.socket(serverUrl, options);
            setupEventListeners();
            socket.connect();
            
        } catch (URISyntaxException e) {
            Log.e(TAG, "Failed to connect to Socket.io server", e);
        }
    }
    
    /**
     * Disconnect from Socket.io server
     */
    public void disconnect() {
        if (socket != null) {
            Log.d(TAG, "Disconnecting from Socket.io server");
            socket.disconnect();
            socket.off();
            socket = null;
        }
        isConnected = false;
        currentToken = null;
        currentUserId = null;
    }
    
    /**
     * Setup event listeners
     */
    private void setupEventListeners() {
        if (socket == null) return;
        
        // Connection events
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "Connected to Socket.io server");
                isConnected = true;
                
            }
        });
        
        socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "Disconnected from Socket.io server");
                isConnected = false;
            }
        });
        
        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.e(TAG, "Socket.io connection error: " + args[0]);
                isConnected = false;
            }
        });
        
        // Call events
        socket.on("call_room_joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    org.json.JSONArray iceServers = data.optJSONArray("iceServers");
                    Log.d(TAG, "Joined call room: " + callId + ", iceServers: " + (iceServers != null ? iceServers.length() : 0));
                    if (callRoomListener != null) {
                        callRoomListener.onCallRoomJoined(callId, iceServers);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing call_room_joined data", e);
                }
            }
        });
        socket.on("incoming_call", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    Log.d(TAG, "Received incoming call: " + data.toString());
                    
                    // CRITICAL: Ignore incoming_call for group calls
                    // Group calls use group_call_passive_alert instead
                    boolean isGroupCall = data.optBoolean("isGroupCall", false);
                    if (isGroupCall) {
                        Log.d(TAG, "Ignoring incoming_call for group call (should use group_call_passive_alert)");
                        return;
                    }
                    
                    String callId = data.getString("callId");
                    String chatId = data.getString("chatId");
                    String callType = data.getString("callType");
                    
                    // De-dup guard: only allow first handler to process a specific callId
                    synchronized (SocketManager.this) {
                        if (activeCallId != null && !activeCallId.isEmpty()) {
                            if (activeCallId.equals(callId)) {
                                Log.d(TAG, "Ignoring duplicate incoming_call for active callId: " + callId);
                                return;
                            } else {
                                // Clear old activeCallId to allow new call
                                // This fixes the issue where subsequent calls are ignored
                                Log.w(TAG, "Clearing old activeCallId (" + activeCallId + ") to allow new call: " + callId);
                                activeCallId = null;
                            }
                        }
                        activeCallId = callId;
                        Log.d(TAG, "Set activeCallId on incoming_call: " + activeCallId);
                    }

                    // Parse caller info
                    JSONObject callerJson = data.getJSONObject("caller");
                    User caller = User.fromJson(callerJson);
                    
                    if (incomingCallListener != null) {
                        incomingCallListener.onIncomingCall(callId, caller, chatId, callType);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing incoming call data", e);
                }
            }
        });
        
        socket.on("call_accepted", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    Log.d(TAG, "Call accepted: " + callId);
                    
                    if (callStatusListener != null) {
                        callStatusListener.onCallAccepted(callId);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing call accepted data", e);
                }
            }
        });
        
        socket.on("call_declined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    Log.d(TAG, "Received call_declined event: " + data.toString());
                    
                    if (callStatusListener != null) {
                        callStatusListener.onCallDeclined(callId);
                    } else {
                        Log.w(TAG, "No call status listener set for call_declined event");
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing call declined data", e);
                }
            }
        });
        
        socket.on("call_ended", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    Log.d(TAG, "Call ended: " + callId);
                    
                    if (callStatusListener != null) {
                        callStatusListener.onCallEnded(callId);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing call ended data", e);
                }
            }
        });

        // Handle member removed from group
        socket.on("member_removed", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (memberRemovedListener != null && args.length > 0) {
                    try {
                        org.json.JSONObject data = (org.json.JSONObject) args[0];
                        String chatId = data.getString("chatId");
                        String chatName = data.getString("chatName");
                        memberRemovedListener.onMemberRemoved(chatId, chatName);
                    } catch (org.json.JSONException e) {
                        Log.e(TAG, "Error parsing member_removed event", e);
                    }
                }
            }
        });

        // Handle member removed from group (for other members)
        socket.on("member_removed_from_group", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (memberRemovedListener != null && args.length > 0) {
                    try {
                        org.json.JSONObject data = (org.json.JSONObject) args[0];
                        String chatId = data.getString("chatId");
                        String removedUserId = data.getString("removedUserId");
                        int totalMembers = data.getInt("totalMembers");
                        memberRemovedListener.onMemberRemovedFromGroup(chatId, removedUserId, totalMembers);
                    } catch (org.json.JSONException e) {
                        Log.e(TAG, "Error parsing member_removed_from_group event", e);
                    }
                }
            }
        });
        
        // Messaging events
        socket.on("private_message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject message = data.getJSONObject("message");
                    // Legacy single listener
                    if (messageListener != null) messageListener.onPrivateMessage(message);
                    // Multi listeners
                    for (MessageListener l : messageListeners) {
                        try {
                            l.onPrivateMessage(message);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying private message listener", ex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing private_message", e);
                }
            }
        });
        socket.on("group_message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject message = data.getJSONObject("message");
                    if (messageListener != null) messageListener.onGroupMessage(message);
                    for (MessageListener l : messageListeners) {
                        try {
                            l.onGroupMessage(message);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying group message listener", ex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing group_message", e);
                }
            }
        });
        socket.on("message_edited", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    JSONObject message = data.getJSONObject("message");
                    if (messageListener != null) messageListener.onMessageEdited(message);
                    for (MessageListener l : messageListeners) {
                        try {
                            l.onMessageEdited(message);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying message edited listener", ex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing message_edited", e);
                }
            }
        });
        socket.on("message_deleted", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    if (messageListener != null) messageListener.onMessageDeleted(data);
                    for (MessageListener l : messageListeners) {
                        try {
                            l.onMessageDeleted(data);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying message deleted listener", ex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing message_deleted", e);
                }
            }
        });
        socket.on("reaction_updated", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    if (messageListener != null) messageListener.onReactionUpdated(data);
                    for (MessageListener l : messageListeners) {
                        try {
                            l.onReactionUpdated(data);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error notifying reaction updated listener", ex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing reaction_updated", e);
                }
            }
        });
        
        // Realtime sync events
        socket.on("new_post", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    org.json.JSONObject postJson = (org.json.JSONObject) args[0];
                    Log.d(TAG, "Received new_post event");
                    
                    // Update local cache via SyncManager
                    if (appContext != null) {
                        try {
                            com.example.chatappjava.models.Post post = 
                                com.example.chatappjava.models.Post.fromJson(postJson);
                            com.example.chatappjava.utils.PostRepository postRepo = 
                                new com.example.chatappjava.utils.PostRepository(appContext);
                            postRepo.savePost(post);
                        } catch (org.json.JSONException e) {
                            Log.e(TAG, "Error parsing post from new_post event", e);
                        }
                    }
                    
                    // Notify listener
                    if (realtimeSyncListener != null) {
                        realtimeSyncListener.onNewPost(postJson);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling new_post event", e);
                }
            }
        });
        
        socket.on("avatar_changed", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    org.json.JSONObject data = (org.json.JSONObject) args[0];
                    String userId = data.getString("userId");
                    String newAvatarUrl = data.getString("avatar");
                    Log.d(TAG, "Received avatar_changed event for user: " + userId);
                    
                    // Notify listener to update UI
                    if (realtimeSyncListener != null) {
                        realtimeSyncListener.onAvatarChanged(userId, newAvatarUrl);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling avatar_changed event", e);
                }
            }
        });
    }
    
    /**
     * Set realtime sync listener
     */
    public void setRealtimeSyncListener(RealtimeSyncListener listener) {
        this.realtimeSyncListener = listener;
    }
    
    /**
     * Remove realtime sync listener
     */
    public void removeRealtimeSyncListener() {
        this.realtimeSyncListener = null;
    }
    
    /**
     * Join a call room
     */
    public void joinCallRoom(String callId) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                socket.emit("join_call_room", data);
                Log.d(TAG, "Joined call room: " + callId);
            } catch (JSONException e) {
                Log.e(TAG, "Error joining call room", e);
            }
        }
    }
    
    /**
     * Leave a call room
     */
    public void leaveCallRoom(String callId) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                socket.emit("leave_call_room", data);
                Log.d(TAG, "Left call room: " + callId);
            } catch (JSONException e) {
                Log.e(TAG, "Error leaving call room", e);
            }
        }
    }
    
    /**
     * Send a video frame to the server
     */
    public void sendVideoFrame(String callId, String base64Frame) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                data.put("frame", base64Frame);
                data.put("timestamp", System.currentTimeMillis());
                socket.emit("video_frame", data);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending video frame", e);
            }
        }
    }
    
    
    /**
     * Interface for receiving video frames
     */
    public interface VideoFrameListener {
        void onVideoFrameReceived(String userId, String base64Frame, long timestamp);
    }
    
    private VideoFrameListener videoFrameListener;
    
    /**
     * Set the listener for video frames
     */
    public void setVideoFrameListener(VideoFrameListener listener) {
        this.videoFrameListener = listener;
        
        // Setup socket listener if connected
        if (socket != null && isConnected && listener != null) {
            socket.on("video_frame", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        JSONObject data = (JSONObject) args[0];
                        String userId = data.getString("userId");
                        String frame = data.getString("frame");
                        long timestamp = data.optLong("timestamp", System.currentTimeMillis());
                        
                        if (videoFrameListener != null) {
                            videoFrameListener.onVideoFrameReceived(userId, frame, timestamp);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error receiving video frame", e);
                    }
                }
            });
        }
    }
    
    /**
     * Remove the listener for video frames
     */
    public void removeVideoFrameListener() {
        this.videoFrameListener = null;
        if (socket != null) {
            socket.off("video_frame");
        }
    }
    
    /**
     * Send an audio frame to the server
     */
    public void sendAudioFrame(String callId, String base64Audio) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                data.put("audio", base64Audio);
                data.put("timestamp", System.currentTimeMillis());
                socket.emit("audio_frame", data);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending audio frame", e);
            }
        }
    }
    
    /**
     * Interface for receiving audio frames
     */
    public interface AudioFrameListener {
        void onAudioFrameReceived(String userId, String base64Audio, long timestamp);
    }
    
    private AudioFrameListener audioFrameListener;
    
    /**
     * Set the listener for audio frames
     */
    public void setAudioFrameListener(AudioFrameListener listener) {
        this.audioFrameListener = listener;
        
        // Setup socket listener if connected
        if (socket != null && isConnected && listener != null) {
            socket.on("audio_frame", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        JSONObject data = (JSONObject) args[0];
                        String userId = data.getString("userId");
                        String audio = data.getString("audio");
                        long timestamp = data.optLong("timestamp", System.currentTimeMillis());
                        
                        if (audioFrameListener != null) {
                            audioFrameListener.onAudioFrameReceived(userId, audio, timestamp);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error receiving audio frame", e);
                    }
                }
            });
        }
    }
    
    /**
     * Remove the listener for audio frames
     */
    public void removeAudioFrameListener() {
        this.audioFrameListener = null;
        if (socket != null) {
            socket.off("audio_frame");
        }
    }

    /**
     * Listen to a socket event with a custom listener
     */
    public void on(String event, Emitter.Listener listener) {
        if (socket != null) {
            socket.on(event, listener);
        }
    }

    /**
     * Remove a socket event listener
     */
    public void off(String event) {
        if (socket != null) {
            socket.off(event);
        }
    }


    // Active call guard APIs
    public synchronized void setActiveCallId(String callId) {
        this.activeCallId = callId;
        Log.d(TAG, "Active call set: " + callId);
    }

    public synchronized void clearActiveCallId(String callId) {
        if (this.activeCallId != null && (callId == null || this.activeCallId.equals(callId))) {
            Log.d(TAG, "Clearing active call: " + this.activeCallId);
            this.activeCallId = null;
        }
    }

    public synchronized String getActiveCallId() {
        return activeCallId;
    }

    /**
     * Reset call-related realtime state and listeners.
     * Use this when a call is ended/declined/cancelled to avoid duplicates.
     */
    public synchronized void resetActiveCall() {
        Log.d(TAG, "Resetting active call state and call listeners");
        this.activeCallId = null;
        // Clear call-related listeners only; keep message/contact listeners intact
        removeCallStatusListener();
        removeCallRoomListener();
    }
    
    // Getters and setters
    public boolean isConnected() {
        return isConnected;
    }
    
    public void setIncomingCallListener(IncomingCallListener listener) {
        this.incomingCallListener = listener;
    }
    
    public void setCallStatusListener(CallStatusListener listener) {
        this.callStatusListener = listener;
    }
    
    public void removeIncomingCallListener() {
        this.incomingCallListener = null;
    }
    
    public void removeCallStatusListener() {
        this.callStatusListener = null;
    }
    

    public void setCallRoomListener(CallRoomListener listener) {
        this.callRoomListener = listener;
    }

    public void removeCallRoomListener() {
        this.callRoomListener = null;
    }
    
    public void setMemberRemovedListener(MemberRemovedListener listener) {
        this.memberRemovedListener = listener;
    }
    
    public void removeMemberRemovedListener() {
        this.memberRemovedListener = null;
    }
    
    public void setMessageListener(MessageListener listener) {
        // legacy behavior: replace the single listener reference
        this.messageListener = listener;
    }
    
    public void removeMessageListener() {
        this.messageListener = null;
        // does not clear multi-listeners
    }

    // New multi-listener APIs
    public void addMessageListener(MessageListener listener) {
        if (listener != null && !messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }

    public void removeMessageListener(MessageListener listener) {
        if (listener != null) {
            messageListeners.remove(listener);
        }
    }
}
