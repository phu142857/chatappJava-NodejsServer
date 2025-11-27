package com.example.chatappjava.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;

import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository class for managing conversations (chats) in SQLite database
 * Handles offline chat storage and retrieval
 */
public class ConversationRepository {
    private static final String TAG = "ConversationRepository";
    
    private final DatabaseHelper dbHelper;
    private final Context context;
    
    public ConversationRepository(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }
    
    /**
     * Save or update a conversation/chat
     */
    public void saveConversation(Chat chat) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_CONV_ID, chat.getId());
            values.put(DatabaseHelper.COL_CONV_TYPE, chat.getType());
            values.put(DatabaseHelper.COL_CONV_NAME, chat.getName());
            values.put(DatabaseHelper.COL_CONV_DESCRIPTION, chat.getDescription());
            
            // For private chats, save other participant's avatar (like calls save caller avatar)
            // For group chats, save group avatar
            String avatarToSave = chat.getAvatar();
            if (chat.isPrivateChat() && chat.getOtherParticipant() != null && 
                chat.getOtherParticipant().getAvatar() != null && 
                !chat.getOtherParticipant().getAvatar().isEmpty()) {
                avatarToSave = chat.getOtherParticipant().getAvatar();
                Log.d(TAG, "Saving other participant avatar for private chat: " + avatarToSave);
            }
            values.put(DatabaseHelper.COL_CONV_AVATAR, avatarToSave);
            values.put(DatabaseHelper.COL_CONV_LAST_MESSAGE, chat.getLastMessage());
            values.put(DatabaseHelper.COL_CONV_LAST_MESSAGE_TIME, chat.getLastMessageTime());
            values.put(DatabaseHelper.COL_CONV_UNREAD_COUNT, chat.getUnreadCount());
            values.put(DatabaseHelper.COL_CONV_IS_ACTIVE, chat.isActive() ? 1 : 0);
            values.put(DatabaseHelper.COL_CONV_CREATOR_ID, chat.getCreatorId());
            values.put(DatabaseHelper.COL_CONV_CREATED_AT, chat.getCreatedAt());
            values.put(DatabaseHelper.COL_CONV_UPDATED_AT, chat.getUpdatedAt());
            values.put(DatabaseHelper.COL_CONV_GROUP_ID, chat.getGroupId());
            values.put(DatabaseHelper.COL_CONV_IS_PUBLIC, chat.isPublicGroup() ? 1 : 0);
            values.put(DatabaseHelper.COL_CONV_VISIBILITY, chat.getVisibility());
            
            // Convert participants list to JSON array
            List<String> participants = chat.getParticipantIds();
            if (participants != null && !participants.isEmpty()) {
                JSONArray participantsArray = new JSONArray();
                for (String participantId : participants) {
                    participantsArray.put(participantId);
                }
                values.put(DatabaseHelper.COL_CONV_PARTICIPANTS, participantsArray.toString());
            } else {
                values.putNull(DatabaseHelper.COL_CONV_PARTICIPANTS);
            }
            
            // Use INSERT OR REPLACE to handle both insert and update
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_CONVERSATIONS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            Log.d(TAG, "Conversation saved: " + chat.getId());
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving conversation: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Save multiple conversations
     */
    public void saveConversations(List<Chat> chats) {
        for (Chat chat : chats) {
            saveConversation(chat);
        }
    }
    
    /**
     * Get all conversations, ordered by last message time (most recent first)
     */
    public List<Chat> getAllConversations() {
        List<Chat> conversations = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String orderBy = DatabaseHelper.COL_CONV_LAST_MESSAGE_TIME + " DESC";
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_CONVERSATIONS,
            null,
            DatabaseHelper.COL_CONV_IS_ACTIVE + " = 1",
            null,
            null,
            null,
            orderBy,
            null
        );
        
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Chat chat = cursorToChat(cursor);
                    if (chat != null) {
                        conversations.add(chat);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        Log.d(TAG, "Retrieved " + conversations.size() + " conversations from database");
        return conversations;
    }
    
    /**
     * Get a specific conversation by ID
     */
    public Chat getConversationById(String chatId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Chat chat = null;
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_CONVERSATIONS,
            null,
            DatabaseHelper.COL_CONV_ID + " = ?",
            new String[]{chatId},
            null,
            null,
            null,
            null
        );
        
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    chat = cursorToChat(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        return chat;
    }
    
    /**
     * Delete a conversation (soft delete - set is_active = 0)
     */
    public void deleteConversation(String chatId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_CONV_IS_ACTIVE, 0);
        
        db.update(
            DatabaseHelper.TABLE_CONVERSATIONS,
            values,
            DatabaseHelper.COL_CONV_ID + " = ?",
            new String[]{chatId}
        );
        
        db.close();
    }
    
    /**
     * Update last message info for a conversation
     */
    public void updateLastMessage(String chatId, String lastMessage, long lastMessageTime) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_CONV_LAST_MESSAGE, lastMessage);
        values.put(DatabaseHelper.COL_CONV_LAST_MESSAGE_TIME, lastMessageTime);
        values.put(DatabaseHelper.COL_CONV_UPDATED_AT, System.currentTimeMillis());
        
        db.update(
            DatabaseHelper.TABLE_CONVERSATIONS,
            values,
            DatabaseHelper.COL_CONV_ID + " = ?",
            new String[]{chatId}
        );
        
        db.close();
    }
    
    /**
     * Update unread count for a conversation
     */
    public void updateUnreadCount(String chatId, int unreadCount) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_CONV_UNREAD_COUNT, unreadCount);
        values.put(DatabaseHelper.COL_CONV_UPDATED_AT, System.currentTimeMillis());
        
        db.update(
            DatabaseHelper.TABLE_CONVERSATIONS,
            values,
            DatabaseHelper.COL_CONV_ID + " = ?",
            new String[]{chatId}
        );
        
        db.close();
    }
    
    /**
     * Convert Cursor to Chat object
     */
    private Chat cursorToChat(Cursor cursor) {
        try {
            Chat chat = new Chat();
            
            int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_ID);
            int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_TYPE);
            int nameIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_NAME);
            int descriptionIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_DESCRIPTION);
            int avatarIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_AVATAR);
            int lastMessageIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_LAST_MESSAGE);
            int lastMessageTimeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_LAST_MESSAGE_TIME);
            int unreadCountIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_UNREAD_COUNT);
            int isActiveIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_IS_ACTIVE);
            int creatorIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_CREATOR_ID);
            int createdAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_CREATED_AT);
            int updatedAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_UPDATED_AT);
            int participantsIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_PARTICIPANTS);
            int groupIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_GROUP_ID);
            int isPublicIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_IS_PUBLIC);
            int visibilityIndex = cursor.getColumnIndex(DatabaseHelper.COL_CONV_VISIBILITY);
            
            if (idIndex >= 0) chat.setId(cursor.getString(idIndex));
            if (typeIndex >= 0) chat.setType(cursor.getString(typeIndex));
            if (nameIndex >= 0) chat.setName(cursor.getString(nameIndex));
            if (descriptionIndex >= 0) chat.setDescription(cursor.getString(descriptionIndex));
            
            // Set avatar - for private chats, this is the other participant's avatar
            if (avatarIndex >= 0) {
                String avatar = cursor.getString(avatarIndex);
                chat.setAvatar(avatar);
                
                // For private chats, also set avatar to otherParticipant if it exists
                // This ensures avatar is available when loading from database
                if (chat.isPrivateChat() && avatar != null && !avatar.isEmpty()) {
                    try {
                        java.lang.reflect.Field otherParticipantField = Chat.class.getDeclaredField("otherParticipant");
                        otherParticipantField.setAccessible(true);
                        User otherParticipant = (User) otherParticipantField.get(chat);
                        
                        // If otherParticipant doesn't exist, create a minimal one with avatar
                        if (otherParticipant == null) {
                            otherParticipant = new User();
                            otherParticipant.setAvatar(avatar);
                            otherParticipantField.set(chat, otherParticipant);
                            Log.d(TAG, "Created otherParticipant with avatar from database: " + avatar);
                        } else {
                            // If otherParticipant exists but has no avatar, set it
                            if (otherParticipant.getAvatar() == null || otherParticipant.getAvatar().isEmpty()) {
                                otherParticipant.setAvatar(avatar);
                                Log.d(TAG, "Set avatar to existing otherParticipant: " + avatar);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not set otherParticipant avatar: " + e.getMessage());
                    }
                }
            }
            
            if (lastMessageIndex >= 0) {
                try {
                    java.lang.reflect.Field lastMsgField = Chat.class.getDeclaredField("lastMessage");
                    lastMsgField.setAccessible(true);
                    lastMsgField.set(chat, cursor.getString(lastMessageIndex));
                } catch (Exception e) {
                    Log.w(TAG, "Could not set lastMessage");
                }
            }
            
            if (lastMessageTimeIndex >= 0) chat.setLastMessageTime(cursor.getLong(lastMessageTimeIndex));
            
            if (unreadCountIndex >= 0) {
                try {
                    java.lang.reflect.Field unreadField = Chat.class.getDeclaredField("unreadCount");
                    unreadField.setAccessible(true);
                    unreadField.setInt(chat, cursor.getInt(unreadCountIndex));
                } catch (Exception e) {
                    Log.w(TAG, "Could not set unreadCount");
                }
            }
            
            if (isActiveIndex >= 0) chat.setActive(cursor.getInt(isActiveIndex) == 1);
            if (creatorIdIndex >= 0) chat.setCreatorId(cursor.getString(creatorIdIndex));
            
            if (createdAtIndex >= 0) {
                try {
                    java.lang.reflect.Field createdAtField = Chat.class.getDeclaredField("createdAt");
                    createdAtField.setAccessible(true);
                    createdAtField.setLong(chat, cursor.getLong(createdAtIndex));
                } catch (Exception e) {
                    Log.w(TAG, "Could not set createdAt");
                }
            }
            
            if (updatedAtIndex >= 0) chat.setUpdatedAt(cursor.getLong(updatedAtIndex));
            
            if (groupIdIndex >= 0) {
                try {
                    java.lang.reflect.Field groupIdField = Chat.class.getDeclaredField("groupId");
                    groupIdField.setAccessible(true);
                    groupIdField.set(chat, cursor.getString(groupIdIndex));
                } catch (Exception e) {
                    Log.w(TAG, "Could not set groupId");
                }
            }
            
            if (isPublicIndex >= 0) {
                try {
                    chat.setIsPublic(cursor.getInt(isPublicIndex) == 1);
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field isPublicField = Chat.class.getDeclaredField("isPublic");
                        isPublicField.setAccessible(true);
                        isPublicField.setBoolean(chat, cursor.getInt(isPublicIndex) == 1);
                    } catch (Exception ex) {
                        Log.w(TAG, "Could not set isPublic");
                    }
                }
            }
            
            if (visibilityIndex >= 0) {
                try {
                    java.lang.reflect.Field visibilityField = Chat.class.getDeclaredField("visibility");
                    visibilityField.setAccessible(true);
                    visibilityField.set(chat, cursor.getString(visibilityIndex));
                } catch (Exception e) {
                    Log.w(TAG, "Could not set visibility");
                }
            }
            
            // Parse participants JSON array
            if (participantsIndex >= 0) {
                String participantsJson = cursor.getString(participantsIndex);
                if (participantsJson != null && !participantsJson.isEmpty()) {
                    try {
                        JSONArray participantsArray = new JSONArray(participantsJson);
                        List<String> participantIds = chat.getParticipantIds();
                        for (int i = 0; i < participantsArray.length(); i++) {
                            participantIds.add(participantsArray.getString(i));
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Could not parse participants JSON");
                    }
                }
            }
            
            return chat;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to chat: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Clear all conversations (for logout)
     */
    public void clearAllConversations() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_CONVERSATIONS, null, null);
        db.close();
        Log.d(TAG, "All conversations cleared");
    }
}








