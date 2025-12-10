package com.example.chatappjava.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
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
        if (isPlaying && audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            Log.w(TAG, "Audio playback already started");
            return;
        }
        
        // Stop existing playback if any
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing old AudioTrack", e);
            }
            audioTrack = null;
        }
        
        try {
            // Initialize AudioTrack with larger buffer for smoother playback
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize == AudioTrack.ERROR_BAD_VALUE || minBufferSize == AudioTrack.ERROR) {
                Log.e(TAG, "Invalid buffer size: " + minBufferSize);
                bufferSize = SAMPLE_RATE * 2; // Fallback: 1 second
            } else {
                bufferSize = minBufferSize * 4; // Use 4x minimum for smoother playback
            }
            
            audioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed, state: " + audioTrack.getState());
                return;
            }
            
            // Set volume to maximum
            float maxVolume = AudioTrack.getMaxVolume();
            int setVolumeResult = audioTrack.setVolume(maxVolume);
            Log.d(TAG, "Set AudioTrack volume: " + maxVolume + ", result: " + setVolumeResult);
            
            // Start playback (play() returns void, not int)
            audioTrack.play();
            
            isPlaying = true;
            Log.d(TAG, "Audio playback started successfully, buffer size: " + bufferSize);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio playback", e);
            isPlaying = false;
            if (audioTrack != null) {
                try {
                    audioTrack.release();
                } catch (Exception ex) {
                    Log.e(TAG, "Error releasing AudioTrack after failure", ex);
                }
                audioTrack = null;
            }
        }
    }
    
    /**
     * Play audio data
     */
    public void playAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "Empty audio data received");
            return;
        }
        
        if (audioTrack == null) {
            Log.w(TAG, "AudioTrack is null, cannot play audio");
            return;
        }
        
        if (!isPlaying) {
            Log.w(TAG, "Audio playback not started, starting now...");
            startPlayback();
            if (!isPlaying) {
                Log.e(TAG, "Failed to start playback");
                return;
            }
        }
        
        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            Log.w(TAG, "AudioTrack not playing, state: " + audioTrack.getPlayState() + ", restarting...");
            try {
                audioTrack.play();
            } catch (Exception e) {
                Log.e(TAG, "Error restarting AudioTrack", e);
                return;
            }
        }
        
        try {
            int written = audioTrack.write(audioData, 0, audioData.length);
            if (written < 0) {
                Log.e(TAG, "Error writing audio data: " + written);
            } else if (written < audioData.length) {
                Log.w(TAG, "Partial write: " + written + " of " + audioData.length + " bytes");
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
