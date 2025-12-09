package com.example.chatappjava.ui.call;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.DatabaseManager;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class RingingActivity extends AppCompatActivity {
    
    private static final String TAG = "RingingActivity";

    private TextView tvCallerStatus;
    private TextView tvRingingStatus;
    private CircleImageView ivCallerAvatar;
    // Swipe UI components
    private FrameLayout swipeThumb;
    private View acceptZone;
    private View declineZone;
    private FrameLayout swipeTrack;
    private LinearLayout outgoingCallInfo;
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
    
    // Synchronization state management
    private boolean isAcceptingCall = false;
    private boolean isDecliningCall = false;
    private final Object callActionLock = new Object();
    
    // Animation
    private AnimatorSet rippleAnimatorSet;
    private Animation avatarPulseAnimation;
    
    // Swipe gesture variables
    private float initialX;
    private float initialThumbX;
    private boolean isDragging = false;
    private int trackWidth;
    private int thumbWidth;
    
    // Audio
    private MediaPlayer ringtonePlayer;
    private AudioManager audioManager;
    
    // Managers
    private DatabaseManager sharedPrefsManager;
    private ApiClient apiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on during ringing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_ringing);
        
        // Initialize managers
        sharedPrefsManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        
        // Initialize audio manager
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // Get call data from intent
        getCallDataFromIntent();
        
        // Initialize UI
        initializeViews();
        setupSwipeGesture();
        
        // Setup call based on type
        if (isIncomingCall) {
            setupIncomingCall();
        } else {
            setupOutgoingCall();
        }
        
        // Setup socket listener for call events
        setupSocketListener();
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
            navigateToHome();
        }
    }
    
    @SuppressLint("SetTextI18n")
    private void initializeViews() {
        // UI Components
        TextView tvCallType = findViewById(R.id.tv_call_type);
        TextView tvCallerName = findViewById(R.id.tv_caller_name);
        tvCallerStatus = findViewById(R.id.tv_caller_status);
        tvRingingStatus = findViewById(R.id.tv_ringing_status);
        ivCallerAvatar = findViewById(R.id.iv_caller_avatar);
        // Swipe components
        swipeThumb = findViewById(R.id.swipe_thumb);
        acceptZone = findViewById(R.id.accept_zone);
        declineZone = findViewById(R.id.decline_zone);
        swipeTrack = findViewById(R.id.swipe_track);
        outgoingCallInfo = findViewById(R.id.outgoing_call_info);
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
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupSwipeGesture() {
        if (swipeThumb == null || swipeTrack == null) return;
        
        // Get dimensions
        swipeTrack.post(() -> {
            trackWidth = swipeTrack.getWidth();
            thumbWidth = swipeThumb.getWidth();
        });
        
        // Set up touch listener for swipe gesture
        swipeThumb.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getRawX();
                        initialThumbX = swipeThumb.getX();
                        isDragging = true;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            float deltaX = event.getRawX() - initialX;
                            float newX = initialThumbX + deltaX;
                            
                            // Constrain movement within track bounds
                            float minX = 0;
                            float maxX = trackWidth - thumbWidth;
                            newX = Math.max(minX, Math.min(maxX, newX));
                            
                            swipeThumb.setX(newX);
                            
                            // Update zone visibility based on position
                            updateZoneVisibility(newX);
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            handleSwipeRelease();
                            isDragging = false;
                        }
                        return true;
                }
                return false;
            }
        });
    }
    
    private void updateZoneVisibility(float thumbX) {
        float centerX = trackWidth / 2f;
        float thumbCenterX = thumbX + thumbWidth / 2f;
        
        if (thumbCenterX < centerX) {
            // In decline zone
            declineZone.setAlpha(0.6f);
            acceptZone.setAlpha(0.1f);
        } else {
            // In accept zone
            acceptZone.setAlpha(0.6f);
            declineZone.setAlpha(0.1f);
        }
    }
    
    private void handleSwipeRelease() {
        float thumbCenterX = swipeThumb.getX() + thumbWidth / 2f;
        float centerX = trackWidth / 2f;
        
        if (thumbCenterX < centerX) {
            // Swiped to decline
            declineCall();
        } else {
            // Swiped to accept
            acceptCall();
        }
        
        // Reset thumb position
        resetThumbPosition();
    }
    
    private void resetThumbPosition() {
        swipeThumb.animate()
            .x(trackWidth / 2f - thumbWidth / 2f)
            .setDuration(200)
            .start();
        
        // Reset zone visibility
        acceptZone.setAlpha(0.3f);
        declineZone.setAlpha(0.3f);
    }
    
    @SuppressLint("SetTextI18n")
    private void setupIncomingCall() {
        tvCallerStatus.setText("is calling you...");
        // Swipe gesture is always visible for incoming calls
        outgoingCallInfo.setVisibility(View.GONE);
        
        // Mark active call to avoid duplicate handling in other screens
        com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        if (sm != null) {
            sm.setActiveCallId(callId);
        }

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
    
    @SuppressLint("SetTextI18n")
    private void setupOutgoingCall() {
        tvCallerStatus.setText("Calling...");
        outgoingCallInfo.setVisibility(View.VISIBLE);
        tvRingingStatus.setText("Ringing");
        
        // Mark active call to avoid duplicate handling across screens
        com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        if (sm != null) {
            sm.setActiveCallId(callId);
        }

        // Start ringing animations
        startRingingAnimations();
        
        // Simulate call ringing
        simulateCallRinging();
    }
    
    private void setupSocketListener() {
        // Get SocketManager instance
        com.example.chatappjava.network.SocketManager socketManager = 
            com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        
        if (socketManager != null) {
            // Set up call status listener
            socketManager.setCallStatusListener(new com.example.chatappjava.network.SocketManager.CallStatusListener() {
                @Override
                public void onCallAccepted(String callId) {
                    runOnUiThread(() -> {
                        if (RingingActivity.this.callId != null && RingingActivity.this.callId.equals(callId)) {
                            Log.d(TAG, "Call accepted");
                            navigateToHome();
                        }
                    });
                }
                
                @Override
                public void onCallDeclined(String callId) {
                    runOnUiThread(() -> {
                        if (RingingActivity.this.callId != null && RingingActivity.this.callId.equals(callId)) {
                            Log.d(TAG, "Call declined by other party");
                            Toast.makeText(RingingActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                            
                            // Set result to indicate call was declined
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("callDeclined", true);
                            resultIntent.putExtra("callId", callId);
                            setResult(RESULT_OK, resultIntent);
                            
                            finish();
                        }
                    });
                }
                
                @Override
                public void onCallEnded(String callId) {
                    runOnUiThread(() -> {
                        if (RingingActivity.this.callId != null && RingingActivity.this.callId.equals(callId)) {
                            Log.d(TAG, "Call ended by other party");
                            Toast.makeText(RingingActivity.this, "Call ended", Toast.LENGTH_SHORT).show();
                            
                            // Set result to indicate call was ended
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("callEnded", true);
                            resultIntent.putExtra("callId", callId);
                            setResult(RESULT_OK, resultIntent);
                            
                            navigateToHome();
                        }
                    });
                }
            });
        }
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
    
    @SuppressLint("SetTextI18n")
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
        synchronized (callActionLock) {
            if (isAcceptingCall || isDecliningCall) {
                Log.d(TAG, "Call action already in progress, ignoring");
                return;
            }
            
            if (isCallActive) {
                Log.d(TAG, "Call already active, ignoring");
                return;
            }
            
            isAcceptingCall = true;
        }
        
        Log.d(TAG, "Accepting call: " + callId);
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
                    synchronized (callActionLock) {
                        isAcceptingCall = false;
                    }
                    navigateToHome();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    synchronized (callActionLock) {
                        isAcceptingCall = false;
                    }
                    
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Call accepted successfully");
                        navigateToHome();
                    } else {
                        Log.e(TAG, "Failed to accept call: " + response.code());
                        navigateToHome();
                    }
                });
            }
        });
    }
    
    private void declineCall() {
        synchronized (callActionLock) {
            if (isAcceptingCall || isDecliningCall) {
                Log.d(TAG, "Call action already in progress, ignoring");
                return;
            }
            
            if (isCallActive) {
                Log.d(TAG, "Call already active, ignoring");
                return;
            }
            
            isDecliningCall = true;
        }
        
        Log.d(TAG, "Declining call: " + callId);
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
                    // Reset active call on failure as well to avoid stuck state
                    com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
                    if (sm != null) sm.resetActiveCall();
                    synchronized (callActionLock) {
                        isDecliningCall = false;
                    }
                    navigateToHome();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Call declined: " + response.code());
                    com.example.chatappjava.network.SocketManager sm = com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
                    if (sm != null) sm.resetActiveCall();
                    synchronized (callActionLock) {
                        isDecliningCall = false;
                    }
                    navigateToHome();
                });
            }
        });
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
    
    private void resetCallActionState() {
        synchronized (callActionLock) {
            isAcceptingCall = false;
            isDecliningCall = false;
            isCallActive = false;
        }
        Log.d(TAG, "Call action state reset");
    }
    
    private void navigateToHome() {
        Log.d(TAG, "Navigating back to HomeActivity");
        
        // Create intent to go back to HomeActivity
        Intent intent = new Intent(this, com.example.chatappjava.ui.theme.HomeActivity.class);
        
        // Clear the activity stack and start fresh from HomeActivity
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingingAnimations();
        stopRingtone();
        
        // Reset call action state
        resetCallActionState();
        
        // Clear socket listener to prevent memory leaks
        com.example.chatappjava.network.SocketManager socketManager = 
            com.example.chatappjava.ChatApplication.getInstance().getSocketManager();
        if (socketManager != null) {
            socketManager.setCallStatusListener(null);
        }
    }
}