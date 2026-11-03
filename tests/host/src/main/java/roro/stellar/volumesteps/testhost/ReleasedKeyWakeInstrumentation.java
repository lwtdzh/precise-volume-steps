package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Emulator only: synthetic helper key delivery with real helper Binder/audio and power state. */
public final class ReleasedKeyWakeInstrumentation extends Instrumentation {
    private final JSONArray tests = new JSONArray();
    private final JSONArray traces = new JSONArray();
    private Object controller;
    private Object bridge;
    private AudioManager audio;
    private Method rawKey;
    private Method key;
    private Method cancel;
    private Method diagnostics;
    private int origin;
    private int minimum;
    private int maximum;
    private int steps;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        steps = Integer.parseInt(args.getString("steps", "5"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = null;
        Map<String, ?> savedPrefs = null;
        ContentProviderClient client = null;
        int savedVolume = -1;
        boolean awake = true, locked = false;
        try {
            if (!android.os.Build.MODEL.toLowerCase().contains("sdk")) {
                throw new IllegalStateException("Disposable emulator only");
            }
            if (steps < 5 || steps > 150) throw new IllegalArgumentException("Invalid steps");
            Context target = getTargetContext();
            prefs = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0);
            savedPrefs = prefs.getAll();
            audio = target.getSystemService(AudioManager.class);
            savedVolume = audio.getStreamVolume(3);
            awake = target.getSystemService(PowerManager.class).isInteractive();
            locked = target.getSystemService(KeyguardManager.class).isKeyguardLocked();
            prefs.edit().putBoolean("enabled", true).putBoolean("media_only", true)
                    .putString("operating_mode", "ASSISTANT").putBoolean("shizuku_enabled", false)
                    .putInt("steps", steps).commit();
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            Method get = target.getClassLoader().loadClass(
                    target.getPackageName() + ".VolumeKeeperService").getDeclaredMethod("controller");
            get.setAccessible(true);
            for (int i = 0; i < 50 && controller == null; i++) {
                controller = get.invoke(null);
                if (controller == null) Thread.sleep(100);
            }
            if (controller == null) throw new IllegalStateException("Controller unavailable");
            rawKey = method("onExplicitKey", String.class, int.class, int.class,
                    long.class, long.class, boolean.class, int.class);
            key = method("onKey", KeyEvent.class);
            cancel = method("cancelPending");
            diagnostics = method("diagnostics");
            Thread.sleep(400);
            bridge = field(controller, "compatibility").get(controller);
            client = target.getContentResolver().acquireUnstableContentProviderClient(
                    Uri.parse("content://com.vivo.finevolumesteps.helper.volume"));
            if (client == null) throw new IllegalStateException("Same-signed helper required");
            IBinder binder = client.call("connect", null, null).getBinder("volume");
            Object remote = target.getClassLoader().loadClass(
                    "roro.stellar.volumesteps.compatibility.IVolumeCompatibility$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
            field(bridge, "remote").set(bridge, remote);
            // AOSP has no Vivo EventHub nodes. Supply readiness only for synthetic delivery;
            // the actual helper provider and volume writes remain real.
            field(bridge, "inputChecked").setBoolean(bridge, true);
            field(bridge, "inputAvailable").setBoolean(bridge, true);
            field(bridge, "inputRegistered").setBoolean(bridge, true);
            minimum = audio.getStreamMinVolume(3);
            maximum = audio.getStreamMaxVolume(3);
            origin = maximum - 1;
            check("fixture uses assistant with real helper IPC", report().contains("ready=true")
                    && report().contains("Shizuku=enabled=false") && !audio.isMusicActive());

            // User's sequence: two settled presses, third release, power wake, awake down.
            reset();
            tap();
            settled(target(1));
            check("first asleep press completes", audio.getStreamVolume(3) == target(1));
            tap();
            settled(target(2));
            check("second asleep press completes", audio.getStreamVolume(3) == target(2));
            press(114, 1);
            Thread.sleep(40);
            press(114, 0);
            press(116, 1);
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            press(116, 0);
            settled(target(3));
            trace("three asleep presses then immediate power wake");
            check("released third press survives power wake", audio.getStreamVolume(3) == target(3));
            awakeDown();
            settled(target(4));
            check("awake verification press starts from all three asleep presses",
                    audio.getStreamVolume(3) == target(4));

            // A screen change without any power callback must also preserve the release.
            reset();
            tap();
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            settled(target(1));
            trace("release then unlock without power callback");
            check("released press survives unlock", audio.getStreamVolume(3) == target(1));

            // Three releases inside the 300 ms wait must retain their complete count.
            reset();
            tap();
            tap();
            tap();
            press(116, 1);
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            press(116, 0);
            settled(target(3));
            check("rapid released burst survives wake", audio.getStreamVolume(3) == target(3));

            reset();
            tap();
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            awakeDown();
            settled(target(2));
            check("awake key during settling retains the released asleep key",
                    audio.getStreamVolume(3) == target(2));

            reset();
            press(114, 1);
            set(origin - 1);
            Thread.sleep(40);
            press(114, 0);
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            settled(target(1));
            check("observed native transition still corrects after unlock",
                    audio.getStreamVolume(3) == target(1));

            reset();
            tap();
            press(114, 0);
            long stale = SystemClock.uptimeMillis() - 251;
            invoke(rawKey, "synthetic-helper-wake", 114, 1, stale, stale, true, -1);
            settled(target(1));
            check("duplicate release and stale down do not cancel an accepted release",
                    audio.getStreamVolume(3) == target(1));

            // Power while the volume key is held is still a chord, not a completed press.
            reset();
            press(114, 1);
            press(116, 1);
            press(114, 0);
            press(116, 0);
            Thread.sleep(800);
            check("power chord remains canceled", audio.getStreamVolume(3) == origin);
            reset();
            press(114, 1);
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            press(114, 0);
            Thread.sleep(800);
            check("unlock before release does not authorize a consumed key",
                    audio.getStreamVolume(3) == origin);

            reset();
            press(114, 0);
            Thread.sleep(800);
            check("unmatched release remains passive", audio.getStreamVolume(3) == origin);
            set(origin - 1);
            Thread.sleep(800);
            check("passive off-screen preset remains unchanged",
                    audio.getStreamVolume(3) == origin - 1);
            check("off-screen work keeps the panel hidden and display asleep",
                    report().contains("overlay=attached=false")
                            && report().contains("screen interactive=false"));
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            try { trace("failure"); } catch (Exception ignored) { }
        } finally {
            try {
                if (controller != null) invoke(cancel);
                if (savedVolume >= 0) set(savedVolume);
                if (bridge != null) {
                    field(bridge, "inputRegistered").setBoolean(bridge, false);
                    field(bridge, "inputAvailable").setBoolean(bridge, false);
                    field(bridge, "inputChecked").setBoolean(bridge, false);
                    field(bridge, "remote").set(bridge, null);
                }
                if (client != null) client.close();
                if (prefs != null && savedPrefs != null) {
                    SharedPreferences.Editor edit = prefs.edit();
                    for (String name : new String[]{"enabled", "media_only", "operating_mode",
                            "shizuku_enabled", "steps"}) {
                        Object value = savedPrefs.get(name);
                        if (value == null) edit.remove(name);
                        else if (value instanceof Boolean) edit.putBoolean(name, (Boolean) value);
                        else if (value instanceof Integer) edit.putInt(name, (Integer) value);
                        else edit.putString(name, (String) value);
                    }
                    edit.commit();
                }
                shell(awake ? "input keyevent 224" : "input keyevent 223");
                if (awake && !locked) shell("wm dismiss-keyguard");
            } catch (Exception exception) { result.putString("restore_error", exception.toString()); }
        }
        result.putString("tests", tests.toString());
        result.putString("timing", traces.toString());
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }

    private int target(int count) {
        return Math.max(minimum, origin + (int) Math.round(
                -count * (double) (maximum - minimum) / Math.min(steps, maximum - minimum)));
    }
    private void reset() throws Exception {
        shell("input keyevent 224");
        shell("wm dismiss-keyguard");
        invoke(cancel);
        set(origin);
        Thread.sleep(400);
        invoke(cancel);
        shell("input keyevent 223");
        Thread.sleep(500);
        check("actual display off before sequence", report().contains("screen interactive=false"));
    }
    private void tap() throws Exception {
        press(114, 1);
        Thread.sleep(40);
        press(114, 0);
    }
    private void press(int code, int value) throws Exception {
        long now = SystemClock.uptimeMillis();
        invoke(rawKey, "synthetic-helper-wake", code, value, now, now, true, -1);
    }
    private void awakeDown() throws Exception {
        long now = SystemClock.uptimeMillis();
        invoke(key, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0));
        set(audio.getStreamVolume(3) - 1);
        invoke(key, new KeyEvent(now, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0));
    }
    private void settled(int expected) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 1500;
        while (SystemClock.uptimeMillis() < deadline
                && (audio.getStreamVolume(3) != expected
                || !report().contains("pending correction=none"))) Thread.sleep(20);
        Thread.sleep(1000); // Inspect the settled value after the first match, including late writes.
    }
    private void set(int value) { runOnMainSync(() -> audio.setStreamVolume(3, value, 0)); }
    private void trace(String name) throws Exception {
        traces.put(new JSONObject().put("sequence", name).put("actual", audio.getStreamVolume(3))
                .put("service", report()));
    }
    private String report() throws Exception { return (String) invoke(diagnostics); }
    private Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private Method method(String name, Class<?>... args) throws Exception {
        Method method = controller.getClass().getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
    private Object invoke(Method method, Object... args) throws Exception {
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        runOnMainSync(() -> {
            try { value.set(method.invoke(controller, args)); }
            catch (Exception exception) { error.set(exception); }
        });
        if (error.get() != null) throw error.get();
        return value.get();
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
