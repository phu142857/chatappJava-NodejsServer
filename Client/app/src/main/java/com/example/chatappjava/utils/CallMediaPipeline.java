package com.example.chatappjava.utils;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.example.chatappjava.network.SocketManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared audio/video capture and socket frame transport for 1-1 and group calls.
 */
public class CallMediaPipeline {
    private static final int FRAME_CAPTURE_INTERVAL_MS = 10;

    public interface Host {
        boolean isCallActive();

        boolean isMuted();

        boolean isCameraOn();

        boolean isFrontCamera();

        void setFrontCamera(boolean frontCamera);

        String getCallId();

        void runOnUiThread(Runnable runnable);

        void onLocalVideoFrame(String base64Frame, boolean frontCamera);

        void onCameraUnavailable();

        void onAudioCaptureError(String message);
    }

    private final String logTag;
    private final SocketManager socketManager;
    private final Host host;

    private CameraCaptureManager cameraCaptureManager;
    private AudioCaptureManager audioCaptureManager;
    private AudioPlaybackManager audioPlaybackManager;
    private Handler frameCaptureHandler;
    private Runnable frameCaptureRunnable;
    private ExecutorService processingExecutor;
    private final AtomicBoolean isSendingFrame = new AtomicBoolean(false);
    private final AtomicBoolean isSendingAudio = new AtomicBoolean(false);

    private Handler callDurationHandler;
    private Runnable callDurationRunnable;
    private long callStartTime;

    public CallMediaPipeline(String logTag, SocketManager socketManager, Host host) {
        this.logTag = logTag;
        this.socketManager = socketManager;
        this.host = host;
    }

