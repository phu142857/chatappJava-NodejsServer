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
    private ApiClient apiClient;
    
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
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
    }
    
    private void setupClickListeners() {
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegister();
            }
        });
        
        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Return to LoginActivity
            }
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
        
        showLoading(true);
        
        try {
            JSONObject registerData = new JSONObject();
            registerData.put("username", username); // Server expects 'username' field
            registerData.put("email", email);
            registerData.put("password", password);
            
            apiClient.register(registerData, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            Toast.makeText(RegisterActivity.this, "Connection Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            handleRegisterResponse(response.code(), responseBody);
                        }
                    });
                }
            });
            
        } catch (JSONException e) {
            showLoading(false);
            Toast.makeText(this, "Data Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
                
                // Return to LoginActivity and pass the email to pre-fill the field
                Intent intent = new Intent();
                intent.putExtra("email", etEmail.getText().toString().trim());
                setResult(RESULT_OK, intent);
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
}