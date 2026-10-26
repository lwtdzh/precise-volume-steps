package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real selected helper IPC and system audio, synthetic keys, app backgrounded. */
public final class ShizukuRegressionInstrumentation extends Instrumentation {
    private boolean companion;
    private boolean screenOff;
    private boolean screenChanged;
    private int requestedSteps;
    private Object controller;
    private Object remote;
    private Method adjust;
    private Method key;
    private Method diagnostics;
    private Method cancel;
    private AudioManager audio;
    private final JSONArray tests = new JSONArray();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        companion = arguments != null && "companion".equals(arguments.getString("backend"));
        screenOff = arguments != null && "true".equals(arguments.getString("screen_off"));
        requestedSteps = arguments == null ? 0 : Integer.parseInt(arguments.getString("steps", "0"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int saved = -1;
        SharedPreferences settings = null;
        int savedSteps = -1;
        try {
            Context target = getTargetContext();
            settings = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0);
            savedSteps = settings.getInt("steps", 30);
            if (requestedSteps > 0) settings.edit().putInt("steps", requestedSteps).commit();
            audio = target.getSystemService(AudioManager.class);
            saved = volume();
            ClassLoader loader = target.getClassLoader();
            Class<?> keeper = loader.loadClass(target.getPackageName() + ".VolumeKeeperService");
            Method get = keeper.getDeclaredMethod("controller");
            get.setAccessible(true);
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            for (int i = 0; i < 100 && remote == null; i++) {
                controller = get.invoke(null);
                if (controller != null) {
                    Field bridge = controller.getClass().getDeclaredField(companion ? "compatibility" : "shizuku");
                    bridge.setAccessible(true);
                    Object connection = bridge.get(controller);
                    Field service = connection.getClass().getDeclaredField("remote");
                    service.setAccessible(true);
                    remote = service.get(connection);
                }
                if (remote == null) Thread.sleep(100);
            }
            if (remote == null) throw new IllegalStateException("Selected volume helper not connected");
            adjust = loader.loadClass(companion
                    ? "roro.stellar.volumesteps.compatibility.IVolumeCompatibility"
                        : target.getPackageName() + ".IVolumeShell")
                    .getMethod("adjust", int.class, int.class, int.class, long.class);
            key = method("onKey", KeyEvent.class);
            diagnostics = method("diagnostics");
            cancel = method("cancelPending");
            runOnMainSync(() -> target.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            Thread.sleep(700);
            if (screenOff) {
                shell("input keyevent 223");
                screenChanged = true;
                Thread.sleep(400);
                check("display remains off before correction", report().contains("screen interactive=false"));
            }
            int max = audio.getStreamMaxVolume(3);
            int steps = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0).getInt("steps", 30);
            int baseline = Math.max(3, Math.min(saved, max / 3));
            reset(baseline);
            check("connected helper in background", report().contains("ready=true"));
            if (companion) {
                check("Shizuku disabled during companion checks",
                        report().contains("Shizuku=enabled=false, ready=false"));
                check("ordinary companion ready",
                        report().contains("companion=supported=true, ready=true"));
            }
            check("idle background does not open panel", quiet(1400));
            int stale = call(baseline - 1, baseline + 1, SystemClock.uptimeMillis() + 500);
            check("stale expected volume skips write", stale == -1 && volume() == baseline);
            int expired = call(baseline - 1, baseline, SystemClock.uptimeMillis() - 1);
            check("expired request skips write", expired == -1 && volume() == baseline);
            boolean refused = false;
            try {
                adjust.invoke(remote, 7, 0, 0, SystemClock.uptimeMillis() + 500);
            } catch (ReflectiveOperationException exception) {
                refused = true;
            }
            check("internal stream rejected", refused && volume() == baseline);
            for (int n = 0; n < 4; n++) {
                int value = baseline + n % 2;
                call(value, volume(), SystemClock.uptimeMillis() + 500);
                check("passive helper preset " + n + " stays quiet",
                        waitFor(value, 600) && quiet(400));
            }
            reset(baseline);
            press(-1);
            check("consumed key stays quiet", quiet(900) && volume() == baseline);
            for (int n = 0; n < 20; n++) {
                int before = volume();
                int direction = n % 2 == 0 ? 1 : -1;
                int expected = direction > 0 ? next(before, max, steps, direction) : baseline;
                press(direction);
                int nativeTarget = Math.max(0, Math.min(max, before + direction * 10));
                int accepted = call(nativeTarget, before, SystemClock.uptimeMillis() + 500);
                check("background press " + n + " native transition", accepted >= 0);
                check("background press " + n + " fine correction", waitFor(expected, 900));
                check("background press " + n + " stable", stable(expected, 150));
            }
            int before = volume();
            for (int n = 0; n < 3; n++) {
                press(1);
                call(Math.min(max, volume() + 10), volume(), SystemClock.uptimeMillis() + 500);
                Thread.sleep(10);
            }
            int expected = target(before, max, steps, 3);
            check("rapid three-key batch", waitFor(expected, 1000));
            check("rapid batch stable after panel dismiss", stable(expected, 1400));
            check("panel dismissed after keys", quiet(500));
            int offGrid = Math.min(max - 11, Math.max(4, baseline + 1));
            reset(offGrid);
            press(1);
            call(Math.min(max, offGrid + 10), volume(), SystemClock.uptimeMillis() + 500);
            check("off-grid outward correction", waitFor(next(offGrid, max, steps, 1), 1000));
            press(-1);
            call(Math.max(0, volume() - 10), volume(), SystemClock.uptimeMillis() + 500);
            check("opposite key restores exact off-grid origin", waitFor(offGrid, 1000));
            reset(offGrid);
            press(1);
            call(Math.min(max, offGrid + 10), volume(), SystemClock.uptimeMillis() + 500);
            Thread.sleep(10);
            press(-1);
            call(Math.max(0, volume() - 10), volume(), SystemClock.uptimeMillis() + 500);
            check("immediate opposite keys restore origin", stableAfter(offGrid));
            reset(offGrid);
            for (int n = 1; n <= 3; n++) {
                press(1);
                call(Math.min(max, volume() + 10), volume(), SystemClock.uptimeMillis() + 500);
                check("off-grid continuation " + n + " has full rounded spacing",
                        waitFor(target(offGrid, max, steps, n), 1000));
            }
            for (int n = 2; n >= 0; n--) {
                press(-1);
                call(Math.max(0, volume() - 10), volume(), SystemClock.uptimeMillis() + 500);
                check("opposite continuation retraces point " + n,
                        waitFor(target(offGrid, max, steps, n), 1000));
            }
            for (int n = 1; n <= 3; n++) {
                press(1);
                call(Math.min(max, volume() + 10), volume(), SystemClock.uptimeMillis() + 500);
                check("revisited point " + n + " has identical spacing",
                        waitFor(target(offGrid, max, steps, n), 1000));
            }
            if (screenOff) {
                shell("input keyevent 223");
                Thread.sleep(200);
                check("screen off before key-up recovery", report().contains("screen interactive=false"));
                reset(offGrid);
                long down = SystemClock.uptimeMillis();
                call(offGrid + 10, offGrid, down + 500);
                Thread.sleep(60);
                invoke(key, new KeyEvent(down, SystemClock.uptimeMillis(),
                        KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0));
                check("screen-off key-up-only correction",
                        waitFor(next(offGrid, max, steps, 1), 1000));
                check("screen stays off after correction", report().contains("screen interactive=false"));
                Thread.sleep(2200);
                check("bounded key wake lock released", report().contains("key wake lock held=false"));
            }
            result.putString("diagnostics", report());
            result.putString("input", "synthetic controller key callbacks; actual "
                    + (companion ? "ordinary companion" : "Shizuku")
                    + " IPC and system audio; app on home screen");
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            try { result.putString("diagnostics", report()); } catch (Exception ignored) { }
        } finally {
            if (saved >= 0 && remote != null) {
                try {
                    invoke(cancel);
                    call(saved, volume(), SystemClock.uptimeMillis() + 500);
                    if (!waitFor(saved, 1500)) result.putString("restore_error", "volume=" + volume());
                } catch (Exception exception) {
                    result.putString("restore_error", exception.toString());
                }
            }
            if (screenChanged) {
                try { shell("input keyevent 224"); } catch (Exception ignored) { }
            }
            if (settings != null && requestedSteps > 0 && savedSteps >= 0) {
                settings.edit().putInt("steps", savedSteps).commit();
            }
        }
        result.putString("tests", tests.toString());
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }

