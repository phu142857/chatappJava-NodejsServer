package com.example.chatappjava.models;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Post {
    private String id;
    private String authorId;
    private String authorUsername;
    private String authorAvatar;
    private String content;
    private List<String> mediaUrls; // Images or videos
    private String mediaType; // "image", "video", "gallery", "none"
    private long timestamp;
    private int likesCount;
    private int commentsCount;
    private int sharesCount;
    private boolean isLiked;
    private String reactionType; // "like", "love", "wow", "sad", "angry", null
    
    // Constructors
    public Post() {
        this.mediaUrls = new ArrayList<>();
    }
    
    public Post(String id, String authorId, String authorUsername, String content) {
        this.id = id;
        this.authorId = authorId;
        this.authorUsername = authorUsername;
        this.content = content;
        this.mediaUrls = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
        this.likesCount = 0;
        this.commentsCount = 0;
        this.sharesCount = 0;
        this.isLiked = false;
        this.mediaType = "none";
    }
    
    // Create Post from JSON
    public static Post fromJson(JSONObject json) throws JSONException {
        Post post = new Post();
        post.id = json.optString("_id", json.optString("id", ""));
        post.content = json.optString("content", "");
        
        // Handle timestamp - backend uses createdAt
        if (json.has("createdAt")) {
            try {
                Object createdAt = json.get("createdAt");
                if (createdAt instanceof Long) {
                    post.timestamp = (Long) createdAt;
                } else if (createdAt instanceof Integer) {
                    post.timestamp = ((Integer) createdAt).longValue();
                } else if (createdAt instanceof String) {
                    // Try to parse ISO date string (e.g., "2024-01-01T12:00:00.000Z")
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        post.timestamp = sdf.parse((String) createdAt).getTime();
                    } catch (Exception e) {
                        // Try alternative format
                        try {
                            java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                            sdf2.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                            post.timestamp = sdf2.parse((String) createdAt).getTime();
                        } catch (Exception e2) {
                            post.timestamp = System.currentTimeMillis();
                        }
                    }
                } else {
                    post.timestamp = System.currentTimeMillis();
                }
            } catch (Exception e) {
                post.timestamp = json.optLong("createdAt", System.currentTimeMillis());
            }
        } else {
            post.timestamp = json.optLong("timestamp", System.currentTimeMillis());
        }
        
        // Handle likes - backend has likes array
        JSONArray likesArray = json.optJSONArray("likes");
        if (likesArray != null) {
            post.likesCount = likesArray.length();
            // Check if current user liked (will be set by caller if needed)
            post.isLiked = false;
        } else {
            post.likesCount = json.optInt("likesCount", 0);
            post.isLiked = json.optBoolean("isLiked", false);
        }
        
        // Handle comments - backend has comments array
        JSONArray commentsArray = json.optJSONArray("comments");
        if (commentsArray != null) {
            post.commentsCount = commentsArray.length();
        } else {
            post.commentsCount = json.optInt("commentsCount", 0);
        }
        
        // Handle shares - backend has shares array
        JSONArray sharesArray = json.optJSONArray("shares");
        if (sharesArray != null) {
            post.sharesCount = sharesArray.length();
        } else {
            post.sharesCount = json.optInt("sharesCount", 0);
        }
        
        post.reactionType = json.optString("reactionType", null);
        
        // Handle author/user information - backend uses userId (populated)
        JSONObject userIdObj = json.optJSONObject("userId");
        if (userIdObj != null) {
            post.authorId = userIdObj.optString("_id", userIdObj.optString("id", ""));
            post.authorUsername = userIdObj.optString("username", "");
            post.authorAvatar = userIdObj.optString("avatar", "");
        } else {
            // Fallback to direct fields
            post.authorId = json.optString("authorId", json.optString("userId", ""));
            post.authorUsername = json.optString("authorUsername", json.optString("username", ""));
            post.authorAvatar = json.optString("authorAvatar", json.optString("avatar", ""));
        }
        
        // Handle media - backend uses "images" array
        JSONArray imagesArray = json.optJSONArray("images");
        if (imagesArray == null) {
            imagesArray = json.optJSONArray("media"); // Fallback
        }
        
        if (imagesArray != null && imagesArray.length() > 0) {
            post.mediaUrls = new ArrayList<>();
            for (int i = 0; i < imagesArray.length(); i++) {
                Object item = imagesArray.get(i);
                if (item instanceof String) {
                    String url = (String) item;
                    // Convert relative URL to full URL if needed
                    if (!url.startsWith("http")) {
                        url = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                              ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + url;
                    }
                    post.mediaUrls.add(url);
                } else if (item instanceof JSONObject) {
                    JSONObject mediaObj = (JSONObject) item;
                    String url = mediaObj.optString("url", mediaObj.optString("path", ""));
                    if (!url.isEmpty()) {
                        if (!url.startsWith("http")) {
                            url = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                                  ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + url;
                        }
                        post.mediaUrls.add(url);
                    }
                }
            }
            
            // Determine media type
            if (post.mediaUrls.size() > 1) {
                post.mediaType = "gallery";
            } else if (post.mediaUrls.size() == 1) {
                String url = post.mediaUrls.get(0).toLowerCase();
                if (url.contains(".mp4") || url.contains(".mov") || url.contains(".avi") || url.contains("video")) {
                    post.mediaType = "video";
                } else {
                    post.mediaType = "image";
                }
            }
        } else {
            post.mediaType = "none";
        }
        
        return post;
    }
    
    // Convert to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("authorId", authorId);
        json.put("authorUsername", authorUsername);
        json.put("authorAvatar", authorAvatar);
        json.put("content", content);
        json.put("timestamp", timestamp);
        json.put("likesCount", likesCount);
        json.put("commentsCount", commentsCount);
        json.put("sharesCount", sharesCount);
        json.put("isLiked", isLiked);
        if (reactionType != null) {
            json.put("reactionType", reactionType);
        }
        
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            JSONArray mediaArray = new JSONArray();
            for (String url : mediaUrls) {
                mediaArray.put(url);
            }
            json.put("media", mediaArray);
        }
        json.put("mediaType", mediaType);
        
        return json;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getAuthorId() {
        return authorId;
    }
    
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }
    
    public String getAuthorUsername() {
        return authorUsername;
    }
    
    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }
    
    public String getAuthorAvatar() {
        return authorAvatar;
    }
    
    public void setAuthorAvatar(String authorAvatar) {
        this.authorAvatar = authorAvatar;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public List<String> getMediaUrls() {
        return mediaUrls;
    }
    
    public void setMediaUrls(List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }
    
    public String getMediaType() {
        return mediaType;
    }
    
    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getLikesCount() {
        return likesCount;
    }
    
    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }
    
    public int getCommentsCount() {
        return commentsCount;
    }
    
    public void setCommentsCount(int commentsCount) {
        this.commentsCount = commentsCount;
    }
    
    public int getSharesCount() {
        return sharesCount;
    }
    
    public void setSharesCount(int sharesCount) {
        this.sharesCount = sharesCount;
    }
    
    public boolean isLiked() {
        return isLiked;
    }
    
    public void setLiked(boolean liked) {
        isLiked = liked;
    }
    
    public String getReactionType() {
        return reactionType;
    }
    
    public void setReactionType(String reactionType) {
        this.reactionType = reactionType;
    }
    
    // Helper methods
    public String getFormattedTimestamp() {
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
    
    @NonNull
    @Override
    public String toString() {
        return "Post{" +
                "id='" + id + '\'' +
                ", authorUsername='" + authorUsername + '\'' +
                ", content='" + content + '\'' +
                ", likesCount=" + likesCount +
                '}';
    }
}

