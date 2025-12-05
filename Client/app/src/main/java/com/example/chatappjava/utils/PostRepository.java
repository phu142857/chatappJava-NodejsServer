package com.example.chatappjava.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.example.chatappjava.models.Post;
import com.example.chatappjava.models.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository class for managing posts in SQLite database
 * Handles offline post storage and retrieval
 * Limits to 50 most recent posts
 */
public class PostRepository {
    private static final String TAG = "PostRepository";
    private static final int MAX_POSTS = 50;
    
    private final DatabaseHelper dbHelper;
    private final Context context;
    
    public PostRepository(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }
    
    /**
     * Save a post to local database
     * If there are more than MAX_POSTS, delete the oldest ones
     */
    public void savePost(Post post) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            // Preserve existing author info and shared post if new values are empty
            // Only preserve if server returned empty values AND existing post has valid values
            // This prevents preserving null/empty values from cache when server has valid data
            String authorUsername = post.getAuthorUsername();
            String authorAvatar = post.getAuthorAvatar();
            com.example.chatappjava.models.Post sharedPost = post.getSharedPost();
            
            // Only check database if new values are empty
            boolean newPostHasEmptyUsername = (authorUsername == null || authorUsername.isEmpty());
            boolean newPostHasEmptyAvatar = (authorAvatar == null || authorAvatar.isEmpty());
            boolean newPostHasNoSharedPost = (sharedPost == null);
            
