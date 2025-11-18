package com.example.chatappjava.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "ChatApp.db";
    private static final int DATABASE_VERSION = 3; // Incremented for offline chat feature

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

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_APP_SETTINGS);
        db.execSQL(CREATE_TABLE_CONVERSATIONS);
        db.execSQL(CREATE_TABLE_MESSAGES);
        
        // Create indexes
        db.execSQL(CREATE_INDEX_MESSAGES_CHAT_ID);
        db.execSQL(CREATE_INDEX_MESSAGES_TIMESTAMP);
        db.execSQL(CREATE_INDEX_MESSAGES_SYNC_STATUS);
        db.execSQL(CREATE_INDEX_MESSAGES_CLIENT_NONCE);
        
        Log.d(TAG, "Database created successfully with offline chat tables");
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
    }
}

