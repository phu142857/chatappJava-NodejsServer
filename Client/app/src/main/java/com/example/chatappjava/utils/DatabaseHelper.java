package com.example.chatappjava.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "ChatApp.db";
    private static final int DATABASE_VERSION = 4; // Incremented for calls and posts caching

    // ===== Table: app_settings =====
    public static final String TABLE_APP_SETTINGS = "app_settings";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_VALUE = "value";

    // ===== Table: conversations (Chats) =====
    public static final String TABLE_CONVERSATIONS = "conversations";
    public static final String COL_CONV_ID = "id";
    public static final String COL_CONV_TYPE = "type"; // "private" or "group"
    public static final String COL_CONV_NAME = "name";
    public static final String COL_CONV_DESCRIPTION = "description";
    public static final String COL_CONV_AVATAR = "avatar";
    public static final String COL_CONV_LAST_MESSAGE = "last_message";
    public static final String COL_CONV_LAST_MESSAGE_TIME = "last_message_time";
    public static final String COL_CONV_UNREAD_COUNT = "unread_count";
    public static final String COL_CONV_IS_ACTIVE = "is_active";
    public static final String COL_CONV_CREATOR_ID = "creator_id";
    public static final String COL_CONV_CREATED_AT = "created_at";
    public static final String COL_CONV_UPDATED_AT = "updated_at";
    public static final String COL_CONV_PARTICIPANTS = "participants"; // JSON array
    public static final String COL_CONV_GROUP_ID = "group_id";
    public static final String COL_CONV_IS_PUBLIC = "is_public";
    public static final String COL_CONV_VISIBILITY = "visibility";

    // ===== Table: messages =====
    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_MSG_ID = "id";
    public static final String COL_MSG_CHAT_ID = "chat_id";
    public static final String COL_MSG_SENDER_ID = "sender_id";
    public static final String COL_MSG_SENDER_NAME = "sender_name";
    public static final String COL_MSG_SENDER_AVATAR = "sender_avatar";
    public static final String COL_MSG_CONTENT = "content";
    public static final String COL_MSG_TYPE = "type"; // "text", "image", "file", "voice"
    public static final String COL_MSG_CHAT_TYPE = "chat_type"; // "private", "group"
    public static final String COL_MSG_TIMESTAMP = "timestamp";
    public static final String COL_MSG_IS_READ = "is_read";
    public static final String COL_MSG_IS_DELETED = "is_deleted";
    public static final String COL_MSG_ATTACHMENTS = "attachments"; // JSON string
    public static final String COL_MSG_LOCAL_IMAGE_URI = "local_image_uri";
    public static final String COL_MSG_REPLY_TO_ID = "reply_to_id";
    public static final String COL_MSG_REPLY_TO_CONTENT = "reply_to_content";
    public static final String COL_MSG_REPLY_TO_SENDER = "reply_to_sender";
    public static final String COL_MSG_EDITED = "edited";
    public static final String COL_MSG_EDITED_AT = "edited_at";
    public static final String COL_MSG_REACTIONS = "reactions"; // JSON string
    public static final String COL_MSG_CLIENT_NONCE = "client_nonce"; // For deduplication
    public static final String COL_MSG_SYNC_STATUS = "sync_status"; // "synced", "pending", "failed"
    public static final String COL_MSG_SYNC_ATTEMPTS = "sync_attempts";
    public static final String COL_MSG_SYNC_ERROR = "sync_error";

    // Create table SQL statements
    private static final String CREATE_TABLE_APP_SETTINGS = 
        "CREATE TABLE " + TABLE_APP_SETTINGS + " (" +
        COLUMN_KEY + " TEXT PRIMARY KEY, " +
        COLUMN_VALUE + " TEXT" +
        ")";

    private static final String CREATE_TABLE_CONVERSATIONS = 
        "CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
        COL_CONV_ID + " TEXT PRIMARY KEY, " +
        COL_CONV_TYPE + " TEXT NOT NULL, " +
        COL_CONV_NAME + " TEXT, " +
        COL_CONV_DESCRIPTION + " TEXT, " +
        COL_CONV_AVATAR + " TEXT, " +
        COL_CONV_LAST_MESSAGE + " TEXT, " +
        COL_CONV_LAST_MESSAGE_TIME + " INTEGER DEFAULT 0, " +
        COL_CONV_UNREAD_COUNT + " INTEGER DEFAULT 0, " +
        COL_CONV_IS_ACTIVE + " INTEGER DEFAULT 1, " +
        COL_CONV_CREATOR_ID + " TEXT, " +
        COL_CONV_CREATED_AT + " INTEGER DEFAULT 0, " +
        COL_CONV_UPDATED_AT + " INTEGER DEFAULT 0, " +
        COL_CONV_PARTICIPANTS + " TEXT, " + // JSON array
        COL_CONV_GROUP_ID + " TEXT, " +
        COL_CONV_IS_PUBLIC + " INTEGER DEFAULT 0, " +
        COL_CONV_VISIBILITY + " TEXT" +
        ")";

    private static final String CREATE_TABLE_MESSAGES = 
        "CREATE TABLE " + TABLE_MESSAGES + " (" +
        COL_MSG_ID + " TEXT PRIMARY KEY, " +
        COL_MSG_CHAT_ID + " TEXT NOT NULL, " +
        COL_MSG_SENDER_ID + " TEXT NOT NULL, " +
        COL_MSG_SENDER_NAME + " TEXT, " +
        COL_MSG_SENDER_AVATAR + " TEXT, " +
        COL_MSG_CONTENT + " TEXT, " +
        COL_MSG_TYPE + " TEXT DEFAULT 'text', " +
        COL_MSG_CHAT_TYPE + " TEXT DEFAULT 'private', " +
        COL_MSG_TIMESTAMP + " INTEGER NOT NULL, " +
        COL_MSG_IS_READ + " INTEGER DEFAULT 0, " +
        COL_MSG_IS_DELETED + " INTEGER DEFAULT 0, " +
        COL_MSG_ATTACHMENTS + " TEXT, " +
        COL_MSG_LOCAL_IMAGE_URI + " TEXT, " +
        COL_MSG_REPLY_TO_ID + " TEXT, " +
        COL_MSG_REPLY_TO_CONTENT + " TEXT, " +
        COL_MSG_REPLY_TO_SENDER + " TEXT, " +
        COL_MSG_EDITED + " INTEGER DEFAULT 0, " +
        COL_MSG_EDITED_AT + " INTEGER DEFAULT 0, " +
        COL_MSG_REACTIONS + " TEXT, " +
        COL_MSG_CLIENT_NONCE + " TEXT, " +
        COL_MSG_SYNC_STATUS + " TEXT DEFAULT 'synced', " + // synced, pending, failed
        COL_MSG_SYNC_ATTEMPTS + " INTEGER DEFAULT 0, " +
        COL_MSG_SYNC_ERROR + " TEXT, " +
        "FOREIGN KEY(" + COL_MSG_CHAT_ID + ") REFERENCES " + TABLE_CONVERSATIONS + "(" + COL_CONV_ID + ") ON DELETE CASCADE" +
        ")";

    // Indexes for better query performance
    private static final String CREATE_INDEX_MESSAGES_CHAT_ID = 
        "CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON " + TABLE_MESSAGES + "(" + COL_MSG_CHAT_ID + ")";
    
    private static final String CREATE_INDEX_MESSAGES_TIMESTAMP = 
        "CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON " + TABLE_MESSAGES + "(" + COL_MSG_TIMESTAMP + ")";
    
    private static final String CREATE_INDEX_MESSAGES_SYNC_STATUS = 
        "CREATE INDEX IF NOT EXISTS idx_messages_sync_status ON " + TABLE_MESSAGES + "(" + COL_MSG_SYNC_STATUS + ")";
    
    private static final String CREATE_INDEX_MESSAGES_CLIENT_NONCE = 
        "CREATE INDEX IF NOT EXISTS idx_messages_client_nonce ON " + TABLE_MESSAGES + "(" + COL_MSG_CLIENT_NONCE + ")";

    // ===== Table: calls =====
    public static final String TABLE_CALLS = "calls";
    public static final String COL_CALL_ID = "call_id";
    public static final String COL_CALL_TYPE = "type"; // "audio" or "video"
    public static final String COL_CALL_CHAT_ID = "chat_id";
    public static final String COL_CALL_CHAT_NAME = "chat_name";
    public static final String COL_CALL_CHAT_TYPE = "chat_type"; // "private" or "group"
    public static final String COL_CALL_STATUS = "status"; // "initiated", "ringing", "active", "ended", "declined", "missed", "cancelled"
    public static final String COL_CALL_STARTED_AT = "started_at";
    public static final String COL_CALL_ENDED_AT = "ended_at";
    public static final String COL_CALL_DURATION = "duration"; // in seconds
    public static final String COL_CALL_IS_GROUP_CALL = "is_group_call";
    public static final String COL_CALL_CALLER_ID = "caller_id";
    public static final String COL_CALL_CALLER_NAME = "caller_name";
    public static final String COL_CALL_CALLER_AVATAR = "caller_avatar";
    public static final String COL_CALL_PARTICIPANTS = "participants"; // JSON array

    // ===== Table: posts =====
    public static final String TABLE_POSTS = "posts";
    public static final String COL_POST_ID = "id";
    public static final String COL_POST_AUTHOR_ID = "author_id";
    public static final String COL_POST_AUTHOR_USERNAME = "author_username";
    public static final String COL_POST_AUTHOR_AVATAR = "author_avatar";
    public static final String COL_POST_CONTENT = "content";
    public static final String COL_POST_MEDIA_URLS = "media_urls"; // JSON array
    public static final String COL_POST_MEDIA_TYPE = "media_type"; // "image", "video", "gallery", "none"
    public static final String COL_POST_TIMESTAMP = "timestamp";
    public static final String COL_POST_LIKES_COUNT = "likes_count";
    public static final String COL_POST_COMMENTS_COUNT = "comments_count";
    public static final String COL_POST_SHARES_COUNT = "shares_count";
    public static final String COL_POST_IS_LIKED = "is_liked";
    public static final String COL_POST_REACTION_TYPE = "reaction_type";
    public static final String COL_POST_SHARED_POST_ID = "shared_post_id";
    public static final String COL_POST_TAGGED_USERS = "tagged_users"; // JSON array

    private static final String CREATE_TABLE_CALLS = 
        "CREATE TABLE " + TABLE_CALLS + " (" +
        COL_CALL_ID + " TEXT PRIMARY KEY, " +
        COL_CALL_TYPE + " TEXT DEFAULT 'video', " +
        COL_CALL_CHAT_ID + " TEXT, " +
        COL_CALL_CHAT_NAME + " TEXT, " +
        COL_CALL_CHAT_TYPE + " TEXT DEFAULT 'private', " +
        COL_CALL_STATUS + " TEXT DEFAULT 'initiated', " +
        COL_CALL_STARTED_AT + " INTEGER NOT NULL, " +
        COL_CALL_ENDED_AT + " INTEGER DEFAULT 0, " +
        COL_CALL_DURATION + " INTEGER DEFAULT 0, " +
        COL_CALL_IS_GROUP_CALL + " INTEGER DEFAULT 0, " +
        COL_CALL_CALLER_ID + " TEXT, " +
        COL_CALL_CALLER_NAME + " TEXT, " +
        COL_CALL_CALLER_AVATAR + " TEXT, " +
        COL_CALL_PARTICIPANTS + " TEXT" + // JSON array
        ")";

    private static final String CREATE_TABLE_POSTS = 
        "CREATE TABLE " + TABLE_POSTS + " (" +
        COL_POST_ID + " TEXT PRIMARY KEY, " +
        COL_POST_AUTHOR_ID + " TEXT NOT NULL, " +
        COL_POST_AUTHOR_USERNAME + " TEXT, " +
        COL_POST_AUTHOR_AVATAR + " TEXT, " +
        COL_POST_CONTENT + " TEXT, " +
        COL_POST_MEDIA_URLS + " TEXT, " + // JSON array
        COL_POST_MEDIA_TYPE + " TEXT DEFAULT 'none', " +
        COL_POST_TIMESTAMP + " INTEGER NOT NULL, " +
        COL_POST_LIKES_COUNT + " INTEGER DEFAULT 0, " +
        COL_POST_COMMENTS_COUNT + " INTEGER DEFAULT 0, " +
        COL_POST_SHARES_COUNT + " INTEGER DEFAULT 0, " +
        COL_POST_IS_LIKED + " INTEGER DEFAULT 0, " +
        COL_POST_REACTION_TYPE + " TEXT, " +
        COL_POST_SHARED_POST_ID + " TEXT, " +
        COL_POST_TAGGED_USERS + " TEXT" + // JSON array
        ")";

    // Indexes for calls and posts
    private static final String CREATE_INDEX_CALLS_STARTED_AT = 
        "CREATE INDEX IF NOT EXISTS idx_calls_started_at ON " + TABLE_CALLS + "(" + COL_CALL_STARTED_AT + ")";
    
    private static final String CREATE_INDEX_POSTS_TIMESTAMP = 
        "CREATE INDEX IF NOT EXISTS idx_posts_timestamp ON " + TABLE_POSTS + "(" + COL_POST_TIMESTAMP + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_APP_SETTINGS);
        db.execSQL(CREATE_TABLE_CONVERSATIONS);
        db.execSQL(CREATE_TABLE_MESSAGES);
        db.execSQL(CREATE_TABLE_CALLS);
        db.execSQL(CREATE_TABLE_POSTS);
        
        // Create indexes
        db.execSQL(CREATE_INDEX_MESSAGES_CHAT_ID);
        db.execSQL(CREATE_INDEX_MESSAGES_TIMESTAMP);
        db.execSQL(CREATE_INDEX_MESSAGES_SYNC_STATUS);
        db.execSQL(CREATE_INDEX_MESSAGES_CLIENT_NONCE);
        db.execSQL(CREATE_INDEX_CALLS_STARTED_AT);
        db.execSQL(CREATE_INDEX_POSTS_TIMESTAMP);
        
        Log.d(TAG, "Database created successfully with offline chat, calls, and posts tables");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        
        if (oldVersion < 3) {
            // Add offline chat tables
            db.execSQL(CREATE_TABLE_CONVERSATIONS);
            db.execSQL(CREATE_TABLE_MESSAGES);
            db.execSQL(CREATE_INDEX_MESSAGES_CHAT_ID);
            db.execSQL(CREATE_INDEX_MESSAGES_TIMESTAMP);
            db.execSQL(CREATE_INDEX_MESSAGES_SYNC_STATUS);
            db.execSQL(CREATE_INDEX_MESSAGES_CLIENT_NONCE);
            Log.d(TAG, "Added offline chat tables");
        }
        
        if (oldVersion < 4) {
            // Add calls and posts tables
            db.execSQL(CREATE_TABLE_CALLS);
            db.execSQL(CREATE_TABLE_POSTS);
            db.execSQL(CREATE_INDEX_CALLS_STARTED_AT);
            db.execSQL(CREATE_INDEX_POSTS_TIMESTAMP);
            Log.d(TAG, "Added calls and posts tables");
        }
    }
}

