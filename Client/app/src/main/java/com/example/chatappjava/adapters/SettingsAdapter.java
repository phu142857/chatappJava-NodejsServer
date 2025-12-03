package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.ui.theme.BlockedUsersActivity;
import com.example.chatappjava.ui.theme.ProfileActivity;
import com.example.chatappjava.utils.DatabaseManager;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder> {
    
    private final Context context;
    private final DatabaseManager databaseManager;
    
    public SettingsAdapter(Context context) {
        this.context = context;
        this.databaseManager = new DatabaseManager(context);
    }
    
    @NonNull
    @Override
    public SettingsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settings_content, parent, false);
        return new SettingsViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SettingsViewHolder holder, int position) {
        holder.setupSettings(context, databaseManager);
    }
    
    @Override
    public int getItemCount() {
        return 1; // Only one item - the settings content
    }
    
    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        private LinearLayout optionProfile;
        private LinearLayout optionChangePassword;
        private LinearLayout optionLogout;
        private LinearLayout optionBlockedUsers;
        private LinearLayout optionDeleteAccount;
        private LinearLayout optionServerConfig;
        private LinearLayout optionResetSettings;
        private LinearLayout optionHelpSupport;
        private Switch switchPushNotifications;
        private Switch switchSound;
        private Switch switchVibrate;
        private TextView tvAppVersion;
        
        SettingsViewHolder(@NonNull View itemView) {
            super(itemView);
            optionProfile = itemView.findViewById(R.id.option_profile);
            optionChangePassword = itemView.findViewById(R.id.option_change_password);
            optionLogout = itemView.findViewById(R.id.option_logout);
            optionBlockedUsers = itemView.findViewById(R.id.option_blocked_users);
            optionDeleteAccount = itemView.findViewById(R.id.option_delete_account);
            optionServerConfig = itemView.findViewById(R.id.option_server_config);
            optionResetSettings = itemView.findViewById(R.id.option_reset_settings);
            optionHelpSupport = itemView.findViewById(R.id.option_help_support);
            switchPushNotifications = itemView.findViewById(R.id.switch_push_notifications);
            switchSound = itemView.findViewById(R.id.switch_sound);
            switchVibrate = itemView.findViewById(R.id.switch_vibrate);
            tvAppVersion = itemView.findViewById(R.id.tv_app_version);
        }
        
        void setupSettings(Context context, DatabaseManager databaseManager) {
            // Load app version
            if (tvAppVersion != null) {
                tvAppVersion.setText("1.0.0");
            }
            
            // Load notification settings
            if (switchPushNotifications != null) {
                switchPushNotifications.setChecked(true); // Default enabled
                switchPushNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Toast.makeText(context, "Push notifications " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                });
            }
            
            if (switchSound != null) {
                switchSound.setChecked(true); // Default enabled
                switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Toast.makeText(context, "Sound " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                });
            }
            
            if (switchVibrate != null) {
                switchVibrate.setChecked(true); // Default enabled
                switchVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Toast.makeText(context, "Vibrate " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                });
            }
            
            // Setup click listeners - delegate to SettingsActivity methods
            if (optionProfile != null) {
                optionProfile.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(context, ProfileActivity.class);
                    context.startActivity(intent);
                });
            }
            
            if (optionChangePassword != null) {
                optionChangePassword.setOnClickListener(v -> showChangePasswordDialog(context, databaseManager));
            }
            
            if (optionLogout != null) {
                optionLogout.setOnClickListener(v -> confirmLogout(context, databaseManager));
            }
            
            if (optionBlockedUsers != null) {
                optionBlockedUsers.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(context, BlockedUsersActivity.class);
                    context.startActivity(intent);
                });
            }
            
            if (optionDeleteAccount != null) {
                optionDeleteAccount.setOnClickListener(v -> startDeleteAccountOtpFlow(context, databaseManager));
            }
            
            if (optionServerConfig != null) {
                optionServerConfig.setOnClickListener(v -> showServerConfigDialog(context, databaseManager));
            }
            
            if (optionResetSettings != null) {
                optionResetSettings.setOnClickListener(v -> confirmResetSettings(context, databaseManager));
            }
            
            if (optionHelpSupport != null) {
                optionHelpSupport.setOnClickListener(v -> showHelpSupport(context));
            }
        }
        
        private void showChangePasswordDialog(Context context, DatabaseManager databaseManager) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            android.view.View dialogView = android.view.LayoutInflater.from(context).inflate(com.example.chatappjava.R.layout.dialog_change_password, null);
            
            android.widget.EditText etCurrentPassword = dialogView.findViewById(com.example.chatappjava.R.id.et_current_password);
            android.widget.EditText etNewPassword = dialogView.findViewById(com.example.chatappjava.R.id.et_new_password);
            android.widget.EditText etConfirmPassword = dialogView.findViewById(com.example.chatappjava.R.id.et_confirm_password);
            android.widget.Button btnSave = dialogView.findViewById(com.example.chatappjava.R.id.btn_save_password);
            android.widget.Button btnCancel = dialogView.findViewById(com.example.chatappjava.R.id.btn_cancel_password);
            
            android.app.AlertDialog dialog = builder.setView(dialogView).create();
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
                if (token == null) { Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show(); return; }
                
                ApiClient api = new ApiClient();
                android.app.ProgressDialog pd = android.app.ProgressDialog.show(context, null, "Updating password...", true, false);
                api.changePassword(token, currentPw, newPw, new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                        ((android.app.Activity) context).runOnUiThread(() -> { pd.dismiss(); Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show(); });
                    }
                    @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                        String resp = response.body() != null ? response.body().string() : "";
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            pd.dismiss();
                            if (response.isSuccessful()) {
                                Toast.makeText(context, "Password changed successfully", Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            } else {
                                Toast.makeText(context, parseErrorMessage(resp), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            });
            
            dialog.show();
        }
        
        private void confirmLogout(Context context, DatabaseManager databaseManager) {
            com.example.chatappjava.utils.DialogUtils.showConfirm(
                    context,
                    "Logout",
                    "Are you sure you want to logout?",
                    "Logout",
                    "Cancel",
                    () -> logout(context, databaseManager),
                    null,
                    false
            );
        }
        
        private void logout(Context context, DatabaseManager databaseManager) {
            databaseManager.clearLoginInfo();
            android.content.Intent intent = new android.content.Intent(context, com.example.chatappjava.ui.theme.LoginActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).finish();
            }
        }
        
        private void startDeleteAccountOtpFlow(Context context, DatabaseManager databaseManager) {
            com.example.chatappjava.utils.DialogUtils.showConfirm(
                    context,
                    "Delete Account",
                    "This will permanently delete your account. You'll receive an OTP via email to confirm.",
                    "Continue",
                    "Cancel",
                    () -> requestDeleteOtp(context, databaseManager),
                    null,
                    false
            );
        }
        
        private void requestDeleteOtp(Context context, DatabaseManager databaseManager) {
            String token = databaseManager.getToken();
            if (token == null) {
                Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show();
                return;
            }
            ApiClient api = new ApiClient();
            android.app.ProgressDialog pd = android.app.ProgressDialog.show(context, null, "Requesting OTP...", true, false);
            api.requestDeleteAccountOTP(token, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    ((android.app.Activity) context).runOnUiThread(() -> { pd.dismiss(); Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show(); });
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        pd.dismiss();
                        if (response.isSuccessful()) {
                            Toast.makeText(context, "OTP sent. Check your email.", Toast.LENGTH_SHORT).show();
                            showDeleteOtpDialog(context, databaseManager);
                        } else {
                            Toast.makeText(context, parseErrorMessage(resp), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
        
        private android.app.AlertDialog deleteOtpDialog;
        private android.os.CountDownTimer deleteOtpCountDownTimer;
        
        private void showDeleteOtpDialog(Context context, DatabaseManager databaseManager) {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
            android.view.View view = inflater.inflate(com.example.chatappjava.R.layout.dialog_otp, null);
            
            android.widget.EditText hiddenOtp = view.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
            android.widget.TextView dialogTvTimer = view.findViewById(com.example.chatappjava.R.id.dialog_tv_timer);
            android.widget.TextView tvError = view.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
            android.view.View circlesContainer = view.findViewById(com.example.chatappjava.R.id.otp_circles_container);
            android.widget.TextView c1 = view.findViewById(com.example.chatappjava.R.id.otp_c1);
            android.widget.TextView c2 = view.findViewById(com.example.chatappjava.R.id.otp_c2);
            android.widget.TextView c3 = view.findViewById(com.example.chatappjava.R.id.otp_c3);
            android.widget.TextView c4 = view.findViewById(com.example.chatappjava.R.id.otp_c4);
            android.widget.TextView c5 = view.findViewById(com.example.chatappjava.R.id.otp_c5);
            android.widget.TextView c6 = view.findViewById(com.example.chatappjava.R.id.otp_c6);
            android.widget.Button btnResend = view.findViewById(com.example.chatappjava.R.id.btn_resend_otp);

            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context)
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
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
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
                        confirmDeleteWithOtp(context, databaseManager, code);
                    }
                }
            };
            hiddenOtp.addTextChangedListener(watcher);

            btnResend.setOnClickListener(v -> {
                String token = databaseManager.getToken();
                if (token == null) {
                    Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show();
                    return;
                }
                btnResend.setEnabled(false);
                ApiClient api = new ApiClient();
                api.requestDeleteAccountOTP(token, new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            btnResend.setEnabled(true);
                            Toast.makeText(context, "Failed to resend: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            btnResend.setEnabled(true);
                            if (response.isSuccessful()) {
                                hiddenOtp.setText("");
                                tvError.setVisibility(android.view.View.GONE);
                                startDeleteOtpTimer(dialogTvTimer);
                                Toast.makeText(context, "OTP resent", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, "Failed to resend OTP", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            });

            startDeleteOtpTimer(dialogTvTimer);
            deleteOtpDialog.show();

            hiddenOtp.postDelayed(() -> {
                hiddenOtp.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        }
        
        private void startDeleteOtpTimer(android.widget.TextView tv) {
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
        
        private void confirmDeleteWithOtp(Context context, DatabaseManager databaseManager, String otp) {
            String token = databaseManager.getToken();
            if (token == null) { Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show(); return; }
            ApiClient api = new ApiClient();
            android.app.ProgressDialog pd = android.app.ProgressDialog.show(context, null, "Deleting account...", true, false);
            api.confirmDeleteAccountWithOTP(token, otp, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        pd.dismiss();
                        if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                            android.widget.TextView err = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                            android.widget.EditText hid = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
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
                            Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        pd.dismiss();
                        if (response.isSuccessful()) {
                            if (deleteOtpCountDownTimer != null) deleteOtpCountDownTimer.cancel();
                            if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) deleteOtpDialog.dismiss();
                            Toast.makeText(context, "Account deleted", Toast.LENGTH_LONG).show();
                            databaseManager.clearLoginInfo();
                            android.content.Intent i = new android.content.Intent(context, com.example.chatappjava.ui.theme.LoginActivity.class);
                            i.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            context.startActivity(i);
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).finish();
                            }
                        } else {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(resp);
                                String message = json.optString("message", "Invalid OTP");
                                if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                                    android.widget.TextView err = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                                    android.widget.EditText hid = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
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
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ex) {
                                if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                                    android.widget.TextView err = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                                    android.widget.EditText hid = deleteOtpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
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
                                    Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
                }
            });
        }
        
        private void updateOtpCircles(int filled, android.widget.TextView... circles) {
            for (int i = 0; i < circles.length; i++) {
                circles[i].setBackgroundResource(i < filled ? com.example.chatappjava.R.drawable.otp_circle_filled : com.example.chatappjava.R.drawable.otp_circle_empty);
            }
        }
        
        private void showServerConfigDialog(Context context, DatabaseManager databaseManager) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            android.view.View dialogView = android.view.LayoutInflater.from(context).inflate(com.example.chatappjava.R.layout.dialog_server_config, null);
            
            android.widget.EditText etServerIp = dialogView.findViewById(com.example.chatappjava.R.id.et_server_ip);
            android.widget.EditText etServerPort = dialogView.findViewById(com.example.chatappjava.R.id.et_server_port);
            android.widget.CheckBox cbUseHttps = dialogView.findViewById(com.example.chatappjava.R.id.cb_use_https);
            android.widget.CheckBox cbUseWss = dialogView.findViewById(com.example.chatappjava.R.id.cb_use_wss);
            android.widget.Button btnSave = dialogView.findViewById(com.example.chatappjava.R.id.btn_save_server_config);
            android.widget.Button btnCancel = dialogView.findViewById(com.example.chatappjava.R.id.btn_cancel_server_config);
            
            // Load current settings
            etServerIp.setText(com.example.chatappjava.config.ServerConfig.getServerIp());
            etServerPort.setText(String.valueOf(com.example.chatappjava.config.ServerConfig.getServerPort()));
            cbUseHttps.setChecked(com.example.chatappjava.config.ServerConfig.isUsingHttps());
            cbUseWss.setChecked(com.example.chatappjava.config.ServerConfig.isUsingWss());
            
            android.app.AlertDialog dialog = builder.setView(dialogView).create();
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
                
                Toast.makeText(context, "Server settings saved. Please restart the app to apply.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
            
            dialog.show();
        }
        
        private void confirmResetSettings(Context context, DatabaseManager databaseManager) {
            new android.app.AlertDialog.Builder(context)
                    .setTitle("Reset Settings")
                    .setMessage("Are you sure you want to reset all settings to default? This will clear all your custom configurations.")
                    .setPositiveButton("Reset", (dialog, which) -> resetSettings(context, databaseManager))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        
        private void resetSettings(Context context, DatabaseManager databaseManager) {
            // Clear all override settings
            databaseManager.setOverrideServerIp(null);
            databaseManager.setOverrideServerPort(-1);
            databaseManager.setOverrideUseHttps(false);
            databaseManager.setOverrideUseWss(false);
            
            Toast.makeText(context, "Settings reset to default. Please restart the app to apply.", Toast.LENGTH_LONG).show();
        }
        
        private void showHelpSupport(Context context) {
            new android.app.AlertDialog.Builder(context)
                    .setTitle("Help & Support")
                    .setMessage("For help and support, please contact:\n\n" +
                               "Email: support@chatapp.com\n" +
                               "Phone: +1 (555) 123-4567\n\n" +
                               "We're here to help!")
                    .setPositiveButton("OK", null)
                    .show();
        }
        
        private String parseErrorMessage(String resp) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(resp);
                return obj.optString("message", "Request failed");
            } catch (Exception e) {
                return "Request failed";
            }
        }
    }
}

