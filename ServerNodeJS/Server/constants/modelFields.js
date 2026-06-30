/**
 * Centralized field names for models to eliminate magic strings
 * Ensures consistency between client and server field definitions
 * Mirrors client-side ModelFields.java
 */

// Common field names
const ID = '_id';
const ID_ALT = 'id';
const CREATED_AT = 'createdAt';
const UPDATED_AT = 'updatedAt';
const TIMESTAMP = 'timestamp';
const IS_ACTIVE = 'isActive';

// User fields
const USERNAME = 'username';
const EMAIL = 'email';
const PASSWORD = 'password';
const AVATAR = 'avatar';
const BIO = 'bio';
const FIRST_NAME = 'firstName';
const LAST_NAME = 'lastName';
const PHONE_NUMBER = 'phoneNumber';
const LAST_SEEN = 'lastSeen';
const ROLE = 'role';
const PROFILE = 'profile';
const FRIENDS = 'friends';
const BLOCKED_USERS = 'blockedUsers';

// Message fields
const CONTENT = 'content';
const TYPE = 'type';
const SENDER = 'sender';
const CHAT = 'chat';
const REPLY_TO = 'replyTo';
const ATTACHMENTS = 'attachments';
const REACTIONS = 'reactions';
const READ_BY = 'readBy';
const IS_READ = 'isRead';
const IS_DELETED = 'isDeleted';
const IS_EDITED = 'isEdited';
const EDITED_AT = 'editedAt';
const EDIT_HISTORY = 'editHistory';
const REACTION_SUMMARY = 'reactionSummary';
const CLIENT_NONCE = 'clientNonce';

// Chat fields
const NAME = 'name';
const DESCRIPTION = 'description';
const PARTICIPANTS = 'participants';
const PARTICIPANT_IDS = 'participantIds';
const LAST_MESSAGE = 'lastMessage';
const LAST_MESSAGE_TIME = 'lastMessageTime';
const UNREAD_COUNT = 'unreadCount';
const CREATED_BY = 'createdBy';
const GROUP_ID = 'groupId';
const VISIBILITY = 'visibility';
const IS_PUBLIC = 'isPublic';
const SETTINGS = 'settings';
const JOIN_REQUEST_STATUS = 'joinRequestStatus';

// Message types
const TYPE_TEXT = 'text';
const TYPE_IMAGE = 'image';
const TYPE_FILE = 'file';
const TYPE_VIDEO = 'video';
const TYPE_AUDIO = 'audio';
const TYPE_VOICE = 'voice';
const TYPE_SYSTEM = 'system';

// Chat types
const TYPE_PRIVATE = 'private';
const TYPE_GROUP = 'group';

// User roles
const ROLE_USER = 'user';
const ROLE_ADMIN = 'admin';
const ROLE_MODERATOR = 'moderator';
const ROLE_MEMBER = 'member';

// Friendship statuses
const FRIENDSHIP_NOT_FRIENDS = 'not_friends';
const FRIENDSHIP_FRIENDS = 'friends';
const FRIENDSHIP_PENDING = 'pending';
const FRIENDSHIP_SENT = 'sent';
const FRIENDSHIP_RECEIVED = 'received';
const FRIENDSHIP_NONE = 'none';

// Privacy/visibility
const VISIBILITY_PUBLIC = 'public';
const VISIBILITY_PRIVATE = 'private';

// Join request statuses
const JOIN_NONE = 'none';
const JOIN_PENDING = 'pending';
const JOIN_APPROVED = 'approved';
const JOIN_REJECTED = 'rejected';

module.exports = {
    // Common field names
    ID,
    ID_ALT,
    CREATED_AT,
    UPDATED_AT,
    TIMESTAMP,
    IS_ACTIVE,
    
    // User fields
    USERNAME,
    EMAIL,
    PASSWORD,
    AVATAR,
    BIO,
    FIRST_NAME,
    LAST_NAME,
    PHONE_NUMBER,
    LAST_SEEN,
    ROLE,
    PROFILE,
    FRIENDS,
    BLOCKED_USERS,
    
    // Message fields
    CONTENT,
    TYPE,
    SENDER,
    CHAT,
    REPLY_TO,
    ATTACHMENTS,
    REACTIONS,
    READ_BY,
    IS_READ,
    IS_DELETED,
    IS_EDITED,
    EDITED_AT,
    EDIT_HISTORY,
    REACTION_SUMMARY,
    CLIENT_NONCE,
    
    // Chat fields
    NAME,
    DESCRIPTION,
    PARTICIPANTS,
    PARTICIPANT_IDS,
    LAST_MESSAGE,
    LAST_MESSAGE_TIME,
    UNREAD_COUNT,
    CREATED_BY,
    GROUP_ID,
    VISIBILITY,
    IS_PUBLIC,
    SETTINGS,
    JOIN_REQUEST_STATUS,
    
    // Message types
    TYPE_TEXT,
    TYPE_IMAGE,
    TYPE_FILE,
    TYPE_VIDEO,
    TYPE_AUDIO,
    TYPE_VOICE,
    TYPE_SYSTEM,
    
    // Chat types
    TYPE_PRIVATE,
    TYPE_GROUP,
    
    // User roles
    ROLE_USER,
    ROLE_ADMIN,
    ROLE_MODERATOR,
    ROLE_MEMBER,
    
    // Friendship statuses
    FRIENDSHIP_NOT_FRIENDS,
    FRIENDSHIP_FRIENDS,
    FRIENDSHIP_PENDING,
    FRIENDSHIP_SENT,
    FRIENDSHIP_RECEIVED,
    FRIENDSHIP_NONE,
    
    // Privacy/visibility
    VISIBILITY_PUBLIC,
    VISIBILITY_PRIVATE,
    
    // Join request statuses
    JOIN_NONE,
    JOIN_PENDING,
    JOIN_APPROVED,
    JOIN_REJECTED
};
