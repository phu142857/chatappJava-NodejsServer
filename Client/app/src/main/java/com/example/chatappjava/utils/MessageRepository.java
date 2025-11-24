package com.example.chatappjava.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.example.chatappjava.models.Message;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository class for managing messages in SQLite database
 * Handles offline message storage, retrieval, and synchronization
 */
public class MessageRepository {
    private static final String TAG = "MessageRepository";
    private static final String SYNC_STATUS_SYNCED = "synced";
    private static final String SYNC_STATUS_PENDING = "pending";
    private static final String SYNC_STATUS_FAILED = "failed";
    
    private final DatabaseHelper dbHelper;
    private final Context context;
    
    public MessageRepository(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }
    
    /**
     * Save a message to local database
     * If message has no ID (new message), generates a temporary ID and sets status to PENDING
     * 
     * @param message Message to save
     * @return The saved message with ID (server ID or generated temp ID)
     */
    public Message saveMessage(Message message) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            // Generate temporary ID if message doesn't have one (offline message)
            String messageId = message.getId();
            String syncStatus = SYNC_STATUS_SYNCED;
            
            if (messageId == null || messageId.isEmpty()) {
                // This is a new offline message - generate temp ID
                messageId = "temp_" + UUID.randomUUID().toString();
                syncStatus = SYNC_STATUS_PENDING;
                message.setId(messageId);
                message.setClientNonce(messageId); // Use temp ID as nonce for deduplication
                Log.d(TAG, "Saving new offline message with temp ID: " + messageId);
            }
            
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_MSG_ID, messageId);
            values.put(DatabaseHelper.COL_MSG_CHAT_ID, message.getChatId());
            values.put(DatabaseHelper.COL_MSG_SENDER_ID, message.getSenderId());
            values.put(DatabaseHelper.COL_MSG_SENDER_NAME, message.getSenderDisplayName());
            values.put(DatabaseHelper.COL_MSG_SENDER_AVATAR, message.getSenderAvatarUrl());
            values.put(DatabaseHelper.COL_MSG_CONTENT, message.getContent());
            values.put(DatabaseHelper.COL_MSG_TYPE, message.getType());
            values.put(DatabaseHelper.COL_MSG_CHAT_TYPE, message.getChatType());
            values.put(DatabaseHelper.COL_MSG_TIMESTAMP, message.getTimestamp());
            values.put(DatabaseHelper.COL_MSG_IS_READ, message.isRead() ? 1 : 0);
            values.put(DatabaseHelper.COL_MSG_IS_DELETED, message.isDeleted() ? 1 : 0);
            values.put(DatabaseHelper.COL_MSG_ATTACHMENTS, message.getAttachments());
            values.put(DatabaseHelper.COL_MSG_LOCAL_IMAGE_URI, message.getLocalImageUri());
            values.put(DatabaseHelper.COL_MSG_REPLY_TO_ID, message.getReplyToMessageId());
            values.put(DatabaseHelper.COL_MSG_REPLY_TO_CONTENT, message.getReplyToContent());
            values.put(DatabaseHelper.COL_MSG_REPLY_TO_SENDER, message.getReplyToSenderName());
            values.put(DatabaseHelper.COL_MSG_EDITED, message.isEdited() ? 1 : 0);
            values.put(DatabaseHelper.COL_MSG_EDITED_AT, message.getEditedAt());
            values.put(DatabaseHelper.COL_MSG_REACTIONS, message.getReactionsRaw());
            values.put(DatabaseHelper.COL_MSG_CLIENT_NONCE, message.getClientNonce());
            values.put(DatabaseHelper.COL_MSG_SYNC_STATUS, syncStatus);
            values.put(DatabaseHelper.COL_MSG_SYNC_ATTEMPTS, 0);
            values.putNull(DatabaseHelper.COL_MSG_SYNC_ERROR);
            
            // Use INSERT OR REPLACE to handle both insert and update
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_MESSAGES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            Log.d(TAG, "Message saved: " + messageId + " (status: " + syncStatus + ")");
            return message;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving message: " + e.getMessage(), e);
            return null;
        } finally {
            db.close();
        }
    }
    
    /**
     * Get all messages for a specific chat, ordered by timestamp
     * 
     * @param chatId Chat ID
     * @param limit Maximum number of messages to retrieve (0 = no limit)
     * @return List of messages
     */
    public List<Message> getMessagesForChat(String chatId, int limit) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String orderBy = DatabaseHelper.COL_MSG_TIMESTAMP + " ASC";
        String limitStr = limit > 0 ? String.valueOf(limit) : null;
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_MESSAGES,
            null,
            DatabaseHelper.COL_MSG_CHAT_ID + " = ? AND " + DatabaseHelper.COL_MSG_IS_DELETED + " = 0",
            new String[]{chatId},
            null,
            null,
            orderBy,
            limitStr
        );
        
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Message message = cursorToMessage(cursor);
                    if (message != null) {
                        messages.add(message);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        Log.d(TAG, "Retrieved " + messages.size() + " messages for chat: " + chatId);
        return messages;
    }
    
    /**
     * Get messages for a chat with pagination (for loading older messages)
     * 
     * @param chatId Chat ID
     * @param beforeTimestamp Load messages before this timestamp
     * @param limit Maximum number of messages
     * @return List of messages
     */
    public List<Message> getMessagesBefore(String chatId, long beforeTimestamp, int limit) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String whereClause = DatabaseHelper.COL_MSG_CHAT_ID + " = ? AND " +
                            DatabaseHelper.COL_MSG_TIMESTAMP + " < ? AND " +
                            DatabaseHelper.COL_MSG_IS_DELETED + " = 0";
        String[] whereArgs = {chatId, String.valueOf(beforeTimestamp)};
        String orderBy = DatabaseHelper.COL_MSG_TIMESTAMP + " DESC";
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_MESSAGES,
            null,
            whereClause,
            whereArgs,
            null,
            null,
            orderBy,
            String.valueOf(limit)
        );
        
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Message message = cursorToMessage(cursor);
                    if (message != null) {
                        messages.add(0, message); // Add to beginning to maintain ASC order
                    }
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        return messages;
    }
    
    /**
     * Get all pending messages that need to be synced
     * 
     * @return List of pending messages
     */
    public List<Message> getPendingMessages() {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_MESSAGES,
            null,
            DatabaseHelper.COL_MSG_SYNC_STATUS + " = ?",
            new String[]{SYNC_STATUS_PENDING},
            null,
            null,
            DatabaseHelper.COL_MSG_TIMESTAMP + " ASC",
            null
        );
        
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Message message = cursorToMessage(cursor);
                    if (message != null) {
                        messages.add(message);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        Log.d(TAG, "Found " + messages.size() + " pending messages");
        return messages;
    }
    
    /**
     * Update message sync status after successful/failed sync
     * 
     * @param messageId Message ID (can be temp ID)
     * @param newMessageId Server-assigned message ID (if sync successful)
     * @param syncStatus New sync status
     * @param error Error message (if sync failed)
     */
    public void updateSyncStatus(String messageId, String newMessageId, String syncStatus, String error) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_MSG_SYNC_STATUS, syncStatus);
            
            // If we got a new server ID, update the message ID
            if (newMessageId != null && !newMessageId.isEmpty() && !newMessageId.equals(messageId)) {
                // First, check if new ID already exists (duplicate)
                Cursor checkCursor = db.query(
                    DatabaseHelper.TABLE_MESSAGES,
                    new String[]{DatabaseHelper.COL_MSG_ID},
                    DatabaseHelper.COL_MSG_ID + " = ?",
                    new String[]{newMessageId},
                    null, null, null
                );
                
                if (checkCursor != null && checkCursor.getCount() > 0) {
                    // New ID already exists - delete the temp message (duplicate)
                    db.delete(DatabaseHelper.TABLE_MESSAGES, 
                        DatabaseHelper.COL_MSG_ID + " = ?", 
                        new String[]{messageId});
                    Log.d(TAG, "Deleted duplicate message with temp ID: " + messageId);
                } else {
                    // Update temp ID to server ID
                    values.put(DatabaseHelper.COL_MSG_ID, newMessageId);
                }
                
                if (checkCursor != null) {
                    checkCursor.close();
                }
            }
            
            if (error != null) {
                values.put(DatabaseHelper.COL_MSG_SYNC_ERROR, error);
                // Increment sync attempts
                Cursor cursor = db.query(
                    DatabaseHelper.TABLE_MESSAGES,
                    new String[]{DatabaseHelper.COL_MSG_SYNC_ATTEMPTS},
                    DatabaseHelper.COL_MSG_ID + " = ?",
                    new String[]{messageId},
                    null, null, null
                );
                
                int attempts = 0;
                if (cursor != null && cursor.moveToFirst()) {
                    int attemptsIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_SYNC_ATTEMPTS);
                    if (attemptsIndex >= 0) {
                        attempts = cursor.getInt(attemptsIndex);
                    }
                    cursor.close();
                }
                values.put(DatabaseHelper.COL_MSG_SYNC_ATTEMPTS, attempts + 1);
            } else {
                values.putNull(DatabaseHelper.COL_MSG_SYNC_ERROR);
            }
            
            // Update by temp ID or new ID
            String updateId = (newMessageId != null && !newMessageId.isEmpty() && !newMessageId.equals(messageId)) 
                ? newMessageId : messageId;
            
            int rowsAffected = db.update(
                DatabaseHelper.TABLE_MESSAGES,
                values,
                DatabaseHelper.COL_MSG_ID + " = ?",
                new String[]{messageId}
            );
            
            if (rowsAffected > 0 && newMessageId != null && !newMessageId.isEmpty() && !newMessageId.equals(messageId)) {
                // If we updated the ID, we need to update the row with the new ID
                // This is a workaround for SQLite's limitation with updating PRIMARY KEY
                // We'll handle this by deleting old and inserting new
                ContentValues newValues = new ContentValues();
                Cursor oldCursor = db.query(
                    DatabaseHelper.TABLE_MESSAGES,
                    null,
                    DatabaseHelper.COL_MSG_ID + " = ?",
                    new String[]{messageId},
                    null, null, null
                );
                
                if (oldCursor != null && oldCursor.moveToFirst()) {
                    // Copy all values to new row
                    for (int i = 0; i < oldCursor.getColumnCount(); i++) {
                        String columnName = oldCursor.getColumnName(i);
                        if (!columnName.equals(DatabaseHelper.COL_MSG_ID)) {
                            int type = oldCursor.getType(i);
                            if (type == Cursor.FIELD_TYPE_STRING) {
                                newValues.put(columnName, oldCursor.getString(i));
                            } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                                newValues.put(columnName, oldCursor.getLong(i));
                            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                                newValues.put(columnName, oldCursor.getDouble(i));
                            } else if (type == Cursor.FIELD_TYPE_BLOB) {
                                newValues.put(columnName, oldCursor.getBlob(i));
                            }
                        }
                    }
                    oldCursor.close();
                    
                    // Insert with new ID
                    newValues.put(DatabaseHelper.COL_MSG_ID, newMessageId);
                    db.insertWithOnConflict(
                        DatabaseHelper.TABLE_MESSAGES,
                        null,
                        newValues,
                        SQLiteDatabase.CONFLICT_REPLACE
                    );
                    
                    // Delete old row
                    db.delete(DatabaseHelper.TABLE_MESSAGES,
                        DatabaseHelper.COL_MSG_ID + " = ?",
                        new String[]{messageId});
                }
            }
            
            Log.d(TAG, "Updated sync status for message: " + messageId + " -> " + syncStatus);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating sync status: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Mark message as read
     */
    public void markMessageAsRead(String messageId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_MSG_IS_READ, 1);
        
        db.update(
            DatabaseHelper.TABLE_MESSAGES,
            values,
            DatabaseHelper.COL_MSG_ID + " = ?",
            new String[]{messageId}
        );
        
        db.close();
    }
    
    /**
     * Delete a message (soft delete)
     */
    public void deleteMessage(String messageId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_MSG_IS_DELETED, 1);
        
        db.update(
            DatabaseHelper.TABLE_MESSAGES,
            values,
            DatabaseHelper.COL_MSG_ID + " = ?",
            new String[]{messageId}
        );
        
        db.close();
    }
    
    /**
     * Delete all messages for a chat (hard delete - removes from database)
     * Used when a chat/group is deleted
     */
    public void deleteAllMessagesForChat(String chatId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            int deleted = db.delete(
                DatabaseHelper.TABLE_MESSAGES,
                DatabaseHelper.COL_MSG_CHAT_ID + " = ?",
                new String[]{chatId}
            );
            Log.d(TAG, "Deleted " + deleted + " messages for chat: " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting messages for chat: " + chatId, e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Convert Cursor to Message object
     */
    private Message cursorToMessage(Cursor cursor) {
        try {
            Message message = new Message();
            
            int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_ID);
            int chatIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_CHAT_ID);
            int senderIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_SENDER_ID);
            int senderNameIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_SENDER_NAME);
            int senderAvatarIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_SENDER_AVATAR);
            int contentIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_CONTENT);
            int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_TYPE);
            int chatTypeIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_CHAT_TYPE);
            int timestampIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_TIMESTAMP);
            int isReadIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_IS_READ);
            int isDeletedIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_IS_DELETED);
            int attachmentsIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_ATTACHMENTS);
            int localImageUriIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_LOCAL_IMAGE_URI);
            int replyToIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_REPLY_TO_ID);
            int replyToContentIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_REPLY_TO_CONTENT);
            int replyToSenderIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_REPLY_TO_SENDER);
            int editedIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_EDITED);
            int editedAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_EDITED_AT);
            int reactionsIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_REACTIONS);
            int clientNonceIndex = cursor.getColumnIndex(DatabaseHelper.COL_MSG_CLIENT_NONCE);
            
            if (idIndex >= 0) message.setId(cursor.getString(idIndex));
            if (chatIdIndex >= 0) message.setChatId(cursor.getString(chatIdIndex));
            if (senderIdIndex >= 0) message.setSenderId(cursor.getString(senderIdIndex));
            if (senderNameIndex >= 0) message.setSenderDisplayName(cursor.getString(senderNameIndex));
            if (senderAvatarIndex >= 0) message.setSenderAvatarUrl(cursor.getString(senderAvatarIndex));
            if (contentIndex >= 0) message.setContent(cursor.getString(contentIndex));
            if (typeIndex >= 0) message.setType(cursor.getString(typeIndex));
            if (chatTypeIndex >= 0) message.setChatType(cursor.getString(chatTypeIndex));
            if (timestampIndex >= 0) message.setTimestamp(cursor.getLong(timestampIndex));
            if (isReadIndex >= 0) {
                int isRead = cursor.getInt(isReadIndex);
                message.setRead(isRead == 1);
            }
            if (isDeletedIndex >= 0) {
                int isDeleted = cursor.getInt(isDeletedIndex);
                message.setDeleted(isDeleted == 1);
            }
            if (attachmentsIndex >= 0) {
                String attachments = cursor.getString(attachmentsIndex);
                if (attachments != null) {
                    try {
                        java.lang.reflect.Field attField = Message.class.getDeclaredField("attachments");
                        attField.setAccessible(true);
                        attField.set(message, attachments);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not set attachments field");
                    }
                }
            }
            if (localImageUriIndex >= 0) message.setLocalImageUri(cursor.getString(localImageUriIndex));
            if (replyToIdIndex >= 0) message.setReplyToMessageId(cursor.getString(replyToIdIndex));
            if (replyToContentIndex >= 0) message.setReplyToContent(cursor.getString(replyToContentIndex));
            if (replyToSenderIndex >= 0) message.setReplyToSenderName(cursor.getString(replyToSenderIndex));
            if (editedIndex >= 0) {
                int edited = cursor.getInt(editedIndex);
                message.setEdited(edited == 1);
            }
            if (editedAtIndex >= 0) message.setEditedAt(cursor.getLong(editedAtIndex));
            if (reactionsIndex >= 0) {
                String reactions = cursor.getString(reactionsIndex);
                try {
                    java.lang.reflect.Field reactField = Message.class.getDeclaredField("reactionsRaw");
                    reactField.setAccessible(true);
                    reactField.set(message, reactions);
                } catch (Exception e) {
                    Log.w(TAG, "Could not set reactions field");
                }
            }
            if (clientNonceIndex >= 0) message.setClientNonce(cursor.getString(clientNonceIndex));
            
            return message;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to message: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get count of pending messages
     */
    public int getPendingMessageCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_MESSAGES + 
            " WHERE " + DatabaseHelper.COL_MSG_SYNC_STATUS + " = ?",
            new String[]{SYNC_STATUS_PENDING}
        );
        
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        db.close();
        return count;
    }
}

