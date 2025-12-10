package com.example.chatappjava.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Manager for continuously capturing audio from the microphone
 */
public class AudioCaptureManager {
    private static final String TAG = "AudioCaptureManager";
    
    private AudioRecord audioRecord;
    private Thread captureThread;
    private boolean isCapturing = false;
    private AudioCaptureCallback audioCallback;
    
    // Audio configuration - optimized for LOW LATENCY
    // CRITICAL: Reduced sample rate to 16kHz for lower latency
    // Lower sample rate = smaller buffers = less delay = lower latency
    // 16kHz is sufficient for voice calls and provides lowest latency
    private static final int SAMPLE_RATE = 16000; // 16 kHz for lowest latency (reduced from 24 kHz)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // CRITICAL: Use smaller buffer (1x minimum) for lower latency
    // Smaller buffer = less delay but more frequent callbacks
    // This ensures audio keeps up with 30 FPS video
    private static final int BUFFER_SIZE_MULTIPLIER = 1;
    
    private int bufferSize;
    
    /**
     * Callback interface for audio capture
     */
    public interface AudioCaptureCallback {
        void onAudioCaptured(byte[] audioData, int bytesRead);
    }
    
    public AudioCaptureManager() {
        // Calculate minimum buffer size
        // CRITICAL: Use absolute minimum buffer for lowest latency
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size");
            bufferSize = SAMPLE_RATE / 50; // Fallback: 20ms of audio (not 1 second!)
        }
        // Use minimum buffer (1x) for lowest latency
        // Target: 20-40ms buffer for ultra-low latency
        bufferSize *= BUFFER_SIZE_MULTIPLIER;
        Log.d(TAG, "Audio capture buffer size: " + bufferSize + " bytes (~" + (bufferSize * 1000 / (SAMPLE_RATE * 2)) + "ms)");
    }
    
    /**
     * Start capturing audio
     */
    public void startCapture(AudioCaptureCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Audio capture already started");
            return;
        }
        
        this.audioCallback = callback;
        
        try {
            // CRITICAL: Initialize AudioRecord with minimum buffer for lowest latency
            // Minimum buffer = minimum capture delay = lowest latency
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize // Minimum buffer size for lowest latency
            );
            
            // CRITICAL: Set performance mode for low latency if available (API 21+)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    audioRecord.setPreferredDevice(null); // Use default device
                    // Note: AudioRecord doesn't have setPerformanceMode, but we can optimize buffer
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not optimize AudioRecord (may not be supported)", e);
            }
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return;
            }
            
            // Start recording
            audioRecord.startRecording();
            isCapturing = true;
            
            // Start capture thread
            captureThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    captureAudio();
                }
            });
            captureThread.start();
            
            Log.d(TAG, "Audio capture started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio capture", e);
            isCapturing = false;
        }
    }
    
    /**
     * Capture audio in background thread
     */
    private void captureAudio() {
        byte[] buffer = new byte[bufferSize];
        
        while (isCapturing && audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0 && audioCallback != null) {
                    // CRITICAL: Zero-copy optimization - pass buffer directly if possible
                    // Only create copy if callback needs to process asynchronously
                    // For immediate processing, pass buffer directly to avoid copy overhead
                    if (bytesRead == buffer.length) {
                        // Full buffer read - can pass directly (zero-copy)
                        audioCallback.onAudioCaptured(buffer, bytesRead);
                    } else {
                        // Partial read - need to create sub-array (minimal copy)
                        byte[] audioData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                        audioCallback.onAudioCaptured(audioData, bytesRead);
                    }
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord read error: ERROR_INVALID_OPERATION");
                    break;
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "AudioRecord read error: ERROR_BAD_VALUE");
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading audio data", e);
                break;
            }
        }
        
        Log.d(TAG, "Audio capture thread ended");
    }
    
    /**
     * Stop capturing audio
     */
    public void stopCapture() {
        if (!isCapturing) {
            return;
        }
        
        isCapturing = false;
        
        try {
            if (audioRecord != null) {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio capture", e);
        }
        
        // Wait for thread to finish
        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting for capture thread", e);
            }
            captureThread = null;
        }
        
        audioCallback = null;
        Log.d(TAG, "Audio capture stopped");
    }
    
    /**
     * Check if currently capturing
     */
    public boolean isCapturing() {
        return isCapturing;
    }
}
