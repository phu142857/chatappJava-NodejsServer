package com.example.chatappjava.models;

import org.json.JSONException;
import org.json.JSONObject;

public class CallParticipant {
    private String userId;
    private String username;
    private String avatar;
    private String status; // "invited", "ringing", "connected", "declined", "missed", "left"
    private long joinedAt;
    private long leftAt;
    private boolean isCaller;
    
    // Constructors
    public CallParticipant() {}

    // Create CallParticipant from JSON
    public static CallParticipant fromJson(JSONObject json) throws JSONException {
        CallParticipant participant = new CallParticipant();
        
        // Handle both ObjectId and populated object cases
        if (json.has("userId")) {
            if (json.get("userId") instanceof JSONObject) {
                // Populated object case
                JSONObject userObj = json.getJSONObject("userId");
                participant.userId = userObj.optString("_id", "");
                participant.username = userObj.optString("username", "");
                participant.avatar = userObj.optString("avatar", "");
            } else {
                // ObjectId case
                participant.userId = json.optString("userId", "");
                participant.username = json.optString("username", "");
                participant.avatar = json.optString("avatar", "");
            }
        }
        
        participant.status = json.optString("status", "invited");
        participant.joinedAt = json.optLong("joinedAt", 0);
        participant.leftAt = json.optLong("leftAt", 0);
        participant.isCaller = json.optBoolean("isCaller", false);
        
        return participant;
    }
    
    // Convert to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("username", username);
        json.put("avatar", avatar);
        json.put("status", status);
        json.put("joinedAt", joinedAt);
        json.put("leftAt", leftAt);
        json.put("isCaller", isCaller);
        
        return json;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isCaller() { return isCaller; }

}
