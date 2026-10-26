package roro.stellar.volumesteps;

import android.content.Context;
import android.content.SharedPreferences;
import android.annotation.SuppressLint;
import android.os.UserManager;

final class VolumeStepSettings {
    static final int DEFAULT_STEPS = 30;
    static final int MIN_STEPS = 5;
    static final int MAX_STEPS = 150;

    private static final String PREFS = "volume_step_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STEPS = "steps";
    private static final String KEY_MEDIA_ONLY = "media_only";

    private VolumeStepSettings() {}

    // Complete migration durably before a subsequent boot can start the service.
    @SuppressLint("ApplySharedPref")
    static SharedPreferences preferences(Context context) {
        Context device = context.createDeviceProtectedStorageContext();
        SharedPreferences preferences = device.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!preferences.getBoolean("device_storage_migrated", false)
                && context.getSystemService(UserManager.class).isUserUnlocked()) {
            device.moveSharedPreferencesFrom(context.getApplicationContext(), PREFS);
            preferences = device.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            preferences.edit().putBoolean("device_storage_migrated", true).commit();
        }
        return preferences;
    }

    static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static int steps(Context context) {
        return preferences(context).getInt(KEY_STEPS, DEFAULT_STEPS);
    }

    static void setSteps(Context context, int steps) {
        int safeSteps = Math.max(MIN_STEPS, Math.min(MAX_STEPS, steps));
        preferences(context).edit().putInt(KEY_STEPS, safeSteps).apply();
    }

    static boolean isMediaOnly(Context context) {
        return preferences(context).getBoolean(KEY_MEDIA_ONLY, true);
    }

    static void setMediaOnly(Context context, boolean mediaOnly) {
        preferences(context).edit().putBoolean(KEY_MEDIA_ONLY, mediaOnly).apply();
    }

    static boolean isShizukuEnabled(Context context) {
        return preferences(context).getBoolean("shizuku_enabled", false);
    }

    static void setShizukuEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("shizuku_enabled", enabled).apply();
    }
}
