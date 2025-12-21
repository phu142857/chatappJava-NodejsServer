package com.example.chatappjava.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;

/**
 * Helper class to handle battery optimization settings
 * Android may kill apps in background to save battery, which can prevent push notifications
 */
public class BatteryOptimizationHelper {
    private static final String TAG = "BatteryOptimization";

    /**
     * Check if battery optimization is disabled for this app
     * Returns true if optimization is disabled (app can run in background)
     */
    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Battery optimization was introduced in Android 6.0 (API 23)
            return true;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return false;
        }

        String packageName = context.getPackageName();
        return powerManager.isIgnoringBatteryOptimizations(packageName);
    }

    /**
     * Request to disable battery optimization for this app
     * This will show a system dialog asking user to allow
     */
    public static void requestDisableBatteryOptimization(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Not needed for Android < 6.0
            return;
        }

        if (isBatteryOptimizationDisabled(context)) {
            Log.d(TAG, "Battery optimization already disabled");
            return;
        }

        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
            Log.d(TAG, "Requested to disable battery optimization");
        } catch (Exception e) {
            Log.e(TAG, "Error requesting battery optimization: " + e.getMessage());
            // Fallback: Open battery optimization settings
            openBatteryOptimizationSettings(context);
        }
    }

    /**
     * Open battery optimization settings page
     * User can manually disable optimization
     */
    public static void openBatteryOptimizationSettings(Context context) {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            context.startActivity(intent);
            Log.d(TAG, "Opened battery optimization settings");
        } catch (Exception e) {
            Log.e(TAG, "Error opening battery optimization settings: " + e.getMessage());
            // Final fallback: Open app settings
            openAppSettings(context);
        }
    }

    /**
     * Open app settings page
     */
    public static void openAppSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
            Log.d(TAG, "Opened app settings");
        } catch (Exception e) {
            Log.e(TAG, "Error opening app settings: " + e.getMessage());
        }
    }

    /**
     * Show dialog explaining why battery optimization should be disabled
     */
    public static void showBatteryOptimizationDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Disable Battery Optimization for Notifications");
        builder.setMessage("To receive notifications when the app is closed, please disable battery optimization for this app.\n\n" +
                "You will be redirected to settings to do this.");
        builder.setPositiveButton("Go to Settings", (dialog, which) -> {
            requestDisableBatteryOptimization(context);
        });
        builder.setNegativeButton("Skip", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.setCancelable(true);
        builder.show();
    }

    /**
     * Check and prompt user if battery optimization is enabled
     * Call this after login or when app starts
     */
    public static void checkAndPromptBatteryOptimization(Context context) {
        if (!isBatteryOptimizationDisabled(context)) {
            Log.d(TAG, "Battery optimization is enabled, showing dialog");
            showBatteryOptimizationDialog(context);
        } else {
            Log.d(TAG, "Battery optimization is disabled, notifications should work");
        }
    }
}

