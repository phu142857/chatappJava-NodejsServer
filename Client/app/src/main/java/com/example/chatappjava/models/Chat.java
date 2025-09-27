package com.example.chatappjava.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class Chat {
    private String id;
    private String type; // "private" or "group"
    private String name;
    private String description;
    private String lastMessage;
    private long lastMessageTime;
    private int unreadCount;
    private boolean isActive;
    private List<String> participantIds;
    private String creatorId;
    private long createdAt;
    private long updatedAt;
    private User otherParticipant;
    private String avatar;
    
    // Constructors
    public Chat() {
        this.participantIds = new ArrayList<>();
        this.unreadCount = 0;
        this.isActive = true;
    }
    
    public Chat(String id, String type, String name) {
        this();
        this.id = id;
        this.type = type;
        this.name = name;
    }
    
    // Create Chat from JSON
    public static Chat fromJson(JSONObject json) throws JSONException {
        Chat chat = new Chat();
        chat.id = json.optString("_id", "");
        chat.type = json.optString("type", "private");
        chat.name = json.optString("name", "");
        chat.description = json.optString("description", "");
        chat.avatar = json.optString("avatar", "");
        
        // lastMessage may be an object (populated) or an id/string
        Object lastMessageField = json.opt("lastMessage");
        if (lastMessageField instanceof JSONObject) {
            JSONObject lastMsgObj = (JSONObject) lastMessageField;
            chat.lastMessage = lastMsgObj.optString("content", "");
            // Try parse createdAt ISO date
            String createdAtStr = lastMsgObj.optString("createdAt", "");
            chat.lastMessageTime = parseIsoDateToMillis(createdAtStr);
        } else {
            chat.lastMessage = json.optString("lastMessage", "");
            chat.lastMessageTime = json.optLong("lastMessageTime", 0);
        }
        chat.unreadCount = json.optInt("unreadCount", 0);
        chat.isActive = json.optBoolean("isActive", true);
        chat.creatorId = json.optString("createdBy", "");
        // createdAt/updatedAt may be ISO strings; fallback to now if not parseable
        chat.createdAt = parseIsoDateToMillis(json.optString("createdAt", ""));
        chat.updatedAt = parseIsoDateToMillis(json.optString("updatedAt", ""));
        
        // Parse participants array
        JSONArray participantsArray = json.optJSONArray("participants");
        if (participantsArray != null) {
            for (int i = 0; i < participantsArray.length(); i++) {
                // Could be string ids OR objects { user: {...} }
                Object participantEntry = participantsArray.opt(i);
                if (participantEntry instanceof String) {
                    String participantId = (String) participantEntry;
                    if (!participantId.isEmpty()) {
                        chat.participantIds.add(participantId);
                    }
                } else if (participantEntry instanceof JSONObject) {
                    JSONObject participantObj = (JSONObject) participantEntry;
                    // Try nested user object
                    JSONObject userObj = participantObj.optJSONObject("user");
                    if (userObj != null) {
                        String participantId = userObj.optString("_id", userObj.optString("id", ""));
                        if (!participantId.isEmpty()) {
                            chat.participantIds.add(participantId);
                        }
                    } else {
                        // Or direct id field
                        String participantId = participantObj.optString("user", "");
                        if (!participantId.isEmpty()) {
                            chat.participantIds.add(participantId);
                        }
                    }
                }
            }
        }
        
        // Parse otherParticipant if server provided it (for private chats)
        JSONObject other = json.optJSONObject("otherParticipant");
        if (other != null) {
            User u = new User();
            u.setId(other.optString("_id", other.optString("id", "")));
            u.setUsername(other.optString("username", ""));
            u.setEmail(other.optString("email", ""));
            u.setAvatar(other.optString("avatar", ""));
            u.setStatus(other.optString("status", "offline"));
            chat.otherParticipant = u;
            if (chat.name == null || chat.name.isEmpty()) {
                chat.name = u.getDisplayName();
            }
        }
        
        return chat;
    }
    
    // Convert Chat to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("_id", id);
        json.put("type", type);
        json.put("name", name);
        json.put("description", description);
        json.put("avatar", avatar);
        json.put("lastMessage", lastMessage);
        json.put("lastMessageTime", lastMessageTime);
        json.put("unreadCount", unreadCount);
        json.put("isActive", isActive);
        json.put("createdBy", creatorId);
        json.put("createdAt", createdAt);
        json.put("updatedAt", updatedAt);
        
        // Convert participants list to JSON array
        JSONArray participantsArray = new JSONArray();
        for (String participantId : participantIds) {
            participantsArray.put(participantId);
        }
        json.put("participants", participantsArray);
        
        return json;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    
    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }
    
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getFullAvatarUrl() {
        android.util.Log.d("ChatModel", "getFullAvatarUrl() called with avatar: " + avatar);
        
        if (avatar == null || avatar.isEmpty()) {
            android.util.Log.d("ChatModel", "Avatar is null or empty, returning null");
            return null;
        }
        if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
            android.util.Log.d("ChatModel", "Avatar is already full URL: " + avatar);
            return avatar; // Already a full URL
        }
        // Construct full URL from relative path
        // Ensure avatar starts with / if it doesn't already
        String avatarPath = avatar.startsWith("/") ? avatar : "/" + avatar;
        String fullUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarPath;
        android.util.Log.d("ChatModel", "getFullAvatarUrl() returning: " + fullUrl + " (from input: " + avatar + ")");
        return fullUrl;
    }
    
    // Helper methods
    public boolean isPrivateChat() {
        return "private".equals(type);
    }
    
    public boolean isGroupChat() {
        return "group".equals(type);
    }
    
    public boolean hasUnreadMessages() {
        return unreadCount > 0;
    }
    
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        } else if (isPrivateChat()) {
            if (otherParticipant != null) {
                return otherParticipant.getDisplayName();
            }
            return "Private Chat";
        } else {
            return "Group Chat";
        }
    }
    
    public String getLastMessageTimeFormatted() {
        if (lastMessageTime <= 0) {
            return "";
        }
        
        long now = System.currentTimeMillis();
        long diff = now - lastMessageTime;
        
        long minutes = diff / (1000 * 60);
        long hours = diff / (1000 * 60 * 60);
        long days = diff / (1000 * 60 * 60 * 24);
        
        if (minutes < 1) {
            return "Now";
        } else if (minutes < 60) {
            return minutes + "m";
        } else if (hours < 24) {
            return hours + "h";
        } else if (days < 7) {
            return days + "d";
        } else {
            return days / 7 + "w";
        }
    }
    
    public void addParticipant(String participantId) {
        if (!participantIds.contains(participantId)) {
            participantIds.add(participantId);
        }
    }
    
    public void removeParticipant(String participantId) {
        participantIds.remove(participantId);
    }
    
    public boolean hasParticipant(String participantId) {
        return participantIds.contains(participantId);
    }
    
    public int getParticipantCount() {
        return participantIds.size();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Chat chat = (Chat) obj;
        return id != null ? id.equals(chat.id) : chat.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    // Helper method to get other participant in private chat
    public User getOtherParticipant() {
        return otherParticipant;
    }

    private static long parseIsoDateToMillis(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            // Try java.time if available
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Throwable t) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return sdf.parse(iso).getTime();
            } catch (Throwable ignored) {
                return 0;
            }
        }
    }
    
    @Override
    public String toString() {
        return "Chat{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", lastMessage='" + lastMessage + '\'' +
                ", unreadCount=" + unreadCount +
                '}';
    }
}
