package com.example.chatappjava.utils;

import android.util.Base64;
import android.util.Log;

/**
 * Encoder to convert audio PCM data to base64
 * Similar to VideoFrameEncoder but for audio
 * 
 * Flow: Capture (PCM) -> Encode (base64) -> Server -> Decode (base64) -> Play (PCM)
 * Note: For real-time voice calls, we use raw PCM with base64 encoding
 * Audio compression (AAC/OPUS) would add latency, so we skip it for low-latency calls
 */
public class AudioEncoder {
    private static final String TAG = "AudioEncoder";
    
    /**
     * Encode PCM audio data to base64
     * Similar to VideoFrameEncoder.encodeFrame() but for audio
     * 
     * @param pcmData Raw PCM audio data (16-bit, mono, 16kHz)
     * @return Base64 encoded string, or null if encoding fails
     */
    public static String encodeAudio(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            Log.w(TAG, "Empty PCM data, cannot encode");
            return null;
        }
        
        try {
            // Encode PCM to base64 (similar to video frame encoding)
            // Note: We don't compress audio here to avoid latency
            // Raw PCM: 16kHz * 2 bytes * 1 channel = 32KB/s (acceptable for voice calls)
            String base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP);
            return base64;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding audio", e);
            return null;
        }
    }
    
    /**
     * Decode base64 audio data to PCM
     * Similar to VideoFrameEncoder.decodeFrame() but for audio
     * 
     * @param base64Data Base64 encoded audio data
     * @return Raw PCM audio data, or null if decoding fails
     */
    public static byte[] decodeAudio(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            Log.w(TAG, "Empty base64 data, cannot decode");
            return null;
        }
        
        try {
            // Decode base64 to PCM (similar to video frame decoding)
            byte[] pcmData = Base64.decode(base64Data, Base64.NO_WRAP);
            return pcmData;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding audio", e);
            return null;
        }
    }
}
