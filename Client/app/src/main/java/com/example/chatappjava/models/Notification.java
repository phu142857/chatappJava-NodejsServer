package com.example.chatappjava.models;

import org.json.JSONException;
import org.json.JSONObject;

public class Notification {
    private String id;
    private String type; // "tagged_in_post", "tagged_in_comment", "friend_posted", "friend_shared"
    private String actorId; // User who performed the action
    private String actorUsername;
    private String actorAvatar;
    private String postId; // Post related to the notification
    private String commentId; // Comment related (if type is tagged_in_comment)
    private String postPreviewImage; // Preview image of the post
    private long timestamp;
    private boolean isRead;
    
    public Notification() {}
    
    public static Notification fromJson(JSONObject json) throws JSONException {
        Notification notification = new Notification();
        notification.id = json.optString("_id", json.optString("id", ""));
        notification.type = json.optString("type", "");
        
        // Handle timestamp
        if (json.has("createdAt")) {
            try {
                Object createdAt = json.get("createdAt");
                if (createdAt instanceof Long) {
                    notification.timestamp = (Long) createdAt;
                } else if (createdAt instanceof Integer) {
                    notification.timestamp = ((Integer) createdAt).longValue();
                } else if (createdAt instanceof String) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        notification.timestamp = sdf.parse((String) createdAt).getTime();
                    } catch (Exception e) {
                        try {
                            java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                            sdf2.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                            notification.timestamp = sdf2.parse((String) createdAt).getTime();
                        } catch (Exception e2) {
                            notification.timestamp = System.currentTimeMillis();
                        }
                    }
                } else {
                    notification.timestamp = System.currentTimeMillis();
                }
            } catch (Exception e) {
                notification.timestamp = json.optLong("createdAt", System.currentTimeMillis());
            }
        } else {
            notification.timestamp = json.optLong("timestamp", System.currentTimeMillis());
        }
        
        notification.isRead = json.optBoolean("isRead", false);
        notification.postId = json.optString("postId", "");
        notification.commentId = json.optString("commentId", "");
        notification.postPreviewImage = json.optString("postPreviewImage", "");
        
        // Handle actor (user who performed the action)
        JSONObject actorObj = json.optJSONObject("actor");
        if (actorObj != null) {
            notification.actorId = actorObj.optString("_id", actorObj.optString("id", ""));
            notification.actorUsername = actorObj.optString("username", "");
            notification.actorAvatar = actorObj.optString("avatar", "");
        } else {
            notification.actorId = json.optString("actorId", "");
            notification.actorUsername = json.optString("actorUsername", "");
            notification.actorAvatar = json.optString("actorAvatar", "");
        }
        
        return notification;
    }
    
    public String getNotificationText() {
        String actorName = actorUsername != null && !actorUsername.isEmpty() ? actorUsername : "Someone";
        
        switch (type) {
            case "tagged_in_post":
                return actorName + " tagged you in a post";
            case "tagged_in_comment":
                return actorName + " tagged you in a comment";
            case "friend_posted":
                return actorName + " posted a new post";
            case "friend_shared":
                return actorName + " shared a post";
            default:
                return actorName + " performed an action";
        }
    }
    
    public String getFormattedTime() {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (hours > 0) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (minutes > 0) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else {
            return "Just now";
        }
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    
    public String getActorAvatar() { return actorAvatar; }
    public void setActorAvatar(String actorAvatar) { this.actorAvatar = actorAvatar; }
    
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
    
    public String getPostPreviewImage() { return postPreviewImage; }
    public void setPostPreviewImage(String postPreviewImage) { this.postPreviewImage = postPreviewImage; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}

