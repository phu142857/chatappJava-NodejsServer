package com.example.chatappjava.constants;

/**
 * Centralized field names for models to eliminate magic strings
 * Ensures consistency between client and server field definitions
 */
public class ModelFields {
    
    // Common field names
    public static final String ID = "_id";
    public static final String ID_ALT = "id";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String TIMESTAMP = "timestamp";
    public static final String IS_ACTIVE = "isActive";
    
    // User fields
    public static final String USERNAME = "username";
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    public static final String AVATAR = "avatar";
    public static final String BIO = "bio";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String PHONE_NUMBER = "phoneNumber";
    public static final String LAST_SEEN = "lastSeen";
    public static final String ROLE = "role";
    public static final String PROFILE = "profile";
    public static final String FRIENDS = "friends";
    public static final String BLOCKED_USERS = "blockedUsers";
    
    // Message fields
    public static final String CONTENT = "content";
    public static final String TYPE = "type";
    public static final String SENDER = "sender";
    public static final String CHAT = "chat";
    public static final String REPLY_TO = "replyTo";
    public static final String ATTACHMENTS = "attachments";
    public static final String REACTIONS = "reactions";
    public static final String READ_BY = "readBy";
    public static final String IS_READ = "isRead";
    public static final String IS_DELETED = "isDeleted";
    public static final String IS_EDITED = "isEdited";
    public static final String EDITED_AT = "editedAt";
    public static final String EDIT_HISTORY = "editHistory";
    public static final String REACTION_SUMMARY = "reactionSummary";
    public static final String CLIENT_NONCE = "clientNonce";
    
    // Chat fields
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String PARTICIPANTS = "participants";
    public static final String PARTICIPANT_IDS = "participantIds";
    public static final String LAST_MESSAGE = "lastMessage";
    public static final String LAST_MESSAGE_TIME = "lastMessageTime";
    public static final String UNREAD_COUNT = "unreadCount";
    public static final String CREATED_BY = "createdBy";
    public static final String GROUP_ID = "groupId";
    public static final String VISIBILITY = "visibility";
    public static final String IS_PUBLIC = "isPublic";
    public static final String SETTINGS = "settings";
    public static final String JOIN_REQUEST_STATUS = "joinRequestStatus";
    
    // Message types
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_VOICE = "voice";
    public static final String TYPE_SYSTEM = "system";
    
    // Chat types
    public static final String TYPE_PRIVATE = "private";
    public static final String TYPE_GROUP = "group";
    
    // User roles
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MODERATOR = "moderator";
    public static final String ROLE_MEMBER = "member";
    
    // Friendship statuses
    public static final String FRIENDSHIP_NOT_FRIENDS = "not_friends";
    public static final String FRIENDSHIP_FRIENDS = "friends";
    public static final String FRIENDSHIP_PENDING = "pending";
    public static final String FRIENDSHIP_SENT = "sent";
    public static final String FRIENDSHIP_RECEIVED = "received";
    public static final String FRIENDSHIP_NONE = "none";
    
    // Privacy/visibility
    public static final String VISIBILITY_PUBLIC = "public";
    public static final String VISIBILITY_PRIVATE = "private";
    
    // Join request statuses
    public static final String JOIN_NONE = "none";
    public static final String JOIN_PENDING = "pending";
    public static final String JOIN_APPROVED = "approved";
    public static final String JOIN_REJECTED = "rejected";
}
