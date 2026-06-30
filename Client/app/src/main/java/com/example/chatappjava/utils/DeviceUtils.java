package com.example.chatappjava.utils;

import android.os.Build;

/**
 * Device environment helpers for network configuration.
 */
public final class DeviceUtils {

    private static final String EMULATOR_LOOPBACK_HOST = "10.0.2.2";

    private DeviceUtils() {
    }

    public static boolean isRunningOnEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    public static String getEmulatorLoopbackHost() {
        return EMULATOR_LOOPBACK_HOST;
    }
}
