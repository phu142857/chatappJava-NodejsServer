package com.example.chatappjava.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.VideoTrack;

public class CallParticipant {
    private String userId;
    private String username;
    private String avatar;
    private boolean audioMuted;
    private boolean videoMuted;
    private boolean screenSharing;
    private boolean isLocal;
    private boolean isCaller;
    private VideoTrack videoTrack;
    private String connectionQuality;
    private String status; // invited, notified, ringing, connected, declined, missed, left

    public CallParticipant() {
        this.audioMuted = false;
        this.videoMuted = true;  // Video off by default
        this.screenSharing = false;
        this.isLocal = false;
        this.isCaller = false;
        this.connectionQuality = "good";
        this.status = "invited";
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isAudioMuted() {
        return audioMuted;
    }

    public void setAudioMuted(boolean audioMuted) {
        this.audioMuted = audioMuted;
    }

    public boolean isVideoMuted() {
        return videoMuted;
    }

    public void setVideoMuted(boolean videoMuted) {
        this.videoMuted = videoMuted;
    }

    public boolean isScreenSharing() {
        return screenSharing;
    }

    public void setScreenSharing(boolean screenSharing) {
        this.screenSharing = screenSharing;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public void setLocal(boolean local) {
        isLocal = local;
    }

    public VideoTrack getVideoTrack() {
        return videoTrack;
    }

    public void setVideoTrack(VideoTrack videoTrack) {
        this.videoTrack = videoTrack;
    }

    public String getConnectionQuality() {
        return connectionQuality;
    }

    public void setConnectionQuality(String connectionQuality) {
        this.connectionQuality = connectionQuality;
    }

    public boolean isCaller() {
        return isCaller;
    }

    public void setCaller(boolean caller) {
        isCaller = caller;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Create CallParticipant from JSON object
     */
    public static CallParticipant fromJson(JSONObject json) throws JSONException {
        CallParticipant participant = new CallParticipant();
        
        // Handle userId - can be a string or an object with _id
        if (json.has("userId")) {
            Object userIdObj = json.get("userId");
            if (userIdObj instanceof JSONObject) {
                JSONObject userObj = (JSONObject) userIdObj;
                participant.setUserId(userObj.optString("_id", ""));
                participant.setUsername(userObj.optString("username", ""));
                participant.setAvatar(userObj.optString("avatar", ""));
            } else {
                participant.setUserId(json.optString("userId", ""));
            }
        }
        
        // Parse other fields
        participant.setUsername(json.optString("username", participant.getUsername()));
        participant.setAvatar(json.optString("avatar", participant.getAvatar()));
        participant.setAudioMuted(json.optBoolean("audioMuted", false));
        participant.setVideoMuted(json.optBoolean("videoMuted", true));
        participant.setScreenSharing(json.optBoolean("screenSharing", false));
        participant.setCaller(json.optBoolean("isCaller", false));
        participant.setStatus(json.optString("status", "invited"));
        participant.setConnectionQuality(json.optString("connectionQuality", "good"));
        
        return participant;
    }

    /**
     * Convert CallParticipant to JSON object
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("username", username);
        json.put("avatar", avatar);
        json.put("audioMuted", audioMuted);
        json.put("videoMuted", videoMuted);
        json.put("screenSharing", screenSharing);
        json.put("isCaller", isCaller);
        json.put("status", status);
        json.put("connectionQuality", connectionQuality);
        return json;
    }
}
