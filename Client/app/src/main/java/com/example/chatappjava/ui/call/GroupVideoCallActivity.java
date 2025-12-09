package com.example.chatappjava.ui.call;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.CustomVideoParticipantAdapter;
import com.example.chatappjava.models.CallParticipant;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.network.SocketManager;
import com.example.chatappjava.utils.CameraCaptureManager;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.VideoFrameEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Activité pour les appels vidéo de groupe personnalisés (sans WebRTC)
 * Capture continuellement des frames depuis la caméra, les encode et les envoie au serveur
 */
public class GroupVideoCallActivity extends AppCompatActivity {
    private static final String TAG = "GroupVideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FRAME_CAPTURE_INTERVAL_MS = 100; // 10 FPS
    
    // UI Components
    private RecyclerView rvVideoGrid;
    private LinearLayout emptyState;
    private LinearLayout loadingOverlay;
    private TextView tvLoadingMessage;
    private TextView tvGroupName;
    private TextView tvParticipantCount;
    private TextView tvCallDuration;
    private ImageButton btnMute;
    private ImageButton btnCameraToggle;
    private ImageButton btnSwitchCamera;
    private ImageButton btnEndCall;
    
    // Data
    private String callId;
    private String chatId;
    private String groupName;
    private String currentUserId;
    private List<CallParticipant> participants;
    private CustomVideoParticipantAdapter adapter;
    
