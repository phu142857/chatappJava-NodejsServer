package com.example.chatappjava.utils;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/**
 * WorkManager worker for background synchronization
 * Runs periodically (every 15 minutes) when app is in background
 */
public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Background sync worker started");
        
        try {
            DatabaseManager dbManager = new DatabaseManager(getApplicationContext());
            String token = dbManager.getToken();
            
            if (token == null || token.isEmpty()) {
                Log.w(TAG, "Cannot sync: no token available");
                return Result.success(); // Not a failure, just no user logged in
            }
            
            SyncManager syncManager = SyncManager.getInstance(getApplicationContext());
            syncManager.syncBackground(token);
            
            // Note: syncBackground is async, but we return success immediately
            // The actual sync happens in background via callbacks
            Log.d(TAG, "Background sync initiated");
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in background sync worker: " + e.getMessage(), e);
            return Result.retry(); // Retry on failure
        }
    }
    
    /**
     * Schedule periodic background sync (every 15 minutes)
     */
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // WiFi or Mobile Data
            .setRequiresBatteryNotLow(false) // Optional: set to true to require good battery
            .build();
        
        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
            SyncWorker.class,
            15, // Repeat interval
            TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .addTag("background_sync")
        .build();
        
        WorkManager.getInstance(context).enqueue(syncWork);
        Log.d(TAG, "Periodic background sync scheduled (every 15 minutes)");
    }
    
    /**
     * Cancel periodic background sync
     */
    public static void cancelPeriodicSync(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("background_sync");
        Log.d(TAG, "Periodic background sync cancelled");
    }
}

