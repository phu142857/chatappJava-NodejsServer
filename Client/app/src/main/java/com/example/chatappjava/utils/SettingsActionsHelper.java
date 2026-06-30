package com.example.chatappjava.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.ui.theme.BlockedUsersActivity;
import com.example.chatappjava.ui.theme.LoginActivity;
import com.example.chatappjava.ui.theme.ProfileActivity;

import org.json.JSONObject;

import okhttp3.Callback;

public final class SettingsActionsHelper {

    private AlertDialog deleteOtpDialog;
    private CountDownTimer deleteOtpCountDownTimer;

    public void bind(View root, Context context, DatabaseManager databaseManager) {
        if (root == null || context == null || databaseManager == null) {
            return;
        }

        TextView tvAppVersion = root.findViewById(R.id.tv_app_version);
        Switch switchPush = root.findViewById(R.id.switch_push_notifications);
        Switch switchSound = root.findViewById(R.id.switch_sound);
        Switch switchVibrate = root.findViewById(R.id.switch_vibrate);

        if (tvAppVersion != null) {
            tvAppVersion.setText(R.string.settings_app_version_value);
        }

        NotificationSettingsHelper.bindSwitches(
                databaseManager,
                switchPush,
                switchSound,
                switchVibrate
        );

        View optionProfile = root.findViewById(R.id.option_profile);
        if (optionProfile != null) {
            optionProfile.setOnClickListener(v ->
                    context.startActivity(new Intent(context, ProfileActivity.class)));
        }

        View optionChangePassword = root.findViewById(R.id.option_change_password);
        if (optionChangePassword != null) {
            optionChangePassword.setOnClickListener(v -> showChangePasswordDialog(context, databaseManager));
        }

        View optionLogout = root.findViewById(R.id.option_logout);
        if (optionLogout != null) {
            optionLogout.setOnClickListener(v -> confirmLogout(context, databaseManager));
        }

        View optionBlocked = root.findViewById(R.id.option_blocked_users);
        if (optionBlocked != null) {
            optionBlocked.setOnClickListener(v ->
                    context.startActivity(new Intent(context, BlockedUsersActivity.class)));
        }

        View optionDelete = root.findViewById(R.id.option_delete_account);
        if (optionDelete != null) {
            optionDelete.setOnClickListener(v -> startDeleteAccountOtpFlow(context, databaseManager));
        }

        View optionServer = root.findViewById(R.id.option_server_config);
        if (optionServer != null) {
            optionServer.setOnClickListener(v -> showServerConfigDialog(context, databaseManager));
        }

        View optionReset = root.findViewById(R.id.option_reset_settings);
        if (optionReset != null) {
            optionReset.setOnClickListener(v ->
                    confirmResetSettings(context, databaseManager, switchPush, switchSound, switchVibrate));
        }

        View optionHelp = root.findViewById(R.id.option_help_support);
        if (optionHelp != null) {
            optionHelp.setOnClickListener(v -> showHelpSupport(context));
        }
    }

