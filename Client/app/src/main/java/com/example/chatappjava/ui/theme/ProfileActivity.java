package com.example.chatappjava.ui.theme;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.cardview.widget.CardView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.squareup.picasso.Picasso;
import com.github.chrisbanes.photoview.PhotoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ProfileActivity extends AppCompatActivity {

    private CircleImageView civAvatar;
    private TextView tvChangeAvatar;
    private EditText etUsername, etFirstName, etLastName, etPhoneNumber, etBio;
    private TextView tvSave;
    private ProgressBar progressBar;
    private ImageView ivBack;

    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefsManager;
    private User currentUser;
    private boolean hasChanges = false;
    private Uri selectedImageUri;
    
    // Image picker launcher
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private AlertDialog imageSelectDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeViews();
        initializeServices();
        setupImagePicker();
        setupClickListeners();
        setupTextWatchers();
        loadUserProfile();
    }

    private void initializeViews() {
        civAvatar = findViewById(R.id.civ_avatar);
        tvChangeAvatar = findViewById(R.id.tv_change_avatar);
        etUsername = findViewById(R.id.et_username);
        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etPhoneNumber = findViewById(R.id.et_phone_number);
        etBio = findViewById(R.id.et_bio);
        tvSave = findViewById(R.id.tv_save);
        progressBar = findViewById(R.id.progress_bar);
        ivBack = findViewById(R.id.iv_back);
    }

    private void initializeServices() {
        apiClient = new ApiClient();
        sharedPrefsManager = new SharedPreferencesManager(this);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            selectedImageUri = data.getData();
                            if (selectedImageUri != null) {
                                // Display the selected image
                                civAvatar.setImageURI(selectedImageUri);
                                hasChanges = true;
                                updateSaveButtonVisibility();
                                Toast.makeText(ProfileActivity.this, "Avatar selected. Click save to upload.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                        Toast.makeText(ProfileActivity.this, ImagePicker.getError(result.getData()), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> {
            if (hasChanges) {
                showUnsavedChangesDialog();
            } else {
                finish();
            }
        });

        tvSave.setOnClickListener(v -> saveProfile());

        tvChangeAvatar.setOnClickListener(v -> {
            showImagePickerOptions();
        });

        // Zoom avatar on tap
        civAvatar.setOnClickListener(v -> showAvatarZoom());
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkForChanges();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        // Special TextWatcher for username with real-time validation
        TextWatcher usernameWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkForChanges();
            }

            @Override
            public void afterTextChanged(Editable s) {
                String username = s.toString().trim();
                if (!username.isEmpty() && !username.equals(currentUser != null ? currentUser.getUsername() : "")) {
                    // Real-time validation for username
                    if (!isValidUsername(username)) {
                        String errorMessage = getUsernameValidationError(username);
                        etUsername.setError(errorMessage);
                    } else {
                        etUsername.setError(null);
                    }
                } else {
                    etUsername.setError(null);
                }
            }
        };

        etUsername.addTextChangedListener(usernameWatcher);
        etFirstName.addTextChangedListener(textWatcher);
        etLastName.addTextChangedListener(textWatcher);
        etPhoneNumber.addTextChangedListener(textWatcher);
        etBio.addTextChangedListener(textWatcher);
    }

    private void checkForChanges() {
        if (currentUser == null) return;

        boolean changed = false;

        // Check username (only if not empty)
        String newUsername = etUsername.getText().toString().trim();
        if (!newUsername.isEmpty() && !newUsername.equals(currentUser.getUsername())) {
            changed = true;
        }

        // Check first name (including clearing fields)
        String newFirstName = etFirstName.getText().toString().trim();
        String currentFirstName = currentUser.getFirstName() != null ? currentUser.getFirstName() : "";
        if (!newFirstName.equals(currentFirstName)) {
            changed = true;
        }

        // Check last name (including clearing fields)
        String newLastName = etLastName.getText().toString().trim();
        String currentLastName = currentUser.getLastName() != null ? currentUser.getLastName() : "";
        if (!newLastName.equals(currentLastName)) {
            changed = true;
        }

        // Check phone number (including clearing fields)
        String newPhoneNumber = etPhoneNumber.getText().toString().trim();
        String currentPhoneNumber = currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "";
        if (!newPhoneNumber.equals(currentPhoneNumber)) {
            changed = true;
        }

        // Check bio (including clearing fields)
        String newBio = etBio.getText().toString().trim();
        String currentBio = currentUser.getBio() != null ? currentUser.getBio() : "";
        if (!newBio.equals(currentBio)) {
            changed = true;
        }

        // Check if avatar has been selected
        if (selectedImageUri != null) {
            changed = true;
        }

        hasChanges = changed;
        updateSaveButtonVisibility();
    }

    private void updateSaveButtonVisibility() {
        if (hasChanges) {
            tvSave.setVisibility(View.VISIBLE);
        } else {
            tvSave.setVisibility(View.GONE);
        }
    }

    private void loadUserProfile() {
        showLoading(true);

        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiClient.getMe(token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(ProfileActivity.this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    showLoading(false);
                    handleLoadProfileResponse(response.code(), responseBody);
                });
            }
        });
    }

    private void handleLoadProfileResponse(int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (statusCode == 200) {
                JSONObject userData = jsonResponse.getJSONObject("data").getJSONObject("user");
                currentUser = User.fromJson(userData);
                populateFields();
            } else {
                String message = jsonResponse.optString("message", "Failed to load profile");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing profile data", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateFields() {
        if (currentUser == null) return;

        etUsername.setText(currentUser.getUsername());
        etFirstName.setText(currentUser.getFirstName() != null ? currentUser.getFirstName() : "");
        etLastName.setText(currentUser.getLastName() != null ? currentUser.getLastName() : "");
        etPhoneNumber.setText(currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "");
        etBio.setText(currentUser.getBio() != null ? currentUser.getBio() : "");

        // Load avatar
        if (currentUser.getAvatar() != null && !currentUser.getAvatar().isEmpty()) {
            String avatarUrl = currentUser.getAvatar();
            // If it's a relative URL, prepend the server base URL
            if (!avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + avatarUrl;
            }
            Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_person_avatar)
                    .error(R.drawable.ic_person_avatar)
                    .into(civAvatar);
        } else {
            civAvatar.setImageResource(R.drawable.ic_person_avatar);
        }

        hasChanges = false;
        updateSaveButtonVisibility();
    }

    private void showAvatarZoom() {
        // Prefer selected image if user just picked one
        String avatarUrl = null;
        if (selectedImageUri != null && !Uri.EMPTY.equals(selectedImageUri)) {
            // Show local selected image
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_image_zoom, null);
            PhotoView zoomImage = dialogView.findViewById(R.id.iv_zoom_image);
            ImageView ivClose = dialogView.findViewById(R.id.iv_close);
            try {
                zoomImage.setImageURI(selectedImageUri);
            } catch (Exception ignored) {}
            builder.setView(dialogView);
            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                android.view.Window w = dialog.getWindow();
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
            if (ivClose != null) ivClose.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            return;
        }

        if (currentUser == null || currentUser.getAvatar() == null || currentUser.getAvatar().isEmpty()) {
            Toast.makeText(this, "No avatar to display", Toast.LENGTH_SHORT).show();
            return;
        }

        avatarUrl = currentUser.getAvatar();
        if (!avatarUrl.startsWith("http")) {
            avatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + avatarUrl;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_image_zoom, null);
        PhotoView zoomImage = dialogView.findViewById(R.id.iv_zoom_image);
        ImageView ivClose = dialogView.findViewById(R.id.iv_close);
        try {
            Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(zoomImage);
        } catch (Exception e) {
            zoomImage.setImageResource(R.drawable.ic_profile_placeholder);
        }
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        if (ivClose != null) ivClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void saveProfile() {
        if (currentUser == null) return;

        showLoading(true);

        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        // If avatar is selected or removed, handle it first
        if (selectedImageUri != null) {
            if (selectedImageUri.equals(Uri.EMPTY)) {
                // Avatar removal
                removeAvatarFromServer(token);
            } else {
                // Avatar upload
                uploadAvatar(token);
            }
            return;
        }

        // Otherwise, update profile data as usual
        updateProfileData(token);
    }

    private void uploadAvatar(String token) {
        try {
            // Convert URI to File
            File imageFile = new File(selectedImageUri.getPath());
            
            apiClient.uploadAvatar(token, imageFile, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this, "Failed to upload avatar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        if (response.code() == 200) {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                String avatarUrl = jsonResponse.getJSONObject("data").getString("avatarUrl");
                                
                                // Update current user with new avatar URL
                                currentUser.setAvatar(avatarUrl);
                                
                                // Continue with profile update
                                updateProfileData(token);
                                
                            } catch (JSONException e) {
                                e.printStackTrace();
                                showLoading(false);
                                Toast.makeText(ProfileActivity.this, "Error processing avatar response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            showLoading(false);
                            Toast.makeText(ProfileActivity.this, "Failed to upload avatar", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            showLoading(false);
            Toast.makeText(this, "Error preparing avatar for upload", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeAvatarFromServer(String token) {
        try {
            JSONObject profileData = new JSONObject();
            profileData.put("avatar", ""); // Empty string to remove avatar
            
            apiClient.updateProfile(token, profileData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this, "Failed to remove avatar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        showLoading(false);
                        handleSaveProfileResponse(response.code(), responseBody);
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            showLoading(false);
            Toast.makeText(this, "Error preparing avatar removal", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateProfileData(String token) {
        try {
            JSONObject profileData = new JSONObject();
            
            // Only include fields that have changed or are not empty
            String newUsername = etUsername.getText().toString().trim();
            if (!newUsername.isEmpty() && !newUsername.equals(currentUser.getUsername())) {
                // Validate username format before sending to server
                if (!isValidUsername(newUsername)) {
                    showLoading(false);
                    String errorMessage = getUsernameValidationError(newUsername);
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    etUsername.setError(errorMessage);
                    etUsername.requestFocus();
                    return;
                }
                profileData.put("username", newUsername);
            }
            
            JSONObject profile = new JSONObject();
            boolean hasProfileChanges = false;
            
            // Check each profile field and include if changed (including clearing fields)
            String newFirstName = etFirstName.getText().toString().trim();
            String currentFirstName = currentUser.getFirstName() != null ? currentUser.getFirstName() : "";
            if (!newFirstName.equals(currentFirstName)) {
                profile.put("firstName", newFirstName);
                hasProfileChanges = true;
            }
            
            String newLastName = etLastName.getText().toString().trim();
            String currentLastName = currentUser.getLastName() != null ? currentUser.getLastName() : "";
            if (!newLastName.equals(currentLastName)) {
                profile.put("lastName", newLastName);
                hasProfileChanges = true;
            }
            
            String newPhoneNumber = etPhoneNumber.getText().toString().trim();
            String currentPhoneNumber = currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "";
            if (!newPhoneNumber.equals(currentPhoneNumber)) {
                // Validate phone number if not empty
                if (!newPhoneNumber.isEmpty() && !isValidPhoneNumber(newPhoneNumber)) {
                    showLoading(false);
                    Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
                    return;
                }
                profile.put("phoneNumber", newPhoneNumber);
                hasProfileChanges = true;
            }
            
            String newBio = etBio.getText().toString().trim();
            String currentBio = currentUser.getBio() != null ? currentUser.getBio() : "";
            if (!newBio.equals(currentBio)) {
                profile.put("bio", newBio);
                hasProfileChanges = true;
            }
            
            // Only include profile object if there are changes
            if (hasProfileChanges) {
                profileData.put("profile", profile);
            }
            
            // Check if there are any changes to send
            if (profileData.length() == 0 && selectedImageUri == null) {
                showLoading(false);
                Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
                return;
            }

            apiClient.updateProfile(token, profileData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        showLoading(false);
                        handleSaveProfileResponse(response.code(), responseBody);
                    });
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
            showLoading(false);
            Toast.makeText(this, "Error preparing profile data", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSaveProfileResponse(int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (statusCode == 200) {
                JSONObject userData = jsonResponse.getJSONObject("data").getJSONObject("user");
                currentUser = User.fromJson(userData);
                
                // Update shared preferences with new user info
                sharedPrefsManager.saveLoginInfo(sharedPrefsManager.getToken(), userData.toString());
                
                // Clear selected image URI since it's been uploaded
                selectedImageUri = null;
                
                // Refresh the UI to show updated avatar
                populateFields();
                
                hasChanges = false;
                updateSaveButtonVisibility();
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
            } else {
                String message = jsonResponse.optString("message", "Failed to update profile");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImagePickerOptions() {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_attachment_select, null);
        
        // Get views
        LinearLayout cameraOption = dialogView.findViewById(R.id.option_camera);
        LinearLayout galleryOption = dialogView.findViewById(R.id.option_gallery);
        // Hide generic file upload for avatar change
        View fileOption = dialogView.findViewById(R.id.option_file);
        if (fileOption != null) fileOption.setVisibility(View.GONE);
        LinearLayout cancelButton = dialogView.findViewById(R.id.btn_cancel);
        LinearLayout removeAvatarCard = dialogView.findViewById(R.id.option_remove_avatar);
        LinearLayout removeAvatarButton = dialogView.findViewById(R.id.btn_remove_avatar);
        
        // Show/hide remove avatar option based on current avatar
        boolean hasAvatar = currentUser != null && currentUser.getAvatar() != null && !currentUser.getAvatar().isEmpty();
        removeAvatarCard.setVisibility(hasAvatar ? View.VISIBLE : View.GONE);
        
        // Set click listeners
        cameraOption.setOnClickListener(v -> {
            ImagePicker.with(this)
                    .cameraOnly()
                    .crop()
                    .compress(1024)
                    .maxResultSize(512, 512)
                    .createIntent(intent -> {
                        imagePickerLauncher.launch(intent);
                        return null;
                    });
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        galleryOption.setOnClickListener(v -> {
            ImagePicker.with(this)
                    .galleryOnly()
                    .crop()
                    .compress(1024)
                    .maxResultSize(512, 512)
                    .createIntent(intent -> {
                        imagePickerLauncher.launch(intent);
                        return null;
                    });
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        removeAvatarButton.setOnClickListener(v -> {
            removeAvatar();
            if (imageSelectDialog != null) {
                imageSelectDialog.dismiss();
            }
        });
        
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                if (imageSelectDialog != null) {
                    imageSelectDialog.dismiss();
                }
            });
        }
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(true);
        
        imageSelectDialog = builder.create();
        
        // Set transparent background to avoid white area issues
        if (imageSelectDialog.getWindow() != null) {
            android.view.Window w = imageSelectDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        imageSelectDialog.show();
    }

    private void removeAvatar() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Avatar")
                .setMessage("Are you sure you want to remove your avatar?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    // Set a special flag to indicate avatar removal
                    selectedImageUri = Uri.EMPTY;
                    civAvatar.setImageResource(R.drawable.circle_background);
                    hasChanges = true;
                    updateSaveButtonVisibility();
                    Toast.makeText(this, "Avatar will be removed when you save", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUnsavedChangesDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Do you want to save them before leaving?")
                .setPositiveButton("Save", (dialog, which) -> {
                    saveProfile();
                })
                .setNegativeButton("Discard", (dialog, which) -> {
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false; // Empty username is not valid
        }
        
        // Check length
        if (username.length() < 3 || username.length() > 30) {
            return false;
        }
        
        // Check format: only letters, numbers, and underscores
        return username.matches("^[a-zA-Z0-9_]+$");
    }

    private String getUsernameValidationError(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "Username cannot be empty";
        }
        
        if (username.length() < 3) {
            return "Username must be at least 3 characters";
        }
        
        if (username.length() > 30) {
            return "Username cannot exceed 30 characters";
        }
        
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return "Username can only contain letters, numbers, and underscores (no spaces or special characters)";
        }
        
        return "Invalid username format";
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return true; // Empty is valid (allows clearing)
        }
        
        // Remove spaces, dashes, parentheses
        String cleaned = phoneNumber.replaceAll("[\\s\\-\\(\\)]", "");
        
        // Check if it's a valid phone number format
        // Allow: +1234567890, 1234567890, 0868788898, etc.
        return cleaned.matches("^[+]?[0-9]\\d{0,15}$");
    }
}
