package com.example.chatappjava.models;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class User {
    private String id;
    private String username;
    private String email;
    private String avatar;
    private String bio;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private long lastSeen;
    private boolean isActive;
    private boolean isFriend;
    private String friendshipStatus;
    private String friendRequestStatus; // none, sent, received
    private String friendRequestId; // id of pending request if any
    
    // Constructors
    public User() {}
    
    public User(String id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.isActive = true;
    }
    
    // Create User from JSON
    public static User fromJson(JSONObject json) throws JSONException {
        User user = new User();
        // Try both "id" and "_id" fields
        user.id = json.optString("id", json.optString("_id", ""));
        user.username = json.optString("username", "");
        user.email = json.optString("email", "");
        user.avatar = json.optString("avatar", "");
        
        // Handle profile nested object
        JSONObject profile = json.optJSONObject("profile");
        if (profile != null) {
            user.firstName = profile.optString("firstName", "");
            user.lastName = profile.optString("lastName", "");
            user.bio = profile.optString("bio", "");
            user.phoneNumber = profile.optString("phoneNumber", "");
        }
        
        user.lastSeen = json.optLong("lastSeen", System.currentTimeMillis());
        user.isActive = json.optBoolean("isActive", true);
        user.isFriend = json.optBoolean("isFriend", false);
        user.friendshipStatus = json.optString("friendshipStatus", "not_friends");
        user.friendRequestStatus = json.optString("friendRequestStatus", "none");
        user.friendRequestId = json.optString("friendRequestId", "");
        
        return user;
    }
    
    // Convert User to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("_id", id);
        json.put("username", username);
        json.put("email", email);
        json.put("avatar", avatar);
        
        // Create profile object
        JSONObject profile = new JSONObject();
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("bio", bio);
        profile.put("phoneNumber", phoneNumber);
        json.put("profile", profile);
        
        json.put("lastSeen", lastSeen);
        json.put("isActive", isActive);
        return json;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getFullAvatarUrl() {
        if (avatar == null || avatar.isEmpty()) {
            return null;
        }
        if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
            return avatar; // Already a full URL
        }
        // Construct full URL from relative path
        // Ensure avatar starts with / if it doesn't already
        String avatarPath = avatar.startsWith("/") ? avatar : "/" + avatar;
        return "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() +
               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarPath;
    }
    
    public String getBio() { return bio; }
    
    public boolean isFriend() { return isFriend; }
    public void setFriend(boolean friend) { isFriend = friend; }

    public String getFriendshipStatus() { return friendshipStatus; }
    public void setFriendshipStatus(String friendshipStatus) { this.friendshipStatus = friendshipStatus; }

    public String getFriendRequestStatus() { return friendRequestStatus; }
    public String getFriendRequestId() { return friendRequestId; }
    
    // Helper methods
    public String getDisplayName() {
        if (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        } else if (firstName != null && !firstName.isEmpty()) {
            return firstName;
        } else {
            return username;
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return Objects.equals(id, user.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", displayName='" + getDisplayName() + '\'' +
                '}';
    }
}
