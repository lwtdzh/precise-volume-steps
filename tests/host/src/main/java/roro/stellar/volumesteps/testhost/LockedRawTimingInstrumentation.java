package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic matched raw callbacks with real helper IPC/readback; not physical key evidence. */
public final class LockedRawTimingInstrumentation extends Instrumentation {
    private Object controller;
    private Object remote;
    private Method adjust;
    private Method rawKey;
    private Method cancel;
    private Method diagnostics;
    private AudioManager audio;
    private final JSONArray tests = new JSONArray();
    private final JSONArray traces = new JSONArray();

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int saved = -1;
        try {
            Context target = getTargetContext();
            audio = target.getSystemService(AudioManager.class);
            saved = audio.getStreamVolume(3);
            ClassLoader loader = target.getClassLoader();
            Class<?> keeper = loader.loadClass(target.getPackageName() + ".VolumeKeeperService");
            Method get = keeper.getDeclaredMethod("controller");
            get.setAccessible(true);
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            for (int i = 0; i < 80 && remote == null; i++) {
                controller = get.invoke(null);
                if (controller != null) {
                    Field bridge = controller.getClass().getDeclaredField("compatibility");
                    bridge.setAccessible(true);
                    Object connection = bridge.get(controller);
                    Field service = connection.getClass().getDeclaredField("remote");
                    service.setAccessible(true);
                    remote = service.get(connection);
                }
                if (remote == null) Thread.sleep(100);
            }
            if (remote == null) throw new IllegalStateException("Assistant helper required");
            adjust = loader.loadClass("roro.stellar.volumesteps.compatibility.IVolumeCompatibility")
                    .getMethod("adjust", int.class, int.class, int.class, long.class);
            rawKey = method("onExplicitKey", String.class, int.class, int.class,
                    long.class, long.class, boolean.class, int.class);
            cancel = method("cancelPending");
            diagnostics = method("diagnostics");
            for (int i = 0; i < 80 && !report().contains(
                    "input checked=true, available=true, registered=true"); i++) Thread.sleep(100);
            check("assistant input ready", report().contains(
                    "input checked=true, available=true, registered=true"));
            check("media-only assistant mode", target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0).getBoolean("media_only", true)
                    && report().contains("Shizuku=enabled=false"));
            check("controlled idle playback", !audio.isMusicActive());
            shell("input keyevent 223");
            Thread.sleep(500);
            shell("input keyevent 224");
            Thread.sleep(700);
            check("actual lit keyguard", report().contains(
                    "screen interactive=true, keyguard locked=true"));
            int minimum = audio.getStreamMinVolume(3);
            int maximum = audio.getStreamMaxVolume(3);
            int origin = minimum + (maximum - minimum) / 2;
            int steps = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0).getInt("steps", 30);
            int targetVolume = origin + (int) Math.round(
                    -(double) (maximum - minimum) / Math.min(steps, maximum - minimum));
            reset(origin);
            press(1);
            set(Math.max(minimum, origin - Math.max(1, (maximum - minimum) / 15)));
            Thread.sleep(60);
            long releasedAt = SystemClock.uptimeMillis();
            press(0);
            long correctedAt = awaitVolume(targetVolume, releasedAt + 1200);
            long panelAt = awaitPanel(releasedAt + 1600);
            traces.put(new JSONObject().put("release_to_readback_ms", correctedAt - releasedAt)
                    .put("release_to_panel_ms", panelAt - releasedAt).put("service", report()));
            check("lit raw readback within 200 ms of release", correctedAt - releasedAt < 200);
            check("lit raw panel within 250 ms of release", panelAt - releasedAt < 250);

            // Force a late firmware-like overwrite during the existing guard.
            set(Math.max(minimum, origin - Math.max(1, (maximum - minimum) / 15)));
            Thread.sleep(20);
            String pending = report();
            check("verified readback settles silently while the guard remains active",
                    pending.contains("correction state=SETTLING")
                    && pending.contains("status text=\"\"")
                    && !pending.contains("pending correction=none"));
            check("late coarse write does not replace displayed actual target",
                    pending.contains("displayed volume=" + targetVolume)
                    && pending.contains("system readback=" + targetVolume + "/" + maximum));
            awaitVolume(targetVolume, SystemClock.uptimeMillis() + 1200);
            Thread.sleep(400);
            check("late native overwrite is repaired", audio.getStreamVolume(3) == targetVolume);
            check("late write guard runs once", report().contains("late native write after early correction"));

            reset(origin);
            press(1);
            Thread.sleep(60);
            releasedAt = SystemClock.uptimeMillis();
            press(0);
            correctedAt = awaitVolume(targetVolume, releasedAt + 1200);
            check("matched consumed lit key corrects within 200 ms",
                    correctedAt - releasedAt < 200 && audio.getStreamVolume(3) == targetVolume);
            Thread.sleep(400);

            // A new opposite key supersedes the guard without losing the original rounding path.
            reset(origin);
            press(1);
            set(Math.max(minimum, origin - Math.max(1, (maximum - minimum) / 15)));
            Thread.sleep(60);
            press(0);
            awaitVolume(targetVolume, SystemClock.uptimeMillis() + 500);
            pressUp(1);
            set(Math.min(maximum, targetVolume + Math.max(1, (maximum - minimum) / 15)));
            Thread.sleep(60);
            pressUp(0);
            awaitVolume(origin, SystemClock.uptimeMillis() + 1200);
            Thread.sleep(400);
            check("pending opposite key returns exactly to origin", audio.getStreamVolume(3) == origin);
            check("settled correction completes", report().contains("pending correction=none"));
            invoke(cancel);
            set(origin + 1);
            Thread.sleep(450);
            check("locked passive preset remains passive", audio.getStreamVolume(3) == origin + 1
                    && report().contains("overlay=attached=false"));
            reset(origin);
            shell("input keyevent 223");
            Thread.sleep(500);
            check("actual display off for unchanged-path check", report().contains(
                    "screen interactive=false"));
            press(1);
            Thread.sleep(60);
            releasedAt = SystemClock.uptimeMillis();
            press(0);
            correctedAt = awaitVolume(targetVolume, releasedAt + 1200);
            check("screen-off idle retains settling delay",
                    correctedAt - releasedAt >= 250 && correctedAt - releasedAt < 1200
                            && audio.getStreamVolume(3) == targetVolume);
            check("screen-off correction keeps panel hidden", report().contains(
                    "overlay=attached=false") && report().contains("screen interactive=false"));
            result.putString("diagnostics", report());
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            try { result.putString("diagnostics", report()); } catch (Exception ignored) { }
        } finally {
            try {
                if (controller != null) invoke(cancel);
                if (saved >= 0 && adjust != null) set(saved);
                shell("input keyevent 224");
            } catch (Exception exception) { result.putString("restore_error", exception.toString()); }
        }
        result.putString("tests", tests.toString());
        result.putString("timing", traces.toString());
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }

    private void reset(int volume) throws Exception {
        invoke(cancel);
        set(volume);
        Thread.sleep(450);
        invoke(cancel);
    }

    private void set(int volume) throws Exception {
        int result = (int) adjust.invoke(remote, 3, volume, audio.getStreamVolume(3),
                SystemClock.uptimeMillis() + 500);
        if (result != volume) throw new IllegalStateException("Preset rejected: " + result);
    }

    private void press(int value) throws Exception { press(114, value); }
    private void pressUp(int value) throws Exception { press(115, value); }
    private void press(int code, int value) throws Exception {
        long now = SystemClock.uptimeMillis();
        invoke(rawKey, "locked-timing-fixture", code, value, now, now, true, -1);
    }

    private long awaitVolume(int volume, long deadline) throws Exception {
        while (audio.getStreamVolume(3) != volume && SystemClock.uptimeMillis() < deadline)
            Thread.sleep(5);
        return SystemClock.uptimeMillis();
    }

    private long awaitPanel(long deadline) throws Exception {
        while (!report().contains("overlay=attached=true")
                && SystemClock.uptimeMillis() < deadline) Thread.sleep(5);
        return SystemClock.uptimeMillis();
    }

    private String report() throws Exception { return (String) invoke(diagnostics); }
    private Method method(String name, Class<?>... args) throws Exception {
        Method method = controller.getClass().getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
    private Object invoke(Method method, Object... args) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        runOnMainSync(() -> {
            try { result.set(method.invoke(controller, args)); }
            catch (Exception exception) { error.set(exception); }
        });
        if (error.get() != null) throw error.get();
        return result.get();
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            stream.readAllBytes();
        }
    }
    private void check(String name, boolean pass) throws Exception {
        tests.put(new JSONObject().put("test", name).put("pass", pass));
        if (!pass) throw new IllegalStateException(name);
    }
}
