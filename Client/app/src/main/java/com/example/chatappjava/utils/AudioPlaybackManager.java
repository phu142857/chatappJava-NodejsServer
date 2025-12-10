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
    // CRITICAL: Reduced sample rate to 16kHz for lower latency
    // Lower sample rate = smaller buffers = less delay = lower latency
    private static final int SAMPLE_RATE = 16000; // 16 kHz for lowest latency (reduced from 24 kHz)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private int bufferSize;
    
    public AudioPlaybackManager() {
        // Calculate minimum buffer size
        // CRITICAL: Use absolute minimum buffer for lowest latency
        // For 16kHz, minimum is typically ~320 bytes (20ms)
        bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
            Log.e(TAG, "Invalid buffer size");
            bufferSize = SAMPLE_RATE / 50; // Fallback: 20ms of audio (not 1 second!)
        }
        // Use minimum buffer (not multiplied) for lowest latency
        // Target: 20-40ms buffer for ultra-low latency
        Log.d(TAG, "Audio playback buffer size: " + bufferSize + " bytes (~" + (bufferSize * 1000 / (SAMPLE_RATE * 2)) + "ms)");
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
            // Initialize AudioTrack with minimum buffer for lowest latency
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize == AudioTrack.ERROR_BAD_VALUE || minBufferSize == AudioTrack.ERROR) {
                Log.e(TAG, "Invalid buffer size: " + minBufferSize);
                bufferSize = SAMPLE_RATE * 2; // Fallback: 1 second
            } else {
                // CRITICAL: Use minimum buffer (1x) for lowest latency
                // Trade-off: Lower latency but may need more frequent writes
                bufferSize = minBufferSize;
            }
            
            // CRITICAL: Use MODE_STREAM with minimum buffer for lowest latency
            // MODE_STREAM allows continuous writing without pre-buffering
            // Minimum buffer size = minimum jitter buffer = lowest latency
            // CRITICAL: Use MODE_STREAM with minimum buffer for lowest latency
            // MODE_STREAM allows continuous writing without pre-buffering
            // Minimum buffer size = minimum jitter buffer = lowest latency
            audioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize, // Minimum buffer size for lowest latency
                AudioTrack.MODE_STREAM // Stream mode for continuous playback
            );
            
            // Note: setPerformanceMode() is not available in standard AudioTrack API
            // Low latency is achieved through minimum buffer size and MODE_STREAM
            
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
            // CRITICAL: Check AudioTrack state before any operations
            if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized, skipping audio playback");
                return;
            }
            
            // CRITICAL: Adaptive flush strategy - only flush when buffer is getting full
            // Flushing every frame causes audio glitches and errors
            // Target: 40-80ms jitter buffer (flush only when needed)
            try {
                int bufferSizeInFrames = audioTrack.getBufferSizeInFrames();
                if (bufferSizeInFrames > 0) {
                    // Check if buffer is more than 50% full
                    int framesWritten = audioTrack.getPlaybackHeadPosition();
                    int bufferUsagePercent = (framesWritten % bufferSizeInFrames) * 100 / bufferSizeInFrames;
                    
                    // Flush only if buffer is more than 50% full (target: keep below 50%)
                    if (bufferUsagePercent > 50) {
                        // CRITICAL: Check state before flush to avoid IllegalStateException
                        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED && 
                            audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.flush();
                            Log.d(TAG, "Flushed audio buffer (usage: " + bufferUsagePercent + "%)");
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore buffer check errors - not critical
                Log.d(TAG, "Could not check buffer level", e);
            }
            
            // CRITICAL: Write audio data immediately
            // AudioTrack.write() is non-blocking in MODE_STREAM
            int written = audioTrack.write(audioData, 0, audioData.length);
            if (written < 0) {
                Log.e(TAG, "Error writing audio data: " + written);
            } else if (written < audioData.length) {
                // Partial write is normal when buffer is full
                Log.d(TAG, "Partial write: " + written + " of " + audioData.length + " bytes");
            }
        } catch (IllegalStateException e) {
            // CRITICAL: Handle IllegalStateException (e.g., AudioTrack was released)
            Log.e(TAG, "AudioTrack illegal state - may have been released", e);
            // Don't crash - just log and continue
            // AudioTrack will be reinitialized on next startPlayback()
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
            // Don't crash - just log and continue
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
