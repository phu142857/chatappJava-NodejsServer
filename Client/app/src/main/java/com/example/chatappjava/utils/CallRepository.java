package com.example.chatappjava.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.example.chatappjava.models.Call;
import com.example.chatappjava.models.CallParticipant;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository class for managing calls in SQLite database
 * Handles offline call storage and retrieval
 */
public class CallRepository {
    private static final String TAG = "CallRepository";
    
    private final DatabaseHelper dbHelper;
    private final Context context;
    
    public CallRepository(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }
    
    /**
     * Save a call to local database
     */
    public void saveCall(Call call) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            // Get private fields using reflection
            long startedAt = 0;
            long endedAt = 0;
            String chatName = "";
            String chatType = "";
            String callerId = "";
            String callerName = "";
            
            try {
                java.lang.reflect.Field startedAtField = Call.class.getDeclaredField("startedAt");
                startedAtField.setAccessible(true);
                startedAt = startedAtField.getLong(call);
                
                java.lang.reflect.Field endedAtField = Call.class.getDeclaredField("endedAt");
                endedAtField.setAccessible(true);
                endedAt = endedAtField.getLong(call);
                
                java.lang.reflect.Field chatNameField = Call.class.getDeclaredField("chatName");
                chatNameField.setAccessible(true);
                chatName = (String) chatNameField.get(call);
                
                java.lang.reflect.Field chatTypeField = Call.class.getDeclaredField("chatType");
                chatTypeField.setAccessible(true);
                chatType = (String) chatTypeField.get(call);
                
                java.lang.reflect.Field callerIdField = Call.class.getDeclaredField("callerId");
                callerIdField.setAccessible(true);
                callerId = (String) callerIdField.get(call);
                
                java.lang.reflect.Field callerNameField = Call.class.getDeclaredField("callerName");
                callerNameField.setAccessible(true);
                callerName = (String) callerNameField.get(call);
            } catch (Exception e) {
                Log.w(TAG, "Error accessing private fields: " + e.getMessage());
            }
            
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_CALL_ID, call.getCallId());
            values.put(DatabaseHelper.COL_CALL_TYPE, call.getType());
            values.put(DatabaseHelper.COL_CALL_CHAT_ID, call.getChatId());
            values.put(DatabaseHelper.COL_CALL_CHAT_NAME, chatName != null ? chatName : call.getDisplayName(""));
            values.put(DatabaseHelper.COL_CALL_CHAT_TYPE, chatType != null && !chatType.isEmpty() ? chatType : (call.isGroupCall() ? "group" : "private"));
            values.put(DatabaseHelper.COL_CALL_STATUS, call.getStatus());
            values.put(DatabaseHelper.COL_CALL_STARTED_AT, startedAt);
            values.put(DatabaseHelper.COL_CALL_ENDED_AT, endedAt);
            values.put(DatabaseHelper.COL_CALL_DURATION, call.getDuration());
            values.put(DatabaseHelper.COL_CALL_IS_GROUP_CALL, call.isGroupCall() ? 1 : 0);
            values.put(DatabaseHelper.COL_CALL_CALLER_ID, callerId);
            values.put(DatabaseHelper.COL_CALL_CALLER_NAME, callerName);
            values.put(DatabaseHelper.COL_CALL_CALLER_AVATAR, call.getCallerAvatar());
            
            // Serialize participants to JSON
            JSONArray participantsArray = new JSONArray();
            // Get participants using reflection since it's private
            try {
                java.lang.reflect.Field participantsField = Call.class.getDeclaredField("participants");
                participantsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<CallParticipant> participants = (List<CallParticipant>) participantsField.get(call);
                if (participants != null) {
                    for (CallParticipant participant : participants) {
                        participantsArray.put(participant.toJson());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error accessing participants field: " + e.getMessage());
            }
            values.put(DatabaseHelper.COL_CALL_PARTICIPANTS, participantsArray.toString());

            // Use INSERT OR REPLACE to handle both insert and update
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_CALLS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            Log.d(TAG, "Call saved: " + call.getCallId());
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving call: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Save multiple calls to local database
     */
    public void saveCalls(List<Call> calls) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            db.beginTransaction();
            
            for (Call call : calls) {
                // Get private fields using reflection
                long startedAt = 0;
                long endedAt = 0;
                String chatName = "";
                String chatType = "";
                String callerId = "";
                String callerName = "";
                
                try {
                    java.lang.reflect.Field startedAtField = Call.class.getDeclaredField("startedAt");
                    startedAtField.setAccessible(true);
                    startedAt = startedAtField.getLong(call);
                    
                    java.lang.reflect.Field endedAtField = Call.class.getDeclaredField("endedAt");
                    endedAtField.setAccessible(true);
                    endedAt = endedAtField.getLong(call);
                    
                    java.lang.reflect.Field chatNameField = Call.class.getDeclaredField("chatName");
                    chatNameField.setAccessible(true);
                    chatName = (String) chatNameField.get(call);
                    
                    java.lang.reflect.Field chatTypeField = Call.class.getDeclaredField("chatType");
                    chatTypeField.setAccessible(true);
                    chatType = (String) chatTypeField.get(call);
                    
                    java.lang.reflect.Field callerIdField = Call.class.getDeclaredField("callerId");
                    callerIdField.setAccessible(true);
                    callerId = (String) callerIdField.get(call);
                    
                    java.lang.reflect.Field callerNameField = Call.class.getDeclaredField("callerName");
                    callerNameField.setAccessible(true);
                    callerName = (String) callerNameField.get(call);
                } catch (Exception e) {
                    Log.w(TAG, "Error accessing private fields: " + e.getMessage());
                }
                
                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COL_CALL_ID, call.getCallId());
                values.put(DatabaseHelper.COL_CALL_TYPE, call.getType());
                values.put(DatabaseHelper.COL_CALL_CHAT_ID, call.getChatId());
                values.put(DatabaseHelper.COL_CALL_CHAT_NAME, chatName != null ? chatName : call.getDisplayName(""));
                values.put(DatabaseHelper.COL_CALL_CHAT_TYPE, chatType != null && !chatType.isEmpty() ? chatType : (call.isGroupCall() ? "group" : "private"));
                values.put(DatabaseHelper.COL_CALL_STATUS, call.getStatus());
                values.put(DatabaseHelper.COL_CALL_STARTED_AT, startedAt);
                values.put(DatabaseHelper.COL_CALL_ENDED_AT, endedAt);
                values.put(DatabaseHelper.COL_CALL_DURATION, call.getDuration());
                values.put(DatabaseHelper.COL_CALL_IS_GROUP_CALL, call.isGroupCall() ? 1 : 0);
                values.put(DatabaseHelper.COL_CALL_CALLER_ID, callerId);
                values.put(DatabaseHelper.COL_CALL_CALLER_NAME, callerName);
                values.put(DatabaseHelper.COL_CALL_CALLER_AVATAR, call.getCallerAvatar());
                
                // Serialize participants to JSON
                JSONArray participantsArray = new JSONArray();
                // Get participants using reflection since it's private
                try {
                    java.lang.reflect.Field participantsField = Call.class.getDeclaredField("participants");
                    participantsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<CallParticipant> participants = (List<CallParticipant>) participantsField.get(call);
                    if (participants != null) {
                        for (CallParticipant participant : participants) {
                            participantsArray.put(participant.toJson());
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error accessing participants field: " + e.getMessage());
                }
                values.put(DatabaseHelper.COL_CALL_PARTICIPANTS, participantsArray.toString());

                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_CALLS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                );
            }
            
            db.setTransactionSuccessful();
            Log.d(TAG, "Saved " + calls.size() + " calls");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving calls: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
            db.close();
        }
    }
    
    /**
     * Get all calls, ordered by started_at descending (newest first)
     */
    public List<Call> getAllCalls(int limit) {
        List<Call> calls = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String orderBy = DatabaseHelper.COL_CALL_STARTED_AT + " DESC";
        String limitStr = limit > 0 ? String.valueOf(limit) : null;
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_CALLS,
            null,
            null,
            null,
            null,
            null,
            orderBy,
            limitStr
        );
        
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Call call = cursorToCall(cursor);
                    if (call != null) {
                        calls.add(call);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        Log.d(TAG, "Retrieved " + calls.size() + " calls");
        return calls;
    }
    
    /**
     * Delete all calls from database
     */
    public void deleteAllCalls() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            int deleted = db.delete(DatabaseHelper.TABLE_CALLS, null, null);
            Log.d(TAG, "Deleted " + deleted + " calls");
        } catch (Exception e) {
            Log.e(TAG, "Error deleting calls: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Convert Cursor to Call object
     */
    private Call cursorToCall(Cursor cursor) {
        try {
            JSONObject callJson = new JSONObject();
            
            int callIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_ID);
            int typeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_TYPE);
            int chatIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_CHAT_ID);
            int chatNameIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_CHAT_NAME);
            int chatTypeIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_CHAT_TYPE);
            int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_STATUS);
            int startedAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_STARTED_AT);
            int endedAtIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_ENDED_AT);
            int durationIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_DURATION);
            int isGroupCallIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_IS_GROUP_CALL);
            int participantsIndex = cursor.getColumnIndex(DatabaseHelper.COL_CALL_PARTICIPANTS);
            
            if (callIdIndex >= 0 && !cursor.isNull(callIdIndex)) {
                callJson.put("callId", cursor.getString(callIdIndex));
            }
            if (typeIndex >= 0 && !cursor.isNull(typeIndex)) {
                callJson.put("type", cursor.getString(typeIndex));
            }
            if (chatIdIndex >= 0 && !cursor.isNull(chatIdIndex)) {
                callJson.put("chatId", cursor.getString(chatIdIndex));
            }
            if (statusIndex >= 0 && !cursor.isNull(statusIndex)) {
                callJson.put("status", cursor.getString(statusIndex));
            }
            if (startedAtIndex >= 0 && !cursor.isNull(startedAtIndex)) {
                callJson.put("startedAt", cursor.getLong(startedAtIndex));
            }
            if (endedAtIndex >= 0 && !cursor.isNull(endedAtIndex)) {
                long endedAt = cursor.getLong(endedAtIndex);
                if (endedAt > 0) {
                    callJson.put("endedAt", endedAt);
                }
            }
            if (durationIndex >= 0 && !cursor.isNull(durationIndex)) {
                callJson.put("duration", cursor.getInt(durationIndex));
            }
            if (isGroupCallIndex >= 0 && !cursor.isNull(isGroupCallIndex)) {
                callJson.put("isGroupCall", cursor.getInt(isGroupCallIndex) == 1);
            }
            
            // Add chat info if available
            if (chatNameIndex >= 0 && !cursor.isNull(chatNameIndex) || 
                chatTypeIndex >= 0 && !cursor.isNull(chatTypeIndex)) {
                JSONObject chatObj = new JSONObject();
                if (chatNameIndex >= 0 && !cursor.isNull(chatNameIndex)) {
                    chatObj.put("name", cursor.getString(chatNameIndex));
                }
                if (chatTypeIndex >= 0 && !cursor.isNull(chatTypeIndex)) {
                    chatObj.put("type", cursor.getString(chatTypeIndex));
                }
                callJson.put("chatId", chatObj);
            }
            
            // Parse participants
            if (participantsIndex >= 0 && !cursor.isNull(participantsIndex)) {
                String participantsJson = cursor.getString(participantsIndex);
                if (participantsJson != null && !participantsJson.isEmpty()) {
                    try {
                        JSONArray participantsArray = new JSONArray(participantsJson);
                        callJson.put("participants", participantsArray);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing participants JSON: " + e.getMessage());
                        callJson.put("participants", new JSONArray());
                    }
                } else {
                    callJson.put("participants", new JSONArray());
                }
            } else {
                callJson.put("participants", new JSONArray());
            }
            
            return Call.fromJson(callJson);
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to call: " + e.getMessage(), e);
            return null;
        }
    }
}

