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
    
    // JPEG compression quality (0-100)
    private static final int JPEG_QUALITY = 70;
    
    // Maximum image size (to reduce bandwidth)
    private static final int MAX_WIDTH = 640;
    private static final int MAX_HEIGHT = 480;
    
    /**
     * Encode a JPEG frame to base64
     */
    public static String encodeFrame(byte[] jpegData) {
        if (jpegData == null || jpegData.length == 0) {
            return null;
        }
        
        try {
            // Decode JPEG to Bitmap for resizing if needed
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            
            if (bitmap == null) {
                Log.w(TAG, "Unable to decode JPEG");
                return null;
            }
            
            // Resize if necessary
            Bitmap resizedBitmap = resizeIfNeeded(bitmap);
            
            // Encode to JPEG with compression
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
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
     * Note: JPEG orientation should be handled by camera capture (JPEG_ORIENTATION)
     * This method just decodes the frame as-is
     */
    public static Bitmap decodeFrame(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return null;
        }
        
        try {
            byte[] decodedBytes = Base64.decode(base64Data, Base64.NO_WRAP);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            
            // Check if bitmap needs rotation based on EXIF orientation
            // Note: If JPEG_ORIENTATION is set correctly in camera capture, this should not be needed
            // But we'll keep it as a fallback
            if (bitmap != null) {
                // Try to read EXIF orientation from JPEG
                android.media.ExifInterface exif = null;
                try {
                    java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(decodedBytes);
                    exif = new android.media.ExifInterface(inputStream);
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
                    // EXIF reading failed, return bitmap as-is
                    Log.d(TAG, "Could not read EXIF orientation, using bitmap as-is");
                }
            }
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding frame", e);
            return null;
        }
    }
}
