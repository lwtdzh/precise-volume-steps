package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Same-signature test helper. No production entry point or root dependency. */
public final class PhoneProbeInstrumentation extends Instrumentation {
    private Bundle args;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            SharedPreferences settings = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", Context.MODE_PRIVATE);
            String restore = args.getString("restore");
            if (restore != null) {
                JSONObject saved = new JSONObject(new String(
                        Base64.decode(restore, Base64.NO_WRAP), java.nio.charset.StandardCharsets.UTF_8));
                SharedPreferences.Editor edit = settings.edit().clear();
                for (java.util.Iterator<String> keys = saved.keys(); keys.hasNext();) {
                    String key = keys.next();
                    Object value = saved.get(key);
                    if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                    else if (value instanceof Number) edit.putLong(key, ((Number) value).longValue());
                    else if (value instanceof String) edit.putString(key, (String) value);
                }
                if (!edit.commit()) throw new IllegalStateException("Settings restore failed");
            }
            result.putString("settings", new JSONObject(settings.getAll()).toString());
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            Class<?> keeper = target.getClassLoader().loadClass(
                    target.getPackageName() + ".VolumeKeeperService");
            Method getController = keeper.getDeclaredMethod("controller");
            getController.setAccessible(true);
            Object controller = null;
            for (int i = 0; i < 50 && controller == null; i++) {
                controller = getController.invoke(null);
                if (controller == null) Thread.sleep(100);
            }
            if (controller != null) {
                Object monitor = controller;
                Method diagnostics = monitor.getClass().getDeclaredMethod("diagnostics");
                diagnostics.setAccessible(true);
                runOnMainSync(() -> {
                    try {
                        result.putString("diagnostics", (String) diagnostics.invoke(monitor));
                    } catch (Exception exception) {
                        result.putString("error", exception.toString());
                    }
                });
            }
            finish(result.containsKey("error") ? 1 : 0, result);
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            finish(1, result);
        }
    }
}