    public void setupAudioMode(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                return;
            }
            audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);

            int maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int currentMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (currentMusicVolume < maxMusicVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVolume, 0);
            }
            Log.d(logTag, "Audio mode set (MUSIC volume " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + maxMusicVolume + ")");
        } catch (Exception e) {
            Log.e(logTag, "Error setting audio mode", e);
        }
    }

    public void resetAudioMode(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Exception e) {
            Log.e(logTag, "Error resetting audio mode", e);
        }
    }

    public void ensurePlaybackReady() {
        if (audioPlaybackManager == null) {
            audioPlaybackManager = new AudioPlaybackManager();
        }
    }

    public void startVideoCapture(Activity activity) {
        if (!host.isCameraOn()) {
            return;
        }
        try {
            host.setFrontCamera(false);
            cameraCaptureManager = new CameraCaptureManager(activity);
            cameraCaptureManager.startCapture((frameData, width, height) -> {
                try {
                    if (frameData != null && host.isCallActive() && host.isCameraOn()) {
                        sendVideoFrame(frameData);
                    }
                } catch (Exception e) {
                    Log.e(logTag, "Error in video frame callback", e);
                }
            });

            frameCaptureHandler = new Handler(Looper.getMainLooper());
            frameCaptureRunnable = new Runnable() {
                @Override
                public void run() {
                    if (host.isCallActive()
                            && host.isCameraOn()
                            && cameraCaptureManager != null
                            && cameraCaptureManager.isCapturing()) {
                        frameCaptureHandler.postDelayed(this, FRAME_CAPTURE_INTERVAL_MS);
                    }
                }
            };
            frameCaptureHandler.post(frameCaptureRunnable);
        } catch (Exception e) {
            Log.e(logTag, "Error starting video capture", e);
            host.onCameraUnavailable();
        }
    }

    public void stopVideoCapture() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.stopCapture();
        }
        if (frameCaptureHandler != null && frameCaptureRunnable != null) {
            frameCaptureHandler.removeCallbacks(frameCaptureRunnable);
        }
    }

    public void switchCamera() {
        if (cameraCaptureManager != null) {
            cameraCaptureManager.switchCamera();
            host.setFrontCamera(!host.isFrontCamera());
            Log.d(logTag, "Camera switched, isFrontCamera=" + host.isFrontCamera());
        }
    }

    public void startAudioCapture() {
        if (host.isMuted() || !host.isCallActive()) {
            return;
        }
        try {
            if (audioCaptureManager != null) {
                audioCaptureManager.stopCapture();
                audioCaptureManager = null;
            }
            audioCaptureManager = new AudioCaptureManager();
            audioCaptureManager.startCapture((audioData, sampleRate) -> {
                try {
                    if (audioData != null && host.isCallActive() && !host.isMuted()) {
                        sendAudioFrame(audioData);
                    }
                } catch (Exception e) {
                    Log.e(logTag, "Error in audio capture callback", e);
                }
            });
        } catch (Exception e) {
            Log.e(logTag, "Error starting audio capture", e);
            host.onAudioCaptureError(e.getMessage());
        }
    }

    public void stopAudioCapture() {
        if (audioCaptureManager != null) {
            audioCaptureManager.stopCapture();
        }
    }

    public void playRemoteAudio(String userId, String base64Audio) {
        if (!host.isCallActive() || base64Audio == null || base64Audio.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                byte[] audioData = AudioFrameEncoder.decodeFrame(base64Audio);
                if (audioData == null || !host.isCallActive()) {
                    return;
                }
                ensurePlaybackReady();
                if (!audioPlaybackManager.isPlaying(userId)) {
                    audioPlaybackManager.startPlayback(userId, 16000);
                }
                audioPlaybackManager.playAudio(userId, audioData);
            } catch (Exception e) {
                Log.e(logTag, "Error playing remote audio for user: " + userId, e);
            }
        }).start();
    }

    public void startCallDurationTimer(TextView durationView) {
        callStartTime = System.currentTimeMillis();
        callDurationHandler = new Handler(Looper.getMainLooper());
        callDurationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!host.isCallActive()) {
                    return;
                }
                long duration = System.currentTimeMillis() - callStartTime;
                long seconds = duration / 1000;
                long minutes = seconds / 60;
                seconds = seconds % 60;
                durationView.setText(String.format("%02d:%02d", minutes, seconds));
                callDurationHandler.postDelayed(this, 1000);
            }
        };
        callDurationHandler.post(callDurationRunnable);
    }

    public void stopCallDurationTimer() {
        if (callDurationHandler != null && callDurationRunnable != null) {
            callDurationHandler.removeCallbacks(callDurationRunnable);
        }
    }

    public void stopAllPlayback() {
        if (audioPlaybackManager != null) {
            audioPlaybackManager.stopAllPlayback();
        }
    }

    public void release(boolean forceShutdownExecutor) {
        stopVideoCapture();
        stopAudioCapture();
        stopCallDurationTimer();
        stopAllPlayback();
        if (processingExecutor != null && !processingExecutor.isShutdown()) {
            if (forceShutdownExecutor) {
                processingExecutor.shutdownNow();
            } else {
                processingExecutor.shutdown();
            }
            processingExecutor = null;
        }
    }

    private void sendVideoFrame(byte[] frameData) {
        if (!isSendingFrame.compareAndSet(false, true)) {
            return;
        }
        ensureProcessingExecutor("VideoProcessor");
        processingExecutor.execute(() -> {
            try {
                if (!host.isCallActive()) {
                    return;
                }
                String base64Frame = VideoFrameEncoder.encodeFrame(frameData);
                if (base64Frame == null || !host.isCallActive()) {
                    return;
                }
                if (socketManager != null) {
                    socketManager.sendVideoFrame(host.getCallId(), base64Frame);
                }
                final boolean frontCamera = host.isFrontCamera();
                host.runOnUiThread(() -> host.onLocalVideoFrame(base64Frame, frontCamera));
            } catch (Exception e) {
                Log.e(logTag, "Error sending video frame", e);
            } finally {
                isSendingFrame.set(false);
            }
        });
    }

    private void sendAudioFrame(byte[] audioData) {
        if (!isSendingAudio.compareAndSet(false, true)) {
            return;
        }
        ensureProcessingExecutor("AudioProcessor");
        processingExecutor.execute(() -> {
            try {
                if (!host.isCallActive() || host.isMuted()) {
                    return;
                }
                String base64Audio = AudioFrameEncoder.encodeFrame(audioData);
                if (base64Audio != null && host.isCallActive() && !host.isMuted() && socketManager != null) {
                    socketManager.sendAudioFrame(host.getCallId(), base64Audio);
                }
            } catch (Exception e) {
                Log.e(logTag, "Error sending audio frame", e);
            } finally {
                isSendingAudio.set(false);
            }
        });
    }

    private void ensureProcessingExecutor(String threadName) {
        if (processingExecutor == null || processingExecutor.isShutdown()) {
            processingExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, threadName);
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            });
        }
    }
}
