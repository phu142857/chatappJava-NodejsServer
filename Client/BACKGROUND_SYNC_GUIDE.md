# Background Sync System Guide

## Overview
The Background Sync System reduces UI latency by caching data locally and syncing only delta changes instead of full datasets. It performs updates silently even when the app is minimized.

## Architecture

### Components

1. **DatabaseHelper** - Extended with `sync_metadata` table to track last sync timestamps
2. **SyncManager** - Handles sync logic and cache updates
3. **SyncWorker** - WorkManager worker for background sync (every 15 minutes)
4. **API Endpoints** - Server endpoints for delta sync:
   - `/api/updates/messages?since=timestamp`
   - `/api/updates/posts?since=timestamp`
   - `/api/updates/conversations?since=timestamp`
5. **WebSocket Events** - Realtime events:
   - `new_post` - New post created
   - `avatar_changed` - User avatar updated

## Usage

### 1. Initialize Sync System

In your `Application` class or main `Activity`:

```java
// Schedule background sync
SyncWorker.schedulePeriodicSync(context);

// Get SyncManager instance
SyncManager syncManager = SyncManager.getInstance(context);
```

### 2. Foreground Sync (When App is Active)

In your activities (e.g., `HomeActivity`, `PostFeedActivity`):

```java
@Override
protected void onResume() {
    super.onResume();
    
    DatabaseManager dbManager = new DatabaseManager(this);
    String token = dbManager.getToken();
    
    if (token != null && !token.isEmpty()) {
        SyncManager syncManager = SyncManager.getInstance(this);
        
        // Check if sync is needed (every 30 seconds)
        if (syncManager.shouldSyncForeground()) {
            syncManager.syncForeground(token);
        }
    }
}
```

### 3. Listen to Sync Events

```java
SyncManager.SyncListener syncListener = new SyncManager.SyncListener() {
    @Override
    public void onSyncComplete(String resourceType, boolean success, int itemsUpdated) {
        if (success && itemsUpdated > 0) {
            // Refresh UI with new data
            runOnUiThread(() -> {
                // Update RecyclerView, etc.
                refreshData();
            });
        }
    }
    
    @Override
    public void onSyncError(String resourceType, String error) {
        Log.e(TAG, "Sync error for " + resourceType + ": " + error);
    }
};

syncManager.addSyncListener(syncListener);
```

### 4. Realtime WebSocket Events

```java
SocketManager.RealtimeSyncListener realtimeListener = new SocketManager.RealtimeSyncListener() {
    @Override
    public void onNewPost(JSONObject postJson) {
        // Post already saved to cache by SocketManager
        // Just refresh UI
        runOnUiThread(() -> {
            refreshPostsFeed();
        });
    }
    
    @Override
    public void onAvatarChanged(String userId, String newAvatarUrl) {
        // Update avatar in UI without full reload
        runOnUiThread(() -> {
            updateUserAvatar(userId, newAvatarUrl);
        });
    }
};

SocketManager.getInstance().setRealtimeSyncListener(realtimeListener);
```

### 5. Load Cached Data First

```java
// Load from cache first (instant)
PostRepository postRepo = new PostRepository(context);
List<Post> cachedPosts = postRepo.getPosts(1, 20);
postAdapter.setPosts(cachedPosts);

// Then sync in background
syncManager.syncPosts(token, false);
```

## Sync Intervals

- **Foreground Sync**: Every 30 seconds when app is active
- **Background Sync**: Every 15 minutes when app is minimized (via WorkManager)

## API Response Format

### Success Response (200)
```json
{
  "success": true,
  "data": {
    "messages": [...],
    "updated_at": 1234567890
  }
}
```

### No Updates (304)
```json
{
  "success": true,
  "message": "No updates available"
}
```

## Server Requirements

1. All API responses must include `updated_at` timestamp
2. Responses should contain minimal payload (delta, not full data)
3. Return `304 Not Modified` when no updates available
4. WebSocket server must emit:
   - `new_post` event when a post is created
   - `avatar_changed` event when a user's avatar is updated

## Benefits

1. **Reduced Latency**: UI loads instantly from cache
2. **Bandwidth Savings**: Only sync changes, not full datasets
3. **Background Updates**: Data stays fresh even when app is minimized
4. **Realtime Updates**: WebSocket events provide instant notifications
5. **Offline Support**: Cached data available when offline

## Notes

- Sync happens silently in background
- UI updates only when new data is available
- Failed syncs are logged but don't block UI
- WorkManager handles battery optimization automatically

