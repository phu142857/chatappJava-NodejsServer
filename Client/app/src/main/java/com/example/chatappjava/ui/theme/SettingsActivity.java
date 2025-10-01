package com.example.chatappjava.ui.theme;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.utils.SharedPreferencesManager;

public class SettingsActivity extends AppCompatActivity {

    private EditText etServerIp, etServerPort;
    private CheckBox cbUseHttps, cbUseWss;
    private Button btnSave, btnReset, btnChangePassword, btnLogout, btnDeleteAccount;
    private EditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private SharedPreferencesManager sharedPrefsManager;
    private LinearLayout sectionServerContent, sectionPasswordContent, sectionAccountContent;
    private ImageView ivServerChevron, ivPasswordChevron, ivAccountChevron;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPrefsManager = new SharedPreferencesManager(this);
        initViews();
        loadCurrentSettings();
        setupListeners();
    }

    private void initViews() {
        etServerIp = findViewById(R.id.et_server_ip);
        etServerPort = findViewById(R.id.et_server_port);
        cbUseHttps = findViewById(R.id.cb_use_https);
        cbUseWss = findViewById(R.id.cb_use_wss);
        btnSave = findViewById(R.id.btn_save_settings);
        btnReset = findViewById(R.id.btn_reset_settings);
        btnChangePassword = findViewById(R.id.btn_change_password);
        btnLogout = findViewById(R.id.btn_logout);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);
        etCurrentPassword = findViewById(R.id.et_current_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        sectionServerContent = findViewById(R.id.section_server_content);
        sectionPasswordContent = findViewById(R.id.section_password_content);
        sectionAccountContent = findViewById(R.id.section_account_content);
        ivServerChevron = findViewById(R.id.iv_server_chevron);
        ivPasswordChevron = findViewById(R.id.iv_password_chevron);
        ivAccountChevron = findViewById(R.id.iv_account_chevron);

        // Only allow digits in port
        etServerPort.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                if (!Character.isDigit(source.charAt(i))) {
                    return "";
                }
            }
            return null;
        }});
    }

    private void loadCurrentSettings() {
        String ip = sharedPrefsManager.getOverrideServerIp();
        int port = sharedPrefsManager.getOverrideServerPort();
        Boolean https = sharedPrefsManager.getOverrideUseHttps();
        Boolean wss = sharedPrefsManager.getOverrideUseWss();

        etServerIp.setText(ip != null && !ip.isEmpty() ? ip : ServerConfig.getServerIp());
        etServerPort.setText(String.valueOf(port > 0 ? port : ServerConfig.getServerPort()));
        cbUseHttps.setChecked(https != null ? https : ServerConfig.isUsingHttps());
        cbUseWss.setChecked(wss != null ? wss : ServerConfig.isUsingWss());
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveSettings());

        btnReset.setOnClickListener(v -> {
            sharedPrefsManager.clearServerOverrides();
            loadCurrentSettings();
            Toast.makeText(SettingsActivity.this, "Restored default configuration", Toast.LENGTH_SHORT).show();
        });

        btnChangePassword.setOnClickListener(v -> changePassword());

        btnLogout.setOnClickListener(v -> performLogout());

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        // Accordions: toggle headers
        findViewById(R.id.section_server_header).setOnClickListener(v -> toggleSection(sectionServerContent, ivServerChevron));
        findViewById(R.id.section_password_header).setOnClickListener(v -> toggleSection(sectionPasswordContent, ivPasswordChevron));
        findViewById(R.id.section_account_header).setOnClickListener(v -> toggleSection(sectionAccountContent, ivAccountChevron));
    }

    private void saveSettings() {
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();
        boolean useHttps = cbUseHttps.isChecked();
        boolean useWss = cbUseWss.isChecked();

        if (ip.isEmpty()) {
            etServerIp.setError("Vui lòng nhập IP server");
            etServerIp.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etServerPort.setError("Cổng không hợp lệ (1-65535)");
            etServerPort.requestFocus();
            return;
        }

        sharedPrefsManager.setOverrideServerIp(ip);
        sharedPrefsManager.setOverrideServerPort(port);
        sharedPrefsManager.setOverrideUseHttps(useHttps);
        sharedPrefsManager.setOverrideUseWss(useWss);

        Toast.makeText(this, "Settings saved. Please restart the app to apply.", Toast.LENGTH_LONG).show();
        finish();
    }

    private void toggleSection(LinearLayout content, ImageView chevron) {
        if (content.getVisibility() == View.VISIBLE) {
            content.setVisibility(View.GONE);
            chevron.setRotation(180f);
        } else {
            content.setVisibility(View.VISIBLE);
            chevron.setRotation(0f);
        }
    }

    private void changePassword() {
        String current = etCurrentPassword.getText().toString();
        String newer = etNewPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (current.isEmpty()) {
            etCurrentPassword.setError("Please enter your current password");
            etCurrentPassword.requestFocus();
            return;
        }
        if (newer.length() < 6) {
            etNewPassword.setError("New password must be at least 6 characters");
            etNewPassword.requestFocus();
            return;
        }
        if (!newer.equals(confirm)) {
            etConfirmPassword.setError("Password confirmation does not match");
            etConfirmPassword.requestFocus();
            return;
        }

        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        findViewById(R.id.btn_change_password).setEnabled(false);
        new com.example.chatappjava.network.ApiClient().changePassword(token, current, newer, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    findViewById(R.id.btn_change_password).setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    findViewById(R.id.btn_change_password).setEnabled(true);
                    if (response.code() == 200) {
                        Toast.makeText(SettingsActivity.this, "Password changed successfully. Please log in again.", Toast.LENGTH_LONG).show();
                        // Logout locally and navigate to login
                        forceLogoutAndNavigate();
                    } else {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            String msg = json.optString("message", "Failed to change password");
                            Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {
                            Toast.makeText(SettingsActivity.this, "Failed to change password", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void performLogout() {
        String token = sharedPrefsManager.getToken();
        // Call logout API (do not wait for success)
        if (token != null && !token.isEmpty()) {
            new com.example.chatappjava.network.ApiClient().logout(token, new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {}

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) { }
            });
        }
        forceLogoutAndNavigate();
    }

    private void forceLogoutAndNavigate() {
        // Disconnect socket and clear login info
        com.example.chatappjava.ChatApplication.getInstance().disconnectSocket();
        sharedPrefsManager.clearLoginInfo();
        navigateToLogin();
    }

    private void navigateToLogin() {
        android.content.Intent intent = new android.content.Intent(this, LoginActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDeleteAccountDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Account");
        builder.setMessage("This action cannot be undone. All your data will be permanently deleted. Please enter your password to confirm.");

        // Create input field for password
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Enter your password");
        passwordInput.setPadding(50, 20, 50, 20);

        builder.setView(passwordInput);

        builder.setPositiveButton("Delete Account", (dialog, which) -> {
            String password = passwordInput.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(SettingsActivity.this, "Please enter your password", Toast.LENGTH_SHORT).show();
                return;
            }
            performDeleteAccount(password);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.setCancelable(true);
        builder.show();
    }

    private void performDeleteAccount(String password) {
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        btnDeleteAccount.setEnabled(false);
        new com.example.chatappjava.network.ApiClient().deleteAccount(token, password, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    btnDeleteAccount.setEnabled(true);
                    Toast.makeText(SettingsActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    btnDeleteAccount.setEnabled(true);
                    if (response.code() == 200) {
                        Toast.makeText(SettingsActivity.this, "Account deleted successfully", Toast.LENGTH_LONG).show();
                        // Clear all data and navigate to login
                        forceLogoutAndNavigate();
                    } else {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            String msg = json.optString("message", "Failed to delete account");
                            Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {
                            Toast.makeText(SettingsActivity.this, "Failed to delete account", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }
}


