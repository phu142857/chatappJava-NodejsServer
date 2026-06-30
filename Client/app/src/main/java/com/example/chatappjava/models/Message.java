package com.example.chatappjava.models;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

final class MessageJsonHelper {
    private MessageJsonHelper() {}

    static String resolveUserId(JSONObject json) {
        if (json == null) return "";
        String id = json.optString("_id", "");
        if (id.isEmpty()) {
            id = json.optString("id", "");
        }
        return id;
    }
}

// Sender class for backend response
class Sender {
    private String id;
    private String username;
    private String avatar;
    
    public Sender() {}
    
    public static Sender fromJson(JSONObject json) throws JSONException {
        Sender sender = new Sender();
        sender.id = MessageJsonHelper.resolveUserId(json);
        sender.username = json.optString("username", "");
        sender.avatar = json.optString("avatar", "");
        return sender;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}

// SenderInfo class for backend response
class SenderInfo {
    private String id;
    private String username;
    private String avatar;
    
    public SenderInfo() {}
    
    public static SenderInfo fromJson(JSONObject json) throws JSONException {
        SenderInfo senderInfo = new SenderInfo();
        senderInfo.id = MessageJsonHelper.resolveUserId(json);
        senderInfo.username = json.optString("username", "");
        senderInfo.avatar = json.optString("avatar", "");
        return senderInfo;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}

public class Message {
    public static final String SYNC_SYNCED = "synced";
    public static final String SYNC_PENDING = "pending";
    public static final String SYNC_FAILED = "failed";

    public static final int DELIVERY_SENT = 0;
    public static final int DELIVERY_PENDING = 1;
    public static final int DELIVERY_FAILED = 2;

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
    private String replyToImageThumb; // local or remote url for reply preview
    // Reactions summary (emoji -> count) and raw list (optional)
    private java.util.Map<String, Integer> reactionSummary;
    private String reactionsRaw; // store raw JSON if needed to show user list
    private String clientNonce; // unique from client to dedupe echo
    private String syncStatus = SYNC_SYNCED;
    
    // Reply and edit info
    private String replyToMessageId;
    private String replyToContent;
    private String replyToSenderName;
    private boolean edited;
    private long editedAt;
    
    // Sender object from backend
    private Sender sender;
    private SenderInfo senderInfo;
    // Client-side helpers (not from server)
    private transient String localSignature;
    
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
        if (message.chatId.isEmpty()) {
            message.chatId = json.optString("chatId", "");
        }
        message.senderId = json.optString("senderId", "");
        if (json.has("sender")) {
            Object senderValue = json.get("sender");
            if (senderValue instanceof String) {
                String senderRef = (String) senderValue;
                if (!senderRef.isEmpty()) {
                    message.senderId = senderRef;
                }
            } else if (senderValue instanceof JSONObject) {
                JSONObject senderJson = (JSONObject) senderValue;
                message.sender = Sender.fromJson(senderJson);
                String resolvedSenderId = message.sender.getId();
                if (!resolvedSenderId.isEmpty()) {
                    message.senderId = resolvedSenderId;
                }
                message.senderDisplayName = message.sender.getUsername();
                message.senderAvatarUrl = message.sender.getAvatar();
            }
        }
        message.content = json.optString("content", "");
        message.type = json.optString("type", "text");
        message.chatType = json.optString("chatType", "private");
        // Parse timestamp: prefer createdAt if available, else use timestamp (supports ISO string or number)
        message.timestamp = parseTimestamp(json, "createdAt", 0);
        if (message.timestamp <= 0) {
            message.timestamp = parseTimestamp(json, "timestamp", System.currentTimeMillis());
        }
        message.isRead = json.optBoolean("isRead", false);
        message.isDeleted = json.optBoolean("isDeleted", false);
        message.attachments = json.optString("attachments", "");
        
