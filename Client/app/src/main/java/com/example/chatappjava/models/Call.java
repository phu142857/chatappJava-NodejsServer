package com.example.chatappjava.models;

import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Call {
    private String callId;
    private String type; // "audio" or "video"
    private String chatId;
    private String chatName;
    private String chatType; // "private" or "group"
    private final List<CallParticipant> participants;
    private String status; // "initiated", "ringing", "active", "ended", "declined", "missed", "cancelled"
    private long startedAt;
    private long endedAt;
    private int duration; // in seconds
    private boolean isGroupCall;
    private String callerId;
    private String callerName;
    private String callerAvatar;
    
    // Constructors
    public Call() {
        participants = new ArrayList<>();
    }
    
    public Call(String callId, String type, String chatId) {
        this.callId = callId;
        this.type = type;
        this.chatId = chatId;
        this.participants = new ArrayList<>();
    }
    
    // Create Call from JSON
    public static Call fromJson(JSONObject json) throws JSONException {
        Call call = new Call();
        
        call.callId = json.optString("callId", "");
        call.type = json.optString("type", "video");
        call.chatId = json.optString("chatId", "");
        call.status = json.optString("status", "initiated");
        // Handle timestamps that might be either numbers (millis) or ISO strings
        call.startedAt = parseTimestamp(json, "startedAt", 0);
        call.endedAt = parseTimestamp(json, "endedAt", 0);
        call.duration = json.optInt("duration", 0);
        call.isGroupCall = json.optBoolean("isGroupCall", false);
        
        // Parse chat information
        if (json.has("chatId")) {
            if (json.get("chatId") instanceof JSONObject) {
                JSONObject chatObj = json.getJSONObject("chatId");
                call.chatName = chatObj.optString("name", "");
                call.chatType = chatObj.optString("type", "private");
            } else {
                // If chatId is just a string, we'll get the chat name from participants
                call.chatId = json.optString("chatId", "");
            }
        }
        
        // Parse participants
        if (json.has("participants")) {
            JSONArray participantsArray = json.getJSONArray("participants");
            for (int i = 0; i < participantsArray.length(); i++) {
                JSONObject participantJson = participantsArray.getJSONObject(i);
                CallParticipant participant = CallParticipant.fromJson(participantJson);
                call.participants.add(participant);
                
                // Set caller information
                if (participant.isCaller()) {
                    call.callerId = participant.getUserId();
                    call.callerName = participant.getUsername();
                    call.callerAvatar = participant.getAvatar();
                }
            }
        }
        
        return call;
    }

    private static long parseTimestamp(JSONObject json, String key, long defaultValue) {
        try {
            if (!json.has(key) || json.isNull(key)) return defaultValue;
            Object value = json.get(key);
            if (value instanceof Number) {
                // Assume milliseconds
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                String iso = (String) value;
                if (iso.isEmpty()) return defaultValue;
                try {
                    // Prefer java.time when available
                    return java.time.Instant.parse(iso).toEpochMilli();
                } catch (Throwable t) {
                    try {
                        @SuppressLint("SimpleDateFormat") java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        return sdf.parse(iso).getTime();
                    } catch (Throwable ignored) {
                        return defaultValue;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fallback below
        }
        return defaultValue;
    }
    
    // Convert to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("callId", callId);
        json.put("type", type);
        json.put("chatId", chatId);
        json.put("status", status);
        json.put("startedAt", startedAt);
        json.put("endedAt", endedAt);
        json.put("duration", duration);
        json.put("isGroupCall", isGroupCall);
        
        // Add chat information
        JSONObject chatObj = new JSONObject();
        chatObj.put("name", chatName);
        chatObj.put("type", chatType);
        json.put("chatId", chatObj);
        
        // Add participants
        JSONArray participantsArray = new JSONArray();
        for (CallParticipant participant : participants) {
            participantsArray.put(participant.toJson());
        }
        json.put("participants", participantsArray);
        
        return json;
    }
    
    // Getters and Setters
    public String getCallId() { return callId; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getChatId() { return chatId; }
    
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public int getDuration() { return duration; }

    public void setDuration(int duration) { this.duration = duration; }
    
    public boolean isGroupCall() { return isGroupCall; }
    
    public String getCallerAvatar() { return callerAvatar; }
    
    // Helper methods
    public boolean isVideoCall() {
        return "video".equals(type);
    }

    public boolean isEnded() {
        return "ended".equals(status);
    }

    @SuppressLint("DefaultLocale")
    public String getFormattedDuration() {
        if (duration <= 0) return "0:00";
        
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    public String getFormattedTime() {
        long time = endedAt > 0 ? endedAt : startedAt;
        if (time <= 0) return "--:--";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(time));
    }
    
    public String getFormattedTimeWithDate() {
        long time = endedAt > 0 ? endedAt : startedAt;
        if (time <= 0) return "--:-- --/--/----";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(time));
    }
    
    public String getFormattedDate() {
        long time = endedAt > 0 ? endedAt : startedAt;
        if (time <= 0) return "--/--/----";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(time));
    }
    
    public String getDisplayName(String currentUserId) {
        if (isGroupCall && chatName != null && !chatName.isEmpty()) {
            return chatName;
        } else {
            // For private calls, show the other participant's name (not the current user)
            String otherParticipantName = getOtherParticipantName(currentUserId);
            if (otherParticipantName != null && !otherParticipantName.isEmpty()) {
                return otherParticipantName;
            } else if (callerName != null && !callerName.isEmpty()) {
                return callerName;
            } else {
                return "Unknown";
            }
        }
    }
    
    public String getOtherParticipantName(String currentUserId) {
        // Find the participant who is not the current user
        for (CallParticipant participant : participants) {
            if (!participant.getUserId().equals(currentUserId) && participant.getUsername() != null && !participant.getUsername().isEmpty()) {
                return participant.getUsername();
            }
        }
        return null;
    }
    
    public String getOtherParticipantAvatar(String currentUserId) {
        // Find the participant who is not the current user
        for (CallParticipant participant : participants) {
            if (!participant.getUserId().equals(currentUserId)) {
                return participant.getAvatar();
            }
        }
        return null;
    }
    
    // Keep old methods for backward compatibility
    public String getDisplayName() {
        return getDisplayName("");
    }
    public String getCallTypeIcon() {
        return isVideoCall() ? "📹" : "📞";
    }
    
    public String getStatusText() {
        switch (status) {
            case "ended":
                return "Completed";
            case "declined":
                return "Declined";
            case "missed":
                return "Missed";
            case "cancelled":
                return "Cancelled";
            default:
                return "Unknown";
        }
    }
}
