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
import androidx.core.content.ContextCompat;
import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.ServerConfigDialogHelper;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_SIGN_UP = "open_sign_up";
    private static final long TAB_ANIMATION_DURATION_MS = 220L;

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgotPassword, tvSignInTab;
    private TextView tvLoginError;
    private EditText etUsername, etRegisterEmail, etRegisterPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvRegisterError;
    private TextView tvAuthTitle, tvAuthSubtitle;
    private TextView tvServerEndpoint;
    private View authSwitchContainer, authTabIndicator, loginPanel, registerPanel, authFormStage, authFormBezel, authTopCluster, loginTitleTop, authLogoBezel;
    private TextView tvAuthEyebrow;
    private ApiClient apiClient;
    private DatabaseManager databaseManager;
    private android.os.CountDownTimer registerCountDownTimer;
    private android.app.AlertDialog registerOtpDialog;
    private boolean isSignUpMode;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initializeViews();
        initializeServices();
        setupClickListeners();
        setupServerConfigEntryPoints();
        
        // Check if the user is already logged in
        if (databaseManager.isLoggedIn()) {
            navigateToMainActivity();
            return;
        }

        setAuthMode(getIntent().getBooleanExtra(EXTRA_OPEN_SIGN_UP, false), false);
        playEntryAnimation();
    }
    
    private void initializeViews() {
        tvAuthTitle = findViewById(R.id.tv_auth_title);
        tvAuthSubtitle = findViewById(R.id.tv_auth_subtitle);
        authTopCluster = findViewById(R.id.auth_top_cluster);
        authLogoBezel = findViewById(R.id.auth_logo_bezel);
        authFormBezel = findViewById(R.id.auth_form_bezel);
        authSwitchContainer = findViewById(R.id.auth_switch_container);
        authTabIndicator = findViewById(R.id.auth_tab_indicator);
        authFormStage = findViewById(R.id.auth_form_stage);
        loginTitleTop = findViewById(R.id.login_title_top);
        tvSignInTab = findViewById(R.id.tv_sign_in_tab);
        loginPanel = findViewById(R.id.login_panel);
        registerPanel = findViewById(R.id.register_panel);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        tvLoginError = findViewById(R.id.tv_login_error);
        etUsername = findViewById(R.id.et_username);
        etRegisterEmail = findViewById(R.id.et_register_email);
        etRegisterPassword = findViewById(R.id.et_register_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        tvRegisterError = findViewById(R.id.tv_register_error);
        tvServerEndpoint = findViewById(R.id.tv_server_endpoint);

        setupPasswordToggle(etPassword);
        setupPasswordToggle(etRegisterPassword);
        setupPasswordToggle(etConfirmPassword);

        authSwitchContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft)) {
                updateTabIndicator(false);
            }
        });
    }
    
    private void initializeServices() {
        apiClient = new ApiClient();
        databaseManager = new DatabaseManager(this);
    }

    private void playEntryAnimation() {
        com.example.chatappjava.utils.MotionUtils.staggeredReveal(this,
                authLogoBezel,
                tvAuthEyebrow,
                tvAuthTitle,
                tvAuthSubtitle,
                authSwitchContainer,
                authFormBezel);
    }
    
    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnRegister.setOnClickListener(v -> attemptRegister());
        tvSignInTab.setOnClickListener(v -> setAuthMode(false, true));
        tvRegister.setOnClickListener(v -> setAuthMode(true, true));
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        com.example.chatappjava.utils.MotionUtils.attachPressFeedback(this,
                btnLogin, btnRegister, tvSignInTab, tvRegister, tvForgotPassword);
    }

    private void setupServerConfigEntryPoints() {
        updateServerEndpointLabel();
        View.OnClickListener openServerConfig = v ->
                ServerConfigDialogHelper.show(this, databaseManager, this::updateServerEndpointLabel);

        if (tvServerEndpoint != null) {
            tvServerEndpoint.setOnClickListener(openServerConfig);
            com.example.chatappjava.utils.MotionUtils.attachPressFeedback(this, tvServerEndpoint);
        }
        if (authLogoBezel != null) {
            authLogoBezel.setOnLongClickListener(v -> {
                openServerConfig.onClick(v);
                return true;
            });
        }
    }

    private void updateServerEndpointLabel() {
        if (tvServerEndpoint == null) {
            return;
        }
        tvServerEndpoint.setText(getString(R.string.login_server_endpoint, ServerConfig.getBaseUrl()));
        tvServerEndpoint.setContentDescription(getString(R.string.login_server_config_cd));
    }

    private void setupPasswordToggle(EditText editText) {
        if (editText == null) {
            return;
        }

        editText.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
        editText.setOnTouchListener((v, event) -> {
            final int drawableEnd = 2;
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.graphics.drawable.Drawable endDrawable = editText.getCompoundDrawables()[drawableEnd];
                if (endDrawable != null) {
                    int drawableWidth = endDrawable.getBounds().width();
                    int touchAreaStart = editText.getWidth() - editText.getPaddingRight() - drawableWidth;
                    if (event.getX() >= touchAreaStart) {
                        boolean isHidden = editText.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
                        if (isHidden) {
                            editText.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye, 0);
                        } else {
                            editText.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_off, 0);
                        }
                        editText.setSelection(editText.getText().length());
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void setAuthMode(boolean signUpMode, boolean animate) {
        boolean modeChanged = isSignUpMode != signUpMode;
        isSignUpMode = signUpMode;
        boolean runMotion = shouldAnimate(animate && modeChanged);

        if (runMotion) {
            animateCopyChange(tvAuthTitle,
                    getString(signUpMode ? R.string.auth_title_sign_up : R.string.auth_title_sign_in), 0L);
            animateCopyChange(tvAuthSubtitle,
                    getString(signUpMode ? R.string.auth_subtitle_sign_up : R.string.auth_subtitle), 35L);
        } else {
            applyModeCopy(signUpMode);
        }

        updateAuthTabAccessibility(signUpMode);

        showLoginInlineError(null);
        showRegisterInlineError(null);
        animateModeChrome(runMotion);
        updateVisiblePanel(runMotion);
        updateTabIndicator(runMotion);
    }

    private boolean shouldAnimate(boolean requested) {
        return com.example.chatappjava.utils.MotionUtils.shouldAnimate(this, requested);
    }

    private void applyModeCopy(boolean signUpMode) {
        tvAuthTitle.setText(signUpMode ? R.string.auth_title_sign_up : R.string.auth_title_sign_in);
        tvAuthSubtitle.setText(signUpMode ? R.string.auth_subtitle_sign_up : R.string.auth_subtitle);
        tvAuthTitle.setAlpha(1f);
        tvAuthSubtitle.setAlpha(1f);
        tvAuthTitle.setTranslationY(0f);
        tvAuthSubtitle.setTranslationY(0f);
    }

    private void updateAuthTabAccessibility(boolean signUpMode) {
        tvSignInTab.setContentDescription(getString(
                signUpMode ? R.string.auth_tab_sign_in_cd : R.string.auth_tab_sign_in_selected_cd));
        tvRegister.setContentDescription(getString(
                signUpMode ? R.string.auth_tab_sign_up_selected_cd : R.string.auth_tab_sign_up_cd));
        tvSignInTab.setSelected(!signUpMode);
        tvRegister.setSelected(signUpMode);
    }

    private void animateCopyChange(TextView textView, String newText, long delayMs) {
        if (com.example.chatappjava.utils.MotionUtils.isMotionReduced(this)) {
            textView.setText(newText);
            textView.setAlpha(1f);
            textView.setTranslationY(0f);
            return;
        }
        float travel = dp(12f);
        textView.animate().cancel();
        textView.animate()
                .alpha(0.88f)
                .translationY(-travel * 0.45f)
                .setStartDelay(delayMs)
                .setDuration(120L)
                .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                .withEndAction(() -> {
                    textView.setText(newText);
                    textView.setTranslationY(travel * 0.35f);
                    textView.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(220L)
                            .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                            .start();
                })
                .start();
    }

    private void animateModeChrome(boolean animate) {
        float topLift = 0f;
        float formLift = 0f;
        float logoScale = isSignUpMode ? 0.985f : 1f;
        float logoRotation = isSignUpMode ? 1f : 0f;

        if (!animate) {
            authTopCluster.setTranslationY(topLift);
            authFormStage.setTranslationY(formLift);
            loginTitleTop.setScaleX(logoScale);
            loginTitleTop.setScaleY(logoScale);
            loginTitleTop.setRotation(logoRotation);
            return;
        }

        authTopCluster.animate().cancel();
        authFormStage.animate().cancel();
        loginTitleTop.animate().cancel();

        authTopCluster.animate()
                .translationY(topLift)
                .setDuration(240L)
                .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                .start();

        authFormStage.animate()
                .translationY(formLift)
                .setDuration(240L)
                .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                .start();

        loginTitleTop.animate()
                .scaleX(logoScale)
                .scaleY(logoScale)
                .rotation(logoRotation)
                .setDuration(240L)
                .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                .start();
    }

    private void updateVisiblePanel(boolean animate) {
        View incomingPanel = isSignUpMode ? registerPanel : loginPanel;
        View outgoingPanel = isSignUpMode ? loginPanel : registerPanel;

        if (!animate || com.example.chatappjava.utils.MotionUtils.isMotionReduced(this)) {
            incomingPanel.setVisibility(View.VISIBLE);
            incomingPanel.setAlpha(1f);
            incomingPanel.setTranslationY(0f);
            incomingPanel.setScaleX(1f);
            incomingPanel.setScaleY(1f);
            outgoingPanel.setVisibility(View.GONE);
            outgoingPanel.setAlpha(1f);
            outgoingPanel.setTranslationY(0f);
            outgoingPanel.setScaleX(1f);
            outgoingPanel.setScaleY(1f);
            return;
        }

        incomingPanel.bringToFront();
        incomingPanel.setVisibility(View.VISIBLE);
        incomingPanel.setAlpha(0.92f);
        incomingPanel.setTranslationY(dp(26f));
        incomingPanel.setScaleX(0.965f);
        incomingPanel.setScaleY(0.965f);
        incomingPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                .start();

        if (outgoingPanel.getVisibility() == View.VISIBLE) {
            outgoingPanel.animate()
                    .alpha(0f)
                    .translationY(-dp(14f))
                    .scaleX(0.975f)
                    .scaleY(0.975f)
                    .setDuration(180L)
                    .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                    .withEndAction(() -> {
                        outgoingPanel.setVisibility(View.GONE);
                        outgoingPanel.setAlpha(1f);
                        outgoingPanel.setTranslationY(0f);
                        outgoingPanel.setScaleX(1f);
                        outgoingPanel.setScaleY(1f);
                    })
                    .start();
        }
    }

    private void updateTabIndicator(boolean animate) {
        authSwitchContainer.post(() -> {
            int availableWidth = authSwitchContainer.getWidth()
                    - authSwitchContainer.getPaddingLeft()
                    - authSwitchContainer.getPaddingRight();
            if (availableWidth <= 0) {
                return;
            }

            int indicatorWidth = availableWidth / 2;
            android.view.ViewGroup.LayoutParams params = authTabIndicator.getLayoutParams();
            if (params.width != indicatorWidth) {
                params.width = indicatorWidth;
                authTabIndicator.setLayoutParams(params);
            }

            float targetTranslation = isSignUpMode ? indicatorWidth : 0f;
            authTabIndicator.animate().cancel();
            if (animate) {
                authTabIndicator.animate()
                        .translationX(targetTranslation)
                        .scaleX(1f)
                        .alpha(1f)
                        .setDuration(220L)
                        .setInterpolator(com.example.chatappjava.utils.MotionUtils.getEaseOutInterpolator())
                        .start();
            } else {
                authTabIndicator.setTranslationX(targetTranslation);
                authTabIndicator.setScaleX(1f);
                authTabIndicator.setAlpha(1f);
            }
        });
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void showForgotPasswordDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        android.widget.TextView tvMsg = dialogView.findViewById(R.id.tv_message);
        android.widget.Button btnPos = dialogView.findViewById(R.id.btn_positive);
        android.widget.Button btnNeg = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(getString(R.string.forgot_password_title));
        tvMsg.setText(getString(R.string.forgot_password_message));

        // Replace message view with an email input field
        // Root view is a LinearLayout, get its first child (the inner LinearLayout with content)
        android.widget.LinearLayout rootLayout = (android.widget.LinearLayout) dialogView;
        android.widget.LinearLayout container = (android.widget.LinearLayout) rootLayout.getChildAt(0);
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("Email address");
        et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        et.setPadding(50, 20, 50, 20);
        et.setTextColor(getResources().getColor(com.example.chatappjava.R.color.text_white));
        et.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.text_white));
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
        btnPos.setText(getString(R.string.action_send_code));
        btnPos.setOnClickListener(v -> {
            String email = et.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                et.setError("Invalid email");
                et.requestFocus();
                return;
            }
            apiClient.requestPasswordReset(email, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_failed_with_message, e.getMessage()), android.widget.Toast.LENGTH_SHORT).show());
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
                                android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_failed_to_send_reset_code), android.widget.Toast.LENGTH_SHORT).show();
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

        tvTitle.setText(getString(R.string.reset_password_title));
        tvMsg.setText(getString(R.string.reset_password_otp_message));

        androidx.appcompat.app.AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            android.view.Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnResend.setOnClickListener(v -> {
            apiClient.requestPasswordReset(email, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_failed_with_message, e.getMessage()), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            hiddenOtp.setText("");
                            tvError.setVisibility(android.view.View.GONE);
                            android.widget.Toast.makeText(LoginActivity.this, getString(R.string.otp_resent), android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_otp_resend), android.widget.Toast.LENGTH_SHORT).show();
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
                            runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_failed_with_message, e.getMessage()), android.widget.Toast.LENGTH_SHORT).show());
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
                                        tvError.setText(getString(R.string.otp_invalid));
                                        tvError.setVisibility(android.view.View.VISIBLE);
                                        hiddenOtp.setText("");
                                    } catch (Exception ex) {
                                        tvError.setText(getString(R.string.otp_invalid));
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
            public void onFinish() { tvTimer.setText(getString(R.string.otp_timer_expired_short)); }
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

        tvTitle.setText(getString(R.string.set_new_password_title));
        tvMsg.setText("");

        // Root view is a LinearLayout, get its first child (the inner LinearLayout with content)
        android.widget.LinearLayout rootLayout = (android.widget.LinearLayout) dialogView;
        android.widget.LinearLayout container = (android.widget.LinearLayout) rootLayout.getChildAt(0);
        android.widget.EditText etNew = new android.widget.EditText(this);
        etNew.setHint("New password");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNew.setPadding(50, 20, 50, 20);
        etNew.setTextColor(getResources().getColor(com.example.chatappjava.R.color.text_white));
        etNew.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.text_white));
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
        etConfirm.setTextColor(getResources().getColor(com.example.chatappjava.R.color.text_white));
        etConfirm.setHintTextColor(getResources().getColor(com.example.chatappjava.R.color.text_white));
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
        btnPos.setText(getString(R.string.action_reset_password));
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
                    runOnUiThread(() -> android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_failed_with_message, e.getMessage()), android.widget.Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            android.widget.Toast.makeText(LoginActivity.this, getString(R.string.success_password_reset_successful_please_log_in), android.widget.Toast.LENGTH_LONG).show();
                        } else {
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(body);
                                String msg = json.optString("message", "Invalid code or expired");
                                android.widget.Toast.makeText(LoginActivity.this, msg, android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Exception ex) {
                                android.widget.Toast.makeText(LoginActivity.this, getString(R.string.error_invalid_code_or_expired), android.widget.Toast.LENGTH_SHORT).show();
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
        
        showLoginLoading(true);
        
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
                            showLoginLoading(false);
                            android.util.Log.e("LoginActivity", "Login failed for " + ServerConfig.getBaseUrl(), e);
                            showLoginInlineError(
                                    getString(R.string.error_login_connection_hint, ServerConfig.getBaseUrl())
                                            + (e.getMessage() != null ? "\n" + e.getMessage() : "")
                            );
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
                            showLoginLoading(false);
                            handleLoginResponse(response.code(), responseBody);
                        }
                    });
                }
            });
            
        } catch (JSONException e) {
            showLoginLoading(false);
            showLoginInlineError("Data error. Please try again.");
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
                databaseManager.saveLoginInfo(token, user.toString());
                
                // Reconnect socket with new token
                com.example.chatappjava.ChatApplication.getInstance().reconnectSocket();
                // Re-register FCM token after login
                com.example.chatappjava.ChatApplication.getInstance().reRegisterFCMToken();
                // Check and prompt for battery optimization (for push notifications)
                com.example.chatappjava.utils.BatteryOptimizationHelper.checkAndPromptBatteryOptimization(LoginActivity.this);
                
                Toast.makeText(this, getString(R.string.success_login_successful), Toast.LENGTH_SHORT).show();
                navigateToMainActivity();
                
            } else {
                // Handle login errors with detailed account status
                handleLoginError(statusCode, responseBody);
            }
            
        } catch (JSONException e) {
            e.printStackTrace(); // Log the actual error for debugging
            showLoginInlineError("Data processing error.");
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
                    showLoginInlineError(message.isEmpty() ? "Account locked by administrator." : message);
                } else {
                    showLoginInlineError(message.isEmpty() ? "Access denied." : message);
                }
                
            } else if (statusCode == 401) {
                // Handle authentication errors
                if ("not_found".equals(accountStatus)) {
                    showLoginInlineError(details.isEmpty() ? "Account not found." : details);
                    
                } else if ("active".equals(accountStatus)) {
                    showLoginInlineError(details.isEmpty() ? "Incorrect password." : details);
                } else {
                    showLoginInlineError(message.isEmpty() ? "Authentication failed." : message);
                }
                
            } else {
                // Other errors
                showLoginInlineError(message.isEmpty() ? "Login error." : message);
            }
            
        } catch (JSONException e) {
            e.printStackTrace();
            showLoginInlineError("Error processing response.");
        }
    }

    private void showLoginInlineError(String msg) {
        if (tvLoginError == null) return;
        if (msg == null || msg.trim().isEmpty()) {
            tvLoginError.setText("");
            tvLoginError.setVisibility(View.GONE);
            return;
        }
        tvLoginError.setText(msg);
        tvLoginError.setVisibility(View.VISIBLE);
    }

    private void showRegisterInlineError(String msg) {
        if (tvRegisterError == null) return;
        if (msg == null || msg.trim().isEmpty()) {
            tvRegisterError.setText("");
            tvRegisterError.setVisibility(View.GONE);
            return;
        }
        tvRegisterError.setText(msg);
        tvRegisterError.setVisibility(View.VISIBLE);
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
                    Toast.makeText(this, getString(R.string.feature_forgot_password_feature_coming_soon), Toast.LENGTH_SHORT).show();
                });
            }
        }
        
        builder.setCancelable(false);
        builder.show();
    }
    
    private void showLoginLoading(boolean show) {
        btnLogin.setEnabled(!show);
        btnLogin.setText(show ? "Signing in..." : "Sign In");
    }

    private void showRegisterLoading(boolean show) {
        btnRegister.setEnabled(!show);
        btnRegister.setText(show ? "Creating account..." : "Sign Up");
    }

    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String email = etRegisterEmail.getText().toString().trim();
        String password = etRegisterPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (!validateRegisterInput(username, email, password, confirmPassword)) {
            return;
        }

        showRegisterLoading(true);
        requestRegisterOtpAndShowDialog(username, email, password);
    }

    private boolean validateRegisterInput(String username, String email, String password, String confirmPassword) {
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

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            etUsername.setError("Username can only contain letters, numbers, and underscores");
            etUsername.requestFocus();
            return false;
        }

        if (email.isEmpty()) {
            etRegisterEmail.setError("Please enter your email");
            etRegisterEmail.requestFocus();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etRegisterEmail.setError("Invalid email format");
            etRegisterEmail.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            etRegisterPassword.setError("Please enter a password");
            etRegisterPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            etRegisterPassword.setError("Password must be at least 6 characters");
            etRegisterPassword.requestFocus();
            return false;
        }

        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")) {
            etRegisterPassword.setError("Password must contain at least one uppercase letter, one lowercase letter, and one number");
            etRegisterPassword.requestFocus();
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

    private void requestRegisterOtpAndShowDialog(String username, String email, String password) {
        try {
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("email", email);
            data.put("password", password);

            apiClient.requestRegisterOTP(data, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        showRegisterLoading(false);
                        showRegisterInlineError("Failed to send OTP. Please try again.");
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        showRegisterLoading(false);
                        if (response.isSuccessful()) {
                            showRegisterInlineError(null);
                            showRegisterOtpDialog(email);
                        } else {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String message = json.optString("message", "Failed to request OTP");
                                showRegisterInlineError(message);
                            } catch (Exception ex) {
                                showRegisterInlineError("Failed to request OTP");
                            }
                        }
                    });
                }
            });
        } catch (JSONException e) {
            showRegisterLoading(false);
            showRegisterInlineError("Data Error. Please try again.");
        }
    }

    private void showRegisterOtpDialog(String email) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_otp, null);
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

        registerOtpDialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (registerOtpDialog.getWindow() != null) {
            android.view.Window w = registerOtpDialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        registerOtpDialog.setOnDismissListener(dialog -> {
            if (registerCountDownTimer != null) {
                registerCountDownTimer.cancel();
            }
        });

        android.view.View.OnClickListener focusInput = v -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
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
                    hiddenOtp.clearFocus();
                    verifyRegisterOtp(email, code);
                }
            }
        };
        hiddenOtp.addTextChangedListener(watcher);

        btnResend.setOnClickListener(v -> resendRegisterOtp(email, hiddenOtp, tvError, tvTimer));

        startRegisterDialogTimer(tvTimer);
        registerOtpDialog.show();

        hiddenOtp.postDelayed(() -> {
            hiddenOtp.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(hiddenOtp, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private void resendRegisterOtp(String email, EditText hiddenOtp, TextView tvError, TextView tvTimer) {
        try {
            JSONObject data = new JSONObject();
            data.put("username", etUsername.getText().toString().trim());
            data.put("email", email);
            data.put("password", etRegisterPassword.getText().toString().trim());

            apiClient.requestRegisterOTP(data, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(R.string.error_otp_resend), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            hiddenOtp.setText("");
                            tvError.setVisibility(View.GONE);
                            startRegisterDialogTimer(tvTimer);
                            Toast.makeText(LoginActivity.this, getString(R.string.otp_resent), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LoginActivity.this, getString(R.string.error_otp_resend), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException e) {
            Toast.makeText(this, getString(R.string.msg_data_error_please_try_again), Toast.LENGTH_SHORT).show();
        }
    }

    private void startRegisterDialogTimer(TextView tvTimer) {
        if (registerCountDownTimer != null) {
            registerCountDownTimer.cancel();
        }
        tvTimer.setText(getString(R.string.otp_timer_initial));
        registerCountDownTimer = new android.os.CountDownTimer(60_000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                long mm = seconds / 60;
                long ss = seconds % 60;
                tvTimer.setText(String.format("%02d:%02d", mm, ss));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.otp_expired));
            }
        }.start();
    }

    private void verifyRegisterOtp(String email, String otp) {
        try {
            JSONObject data = new JSONObject();
            data.put("email", email);
            data.put("otpCode", otp);
            apiClient.verifyRegisterOTP(data, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(R.string.error_verification, e.getMessage()), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        if (response.code() == 201) {
                            if (registerCountDownTimer != null) {
                                registerCountDownTimer.cancel();
                            }
                            if (registerOtpDialog != null && registerOtpDialog.isShowing()) {
                                registerOtpDialog.dismiss();
                            }
                            handleRegisterResponse(response.code(), responseBody);
                        } else {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String message = json.optString("message", "Invalid OTP");
                                showRegisterOtpError(message);
                            } catch (Exception ex) {
                                showRegisterOtpError("Invalid OTP");
                            }
                        }
                    });
                }
            });
        } catch (JSONException e) {
            Toast.makeText(this, getString(R.string.msg_data_error_please_try_again), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRegisterOtpError(String message) {
        if (registerOtpDialog == null || !registerOtpDialog.isShowing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        TextView errorView = registerOtpDialog.findViewById(R.id.dialog_tv_error);
        EditText hiddenOtp = registerOtpDialog.findViewById(R.id.dialog_et_hidden_otp);
        if (errorView != null) {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
        if (hiddenOtp != null) {
            hiddenOtp.setText("");
        }
    }

    private void handleRegisterResponse(int statusCode, String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (statusCode == 201) {
                Toast.makeText(this, getString(R.string.success_registration_successful_please_sign_in), Toast.LENGTH_LONG).show();
                etEmail.setText(etRegisterEmail.getText().toString().trim());
                etPassword.setText("");
                clearRegisterInputs();
                setAuthMode(false, true);
                etPassword.requestFocus();
            } else {
                String message = jsonResponse.optString("message", "Registration failed");
                showRegisterInlineError(message);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            showRegisterInlineError("Data processing error: " + e.getMessage());
        }
    }

    private void clearRegisterInputs() {
        etUsername.setText("");
        etRegisterEmail.setText("");
        etRegisterPassword.setText("");
        etConfirmPassword.setText("");
        showRegisterInlineError(null);
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_SIGN_UP, false)) {
            setAuthMode(true, false);
        }
    }

    @Override
    protected void onDestroy() {
        if (registerCountDownTimer != null) {
            registerCountDownTimer.cancel();
        }
        super.onDestroy();
    }
}
