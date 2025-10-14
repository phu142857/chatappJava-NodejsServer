package com.example.chatappjava.ui.theme;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvUserName, tvAppVersion;
    private ImageView ivBack;
    private Switch switchPushNotifications, switchSound, switchVibrate;
    
    private SharedPreferencesManager sharedPrefsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPrefsManager = new SharedPreferencesManager(this);
        initViews();
        loadCurrentSettings();
        setupClickListeners();
    }

    private void initViews() {
        tvUserName = findViewById(R.id.tv_user_name);
        tvAppVersion = findViewById(R.id.tv_app_version);
        ivBack = findViewById(R.id.iv_back);
        switchPushNotifications = findViewById(R.id.switch_push_notifications);
        switchSound = findViewById(R.id.switch_sound);
        switchVibrate = findViewById(R.id.switch_vibrate);
    }

    private void loadCurrentSettings() {
        // Load user name
        String userName = sharedPrefsManager.getUserName();
        if (userName != null && !userName.isEmpty()) {
            tvUserName.setText(userName);
        } else {
            tvUserName.setText("User");
        }

        // Load app version
        tvAppVersion.setText("1.0.0");

        // Load notification settings
        switchPushNotifications.setChecked(true); // Default enabled
        switchSound.setChecked(true); // Default enabled
        switchVibrate.setChecked(true); // Default enabled
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        // Account Settings
        findViewById(R.id.option_profile).setOnClickListener(v -> openProfile());
        findViewById(R.id.option_change_password).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.option_logout).setOnClickListener(v -> confirmLogout());

        // Privacy & Security
        findViewById(R.id.option_blocked_users).setOnClickListener(v -> showBlockedUsers());
        findViewById(R.id.option_delete_account).setOnClickListener(v -> confirmDeleteAccount());

        // Server Settings
        findViewById(R.id.option_server_config).setOnClickListener(v -> showServerConfigDialog());
        findViewById(R.id.option_reset_settings).setOnClickListener(v -> confirmResetSettings());

        // About
        findViewById(R.id.option_help_support).setOnClickListener(v -> showHelpSupport());

        // Notification switches
        switchPushNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Save notification preference
            Toast.makeText(this, "Push notifications " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Save sound preference
            Toast.makeText(this, "Sound " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Save vibrate preference
            Toast.makeText(this, "Vibrate " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        
        EditText etCurrentPassword = dialogView.findViewById(R.id.et_current_password);
        EditText etNewPassword = dialogView.findViewById(R.id.et_new_password);
        EditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);
        Button btnSave = dialogView.findViewById(R.id.btn_save_password);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_password);

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String currentPassword = etCurrentPassword.getText().toString().trim();
            String newPassword = etNewPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            String token = sharedPrefsManager.getToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "You are not logged in", Toast.LENGTH_SHORT).show();
                return;
            }

            new ApiClient().changePassword(token, currentPassword, newPassword, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    final boolean success = response.isSuccessful();
                    final String resp = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(SettingsActivity.this, "Password changed successfully", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
                        } else {
                            String message = "Failed to change password";
                            try {
                                org.json.JSONObject obj = new org.json.JSONObject(resp);
                                if (obj.has("message")) message = obj.optString("message");
                                else if (obj.has("error")) message = obj.optString("error");
                            } catch (Exception ignored) { }
                            Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });

        dialog.show();
    }

    private void confirmLogout() {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Logout",
                "Are you sure you want to logout?",
                "Logout",
                "Cancel",
                this::logout,
                null,
                false
        );
    }

    private void logout() {
        sharedPrefsManager.clearLoginInfo();
        
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showBlockedUsers() {
        Intent intent = new Intent(this, BlockedUsersActivity.class);
        startActivity(intent);
    }

    private void confirmDeleteAccount() {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Delete Account",
                "Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently lost.",
                "Delete",
                "Cancel",
                this::deleteAccount,
                null,
                false
        );
    }

    private void deleteAccount() {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "You are not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gửi OTP xoá tài khoản (endpoint riêng, cần token), sau đó mở dialog OTP
        new ApiClient().requestDeleteAccountOTP(token, new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String email = sharedPrefsManager.getUserEmail();
                runOnUiThread(() -> showDeleteWithOtpDialog(email));
            }
        });
    }

    private void showDeleteWithOtpDialog(String email) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_otp, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        android.widget.TextView tvMsg = dialogView.findViewById(R.id.tv_message);
        android.widget.EditText hiddenOtp = dialogView.findViewById(R.id.dialog_et_hidden_otp);
        android.widget.TextView tvTimer = dialogView.findViewById(R.id.dialog_tv_timer);
        android.widget.TextView tvError = dialogView.findViewById(R.id.dialog_tv_error);
        android.view.View circlesContainer = dialogView.findViewById(R.id.otp_circles_container);
        android.widget.TextView c1 = dialogView.findViewById(R.id.otp_c1);
        android.widget.TextView c2 = dialogView.findViewById(R.id.otp_c2);
        android.widget.TextView c3 = dialogView.findViewById(R.id.otp_c3);
        android.widget.TextView c4 = dialogView.findViewById(R.id.otp_c4);
        android.widget.TextView c5 = dialogView.findViewById(R.id.otp_c5);
        android.widget.TextView c6 = dialogView.findViewById(R.id.otp_c6);
        android.widget.Button btnResend = dialogView.findViewById(R.id.btn_resend_otp);

        tvTitle.setText("Confirm Account Deletion");
        tvMsg.setText("Enter the 6-digit code sent to your email.");

        androidx.appcompat.app.AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnResend.setOnClickListener(v -> {
            String token = sharedPrefsManager.getToken();
            if (token == null || token.isEmpty()) {
                android.widget.Toast.makeText(SettingsActivity.this, "You are not logged in", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            new ApiClient().requestDeleteAccountOTP(token, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(SettingsActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            hiddenOtp.setText("");
                            tvError.setVisibility(android.view.View.GONE);
                            android.widget.Toast.makeText(SettingsActivity.this, "OTP resent", android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(SettingsActivity.this, "Failed to resend OTP", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });

        android.view.View.OnClickListener focusInput = v -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        };
        circlesContainer.setOnClickListener(focusInput);
        dialogView.setOnClickListener(focusInput);

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String code = s.toString();
                updateOtpCircles(code.length(), c1, c2, c3, c4, c5, c6);
                if (code.length() > 0) {
                    tvError.setVisibility(android.view.View.GONE);
                }
                if (code.length() == 6) {
                    // Verify OTP then delete account
                    new ApiClient().verifyPasswordResetOTP(email, code, new okhttp3.Callback() {
                        @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            runOnUiThread(() -> android.widget.Toast.makeText(SettingsActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                        }
                        @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            String body = response.body() != null ? response.body().string() : "";
                            runOnUiThread(() -> {
                                if (response.isSuccessful()) {
                                    String token = sharedPrefsManager.getToken();
                                    if (token == null || token.isEmpty()) {
                                        android.widget.Toast.makeText(SettingsActivity.this, "You are not logged in", android.widget.Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    new ApiClient().deleteAccount(token, null, code, new okhttp3.Callback() {
                                        @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                                            runOnUiThread(() -> android.widget.Toast.makeText(SettingsActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                                        }
                                        @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                                            String resp = response.body() != null ? response.body().string() : "";
                                            runOnUiThread(() -> {
                                                if (response.isSuccessful()) {
                                                    dialog.dismiss();
                                                    android.widget.Toast.makeText(SettingsActivity.this, "Account deleted", android.widget.Toast.LENGTH_LONG).show();
                                                    sharedPrefsManager.clearLoginInfo();
                                                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                    startActivity(intent);
                                                    finish();
                                                } else {
                                                    try {
                                                        org.json.JSONObject json = new org.json.JSONObject(resp);
                                                        String msg = json.optString("message", "Failed to delete account");
                                                        tvError.setText(msg);
                                                        tvError.setVisibility(android.view.View.VISIBLE);
                                                        hiddenOtp.setText("");
                                                    } catch (Exception ex) {
                                                        tvError.setText("Failed to delete account");
                                                        tvError.setVisibility(android.view.View.VISIBLE);
                                                        hiddenOtp.setText("");
                                                    }
                                                }
                                            });
                                        }
                                    });
                                } else {
                                    tvError.setText("Invalid OTP");
                                    tvError.setVisibility(android.view.View.VISIBLE);
                                    hiddenOtp.setText("");
                                }
                            });
                        }
                    });
                }
            }
        };
        hiddenOtp.addTextChangedListener(watcher);

        new android.os.CountDownTimer(10 * 60 * 1000, 1000) {
            public void onTick(long ms) {
                long s = ms / 1000; long mm = s / 60; long ss = s % 60;
                tvTimer.setText(String.format("%02d:%02d", mm, ss));
            }
            public void onFinish() { tvTimer.setText("Expired. Request again."); }
        }.start();

        dialog.show();

        hiddenOtp.postDelayed(() -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void updateOtpCircles(int filled, android.widget.TextView... circles) {
        for (int i = 0; i < circles.length; i++) {
            circles[i].setBackgroundResource(i < filled ? R.drawable.otp_circle_filled : R.drawable.otp_circle_empty);
        }
    }

    private void showServerConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_server_config, null);
        
        EditText etServerIp = dialogView.findViewById(R.id.et_server_ip);
        EditText etServerPort = dialogView.findViewById(R.id.et_server_port);
        CheckBox cbUseHttps = dialogView.findViewById(R.id.cb_use_https);
        CheckBox cbUseWss = dialogView.findViewById(R.id.cb_use_wss);
        Button btnSave = dialogView.findViewById(R.id.btn_save_server_config);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_server_config);

        // Load current settings
        etServerIp.setText(ServerConfig.getServerIp());
        etServerPort.setText(String.valueOf(ServerConfig.getServerPort()));
        cbUseHttps.setChecked(ServerConfig.isUsingHttps());
        cbUseWss.setChecked(ServerConfig.isUsingWss());

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();
        boolean useHttps = cbUseHttps.isChecked();
        boolean useWss = cbUseWss.isChecked();

        if (ip.isEmpty()) {
                etServerIp.setError("Please enter server IP");
            etServerIp.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
                etServerPort.setError("Invalid port (1-65535)");
            etServerPort.requestFocus();
            return;
        }

        sharedPrefsManager.setOverrideServerIp(ip);
        sharedPrefsManager.setOverrideServerPort(port);
        sharedPrefsManager.setOverrideUseHttps(useHttps);
        sharedPrefsManager.setOverrideUseWss(useWss);

            Toast.makeText(this, "Server settings saved. Please restart the app to apply.", Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void confirmResetSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Settings")
                .setMessage("Are you sure you want to reset all settings to default? This will clear all your custom configurations.")
                .setPositiveButton("Reset", (dialog, which) -> resetSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetSettings() {
        // Clear all override settings
        sharedPrefsManager.setOverrideServerIp(null);
        sharedPrefsManager.setOverrideServerPort(-1);
        sharedPrefsManager.setOverrideUseHttps(false);
        sharedPrefsManager.setOverrideUseWss(false);

        Toast.makeText(this, "Settings reset to default. Please restart the app to apply.", Toast.LENGTH_LONG).show();
    }

    private void showHelpSupport() {
        new AlertDialog.Builder(this)
                .setTitle("Help & Support")
                .setMessage("For help and support, please contact:\n\n" +
                           "Email: support@chatapp.com\n" +
                           "Phone: +1 (555) 123-4567\n\n" +
                           "We're here to help!")
                .setPositiveButton("OK", null)
                .show();
    }
}