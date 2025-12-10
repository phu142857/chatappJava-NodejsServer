package com.example.chatappjava.utils;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encoder to convert audio frames to base64
 * Uses compression to reduce bandwidth
 */
public class AudioFrameEncoder {
    private static final String TAG = "AudioFrameEncoder";
    
    // Compression level (0-9, 6 is a good balance)
    private static final int COMPRESSION_LEVEL = 6;
    
    /**
     * Encode audio data to base64 with compression
     */
    public static String encodeFrame(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return null;
        }
        
        try {
            // Compress audio data using Deflater
            Deflater deflater = new Deflater(COMPRESSION_LEVEL);
            deflater.setInput(audioData);
            deflater.finish();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(audioData.length);
            byte[] buffer = new byte[1024];
            
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            
            deflater.end();
            
            byte[] compressedData = outputStream.toByteArray();
            
            // Convert to base64
            String base64 = Base64.encodeToString(compressedData, Base64.NO_WRAP);
            
            return base64;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding audio frame", e);
            return null;
        }
    }
    
    /**
     * Decode base64 audio data with decompression
     */
    public static byte[] decodeFrame(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return null;
        }
        
        try {
            // Decode from base64
            byte[] compressedData = Base64.decode(base64Data, Base64.NO_WRAP);
            
            // Decompress using Inflater
            Inflater inflater = new Inflater();
            inflater.setInput(compressedData);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedData.length * 2);
            byte[] buffer = new byte[1024];
            
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            
            inflater.end();
            
            byte[] audioData = outputStream.toByteArray();
            
            return audioData;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding audio frame", e);
            return null;
        }
    }
}
