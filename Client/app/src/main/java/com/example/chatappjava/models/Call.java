package com.example.chatappjava.models;

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
    private List<CallParticipant> participants;
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
        call.startedAt = json.optLong("startedAt", System.currentTimeMillis());
        call.endedAt = json.optLong("endedAt", 0);
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
    public void setCallId(String callId) { this.callId = callId; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    
    public String getChatName() { return chatName; }
    public void setChatName(String chatName) { this.chatName = chatName; }
    
    public String getChatType() { return chatType; }
    public void setChatType(String chatType) { this.chatType = chatType; }
    
    public List<CallParticipant> getParticipants() { return participants; }
    public void setParticipants(List<CallParticipant> participants) { this.participants = participants; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
    
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    
    public boolean isGroupCall() { return isGroupCall; }
    public void setGroupCall(boolean groupCall) { isGroupCall = groupCall; }
    
    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }
    
    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }
    
    public String getCallerAvatar() { return callerAvatar; }
    public void setCallerAvatar(String callerAvatar) { this.callerAvatar = callerAvatar; }
    
    // Helper methods
    public boolean isVideoCall() {
        return "video".equals(type);
    }
    
    public boolean isAudioCall() {
        return "audio".equals(type);
    }
    
    public boolean isEnded() {
        return "ended".equals(status);
    }
    
    public boolean isDeclined() {
        return "declined".equals(status);
    }
    
    public boolean isMissed() {
        return "missed".equals(status);
    }
    
    public boolean isCancelled() {
        return "cancelled".equals(status);
    }
    
    public String getFormattedDuration() {
        if (duration <= 0) return "0:00";
        
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    public String getFormattedTime() {
        long time = endedAt > 0 ? endedAt : startedAt;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(time));
    }
    
    public String getFormattedDate() {
        long time = endedAt > 0 ? endedAt : startedAt;
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
    
    public String getOtherParticipantName() {
        return getOtherParticipantName("");
    }
    
    public String getOtherParticipantAvatar() {
        return getOtherParticipantAvatar("");
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
