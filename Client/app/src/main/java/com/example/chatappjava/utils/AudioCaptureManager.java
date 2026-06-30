package com.example.chatappjava.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Manager for continuously capturing audio from the microphone
 */
public class AudioCaptureManager {
    private static final String TAG = "AudioCaptureManager";
    
    // Audio configuration for voice calls
    private static final int SAMPLE_RATE = 16000; // 16kHz for voice calls (good quality, lower bandwidth)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // Mono for voice
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 16-bit PCM
    private static final int BUFFER_SIZE_MULTIPLIER = 2; // Buffer size multiplier for smooth capture
    
    private AudioRecord audioRecord;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private AudioCaptureCallback callback;
    private boolean isCapturing = false;
    private int bufferSize;
    
    public interface AudioCaptureCallback {
        void onAudioCaptured(byte[] audioData, int sampleRate);
    }
    
    public AudioCaptureManager() {
        // Calculate buffer size
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            bufferSize = SAMPLE_RATE * 2; // Fallback: 1 second buffer
            Log.w(TAG, "Using fallback buffer size: " + bufferSize);
        }
        bufferSize *= BUFFER_SIZE_MULTIPLIER; // Multiply for smoother capture
    }
    
    /**
     * Start audio capture
     */
    public void startCapture(AudioCaptureCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Audio capture already in progress");
            return;
        }
        
        this.callback = callback;
        
        try {
            // Initialize AudioRecord
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Optimized for voice calls
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return;
            }
            
            // Start recording
            audioRecord.startRecording();
            isCapturing = true;
            
            // Start capture thread
            startCaptureThread();
            
            Log.d(TAG, "Audio capture started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio capture", e);
            isCapturing = false;
        }
    }
    
    /**
     * Stop audio capture
     */
    public void stopCapture() {
        if (!isCapturing) {
            return;
        }
        
        isCapturing = false;
        
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio capture", e);
            }
        }
        
        stopCaptureThread();
        Log.d(TAG, "Audio capture stopped");
    }
    
    /**
     * Start capture thread to continuously read audio data
     */
    private void startCaptureThread() {
        captureThread = new HandlerThread("AudioCaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        
        captureHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isCapturing || audioRecord == null) {
                    return;
                }
                
                try {
                    byte[] buffer = new byte[bufferSize];
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    
                    if (bytesRead > 0 && callback != null) {
                        // Create a copy of the actual data read
                        byte[] audioData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                        callback.onAudioCaptured(audioData, SAMPLE_RATE);
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord read error: ERROR_INVALID_OPERATION");
                        return;
                    } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord read error: ERROR_BAD_VALUE");
                        return;
                    }
                    
                    // Continue reading
                    if (isCapturing && captureHandler != null) {
                        captureHandler.post(this);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading audio data", e);
                    // Continue reading even on error
                    if (isCapturing && captureHandler != null) {
                        captureHandler.post(this);
                    }
                }
            }
        });
    }
    
    /**
     * Stop capture thread
     */
    private void stopCaptureThread() {
        if (captureThread != null) {
            captureThread.quitSafely();
            try {
                captureThread.join();
                captureThread = null;
                captureHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping capture thread", e);
            }
        }
    }
    
    /**
     * Check if capturing
     */
    public boolean isCapturing() {
        return isCapturing;
    }
    
    /**
     * Get sample rate
     */
    public int getSampleRate() {
        return SAMPLE_RATE;
    }
}
