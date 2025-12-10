package com.example.chatappjava.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Encoder to convert video frames to base64
 */
public class VideoFrameEncoder {
    private static final String TAG = "VideoFrameEncoder";
    
    // CRITICAL: Optimized JPEG quality for balance between quality and encoding speed
    // Lower quality = faster encoding/decoding = lower latency
    // 50 is optimal for low latency - prioritizes speed over quality
    private static final int JPEG_QUALITY = 50; // Reduced for faster encoding and lower latency
    
    // Maximum image size (reduced for faster processing and lower bandwidth)
    // 480x360 provides good quality while maintaining smooth 60 FPS
    private static final int MAX_WIDTH = 480;
    private static final int MAX_HEIGHT = 360;
    
    /**
     * Encode a JPEG frame to base64
     * CRITICAL: Optimized for low latency - uses inSampleSize for faster decoding
     */
    public static String encodeFrame(byte[] jpegData) {
        if (jpegData == null || jpegData.length == 0) {
            return null;
        }
        
        try {
            // CRITICAL: Skip decode/resize if JPEG is already small enough
            // Direct base64 encoding for lowest latency when no resize needed
            // Check JPEG size first (quick check)
            if (jpegData.length < 50000) { // ~50KB - likely already small enough
                // Try direct base64 encoding without decode/resize for maximum speed
                // This can save 20-50ms per frame
                try {
                    String base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP);
                    // Verify it's valid base64 (quick check)
                    if (base64 != null && base64.length() > 0) {
                        return base64;
                    }
                } catch (Exception e) {
                    // Fall through to normal encoding if direct encoding fails
                    Log.d(TAG, "Direct encoding failed, using normal encoding");
                }
            }
            
            // Normal encoding path with resize if needed
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, options);
            
            // Calculate inSampleSize if image is larger than MAX dimensions
            if (options.outWidth > MAX_WIDTH || options.outHeight > MAX_HEIGHT) {
                int widthRatio = (int) Math.ceil((float) options.outWidth / MAX_WIDTH);
                int heightRatio = (int) Math.ceil((float) options.outHeight / MAX_HEIGHT);
                options.inSampleSize = Math.max(widthRatio, heightRatio);
            } else {
                options.inSampleSize = 1;
            }
            
            // Now decode with inSampleSize for faster processing
            options.inJustDecodeBounds = false;
            // CRITICAL: Use RGB_565 for faster decoding (half memory, faster processing)
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            // CRITICAL: Enable purgeable and input shareable for better memory management
            options.inPurgeable = true;
            options.inInputShareable = true;
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, options);
            
            if (bitmap == null) {
                Log.w(TAG, "Unable to decode JPEG");
                return null;
            }
            
            // Resize if still necessary (inSampleSize may not be exact)
            Bitmap resizedBitmap = resizeIfNeeded(bitmap);
            
            // Encode to JPEG with compression
            // CRITICAL: Pre-size ByteArrayOutputStream to reduce reallocation overhead
            int estimatedSize = (resizedBitmap.getWidth() * resizedBitmap.getHeight() * 2) / 3;
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(estimatedSize);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            
            // Convert to base64
            byte[] compressedData = outputStream.toByteArray();
            String base64 = Base64.encodeToString(compressedData, Base64.NO_WRAP);
            
            // Clean up
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            bitmap.recycle();
            
            return base64;
            
        } catch (Exception e) {
            Log.e(TAG, "Error encoding frame", e);
            return null;
        }
    }
    
    /**
     * Resize bitmap if necessary
     */
    private static Bitmap resizeIfNeeded(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return bitmap;
        }
        
        // Calculate new dimensions while maintaining aspect ratio
        float ratio = Math.min((float) MAX_WIDTH / width, (float) MAX_HEIGHT / height);
        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
    
    /**
     * Decode a base64 frame to Bitmap
     * CRITICAL: Optimized for low latency - uses RGB_565 and skips EXIF when possible
     * Note: JPEG orientation should be handled by camera capture (JPEG_ORIENTATION)
     */
    public static Bitmap decodeFrame(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return null;
        }
        
        try {
            byte[] decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP);
            
            // CRITICAL: Use RGB_565 for faster decoding (half memory, faster processing)
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            // CRITICAL: Enable purgeable and input shareable for better memory management
            // This allows Android to reclaim bitmap memory if needed, improving performance
            options.inPurgeable = true;
            options.inInputShareable = true;
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, options);
            
            // Check if bitmap needs rotation based on EXIF orientation
            // Note: If JPEG_ORIENTATION is set correctly in camera capture, this should not be needed
            // But we'll keep it as a fallback
            if (bitmap != null) {
                // CRITICAL: Only read EXIF if bitmap is valid (optimization)
                // Try to read EXIF orientation from JPEG
                try {
                    java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(decodedBytes);
                    android.media.ExifInterface exif = new android.media.ExifInterface(inputStream);
                    int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 
                                                          android.media.ExifInterface.ORIENTATION_NORMAL);
                    
                    if (orientation != android.media.ExifInterface.ORIENTATION_NORMAL) {
                        Matrix matrix = new Matrix();
                        switch (orientation) {
                            case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                                matrix.postRotate(90);
                                break;
                            case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                                matrix.postRotate(180);
                                break;
                            case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                                matrix.postRotate(270);
                                break;
                            case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                                matrix.postScale(-1, 1);
                                break;
                            case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL:
                                matrix.postScale(1, -1);
                                break;
                        }
                        
                        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, 
                                                                  bitmap.getWidth(), bitmap.getHeight(), 
                                                                  matrix, true);
                        if (rotatedBitmap != bitmap) {
                            bitmap.recycle();
                        }
                        return rotatedBitmap;
                    }
                } catch (Exception e) {
                    // EXIF reading failed, return bitmap as-is (common case, no need to log)
                    // Log.d(TAG, "Could not read EXIF orientation, using bitmap as-is");
                }
            }
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding frame", e);
            return null;
        }
    }
}
