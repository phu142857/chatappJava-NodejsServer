package com.example.chatappjava.models;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Chat {
    private String id;
    private String type; // "private" or "group"
    private String groupId; // link to server Group id for group chats
    private String name;
    private String description;
    private String lastMessage;
    private long lastMessageTime;
    private int unreadCount;
    private boolean isActive;
    private final List<String> participantIds;
    private String creatorId;
    private long createdAt;
    private long updatedAt;
    private User otherParticipant;
    private String avatar;
    private String visibility; // public or private (optional)
    private boolean isPublic;
    private String joinRequestStatus; // none | pending | approved | rejected (client uses pending)
    private boolean hasPublicFlag;
    
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
        return fromJson(json, null);
    }

    public static Chat fromJson(JSONObject json, String currentUserId) throws JSONException {
        Chat chat = new Chat();
        chat.id = json.optString("_id", "");
        chat.type = json.optString("type", "private");
        // Parse groupId if provided by server for group chats
        chat.groupId = json.optString("groupId", "");
        chat.name = json.optString("name", "");
        chat.description = json.optString("description", "");
        chat.avatar = json.optString("avatar", "");
        // Visibility can be provided as `visibility` or legacy `privacy`. If missing, leave empty to avoid defaulting to public.
        chat.visibility = json.optString("visibility", json.optString("privacy", ""));
        // Determine isPublic from multiple possible shapes: root.isPublic, settings.isPublic, or visibility string
        boolean hasIsPublicRoot = json.has("isPublic");
        boolean isPublicRoot = json.optBoolean("isPublic", false);
        boolean isPublicBySettings = false;
        boolean hasIsPublicInSettings = false;
        try {
            JSONObject settingsObj = json.optJSONObject("settings");
            if (settingsObj != null) {
                hasIsPublicInSettings = settingsObj.has("isPublic");
                isPublicBySettings = settingsObj.optBoolean("isPublic", false);
            }
        } catch (Throwable ignored) {}
        boolean hasVisibility = chat.visibility != null && !chat.visibility.isEmpty();
        boolean isPublicByVisibility = "public".equalsIgnoreCase(chat.visibility);
        // Prefer explicit flags over inferred string
        chat.isPublic = isPublicRoot || isPublicBySettings || isPublicByVisibility;
        chat.hasPublicFlag = hasIsPublicRoot || hasIsPublicInSettings || hasVisibility;
        chat.joinRequestStatus = json.optString("joinRequestStatus", "");
        
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
        // Handle createdBy field - it might be a string ID or an object with _id
        if (json.has("createdBy")) {
            Object createdByObj = json.opt("createdBy");
            if (createdByObj instanceof String) {
                chat.creatorId = (String) createdByObj;
            } else if (createdByObj instanceof org.json.JSONObject) {
                org.json.JSONObject createdByJson = (org.json.JSONObject) createdByObj;
                chat.creatorId = createdByJson.optString("_id", "");
            } else {
                chat.creatorId = "";
            }
        } else {
            chat.creatorId = "";
        }
        // createdAt/updatedAt may be ISO strings; fallback to now if not parseable
        chat.createdAt = parseIsoDateToMillis(json.optString("createdAt", ""));
        chat.updatedAt = parseIsoDateToMillis(json.optString("updatedAt", ""));
        
        // Parse participants array
        JSONArray participantsArray = json.optJSONArray("participants");
        if (participantsArray != null) {
            android.util.Log.d("Chat.fromJson", "Found participants array with " + participantsArray.length() + " items");
            for (int i = 0; i < participantsArray.length(); i++) {
                // Could be string ids OR objects { user: {...} }
                Object participantEntry = participantsArray.opt(i);
                android.util.Log.d("Chat.fromJson", "Participant " + i + ": " + participantEntry.toString());
                if (participantEntry instanceof String) {
                    String participantId = (String) participantEntry;
                    if (!participantId.isEmpty()) {
                        chat.participantIds.add(participantId);
                        android.util.Log.d("Chat.fromJson", "Added participant ID: " + participantId);
                    }
                } else if (participantEntry instanceof JSONObject) {
                    JSONObject participantObj = (JSONObject) participantEntry;
                    // Try nested user object
                    JSONObject userObj = participantObj.optJSONObject("user");
                    if (userObj != null) {
                        String participantId = userObj.optString("_id", userObj.optString("id", ""));
                        if (!participantId.isEmpty()) {
                            chat.participantIds.add(participantId);
                            android.util.Log.d("Chat.fromJson", "Added participant ID from user object: " + participantId);
                        }
                    } else {
                        // Or direct id field
                        String participantId = participantObj.optString("user", "");
                        if (!participantId.isEmpty()) {
                            chat.participantIds.add(participantId);
                            android.util.Log.d("Chat.fromJson", "Added participant ID from direct field: " + participantId);
                        }
                    }
                }
            }
            android.util.Log.d("Chat.fromJson", "Final participant count: " + chat.participantIds.size());
        } else {
            android.util.Log.d("Chat.fromJson", "No participants field found in JSON");
        }
        
        parseOtherParticipant(chat, json);
        if (currentUserId != null && !currentUserId.isEmpty()) {
            resolvePrivateChatPeer(chat, currentUserId, json);
        }
        chat.syncPrivateChatDisplayName();

        return chat;
    }

    private static boolean isGenericPrivateChatName(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return "Private Chat".equalsIgnoreCase(value) || "Unknown User".equalsIgnoreCase(value);
    }

    private static User parseParticipantUser(JSONObject participantObj) throws JSONException {
        if (participantObj == null) {
            return null;
        }
        JSONObject userObj = participantObj.optJSONObject("user");
        if (userObj != null) {
            return User.fromJsonStatic(userObj);
        }
        if (participantObj.has("username") || participantObj.has("_id") || participantObj.has("id")) {
            return User.fromJsonStatic(participantObj);
        }
        return null;
    }

    private static void parseOtherParticipant(Chat chat, JSONObject json) throws JSONException {
        JSONObject other = json.optJSONObject("otherParticipant");
        if (other == null) {
            return;
        }

        JSONObject userObj = other.optJSONObject("user");
        if (userObj != null) {
            chat.otherParticipant = User.fromJsonStatic(userObj);
            return;
        }

        User u = new User();
        u.setId(other.optString("_id", other.optString("id", "")));
        u.setUsername(other.optString("username", ""));
        u.setEmail(other.optString("email", ""));
        u.setAvatar(other.optString("avatar", ""));
        u.setFriend(other.optBoolean("isFriend", false));
        u.setFriendshipStatus(other.optString("friendshipStatus", "not_friends"));

        JSONObject profile = other.optJSONObject("profile");
        if (profile != null) {
            u.setFirstName(profile.optString("firstName", ""));
            u.setLastName(profile.optString("lastName", ""));
        }

        chat.otherParticipant = u;
    }

    private static void resolvePrivateChatPeer(Chat chat, String currentUserId, JSONObject json) throws JSONException {
        if (!chat.isPrivateChat() || chat.otherParticipant != null) {
            return;
        }

        JSONArray participantsArray = json.optJSONArray("participants");
        if (participantsArray == null) {
            return;
        }

        for (int i = 0; i < participantsArray.length(); i++) {
            Object participantEntry = participantsArray.opt(i);
            if (!(participantEntry instanceof JSONObject)) {
                continue;
            }
            User participantUser = parseParticipantUser((JSONObject) participantEntry);
            if (participantUser == null) {
                continue;
            }
            String participantId = participantUser.getId();
            if (participantId != null && !participantId.isEmpty() && !participantId.equals(currentUserId)) {
                chat.otherParticipant = participantUser;
                return;
            }
        }
    }

    private void syncPrivateChatDisplayName() {
        if (!isPrivateChat() || otherParticipant == null) {
            return;
        }
        String displayName = otherParticipant.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = otherParticipant.getUsername();
        }
        if (displayName != null && !displayName.isEmpty() && isGenericPrivateChatName(name)) {
            name = displayName;
        }
    }

    public void setOtherParticipant(User otherParticipant) {
        this.otherParticipant = otherParticipant;
        syncPrivateChatDisplayName();
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
        if (joinRequestStatus != null && !joinRequestStatus.isEmpty()) {
            json.put("joinRequestStatus", joinRequestStatus);
        }
        
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
    public String getGroupId() { return groupId; }

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
    public void setActive(boolean active) { this.isActive = active; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getParticipantIds() { return participantIds; }
    
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    
    public boolean isUserAdmin(String userId) {
        if (userId == null || userId.isEmpty() || participantIds == null) return false;
        
        // Check if user is creator (owner)
        if (creatorId != null && !creatorId.isEmpty() && userId.equals(creatorId)) {
            return true;
        }
        
        // Check if user is admin in participants
        // For now, assume first participant is admin (creator)
        // In a real implementation, this would check the role field from server
        return !participantIds.isEmpty() && userId.equals(participantIds.get(0));
    }
    
    public boolean isUserModerator(String userId) {
        if (userId == null || userId.isEmpty() || participantIds == null) return false;
        
        // Check if user is creator (owner) - they have all permissions
        if (creatorId != null && !creatorId.isEmpty() && userId.equals(creatorId)) {
            return true;
        }
        
        // For now, assume first participant is admin (creator)
        // In a real implementation, this would check the role field from server
        return !participantIds.isEmpty() && userId.equals(participantIds.get(0));
    }
    
    public boolean hasManagementPermissions(String userId) {
        return isUserAdmin(userId) || isUserModerator(userId);
    }
    
    public long getCreatedAt() { return createdAt; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public boolean isPublicGroup() { return isGroupChat() && isPublic; }
    public void setIsPublic(boolean isPublic) { this.isPublic = isPublic; }
    public boolean hasExplicitPublicFlag() { return hasPublicFlag; }
    public String getVisibility() { return visibility; }
    public String getJoinRequestStatus() { return joinRequestStatus; }
    public void setJoinRequestStatus(String status) { this.joinRequestStatus = status; }
    
    public String getFullAvatarUrl() {
        return com.example.chatappjava.utils.UrlUtils.getFullAvatarUrl(avatar);
    }

    /**
     * Avatar shown in chat lists: peer avatar for private chats, group avatar otherwise.
     */
    public String getListAvatarUrl() {
        if (isGroupChat()) {
            return getFullAvatarUrl();
        }
        if (otherParticipant != null) {
            String peerAvatar = otherParticipant.getFullAvatarUrl();
            if (peerAvatar != null && !peerAvatar.isEmpty()) {
                return peerAvatar;
            }
        }
        return getFullAvatarUrl();
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
        if (isPrivateChat()) {
            if (otherParticipant != null) {
                String otherName = otherParticipant.getDisplayName();
                if (otherName != null && !otherName.isEmpty()) {
                    return otherName;
                }
                if (otherParticipant.getUsername() != null && !otherParticipant.getUsername().isEmpty()) {
                    return otherParticipant.getUsername();
                }
            }
            if (!isGenericPrivateChatName(name)) {
                return name;
            }
            return "Private Chat";
        }
        
        // For group chats, use the name field
        if (name != null && !name.isEmpty()) {
            return name;
        }
        
        return "Group Chat";
    }
    
    public String getLastMessageTimeFormatted() {
        if (lastMessageTime <= 0) {
            return "";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(lastMessageTime));
    }

    public int getParticipantCount() {
        return participantIds.size();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Chat chat = (Chat) obj;
        return Objects.equals(id, chat.id);
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
                @SuppressLint("SimpleDateFormat") java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return Objects.requireNonNull(sdf.parse(iso)).getTime();
            } catch (Throwable ignored) {
                return 0;
            }
        }
    }
    
    @NonNull
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
