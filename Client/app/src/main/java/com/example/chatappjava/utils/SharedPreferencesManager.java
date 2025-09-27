package com.example.chatappjava.utils;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

public class SharedPreferencesManager {
    
    private static final String PREF_NAME = "ChatAppPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_INFO = "userInfo";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_AVATAR = "userAvatar";
    
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private Context context;
    
    public SharedPreferencesManager(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }
    
    /**
     * Save user login information
     */
    public void saveLoginInfo(String token, String userInfo) {
        try {
            JSONObject userJson = new JSONObject(userInfo);
            
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.putString(KEY_TOKEN, token);
            editor.putString(KEY_USER_INFO, userInfo);
            editor.putString(KEY_USER_ID, userJson.optString("_id", ""));
            editor.putString(KEY_USER_NAME, userJson.optString("username", ""));
            editor.putString(KEY_USER_EMAIL, userJson.optString("email", ""));
            editor.putString(KEY_USER_AVATAR, userJson.optString("avatar", ""));
            editor.apply();
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    /**
     * Get login token
     */
    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }
    
    /**
     * Get user information as JSON string
     */
    public String getUserInfo() {
        return sharedPreferences.getString(KEY_USER_INFO, null);
    }
    
    /**
     * Get user ID
     */
    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, "");
    }
    
    /**
     * Get username
     */
    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }
    
    /**
     * Get user email
     */
    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }
    
    /**
     * Get user avatar
     */
    public String getUserAvatar() {
        return sharedPreferences.getString(KEY_USER_AVATAR, "");
    }
    
    /**
     * Get user information as JSONObject
     */
    public JSONObject getUserInfoAsJson() {
        try {
            String userInfo = getUserInfo();
            if (userInfo != null) {
                return new JSONObject(userInfo);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Clear all login information (logout)
     */
    public void clearLoginInfo() {
        editor.clear();
        editor.apply();
    }
    
    /**
     * Update new token
     */
    public void updateToken(String newToken) {
        editor.putString(KEY_TOKEN, newToken);
        editor.apply();
    }
    
    /**
     * Update user information
     */
    public void updateUserInfo(String userInfo) {
        try {
            JSONObject userJson = new JSONObject(userInfo);
            
            editor.putString(KEY_USER_INFO, userInfo);
            editor.putString(KEY_USER_ID, userJson.optString("_id", ""));
            editor.putString(KEY_USER_NAME, userJson.optString("username", ""));
            editor.putString(KEY_USER_EMAIL, userJson.optString("email", ""));
            editor.putString(KEY_USER_AVATAR, userJson.optString("avatar", ""));
            editor.apply();
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
