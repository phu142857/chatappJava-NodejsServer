package com.example.chatappjava.ui.call;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class RingingActivity extends AppCompatActivity {
    
    private static final String TAG = "RingingActivity";
    
    // UI Components
    private TextView tvCallType;
    private TextView tvCallerName;
    private TextView tvCallerStatus;
    private TextView tvCallDuration;
    private TextView tvRingingStatus;
    private CircleImageView ivCallerAvatar;
    private ImageButton btnAccept;
    private ImageButton btnDecline;
    private LinearLayout outgoingCallInfo;
    private FrameLayout ringingAnimationContainer;
    private View ripple1;
    private View ripple2;
    private View ripple3;
    
    // Call data
    private Chat currentChat;
    private User caller;
    private String callId;
    private String callType;
    private boolean isIncomingCall;
    private boolean isCallActive = false;
    
    // Animation
    private AnimatorSet rippleAnimatorSet;
    private Animation avatarPulseAnimation;
    
    // Audio
    private MediaPlayer ringtonePlayer;
    private AudioManager audioManager;
    
    // Managers
    private SharedPreferencesManager sharedPrefsManager;
    private ApiClient apiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on during ringing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_ringing);
        
        // Initialize managers
        sharedPrefsManager = new SharedPreferencesManager(this);
        apiClient = new ApiClient();
        
        // Initialize audio manager
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Get call data from intent
        getCallDataFromIntent();
        
        // Initialize UI
        initializeViews();
        setupClickListeners();
        
        // Setup call based on type
        if (isIncomingCall) {
            setupIncomingCall();
        } else {
            setupOutgoingCall();
        }
    }
    
    private void getCallDataFromIntent() {
        try {
            String chatJson = getIntent().getStringExtra("chat");
            if (chatJson != null) {
                currentChat = Chat.fromJson(new JSONObject(chatJson));
            }
            
            String callerJson = getIntent().getStringExtra("caller");
            if (callerJson != null) {
                caller = User.fromJson(new JSONObject(callerJson));
            }
            
            callId = getIntent().getStringExtra("callId");
            callType = getIntent().getStringExtra("callType");
            isIncomingCall = getIntent().getBooleanExtra("isIncomingCall", false);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing call data", e);
            finish();
        }
    }
    
    private void initializeViews() {
        tvCallType = findViewById(R.id.tv_call_type);
        tvCallerName = findViewById(R.id.tv_caller_name);
        tvCallerStatus = findViewById(R.id.tv_caller_status);
        tvCallDuration = findViewById(R.id.tv_call_duration);
        tvRingingStatus = findViewById(R.id.tv_ringing_status);
        ivCallerAvatar = findViewById(R.id.iv_caller_avatar);
        btnAccept = findViewById(R.id.btn_accept);
        btnDecline = findViewById(R.id.btn_decline);
        outgoingCallInfo = findViewById(R.id.outgoing_call_info);
        ringingAnimationContainer = findViewById(R.id.ringing_animation_container);
        ripple1 = findViewById(R.id.ripple_1);
        ripple2 = findViewById(R.id.ripple_2);
        ripple3 = findViewById(R.id.ripple_3);
        
        // Set caller information
        if (caller != null) {
            tvCallerName.setText(caller.getUsername());
            
            // Load avatar
            String avatarUrl = caller.getFullAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Picasso.get()
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(ivCallerAvatar);
            }
        }
        
        // Set call type
        if ("audio".equals(callType)) {
            tvCallType.setText("Incoming Audio Call");
        } else {
            tvCallType.setText("Incoming Video Call");
        }
    }
    
    private void setupClickListeners() {
        if (isIncomingCall) {
            // For incoming calls, both accept and decline are available
            btnAccept.setOnClickListener(v -> acceptCall());
            btnDecline.setOnClickListener(v -> declineCall());
        } else {
            // For outgoing calls, only decline (end call) is available
            btnDecline.setOnClickListener(v -> endCall());
        }
    }
    
    private void setupIncomingCall() {
        tvCallerStatus.setText("is calling you...");
        btnAccept.setVisibility(View.VISIBLE);
        btnDecline.setVisibility(View.VISIBLE);
        outgoingCallInfo.setVisibility(View.GONE);
        
        // Start ringing animations
        startRingingAnimations();
        
        // Start ringtone
        startRingtone();
        
        // Auto-decline after 30 seconds if not answered
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isCallActive) {
                declineCall();
            }
        }, 30000);
    }
    
    private void setupOutgoingCall() {
        tvCallerStatus.setText("Calling...");
        btnAccept.setVisibility(View.GONE);
        btnDecline.setVisibility(View.VISIBLE);
        outgoingCallInfo.setVisibility(View.VISIBLE);
        tvRingingStatus.setText("Ringing");
        
        // Start ringing animations
        startRingingAnimations();
        
        // Simulate call ringing
        simulateCallRinging();
    }
    
    private void startRingingAnimations() {
        // Avatar pulse animation
        avatarPulseAnimation = AnimationUtils.loadAnimation(this, R.anim.avatar_pulse);
        ivCallerAvatar.startAnimation(avatarPulseAnimation);
        
        // Ripple animations
        startRippleAnimations();
    }
    
    private void startRippleAnimations() {
        // Create ripple animations with staggered timing
        ObjectAnimator ripple1ScaleX = ObjectAnimator.ofFloat(ripple1, "scaleX", 0.5f, 1.5f);
        ObjectAnimator ripple1ScaleY = ObjectAnimator.ofFloat(ripple1, "scaleY", 0.5f, 1.5f);
        ObjectAnimator ripple1Alpha = ObjectAnimator.ofFloat(ripple1, "alpha", 0.8f, 0.0f);
        
        ObjectAnimator ripple2ScaleX = ObjectAnimator.ofFloat(ripple2, "scaleX", 0.5f, 1.5f);
        ObjectAnimator ripple2ScaleY = ObjectAnimator.ofFloat(ripple2, "scaleY", 0.5f, 1.5f);
        ObjectAnimator ripple2Alpha = ObjectAnimator.ofFloat(ripple2, "alpha", 0.6f, 0.0f);
        
        ObjectAnimator ripple3ScaleX = ObjectAnimator.ofFloat(ripple3, "scaleX", 0.5f, 1.5f);
        ObjectAnimator ripple3ScaleY = ObjectAnimator.ofFloat(ripple3, "scaleY", 0.5f, 1.5f);
        ObjectAnimator ripple3Alpha = ObjectAnimator.ofFloat(ripple3, "alpha", 0.4f, 0.0f);
        
        // Set duration
        ripple1ScaleX.setDuration(2000);
        ripple1ScaleY.setDuration(2000);
        ripple1Alpha.setDuration(2000);
        
        ripple2ScaleX.setDuration(2000);
        ripple2ScaleY.setDuration(2000);
        ripple2Alpha.setDuration(2000);
        
        ripple3ScaleX.setDuration(2000);
        ripple3ScaleY.setDuration(2000);
        ripple3Alpha.setDuration(2000);
        
        // Create animator sets
        AnimatorSet ripple1Set = new AnimatorSet();
        ripple1Set.playTogether(ripple1ScaleX, ripple1ScaleY, ripple1Alpha);
        
        AnimatorSet ripple2Set = new AnimatorSet();
        ripple2Set.playTogether(ripple2ScaleX, ripple2ScaleY, ripple2Alpha);
        
        AnimatorSet ripple3Set = new AnimatorSet();
        ripple3Set.playTogether(ripple3ScaleX, ripple3ScaleY, ripple3Alpha);
        
        // Stagger the animations
        rippleAnimatorSet = new AnimatorSet();
        rippleAnimatorSet.play(ripple1Set).after(0);
        rippleAnimatorSet.play(ripple2Set).after(500);
        rippleAnimatorSet.play(ripple3Set).after(1000);
        
        // Repeat infinitely
        rippleAnimatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (isCallActive) {
                    rippleAnimatorSet.start();
                }
            }
        });
        
        rippleAnimatorSet.start();
    }
    
    private void simulateCallRinging() {
        // Simulate call ringing for outgoing calls
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isCallActive) {
                // Simulate call answered
                tvRingingStatus.setText("Connected");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isCallActive) {
                        acceptCall();
                    }
                }, 1000);
            }
        }, 3000);
    }
    
    private void acceptCall() {
        isCallActive = true;
        stopRingingAnimations();
        stopRingtone();
        
        // Call API to accept call
        String token = sharedPrefsManager.getToken();
        apiClient.joinCall(token, callId, new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to accept call", e);
                    finish();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            openVideoCallActivity();
                        } catch (JSONException e) {
                            Log.e(TAG, "Error opening video call activity", e);
                            finish();
                        }
                    } else {
                        Log.e(TAG, "Failed to accept call: " + response.code());
                        finish();
                    }
                });
            }
        });
    }
    
    private void declineCall() {
        isCallActive = true;
        stopRingingAnimations();
        stopRingtone();
        
        // Call API to decline call
        String token = sharedPrefsManager.getToken();
        apiClient.declineCall(token, callId, new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to decline call", e);
                    finish();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    Log.d(TAG, "Call declined: " + response.code());
                    finish();
                });
            }
        });
    }
    
    private void endCall() {
        stopRingtone();
        String token = sharedPrefsManager.getToken();
        apiClient.endCall(token, callId, new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Failed to end call", e);
                    finish();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    Log.d(TAG, "Call ended: " + response.code());
                    finish();
                });
            }
        });
    }
    
    private void openVideoCallActivity() throws JSONException {
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra("chat", currentChat.toJson().toString());
        intent.putExtra("caller", caller.toJson().toString());
        intent.putExtra("callId", callId);
        intent.putExtra("callType", callType);
        intent.putExtra("isIncomingCall", isIncomingCall); // Keep the original call type
        
        startActivity(intent);
        finish();
    }
    
    private void stopRingingAnimations() {
        if (avatarPulseAnimation != null) {
            ivCallerAvatar.clearAnimation();
        }
        
        if (rippleAnimatorSet != null) {
            rippleAnimatorSet.cancel();
        }
    }
    
    private void startRingtone() {
        try {
            // Stop any existing ringtone
            stopRingtone();
            
            // Create MediaPlayer for ringtone
            ringtonePlayer = new MediaPlayer();
            
            // Set audio attributes for ringtone
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            
            ringtonePlayer.setAudioAttributes(audioAttributes);
            
            // Set volume to maximum for ringtone
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0);
            
            // Use default ringtone URI
            android.net.Uri ringtoneUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
            ringtonePlayer.setDataSource(this, ringtoneUri);
            
            // Set looping
            ringtonePlayer.setLooping(true);
            
            // Prepare and start
            ringtonePlayer.prepare();
            ringtonePlayer.start();
            
            Log.d(TAG, "Ringtone started");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ringtone", e);
            // Fallback: try to use system ringtone
            try {
                android.media.Ringtone ringtone = android.media.RingtoneManager.getRingtone(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
                if (ringtone != null) {
                    ringtone.play();
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to play system ringtone", ex);
            }
        }
    }
    
    private void stopRingtone() {
        try {
            if (ringtonePlayer != null) {
                if (ringtonePlayer.isPlaying()) {
                    ringtonePlayer.stop();
                }
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
            Log.d(TAG, "Ringtone stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop ringtone", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingingAnimations();
        stopRingtone();
    }
    
    @Override
    public void onBackPressed() {
        // Decline call when back button is pressed
        declineCall();
    }
}