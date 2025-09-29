package com.example.chatappjava.network;

import com.example.chatappjava.config.ServerConfig;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class ApiClient {

    // Use ServerConfig for dynamic server URL
    private static String getBaseUrl() {
        return ServerConfig.getBaseUrl();
    }

    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REGISTER_ENDPOINT = "/api/auth/register";
    private static final String REFRESH_TOKEN_ENDPOINT = "/api/auth/refresh";
    private static final String SEARCH_USERS_ENDPOINT = "/api/users/search";
    private static final String CREATE_CHAT_ENDPOINT = "/api/chats/private";
    private static final String CREATE_GROUP_CHAT_ENDPOINT = "/api/chats/group";
    private static final String GET_CHATS_ENDPOINT = "/api/chats";
    private static final String SEND_FRIEND_REQUEST_ENDPOINT = "/api/friend-requests";
    private static final String GET_FRIEND_REQUESTS_ENDPOINT = "/api/friend-requests";
    private static final String RESPOND_FRIEND_REQUEST_ENDPOINT = "/api/friend-requests";
    private static final String GET_ME_ENDPOINT = "/api/auth/me";
    private static final String UPDATE_PROFILE_ENDPOINT = "/api/auth/profile";
    private static final String UPLOAD_AVATAR_ENDPOINT = "/api/auth/upload-avatar";
    private static final String UPLOAD_CHAT_IMAGE_ENDPOINT = "/api/upload/chat";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient client;

    public ApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Authenticates a user.
     */
    public void login(JSONObject loginData, Callback callback) {
        String url = getBaseUrl() + LOGIN_ENDPOINT;
        android.util.Log.d("ApiClient", "Login URL: " + url);
        
        RequestBody body = RequestBody.create(loginData.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Registers a new user.
     */
    public void register(JSONObject registerData, Callback callback) {
        RequestBody body = RequestBody.create(registerData.toString(), JSON);
        Request request = new Request.Builder()
                .url(getBaseUrl() + REGISTER_ENDPOINT)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Refreshes the authentication token.
     */
    public void refreshToken(String refreshToken, Callback callback) {
        try {
            JSONObject tokenData = new JSONObject();
            tokenData.put("refreshToken", refreshToken);

            RequestBody body = RequestBody.create(tokenData.toString(), JSON);
            Request request = new Request.Builder()
                    .url(getBaseUrl() + REFRESH_TOKEN_ENDPOINT)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(callback);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a request builder with an Authorization header.
     */
    public Request.Builder createAuthenticatedRequest(String token) {
        return new Request.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json");
    }

    /**
     * Sends an authenticated GET request.
     */
    public void authenticatedGet(String endpoint, String token, Callback callback) {
        Request request = createAuthenticatedRequest(token)
                .url(getBaseUrl() + endpoint)
                .get()
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Sends an authenticated POST request.
     */
    public void authenticatedPost(String endpoint, String token, JSONObject data, Callback callback) {
        RequestBody body = RequestBody.create(data.toString(), JSON);
        Request request = createAuthenticatedRequest(token)
                .url(getBaseUrl() + endpoint)
                .post(body)
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Sends an authenticated PUT request.
     */
    public void authenticatedPut(String endpoint, String token, JSONObject data, Callback callback) {
        RequestBody body = RequestBody.create(data.toString(), JSON);
        Request request = createAuthenticatedRequest(token)
                .url(getBaseUrl() + endpoint)
                .put(body)
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Sends an authenticated DELETE request.
     */
    public void authenticatedDelete(String endpoint, String token, Callback callback) {
        Request request = createAuthenticatedRequest(token)
                .url(getBaseUrl() + endpoint)
                .delete()
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Send friend request
     */
    public void sendFriendRequest(String token, JSONObject requestData, Callback callback) {
        authenticatedPost(SEND_FRIEND_REQUEST_ENDPOINT, token, requestData, callback);
    }

    /**
     * Get friend requests (sent and received)
     */
    public void getFriendRequests(String token, Callback callback) {
        authenticatedGet(GET_FRIEND_REQUESTS_ENDPOINT, token, callback);
    }

    /**
     * Respond to friend request (accept/reject)
     */
    public void respondToFriendRequest(String token, String requestId, String action, Callback callback) {
        try {
            JSONObject responseData = new JSONObject();
            responseData.put("action", action); // "accept" or "reject"
            
            String endpoint = RESPOND_FRIEND_REQUEST_ENDPOINT + "/" + requestId;
            authenticatedPut(endpoint, token, responseData, callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cancel friend request
     */
    public void cancelFriendRequest(String token, String requestId, Callback callback) {
        String endpoint = RESPOND_FRIEND_REQUEST_ENDPOINT + "/" + requestId;
        authenticatedDelete(endpoint, token, callback);
    }

    /**
     * Create a new chat
     */
    public void createChat(String token, JSONObject chatData, Callback callback) {
        authenticatedPost(CREATE_CHAT_ENDPOINT, token, chatData, callback);
    }

    /**
     * Create a new group chat
     */
    public void createGroupChat(String token, JSONObject groupData, Callback callback) {
        authenticatedPost(CREATE_GROUP_CHAT_ENDPOINT, token, groupData, callback);
    }

    /**
     * Get user's chats
     */
    public void getChats(String token, Callback callback) {
        authenticatedGet(GET_CHATS_ENDPOINT, token, callback);
    }

    /**
     * Send message to chat
     */
    public void sendMessage(String token, JSONObject messageData, Callback callback) {
        authenticatedPost("/api/messages", token, messageData, callback);
    }
    
    /**
     * Edit a message content
     */
    public void editMessage(String token, String messageId, String newContent, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("content", newContent);
            authenticatedPut("/api/messages/" + messageId, token, body, callback);
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to prepare edit message: " + e.getMessage()));
        }
    }

    /**
     * Delete a message
     */
    public void deleteMessage(String token, String messageId, Callback callback) {
        authenticatedDelete("/api/messages/" + messageId, token, callback);
    }

    /**
     * Add reaction to a message
     */
    public void addReaction(String token, String messageId, String emoji, Callback callback) {
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("emoji", emoji);
            authenticatedPost("/api/messages/" + messageId + "/reactions", token, body, callback);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to prepare add reaction: " + e.getMessage()));
        }
    }

    /**
     * Remove reaction from a message
     */
    public void removeReaction(String token, String messageId, String emoji, Callback callback) {
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("emoji", emoji);
            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = createAuthenticatedRequest(token)
                    .url(getBaseUrl() + "/api/messages/" + messageId + "/reactions")
                    .delete(requestBody)
                    .build();
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to prepare remove reaction: " + e.getMessage()));
        }
    }
    
    /**
     * Get messages from chat
     */
    public void getMessages(String token, String chatId, Callback callback) {
        // Server route is GET /api/messages/:chatId
        authenticatedGet("/api/messages/" + chatId, token, callback);
    }
    
    /**
     * Delete a chat
     */
    public void deleteChat(String token, String chatId, Callback callback) {
        authenticatedDelete("/api/chats/" + chatId, token, callback);
    }

    /**
     * Unfriend a user (remove both sides friendship)
     */
    public void unfriendUser(String token, String userId, Callback callback) {
        // If there is a dedicated unfriend endpoint, use it; else fallback to friend requests controller if provided
        authenticatedDelete("/api/users/" + userId + "/friends", token, callback);
    }
    
    /**
     * Get current user profile
     */
    public void getMe(String token, Callback callback) {
        authenticatedGet(GET_ME_ENDPOINT, token, callback);
    }

    /**
     * Update user profile
     */
    public void updateProfile(String token, JSONObject profileData, Callback callback) {
        authenticatedPut(UPDATE_PROFILE_ENDPOINT, token, profileData, callback);
    }

    /**
     * Uploads user avatar image.
     */
    public void uploadAvatar(String token, File imageFile, Callback callback) {
        try {
            String url = getBaseUrl() + UPLOAD_AVATAR_ENDPOINT;
            
            RequestBody fileBody = RequestBody.create(
                MediaType.parse("image/*"), 
                imageFile
            );
            
            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("avatar", imageFile.getName(), fileBody)
                .build();
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();
            
            client.newCall(request).enqueue(callback);
            
        } catch (Exception e) {
            e.printStackTrace();
            // Create a failed callback
            callback.onFailure(null, new IOException("Failed to prepare avatar upload: " + e.getMessage()));
        }
    }

    /**
     * Upload chat image to server
     * @param token Authentication token
     * @param imageFile Image file to upload
     * @param chatId Chat ID for organizing uploads
     * @param callback Response callback
     */
    public void uploadChatImage(String token, File imageFile, String chatId, Callback callback) {
        try {
            String url = getBaseUrl() + UPLOAD_CHAT_IMAGE_ENDPOINT + "/" + chatId;
            
            RequestBody fileBody = RequestBody.create(
                MediaType.parse("image/*"), 
                imageFile
            );
            
            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(), fileBody)
                .addFormDataPart("chatId", chatId)
                .build();
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();
            
            client.newCall(request).enqueue(callback);
            
        } catch (Exception e) {
            e.printStackTrace();
            // Create a failed callback
            callback.onFailure(null, new IOException("Failed to prepare chat image upload: " + e.getMessage()));
        }
    }

    /**
     * Leave a group chat
     */
    public void leaveGroup(String token, String groupId, Callback callback) {
        String url = getBaseUrl() + "/api/chats/" + groupId + "/leave";
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + token)
                .build();
        
        client.newCall(request).enqueue(callback);
    }

    /**
     * Add members to a group chat
     */
    public void addMembers(String token, String groupId, JSONObject memberData, Callback callback) {
        String url = getBaseUrl() + "/api/chats/" + groupId + "/members";
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(memberData.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + token)
                .build();
        
        client.newCall(request).enqueue(callback);
    }

    /**
     * Remove member from a group chat
     */
    public void removeMember(String token, String groupId, JSONObject memberData, Callback callback) {
        String url = getBaseUrl() + "/api/chats/" + groupId + "/members";
        
        Request request = new Request.Builder()
                .url(url)
                .delete(RequestBody.create(memberData.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + token)
                .build();
        
        client.newCall(request).enqueue(callback);
    }

    /**
     * Get group members
     */
    public void getGroupMembers(String token, String groupId, Callback callback) {
        String url = getBaseUrl() + "/api/chats/" + groupId + "/members";
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + token)
                .build();
        
        client.newCall(request).enqueue(callback);
    }

    /**
     * Upload group avatar
     */
    public void uploadGroupAvatar(String token, String groupId, File imageFile, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/chats/" + groupId + "/avatar";
            
            RequestBody fileBody = RequestBody.create(imageFile, MediaType.parse("image/*"));
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("avatar", imageFile.getName(), fileBody)
                    .build();
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            // Create a failed callback
            callback.onFailure(null, new IOException("Failed to prepare group avatar upload: " + e.getMessage()));
        }
    }

    /**
     * Initiate a new call
     */
    public void initiateCall(String token, String chatId, String callType, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/initiate";
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("chatId", chatId);
            requestBody.put("type", callType);
            
            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to initiate call: " + e.getMessage()));
        }
    }
    
    /**
     * Join an existing call
     */
    public void joinCall(String token, String callId, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/" + callId + "/join";
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to join call: " + e.getMessage()));
        }
    }
    
    /**
     * Decline a call
     */
    public void declineCall(String token, String callId, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/" + callId + "/decline";
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to decline call: " + e.getMessage()));
        }
    }
    
    /**
     * Leave a call
     */
    public void leaveCall(String token, String callId, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/" + callId + "/leave";
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to leave call: " + e.getMessage()));
        }
    }
    
    /**
     * End a call
     */
    public void endCall(String token, String callId, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/" + callId + "/end";
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to end call: " + e.getMessage()));
        }
    }
    
    /**
     * Get call details
     */
    public void getCallDetails(String token, String callId, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/" + callId;
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to get call details: " + e.getMessage()));
        }
    }
    
    /**
     * Get call history
     */
    public void getCallHistory(String token, int limit, int page, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/history?limit=" + limit + "&page=" + page;
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to get call history: " + e.getMessage()));
        }
    }
    
    /**
     * Get active calls
     */
    public void getActiveCalls(String token, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/active";
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to get active calls: " + e.getMessage()));
        }
    }
    
    /**
     * Update call settings
     */
    public void updateCallSettings(String token, String callId, String settingsJson, Callback callback) {
        try {
            String url = getBaseUrl() + "/api/calls/" + callId + "/settings";
            
            RequestBody body = RequestBody.create(settingsJson, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .put(body)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(null, new IOException("Failed to update call settings: " + e.getMessage()));
        }
    }

    /**
     * Cancels all pending requests.
     */
    public void cancelAllRequests() {
        client.dispatcher().cancelAll();
    }
}