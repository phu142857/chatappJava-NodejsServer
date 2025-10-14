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
        android.widget.LinearLayout container = (android.widget.LinearLayout) dialogView;
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("Email address");
        et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        et.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
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
        android.widget.EditText etOtp = dialogView.findViewById(R.id.dialog_et_otp);
        android.widget.TextView tvTimer = dialogView.findViewById(R.id.dialog_tv_timer);
        android.widget.Button btnNeg = dialogView.findViewById(R.id.btn_negative);
        android.widget.Button btnPos = dialogView.findViewById(R.id.btn_positive);

        tvTitle.setText("Reset Password");
        tvMsg.setText("Enter the 6-digit code sent to your email, then set a new password.");

        // Add new password field below OTP
        android.widget.LinearLayout container = (android.widget.LinearLayout) dialogView;
        android.widget.EditText etNew = new android.widget.EditText(this);
        etNew.setHint("New password");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNew.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(etNew, container.getChildCount() - 1); // before button row

        androidx.appcompat.app.AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnNeg.setOnClickListener(v -> dialog.dismiss());
        btnPos.setText("Verify OTP");
        btnPos.setOnClickListener(v -> {
            String otp = etOtp.getText().toString().trim();
            if (otp.length() != 6) {
                etOtp.setError("Enter 6-digit OTP");
                etOtp.requestFocus();
                return;
            }
            apiClient.verifyPasswordResetOTP(email, otp, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, "Failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            showNewPasswordDialog(email, otp);
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

        // 10-minute timer visual (optional): show 10:00 countdown
        new android.os.CountDownTimer(10 * 60 * 1000, 1000) {
            public void onTick(long ms) {
                long s = ms / 1000; long mm = s / 60; long ss = s % 60;
                tvTimer.setText(String.format("%02d:%02d", mm, ss));
            }
            public void onFinish() { tvTimer.setText("Expired. Request again."); }
        }.start();

        dialog.show();
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

        android.widget.LinearLayout container = (android.widget.LinearLayout) dialogView;
        android.widget.EditText etNew = new android.widget.EditText(this);
        etNew.setHint("New password");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNew.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(etNew, 2);

        android.widget.EditText etConfirm = new android.widget.EditText(this);
        etConfirm.setHint("Confirm password");
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etConfirm.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
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
                            Toast.makeText(LoginActivity.this, "Connection Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Data Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Data processing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    showAccountStatusDialog(
                        "🔒 Account Locked",
                        message,
                        details.isEmpty() ? 
                            "Your account has been locked by an administrator.\nPlease contact support for assistance." : details,
                        false
                    );
                } else {
                    showAccountStatusDialog("🚫 Access Denied", message, details, false);
                }
                
            } else if (statusCode == 401) {
                // Handle authentication errors
                if ("not_found".equals(accountStatus)) {
                    showAccountStatusDialog(
                        "❓ Account Not Found",
                        message,
                        details.isEmpty() ? 
                            "No account found with this email address.\nPlease check your email or register for a new account." : details,
                        false
                    );
                    
                } else if ("active".equals(accountStatus)) {
                    showAccountStatusDialog(
                        "🔑 Incorrect Password",
                        message,
                        details.isEmpty() ? 
                            "The password you entered is incorrect.\nPlease try again or use the forgot password feature." : details,
                        false
                    );
                } else {
                    showAccountStatusDialog("🔒 Authentication Failed", message, details, false);
                }
                
            } else {
                // Other errors
                showAccountStatusDialog("⚠️ Login Error", message, details, false);
            }
            
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error processing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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