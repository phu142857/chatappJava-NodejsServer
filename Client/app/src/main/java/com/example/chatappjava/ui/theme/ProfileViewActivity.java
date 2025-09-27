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
        
        // Load avatar
        if (otherUser.getAvatar() != null && !otherUser.getAvatar().isEmpty()) {
            avatarManager.loadAvatar(
                otherUser.getAvatar(), 
                civAvatar, 
                R.drawable.ic_profile_placeholder
            );
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
        
        // Load the same avatar in zoomed view
        avatarManager.loadAvatar(
            otherUser.getAvatar(), 
            zoomedAvatar, 
            R.drawable.ic_profile_placeholder
        );
        
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
}