            if (newPostHasEmptyUsername || newPostHasEmptyAvatar || newPostHasNoSharedPost) {
                Post existingPost = getPostById(post.getId(), db);
                if (existingPost != null) {
                    // Only preserve if new value is empty AND existing value is valid
                    if (newPostHasEmptyUsername) {
                        String existingUsername = existingPost.getAuthorUsername();
                        if (existingUsername != null && !existingUsername.isEmpty()) {
                            authorUsername = existingUsername;
                        }
                    }
                    
                    if (newPostHasEmptyAvatar) {
                        String existingAvatar = existingPost.getAuthorAvatar();
                        if (existingAvatar != null && !existingAvatar.isEmpty()) {
                            authorAvatar = existingAvatar;
                        }
                    }
                    
                    // Preserve shared post if new post doesn't have it AND existing post has it
                    if (newPostHasNoSharedPost && existingPost.getSharedPost() != null) {
                        sharedPost = existingPost.getSharedPost();
                        post.setSharedPost(sharedPost);
                        // Also preserve sharedPostId if it's missing
                        if ((post.getSharedPostId() == null || post.getSharedPostId().isEmpty()) &&
                            (existingPost.getSharedPostId() != null && !existingPost.getSharedPostId().isEmpty())) {
                            post.setSharedPostId(existingPost.getSharedPostId());
                        }
                    }
                }
            }
            
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_POST_ID, post.getId());
            values.put(DatabaseHelper.COL_POST_AUTHOR_ID, post.getAuthorId());
            values.put(DatabaseHelper.COL_POST_AUTHOR_USERNAME, authorUsername != null ? authorUsername : "");
            values.put(DatabaseHelper.COL_POST_AUTHOR_AVATAR, authorAvatar != null ? authorAvatar : "");
            values.put(DatabaseHelper.COL_POST_CONTENT, post.getContent());
            values.put(DatabaseHelper.COL_POST_MEDIA_TYPE, post.getMediaType());
            values.put(DatabaseHelper.COL_POST_TIMESTAMP, post.getTimestamp());
            values.put(DatabaseHelper.COL_POST_LIKES_COUNT, post.getLikesCount());
            values.put(DatabaseHelper.COL_POST_COMMENTS_COUNT, post.getCommentsCount());
            values.put(DatabaseHelper.COL_POST_SHARES_COUNT, post.getSharesCount());
            values.put(DatabaseHelper.COL_POST_IS_LIKED, post.isLiked() ? 1 : 0);
            values.put(DatabaseHelper.COL_POST_REACTION_TYPE, post.getReactionType());
            values.put(DatabaseHelper.COL_POST_SHARED_POST_ID, post.getSharedPostId());
            
            // Serialize shared post to JSON if it exists (use the preserved sharedPost variable)
            if (sharedPost != null) {
                try {
                    JSONObject sharedPostJson = sharedPost.toJson();
                    values.put(DatabaseHelper.COL_POST_SHARED_POST, sharedPostJson.toString());
                } catch (JSONException e) {
                    Log.w(TAG, "Error serializing shared post: " + e.getMessage());
                    values.putNull(DatabaseHelper.COL_POST_SHARED_POST);
                }
            } else {
                values.putNull(DatabaseHelper.COL_POST_SHARED_POST);
            }
            
            // Serialize media URLs to JSON
            if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                JSONArray mediaArray = new JSONArray();
                for (String url : post.getMediaUrls()) {
                    mediaArray.put(url);
                }
                values.put(DatabaseHelper.COL_POST_MEDIA_URLS, mediaArray.toString());
            } else {
                values.putNull(DatabaseHelper.COL_POST_MEDIA_URLS);
            }
            
            // Serialize tagged users to JSON
            if (post.getTaggedUsers() != null && !post.getTaggedUsers().isEmpty()) {
                JSONArray taggedArray = new JSONArray();
                for (User user : post.getTaggedUsers()) {
                    try {
                        taggedArray.put(user.toJson());
                    } catch (JSONException e) {
                        Log.w(TAG, "Error serializing tagged user: " + e.getMessage());
                    }
                }
                values.put(DatabaseHelper.COL_POST_TAGGED_USERS, taggedArray.toString());
            } else {
                values.putNull(DatabaseHelper.COL_POST_TAGGED_USERS);
            }
            
            // Use INSERT OR REPLACE to handle both insert and update
            db.insertWithOnConflict(
                DatabaseHelper.TABLE_POSTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            );
            
            // Keep only MAX_POSTS most recent posts
            keepOnlyRecentPosts(db);
            
            Log.d(TAG, "Post saved: " + post.getId());
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving post: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Save multiple posts to local database
     * If there are more than MAX_POSTS, delete the oldest ones
     */
    public void savePosts(List<Post> posts) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            db.beginTransaction();
            
            for (Post post : posts) {
                // Preserve existing author info and shared post if new values are empty
                // Only preserve if server returned empty values AND existing post has valid values
                // This prevents preserving null/empty values from cache when server has valid data
                String authorUsername = post.getAuthorUsername();
                String authorAvatar = post.getAuthorAvatar();
                com.example.chatappjava.models.Post sharedPost = post.getSharedPost();
                
                // Only check database if new values are empty
                boolean newPostHasEmptyUsername = (authorUsername == null || authorUsername.isEmpty());
                boolean newPostHasEmptyAvatar = (authorAvatar == null || authorAvatar.isEmpty());
                boolean newPostHasNoSharedPost = (sharedPost == null);
                
                if (newPostHasEmptyUsername || newPostHasEmptyAvatar || newPostHasNoSharedPost) {
                    Post existingPost = getPostById(post.getId(), db);
                    if (existingPost != null) {
                        // Only preserve if new value is empty AND existing value is valid
                        if (newPostHasEmptyUsername) {
                            String existingUsername = existingPost.getAuthorUsername();
                            if (existingUsername != null && !existingUsername.isEmpty()) {
                                authorUsername = existingUsername;
                            }
                        }
                        
                        if (newPostHasEmptyAvatar) {
                            String existingAvatar = existingPost.getAuthorAvatar();
                            if (existingAvatar != null && !existingAvatar.isEmpty()) {
                                authorAvatar = existingAvatar;
                            }
                        }
                        
                        // Preserve shared post if new post doesn't have it AND existing post has it
                        if (newPostHasNoSharedPost && existingPost.getSharedPost() != null) {
                            sharedPost = existingPost.getSharedPost();
                            post.setSharedPost(sharedPost);
                            // Also preserve sharedPostId if it's missing
                            if ((post.getSharedPostId() == null || post.getSharedPostId().isEmpty()) &&
                                (existingPost.getSharedPostId() != null && !existingPost.getSharedPostId().isEmpty())) {
                                post.setSharedPostId(existingPost.getSharedPostId());
                            }
                        }
                    }
                }
                
                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COL_POST_ID, post.getId());
                values.put(DatabaseHelper.COL_POST_AUTHOR_ID, post.getAuthorId());
                values.put(DatabaseHelper.COL_POST_AUTHOR_USERNAME, authorUsername != null ? authorUsername : "");
                values.put(DatabaseHelper.COL_POST_AUTHOR_AVATAR, authorAvatar != null ? authorAvatar : "");
                values.put(DatabaseHelper.COL_POST_CONTENT, post.getContent());
                values.put(DatabaseHelper.COL_POST_MEDIA_TYPE, post.getMediaType());
                values.put(DatabaseHelper.COL_POST_TIMESTAMP, post.getTimestamp());
                values.put(DatabaseHelper.COL_POST_LIKES_COUNT, post.getLikesCount());
                values.put(DatabaseHelper.COL_POST_COMMENTS_COUNT, post.getCommentsCount());
                values.put(DatabaseHelper.COL_POST_SHARES_COUNT, post.getSharesCount());
                values.put(DatabaseHelper.COL_POST_IS_LIKED, post.isLiked() ? 1 : 0);
                values.put(DatabaseHelper.COL_POST_REACTION_TYPE, post.getReactionType());
                values.put(DatabaseHelper.COL_POST_SHARED_POST_ID, post.getSharedPostId());
                
                // Serialize shared post to JSON if it exists (use the preserved sharedPost variable)
                if (sharedPost != null) {
                    try {
                        JSONObject sharedPostJson = sharedPost.toJson();
                        values.put(DatabaseHelper.COL_POST_SHARED_POST, sharedPostJson.toString());
                    } catch (JSONException e) {
                        Log.w(TAG, "Error serializing shared post: " + e.getMessage());
                        values.putNull(DatabaseHelper.COL_POST_SHARED_POST);
                    }
                } else {
                    values.putNull(DatabaseHelper.COL_POST_SHARED_POST);
                }
                
                // Serialize media URLs to JSON
                if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
                    JSONArray mediaArray = new JSONArray();
                    for (String url : post.getMediaUrls()) {
                        mediaArray.put(url);
                    }
                    values.put(DatabaseHelper.COL_POST_MEDIA_URLS, mediaArray.toString());
                } else {
                    values.putNull(DatabaseHelper.COL_POST_MEDIA_URLS);
                }
                
                // Serialize tagged users to JSON
                if (post.getTaggedUsers() != null && !post.getTaggedUsers().isEmpty()) {
                    JSONArray taggedArray = new JSONArray();
                    for (User user : post.getTaggedUsers()) {
                        try {
                            taggedArray.put(user.toJson());
                        } catch (JSONException e) {
                            Log.w(TAG, "Error serializing tagged user: " + e.getMessage());
                        }
                    }
                    values.put(DatabaseHelper.COL_POST_TAGGED_USERS, taggedArray.toString());
                } else {
                    values.putNull(DatabaseHelper.COL_POST_TAGGED_USERS);
                }
                
                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_POSTS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                );
            }
            
            // Keep only MAX_POSTS most recent posts
            keepOnlyRecentPosts(db);
            
            db.setTransactionSuccessful();
            Log.d(TAG, "Saved " + posts.size() + " posts");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving posts: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
            db.close();
        }
    }
    
    /**
     * Get all posts, ordered by timestamp descending (newest first)
     * Limited to MAX_POSTS
     */
    public List<Post> getAllPosts() {
        List<Post> posts = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String orderBy = DatabaseHelper.COL_POST_TIMESTAMP + " DESC";
        String limitStr = String.valueOf(MAX_POSTS);
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_POSTS,
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
                    Post post = cursorToPost(cursor);
                    if (post != null) {
                        posts.add(post);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        
        db.close();
        Log.d(TAG, "Retrieved " + posts.size() + " posts");
        return posts;
    }
    
    /**
     * Get a post by ID from local database
     * @param postId The post ID to retrieve
     * @param db Optional existing database connection. If null, a new connection will be opened and closed.
     */
    public Post getPostById(String postId, SQLiteDatabase db) {
        if (postId == null || postId.isEmpty()) {
            return null;
        }
        
        boolean shouldClose = false;
        if (db == null) {
            db = dbHelper.getReadableDatabase();
            shouldClose = true;
        }
        
        Post post = null;
        
        String selection = DatabaseHelper.COL_POST_ID + " = ?";
        String[] selectionArgs = {postId};
        
        Cursor cursor = db.query(
            DatabaseHelper.TABLE_POSTS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null,
            "1"
        );
        
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    post = cursorToPost(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        
        if (shouldClose) {
            db.close();
        }
        return post;
    }
    
    /**
     * Get a post by ID from local database (opens and closes its own connection)
     */
    public Post getPostById(String postId) {
        return getPostById(postId, null);
    }
    
    /**
     * Delete all posts from database
     */
    public void deleteAllPosts() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            int deleted = db.delete(DatabaseHelper.TABLE_POSTS, null, null);
            Log.d(TAG, "Deleted " + deleted + " posts");
        } catch (Exception e) {
            Log.e(TAG, "Error deleting posts: " + e.getMessage(), e);
        } finally {
            db.close();
        }
    }
    
    /**
     * Keep only MAX_POSTS most recent posts, delete the rest
     */
    private void keepOnlyRecentPosts(SQLiteDatabase db) {
        try {
            // Get count of posts
            Cursor countCursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_POSTS,
                null
            );
            int count = 0;
            if (countCursor != null && countCursor.moveToFirst()) {
                count = countCursor.getInt(0);
                countCursor.close();
            }
            
            // If we have more than MAX_POSTS, delete the oldest ones
            if (count > MAX_POSTS) {
                int toDelete = count - MAX_POSTS;
                // Delete oldest posts (lowest timestamp)
                db.execSQL(
                    "DELETE FROM " + DatabaseHelper.TABLE_POSTS +
                    " WHERE " + DatabaseHelper.COL_POST_ID + " IN (" +
                    "SELECT " + DatabaseHelper.COL_POST_ID +
                    " FROM " + DatabaseHelper.TABLE_POSTS +
                    " ORDER BY " + DatabaseHelper.COL_POST_TIMESTAMP + " ASC" +
                    " LIMIT " + toDelete + ")"
                );
                Log.d(TAG, "Deleted " + toDelete + " oldest posts to keep only " + MAX_POSTS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error keeping only recent posts: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert Cursor to Post object
     */
    private Post cursorToPost(Cursor cursor) {
        try {
            JSONObject postJson = new JSONObject();
            
            int idIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_ID);
            int authorIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_AUTHOR_ID);
            int authorUsernameIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_AUTHOR_USERNAME);
            int authorAvatarIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_AUTHOR_AVATAR);
            int contentIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_CONTENT);
            int mediaUrlsIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_MEDIA_URLS);
            int mediaTypeIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_MEDIA_TYPE);
            int timestampIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_TIMESTAMP);
            int likesCountIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_LIKES_COUNT);
            int commentsCountIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_COMMENTS_COUNT);
            int sharesCountIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_SHARES_COUNT);
            int isLikedIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_IS_LIKED);
            int reactionTypeIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_REACTION_TYPE);
            int sharedPostIdIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_SHARED_POST_ID);
            int sharedPostIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_SHARED_POST);
            int taggedUsersIndex = cursor.getColumnIndex(DatabaseHelper.COL_POST_TAGGED_USERS);
            
            if (idIndex >= 0 && !cursor.isNull(idIndex)) {
                postJson.put("_id", cursor.getString(idIndex));
            }
            if (authorIdIndex >= 0 && !cursor.isNull(authorIdIndex)) {
                JSONObject userIdObj = new JSONObject();
                userIdObj.put("_id", cursor.getString(authorIdIndex));
                if (authorUsernameIndex >= 0 && !cursor.isNull(authorUsernameIndex)) {
                    userIdObj.put("username", cursor.getString(authorUsernameIndex));
                }
                if (authorAvatarIndex >= 0 && !cursor.isNull(authorAvatarIndex)) {
                    userIdObj.put("avatar", cursor.getString(authorAvatarIndex));
                }
                postJson.put("userId", userIdObj);
            }
            if (contentIndex >= 0 && !cursor.isNull(contentIndex)) {
                postJson.put("content", cursor.getString(contentIndex));
            }
            if (timestampIndex >= 0 && !cursor.isNull(timestampIndex)) {
                postJson.put("createdAt", cursor.getLong(timestampIndex));
            }
            if (likesCountIndex >= 0 && !cursor.isNull(likesCountIndex)) {
                postJson.put("likesCount", cursor.getInt(likesCountIndex));
            }
            if (commentsCountIndex >= 0 && !cursor.isNull(commentsCountIndex)) {
                postJson.put("commentsCount", cursor.getInt(commentsCountIndex));
            }
            if (sharesCountIndex >= 0 && !cursor.isNull(sharesCountIndex)) {
                postJson.put("sharesCount", cursor.getInt(sharesCountIndex));
            }
            if (isLikedIndex >= 0 && !cursor.isNull(isLikedIndex)) {
                postJson.put("isLiked", cursor.getInt(isLikedIndex) == 1);
            }
            if (reactionTypeIndex >= 0 && !cursor.isNull(reactionTypeIndex)) {
                postJson.put("reactionType", cursor.getString(reactionTypeIndex));
            }
            // Handle shared post - prefer the complete object if available, otherwise use ID
            if (sharedPostIndex >= 0 && !cursor.isNull(sharedPostIndex)) {
                // Load complete shared post object from JSON
                String sharedPostJsonStr = cursor.getString(sharedPostIndex);
                if (sharedPostJsonStr != null && !sharedPostJsonStr.isEmpty()) {
                    try {
                        JSONObject sharedPostJsonObj = new JSONObject(sharedPostJsonStr);
                        // Put as populated object (same format as backend)
                        postJson.put("sharedPostId", sharedPostJsonObj);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing shared post JSON: " + e.getMessage());
                        // Fallback to ID if JSON parsing fails
                        if (sharedPostIdIndex >= 0 && !cursor.isNull(sharedPostIdIndex)) {
                            postJson.put("sharedPostId", cursor.getString(sharedPostIdIndex));
                        }
                    }
                } else if (sharedPostIdIndex >= 0 && !cursor.isNull(sharedPostIdIndex)) {
                    // Fallback to ID if shared post JSON is empty
                    postJson.put("sharedPostId", cursor.getString(sharedPostIdIndex));
                }
            } else if (sharedPostIdIndex >= 0 && !cursor.isNull(sharedPostIdIndex)) {
                // Only ID available (old data)
                postJson.put("sharedPostId", cursor.getString(sharedPostIdIndex));
            }
            
            // Parse media URLs
            if (mediaUrlsIndex >= 0 && !cursor.isNull(mediaUrlsIndex)) {
                String mediaUrlsJson = cursor.getString(mediaUrlsIndex);
                if (mediaUrlsJson != null && !mediaUrlsJson.isEmpty()) {
                    try {
                        JSONArray mediaArray = new JSONArray(mediaUrlsJson);
                        postJson.put("images", mediaArray);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing media URLs: " + e.getMessage());
                        postJson.put("images", new JSONArray());
                    }
                } else {
                    postJson.put("images", new JSONArray());
                }
            } else {
                postJson.put("images", new JSONArray());
            }
            
            if (mediaTypeIndex >= 0 && !cursor.isNull(mediaTypeIndex)) {
                postJson.put("mediaType", cursor.getString(mediaTypeIndex));
            }
            
            // Parse tagged users
            if (taggedUsersIndex >= 0 && !cursor.isNull(taggedUsersIndex)) {
                String taggedUsersJson = cursor.getString(taggedUsersIndex);
                if (taggedUsersJson != null && !taggedUsersJson.isEmpty()) {
                    try {
                        JSONArray taggedArray = new JSONArray(taggedUsersJson);
                        postJson.put("tags", taggedArray);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing tagged users: " + e.getMessage());
                        postJson.put("tags", new JSONArray());
                    }
                } else {
                    postJson.put("tags", new JSONArray());
                }
            } else {
                postJson.put("tags", new JSONArray());
            }
            
            return Post.fromJson(postJson);
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to post: " + e.getMessage(), e);
            return null;
        }
    }
}

