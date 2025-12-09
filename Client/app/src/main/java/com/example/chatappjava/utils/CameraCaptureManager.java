package com.example.chatappjava.utils;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Manager for continuously capturing frames from the camera
 */
public class CameraCaptureManager {
    private static final String TAG = "CameraCaptureManager";
    
    private Context context;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private FrameCaptureCallback frameCallback;
    private boolean isCapturing = false;
    private int currentCameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    
    // Capture configuration
    private static final int CAPTURE_WIDTH = 640;
    private static final int CAPTURE_HEIGHT = 480;
    private static final int MAX_IMAGES = 1;
    
    public interface FrameCaptureCallback {
        void onFrameCaptured(byte[] frameData, int width, int height);
    }
    
    public CameraCaptureManager(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }
    
    /**
     * Start video capture
     */
    public void startCapture(FrameCaptureCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Capture already in progress");
            return;
        }
        
        this.frameCallback = callback;
        startBackgroundThread();
        
        try {
            // Find the camera
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == currentCameraFacing) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId == null && cameraIds.length > 0) {
                cameraId = cameraIds[0]; // Use the first available camera
            }
            
            if (cameraId == null) {
                Log.e(TAG, "No camera available");
                if (callback != null) {
                    callback.onFrameCaptured(null, 0, 0);
                }
                return;
            }
            
            // Create ImageReader to receive frames
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                Log.e(TAG, "StreamConfigurationMap is null");
                return;
            }
            
            // Use JPEG for capture (simpler and more compatible)
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            if (sizes == null || sizes.length == 0) {
                Log.e(TAG, "No JPEG size available");
                return;
            }
            Size optimalSize = getOptimalSize(sizes, CAPTURE_WIDTH, CAPTURE_HEIGHT);
            
            imageReader = ImageReader.newInstance(
                optimalSize.getWidth(),
                optimalSize.getHeight(),
                ImageFormat.JPEG,
                MAX_IMAGES
            );
            
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        processImage(image);
                        image.close();
                    }
                }
            }, backgroundHandler);
            
            // Open the camera
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening camera", e);
            if (callback != null) {
                callback.onFrameCaptured(null, 0, 0);
            }
        }
    }
    
    /**
     * Stop video capture
     */
    public void stopCapture() {
        if (!isCapturing) {
            return;
        }
        
        isCapturing = false;
        
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        stopBackgroundThread();
    }
    
    /**
     * Switch camera (front/back)
     */
    public void switchCamera() {
        if (isCapturing) {
            stopCapture();
            currentCameraFacing = (currentCameraFacing == CameraCharacteristics.LENS_FACING_BACK) 
                ? CameraCharacteristics.LENS_FACING_FRONT 
                : CameraCharacteristics.LENS_FACING_BACK;
            
            if (frameCallback != null) {
                startCapture(frameCallback);
            }
        }
    }
    
    /**
     * Process captured image
     */
    private void processImage(Image image) {
        try {
            // Read JPEG data directly
            Image.Plane[] planes = image.getPlanes();
            if (planes.length > 0) {
                ByteBuffer buffer = planes[0].getBuffer();
                byte[] frameData = new byte[buffer.remaining()];
                buffer.get(frameData);
                
                if (frameCallback != null && isCapturing) {
                    frameCallback.onFrameCaptured(frameData, image.getWidth(), image.getHeight());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }
    
    /**
     * Callback for camera state
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCaptureSession();
        }
        
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            camera.close();
            cameraDevice = null;
        }
    };
    
    /**
     * Create capture session
     */
    private void createCaptureSession() {
        try {
            if (cameraDevice == null || imageReader == null) {
                return;
            }
            
            Surface surface = imageReader.getSurface();
            List<Surface> surfaces = Arrays.asList(surface);
            
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    
                    captureSession = session;
                    startRepeatingRequest();
                    isCapturing = true;
                    Log.d(TAG, "Capture started successfully");
                }
                
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure capture session");
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating capture session", e);
        }
    }
    
    /**
     * Start repeating capture request
     */
    private void startRepeatingRequest() {
        try {
            if (cameraDevice == null || captureSession == null || imageReader == null) {
                return;
            }
            
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.JPEG_ORIENTATION, 0);
            
            CaptureRequest request = builder.build();
            
            // For continuous capture with JPEG, we need to capture periodically
            // because setRepeatingRequest doesn't work well with JPEG
            startPeriodicCapture(request);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error starting capture request", e);
        }
    }
    
    /**
     * Start periodic capture
     */
    private void startPeriodicCapture(CaptureRequest request) {
        if (backgroundHandler == null) {
            return;
        }
        
        final CameraCaptureManager manager = this;
        final CaptureRequest captureRequest = request;
        
        // Declare and initialize captureRunnable before using it in callback
        final Runnable[] captureRunnableRef = new Runnable[1];
        
        captureRunnableRef[0] = new Runnable() {
            @Override
            public void run() {
                if (!manager.isCapturing || manager.captureSession == null) {
                    return;
                }
                
                try {
                    manager.captureSession.capture(captureRequest, new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                      @NonNull CaptureRequest request,
                                                      @NonNull android.hardware.camera2.TotalCaptureResult result) {
                            // Schedule next capture
                            if (manager.isCapturing && manager.backgroundHandler != null) {
                                manager.backgroundHandler.postDelayed(captureRunnableRef[0], FRAME_CAPTURE_INTERVAL_MS);
                            }
                        }
                    }, manager.backgroundHandler);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Error during capture", e);
                }
            }
        };
        
        backgroundHandler.post(captureRunnableRef[0]);
    }
    
    private static final int FRAME_CAPTURE_INTERVAL_MS = 100; // 10 FPS
    
    /**
     * Get optimal size
     */
    private Size getOptimalSize(Size[] sizes, int width, int height) {
        if (sizes == null || sizes.length == 0) {
            return new Size(CAPTURE_WIDTH, CAPTURE_HEIGHT);
        }
        
        Size optimalSize = sizes[0];
        int minDiff = Integer.MAX_VALUE;
        
        for (Size size : sizes) {
            int diff = Math.abs(size.getWidth() - width) + Math.abs(size.getHeight() - height);
            if (diff < minDiff) {
                minDiff = diff;
                optimalSize = size;
            }
        }
        
        return optimalSize;
    }
    
    /**
     * Start background thread
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    /**
     * Stop background thread
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping thread", e);
            }
        }
    }
    
    public boolean isCapturing() {
        return isCapturing;
    }
}
