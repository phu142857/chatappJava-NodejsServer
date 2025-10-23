package com.example.chatappjava.ui.theme;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.chatappjava.R;
import com.example.chatappjava.network.ApiClient;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {
    
    private EditText etUsername, etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvLogin;
    private TextView tvRegisterError;
    private ApiClient apiClient;
    private android.os.CountDownTimer countDownTimer;
    private android.app.AlertDialog otpDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        
        initializeViews();
        initializeServices();
        setupClickListeners();
    }
    
    private void initializeViews() {
        etUsername = findViewById(R.id.et_username);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        tvLogin = findViewById(R.id.tv_login);
        tvRegisterError = findViewById(R.id.tv_register_error);

        // Toggle password visibility (password field) using transformation method
        etPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye_off, 0);
        etPassword.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2;
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

        // Toggle password visibility (confirm field) using transformation method
        etConfirmPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        etConfirmPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye_off, 0);
        etConfirmPassword.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2;
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.graphics.drawable.Drawable endDrawable = etConfirmPassword.getCompoundDrawables()[DRAWABLE_END];
                if (endDrawable != null) {
                    int drawableWidth = endDrawable.getBounds().width();
                    int touchAreaStart = etConfirmPassword.getWidth() - etConfirmPassword.getPaddingRight() - drawableWidth;
                    if (event.getX() >= touchAreaStart) {
                        boolean isHidden = etConfirmPassword.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
                        if (isHidden) {
                            etConfirmPassword.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                            etConfirmPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye, 0);
                        } else {
                            etConfirmPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                            etConfirmPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, com.example.chatappjava.R.drawable.ic_eye_off, 0);
                        }
                        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
                        return true;
                    }
                }
            }
            return false;
        });
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
    }
    
    private void setupClickListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        
        tvLogin.setOnClickListener(v -> {
            finish(); // Return to LoginActivity
        });
    }
    
    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        
        if (!validateInput(username, email, password, confirmPassword)) {
            return;
        }
        
        requestOtpAndShowDialog(username, email, password);
    }
    
    private boolean validateInput(String username, String email, String password, String confirmPassword) {
        if (username.isEmpty()) {
            etUsername.setError("Please enter username");
            etUsername.requestFocus();
            return false;
        }
        
        if (username.length() < 3 || username.length() > 30) {
            etUsername.setError("Username must be between 3 and 30 characters");
            etUsername.requestFocus();
            return false;
        }
        
        // Check username format (letters, numbers, underscores only)
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            etUsername.setError("Username can only contain letters, numbers, and underscores");
            etUsername.requestFocus();
            return false;
        }
        
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
            etPassword.setError("Please enter a password");
            etPassword.requestFocus();
            return false;
        }
        
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }
        
        // Check password strength (must contain uppercase, lowercase, and number)
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")) {
            etPassword.setError("Password must contain at least one uppercase letter, one lowercase letter, and one number");
            etPassword.requestFocus();
            return false;
        }
        
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            etConfirmPassword.requestFocus();
            return false;
        }
        
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void handleRegisterResponse(int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (statusCode == 201) {
                Toast.makeText(this, "Registration successful! Please log in.", Toast.LENGTH_LONG).show();

                // Navigate explicitly to LoginActivity (match app pattern)
                Intent intent = new Intent(this, LoginActivity.class);
                intent.putExtra("email", etEmail.getText().toString().trim());
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                
            } else {
                String message = jsonResponse.optString("message", "Registration failed");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            
        } catch (JSONException e) {
            e.printStackTrace(); // Log the actual error for debugging
            Toast.makeText(this, "Data processing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showLoading(boolean show) {
        btnRegister.setEnabled(!show);
        btnRegister.setText(show ? "Registering..." : "Register");
    }

    private void requestOtpAndShowDialog(String username, String email, String password) {
        try {
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("email", email);
            data.put("password", password);

            btnRegister.setEnabled(false);
            apiClient.requestRegisterOTP(data, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        btnRegister.setEnabled(true);
                        showInlineError("Failed to send OTP. Please try again.");
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        btnRegister.setEnabled(true);
                        if (response.isSuccessful()) {
                            showOtpDialog(email);
                        } else {
                            try {
                                String body = response.body().string();
                                JSONObject json = new JSONObject(body);
                                String message = json.optString("message", "Failed to request OTP");
                                showInlineError(message);
                            } catch (Exception ex) {
                                showInlineError("Failed to request OTP");
                            }
                        }
                    });
                }
            });
        } catch (JSONException e) {
            showInlineError("Data Error. Please try again.");
        }
    }

    private void showInlineError(String msg) {
        if (tvRegisterError == null) return;
        tvRegisterError.setText(msg);
        tvRegisterError.setVisibility(View.VISIBLE);
    }

    private void showOtpDialog(String email) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
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

        otpDialog = builder.create();
        if (otpDialog.getWindow() != null) {
            android.view.Window w = otpDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        otpDialog.setOnDismissListener(d -> { if (countDownTimer != null) countDownTimer.cancel(); });

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
                    verifyOtpFromDialog(email, code);
                }
            }
        };
        hiddenOtp.addTextChangedListener(watcher);

        btnResend.setOnClickListener(v -> {
            try {
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("username", etUsername.getText().toString().trim());
                data.put("email", email);
                data.put("password", etPassword.getText().toString().trim());
                btnResend.setEnabled(false);
                apiClient.requestRegisterOTP(data, new Callback() {
                    @Override public void onFailure(Call call, java.io.IOException e) {
                        runOnUiThread(() -> {
                            btnResend.setEnabled(true);
                            android.widget.Toast.makeText(RegisterActivity.this, "Failed to resend: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onResponse(Call call, Response response) throws java.io.IOException {
                        runOnUiThread(() -> {
                            btnResend.setEnabled(true);
                            if (response.isSuccessful()) {
                                hiddenOtp.setText("");
                                tvError.setVisibility(android.view.View.GONE);
                                startDialogTimer(dialogTvTimer);
                                android.widget.Toast.makeText(RegisterActivity.this, "OTP resent", android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                android.widget.Toast.makeText(RegisterActivity.this, "Failed to resend OTP", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            } catch (org.json.JSONException ex) {
                android.widget.Toast.makeText(RegisterActivity.this, "Data Error: " + ex.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        startDialogTimer(dialogTvTimer);
        otpDialog.show();

        hiddenOtp.postDelayed(() -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void updateOtpCircles(int filled, TextView... circles) {
        for (int i = 0; i < circles.length; i++) {
            circles[i].setBackgroundResource(i < filled ? com.example.chatappjava.R.drawable.otp_circle_filled : com.example.chatappjava.R.drawable.otp_circle_empty);
        }
    }

    private void startDialogTimer(TextView tv) {
        if (countDownTimer != null) countDownTimer.cancel();
        tv.setText("01:00");
        countDownTimer = new android.os.CountDownTimer(60000, 1000) {
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

    private void verifyOtpFromDialog(String email, String otp) {
        try {
            JSONObject data = new JSONObject();
            data.put("email", email);
            data.put("otpCode", otp);
            apiClient.verifyRegisterOTP(data, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(() -> {
                        int code = response.code();
                        if (code == 201) {
                            if (countDownTimer != null) countDownTimer.cancel();
                            if (otpDialog != null && otpDialog.isShowing()) otpDialog.dismiss();
                            handleRegisterResponse(code, responseBody);
                        } else {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String message = json.optString("message", "Invalid OTP");
                                if (otpDialog != null && otpDialog.isShowing()) {
                                    android.widget.TextView err = otpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                                    android.widget.EditText hid = otpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
                                    if (err != null) { err.setText("Invalid OTP"); err.setVisibility(android.view.View.VISIBLE); }
                                    if (hid != null) { hid.setText(""); }
                                } else {
                                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ex) {
                                if (otpDialog != null && otpDialog.isShowing()) {
                                    android.widget.TextView err = otpDialog.findViewById(com.example.chatappjava.R.id.dialog_tv_error);
                                    android.widget.EditText hid = otpDialog.findViewById(com.example.chatappjava.R.id.dialog_et_hidden_otp);
                                    if (err != null) { err.setText("Invalid OTP"); err.setVisibility(android.view.View.VISIBLE); }
                                    if (hid != null) { hid.setText(""); }
                                } else {
                                    Toast.makeText(RegisterActivity.this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
                }
            });
        } catch (JSONException e) {
            Toast.makeText(this, "Data Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }
}