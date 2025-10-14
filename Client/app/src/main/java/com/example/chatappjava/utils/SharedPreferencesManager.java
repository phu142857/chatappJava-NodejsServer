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
    private static final String KEY_OVERRIDE_SERVER_IP = "overrideServerIp";
    private static final String KEY_OVERRIDE_SERVER_PORT = "overrideServerPort";
    private static final String KEY_OVERRIDE_USE_HTTPS = "overrideUseHttps";
    private static final String KEY_OVERRIDE_USE_WSS = "overrideUseWss";
    
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public SharedPreferencesManager(Context context) {
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
     * Clear all login information (logout)
     */
    public void clearLoginInfo() {
        editor.clear();
        editor.apply();
    }

    // ===== Server overrides =====
    public void setOverrideServerIp(String ip) {
        editor.putString(KEY_OVERRIDE_SERVER_IP, ip);
        editor.apply();
    }

    public String getOverrideServerIp() {
        return sharedPreferences.getString(KEY_OVERRIDE_SERVER_IP, null);
    }

    public void setOverrideServerPort(int port) {
        editor.putInt(KEY_OVERRIDE_SERVER_PORT, port);
        editor.apply();
    }

    public int getOverrideServerPort() {
        return sharedPreferences.getInt(KEY_OVERRIDE_SERVER_PORT, -1);
    }

    public void setOverrideUseHttps(boolean useHttps) {
        editor.putBoolean(KEY_OVERRIDE_USE_HTTPS, useHttps);
        editor.apply();
    }

    public Boolean getOverrideUseHttps() {
        return sharedPreferences.contains(KEY_OVERRIDE_USE_HTTPS)
                ? sharedPreferences.getBoolean(KEY_OVERRIDE_USE_HTTPS, false)
                : null;
    }

    public void setOverrideUseWss(boolean useWss) {
        editor.putBoolean(KEY_OVERRIDE_USE_WSS, useWss);
        editor.apply();
    }

    public Boolean getOverrideUseWss() {
        return sharedPreferences.contains(KEY_OVERRIDE_USE_WSS)
                ? sharedPreferences.getBoolean(KEY_OVERRIDE_USE_WSS, false)
                : null;
    }

    public void clearServerOverrides() {
        editor.remove(KEY_OVERRIDE_SERVER_IP);
        editor.remove(KEY_OVERRIDE_SERVER_PORT);
        editor.remove(KEY_OVERRIDE_USE_HTTPS);
        editor.remove(KEY_OVERRIDE_USE_WSS);
        editor.apply();
    }
}
