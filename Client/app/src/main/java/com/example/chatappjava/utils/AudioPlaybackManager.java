package com.example.chatappjava.utils;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for playing audio from remote participants
 */
public class AudioPlaybackManager {
    private static final String TAG = "AudioPlaybackManager";
    
    // Audio configuration - must match AudioCaptureManager
    private static final int SAMPLE_RATE = 16000; // 16kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO; // Mono output
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 16-bit PCM
    private static final int BUFFER_SIZE_MULTIPLIER = 2;
    
    // Map to store AudioTrack instances for each user
    private Map<String, AudioTrack> audioTracks = new ConcurrentHashMap<>();
    private Map<String, HandlerThread> playbackThreads = new ConcurrentHashMap<>();
    private Map<String, Handler> playbackHandlers = new ConcurrentHashMap<>();
    private Map<String, Boolean> isPlaying = new ConcurrentHashMap<>();
    
    /**
     * Start playing audio for a specific user
     */
    public void startPlayback(String userId, int sampleRate) {
        if (isPlaying.getOrDefault(userId, false)) {
            return; // Already playing
        }
        
        try {
            // Calculate buffer size
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
                bufferSize = sampleRate * 2; // Fallback: 1 second buffer
                Log.w(TAG, "Using fallback buffer size: " + bufferSize);
            }
            bufferSize *= BUFFER_SIZE_MULTIPLIER;
            
            // Create AudioTrack with optimized attributes for voice calls
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            
            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_CONFIG)
                .build();
            
            AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed for user: " + userId);
                return;
            }
            
            audioTrack.play();
            audioTracks.put(userId, audioTrack);
            isPlaying.put(userId, true);
            
            Log.d(TAG, "Started audio playback for user: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio playback for user: " + userId, e);
        }
    }
    
    /**
     * Play audio data for a specific user
     */
    public void playAudio(String userId, byte[] audioData) {
        AudioTrack audioTrack = audioTracks.get(userId);
        if (audioTrack == null || !isPlaying.getOrDefault(userId, false)) {
            // Auto-start playback if not started
            if (audioData != null && audioData.length > 0) {
                startPlayback(userId, SAMPLE_RATE);
                audioTrack = audioTracks.get(userId);
            }
        }
        
        if (audioTrack != null && audioData != null && audioData.length > 0) {
            try {
                int written = audioTrack.write(audioData, 0, audioData.length);
                if (written < 0) {
                    Log.e(TAG, "Error writing audio data for user: " + userId + ", error: " + written);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playing audio for user: " + userId, e);
            }
        }
    }
    
    /**
     * Stop playing audio for a specific user
     */
    public void stopPlayback(String userId) {
        AudioTrack audioTrack = audioTracks.get(userId);
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio playback for user: " + userId, e);
            }
            audioTracks.remove(userId);
        }
        
        isPlaying.put(userId, false);
        
        HandlerThread thread = playbackThreads.remove(userId);
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping playback thread for user: " + userId, e);
            }
        }
        playbackHandlers.remove(userId);
        
        Log.d(TAG, "Stopped audio playback for user: " + userId);
    }
    
    /**
     * Stop all audio playback
     */
    public void stopAllPlayback() {
        for (String userId : audioTracks.keySet()) {
            stopPlayback(userId);
        }
        audioTracks.clear();
        isPlaying.clear();
        playbackThreads.clear();
        playbackHandlers.clear();
        Log.d(TAG, "Stopped all audio playback");
    }
    
    /**
     * Check if playing for a specific user
     */
    public boolean isPlaying(String userId) {
        return isPlaying.getOrDefault(userId, false);
    }
}
