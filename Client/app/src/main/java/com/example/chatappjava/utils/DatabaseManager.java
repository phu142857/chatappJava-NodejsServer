package com.example.chatappjava.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class DatabaseManager {
    private static final String TAG = "DatabaseManager";
    
    // Keys for app settings
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_INFO = "userInfo";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_AVATAR = "userAvatar";
    private static final String KEY_OVERRIDE_SERVER_IP = "overrideServerIp";
    private static final String KEY_OVERRIDE_SERVER_PORT = "overrideServerPort";
    private static final String KEY_OVERRIDE_USE_HTTPS = "overrideUseHttps";
    private static final String KEY_OVERRIDE_USE_WSS = "overrideUseWss";
    
    private final DatabaseHelper dbHelper;
    private final Context context;

    public DatabaseManager(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }

    /**
     * Save a string value
     */
    private void putString(String key, String value) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            if (db == null) {
                return;
            }
            
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COLUMN_KEY, key);
            values.put(DatabaseHelper.COLUMN_VALUE, value);
            
            // Use INSERT OR REPLACE to handle both insert and update
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_APP_SETTINGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            db.close();
        } catch (Exception e) {
            android.util.Log.w("DatabaseManager", "Error putting string for key " + key + ": " + e.getMessage());
        }
    }

    /**
     * Get a string value
     */
    private String getString(String key, String defaultValue) {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            if (db == null) {
                return defaultValue;
            }
            
            String value = defaultValue;
            
            Cursor cursor = db.query(
                DatabaseHelper.TABLE_APP_SETTINGS,
                new String[]{DatabaseHelper.COLUMN_VALUE},
                DatabaseHelper.COLUMN_KEY + " = ?",
                new String[]{key},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_VALUE);
                if (columnIndex >= 0) {
                    value = cursor.getString(columnIndex);
                }
                cursor.close();
            }
            db.close();
            return value;
        } catch (Exception e) {
            android.util.Log.w("DatabaseManager", "Error getting string for key " + key + ": " + e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Save a boolean value
     */
    private void putBoolean(String key, boolean value) {
        putString(key, String.valueOf(value));
    }

    /**
     * Get a boolean value
     */
    private boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Save an integer value
     */
    private void putInt(String key, int value) {
        putString(key, String.valueOf(value));
    }

    /**
     * Get an integer value
     */
    private int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Check if a key exists
     */
    private boolean contains(String key) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_APP_SETTINGS,
            new String[]{DatabaseHelper.COLUMN_KEY},
            DatabaseHelper.COLUMN_KEY + " = ?",
            new String[]{key},
            null, null, null
        );
        boolean exists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return exists;
    }

    /**
     * Remove a key
     */
    private void remove(String key) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
            DatabaseHelper.TABLE_APP_SETTINGS,
            DatabaseHelper.COLUMN_KEY + " = ?",
            new String[]{key}
        );
        db.close();
    }

    /**
     * Clear all data
     */
    public void clearAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_APP_SETTINGS, null, null);
        db.close();
    }

    /**
     * Save user login information
     */
    public void saveLoginInfo(String token, String userInfo) {
        try {
            JSONObject userJson = new JSONObject(userInfo);
            
            putBoolean(KEY_IS_LOGGED_IN, true);
            putString(KEY_TOKEN, token);
            putString(KEY_USER_INFO, userInfo);
            putString(KEY_USER_ID, userJson.optString("_id", ""));
            putString(KEY_USER_NAME, userJson.optString("username", ""));
            putString(KEY_USER_EMAIL, userJson.optString("email", ""));
            putString(KEY_USER_AVATAR, userJson.optString("avatar", ""));
            
            Log.d(TAG, "Login info saved successfully");
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing userInfo JSON: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error saving login info: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Get login token
     */
    public String getToken() {
        String token = getString(KEY_TOKEN, null);
        return "null".equals(token) ? null : token;
    }

    /**
     * Get user ID
     */
    public String getUserId() {
        return getString(KEY_USER_ID, "");
    }

    /**
     * Get username
     */
    public String getUserName() {
        return getString(KEY_USER_NAME, "");
    }

    /**
     * Get user email
     */
    public String getUserEmail() {
        return getString(KEY_USER_EMAIL, "");
    }

    /**
     * Get user avatar
     */
    public String getUserAvatar() {
        return getString(KEY_USER_AVATAR, "");
    }

    /**
     * Clear all login information (logout)
     */
    public void clearLoginInfo() {
        remove(KEY_IS_LOGGED_IN);
        remove(KEY_TOKEN);
        remove(KEY_USER_INFO);
        remove(KEY_USER_ID);
        remove(KEY_USER_NAME);
        remove(KEY_USER_EMAIL);
        remove(KEY_USER_AVATAR);
        Log.d(TAG, "Login info cleared");
    }

    // ===== Server overrides =====
    public void setOverrideServerIp(String ip) {
        putString(KEY_OVERRIDE_SERVER_IP, ip);
    }

    public String getOverrideServerIp() {
        String value = getString(KEY_OVERRIDE_SERVER_IP, null);
        return "null".equals(value) ? null : value;
    }

    public void setOverrideServerPort(int port) {
        putInt(KEY_OVERRIDE_SERVER_PORT, port);
    }

    public int getOverrideServerPort() {
        return getInt(KEY_OVERRIDE_SERVER_PORT, -1);
    }

    public void setOverrideUseHttps(boolean useHttps) {
        putBoolean(KEY_OVERRIDE_USE_HTTPS, useHttps);
    }

    public Boolean getOverrideUseHttps() {
        if (!contains(KEY_OVERRIDE_USE_HTTPS)) {
            return null;
        }
        return getBoolean(KEY_OVERRIDE_USE_HTTPS, false);
    }

    public void setOverrideUseWss(boolean useWss) {
        putBoolean(KEY_OVERRIDE_USE_WSS, useWss);
    }

    public Boolean getOverrideUseWss() {
        if (!contains(KEY_OVERRIDE_USE_WSS)) {
            return null;
        }
        return getBoolean(KEY_OVERRIDE_USE_WSS, false);
    }

    public void clearServerOverrides() {
        remove(KEY_OVERRIDE_SERVER_IP);
        remove(KEY_OVERRIDE_SERVER_PORT);
        remove(KEY_OVERRIDE_USE_HTTPS);
        remove(KEY_OVERRIDE_USE_WSS);
    }
}