        // Reply info (server returns replyTo as object)
        if (json.has("replyTo") && json.get("replyTo") instanceof JSONObject) {
            JSONObject replyJson = json.getJSONObject("replyTo");
            message.replyToMessageId = replyJson.optString("_id", "");
            message.replyToContent = replyJson.optString("content", "");
            String replyType = replyJson.optString("type", "");
            // try extract image thumb from attachments of replyTo
            if (replyJson.has("attachments") && replyJson.get("attachments") instanceof org.json.JSONArray) {
                org.json.JSONArray arr = replyJson.getJSONArray("attachments");
                if (arr.length() > 0) {
                    org.json.JSONObject att = arr.getJSONObject(0);
                    String url = att.optString("url", "");
                    if (!url.isEmpty()) message.replyToImageThumb = url;
                }
            }
            // Fallbacks
            String c = replyJson.optString("content", "");
            if ((message.replyToImageThumb == null || message.replyToImageThumb.isEmpty()) && "image".equals(replyType)) {
                if (!c.isEmpty()) message.replyToImageThumb = c;
            }
            if (message.replyToImageThumb == null || message.replyToImageThumb.isEmpty()) {
                // Heuristic: if content looks like an image URL/path, use it
                if (!c.isEmpty()) {
                    String lc = c.toLowerCase();
                    if (lc.startsWith("http") || lc.startsWith("/") || lc.contains("/uploads")) {
                        if (lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") || lc.endsWith(".webp") || lc.endsWith(".gif")) {
                            message.replyToImageThumb = c;
                        }
                    }
                }
            }
            if (replyJson.has("sender") && replyJson.get("sender") instanceof JSONObject) {
                JSONObject replySender = replyJson.getJSONObject("sender");
                message.replyToSenderName = replySender.optString("username", "");
            }
        }
        
        // Edited info — never infer "edited" from key presence alone (editedAt:null used to mark all as edited)
        message.edited = json.optBoolean("edited", false) || json.optBoolean("isEdited", false);
        message.editedAt = parseTimestamp(json, "editedAt", 0);

        // Parse senderInfo for display fields (delta-sync API)
        if (json.has("senderInfo") && json.get("senderInfo") instanceof JSONObject) {
            JSONObject senderInfoJson = json.getJSONObject("senderInfo");
            message.senderInfo = SenderInfo.fromJson(senderInfoJson);
            if (message.senderId == null || message.senderId.isEmpty()) {
                message.senderId = message.senderInfo.getId();
            }
            if (message.senderDisplayName == null || message.senderDisplayName.isEmpty()) {
                message.senderDisplayName = message.senderInfo.getUsername();
            }
            if (message.senderAvatarUrl == null || message.senderAvatarUrl.isEmpty()) {
                message.senderAvatarUrl = message.senderInfo.getAvatar();
            }
        }

        // Parse reaction summary if present (from server virtual)
        if (json.has("reactionSummary") && json.get("reactionSummary") instanceof JSONObject) {
            JSONObject sum = json.getJSONObject("reactionSummary");
            java.util.Iterator<String> keys = sum.keys();
            java.util.Map<String, Integer> map = new java.util.HashMap<>();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, sum.optInt(k, 0));
            }
            message.reactionSummary = map;
        }
        if (json.has("reactions") && json.get("reactions") instanceof org.json.JSONArray) {
            message.reactionsRaw = json.getJSONArray("reactions").toString();
        }
        message.ensureReactionSummaryFromRaw();
        message.clientNonce = json.optString("clientNonce", null);
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

