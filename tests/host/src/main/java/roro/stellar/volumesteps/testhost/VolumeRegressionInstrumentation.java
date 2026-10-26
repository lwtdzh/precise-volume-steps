package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Exercises the real device AudioManager with synthetic controller callbacks.
 * These tests do not claim to inject physical keys or verify OEM key routing.
 */
public final class VolumeRegressionInstrumentation extends Instrumentation {
    private Object controller;
    private Method onKey;
    private Method diagnostics;
    private Method cancel;
    private AudioManager audio;
    private final JSONArray results = new JSONArray();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int saved = -1;
        try {
            Context target = getTargetContext();
            result.putString("target_op_package", target.getOpPackageName());
            result.putString("test_package", getContext().getPackageName());
            audio = target.getSystemService(AudioManager.class);
            saved = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            runOnMainSync(() -> target.startActivity(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            Class<?> keeper = target.getClassLoader().loadClass(
                    target.getPackageName() + ".VolumeKeeperService");
            Method getController = keeper.getDeclaredMethod("controller");
            getController.setAccessible(true);
            for (int i = 0; i < 50 && controller == null; i++) {
                controller = getController.invoke(null);
                if (controller == null) Thread.sleep(100);
            }
            if (controller == null) throw new IllegalStateException("Monitor did not start");
            onKey = controller.getClass().getDeclaredMethod("onKey", KeyEvent.class);
            diagnostics = controller.getClass().getDeclaredMethod("diagnostics");
            cancel = controller.getClass().getDeclaredMethod("cancelPending");
            onKey.setAccessible(true);
            diagnostics.setAccessible(true);
            cancel.setAccessible(true);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int steps = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", Context.MODE_PRIVATE).getInt("steps", 30);
            // Use a quiet but nonzero baseline so correction checks cannot
            // pass trivially at the lower volume limit. Restore the user's
            // original volume in finally, including when it was muted.
            int baseline = Math.min(max, Math.max(3 * Math.max(1, max / steps),
                    Math.min(saved, Math.max(1, saved - 15))));
            reset(baseline);
            check("idle does not open panel", noPanelFor(1500), report());
            for (int value : new int[]{Math.max(0, baseline - 1), baseline, 0, baseline}) {
                set(value);
                boolean noPanel = noPanelFor(1100);
                check("passive preset " + value + " preserved without popup",
                        noPanel && volume() == value, report());
            }
            reset(baseline);
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
            check("key consumed without native change does not open panel",
                    noPanelFor(1000) && volume() == baseline, report());
            reset(baseline);
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.FLAG_CANCELED);
            set(Math.max(0, baseline - 1));
            check("canceled key does not authorize later volume change",
                    noPanelFor(1100) && volume() == Math.max(0, baseline - 1), report());

            for (int delay : new int[]{0, 320}) {
                reset(baseline);
                key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
                Thread.sleep(delay);
                set(Math.max(0, baseline - 10));
                key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
                int targetVolume = next(baseline, max, steps, -1);
                check("synthetic key with native change delay " + delay + "ms",
                        waitFor(targetVolume, 1400), report());
                check("corrected volume remains stable " + delay + "ms",
                        stable(targetVolume, 1100), report());
            }
            reset(baseline);
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
            set(Math.max(0, baseline - 10));
            Thread.sleep(110);
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
            set(Math.max(0, volume() - 10));
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
            int twice = next(next(baseline, max, steps, -1), max, steps, -1);
            check("two synthetic presses while device writes may be pending",
                    waitFor(twice, 1600) && stable(twice, 1100), report());
            result.putString("diagnostics", report());
            result.putString("tests", results.toString());
            result.putString("input", "synthetic controller callbacks; real AudioManager");
            result.putString("device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            result.putString("tests", results.toString());
        } finally {
            if (saved >= 0) {
                try {
                    if (controller != null) invoke(cancel);
                    set(saved);
                    if (!waitFor(saved, 2500)) result.putString("restore_error", "Volume did not restore");
                } catch (Exception exception) {
                    result.putString("restore_error", exception.toString());
                }
            }
        }
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }

    private void check(String name, boolean pass, String detail) throws Exception {
        results.put(new JSONObject().put("test", name).put("pass", pass).put("detail", detail));
        Bundle status = new Bundle();
        status.putString("test", name);
        status.putBoolean("pass", pass);
        sendStatus(0, status);
        if (!pass) throw new IllegalStateException(name);
    }

    private void reset(int value) throws Exception {
        invoke(cancel);
        set(value);
        if (!waitFor(value, 2500)) throw new IllegalStateException(
                "Baseline " + value + " did not settle; actual=" + volume() + "\n" + report());
        Thread.sleep(300);
    }

    private void set(int value) {
        runOnMainSync(() -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0));
    }

    private int volume() {
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private boolean waitFor(int value, long timeout) throws Exception {
        long end = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < end) {
            if (volume() == value) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private boolean stable(int value, long duration) throws Exception {
        long end = SystemClock.uptimeMillis() + duration;
        while (SystemClock.uptimeMillis() < end) {
            if (volume() != value) return false;
            Thread.sleep(20);
        }
        return true;
    }

    private boolean noPanelFor(long duration) throws Exception {
        long end = SystemClock.uptimeMillis() + duration;
        while (SystemClock.uptimeMillis() < end) {
            if (report().contains("overlay=attached=true")) return false;
            Thread.sleep(25);
        }
        return true;
    }

    private void key(int action, int code, int flags) throws Exception {
        long now = SystemClock.uptimeMillis();
        invoke(onKey, new KeyEvent(now, now, action, code, 0, 0, -1, 0, flags));
    }

    private String report() throws Exception {
        return (String) invoke(diagnostics);
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

    private static int next(int value, int max, int steps, int direction) {
        int intervals = Math.min(max, steps);
        int result = direction > 0 ? max : 0;
        for (int i = 0; i <= intervals; i++) {
            int point = Math.round((float) i * max / intervals);
            if (direction > 0 && point > value) result = Math.min(result, point);
            if (direction < 0 && point < value) result = Math.max(result, point);
        }
        return result;
    }
}