    // Managers
    private DatabaseManager databaseManager;
    private ApiClient apiClient;
    private SocketManager socketManager;
    private CameraCaptureManager cameraCaptureManager;
    
    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isCallActive = false;
    private AtomicBoolean isSendingFrame = new AtomicBoolean(false);
    private Handler frameCaptureHandler;
    private Runnable frameCaptureRunnable;
    private long callStartTime;
    private Handler callDurationHandler;
    private Runnable callDurationRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_video_call);
        
        // Obtenir les données de l'intent
        getIntentData();
        
        // Initialiser les managers
        databaseManager = new DatabaseManager(this);
        apiClient = new ApiClient();
        socketManager = SocketManager.getInstance();
        currentUserId = databaseManager.getUserId();
        
        // Initialiser les vues
        initViews();
        
        // Vérifier les permissions
        if (checkPermissions()) {
            initializeCall();
        } else {
            requestPermissions();
        }
    }
    
    private void getIntentData() {
        callId = getIntent().getStringExtra("callId");
        chatId = getIntent().getStringExtra("chatId");
        groupName = getIntent().getStringExtra("groupName");
        
        if (callId == null || chatId == null) {
            Toast.makeText(this, "Données d'appel invalides", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void initViews() {
        rvVideoGrid = findViewById(R.id.rv_video_grid);
        emptyState = findViewById(R.id.empty_state);
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvLoadingMessage = findViewById(R.id.tv_loading_message);
        tvGroupName = findViewById(R.id.tv_group_name);
        tvParticipantCount = findViewById(R.id.tv_participant_count);
        tvCallDuration = findViewById(R.id.tv_call_duration);
        btnMute = findViewById(R.id.btn_mute);
        btnCameraToggle = findViewById(R.id.btn_camera_toggle);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnEndCall = findViewById(R.id.btn_end_call);
        
        if (groupName != null) {
            tvGroupName.setText(groupName);
        }
        
        // Configurer le RecyclerView avec GridLayoutManager
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        rvVideoGrid.setLayoutManager(layoutManager);
        
        participants = new ArrayList<>();
        adapter = new CustomVideoParticipantAdapter(this, participants);
        rvVideoGrid.setAdapter(adapter);
        
        // Configurer les listeners
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        btnMute.setOnClickListener(v -> toggleMute());
        btnCameraToggle.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnEndCall.setOnClickListener(v -> endCall());
    }
    
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                initializeCall();
            } else {
                Toast.makeText(this, "Les permissions sont nécessaires pour l'appel vidéo", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void initializeCall() {
        showLoading("Connexion à l'appel...");
        
        // CRITICAL: Setup listeners TRƯỚC khi join room để không bỏ lỡ event call_room_joined
        setupSocketListeners();
        
        // Thêm local participant trước
        addLocalParticipant();
        
        // Rejoindre la salle d'appel via socket (sẽ trigger call_room_joined với danh sách participants)
        socketManager.joinCallRoom(callId);
        
        // Démarrer la capture vidéo
        startVideoCapture();
        
        // Démarrer le chronomètre
        startCallDurationTimer();
        
        hideLoading();
        isCallActive = true;
    }
    
    private void setupSocketListeners() {
        // Listener pour les frames vidéo reçues
        socketManager.setVideoFrameListener(new SocketManager.VideoFrameListener() {
            @Override
            public void onVideoFrameReceived(String userId, String base64Frame, long timestamp) {
                Log.d(TAG, "Received video frame from user: " + userId);
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.updateVideoFrame(userId, base64Frame);
                    } else {
                        Log.w(TAG, "Adapter is null, cannot update video frame");
                    }
                });
            }
        });
        
        // CRITICAL: Listener pour call_room_joined - load danh sách participants hiện có
        socketManager.on("call_room_joined", args -> {
            JSONObject data = (JSONObject) args[0];
            JSONArray participantsArray = data.optJSONArray("participants");

            if (participantsArray != null) {
                Log.d(TAG, "Received call_room_joined with " + participantsArray.length() + " participants");
                runOnUiThread(() -> {
                    // CRITICAL: Xóa tất cả participants cũ (trừ local participant) trước khi load danh sách mới
                    // Điều này đảm bảo chỉ hiển thị những người đang thực sự trong call
                    List<CallParticipant> toRemove = new ArrayList<>();
                    for (CallParticipant p : participants) {
                        if (!p.isLocal() && p.getUserId() != null && !p.getUserId().equals(currentUserId)) {
                            toRemove.add(p);
                        }
                    }
                    for (CallParticipant p : toRemove) {
                        participants.remove(p);
                    }
                    
                    // Load tất cả participants hiện có từ server (chỉ những người đã join room)
                    for (int i = 0; i < participantsArray.length(); i++) {
                        try {
                            JSONObject participantObj = participantsArray.getJSONObject(i);
                            String userId;

                            // Handle both ObjectId and populated object
                            if (participantObj.has("userId")) {
                                Object userIdObj = participantObj.get("userId");
                                if (userIdObj instanceof JSONObject) {
                                    JSONObject userIdJson = (JSONObject) userIdObj;
                                    userId = userIdJson.optString("_id", userIdJson.optString("id", ""));
                                } else {
                                    userId = userIdObj.toString();
                                }
                            } else {
                                continue;
                            }

                            // Skip local participant (đã được thêm trong addLocalParticipant)
                            if (userId != null && userId.equals(currentUserId)) {
                                continue;
                            }

                            String username = participantObj.optString("username", "");
                            String avatar = participantObj.optString("avatar", "");

                            // Thêm participant (đã được filter ở server - chỉ những người đã join)
                            addParticipant(userId, username, avatar);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing participant at index " + i, e);
                        }
                    }
                    
                    // Update adapter sau khi load xong
                    adapter.notifyDataSetChanged();
                    updateParticipantCount();
                });
            }
        });
        
        // Listener pour les participants qui rejoignent
        socketManager.on("user_joined_call", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");
                String username = data.getString("username");
                String avatar = data.optString("avatar", "");
                
                runOnUiThread(() -> {
                    addParticipant(userId, username, avatar);
                });
            } catch (JSONException e) {
                Log.e(TAG, "Erreur lors de la réception de user_joined_call", e);
            }
        });
        
        // Listener pour les participants qui quittent
        socketManager.on("user_left_call", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");
                
                runOnUiThread(() -> {
                    removeParticipant(userId);
                });
            } catch (JSONException e) {
                Log.e(TAG, "Erreur lors de la réception de user_left_call", e);
            }
        });
    }
    
    // Method này không còn cần thiết vì participants được load từ call_room_joined event
    // Giữ lại để tương thích nếu có code khác gọi
    private void loadParticipants() {
        // Participants sẽ được load từ event call_room_joined
        // Chỉ thêm local participant nếu chưa có
        boolean hasLocal = false;
        for (CallParticipant p : participants) {
            if (p.isLocal() && p.getUserId() != null && p.getUserId().equals(currentUserId)) {
                hasLocal = true;
                break;
            }
        }
        if (!hasLocal) {
            addLocalParticipant();
        }
    }
    
    private void addLocalParticipant() {
        CallParticipant localParticipant = new CallParticipant();
        localParticipant.setUserId(currentUserId);
        localParticipant.setUsername(databaseManager.getUserName());
        localParticipant.setAvatar(databaseManager.getUserAvatar());
        localParticipant.setLocal(true);
        localParticipant.setAudioMuted(isMuted);
        localParticipant.setVideoMuted(!isCameraOn);
        
        participants.add(localParticipant);
        adapter.notifyDataSetChanged();
        updateParticipantCount();
    }
    
    private void addParticipant(String userId, String username, String avatar) {
        // Vérifier si le participant existe déjà
        for (CallParticipant p : participants) {
            if (p.getUserId() != null && p.getUserId().equals(userId)) {
                return; // Déjà présent
            }
        }
        
        CallParticipant participant = new CallParticipant();
        participant.setUserId(userId);
        participant.setUsername(username);
        participant.setAvatar(avatar);
        participant.setLocal(false);
        
        participants.add(participant);
        adapter.notifyDataSetChanged();
        updateParticipantCount();
        
        if (emptyState.getVisibility() == View.VISIBLE) {
            emptyState.setVisibility(View.GONE);
        }
    }
    
    private void removeParticipant(String userId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getUserId() != null && 
                participants.get(i).getUserId().equals(userId)) {
                participants.remove(i);
                adapter.notifyItemRemoved(i);
                updateParticipantCount();
                
                if (participants.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                }
                break;
            }
        }
    }
    
    private void updateParticipantCount() {
        int count = participants.size();
        tvParticipantCount.setText(count + " participant" + (count > 1 ? "s" : ""));
    }
    
    private void startVideoCapture() {
        if (!isCameraOn) {
            return;
        }
        
        cameraCaptureManager = new CameraCaptureManager(this);
        cameraCaptureManager.startCapture(new CameraCaptureManager.FrameCaptureCallback() {
            @Override
            public void onFrameCaptured(byte[] frameData, int width, int height) {
                if (frameData != null && isCallActive && isCameraOn) {
                    sendVideoFrame(frameData);
                }
            }
        });
        
        // Démarrer la capture périodique
        frameCaptureHandler = new Handler(Looper.getMainLooper());
        frameCaptureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallActive && isCameraOn && cameraCaptureManager != null && cameraCaptureManager.isCapturing()) {
                    // La capture est continue, on envoie les frames périodiquement
                    frameCaptureHandler.postDelayed(this, FRAME_CAPTURE_INTERVAL_MS);
                }
            }
        };
        frameCaptureHandler.post(frameCaptureRunnable);
    }
    
    private void sendVideoFrame(byte[] frameData) {
        // Éviter d'envoyer plusieurs frames en même temps
        if (!isSendingFrame.compareAndSet(false, true)) {
            return;
        }
        
        new Thread(() -> {
            try {
                // Encoder la frame en base64
                String base64Frame = VideoFrameEncoder.encodeFrame(frameData);
                
                if (base64Frame != null) {
                    // CRITICAL: Update local participant video frame immediately
                    // This ensures the user sees their own video
                    runOnUiThread(() -> {
                        if (adapter != null && currentUserId != null) {
                            adapter.updateVideoFrame(currentUserId, base64Frame);
                        }
                    });
                    
                    // Send to server to forward to other participants
                    if (socketManager != null) {
                        Log.d(TAG, "Sending video frame to server for callId: " + callId);
                        socketManager.sendVideoFrame(callId, base64Frame);
                    } else {
                        Log.w(TAG, "SocketManager is null, cannot send video frame");
                    }
                } else {
                    Log.w(TAG, "Failed to encode video frame");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'envoi de la frame", e);
            } finally {
                isSendingFrame.set(false);
            }
        }).start();
    }
    
    private void toggleMute() {
        isMuted = !isMuted;
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        
        // Mettre à jour le participant local
        for (CallParticipant p : participants) {
            if (p.isLocal()) {
                p.setAudioMuted(isMuted);
                adapter.notifyItemChanged(participants.indexOf(p));
                break;
            }
        }
        
        // Envoyer la mise à jour au serveur
        updateMediaState();
    }
    
    private void toggleCamera() {
        isCameraOn = !isCameraOn;
        btnCameraToggle.setImageResource(isCameraOn ? R.drawable.ic_video_call : R.drawable.ic_video_off);
        
        if (isCameraOn) {
            startVideoCapture();
        } else {
            stopVideoCapture();
        }
        
        // Mettre à jour le participant local
        for (CallParticipant p : participants) {
            if (p.isLocal()) {
                p.setVideoMuted(!isCameraOn);
                adapter.notifyItemChanged(participants.indexOf(p));
                break;
            }
        }
        
        // Envoyer la mise à jour au serveur
        updateMediaState();
    }
    
    private void switchCamera() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.switchCamera();
        }
    }
    
    private void stopVideoCapture() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        
        if (frameCaptureHandler != null && frameCaptureRunnable != null) {
            frameCaptureHandler.removeCallbacks(frameCaptureRunnable);
        }
    }
    
    private void updateMediaState() {
        // Envoyer la mise à jour de l'état média au serveur
        // Note: Vous devrez peut-être créer cette méthode dans ApiClient
    }
    
    private void startCallDurationTimer() {
        callStartTime = System.currentTimeMillis();
        callDurationHandler = new Handler(Looper.getMainLooper());
        callDurationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCallActive) {
                    long duration = System.currentTimeMillis() - callStartTime;
                    long seconds = duration / 1000;
                    long minutes = seconds / 60;
                    seconds = seconds % 60;
                    
                    tvCallDuration.setText(String.format("%02d:%02d", minutes, seconds));
                    callDurationHandler.postDelayed(this, 1000);
                }
            }
        };
        callDurationHandler.post(callDurationRunnable);
    }
    
    private void endCall() {
        isCallActive = false;
        
        // Arrêter la capture vidéo
        stopVideoCapture();
        
        // Quitter la salle d'appel
        if (socketManager != null) {
            socketManager.leaveCallRoom(callId);
            socketManager.removeVideoFrameListener();
        }
        
        // Arrêter le chronomètre
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
        
        // Appeler l'API pour quitter l'appel
        String token = databaseManager.getToken();
        if (token != null && callId != null) {
            // Note: Vous devrez peut-être créer cette méthode dans ApiClient
            // apiClient.leaveGroupCall(token, callId, ...);
        }
        
        finish();
    }
    
    private void showLoading(String message) {
        loadingOverlay.setVisibility(View.VISIBLE);
        if (message != null) {
            tvLoadingMessage.setText(message);
        }
    }
    
    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Nettoyer les ressources
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        
        if (socketManager != null) {
            socketManager.removeVideoFrameListener();
            socketManager.off("user_joined_call");
            socketManager.off("user_left_call");
        }
        
        if (adapter != null) {
            adapter.clearVideoFrames();
        }
        
        if (frameCaptureHandler != null && frameCaptureRunnable != null) {
            frameCaptureHandler.removeCallbacks(frameCaptureRunnable);
        }
        
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
    }
}
