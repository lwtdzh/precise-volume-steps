package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Emulator power-state regression; real audio, synthetic accessibility callbacks. */
public final class ScreenOffInstrumentation extends Instrumentation {
    private Object controller;
    private Method key;
    private Method cancel;
    private Method diagnostics;
    private AudioManager audio;
    private final JSONArray tests = new JSONArray();

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int saved = -1;
        try {
            if (!android.os.Build.FINGERPRINT.contains("generic")
                    && !android.os.Build.MODEL.toLowerCase().contains("sdk")) {
                throw new IllegalStateException("Disposable emulator only");
            }
            Context target = getTargetContext();
            audio = target.getSystemService(AudioManager.class);
            saved = audio.getStreamVolume(3);
            int max = audio.getStreamMaxVolume(3);
            int steps = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0).getInt("steps", 30);
            int expected = 4 + (int) Math.round((double) max / Math.min(max, steps));
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            Method get = target.getClassLoader().loadClass(target.getPackageName() + ".VolumeKeeperService")
                    .getDeclaredMethod("controller");
            get.setAccessible(true);
            for (int n = 0; n < 50 && controller == null; n++) {
                controller = get.invoke(null);
                if (controller == null) Thread.sleep(100);
            }
            if (controller == null) throw new IllegalStateException("Controller unavailable");
            key = method("onKey", KeyEvent.class);
            cancel = method("cancelPending");
            diagnostics = method("diagnostics");
            runOnMainSync(() -> target.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            // Baselines are set while unlocked so passive preset changes are not inferred
            // as keys; the screen is then switched off for each routed scenario.
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            Thread.sleep(600);
            set(4);
            Thread.sleep(400);
            invoke(cancel);
            shell("input keyevent 223");
            Thread.sleep(600);
            check("screen is off", report().contains("screen interactive=false"));
            long now = SystemClock.uptimeMillis();
            invoke(key, new KeyEvent(now, now, 0, KeyEvent.KEYCODE_VOLUME_UP, 0));
            set(5);
            invoke(key, new KeyEvent(now, now + 5, 1, KeyEvent.KEYCODE_VOLUME_UP, 0));
            check("screen-off full key corrects native volume", waitFor(expected));
            check("screen-off full key keeps panel hidden", report().contains("overlay=attached=false"));
            check("correction does not wake display", report().contains("screen interactive=false"));
            // Reset the baseline while unlocked before exercising key-up-only delivery.
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            Thread.sleep(600);
            invoke(cancel);
            set(4);
            Thread.sleep(400);
            invoke(cancel);
            shell("input keyevent 223");
            Thread.sleep(600);
            now = SystemClock.uptimeMillis();
            set(5);
            Thread.sleep(60);
            invoke(key, new KeyEvent(now, SystemClock.uptimeMillis(), 1, KeyEvent.KEYCODE_VOLUME_UP, 0));
            check("key-up-only delivery restores correction", waitFor(expected));
            check("key-up recovery recorded", report().contains("locked/off key up recovered"));
            Thread.sleep(2200);
            check("wake lock released after key", report().contains("key wake lock held=false"));
            check("display remains off after wake lock release", report().contains("screen interactive=false"));

            // Firmware may route the hardware key straight to audio while the screen is
            // off, delivering neither key-down nor key-up to accessibility. A single
            // coarse native jump must still be recognized and corrected.
            invoke(cancel);
            int interval = (int) Math.round((double) max / Math.min(max, steps));
            int origin = Math.min(max - interval - 1, expected + 2);
            int routed = origin - 1;
            int inferredTarget = origin - interval;
            // Establish the baseline while unlocked; passive changes must not be treated
            // as keys there.
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            Thread.sleep(800);
            invoke(cancel);
            set(origin);
            Thread.sleep(400);
            invoke(cancel);
            // Simulate firmware-only audio routing after the display sleeps.
            shell("input keyevent 223");
            Thread.sleep(600);
            check("screen is off before routed jump", report().contains("screen interactive=false"));
            set(routed);
            check("screen-off routed coarse jump is inferred as a key",
                    waitFor(inferredTarget));
            check("inference recorded", report().contains("native change inferred"));
            check("inferred correction keeps panel hidden", report().contains("overlay=attached=false"));
            check("inferred correction does not wake display",
                    report().contains("screen interactive=false"));

            // The unlocked display must keep the strict key-only rule: the same
            // externally applied volume stays untouched.
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            Thread.sleep(800);
            invoke(cancel);
            int unlocked = audio.getStreamVolume(3);
            set(Math.max(0, unlocked - 1));
            Thread.sleep(700);
            check("unlocked external volume change is not inferred as a key",
                    audio.getStreamVolume(3) == Math.max(0, unlocked - 1));

            result.putString("diagnostics", report());
        } catch (Exception e) {
            result.putString("error", e.toString());
            try { result.putString("diagnostics", report()); } catch (Exception ignored) { }
        } finally {
            try {
                if (controller != null) invoke(cancel);
                if (saved >= 0) set(saved);
                shell("input keyevent 224");
            } catch (Exception e) { result.putString("restore_error", e.toString()); }
        }
        result.putString("tests", tests.toString());
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }
    private Method method(String name, Class<?>... args) throws Exception {
        Method m = controller.getClass().getDeclaredMethod(name, args);
        m.setAccessible(true); return m;
    }
    private Object invoke(Method m, Object... args) throws Exception {
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        runOnMainSync(() -> {
            try { value.set(m.invoke(controller, args)); } catch (Exception e) { error.set(e); }
        });
        if (error.get() != null) throw error.get();
        return value.get();
    }
    private void set(int value) {
        runOnMainSync(() -> audio.setStreamVolume(3, value, 0));
    }
    private boolean waitFor(int value) throws Exception {
        long end = SystemClock.uptimeMillis() + 1500;
        while (SystemClock.uptimeMillis() < end) {
            if (audio.getStreamVolume(3) == value) return true;
            Thread.sleep(20);
        }
        return false;
    }
    private String report() throws Exception { return (String) invoke(diagnostics); }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor d = getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);
             FileInputStream input = new FileInputStream(d.getFileDescriptor())) { input.readAllBytes(); }
    }
    private void check(String name, boolean pass) throws Exception {
        tests.put(new JSONObject().put("test", name).put("pass", pass));
        if (!pass) throw new IllegalStateException(name);
    }
}
