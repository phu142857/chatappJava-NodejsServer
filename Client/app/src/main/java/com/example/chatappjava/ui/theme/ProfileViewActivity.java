package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONException;
import org.json.JSONObject;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileViewActivity extends AppCompatActivity {
    
    private CircleImageView civAvatar;
    private TextView tvUsername;
    private TextView tvFirstName;
    private TextView tvLastName;
    private TextView tvPhoneNumber;
    private TextView tvBio;
    private TextView tvStatus;
    private ImageView ivBack;
    private ImageView ivMore;
    
    private User otherUser;
    private AvatarManager avatarManager;
    private SharedPreferencesManager sharedPrefsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_view);
        
        initViews();
        initData();
        setupClickListeners();
        loadUserData();
    }
    
    private void initViews() {
        civAvatar = findViewById(R.id.civ_avatar);
        tvUsername = findViewById(R.id.tv_username);
        tvFirstName = findViewById(R.id.tv_first_name);
        tvLastName = findViewById(R.id.tv_last_name);
        tvPhoneNumber = findViewById(R.id.tv_phone_number);
        tvBio = findViewById(R.id.tv_bio);
        tvStatus = findViewById(R.id.tv_status);
        ivBack = findViewById(R.id.iv_back);
        ivMore = findViewById(R.id.iv_more);
    }
    
    private void initData() {
        avatarManager = AvatarManager.getInstance(this);
        sharedPrefsManager = new SharedPreferencesManager(this);
        
        Intent intent = getIntent();
        if (intent.hasExtra("user")) {
            try {
                String userJson = intent.getStringExtra("user");
                JSONObject userJsonObj = new JSONObject(userJson);
                otherUser = User.fromJson(userJsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else if (intent.hasExtra("username")) {
            // Fallback: fetch by username from server
            String username = intent.getStringExtra("username");
            fetchUserByUsername(username);
        } else {
            Toast.makeText(this, "No user data provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }
    
    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
        
        civAvatar.setOnClickListener(v -> showAvatarZoom());
        
        ivMore.setOnClickListener(v -> showMoreOptions());
    }
    
    private void loadUserData() {
        if (otherUser == null) return;
        
        // Load avatar - handle URL like ProfileActivity
        if (otherUser.getAvatar() != null && !otherUser.getAvatar().isEmpty()) {
            String avatarUrl = otherUser.getAvatar();
            android.util.Log.d("ProfileViewActivity", "Original avatar URL: " + avatarUrl);
            
            // If it's a relative URL, prepend the server base URL
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                android.util.Log.d("ProfileViewActivity", "Constructed full URL: " + avatarUrl);
            }
            
            try {
                // Try AvatarManager first
                avatarManager.loadAvatar(avatarUrl, civAvatar, R.drawable.ic_profile_placeholder);
                
                // Backup: Also try Picasso directly (like ProfileActivity)
                com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(civAvatar);
                        
            } catch (Exception e) {
                android.util.Log.e("ProfileViewActivity", "Error loading avatar: " + e.getMessage());
                // Fallback to direct Picasso load
                try {
                    com.squareup.picasso.Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(civAvatar);
                } catch (Exception e2) {
                    android.util.Log.e("ProfileViewActivity", "Picasso also failed: " + e2.getMessage());
                    civAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        } else {
            civAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // Set user information
        tvUsername.setText(otherUser.getUsername() != null ? otherUser.getUsername() : "N/A");
        tvFirstName.setText(otherUser.getFirstName() != null ? otherUser.getFirstName() : "N/A");
        tvLastName.setText(otherUser.getLastName() != null ? otherUser.getLastName() : "N/A");
        tvPhoneNumber.setText(otherUser.getPhoneNumber() != null ? otherUser.getPhoneNumber() : "N/A");
        tvBio.setText(otherUser.getBio() != null ? otherUser.getBio() : "No bio available");
        tvStatus.setText(otherUser.getStatusText());
    }
    
    private void showAvatarZoom() {
        if (otherUser == null || otherUser.getAvatar() == null || otherUser.getAvatar().isEmpty()) {
            Toast.makeText(this, "No avatar to display", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_zoom, null);
        
        CircleImageView zoomedAvatar = dialogView.findViewById(R.id.civ_zoomed_avatar);
        
        // Load the same avatar in zoomed view - handle URL like ProfileActivity
        String avatarUrl = otherUser.getAvatar();
        // If it's a relative URL, prepend the server base URL
        if (!avatarUrl.startsWith("http")) {
            avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                       ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
        }
        
        try {
            // Try AvatarManager first
            avatarManager.loadAvatar(avatarUrl, zoomedAvatar, R.drawable.ic_profile_placeholder);
            
            // Backup: Also try Picasso directly
            com.squareup.picasso.Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(zoomedAvatar);
                    
        } catch (Exception e) {
            android.util.Log.e("ProfileViewActivity", "Error loading zoomed avatar: " + e.getMessage());
            // Fallback to direct Picasso load
            try {
                com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(zoomedAvatar);
            } catch (Exception e2) {
                android.util.Log.e("ProfileViewActivity", "Picasso also failed: " + e2.getMessage());
                zoomedAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
        
        builder.setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }
    
    private void showMoreOptions() {
        String[] options = {"Send Message", "Call", "Video Call", "Block User"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            // Send Message - navigate to private chat
                            navigateToPrivateChat();
                            break;
                        case 1:
                            Toast.makeText(this, "Voice call feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            Toast.makeText(this, "Video call feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            Toast.makeText(this, "Block user feature coming soon", Toast.LENGTH_SHORT).show();
                            break;
                    }
                });
        builder.show();
    }
    
    private void navigateToPrivateChat() {
        if (otherUser == null) return;
        
        Intent intent = new Intent(this, PrivateChatActivity.class);
        try {
            // Create a simple chat object for private chat
            JSONObject chatJson = new JSONObject();
            chatJson.put("type", "private");
            chatJson.put("name", otherUser.getDisplayName());
            
            intent.putExtra("chat", chatJson.toString());
            intent.putExtra("user", otherUser.toJson().toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Reload user data when returning to activity
        // This ensures profile changes are reflected immediately
        if (otherUser != null) {
            loadOtherUserData();
        }
    }
    
    private void loadOtherUserData() {
        if (otherUser == null) return;
        
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) return;
        
        // Use ApiClient to get updated user data
        com.example.chatappjava.network.ApiClient apiClient = new com.example.chatappjava.network.ApiClient();
        apiClient.authenticatedGet("/api/users/" + otherUser.getId(), token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                android.util.Log.e("ProfileViewActivity", "Failed to reload user data: " + e.getMessage());
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
                        org.json.JSONObject userData = jsonResponse.getJSONObject("data").getJSONObject("user");
                        
                        runOnUiThread(() -> {
                            try {
                                otherUser = User.fromJson(userData);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            loadUserData(); // Refresh the UI with updated data
                        });
                    } catch (Exception e) {
                        android.util.Log.e("ProfileViewActivity", "Error parsing user data: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        com.example.chatappjava.network.ApiClient apiClient = new com.example.chatappjava.network.ApiClient();
        // Use search API, then pick exact match (case-insensitive)
        apiClient.authenticatedGet("/api/users/search?q=" + username, token, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileViewActivity.this, "Failed to load user", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileViewActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                try {
                    String body = response.body().string();
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    org.json.JSONArray arr = null;
                    if (json.has("data") && json.get("data") instanceof org.json.JSONObject) {
                        org.json.JSONObject data = json.getJSONObject("data");
                        if (data.has("users")) arr = data.getJSONArray("users");
                    }
                    if (arr == null && json.has("users")) {
                        arr = json.getJSONArray("users");
                    }
                    if (arr == null || arr.length() == 0) {
                        runOnUiThread(() -> {
                            Toast.makeText(ProfileViewActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                        return;
                    }
                    // Pick best match
                    org.json.JSONObject userObj = null;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject u = arr.getJSONObject(i);
                        String uname = u.optString("username", "");
                        if (uname.equalsIgnoreCase(username)) { userObj = u; break; }
                    }
                    if (userObj == null) userObj = arr.getJSONObject(0);
                    User fetched = User.fromJson(userObj);
                    otherUser = fetched;
                    runOnUiThread(() -> loadUserData());
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileViewActivity.this, "Error parsing user", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }
        });
    }
}
