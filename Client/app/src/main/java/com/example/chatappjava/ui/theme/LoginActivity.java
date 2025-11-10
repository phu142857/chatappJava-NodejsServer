package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.chatappjava.R;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {
    
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgotPassword;
    private TextView tvLoginError;
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initializeViews();
        initializeServices();
        setupClickListeners();
        
        // Check if the user is already logged in
        if (sharedPrefsManager.isLoggedIn()) {
            navigateToMainActivity();
        }
    }
    
    private void initializeViews() {
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvLoginError = findViewById(R.id.tv_login_error);

        // Toggle password visibility when tapping drawableEnd using transformation method
        etPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye_off, 0);
        etPassword.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2; // right drawable
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.graphics.drawable.Drawable endDrawable = etPassword.getCompoundDrawables()[DRAWABLE_END];
                if (endDrawable != null) {
                    int drawableWidth = endDrawable.getBounds().width();
                    int touchAreaStart = etPassword.getWidth() - etPassword.getPaddingRight() - drawableWidth;
                    if (event.getX() >= touchAreaStart) {
                        boolean isHidden = etPassword.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
                        if (isHidden) {
                            etPassword.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                            etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye, 0);
                        } else {
                            etPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                            etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye_off, 0);
                        }
                        etPassword.setSelection(etPassword.getText().length());
                        return true;
                    }
                }
            }
            return false;
        });
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
        sharedPrefsManager = new SharedPreferencesManager(this);
    }
    
    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivityForResult(intent, 1);
            }
        });
        
        tvForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showForgotPasswordDialog();
            }
        });
    }

    private void showForgotPasswordDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        android.widget.TextView tvMsg = dialogView.findViewById(R.id.tv_message);
        android.widget.Button btnPos = dialogView.findViewById(R.id.btn_positive);
        android.widget.Button btnNeg = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText("Forgot Password");
        tvMsg.setText("Enter your email to receive a reset code.");

        // Replace message view with an email input field
        // Root view is a LinearLayout, get its first child (the inner LinearLayout with content)
        android.widget.LinearLayout rootLayout = (android.widget.LinearLayout) dialogView;
        android.widget.LinearLayout container = (android.widget.LinearLayout) rootLayout.getChildAt(0);
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("Email address");
        et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        et.setPadding(50, 20, 50, 20);
        et.setTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        et.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        et.setBackground(getResources().getDrawable(com.example.chatappjava.R.drawable.rounded_container));
        
        // Set layout parameters
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 10, 20, 20);
        et.setLayoutParams(params);
        container.addView(et, 2); // after title & message

        androidx.appcompat.app.AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        btnNeg.setOnClickListener(v -> dialog.dismiss());
        btnPos.setText("Send Code");
        btnPos.setOnClickListener(v -> {
            String email = et.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                et.setError("Invalid email");
                et.requestFocus();
                return;
            }
            apiClient.requestPasswordReset(email, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    final String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            showResetWithOtpDialog(email);
                        } else {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(body);
                                String msg = json.optString("message", "Failed to send reset code");
                                android.widget.Toast.makeText(LoginActivity.this, msg, android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Exception ex) {
                                android.widget.Toast.makeText(LoginActivity.this, "Failed to send reset code", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        });
        dialog.show();
    }

    private void showResetWithOtpDialog(String email) {
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

        tvTitle.setText("Reset Password");
        tvMsg.setText("Enter the 6-digit code sent to your email.");

        androidx.appcompat.app.AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnResend.setOnClickListener(v -> {
            apiClient.requestPasswordReset(email, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            hiddenOtp.setText("");
                            tvError.setVisibility(android.view.View.GONE);
                            android.widget.Toast.makeText(LoginActivity.this, "OTP resent", android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(LoginActivity.this, "Failed to resend OTP", android.widget.Toast.LENGTH_SHORT).show();
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
                    apiClient.verifyPasswordResetOTP(email, code, new okhttp3.Callback() {
                        @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                            runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                        }
                        @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                            String body = response.body() != null ? response.body().string() : "";
                            runOnUiThread(() -> {
                                if (response.isSuccessful()) {
                                    dialog.dismiss();
                                    showNewPasswordDialog(email, code);
                                } else {
                                    try {
                                        org.json.JSONObject json = new org.json.JSONObject(body);
                                        String msg = json.optString("message", "Invalid OTP");
                                        tvError.setText("Invalid OTP");
                                        tvError.setVisibility(android.view.View.VISIBLE);
                                        hiddenOtp.setText("");
                                    } catch (Exception ex) {
                                        tvError.setText("Invalid OTP");
                                        tvError.setVisibility(android.view.View.VISIBLE);
                                        hiddenOtp.setText("");
                                    }
                                }
                            });
                        }
                    });
                }
            }
        };
        hiddenOtp.addTextChangedListener(watcher);

        // 10-minute timer visual (optional): show 10:00 countdown
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


    private void showNewPasswordDialog(String email, String verifiedOtp) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        android.widget.TextView tvMsg = dialogView.findViewById(R.id.tv_message);
        android.widget.Button btnPos = dialogView.findViewById(R.id.btn_positive);
        android.widget.Button btnNeg = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText("Set New Password");
        tvMsg.setText("");

        // Root view is a LinearLayout, get its first child (the inner LinearLayout with content)
        android.widget.LinearLayout rootLayout = (android.widget.LinearLayout) dialogView;
        android.widget.LinearLayout container = (android.widget.LinearLayout) rootLayout.getChildAt(0);
        android.widget.EditText etNew = new android.widget.EditText(this);
        etNew.setHint("New password");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNew.setPadding(50, 20, 50, 20);
        etNew.setTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        etNew.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        etNew.setBackground(getResources().getDrawable(com.example.chatappjava.R.drawable.rounded_container));
        
        // Set layout parameters for new password field
        android.widget.LinearLayout.LayoutParams paramsNew = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        paramsNew.setMargins(20, 10, 20, 10);
        etNew.setLayoutParams(paramsNew);
        container.addView(etNew, 2);

        android.widget.EditText etConfirm = new android.widget.EditText(this);
        etConfirm.setHint("Confirm password");
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etConfirm.setPadding(50, 20, 50, 20);
        etConfirm.setTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        etConfirm.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.black));
        etConfirm.setBackground(getResources().getDrawable(com.example.chatappjava.R.drawable.rounded_container));
        
        // Set layout parameters for confirm password field
        android.widget.LinearLayout.LayoutParams paramsConfirm = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        paramsConfirm.setMargins(20, 10, 20, 20);
        etConfirm.setLayoutParams(paramsConfirm);
        container.addView(etConfirm, 3);

        androidx.appcompat.app.AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        btnNeg.setOnClickListener(v -> dialog.dismiss());
        btnPos.setText("Reset Password");
        btnPos.setOnClickListener(v -> {
            String newPwd = etNew.getText().toString();
            String confirmPwd = etConfirm.getText().toString();
            if (newPwd.length() < 6 || !newPwd.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")) {
                etNew.setError("Password must have upper, lower, number (>=6)");
                etNew.requestFocus();
                return;
            }
            if (!newPwd.equals(confirmPwd)) {
                etConfirm.setError("Passwords do not match");
                etConfirm.requestFocus();
                return;
            }
            apiClient.confirmPasswordReset(email, verifiedOtp, newPwd, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            android.widget.Toast.makeText(LoginActivity.this, "Password reset successful. Please log in.", android.widget.Toast.LENGTH_LONG).show();
                        } else {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(body);
                                String msg = json.optString("message", "Invalid code or expired");
                                android.widget.Toast.makeText(LoginActivity.this, msg, android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Exception ex) {
                                android.widget.Toast.makeText(LoginActivity.this, "Invalid code or expired", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        });
        dialog.show();
    }
    
    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        if (!validateInput(email, password)) {
            return;
        }
        
        showLoading(true);
        
        try {
            JSONObject loginData = new JSONObject();
            loginData.put("email", email);
            loginData.put("password", password);
            
            apiClient.login(loginData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            showInlineError("Connection error. Please try again.");
                        }
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    
                    // Log response for debugging
                    System.out.println("Login Response Code: " + response.code());
                    System.out.println("Login Response Body: " + responseBody);
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            handleLoginResponse(response.code(), responseBody);
                        }
                    });
                }
            });
            
        } catch (JSONException e) {
            showLoading(false);
            showInlineError("Data error. Please try again.");
        }
    }
    
    private boolean validateInput(String email, String password) {
        if (email.isEmpty()) {
            etEmail.setError("Please enter your email");
            etEmail.requestFocus();
            return false;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email format");
            etEmail.requestFocus();
            return false;
        }
        
        if (password.isEmpty()) {
            etPassword.setError("Please enter your password");
            etPassword.requestFocus();
            return false;
        }
        
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void handleLoginResponse(int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 200) {
                // Server returns data in "data" object
                JSONObject data = jsonResponse.getJSONObject("data");
                String token = data.getString("token");
                JSONObject user = data.getJSONObject("user");
                
                // Save login information
                sharedPrefsManager.saveLoginInfo(token, user.toString());
                
                // Reconnect socket with new token
                com.example.chatappjava.ChatApplication.getInstance().reconnectSocket();
                
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                navigateToMainActivity();
                
            } else {
                // Handle login errors with detailed account status
                handleLoginError(statusCode, responseBody);
            }
            
        } catch (JSONException e) {
            e.printStackTrace(); // Log the actual error for debugging
            showInlineError("Data processing error.");
        }
    }
    
    private void handleLoginError(int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            String message = jsonResponse.optString("message", "Login failed");
            String accountStatus = jsonResponse.optString("accountStatus", "");
            String details = jsonResponse.optString("details", "");
            
            if (statusCode == 403) {
                // Handle account locked
                if ("locked".equals(accountStatus)) {
                    showInlineError(message.isEmpty() ? "Account locked by administrator." : message);
                } else {
                    showInlineError(message.isEmpty() ? "Access denied." : message);
                }
                
            } else if (statusCode == 401) {
                // Handle authentication errors
                if ("not_found".equals(accountStatus)) {
                    showInlineError(details.isEmpty() ? "Account not found." : details);
                    
                } else if ("active".equals(accountStatus)) {
                    showInlineError(details.isEmpty() ? "Incorrect password." : details);
                } else {
                    showInlineError(message.isEmpty() ? "Authentication failed." : message);
                }
                
            } else {
                // Other errors
                showInlineError(message.isEmpty() ? "Login error." : message);
            }
            
        } catch (JSONException e) {
            e.printStackTrace();
            showInlineError("Error processing response.");
        }
    }

    private void showInlineError(String msg) {
        if (tvLoginError == null) return;
        tvLoginError.setText(msg);
        tvLoginError.setVisibility(View.VISIBLE);
    }
    
    private void showAccountStatusDialog(String title, String message, String details, boolean isSuccess) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        
        StringBuilder content = new StringBuilder(message);
        if (!details.isEmpty()) {
            content.append("\n\n").append(details);
        }
        
        builder.setMessage(content.toString());
        
        if (isSuccess) {
            builder.setPositiveButton("OK", (dialog, which) -> {
                dialog.dismiss();
                navigateToMainActivity();
            });
        } else {
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            
            // Add "Forgot Password" button for password errors
            if (message.toLowerCase().contains("password")) {
                builder.setNeutralButton("Forgot Password?", (dialog, which) -> {
                    // TODO: Implement forgot password functionality
                    Toast.makeText(this, "Forgot password feature coming soon!", Toast.LENGTH_SHORT).show();
                });
            }
        }
        
        builder.setCancelable(false);
        builder.show();
    }
    
    private void showLoading(boolean show) {
        btnLogin.setEnabled(!show);
        btnLogin.setText(show ? "Logging in..." : "Login");
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            String email = data.getStringExtra("email");
            if (email != null) {
                etEmail.setText(email);
                etPassword.requestFocus();
            }
        }
    }
}