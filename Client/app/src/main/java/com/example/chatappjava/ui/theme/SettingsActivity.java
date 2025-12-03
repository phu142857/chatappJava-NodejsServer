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
import com.example.chatappjava.utils.DatabaseManager;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvUserName, tvAppVersion;
    private ImageView ivBack;
    private Switch switchPushNotifications, switchSound, switchVibrate;
    
    private DatabaseManager databaseManager;
    private android.os.CountDownTimer deleteOtpCountDownTimer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        databaseManager = new DatabaseManager(this);
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
        String userName = databaseManager.getUserName();
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
        findViewById(R.id.option_delete_account).setOnClickListener(v -> startDeleteAccountOtpFlow());

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
            String currentPw = etCurrentPassword.getText().toString();
            String newPw = etNewPassword.getText().toString();
            String confirmPw = etConfirmPassword.getText().toString();

            if (currentPw.isEmpty()) { etCurrentPassword.setError("Enter current password"); etCurrentPassword.requestFocus(); return; }
            if (newPw.length() < 6) { etNewPassword.setError("At least 6 characters"); etNewPassword.requestFocus(); return; }
            if (!newPw.equals(confirmPw)) { etConfirmPassword.setError("Passwords do not match"); etConfirmPassword.requestFocus(); return; }

            String token = databaseManager.getToken();
            if (token == null) { Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show(); return; }

            ApiClient api = new ApiClient();
            android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, null, "Updating password...", true, false);
            api.changePassword(token, currentPw, newPw, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> { pd.dismiss(); Toast.makeText(SettingsActivity.this, "Network error", Toast.LENGTH_SHORT).show(); });
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        pd.dismiss();
                        if (response.isSuccessful()) {
                            Toast.makeText(SettingsActivity.this, "Password changed successfully", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(SettingsActivity.this, parseErrorMessage(resp), Toast.LENGTH_LONG).show();
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
        databaseManager.clearLoginInfo();
        
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showBlockedUsers() {
        Intent intent = new Intent(this, BlockedUsersActivity.class);
        startActivity(intent);
    }

    private void startDeleteAccountOtpFlow() {
        com.example.chatappjava.utils.DialogUtils.showConfirm(
                this,
                "Delete Account",
                "This will permanently delete your account. You'll receive an OTP via email to confirm.",
                "Continue",
                "Cancel",
                this::requestDeleteOtp,
                null,
                false
        );
    }

    private void requestDeleteOtp() {
        String token = databaseManager.getToken();
        if (token == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient api = new ApiClient();
        android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, null, "Requesting OTP...", true, false);
        api.requestDeleteAccountOTP(token, new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> { pd.dismiss(); Toast.makeText(SettingsActivity.this, "Network error", Toast.LENGTH_SHORT).show(); });
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String resp = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    pd.dismiss();
                    if (response.isSuccessful()) {
                        Toast.makeText(SettingsActivity.this, "OTP sent. Check your email.", Toast.LENGTH_SHORT).show();
                        showDeleteOtpDialog();
                    } else {
                        Toast.makeText(SettingsActivity.this, parseErrorMessage(resp), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private android.app.AlertDialog deleteOtpDialog;

    private void showDeleteOtpDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View view = inflater.inflate(com.example.chatappjava.R.layout.dialog_otp, null);
        
        EditText hiddenOtp = view.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
        TextView dialogTvTimer = view.findViewById(com.example.chatappjava.R.id.dialog_tv_timer);
        TextView tvError = view.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
        android.view.View circlesContainer = view.findViewById(com.example.chatappjava.R.id.otp_circles_container);
        TextView c1 = view.findViewById(com.example.chatappjava.R.id.otp_c1);
        TextView c2 = view.findViewById(com.example.chatappjava.R.id.otp_c2);
        TextView c3 = view.findViewById(com.example.chatappjava.R.id.otp_c3);
        TextView c4 = view.findViewById(com.example.chatappjava.R.id.otp_c4);
        TextView c5 = view.findViewById(com.example.chatappjava.R.id.otp_c5);
        TextView c6 = view.findViewById(com.example.chatappjava.R.id.otp_c6);
        android.widget.Button btnResend = view.findViewById(com.example.chatappjava.R.id.btn_resend_otp);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false);

        deleteOtpDialog = builder.create();
        if (deleteOtpDialog.getWindow() != null) {
            android.view.Window w = deleteOtpDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        deleteOtpDialog.setOnDismissListener(d -> { 
            if (deleteOtpCountDownTimer != null) deleteOtpCountDownTimer.cancel(); 
        });

        android.view.View.OnClickListener focusInput = v -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        };
        circlesContainer.setOnClickListener(focusInput);
        view.setOnClickListener(focusInput);

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
                    hiddenOtp.clearFocus();
                    confirmDeleteWithOtp(code);
                }
            }
        };
        hiddenOtp.addTextChangedListener(watcher);

        btnResend.setOnClickListener(v -> {
            String token = databaseManager.getToken();
            if (token == null) {
                Toast.makeText(SettingsActivity.this, "Not logged in", Toast.LENGTH_SHORT).show();
                return;
            }
            btnResend.setEnabled(false);
            ApiClient api = new ApiClient();
            api.requestDeleteAccountOTP(token, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> {
                        btnResend.setEnabled(true);
                        Toast.makeText(SettingsActivity.this, "Failed to resend: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        btnResend.setEnabled(true);
                        if (response.isSuccessful()) {
                            hiddenOtp.setText("");
                            tvError.setVisibility(android.view.View.GONE);
                            startDeleteOtpTimer(dialogTvTimer);
                            Toast.makeText(SettingsActivity.this, "OTP resent", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SettingsActivity.this, "Failed to resend OTP", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });

        startDeleteOtpTimer(dialogTvTimer);
        deleteOtpDialog.show();

        hiddenOtp.postDelayed(() -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void startDeleteOtpTimer(TextView tv) {
        if (deleteOtpCountDownTimer != null) deleteOtpCountDownTimer.cancel();
        tv.setText("01:00");
        deleteOtpCountDownTimer = new android.os.CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                long mm = seconds / 60;
                long ss = seconds % 60;
                tv.setText(String.format("%02d:%02d", mm, ss));
            }

            @Override
            public void onFinish() {
                tv.setText("Expired. Please request again.");
            }
        }.start();
    }

    private void confirmDeleteWithOtp(String otp) {
        String token = databaseManager.getToken();
        if (token == null) { Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show(); return; }
        ApiClient api = new ApiClient();
        android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, null, "Deleting account...", true, false);
        api.confirmDeleteAccountWithOTP(token, otp, new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                        TextView err = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                        EditText hid = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
                        if (err != null) { 
                            err.setText("Network error. Please try again."); 
                            err.setVisibility(android.view.View.VISIBLE); 
                        }
                        if (hid != null) { hid.setText(""); }
                        updateOtpCircles(0, 
                            deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c1),
                            deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c2),
                            deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c3),
                            deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c4),
                            deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c5),
                            deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c6));
                    } else {
                        Toast.makeText(SettingsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String resp = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    pd.dismiss();
                    if (response.isSuccessful()) {
                        if (deleteOtpCountDownTimer != null) deleteOtpCountDownTimer.cancel();
                        if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) deleteOtpDialog.dismiss();
                        Toast.makeText(SettingsActivity.this, "Account deleted", Toast.LENGTH_LONG).show();
                        databaseManager.clearLoginInfo();
                        Intent i = new Intent(SettingsActivity.this, LoginActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    } else {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(resp);
                            String message = json.optString("message", "Invalid OTP");
                            if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                                TextView err = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                                EditText hid = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
                                if (err != null) { err.setText("Invalid OTP"); err.setVisibility(android.view.View.VISIBLE); }
                                if (hid != null) { hid.setText(""); }
                                updateOtpCircles(0, 
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c1),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c2),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c3),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c4),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c5),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c6));
                            } else {
                                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                                TextView err = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                                EditText hid = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
                                if (err != null) { err.setText("Invalid OTP"); err.setVisibility(android.view.View.VISIBLE); }
                                if (hid != null) { hid.setText(""); }
                                updateOtpCircles(0, 
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c1),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c2),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c3),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c4),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c5),
                                    deleteOtpDialog.findViewById(com.example.chatappjava.R.id.otp_c6));
                            } else {
                                Toast.makeText(SettingsActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        });
    }

    private String parseErrorMessage(String resp) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(resp);
            return obj.optString("message", "Request failed");
        } catch (Exception e) {
            return "Request failed";
        }
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

        databaseManager.setOverrideServerIp(ip);
        databaseManager.setOverrideServerPort(port);
        databaseManager.setOverrideUseHttps(useHttps);
        databaseManager.setOverrideUseWss(useWss);

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
        databaseManager.setOverrideServerIp(null);
        databaseManager.setOverrideServerPort(-1);
        databaseManager.setOverrideUseHttps(false);
        databaseManager.setOverrideUseWss(false);

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

    @Override
    protected void onDestroy() {
        if (deleteOtpCountDownTimer != null) deleteOtpCountDownTimer.cancel();
        super.onDestroy();
    }
}