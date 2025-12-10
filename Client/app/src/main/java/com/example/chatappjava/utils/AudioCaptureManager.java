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
    
    // Audio configuration
    private static final int SAMPLE_RATE = 16000; // 16 kHz for voice
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_MULTIPLIER = 2;
    
    private int bufferSize;
    
    /**
     * Callback interface for audio capture
     */
    public interface AudioCaptureCallback {
        void onAudioCaptured(byte[] audioData, int bytesRead);
    }
    
    public AudioCaptureManager() {
        // Calculate minimum buffer size
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size");
            bufferSize = SAMPLE_RATE * 2; // Fallback: 1 second of audio
        }
        // Multiply by multiplier for smoother capture
        bufferSize *= BUFFER_SIZE_MULTIPLIER;
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
            // Initialize AudioRecord
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
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
                    // Create a copy of the buffer with actual data size
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    
                    // Call callback on the same thread (caller should handle threading)
                    audioCallback.onAudioCaptured(audioData, bytesRead);
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
