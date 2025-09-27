package com.example.chatappjava.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;

public class ErrorHandler {
    
    public static void handleApiError(Context context, int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            String message = jsonResponse.optString("message", "An error occurred");
            String accountStatus = jsonResponse.optString("accountStatus", "");
            String details = jsonResponse.optString("details", "");
            
            if (statusCode == 403) {
                // Handle different 403 scenarios
                if ("locked".equals(accountStatus)) {
                    // User account is locked
                    StringBuilder errorMessage = new StringBuilder("🔒 ").append(message);
                    if (!details.isEmpty()) {
                        errorMessage.append("\n\n").append(details);
                    }
                    
                    showDetailedToast(context, errorMessage.toString(), true);
                    
                } else {
                    // Other 403 errors
                    showDetailedToast(context, "🚫 " + message, true);
                }
                
                // Clear user session and redirect to login for 403 errors
                clearSessionAndRedirect(context);
                
            } else if (statusCode == 401) {
                // Handle different 401 scenarios
                if ("not_found".equals(accountStatus)) {
                    // Account not found
                    StringBuilder errorMessage = new StringBuilder("❓ ").append(message);
                    if (!details.isEmpty()) {
                        errorMessage.append("\n\n").append(details);
                    }
                    
                    showDetailedToast(context, errorMessage.toString(), false);
                    
                } else if ("active".equals(accountStatus)) {
                    // Wrong password but account is active
                    StringBuilder errorMessage = new StringBuilder("🔑 ").append(message);
                    if (!details.isEmpty()) {
                        errorMessage.append("\n\n").append(details);
                    }
                    
                    showDetailedToast(context, errorMessage.toString(), false);
                    
                } else {
                    // Token expired or invalid
                    showDetailedToast(context, "🔒 " + message, true);
                    clearSessionAndRedirect(context);
                }
                
            } else {
                // Other errors
                showDetailedToast(context, "⚠️ " + message, false);
            }
            
        } catch (JSONException e) {
            e.printStackTrace();
            showDetailedToast(context, "❌ Error processing response: " + e.getMessage(), false);
        }
    }
    
    private static void showDetailedToast(Context context, String message, boolean isLong) {
        Toast.makeText(context, message, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }
    
    private static void clearSessionAndRedirect(Context context) {
        // Clear user session
        SharedPreferencesManager sharedPrefsManager = new SharedPreferencesManager(context);
        sharedPrefsManager.clearLoginInfo();
        
        // Redirect to login activity
        Intent intent = new Intent(context, com.example.chatappjava.ui.theme.LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
    
    public static void handleApiError(Context context, int statusCode, String responseBody, boolean showToast) {
        if (showToast) {
            handleApiError(context, statusCode, responseBody);
        } else {
            // Just handle the error without showing toast
            try {
                JSONObject jsonResponse = new JSONObject(responseBody);
                String accountStatus = jsonResponse.optString("accountStatus", "");
                
                // Only redirect for serious account issues (blocked, deactivated, token expired)
                if (statusCode == 403 || 
                    (statusCode == 401 && !"not_found".equals(accountStatus) && !"active".equals(accountStatus))) {
                    clearSessionAndRedirect(context);
                }
                
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