    private static long parseTimestamp(org.json.JSONObject json, String key, long defaultValue) {
        try {
            if (!json.has(key) || json.isNull(key)) return defaultValue;
            Object value = json.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                String iso = (String) value;
                if (iso.isEmpty()) return defaultValue;
                try {
                    return java.time.Instant.parse(iso).toEpochMilli();
                } catch (Throwable t) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        java.util.Date d = sdf.parse(iso);
                        return d != null ? d.getTime() : defaultValue;
                    } catch (Throwable ignored) {
                        return defaultValue;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return defaultValue;
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
        if (replyToMessageId != null && !replyToMessageId.isEmpty()) {
            json.put("replyTo", replyToMessageId);
        }
        if (edited) {
            json.put("edited", true);
            if (editedAt > 0) json.put("editedAt", editedAt);
        }
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
    public void setRead(boolean read) { this.isRead = read; }
    
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getAttachments() { return attachments; }
    public String getLocalImageUri() { return localImageUri; }
    public void setLocalImageUri(String localImageUri) { this.localImageUri = localImageUri; }
    public String getReplyToImageThumb() { return replyToImageThumb; }
    public void setReplyToImageThumb(String v) { this.replyToImageThumb = v; }

    public java.util.Map<String, Integer> getReactionSummary() { return reactionSummary; }

    public void setReactionsRaw(String reactionsRaw) {
        this.reactionsRaw = reactionsRaw;
        this.reactionSummary = null;
        ensureReactionSummaryFromRaw();
    }

    /** Build summary map from stored reactions JSON (e.g. when loading from SQLite). */
    public void ensureReactionSummaryFromRaw() {
        if (reactionSummary != null && !reactionSummary.isEmpty()) {
            return;
        }
        if (reactionsRaw == null || reactionsRaw.isEmpty()) {
            return;
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray(reactionsRaw);
            java.util.Map<String, Integer> map = new java.util.HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject r = arr.getJSONObject(i);
                String emoji = r.optString("emoji", "");
                if (!emoji.isEmpty()) {
                    map.put(emoji, map.getOrDefault(emoji, 0) + 1);
                }
            }
            if (!map.isEmpty()) {
                reactionSummary = map;
            }
        } catch (org.json.JSONException ignored) {
        }
    }

    public String getTopReactionEmoji() {
        ensureReactionSummaryFromRaw();
        if (reactionSummary == null || reactionSummary.isEmpty()) {
            return "";
        }
        String topEmoji = "";
        int max = -1;
        for (java.util.Map.Entry<String, Integer> entry : reactionSummary.entrySet()) {
            Integer count = entry.getValue();
            if (count != null && count > max) {
                max = count;
                topEmoji = entry.getKey();
            }
        }
        return topEmoji != null ? topEmoji : "";
    }

    public void copyReactionDataFrom(Message other) {
        if (other == null) {
            return;
        }
        reactionsRaw = other.reactionsRaw;
        if (other.reactionSummary != null && !other.reactionSummary.isEmpty()) {
            reactionSummary = new java.util.HashMap<>(other.reactionSummary);
        } else {
            reactionSummary = null;
            ensureReactionSummaryFromRaw();
        }
    }

    /** Apply authoritative reactions list from a socket/API payload. */
    public void applyReactions(org.json.JSONArray reactions) {
        if (reactions == null) {
            reactionsRaw = "[]";
        } else {
            reactionsRaw = reactions.toString();
        }
        reactionSummary = null;
        ensureReactionSummaryFromRaw();
    }

    public static boolean reactionsVisuallyEqual(Message a, Message b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return java.util.Objects.equals(a.getTopReactionEmoji(), b.getTopReactionEmoji());
    }

    public void incrementReaction(String emoji) {
        if (emoji == null) return;
        if (reactionSummary == null) reactionSummary = new java.util.HashMap<>();

        Integer value = reactionSummary.get(emoji);
        int c = (value == null) ? 0 : value;
        reactionSummary.put(emoji, c + 1);
    }

    public String findReactionEmojiForUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            if (reactionsRaw != null && !reactionsRaw.isEmpty()) {
                org.json.JSONArray arr = new org.json.JSONArray(reactionsRaw);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject reaction = arr.getJSONObject(i);
                    if (userId.equals(extractReactionUserId(reaction))) {
                        String emoji = reaction.optString("emoji", "");
                        return emoji.isEmpty() ? null : emoji;
                    }
                }
            }
        } catch (org.json.JSONException ignored) {
        }
        return null;
    }

    /** Replace current user's reaction (matches server addReaction behaviour). */
    public void upsertUserReaction(String userId, String emoji) {
        if (userId == null || userId.isEmpty() || emoji == null || emoji.isEmpty()) {
            return;
        }
        try {
            org.json.JSONArray source = (reactionsRaw != null && !reactionsRaw.isEmpty())
                    ? new org.json.JSONArray(reactionsRaw)
                    : new org.json.JSONArray();
            org.json.JSONArray updated = new org.json.JSONArray();
            for (int i = 0; i < source.length(); i++) {
                org.json.JSONObject reaction = source.getJSONObject(i);
                if (!userId.equals(extractReactionUserId(reaction))) {
                    updated.put(reaction);
                }
            }
            org.json.JSONObject entry = new org.json.JSONObject();
            entry.put("emoji", emoji);
            org.json.JSONObject user = new org.json.JSONObject();
            user.put("_id", userId);
            entry.put("user", user);
            updated.put(entry);
            applyReactions(updated);
        } catch (org.json.JSONException e) {
            incrementReaction(emoji);
        }
    }

    /** Remove the current user's reaction locally. Returns removed emoji if any. */
    public String removeUserReactionForUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        String emoji = findReactionEmojiForUser(userId);
        if (emoji == null) {
            return null;
        }
        try {
            if (reactionsRaw == null || reactionsRaw.isEmpty()) {
                decrementReaction(emoji);
                return emoji;
            }
            org.json.JSONArray source = new org.json.JSONArray(reactionsRaw);
            org.json.JSONArray updated = new org.json.JSONArray();
            boolean removed = false;
            for (int i = 0; i < source.length(); i++) {
                org.json.JSONObject reaction = source.getJSONObject(i);
                if (!removed && userId.equals(extractReactionUserId(reaction))) {
                    removed = true;
                    continue;
                }
                updated.put(reaction);
            }
            if (removed) {
                applyReactions(updated);
                return emoji;
            }
        } catch (org.json.JSONException e) {
            decrementReaction(emoji);
            return emoji;
        }
        return null;
    }

    private static String extractReactionUserId(org.json.JSONObject reaction) {
        if (reaction == null) {
            return null;
        }
        Object userValue = reaction.opt("user");
        if (userValue instanceof org.json.JSONObject) {
            org.json.JSONObject user = (org.json.JSONObject) userValue;
            String id = user.optString("_id", user.optString("id", ""));
            return id.isEmpty() ? null : id;
        }
        if (userValue instanceof String) {
            String id = (String) userValue;
            return id.isEmpty() ? null : id;
        }
        String userId = reaction.optString("userId", "");
        return userId.isEmpty() ? null : userId;
    }


    public void decrementReaction(String emoji) {
        if (emoji == null || reactionSummary == null) return;
        Integer c = reactionSummary.get(emoji);
        if (c == null) return;
        if (c <= 1) {
            reactionSummary.remove(emoji);
        } else {
            reactionSummary.put(emoji, c - 1);
        }
    }
    public String getReactionsRaw() { return reactionsRaw; }
    public String getClientNonce() { return clientNonce; }
    public void setClientNonce(String clientNonce) { this.clientNonce = clientNonce; }

    public String getSyncStatus() { return syncStatus; }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus != null ? syncStatus : SYNC_SYNCED;
    }

    /** Resolve pending state from temp ids when status was not loaded from DB. */
    public void inferSyncStatusIfNeeded() {
        if (SYNC_FAILED.equals(syncStatus)) {
            return;
        }
        if (isPlaceholderId(id)) {
            syncStatus = SYNC_PENDING;
        } else if (syncStatus == null || syncStatus.isEmpty()) {
            syncStatus = SYNC_SYNCED;
        }
    }

    public int getOutgoingDeliveryState() {
        inferSyncStatusIfNeeded();
        if (SYNC_FAILED.equals(syncStatus)) {
            return DELIVERY_FAILED;
        }
        if (SYNC_PENDING.equals(syncStatus) || isPlaceholderId(id)) {
            return DELIVERY_PENDING;
        }
        return DELIVERY_SENT;
    }

    private static boolean isPlaceholderId(String messageId) {
        return messageId != null && (messageId.startsWith("temp_") || messageId.startsWith("local-"));
    }
    
    public String getReplyToMessageId() { return replyToMessageId; }
    public void setReplyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }
    public String getReplyToContent() { return replyToContent; }
    public void setReplyToContent(String replyToContent) { this.replyToContent = replyToContent; }
    public String getReplyToSenderName() { return replyToSenderName; }
    public void setReplyToSenderName(String replyToSenderName) { this.replyToSenderName = replyToSenderName; }
    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }

    /** True only when the message was actually edited (not a parse/sync artifact). */
    public boolean shouldShowEditedLabel() {
        return edited && editedAt > 0;
    }
    
    public long getEditedAt() { return editedAt; }
    public void setEditedAt(long editedAt) { this.editedAt = editedAt; }
    
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
    
    public boolean isVoiceMessage() {
        return "voice".equals(type) || "audio".equals(type);
    }

    public boolean isGroupChat() {
        return "group".equals(chatType);
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
    
    @NonNull
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

    // Client-side helpers
    public String getLocalSignature() { return localSignature; }
    public void setLocalSignature(String sig) { this.localSignature = sig; }
}
