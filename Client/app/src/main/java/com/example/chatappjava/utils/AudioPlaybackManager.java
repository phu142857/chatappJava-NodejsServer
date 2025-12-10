package com.example.chatappjava.utils;

import android.content.Context;
import android.media.AudioAttributes;
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
    private int actualBufferSize; // Store actual buffer size for use in restart logic
    
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
                bufferSize = SAMPLE_RATE / 50; // Fallback: 20ms of audio (not 1 second!)
            } else {
                // CRITICAL: Use minimum buffer (1x) for lowest latency
                // Trade-off: Lower latency but may need more frequent writes
                bufferSize = minBufferSize;
            }
            
            // CRITICAL: Use MODE_STREAM with minimum buffer for lowest latency
            // MODE_STREAM allows continuous writing without pre-buffering
            // Minimum buffer size = minimum jitter buffer = lowest latency
            // NOTE: Increased buffer size to 12x minimum to prevent buffer underruns
            // Buffer underruns cause AudioTrack to be disabled and restarted
            // Larger buffer = more tolerance for network latency and write delays = fewer underruns
            // For 16kHz audio: 12x minimum = ~1 second of audio buffer
            // This provides enough buffer to handle network latency, processing delays, and temporary gaps
            this.actualBufferSize = bufferSize * 12; // Use 12x minimum to prevent underruns
            
            // CRITICAL: Use AudioAttributes instead of deprecated stream type
            // AudioAttributes is the modern way to specify audio usage and content type
            // NOTE: USAGE_MEDIA is more reliable for playback than USAGE_VOICE_COMMUNICATION
            // USAGE_VOICE_COMMUNICATION may route to earpiece/Bluetooth SCO instead of speaker
            // USAGE_MEDIA ensures audio plays through the main audio output (speaker/headphones)
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) // Use MEDIA instead of VOICE_COMMUNICATION for reliable playback
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // Speech content
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY) // Low latency flag for real-time audio
                .build();
            
            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_CONFIG)
                .build();
            
            audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(actualBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            
            Log.d(TAG, "Created AudioTrack with AudioAttributes (USAGE_MEDIA), buffer size: " + actualBufferSize + " bytes (12x minimum: " + bufferSize + ", ~" + (actualBufferSize * 1000 / (SAMPLE_RATE * 2)) + "ms of audio)");
            
            // Note: Audio routing is controlled by AudioAttributes (USAGE_MEDIA) and AudioManager settings
            // USAGE_MEDIA ensures audio plays through the main audio output (speaker/headphones)
            
            // Note: setPerformanceMode() is not available in standard AudioTrack API
            // Low latency is achieved through minimum buffer size and MODE_STREAM
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed, state: " + audioTrack.getState());
                if (audioTrack != null) {
                    try {
                        audioTrack.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing failed AudioTrack", e);
                    }
                    audioTrack = null;
                }
                return;
            }
            
            // CRITICAL: Set volume to a reasonable level (not maximum to avoid distortion)
            // setVolume() takes normalized volume (0.0 to 1.0)
            // Maximum volume (1.0) can cause clipping and distortion
            // Use 0.8 (80%) for better audio quality without distortion
            float volume = 0.8f; // 80% volume to avoid clipping/distortion
            int setVolumeResult = audioTrack.setVolume(volume);
            Log.d(TAG, "Set AudioTrack volume: " + volume + " (80% to avoid distortion), result: " + setVolumeResult);
            
            // CRITICAL: Verify volume was set correctly
            if (setVolumeResult != android.media.AudioTrack.SUCCESS) {
                Log.w(TAG, "WARNING: setVolume() returned non-success code: " + setVolumeResult);
            }
            
            // CRITICAL: Start playback FIRST, then pre-fill buffer
            // For MODE_STREAM, play() can be called before or after write()
            // Starting playback first ensures AudioTrack is ready to accept data
            audioTrack.play();
            Log.d(TAG, "Called audioTrack.play() to start playback");
            
            // CRITICAL: Pre-fill buffer AFTER starting playback to prevent underruns
            // Pre-filling AFTER play() ensures data is written to an active AudioTrack
            // Pre-fill with 60% of buffer size to provide substantial initial buffer
            // With 12x minimum buffer, 60% = 7.2x minimum = ~450ms of audio
            // This ensures we have enough data to start playback smoothly
            byte[] silence = new byte[actualBufferSize];
            int totalPrefilled = 0;
            int prefillAttempts = 0;
            int maxPrefillBytes = (int)(actualBufferSize * 0.6); // Pre-fill up to 60% of buffer size
            
            while (totalPrefilled < maxPrefillBytes && prefillAttempts < 30) {
                int bytesToWrite = Math.min(maxPrefillBytes - totalPrefilled, actualBufferSize / 4);
                int prefillWritten = audioTrack.write(silence, 0, bytesToWrite);
                if (prefillWritten > 0) {
                    totalPrefilled += prefillWritten;
                } else if (prefillWritten == 0) {
                    // Buffer may be full, which is okay
                    Log.d(TAG, "Pre-fill buffer appears full after " + totalPrefilled + " bytes");
                    break;
                } else {
                    // Error occurred
                    Log.w(TAG, "Pre-fill error: " + prefillWritten);
                    break;
                }
                prefillAttempts++;
            }
            Log.d(TAG, "Pre-filled AudioTrack buffer with " + totalPrefilled + " bytes (~" + (totalPrefilled * 1000 / (SAMPLE_RATE * 2)) + "ms) of silence after play()");
            
            // CRITICAL: Verify playback state after pre-fill
            int playStateAfterStart = audioTrack.getPlayState();
            Log.d(TAG, "PlayState after pre-fill: " + playStateAfterStart + " (PLAYSTATE_PLAYING=" + AudioTrack.PLAYSTATE_PLAYING + ")");
            
            // CRITICAL: Write a small amount of real data immediately after pre-fill
            // This helps maintain the buffer and prevents immediate underrun
            // Note: This will be overwritten by actual audio data from the call
            
            // Verify playback state
            int playState = audioTrack.getPlayState();
            if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.e(TAG, "AudioTrack play() failed, state: " + playState);
                if (audioTrack != null) {
                    try {
                        audioTrack.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing AudioTrack after play() failure", e);
                    }
                    audioTrack = null;
                }
                isPlaying = false;
                return;
            }
            
            isPlaying = true;
            Log.d(TAG, "Audio playback started successfully, buffer size: " + actualBufferSize + " bytes (~" + (actualBufferSize * 1000 / (SAMPLE_RATE * 2)) + "ms), playState: " + playState);
            
            // CRITICAL: Verify AudioTrack is actually playing and ready
            int finalPlayState = audioTrack.getPlayState();
            int finalTrackState = audioTrack.getState();
            Log.d(TAG, "Final AudioTrack state - playState: " + finalPlayState + " (PLAYSTATE_PLAYING=" + AudioTrack.PLAYSTATE_PLAYING + "), trackState: " + finalTrackState + " (STATE_INITIALIZED=" + AudioTrack.STATE_INITIALIZED + ")");
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
        
        // CRITICAL: Check if audioTrack is null before accessing it
        if (audioTrack == null) {
            Log.e(TAG, "AudioTrack is null, cannot check playState");
            return;
        }
        
        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            Log.w(TAG, "AudioTrack not playing, state: " + audioTrack.getPlayState() + ", restarting...");
            try {
                // Check AudioTrack state before attempting restart
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack not initialized, state: " + audioTrack.getState() + ", cannot restart");
                    // Need to reinitialize
                    stopPlayback();
                    startPlayback();
                    return;
                }
                audioTrack.play();
            } catch (Exception e) {
                Log.e(TAG, "Error restarting AudioTrack", e);
                // Try to reinitialize if restart fails
                try {
                    stopPlayback();
                    startPlayback();
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to reinitialize AudioTrack", ex);
                }
                return;
            }
        }
        
        try {
            // CRITICAL: Check AudioTrack state before any operations
            if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized, state: " + (audioTrack == null ? "null" : audioTrack.getState()));
                return;
            }
            
            // CRITICAL: Verify AudioTrack is actually playing
            // CRITICAL: Check if audioTrack is null before accessing it
            if (audioTrack == null) {
                Log.e(TAG, "AudioTrack is null, cannot check playState");
                return;
            }
            
            int playState = audioTrack.getPlayState();
            if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.w(TAG, "AudioTrack not playing, playState: " + playState + ", attempting to restart...");
                try {
                    // Check AudioTrack state before attempting restart
                    if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioTrack not initialized, state: " + audioTrack.getState() + ", cannot restart");
                        return;
                    }
                    audioTrack.play();
                    playState = audioTrack.getPlayState();
                    if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                        Log.e(TAG, "Failed to restart AudioTrack, playState: " + playState);
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error restarting AudioTrack", e);
                    return;
                }
            }
            
            // CRITICAL: Adaptive flush strategy - only flush when buffer is getting full
            // Flushing too frequently causes audio dropouts and glitches
            // For MODE_STREAM, AudioTrack manages buffering automatically
            // We only flush if we detect buffer overflow (which is rare)
            // NOTE: Disabled aggressive flushing - let AudioTrack handle buffering naturally
            // Flushing should only happen on buffer overflow, not based on usage percentage
            try {
                // Check if AudioTrack has underrun (buffer underflow)
                // This is a better indicator than trying to calculate buffer usage
                // For now, we'll skip automatic flushing and let AudioTrack handle it
                // Only flush if we get an error from write() indicating buffer issues
            } catch (Exception e) {
                // Ignore buffer check errors - not critical
                Log.d(TAG, "Could not check buffer level", e);
            }
            
            // CRITICAL: Write audio data immediately
            // AudioTrack.write() is non-blocking in MODE_STREAM by default
            // For MODE_STREAM, write() may return 0 if buffer is full (non-blocking behavior)
            // CRITICAL: Check AudioTrack state before writing to avoid I/O errors
            if (audioTrack == null) {
                Log.e(TAG, "AudioTrack is null, cannot write audio data");
                return;
            }
            
            // Check if AudioTrack is in valid state for writing
            int trackState = audioTrack.getState();
            if (trackState != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized, state: " + trackState + ", skipping write");
                return;
            }
            
            int written = audioTrack.write(audioData, 0, audioData.length);
            
            // CRITICAL: Log write results only occasionally to avoid spam
            // Log errors immediately, but success only every few seconds
            if (written < 0) {
                Log.e(TAG, "Write error: " + written);
            } else if (written == 0) {
                // Only log if this happens frequently (buffer full)
                if (System.currentTimeMillis() % 3000 < 50) {
                    Log.w(TAG, "Write returned 0 bytes (buffer may be full)");
                }
            } else {
                // Log success only occasionally
                if (System.currentTimeMillis() % 5000 < 50) {
                    Log.d(TAG, "Wrote " + written + " bytes to AudioTrack (playState: " + audioTrack.getPlayState() + ")");
                }
            }
            
            // CRITICAL: Verify AudioTrack is still playing after write
            // Sometimes AudioTrack stops playing if buffer underruns
            // Only check playState if write was successful to avoid unnecessary checks
            if (written > 0) {
                int currentPlayState = audioTrack.getPlayState();
                if (currentPlayState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.w(TAG, "AudioTrack stopped playing, playState: " + currentPlayState + ", attempting to restart...");
                    try {
                        // CRITICAL: Check AudioTrack state before attempting restart
                        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                            Log.e(TAG, "AudioTrack not initialized, state: " + audioTrack.getState() + ", cannot restart");
                            // Need to reinitialize AudioTrack
                            stopPlayback();
                            startPlayback();
                            return;
                        }
                        
                        // Pre-fill buffer again before restarting to prevent immediate underrun
                        // Use 60% of buffer size for pre-fill
                        byte[] silence = new byte[actualBufferSize];
                        int totalPrefilled = 0;
                        int maxPrefillBytes = (int)(actualBufferSize * 0.6);
                        for (int i = 0; i < 15 && totalPrefilled < maxPrefillBytes; i++) {
                            int bytesToWrite = Math.min(maxPrefillBytes - totalPrefilled, actualBufferSize / 4);
                            int prefillWritten = audioTrack.write(silence, 0, bytesToWrite);
                            if (prefillWritten > 0) {
                                totalPrefilled += prefillWritten;
                            } else {
                                break;
                            }
                        }
                        Log.d(TAG, "Pre-filled buffer before restart: " + totalPrefilled + " bytes (~" + (totalPrefilled * 1000 / (SAMPLE_RATE * 2)) + "ms)");
                        
                        // CRITICAL: Double-check audioTrack is not null before calling play()
                        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                            audioTrack.play();
                            currentPlayState = audioTrack.getPlayState();
                            if (currentPlayState != AudioTrack.PLAYSTATE_PLAYING) {
                                Log.e(TAG, "Failed to restart AudioTrack, playState: " + currentPlayState);
                            } else {
                                Log.d(TAG, "AudioTrack restarted successfully with pre-filled buffer");
                            }
                        } else {
                            Log.e(TAG, "AudioTrack became null or invalid during restart attempt");
                        }
                    } catch (IllegalStateException e) {
                        // AudioTrack may have been released - try to reinitialize
                        Log.w(TAG, "AudioTrack illegal state during restart, reinitializing...", e);
                        try {
                            stopPlayback();
                            startPlayback();
                            Log.d(TAG, "Reinitialized AudioTrack after illegal state");
                        } catch (Exception ex) {
                            Log.e(TAG, "Failed to reinitialize AudioTrack", ex);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error restarting AudioTrack", e);
                        // If restart fails, try to reinitialize
                        try {
                            stopPlayback();
                            startPlayback();
                            Log.d(TAG, "Reinitialized AudioTrack after restart failure");
                        } catch (Exception ex) {
                            Log.e(TAG, "Failed to reinitialize AudioTrack", ex);
                        }
                    }
                }
            }
            
            if (written < 0) {
                // Error codes: ERROR_INVALID_OPERATION, ERROR_BAD_VALUE, ERROR_DEAD_OBJECT
                if (written == AudioTrack.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "ERROR_INVALID_OPERATION: AudioTrack not in valid state for writing");
                } else if (written == AudioTrack.ERROR_BAD_VALUE) {
                    Log.e(TAG, "ERROR_BAD_VALUE: Invalid parameters to write()");
                } else {
                    Log.e(TAG, "ERROR writing audio data: " + written);
                }
                
                // Try to recover by reinitializing AudioTrack
                // Don't try to restart if AudioTrack is in bad state
                try {
                    if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                        // Only try to recover if AudioTrack is still initialized
                        stopPlayback();
                        startPlayback();
                        Log.d(TAG, "Reinitialized AudioTrack after write error");
                    } else {
                        Log.w(TAG, "Cannot recover AudioTrack - not in valid state");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error recovering AudioTrack", e);
                }
            } else if (written == 0) {
                // Buffer is full - this is normal for high-frequency writes
                // AudioTrack will continue playing from buffer
                // Don't log this frequently to avoid spam
                if (System.currentTimeMillis() % 5000 < 50) {
                    Log.v(TAG, "Buffer full, written 0 bytes (AudioTrack will continue playing)");
                }
            } else if (written < audioData.length) {
                // Partial write - buffer had some space but not enough for full frame
                Log.v(TAG, "Partial write: " + written + " of " + audioData.length + " bytes");
            } else {
                // Full write successful - this is the normal case
                // Log occasionally to verify audio is being written
                if (System.currentTimeMillis() % 5000 < 50) {
                    int currentPlayState = audioTrack != null ? audioTrack.getPlayState() : -1;
                    Log.d(TAG, "Successfully wrote " + written + " bytes, playState: " + currentPlayState);
                }
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
        if (!isPlaying && audioTrack == null) {
            return;
        }
        
        isPlaying = false;
        
        try {
            if (audioTrack != null) {
                // CRITICAL: Check state before stopping to avoid I/O errors
                int playState = audioTrack.getPlayState();
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        audioTrack.stop();
                        Log.d(TAG, "Stopped AudioTrack playback");
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "AudioTrack already stopped or released", e);
                    }
                }
                
                // CRITICAL: Check state before releasing to avoid I/O errors
                int trackState = audioTrack.getState();
                if (trackState == AudioTrack.STATE_INITIALIZED) {
                    try {
                        audioTrack.release();
                        Log.d(TAG, "Released AudioTrack");
                    } catch (Exception e) {
                        Log.w(TAG, "Error releasing AudioTrack (may already be released)", e);
                    }
                } else {
                    Log.d(TAG, "AudioTrack not initialized, state: " + trackState + ", skipping release");
                }
                
                audioTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio playback", e);
            audioTrack = null; // Ensure audioTrack is null even if error occurs
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
