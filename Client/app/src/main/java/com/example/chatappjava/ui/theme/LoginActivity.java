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
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
        
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
                // TODO: Implement forgot password functionality
                Toast.makeText(LoginActivity.this, "Forgot password functionality will be updated", Toast.LENGTH_SHORT).show();
            }
        });
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
                String message = jsonResponse.optString("message", "Login failed");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
            
        } catch (JSONException e) {
            e.printStackTrace(); // Log the actual error for debugging
            Toast.makeText(this, "Data processing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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