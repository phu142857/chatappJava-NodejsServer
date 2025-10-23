package com.example.chatappjava.models;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class FriendRequest {
    private String id;
    private String senderId;
    private String receiverId;
    private String status; // "pending", "accepted", "rejected"
    private long createdAt;
    private long updatedAt;
    private User sender;
    private User receiver;

    public FriendRequest() {
    }



    public static FriendRequest fromJson(JSONObject json) throws JSONException {
        System.out.println("FriendRequest.fromJson: Parsing JSON: " + json.toString());
        
        FriendRequest request = new FriendRequest();
        request.id = json.optString("_id", "");
        request.senderId = json.optString("senderId", "");
        request.receiverId = json.optString("receiverId", "");
        request.status = json.optString("status", "pending");
        request.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        request.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
        
        System.out.println("FriendRequest.fromJson: Parsed IDs - senderId: " + request.senderId + ", receiverId: " + request.receiverId);

        // Parse sender user info
        if (json.has("senderId") && !json.isNull("senderId")) {
            try {
                JSONObject senderJson = json.getJSONObject("senderId");
                request.sender = User.fromJson(senderJson);
                // Extract sender ID from the JSON object
                request.senderId = senderJson.optString("_id", "");
                System.out.println("FriendRequest.fromJson: Parsed sender: " + request.sender.getDisplayName());
                System.out.println("FriendRequest.fromJson: Extracted senderId: " + request.senderId);
            } catch (Exception e) {
                System.out.println("FriendRequest.fromJson: Error parsing sender: " + e.getMessage());
            }
        }

        // Parse receiver user info
        if (json.has("receiverId") && !json.isNull("receiverId")) {
            try {
                JSONObject receiverJson = json.getJSONObject("receiverId");
                request.receiver = User.fromJson(receiverJson);
                // Extract receiver ID from the JSON object
                request.receiverId = receiverJson.optString("_id", "");
                System.out.println("FriendRequest.fromJson: Parsed receiver: " + (request.receiver != null ? request.receiver.getDisplayName() : "null"));
                System.out.println("FriendRequest.fromJson: Extracted receiverId: " + request.receiverId);
            } catch (Exception e) {
                System.out.println("FriendRequest.fromJson: Error parsing receiver: " + e.getMessage());
            }
        }

        return request;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("senderId", senderId);
        json.put("receiverId", receiverId);
        json.put("status", status);
        json.put("createdAt", createdAt);
        json.put("updatedAt", updatedAt);
        return json;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }
    public String getReceiverId() {
        return receiverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getSender() {
        return sender;
    }
    public User getReceiver() {
        return receiver;
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    @NonNull
    @Override
    public String toString() {
        return "FriendRequest{" +
                "id='" + id + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