    public void onDestroy() {
        if (deleteOtpCountDownTimer != null) {
            deleteOtpCountDownTimer.cancel();
            deleteOtpCountDownTimer = null;
        }
        if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
            deleteOtpDialog.dismiss();
        }
        deleteOtpDialog = null;
    }

    private void showChangePasswordDialog(Context context, DatabaseManager databaseManager) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null);

        EditText etCurrentPassword = dialogView.findViewById(R.id.et_current_password);
        EditText etNewPassword = dialogView.findViewById(R.id.et_new_password);
        EditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);
        Button btnSave = dialogView.findViewById(R.id.btn_save_password);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_password);

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String currentPw = etCurrentPassword.getText().toString();
            String newPw = etNewPassword.getText().toString();
            String confirmPw = etConfirmPassword.getText().toString();

            if (currentPw.isEmpty()) {
                etCurrentPassword.setError(context.getString(R.string.error_enter_current_password));
                etCurrentPassword.requestFocus();
                return;
            }
            if (newPw.length() < 6) {
                etNewPassword.setError(context.getString(R.string.error_password_min_length));
                etNewPassword.requestFocus();
                return;
            }
            if (!newPw.equals(confirmPw)) {
                etConfirmPassword.setError(context.getString(R.string.error_passwords_mismatch));
                etConfirmPassword.requestFocus();
                return;
            }

            String token = databaseManager.getToken();
            if (token == null) {
                Toast.makeText(context, context.getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
                return;
            }

            ApiClient api = new ApiClient();
            android.app.ProgressDialog pd = android.app.ProgressDialog.show(
                    context, null, context.getString(R.string.updating_password), true, false);
            api.changePassword(token, currentPw, newPw, new Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUi(context, () -> {
                        pd.dismiss();
                        Toast.makeText(context, context.getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String resp = response.body() != null ? response.body().string() : "";
                    runOnUi(context, () -> {
                        pd.dismiss();
                        if (response.isSuccessful()) {
                            Toast.makeText(context, context.getString(R.string.password_changed_success), Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(context, parseErrorMessage(context, resp), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        });

        dialog.show();
    }

    private void confirmLogout(Context context, DatabaseManager databaseManager) {
        DialogUtils.showConfirm(
                context,
                context.getString(R.string.dialog_logout_title),
                context.getString(R.string.dialog_logout_message),
                context.getString(R.string.settings_logout),
                context.getString(R.string.action_cancel),
                () -> logout(context, databaseManager),
                null,
                false
        );
    }

    private void logout(Context context, DatabaseManager databaseManager) {
        databaseManager.clearLoginInfo();
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
    }

    private void startDeleteAccountOtpFlow(Context context, DatabaseManager databaseManager) {
        DialogUtils.showConfirm(
                context,
                context.getString(R.string.dialog_delete_account_title),
                context.getString(R.string.dialog_delete_account_message),
                context.getString(R.string.action_continue),
                context.getString(R.string.action_cancel),
                () -> requestDeleteOtp(context, databaseManager),
                null,
                false
        );
    }

    private void requestDeleteOtp(Context context, DatabaseManager databaseManager) {
        String token = databaseManager.getToken();
        if (token == null) {
            Toast.makeText(context, context.getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient api = new ApiClient();
        android.app.ProgressDialog pd = android.app.ProgressDialog.show(
                context, null, context.getString(R.string.requesting_otp), true, false);
        api.requestDeleteAccountOTP(token, new Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUi(context, () -> {
                    pd.dismiss();
                    Toast.makeText(context, context.getString(R.string.error_network), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String resp = response.body() != null ? response.body().string() : "";
                runOnUi(context, () -> {
                    pd.dismiss();
                    if (response.isSuccessful()) {
                        Toast.makeText(context, context.getString(R.string.otp_sent), Toast.LENGTH_SHORT).show();
                        showDeleteOtpDialog(context, databaseManager);
                    } else {
                        Toast.makeText(context, parseErrorMessage(context, resp), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showDeleteOtpDialog(Context context, DatabaseManager databaseManager) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_otp, null);

        EditText hiddenOtp = view.findViewById(R.id.dialog_et_hidden_otp);
        TextView dialogTvTimer = view.findViewById(R.id.dialog_tv_timer);
        TextView tvError = view.findViewById(R.id.dialog_tv_error);
        View circlesContainer = view.findViewById(R.id.otp_circles_container);
        TextView c1 = view.findViewById(R.id.otp_c1);
        TextView c2 = view.findViewById(R.id.otp_c2);
        TextView c3 = view.findViewById(R.id.otp_c3);
        TextView c4 = view.findViewById(R.id.otp_c4);
        TextView c5 = view.findViewById(R.id.otp_c5);
        TextView c6 = view.findViewById(R.id.otp_c6);
        Button btnResend = view.findViewById(R.id.btn_resend_otp);

        deleteOtpDialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(false)
                .create();
        if (deleteOtpDialog.getWindow() != null) {
            deleteOtpDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        deleteOtpDialog.setOnDismissListener(d -> {
            if (deleteOtpCountDownTimer != null) {
                deleteOtpCountDownTimer.cancel();
            }
        });

        View.OnClickListener focusInput = v -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        };
        circlesContainer.setOnClickListener(focusInput);
        view.setOnClickListener(focusInput);

        hiddenOtp.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String code = s.toString();
                updateOtpCircles(code.length(), c1, c2, c3, c4, c5, c6);
                if (code.length() > 0) {
                    tvError.setVisibility(View.GONE);
                }
                if (code.length() == 6) {
                    hiddenOtp.clearFocus();
                    confirmDeleteWithOtp(context, databaseManager, code);
                }
            }
        });

        btnResend.setOnClickListener(v -> {
            String token = databaseManager.getToken();
            if (token == null) {
                Toast.makeText(context, context.getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
                return;
            }
            btnResend.setEnabled(false);
            new ApiClient().requestDeleteAccountOTP(token, new Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUi(context, () -> {
                        btnResend.setEnabled(true);
                        Toast.makeText(context,
                                context.getString(R.string.error_otp_resend_failed, e.getMessage()),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                    runOnUi(context, () -> {
                        btnResend.setEnabled(true);
                        if (response.isSuccessful()) {
                            hiddenOtp.setText("");
                            tvError.setVisibility(View.GONE);
                            startDeleteOtpTimer(dialogTvTimer);
                            Toast.makeText(context, context.getString(R.string.otp_resent), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, context.getString(R.string.error_otp_resend), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });

        startDeleteOtpTimer(dialogTvTimer);
        deleteOtpDialog.show();

        hiddenOtp.postDelayed(() -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private void startDeleteOtpTimer(TextView tv) {
        if (deleteOtpCountDownTimer != null) {
            deleteOtpCountDownTimer.cancel();
        }
        tv.setText(R.string.otp_timer_initial);
        deleteOtpCountDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                long mm = seconds / 60;
                long ss = seconds % 60;
                tv.setText(String.format("%02d:%02d", mm, ss));
            }

            @Override
            public void onFinish() {
                tv.setText(tv.getContext().getString(R.string.otp_expired));
            }
        }.start();
    }

    private void confirmDeleteWithOtp(Context context, DatabaseManager databaseManager, String otp) {
        String token = databaseManager.getToken();
        if (token == null) {
            Toast.makeText(context, context.getString(R.string.error_not_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient api = new ApiClient();
        android.app.ProgressDialog pd = android.app.ProgressDialog.show(
                context, null, context.getString(R.string.deleting_account), true, false);
        api.confirmDeleteAccountWithOTP(token, otp, new Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUi(context, () -> {
                    pd.dismiss();
                    showOtpError(context, context.getString(R.string.error_network_retry));
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String resp = response.body() != null ? response.body().string() : "";
                runOnUi(context, () -> {
                    pd.dismiss();
                    if (response.isSuccessful()) {
                        if (deleteOtpCountDownTimer != null) {
                            deleteOtpCountDownTimer.cancel();
                        }
                        if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
                            deleteOtpDialog.dismiss();
                        }
                        Toast.makeText(context, context.getString(R.string.account_deleted), Toast.LENGTH_LONG).show();
                        databaseManager.clearLoginInfo();
                        Intent i = new Intent(context, LoginActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(i);
                        if (context instanceof Activity) {
                            ((Activity) context).finish();
                        }
                    } else {
                        try {
                            JSONObject json = new JSONObject(resp);
                            String message = json.optString("message", context.getString(R.string.otp_invalid));
                            showOtpError(context, message);
                        } catch (Exception ex) {
                            showOtpError(context, context.getString(R.string.otp_invalid));
                        }
                    }
                });
            }
        });
    }

    private void showOtpError(Context context, String message) {
        if (deleteOtpDialog != null && deleteOtpDialog.isShowing()) {
            TextView err = deleteOtpDialog.findViewById(R.id.dialog_tv_error);
            EditText hid = deleteOtpDialog.findViewById(R.id.dialog_et_hidden_otp);
            if (err != null) {
                err.setText(message);
                err.setVisibility(View.VISIBLE);
            }
            if (hid != null) {
                hid.setText("");
            }
            updateOtpCircles(0,
                    deleteOtpDialog.findViewById(R.id.otp_c1),
                    deleteOtpDialog.findViewById(R.id.otp_c2),
                    deleteOtpDialog.findViewById(R.id.otp_c3),
                    deleteOtpDialog.findViewById(R.id.otp_c4),
                    deleteOtpDialog.findViewById(R.id.otp_c5),
                    deleteOtpDialog.findViewById(R.id.otp_c6));
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showServerConfigDialog(Context context, DatabaseManager databaseManager) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_server_config, null);

        EditText etServerIp = dialogView.findViewById(R.id.et_server_ip);
        EditText etServerPort = dialogView.findViewById(R.id.et_server_port);
        CheckBox cbUseHttps = dialogView.findViewById(R.id.cb_use_https);
        CheckBox cbUseWss = dialogView.findViewById(R.id.cb_use_wss);
        Button btnSave = dialogView.findViewById(R.id.btn_save_server_config);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_server_config);

        etServerIp.setText(ServerConfig.getServerIp());
        etServerPort.setText(String.valueOf(ServerConfig.getServerPort()));
        cbUseHttps.setChecked(ServerConfig.isUsingHttps());
        cbUseWss.setChecked(ServerConfig.isUsingWss());

        AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String ip = etServerIp.getText().toString().trim();
            String portStr = etServerPort.getText().toString().trim();
            boolean useHttps = cbUseHttps.isChecked();
            boolean useWss = cbUseWss.isChecked();

            if (ip.isEmpty()) {
                etServerIp.setError(context.getString(R.string.error_server_ip_required));
                etServerIp.requestFocus();
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
                if (port <= 0 || port > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                etServerPort.setError(context.getString(R.string.error_server_port_invalid));
                etServerPort.requestFocus();
                return;
            }

            databaseManager.setOverrideServerIp(ip);
            databaseManager.setOverrideServerPort(port);
            databaseManager.setOverrideUseHttps(useHttps);
            databaseManager.setOverrideUseWss(useWss);

            Toast.makeText(context, context.getString(R.string.server_settings_saved), Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void confirmResetSettings(
            Context context,
            DatabaseManager databaseManager,
            Switch switchPush,
            Switch switchSound,
            Switch switchVibrate
    ) {
        DialogUtils.showConfirm(
                context,
                context.getString(R.string.dialog_reset_settings_title),
                context.getString(R.string.dialog_reset_settings_message),
                context.getString(R.string.settings_reset),
                context.getString(R.string.action_cancel),
                () -> resetSettings(context, databaseManager, switchPush, switchSound, switchVibrate),
                null,
                false
        );
    }

    private void resetSettings(
            Context context,
            DatabaseManager databaseManager,
            Switch switchPush,
            Switch switchSound,
            Switch switchVibrate
    ) {
        databaseManager.setOverrideServerIp(null);
        databaseManager.setOverrideServerPort(-1);
        databaseManager.setOverrideUseHttps(false);
        databaseManager.setOverrideUseWss(false);
        NotificationSettingsHelper.resetToDefaults(databaseManager, switchPush, switchSound, switchVibrate);
        Toast.makeText(context, context.getString(R.string.settings_reset_success), Toast.LENGTH_LONG).show();
    }

    private void showHelpSupport(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_help_support)
                .setMessage(R.string.dialog_help_support_message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    private static void updateOtpCircles(int filled, TextView... circles) {
        for (int i = 0; i < circles.length; i++) {
            if (circles[i] == null) {
                continue;
            }
            circles[i].setBackgroundResource(i < filled
                    ? R.drawable.otp_circle_filled
                    : R.drawable.otp_circle_empty);
        }
    }

    private static String parseErrorMessage(Context context, String resp) {
        try {
            JSONObject obj = new JSONObject(resp);
            return obj.optString("message", context.getString(R.string.error_request_failed));
        } catch (Exception e) {
            return context.getString(R.string.error_request_failed);
        }
    }

    private static void runOnUi(Context context, Runnable action) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(action);
        } else {
            action.run();
        }
    }
}
