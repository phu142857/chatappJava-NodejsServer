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
    
    // Callback interfaces
    public interface IncomingCallListener {
        void onIncomingCall(String callId, User caller, String chatId, String callType);
    }
    
    public interface CallStatusListener {
        void onCallAccepted(String callId);
        void onCallDeclined(String callId);
        void onCallEnded(String callId);
    }
    
    public interface WebRTCListener {
        void onWebRTCOffer(String callId, JSONObject offer);
        void onWebRTCAnswer(String callId, JSONObject answer);
        void onICECandidate(String callId, JSONObject candidate);
        void onCallSettingsUpdate(String callId, JSONObject settings);
    }

    public interface CallRoomListener {
        void onCallRoomJoined(String callId, org.json.JSONArray iceServers);
    }
    
    public interface ContactStatusListener {
        void onContactStatusChange(String userId, String status);
    }
    
    public interface MessageListener {
        void onPrivateMessage(org.json.JSONObject messageJson);
        void onGroupMessage(org.json.JSONObject messageJson);
        void onMessageEdited(org.json.JSONObject messageJson);
        void onMessageDeleted(org.json.JSONObject messageMetaJson);
        void onReactionUpdated(org.json.JSONObject reactionJson);
    }
    
    private IncomingCallListener incomingCallListener;
    private CallStatusListener callStatusListener;
    private WebRTCListener webrtcListener;
    private CallRoomListener callRoomListener;
    private ContactStatusListener contactStatusListener;
    private MessageListener messageListener;
    
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
    public void connect(String token, String userId) {
        if (isConnected && token.equals(currentToken)) {
            Log.d(TAG, "Already connected with same token");
            return;
        }
        
        disconnect(); // Disconnect previous connection if any
        
        currentToken = token;
        currentUserId = userId;
        
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
                    
                    String callId = data.getString("callId");
                    String chatId = data.getString("chatId");
                    String callType = data.getString("callType");
                    
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
        
        // Contact status change events
        socket.on("contact_status_change", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String userId = data.getString("userId");
                    String status = data.getString("status");
                    Log.d(TAG, "Received contact_status_change event: " + userId + " -> " + status);
                    Log.d(TAG, "Contact status listener is " + (contactStatusListener != null ? "set" : "null"));
                    
                    if (contactStatusListener != null) {
                        Log.d(TAG, "Calling contact status listener");
                        contactStatusListener.onContactStatusChange(userId, status);
                    } else {
                        Log.w(TAG, "No contact status listener set, ignoring event");
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing contact status change data", e);
                }
            }
        });
        
        // Messaging events
        socket.on("private_message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    if (messageListener != null) messageListener.onPrivateMessage(data.getJSONObject("message"));
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
                    if (messageListener != null) messageListener.onGroupMessage(data.getJSONObject("message"));
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
                    if (messageListener != null) messageListener.onMessageEdited(data.getJSONObject("message"));
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
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing reaction_updated", e);
                }
            }
        });
        
        // WebRTC signaling events
        socket.on("webrtc_offer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    JSONObject offer = data.getJSONObject("offer");
                    // Inject fromUserId into offer so upper layers can self-filter
                    if (data.has("fromUserId")) {
                        offer.put("fromUserId", data.getString("fromUserId"));
                    }
                    Log.d(TAG, "Received WebRTC offer for call: " + callId);
                    
                    if (webrtcListener != null) {
                        webrtcListener.onWebRTCOffer(callId, offer);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing WebRTC offer", e);
                }
            }
        });
        
        socket.on("webrtc_answer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    JSONObject answer = data.getJSONObject("answer");
                    // Inject fromUserId into answer so upper layers can self-filter
                    if (data.has("fromUserId")) {
                        answer.put("fromUserId", data.getString("fromUserId"));
                    }
                    Log.d(TAG, "Received WebRTC answer for call: " + callId);
                    
                    if (webrtcListener != null) {
                        webrtcListener.onWebRTCAnswer(callId, answer);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing WebRTC answer", e);
                }
            }
        });
        
        socket.on("webrtc_ice_candidate", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    JSONObject candidate = data.getJSONObject("candidate");
                    // Inject fromUserId into candidate so upper layers can self-filter
                    if (data.has("fromUserId")) {
                        candidate.put("fromUserId", data.getString("fromUserId"));
                    }
                    Log.d(TAG, "Received ICE candidate for call: " + callId);
                    
                    if (webrtcListener != null) {
                        webrtcListener.onICECandidate(callId, candidate);
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing ICE candidate", e);
                }
            }
        });
        
        // Call settings update event
        socket.on("call_settings_updated", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String callId = data.getString("callId");
                    JSONObject settings = data.getJSONObject("settings");
                    String fromUserId = data.optString("userId", "");
                    String currentUserId = SocketManager.this.currentUserId;
                    
                    // Only process if not from ourselves
                    if (!fromUserId.isEmpty() && !fromUserId.equals(currentUserId)) {
                        Log.d(TAG, "Received call settings update for call: " + callId + " from user: " + fromUserId);
                        
                        if (webrtcListener != null) {
                            webrtcListener.onCallSettingsUpdate(callId, settings);
                        }
                    } else {
                        Log.d(TAG, "Ignoring call settings update from self");
                    }
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing call settings update", e);
                }
            }
        });
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
     * Send WebRTC offer
     */
    public void sendWebRTCOffer(String callId, JSONObject offer) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                data.put("offer", offer);
                socket.emit("webrtc_offer", data);
                Log.d(TAG, "Sent WebRTC offer for call: " + callId);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending WebRTC offer", e);
            }
        }
    }
    
    /**
     * Send WebRTC answer
     */
    public void sendWebRTCAnswer(String callId, JSONObject answer) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                data.put("answer", answer);
                socket.emit("webrtc_answer", data);
                Log.d(TAG, "Sent WebRTC answer for call: " + callId);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending WebRTC answer", e);
            }
        }
    }
    
    /**
     * Send ICE candidate
     */
    public void sendICECandidate(String callId, JSONObject candidate) {
        if (socket != null && isConnected) {
            JSONObject data = new JSONObject();
            try {
                data.put("callId", callId);
                data.put("candidate", candidate);
                socket.emit("webrtc_ice_candidate", data);
                Log.d(TAG, "Sent ICE candidate for call: " + callId);
            } catch (JSONException e) {
                Log.e(TAG, "Error sending ICE candidate", e);
            }
        }
    }
    
    /**
     * Send call settings update
     */
    public void sendCallSettingsUpdate(JSONObject data) {
        if (socket != null && isConnected) {
            try {
                socket.emit("call_settings_update", data);
                Log.d(TAG, "Sent call settings update for call: " + data.optString("callId"));
            } catch (Exception e) {
                Log.e(TAG, "Error sending call settings update", e);
            }
        }
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
    
    public void setWebRTCListener(WebRTCListener listener) {
        this.webrtcListener = listener;
    }
    
    public void removeWebRTCListener() {
        this.webrtcListener = null;
    }

    public void setCallRoomListener(CallRoomListener listener) {
        this.callRoomListener = listener;
    }
    
    public void setContactStatusListener(ContactStatusListener listener) {
        this.contactStatusListener = listener;
    }

    public void removeCallRoomListener() {
        this.callRoomListener = null;
    }
    
    public void removeContactStatusListener() {
        this.contactStatusListener = null;
    }
    
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }
    
    public void removeMessageListener() {
        this.messageListener = null;
    }
}