    private Method method(String name, Class<?>... types) throws Exception {
        Method value = controller.getClass().getDeclaredMethod(name, types);
        value.setAccessible(true);
        return value;
    }

    private int call(int value, int before, long expires) throws Exception {
        return (Integer) adjust.invoke(remote, 3, value, before, expires);
    }

    private void reset(int value) throws Exception {
        invoke(cancel);
        call(value, volume(), SystemClock.uptimeMillis() + 500);
        if (!waitFor(value, 1000)) throw new IllegalStateException("Baseline not applied");
        Thread.sleep(100);
    }

    private void press(int direction) throws Exception {
        long now = SystemClock.uptimeMillis();
        int code = direction > 0 ? KeyEvent.KEYCODE_VOLUME_UP : KeyEvent.KEYCODE_VOLUME_DOWN;
        invoke(key, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        invoke(key, new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    private int volume() { return audio.getStreamVolume(3); }
    private boolean stableAfter(int value) throws Exception {
        return waitFor(value, 1000) && stable(value, 1300);
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            input.readAllBytes();
        }
    }
    private String report() throws Exception { return (String) invoke(diagnostics); }
    private boolean waitFor(int expected, long duration) throws Exception {
        long end = SystemClock.uptimeMillis() + duration;
        do {
            if (volume() == expected) return true;
            Thread.sleep(10);
        } while (SystemClock.uptimeMillis() < end);
        return false;
    }
    private boolean stable(int expected, long duration) throws Exception {
        long end = SystemClock.uptimeMillis() + duration;
        do {
            if (volume() != expected) return false;
            Thread.sleep(10);
        } while (SystemClock.uptimeMillis() < end);
        return true;
    }
    private boolean quiet(long duration) throws Exception {
        long end = SystemClock.uptimeMillis() + duration;
        do {
            if (report().contains("overlay=attached=true")) return false;
            Thread.sleep(25);
        } while (SystemClock.uptimeMillis() < end);
        return true;
    }
    private Object invoke(Method method, Object... args) throws Exception {
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { value.set(method.invoke(controller, args)); }
            catch (Exception exception) { failure.set(exception); }
        });
        if (failure.get() != null) throw failure.get();
        return value.get();
    }
    private void check(String name, boolean pass) throws Exception {
        tests.put(new JSONObject().put("test", name).put("pass", pass));
        Bundle status = new Bundle();
        status.putString("test", name);
        status.putBoolean("pass", pass);
        sendStatus(0, status);
        if (!pass) throw new IllegalStateException(name);
    }
    private static int next(int value, int max, int steps, int direction) {
        return target(value, max, steps, direction);
    }
    private static int target(int origin, int max, int steps, int position) {
        return Math.max(0, Math.min(max, origin + (int) Math.round(
                position * (double) max / Math.min(max, steps))));
    }
}
