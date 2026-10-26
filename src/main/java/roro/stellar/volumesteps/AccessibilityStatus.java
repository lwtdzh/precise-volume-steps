package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

final class AccessibilityStatus {
    private AccessibilityStatus() {}

    static boolean isEnabled(Context context) {
        ComponentName expected = new ComponentName(
                context,
                FineVolumeAccessibilityService.class
        );
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        for (String component : enabled.split(":")) {
            if (expected.equals(ComponentName.unflattenFromString(component))) {
                return true;
            }
        }
        return false;
    }
}
