package com.example.chatappjava.models;

import org.json.JSONException;
import org.json.JSONObject;

// Sender class for backend response
class Sender {
    private String id;
    private String username;
    private String avatar;
    private String status;
    
    public Sender() {}
    
    public static Sender fromJson(JSONObject json) throws JSONException {
        Sender sender = new Sender();
        sender.id = json.optString("_id", "");
        sender.username = json.optString("username", "");
        sender.avatar = json.optString("avatar", "");
        sender.status = json.optString("status", "offline");
        return sender;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

// SenderInfo class for backend response
class SenderInfo {
    private String id;
    private String username;
    private String avatar;
    private String status;
    
    public SenderInfo() {}
    
    public static SenderInfo fromJson(JSONObject json) throws JSONException {
        SenderInfo senderInfo = new SenderInfo();
        senderInfo.id = json.optString("id", "");
        senderInfo.username = json.optString("username", "");
        senderInfo.avatar = json.optString("avatar", "");
        senderInfo.status = json.optString("status", "offline");
        return senderInfo;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

public class Message {
    private String id;
    private String chatId;
    private String senderId;
    private String senderDisplayName;
    private String senderAvatarUrl;
    private String content;
    private String type; // text, image, file, etc.
    private String chatType; // private, group
    private long timestamp;
    private boolean isRead;
    private boolean isDeleted;
    private String attachments; // JSON string of attachments array
    private String localImageUri; // Local URI for zoom functionality
    
    // Sender object from backend
    private Sender sender;
    private SenderInfo senderInfo;
    
    // Constructors
    public Message() {}
    
    public Message(String content, String type, String senderId) {
        this.content = content;
        this.type = type;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.isDeleted = false;
    }
    
    // Create Message from JSON
    public static Message fromJson(JSONObject json) throws JSONException {
        Message message = new Message();
        message.id = json.optString("_id", "");
        message.chatId = json.optString("chat", "");
        message.senderId = json.optString("sender", "");
        message.content = json.optString("content", "");
        message.type = json.optString("type", "text");
        message.chatType = json.optString("chatType", "private");
        message.timestamp = json.optLong("timestamp", System.currentTimeMillis());
        message.isRead = json.optBoolean("isRead", false);
        message.isDeleted = json.optBoolean("isDeleted", false);
        message.attachments = json.optString("attachments", "");
        
        // Parse sender object if available
        if (json.has("sender") && json.get("sender") instanceof JSONObject) {
            JSONObject senderJson = json.getJSONObject("sender");
            message.sender = Sender.fromJson(senderJson);
            // Update legacy fields for backward compatibility
            message.senderId = message.sender.getId();
            message.senderDisplayName = message.sender.getUsername();
            message.senderAvatarUrl = message.sender.getAvatar();
            android.util.Log.d("Message", "Parsed sender: " + message.sender.getUsername() + ", avatar: " + message.sender.getAvatar());
        }
        
        // Parse senderInfo object if available
        if (json.has("senderInfo") && json.get("senderInfo") instanceof JSONObject) {
            JSONObject senderInfoJson = json.getJSONObject("senderInfo");
            message.senderInfo = SenderInfo.fromJson(senderInfoJson);
            // Use senderInfo as fallback if sender is not available
            if (message.sender == null) {
                message.senderId = message.senderInfo.getId();
                message.senderDisplayName = message.senderInfo.getUsername();
                message.senderAvatarUrl = message.senderInfo.getAvatar();
            }
            android.util.Log.d("Message", "Parsed senderInfo: " + message.senderInfo.getUsername() + ", avatar: " + message.senderInfo.getAvatar());
        }
        
        return message;
    }
    
    // Convert Message to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("_id", id);
        json.put("chat", chatId);
        json.put("sender", senderId);
        json.put("content", content);
        json.put("type", type);
        json.put("timestamp", timestamp);
        json.put("isRead", isRead);
        json.put("isDeleted", isDeleted);
        return json;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }

    public String getSenderAvatarUrl() { return senderAvatarUrl; }
    public void setSenderAvatarUrl(String senderAvatarUrl) { this.senderAvatarUrl = senderAvatarUrl; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getChatType() { return chatType; }
    public void setChatType(String chatType) { this.chatType = chatType; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    
    public Sender getSender() { return sender; }
    public void setSender(Sender sender) { this.sender = sender; }
    
    public SenderInfo getSenderInfo() { return senderInfo; }
    public void setSenderInfo(SenderInfo senderInfo) { this.senderInfo = senderInfo; }
    
    public String getAttachments() { return attachments; }
    public void setAttachments(String attachments) { this.attachments = attachments; }
    
    public String getLocalImageUri() { return localImageUri; }
    public void setLocalImageUri(String localImageUri) { this.localImageUri = localImageUri; }
    
    // Helper methods
    public boolean isTextMessage() {
        return "text".equals(type);
    }
    
    public boolean isImageMessage() {
        return "image".equals(type);
    }
    
    public boolean isFileMessage() {
        return "file".equals(type);
    }
    
    public boolean isGroupChat() {
        return "group".equals(chatType);
    }
    
    public boolean isPrivateChat() {
        return "private".equals(chatType);
    }
    
    // Get sender avatar with fallback
    public String getSenderAvatar() {
        if (sender != null && sender.getAvatar() != null && !sender.getAvatar().isEmpty()) {
            return sender.getAvatar();
        }
        if (senderInfo != null && senderInfo.getAvatar() != null && !senderInfo.getAvatar().isEmpty()) {
            return senderInfo.getAvatar();
        }
        return senderAvatarUrl != null ? senderAvatarUrl : "";
    }
    
    // Get sender username with fallback
    public String getSenderUsername() {
        if (sender != null && sender.getUsername() != null && !sender.getUsername().isEmpty()) {
            return sender.getUsername();
        }
        if (senderInfo != null && senderInfo.getUsername() != null && !senderInfo.getUsername().isEmpty()) {
            return senderInfo.getUsername();
        }
        return senderDisplayName != null ? senderDisplayName : "";
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", chatId='" + chatId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", content='" + content + '\'' +
                ", type='" + type + '\'' +
                ", timestamp=" + timestamp +
                ", isRead=" + isRead +
                ", isDeleted=" + isDeleted +
                '}';
    }
}
