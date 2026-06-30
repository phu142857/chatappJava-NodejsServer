package com.example.chatappjava.utils;

import android.content.res.Resources;
import android.widget.ImageButton;

import com.example.chatappjava.R;

/**
 * TalkBack labels for in-call controls; call after each toggle so state is announced.
 */
public final class CallAccessibilityHelper {

    private CallAccessibilityHelper() {
    }

    public static void bindInCallControls(ImageButton mute,
                                        ImageButton cameraToggle,
                                        ImageButton switchCamera,
                                        ImageButton endCall,
                                        boolean muted,
                                        boolean cameraOn) {
        if (mute != null) {
            mute.setContentDescription(mute.getResources().getString(
                    muted ? R.string.call_unmute_cd : R.string.call_mute_cd));
        }
        if (cameraToggle != null) {
            cameraToggle.setContentDescription(cameraToggle.getResources().getString(
                    cameraOn ? R.string.call_camera_off_cd : R.string.call_camera_on_cd));
        }
        if (switchCamera != null) {
            switchCamera.setContentDescription(
                    switchCamera.getResources().getString(R.string.call_switch_camera_cd));
        }
        if (endCall != null) {
            endCall.setContentDescription(endCall.getResources().getString(R.string.call_end_cd));
        }
    }

    public static void announceMuteToggle(Resources res, ImageButton mute, boolean muted) {
        if (mute != null) {
            mute.setContentDescription(res.getString(
                    muted ? R.string.call_unmute_cd : R.string.call_mute_cd));
        }
    }

    public static void announceCameraToggle(Resources res, ImageButton cameraToggle, boolean cameraOn) {
        if (cameraToggle != null) {
            cameraToggle.setContentDescription(res.getString(
                    cameraOn ? R.string.call_camera_off_cd : R.string.call_camera_on_cd));
        }
    }
}
