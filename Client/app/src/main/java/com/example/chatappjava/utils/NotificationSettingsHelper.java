package com.example.chatappjava.utils;

import android.widget.CompoundButton;
import android.widget.Switch;

public final class NotificationSettingsHelper {

    private NotificationSettingsHelper() {
    }

    public static void bindSwitches(
            DatabaseManager databaseManager,
            Switch pushSwitch,
            Switch soundSwitch,
            Switch vibrateSwitch
    ) {
        if (pushSwitch != null) {
            bindSwitch(
                    pushSwitch,
                    databaseManager.isPushNotificationsEnabled(),
                    databaseManager::setPushNotificationsEnabled
            );
        }
        if (soundSwitch != null) {
            bindSwitch(
                    soundSwitch,
                    databaseManager.isSoundNotificationsEnabled(),
                    databaseManager::setSoundNotificationsEnabled
            );
        }
        if (vibrateSwitch != null) {
            bindSwitch(
                    vibrateSwitch,
                    databaseManager.isVibrateNotificationsEnabled(),
                    databaseManager::setVibrateNotificationsEnabled
            );
        }
    }

    public static void resetToDefaults(
            DatabaseManager databaseManager,
            Switch pushSwitch,
            Switch soundSwitch,
            Switch vibrateSwitch
    ) {
        databaseManager.resetNotificationSettings();
        bindSwitches(databaseManager, pushSwitch, soundSwitch, vibrateSwitch);
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private static void bindSwitch(Switch switchView, boolean checked, BoolSetter setter) {
        switchView.setOnCheckedChangeListener(null);
        switchView.setChecked(checked);
        switchView.setOnCheckedChangeListener(
                (CompoundButton buttonView, boolean isChecked) -> setter.set(isChecked)
        );
    }
}
