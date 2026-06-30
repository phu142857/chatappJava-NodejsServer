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
     * Save or update a call
     */
    public void saveCall(Call call) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        try {
            values.put(DatabaseHelper.COL_CALL_ID, call.getCallId());
            values.put(DatabaseHelper.COL_CALL_TYPE, call.getType());
            values.put(DatabaseHelper.COL_CALL_CHAT_ID, call.getChatId());
            values.put(DatabaseHelper.COL_CALL_CHAT_NAME, call.getDisplayName(""));
            values.put(DatabaseHelper.COL_CALL_CHAT_TYPE, call.isGroupCall() ? "group" : "private");
            values.put(DatabaseHelper.COL_CALL_STATUS, call.getStatus());
            values.put(DatabaseHelper.COL_CALL_STARTED_AT, call.getStartedAt());
            values.put(DatabaseHelper.COL_CALL_ENDED_AT, call.getEndedAt());
            values.put(DatabaseHelper.COL_CALL_DURATION, call.getDuration());
            values.put(DatabaseHelper.COL_CALL_IS_GROUP_CALL, call.isGroupCall() ? 1 : 0);
            values.put(DatabaseHelper.COL_CALL_CALLER_ID, call.getCallerId());
            values.put(DatabaseHelper.COL_CALL_CALLER_NAME, call.getCallerName());
            values.put(DatabaseHelper.COL_CALL_CALLER_AVATAR, call.getCallerAvatar());
            
            // Serialize participants to JSON
            JSONArray participantsArray = new JSONArray();
            for (CallParticipant participant : call.getParticipants()) {
                participantsArray.put(participant.toJson());
            }
            values.put(DatabaseHelper.COL_CALL_PARTICIPANTS, participantsArray.toString());
            
            // Use INSERT OR REPLACE to handle updates
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_CALLS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            Log.d(TAG, "Saved call: " + call.getCallId());
            
        } catch (JSONException e) {
            Log.e(TAG, "Error saving call: " + call.getCallId(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Save multiple calls
     */
    public void saveCalls(List<Call> calls) {
        if (calls == null || calls.isEmpty()) {
            return;
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        
        try {
            for (Call call : calls) {
                ContentValues values = new ContentValues();
                
                values.put(DatabaseHelper.COL_CALL_ID, call.getCallId());
                values.put(DatabaseHelper.COL_CALL_TYPE, call.getType());
                values.put(DatabaseHelper.COL_CALL_CHAT_ID, call.getChatId());
                values.put(DatabaseHelper.COL_CALL_CHAT_NAME, call.getDisplayName(""));
                values.put(DatabaseHelper.COL_CALL_CHAT_TYPE, call.isGroupCall() ? "group" : "private");
                values.put(DatabaseHelper.COL_CALL_STATUS, call.getStatus());
                values.put(DatabaseHelper.COL_CALL_STARTED_AT, call.getStartedAt());
                values.put(DatabaseHelper.COL_CALL_ENDED_AT, call.getEndedAt());
                values.put(DatabaseHelper.COL_CALL_DURATION, call.getDuration());
                values.put(DatabaseHelper.COL_CALL_IS_GROUP_CALL, call.isGroupCall() ? 1 : 0);
                values.put(DatabaseHelper.COL_CALL_CALLER_ID, call.getCallerId());
                values.put(DatabaseHelper.COL_CALL_CALLER_NAME, call.getCallerName());
                values.put(DatabaseHelper.COL_CALL_CALLER_AVATAR, call.getCallerAvatar());
                
                // Serialize participants to JSON
                JSONArray participantsArray = new JSONArray();
                for (CallParticipant participant : call.getParticipants()) {
                    participantsArray.put(participant.toJson());
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
            
        } catch (JSONException e) {
            Log.e(TAG, "Error saving calls", e);
        } finally {
            db.endTransaction();
            db.close();
        }
    }
    
    /**
     * Get all calls, ordered by started_at descending
     * @param limit Maximum number of calls to return (0 = no limit)
     */
    public List<Call> getAllCalls(int limit) {
        List<Call> calls = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String query = "SELECT * FROM " + DatabaseHelper.TABLE_CALLS +
                      " ORDER BY " + DatabaseHelper.COL_CALL_STARTED_AT + " DESC";
        
        if (limit > 0) {
            query += " LIMIT " + limit;
        }
        
        Cursor cursor = db.rawQuery(query, null);
        
        try {
            while (cursor.moveToNext()) {
                Call call = cursorToCall(cursor);
                if (call != null) {
                    calls.add(call);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading calls from database", e);
        } finally {
            cursor.close();
            db.close();
        }
        
        return calls;
    }
    
    /**
     * Get a call by ID
     */
    public Call getCallById(String callId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Call call = null;
        
        String selection = DatabaseHelper.COL_CALL_ID + " = ?";
        String[] selectionArgs = {callId};
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_CALLS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        );
        
        try {
            if (cursor.moveToFirst()) {
                call = cursorToCall(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading call from database", e);
        } finally {
            cursor.close();
            db.close();
        }
        
        return call;
    }
    
    /**
     * Delete a call
     */
    public void deleteCall(String callId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        String whereClause = DatabaseHelper.COL_CALL_ID + " = ?";
        String[] whereArgs = {callId};
        
        db.delete(DatabaseHelper.TABLE_CALLS, whereClause, whereArgs);
        db.close();
        
        Log.d(TAG, "Deleted call: " + callId);
    }
    
    /**
     * Delete all calls
     */
    public void deleteAllCalls() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_CALLS, null, null);
        db.close();
        
        Log.d(TAG, "Deleted all calls");
    }
    
    /**
     * Convert cursor to Call object
     */
    private Call cursorToCall(Cursor cursor) {
        try {
            Call call = new Call();
            
            call.setCallId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_ID)));
            call.setType(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_TYPE)));
            call.setChatId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_CHAT_ID)));
            call.setChatName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_CHAT_NAME)));
            call.setChatType(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_CHAT_TYPE)));
            call.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_STATUS)));
            call.setStartedAt(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_STARTED_AT)));
            call.setEndedAt(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_ENDED_AT)));
            call.setDuration(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_DURATION)));
            
            int isGroupCallInt = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_IS_GROUP_CALL));
            call.setIsGroupCall(isGroupCallInt == 1);
            
            call.setCallerId(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_CALLER_ID)));
            call.setCallerName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_CALLER_NAME)));
            call.setCallerAvatar(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_CALLER_AVATAR)));
            
            // Parse participants from JSON
            String participantsJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CALL_PARTICIPANTS));
            if (participantsJson != null && !participantsJson.isEmpty()) {
                JSONArray participantsArray = new JSONArray(participantsJson);
                for (int i = 0; i < participantsArray.length(); i++) {
                    JSONObject participantJson = participantsArray.getJSONObject(i);
                    CallParticipant participant = CallParticipant.fromJson(participantJson);
                    call.getParticipants().add(participant);
                }
            }
            
            return call;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to Call", e);
            return null;
        }
    }
    
}
