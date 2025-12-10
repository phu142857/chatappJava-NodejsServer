package com.example.chatappjava.utils;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Manager for playing audio received from remote users
 */
public class AudioPlaybackManager {
    private static final String TAG = "AudioPlaybackManager";
    
    private AudioTrack audioTrack;
    private boolean isPlaying = false;
    
    // Audio configuration (must match AudioCaptureManager)
    private static final int SAMPLE_RATE = 16000; // 16 kHz for voice
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private int bufferSize;
    
    public AudioPlaybackManager() {
        // Calculate minimum buffer size
        bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
            Log.e(TAG, "Invalid buffer size");
            bufferSize = SAMPLE_RATE * 2; // Fallback: 1 second of audio
        }
    }
    
    /**
     * Start audio playback
     */
    public void startPlayback() {
        if (isPlaying) {
            Log.w(TAG, "Audio playback already started");
            return;
        }
        
        try {
            // Initialize AudioTrack
            audioTrack = new AudioTrack(
                android.media.AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed");
                return;
            }
            
            // Start playback
            audioTrack.play();
            isPlaying = true;
            
            Log.d(TAG, "Audio playback started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio playback", e);
            isPlaying = false;
        }
    }
    
    /**
     * Play audio data
     */
    public void playAudio(byte[] audioData) {
        if (!isPlaying || audioTrack == null || audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        
        try {
            int written = audioTrack.write(audioData, 0, audioData.length);
            if (written < 0) {
                Log.e(TAG, "Error writing audio data: " + written);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
        }
    }
    
    /**
     * Stop audio playback
     */
    public void stopPlayback() {
        if (!isPlaying) {
            return;
        }
        
        isPlaying = false;
        
        try {
            if (audioTrack != null) {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
                audioTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio playback", e);
        }
        
        Log.d(TAG, "Audio playback stopped");
    }
    
    /**
     * Check if currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }
}
