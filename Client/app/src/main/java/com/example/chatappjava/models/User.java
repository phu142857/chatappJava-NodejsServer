package com.example.chatappjava.models;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import com.example.chatappjava.constants.ModelFields;

public class User extends BaseModel {
    private String username;
    private String email;
    private String avatar;
    private String bio;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private long lastSeen;
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
    
    // Note: hashCode() is inherited from BaseModel, no need to override
    
    // Create User from JSON
    public static User fromJsonStatic(JSONObject json) throws JSONException {
        User user = new User();
        user.fromJson(json);
        return user;
    }
    
    @Override
    public void fromJson(JSONObject json) throws JSONException {
        // Use BaseModel utility methods
        this.id = parseStringWithFallback(json, ModelFields.ID_ALT, ModelFields.ID, "");
        this.username = parseString(json, ModelFields.USERNAME, "");
        this.email = parseString(json, ModelFields.EMAIL, "");
        this.avatar = parseString(json, ModelFields.AVATAR, "");
        
        // Handle profile nested object
        JSONObject profile = json.optJSONObject(ModelFields.PROFILE);
        if (profile != null) {
            this.firstName = parseString(profile, ModelFields.FIRST_NAME, "");
            this.lastName = parseString(profile, ModelFields.LAST_NAME, "");
            this.bio = parseString(profile, ModelFields.BIO, "");
            this.phoneNumber = parseString(profile, ModelFields.PHONE_NUMBER, "");
        }
        
        this.lastSeen = parseTimestamp(json, ModelFields.LAST_SEEN, System.currentTimeMillis());
        this.isActive = parseBoolean(json, ModelFields.IS_ACTIVE, true);
        this.isFriend = parseBoolean(json, "isFriend", false);
        this.friendshipStatus = parseString(json, "friendshipStatus", ModelFields.FRIENDSHIP_NOT_FRIENDS);
        this.friendRequestStatus = parseString(json, "friendRequestStatus", ModelFields.FRIENDSHIP_NONE);
        this.friendRequestId = parseString(json, "friendRequestId", "");
        
        // Parse common fields from BaseModel
        this.createdAt = parseTimestamp(json, ModelFields.CREATED_AT, 0);
        this.updatedAt = parseTimestamp(json, ModelFields.UPDATED_AT, 0);
    }
    
    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        
        // Use BaseModel utility for common fields
        putCommonFields(json);
        
        // Add User-specific fields
        json.put(ModelFields.USERNAME, username);
        json.put(ModelFields.EMAIL, email);
        json.put(ModelFields.AVATAR, avatar);
        json.put(ModelFields.LAST_SEEN, lastSeen);
        
        // Create profile object
        JSONObject profile = new JSONObject();
        profile.put(ModelFields.FIRST_NAME, firstName);
        profile.put(ModelFields.LAST_NAME, lastName);
        profile.put(ModelFields.BIO, bio);
        profile.put(ModelFields.PHONE_NUMBER, phoneNumber);
        json.put(ModelFields.PROFILE, profile);
        
        return json;
    }
    
    // Getters and Setters
    // Note: getId(), setId(), getCreatedAt(), setCreatedAt(), getUpdatedAt(), setUpdatedAt(), 
    // isActive(), setActive() are inherited from BaseModel
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getPhoneNumber() { return phoneNumber; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getFullAvatarUrl() {
        return com.example.chatappjava.utils.UrlUtils.getFullAvatarUrl(avatar);
    }
    
    public String getBio() { return bio; }
    
    public boolean isFriend() { return isFriend; }
    public void setFriend(boolean friend) { isFriend = friend; }

    public String getFriendshipStatus() { return friendshipStatus; }
    public void setFriendshipStatus(String friendshipStatus) { this.friendshipStatus = friendshipStatus; }

    public String getFriendRequestStatus() { return friendRequestStatus; }
    public String getFriendRequestId() { return friendRequestId; }
    
    // Note: hashCode() is inherited from BaseModel, no need to override
    
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
    
    // Note: hashCode() is inherited from BaseModel, no need to override
    
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
