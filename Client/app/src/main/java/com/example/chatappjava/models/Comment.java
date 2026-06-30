package com.example.chatappjava.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Comment {
    private String id;
    private String userId;
    private String username;
    private String userAvatar;
    private String content;
    private long createdAt;
    private long updatedAt;
    private int likesCount;
    private boolean isLiked;
    private String parentCommentId; // For nested replies
    private List<Comment> replies; // Nested replies
    private int repliesCount;
    private String mediaUrl; // Image/GIF attachment
    private List<Reaction> reactions; // Reactions array
    private String currentUserReaction; // Current user's reaction type
    private boolean isEdited;

    public Comment() {
        this.replies = new ArrayList<>();
        this.repliesCount = 0;
        this.isLiked = false;
        this.reactions = new ArrayList<>();
        this.isEdited = false;
    }

    public Comment(String id, String userId, String username, String content) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.replies = new ArrayList<>();
        this.repliesCount = 0;
        this.likesCount = 0;
        this.isLiked = false;
        this.reactions = new ArrayList<>();
        this.isEdited = false;
    }

    // Create Comment from JSON (backend format)
    public static Comment fromJson(JSONObject json, String currentUserId) throws JSONException {
        Comment comment = new Comment();
        
        // Handle comment ID (could be _id or id)
        comment.id = json.optString("_id", json.optString("id", ""));
        
        // Handle user information
        JSONObject userObj = json.optJSONObject("user");
        if (userObj != null) {
            comment.userId = userObj.optString("_id", userObj.optString("id", ""));
            comment.username = userObj.optString("username", "");
            comment.userAvatar = userObj.optString("avatar", "");
        } else {
            // Fallback if user is not populated
            comment.userId = json.optString("userId", "");
            comment.username = json.optString("username", "");
            comment.userAvatar = json.optString("avatar", "");
        }
        
        // Content
        comment.content = json.optString("content", "");
        
        // Timestamps
        if (json.has("createdAt")) {
            Object createdAt = json.get("createdAt");
            if (createdAt instanceof Long) {
                comment.createdAt = (Long) createdAt;
            } else if (createdAt instanceof Integer) {
                comment.createdAt = ((Integer) createdAt).longValue();
            } else if (createdAt instanceof String) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    comment.createdAt = sdf.parse((String) createdAt).getTime();
                } catch (Exception e) {
                    comment.createdAt = System.currentTimeMillis();
                }
            } else {
                comment.createdAt = System.currentTimeMillis();
            }
        } else {
            comment.createdAt = System.currentTimeMillis();
        }
        
        if (json.has("updatedAt")) {
            Object updatedAt = json.get("updatedAt");
            if (updatedAt instanceof Long) {
                comment.updatedAt = (Long) updatedAt;
            } else if (updatedAt instanceof Integer) {
                comment.updatedAt = ((Integer) updatedAt).longValue();
            } else if (updatedAt instanceof String) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    comment.updatedAt = sdf.parse((String) updatedAt).getTime();
                } catch (Exception e) {
                    comment.updatedAt = comment.createdAt;
                }
            } else {
                comment.updatedAt = comment.createdAt;
            }
        } else {
            comment.updatedAt = comment.createdAt;
        }
        
        // Parent comment ID (for replies) - can be string or ObjectId object
        if (json.has("parentCommentId")) {
            Object parentCommentIdObj = json.opt("parentCommentId");
            if (parentCommentIdObj == null || parentCommentIdObj.equals(JSONObject.NULL)) {
                comment.parentCommentId = null;
            } else if (parentCommentIdObj instanceof String) {
                comment.parentCommentId = (String) parentCommentIdObj;
            } else if (parentCommentIdObj instanceof JSONObject) {
                // If it's an object, try to get _id or id field
                JSONObject parentObj = (JSONObject) parentCommentIdObj;
                comment.parentCommentId = parentObj.optString("_id", parentObj.optString("id", null));
            } else {
                comment.parentCommentId = String.valueOf(parentCommentIdObj);
            }
        } else {
            comment.parentCommentId = null;
        }
        
        // Media URL
        comment.mediaUrl = json.optString("mediaUrl", null);
        
        // Reactions (new system)
        JSONArray reactionsArray = json.optJSONArray("reactions");
        if (reactionsArray != null) {
            comment.reactions = new ArrayList<>();
            for (int i = 0; i < reactionsArray.length(); i++) {
                try {
                    JSONObject reactionObj = reactionsArray.getJSONObject(i);
                    Reaction reaction = new Reaction();
                    JSONObject reactionUserObj = reactionObj.optJSONObject("user");
                    if (reactionUserObj != null) {
                        reaction.userId = reactionUserObj.optString("_id", reactionUserObj.optString("id", ""));
                        reaction.username = reactionUserObj.optString("username", "");
                        reaction.avatar = reactionUserObj.optString("avatar", "");
                    } else {
                        reaction.userId = "";
                        reaction.username = "";
                        reaction.avatar = "";
                    }
                    reaction.type = reactionObj.optString("type", "like");
                    comment.reactions.add(reaction);
                    
                    // Check if current user reacted
                    if (currentUserId != null && reaction.userId.equals(currentUserId)) {
                        comment.isLiked = true;
                        comment.currentUserReaction = reaction.type;
                    }
                } catch (JSONException e) {
                    // Skip invalid reactions
                }
            }
            comment.likesCount = comment.reactions.size();
        } else {
            // Fallback to old likes system
            JSONArray likesArray = json.optJSONArray("likes");
            if (likesArray != null) {
                comment.likesCount = likesArray.length();
                if (currentUserId != null) {
                    for (int i = 0; i < likesArray.length(); i++) {
                        JSONObject likeObj = likesArray.optJSONObject(i);
                        if (likeObj != null) {
                            JSONObject userLikeObj = likeObj.optJSONObject("user");
                            if (userLikeObj != null && currentUserId.equals(userLikeObj.optString("_id", ""))) {
                                comment.isLiked = true;
                                break;
                            }
                        }
                    }
                }
            } else {
                comment.likesCount = 0;
            }
        }
        
        // Is edited
        comment.isEdited = json.optBoolean("isEdited", false);
        
        // Replies (if backend supports nested structure)
        JSONArray repliesArray = json.optJSONArray("replies");
        if (repliesArray != null) {
            comment.replies = new ArrayList<>();
            for (int i = 0; i < repliesArray.length(); i++) {
                try {
                    Comment reply = Comment.fromJson(repliesArray.getJSONObject(i), currentUserId);
                    comment.replies.add(reply);
                } catch (JSONException e) {
                    // Skip invalid replies
                }
            }
            comment.repliesCount = comment.replies.size();
        } else {
            comment.repliesCount = json.optInt("repliesCount", 0);
        }
        
        return comment;
    }

    // Convert to JSON for API calls
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        if (id != null && !id.isEmpty()) {
            json.put("id", id);
        }
        json.put("content", content);
        if (parentCommentId != null && !parentCommentId.isEmpty()) {
            json.put("parentCommentId", parentCommentId);
        }
        return json;
    }

    // Format timestamp for display
    public String getFormattedTimestamp() {
        long timeDiff = System.currentTimeMillis() - createdAt;
        long seconds = timeDiff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d";
        } else if (hours > 0) {
            return hours + "h";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return "now";
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public String getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }

    public List<Comment> getReplies() { return replies; }
    public void setReplies(List<Comment> replies) { 
        this.replies = replies;
        this.repliesCount = replies != null ? replies.size() : 0;
    }

    public int getRepliesCount() { return repliesCount; }
    public void setRepliesCount(int repliesCount) { this.repliesCount = repliesCount; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public List<Reaction> getReactions() { return reactions; }
    public void setReactions(List<Reaction> reactions) { this.reactions = reactions; }

    public String getCurrentUserReaction() { return currentUserReaction; }
    public void setCurrentUserReaction(String currentUserReaction) { this.currentUserReaction = currentUserReaction; }

    public boolean isEdited() { return isEdited; }
    public void setEdited(boolean edited) { isEdited = edited; }

    // Inner class for Reaction
    public static class Reaction {
        public String userId;
        public String username;
        public String avatar;
        public String type; // like, love, haha, wow, sad, angry
    }
}

