package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.ViewConfiguration;
import android.view.View;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Emulator: synthetic raw edges/readiness, real selected privileged volume IPC/readback. */
public final class PrivilegedHoldInstrumentation extends Instrumentation {
    private final JSONArray tests = new JSONArray(), traces = new JSONArray();
    private Object controller, reader;
    private AudioManager audio;
    private Method raw, cancel, diagnostics;
    private int steps, origin, minimum, maximum;
    private boolean root, settlingOnly, nativeHoldOnly;
    private boolean failed;
    private String node;
    private final ArrayList<Integer> displayedFrames = new ArrayList<>(), systemFrames = new ArrayList<>();
    private final ArrayList<String> frameStates = new ArrayList<>(), rowBounds = new ArrayList<>();

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        steps = Integer.parseInt(args.getString("steps", "10"));
        root = "root".equals(args.getString("backend"));
        settlingOnly = "settling".equals(args.getString("scenario"));
        nativeHoldOnly = "native-hold".equals(args.getString("scenario"));
        node = root ? "root:synthetic-hold" : "synthetic-hold";
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = null;
        Map<String, ?> savedPrefs = null;
        int savedVolume = -1;
        boolean awake = true, locked = false;
        boolean accessibilityChanged = false;
        String savedAccessibility = null, savedMaster = null;
        try {
            if (!android.os.Build.MODEL.toLowerCase().contains("sdk"))
                throw new IllegalStateException("Disposable emulator only");
            Context target = getTargetContext();
            audio = target.getSystemService(AudioManager.class);
            savedVolume = audio.getStreamVolume(3);
            awake = target.getSystemService(PowerManager.class).isInteractive();
            locked = target.getSystemService(KeyguardManager.class).isKeyguardLocked();
            savedAccessibility = shell("settings get secure enabled_accessibility_services").trim();
            savedMaster = shell("settings get secure accessibility_enabled").trim();
            if (!savedAccessibility.matches("[A-Za-z0-9_./:$-]+")
                    && !savedAccessibility.equals("null"))
                throw new IllegalStateException("Unexpected accessibility list; preserve it before testing");
            prefs = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0);
            savedPrefs = prefs.getAll();
            prefs.edit().putBoolean("enabled", true).putBoolean("media_only", true)
                    .putString("operating_mode", root ? "ROOT" : "SHIZUKU")
                    .putBoolean("shizuku_enabled", !root).putInt("steps", steps).commit();
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            ClassLoader loader = target.getClassLoader();
            if (root) loader.loadClass("com.topjohnwu.superuser.Shell")
                    .getField("enableVerboseLogging").setBoolean(null, true);
            Method get = loader.loadClass(target.getPackageName() + ".VolumeKeeperService")
                    .getDeclaredMethod("controller");
            get.setAccessible(true);
            Object writer = null;
            boolean setupRequested = false;
            for (int i = 0; i < 100; i++) {
                controller = get.invoke(null);
                if (controller != null) {
                    diagnostics = method("diagnostics");
                    writer = field(controller, root ? "root" : "shizuku").get(controller);
                    if (root && !setupRequested) {
                        // Drain the preference listener's posted mode transition
                        // before authorization captures its generation.
                        waitForIdleSync();
                        Method setup = method("setupRoot", java.util.function.Consumer.class);
                        invoke(setup, (java.util.function.Consumer<String>) text ->
                                result.putString("root_setup", text));
                        setupRequested = true;
                    }
                    Method ready = writer.getClass().getDeclaredMethod("ready");
                    ready.setAccessible(true);
                    if ((boolean) ready.invoke(writer)) break;
                }
                Thread.sleep(100);
            }
            if (writer == null || field(writer, "remote").get(writer) == null)
                throw new IllegalStateException("Real selected privileged writer required");
            raw = method("onExplicitKey", String.class, int.class, int.class,
                    long.class, long.class, boolean.class, int.class);
            cancel = method("cancelPending");
            diagnostics = method("diagnostics");
            // Supply only input readiness: AOSP lacks the dedicated Vivo input nodes.
            // The writer above stays connected to the actual selected privileged service.
            reader = root ? writer : field(controller, "rawKeys").get(controller);
            if (!root) {
                Class<?> callback = loader.loadClass(target.getPackageName() + ".IVolumeKeyShell");
                Binder binder = new Binder();
                Object synthetic = Proxy.newProxyInstance(loader, new Class<?>[]{callback},
                        (obj, method, args) -> method.getName().equals("asBinder") ? binder : null);
                runOnMainSync(() -> {
                    try {
                        // Retire the actual ServiceConnection, including late
                        // onServiceConnected, before installing a synthetic reader.
                        Method disconnect = reader.getClass().getDeclaredMethod("disconnect");
                        disconnect.setAccessible(true);
                        disconnect.invoke(reader);
                        field(reader, "remote").set(reader, synthetic);
                        field(reader, "error").set(reader, null);
                        field(reader, "devices").setInt(reader, 1);
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                });
            } else {
                Field inputGeneration = field(reader, "generation");
                inputGeneration.setLong(reader, inputGeneration.getLong(reader) + 1);
                field(reader, "inputError").set(reader, null);
                field(reader, "devices").setInt(reader, 1);
            }
            // Keep this fixture from requesting accessibility restoration while verifying absence.
            field(controller, "privilegedAccessibilityRequested").setBoolean(controller, true);
            accessibilityChanged = true;
            shell("settings put secure enabled_accessibility_services null");
            shell("settings put secure accessibility_enabled 0");
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            minimum = audio.getStreamMinVolume(3);
            maximum = audio.getStreamMaxVolume(3);
            origin = maximum - 1;
            check("actual privileged writer connected", field(writer, "remote").get(writer) != null);
            check("accessibility disabled during raw hold", shell(
                    "settings get secure accessibility_enabled").trim().equals("0"));
            int timeout = ViewConfiguration.getKeyRepeatTimeout();
            int delay = ViewConfiguration.getKeyRepeatDelay();
            observeFrames();

            if (nativeHoldOnly) {
                nativeHoldProgress();
            } else {
            staggeredNativeWrites();
            rejectedEarlyWrite();
            if (settlingOnly) settlingCancellation(prefs);
            if (!settlingOnly) {
            // No native volume change and no EV_KEY REPEAT: count by Android's repeat timing.
            reset();
            long downAt = press(114, 1);
            Thread.sleep(Math.max(1, timeout - 100));
            check("a short consumed held key stays passive", audio.getStreamVolume(3) == origin);
            long sampleAt = downAt + timeout + delay + delay / 2;
            until(sampleAt);
            // The first repeat tick and Binder acknowledgement can be queued
            // beside our read. Require actual progress while still held, with
            // a bounded 200 ms allowance for that acknowledgement.
            while (audio.getStreamVolume(3) == origin
                    && SystemClock.uptimeMillis() < sampleAt + 200) Thread.sleep(5);
            int during = audio.getStreamVolume(3);
            trace("down/up-only during hold");
            check("down/up-only long hold progresses before release",
                    during <= target(2) && during >= minimum);
            long upAt = press(114, 0);
            int count = count(upAt - downAt, timeout, delay);
            Thread.sleep(1400);
            trace("down/up-only after release");
            check("release settles to the complete hold count", audio.getStreamVolume(3) == target(count));
            checkFrames("down/up-only hold");

            // Simulate the native coarse writes the system makes while a real key is held.
            for (long duration : new long[]{700, 1400, 3000}) {
                reset();
                downAt = press(114, 1);
                set(audio.getStreamVolume(3) - 1);
                long end = downAt + duration;
                long nextNative = downAt + timeout;
                while (SystemClock.uptimeMillis() < end) {
                    until(Math.min(end, nextNative));
                    if (SystemClock.uptimeMillis() >= end) break;
                    set(Math.max(minimum, audio.getStreamVolume(3) - 1));
                    nextNative += delay;
                }
                int beforeUp = audio.getStreamVolume(3);
                trace("native coarse down hold " + duration + " ms before UP");
                upAt = press(114, 0);
                count = count(upAt - downAt, timeout, delay);
                Thread.sleep(1400);
                trace("native coarse down hold " + duration + " ms");
                check("native hold " + duration + " ms does not rebound after release",
                        audio.getStreamVolume(3) <= beforeUp);
                check("native hold " + duration + " ms counts Android repeats",
                        audio.getStreamVolume(3) == target(count));
            }
            // Explicit hardware repeats are authoritative even when their cadence
            // differs from the platform's down/up-only estimate.
            reset();
            press(114, 1);
            for (int n = 0; n < 3; n++) {
                Thread.sleep(80);
                press(114, 2);
            }
            Thread.sleep(timeout + 2L * delay);
            trace("explicit repeats before release");
            check("explicit repeats progress while held", audio.getStreamVolume(3) == target(4));
            press(114, 0);
            Thread.sleep(1400);
            check("explicit repeats are not recounted at release", audio.getStreamVolume(3) == target(4));
            checkFrames("explicit repeat hold");

            // Return along the same off-grid rounding path without resetting history.
            int lowered = audio.getStreamVolume(3);
            press(115, 1);
            for (int n = 0; n < 3; n++) {
                Thread.sleep(80);
                press(115, 2);
            }
            Thread.sleep(250);
            check("reverse hold progresses before release", audio.getStreamVolume(3) > lowered);
            press(115, 0);
            Thread.sleep(1400);
            trace("reverse explicit hold settled");
            check("equal reverse hold returns to original off-grid volume",
                    audio.getStreamVolume(3) == origin);

            reset();
            long stale = SystemClock.uptimeMillis() - 300;
            invoke(raw, node, 114, 1, stale, stale, false, -1);
            Thread.sleep(timeout + 3L * delay);
            press(114, 0);
            Thread.sleep(500);
            check("stale DOWN never arms a held write", audio.getStreamVolume(3) == origin);

            reset();
            press(114, 1);
            press(116, 1);
            Thread.sleep(timeout + 3L * delay);
            press(114, 0);
            press(116, 0);
            Thread.sleep(600);
            check("power chord never starts held-key writes", audio.getStreamVolume(3) == origin);
            reset();
            press(114, 1);
            press(115, 1);
            Thread.sleep(timeout + 3L * delay);
            press(114, 0);
            press(115, 0);
            Thread.sleep(600);
            check("opposite-key chord cancels held writes", audio.getStreamVolume(3) == origin);
            reset();
            press(114, 0);
            Thread.sleep(timeout + 3L * delay);
            check("unmatched release remains passive", audio.getStreamVolume(3) == origin);
            reset();
            press(114, 1);
            Thread.sleep(10100);
            int expired = audio.getStreamVolume(3);
            // A fresh preset after expiry must remain outside the old gesture.
            set(origin);
            Thread.sleep(600);
            press(114, 0);
            Thread.sleep(600);
            check("ten-second expiry retires timer and late release",
                    expired == minimum && audio.getStreamVolume(3) == origin);

            reset();
            press(114, 1);
            field(reader, "devices").setInt(reader, 0);
            Thread.sleep(timeout + 3L * delay);
            check("input readiness loss stops held writes", audio.getStreamVolume(3) == origin);
            field(reader, "devices").setInt(reader, 1);
            invoke(cancel);

            reset();
            press(114, 1);
            prefs.edit().putBoolean("enabled", false).commit();
            Thread.sleep(timeout + 3L * delay);
            check("disabled correction stops held writes", audio.getStreamVolume(3) == origin);
            prefs.edit().putBoolean("enabled", true).commit();
            invoke(cancel);

            reset();
            press(114, 1);
            prefs.edit().putString("operating_mode", "ASSISTANT")
                    .putBoolean("shizuku_enabled", false).commit();
            Thread.sleep(timeout + 3L * delay);
            check("mode change stops old privileged hold", audio.getStreamVolume(3) == origin);
            }
            }
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            try { trace("failure"); } catch (Exception ignored) {}
        } finally {
            try {
                if (controller != null && cancel != null) invoke(cancel);
                if (savedVolume >= 0) set(savedVolume);
                if (reader != null) field(reader, "devices").setInt(reader, 0);
                if (prefs != null && savedPrefs != null) {
                    SharedPreferences.Editor edit = prefs.edit();
                    for (String name : new String[]{"enabled", "media_only", "operating_mode",
                            "shizuku_enabled", "steps", "root_authorized"}) {
                        Object value = savedPrefs.get(name);
                        if (value == null) edit.remove(name);
                        else if (value instanceof Boolean) edit.putBoolean(name, (Boolean) value);
                        else if (value instanceof Integer) edit.putInt(name, (Integer) value);
                        else edit.putString(name, (String) value);
                    }
                    edit.commit();
                }
                if (accessibilityChanged && savedAccessibility != null)
                    // UiAutomation executes argv directly; quote characters
                    // would be stored literally in this colon-delimited value.
                    shell("settings put secure enabled_accessibility_services " + savedAccessibility);
                if (accessibilityChanged && savedMaster != null)
                    shell("settings put secure accessibility_enabled " + savedMaster);
                shell(awake ? "input keyevent 224" : "input keyevent 223");
                if (awake && !locked) shell("wm dismiss-keyguard");
            } catch (Exception exception) { result.putString("restore_error", exception.toString()); }
        }
        result.putString("tests", tests.toString());
        result.putString("timing", traces.toString());
        finish(failed || result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }
    private int target(int count) {
        return Math.max(minimum, origin + (int) Math.round(
                -count * (double) (maximum - minimum) / Math.min(steps, maximum - minimum)));
    }
    private void nativeHoldProgress() throws Exception {
        int savedSteps = steps;
        int savedOrigin = origin;
        SharedPreferences prefs = getTargetContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("volume_step_settings", 0);
        for (int relation = 0; relation < 3; relation++) {
            // AOSP media range is 15. Read the actual range rather than using
            // the phone's range; select fine/native ratios in both directions.
            steps = relation == 0 ? 5 : Math.min(150, maximum - minimum);
            prefs.edit().putInt("steps", steps).commit();
            Thread.sleep(400);
            // A steps change reconnects the selected reader. Replace that
            // connection again before supplying this scenario's raw edges.
            if (!root) {
                Class<?> callback = getTargetContext().getClassLoader()
                        .loadClass(getTargetContext().getPackageName() + ".IVolumeKeyShell");
                Binder binder = new Binder();
                Object synthetic = Proxy.newProxyInstance(callback.getClassLoader(),
                        new Class<?>[]{callback},
                        (obj, method, args) -> method.getName().equals("asBinder") ? binder : null);
                runOnMainSync(() -> {
                    try {
                        Method disconnect = reader.getClass().getDeclaredMethod("disconnect");
                        disconnect.setAccessible(true);
                        disconnect.invoke(reader);
                        field(reader, "remote").set(reader, synthetic);
                        field(reader, "error").set(reader, null);
                        field(reader, "devices").setInt(reader, 1);
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                });
            }
            field(controller, "privilegedAccessibilityRequested").setBoolean(controller, true);
            int nativeStep = relation == 2 ? 3 : 1;
            for (int direction : new int[]{-1, 1}) {
                origin = direction < 0 ? maximum - 1 : minimum + 1;
                reset();
                int code = direction < 0 ? 114 : 115;
                long down = press(code, 1);
                ArrayList<Integer> samples = new ArrayList<>();
                samples.add(audio.getStreamVolume(3));
                long end = down + 700;
                long nextNative = down + 20;
                int timeout = ViewConfiguration.getKeyRepeatTimeout();
                int delay = ViewConfiguration.getKeyRepeatDelay();
                while (SystemClock.uptimeMillis() < end) {
                    if (SystemClock.uptimeMillis() >= nextNative) {
                        // Native audio remains independent of the controller.
                        audio.setStreamVolume(3, Math.max(minimum, Math.min(maximum,
                                audio.getStreamVolume(3) + direction * nativeStep)), 0);
                        nextNative = nextNative < down + timeout
                                ? down + timeout : nextNative + delay;
                    }
                    samples.add(audio.getStreamVolume(3));
                    Thread.sleep(5);
                }
                String heldState = invoke(diagnostics).toString();
                heldState = heldState.substring(heldState.lastIndexOf("raw key "));
                press(code, 0);
                long released = SystemClock.uptimeMillis();
                boolean late = false;
                while (SystemClock.uptimeMillis() < released + 1400) {
                    if (!late && SystemClock.uptimeMillis() >= released + 120) {
                        audio.setStreamVolume(3, Math.max(minimum, Math.min(maximum,
                                audio.getStreamVolume(3) + direction * nativeStep)), 0);
                        late = true;
                    }
                    samples.add(audio.getStreamVolume(3));
                    Thread.sleep(5);
                }
                String name = new String[]{"native smaller", "native equal", "native larger"}[relation]
                        + (direction < 0 ? " DOWN" : " UP");
                boolean monotonic = true;
                for (int i = 1; i < samples.size(); i++)
                    if (direction * (samples.get(i) - samples.get(i - 1)) < 0) monotonic = false;
                traces.put(new JSONObject().put("sequence", name)
                        .put("steps", steps).put("native_step", nativeStep)
                        .put("origin", origin).put("samples", new JSONArray(samples)));
                check(name + " progresses while held", direction
                        * (samples.get(Math.min(100, samples.size() - 1)) - origin) > 0);
                check(name + " preserves native repeats without concurrent absolute writes",
                        heldState.contains("raw held native progress")
                                && !heldState.contains("key write stream="));
                check(name + " never reverses during hold or settling", monotonic);
                check(name + " leaves no pending correction",
                        field(controller, "pendingCorrection").get(controller) == null);
            }
        }
        steps = savedSteps;
        origin = savedOrigin;
    }
    private void staggeredNativeWrites() throws Exception {
        reset();
        for (int n = 0; n < 3; n++) {
            press(114, 1);
            Thread.sleep(20);
            press(114, 0);
        }
        long releasedAt = SystemClock.uptimeMillis();
        int expected = target(3);
        // Real AudioManager writes in separate batches after the final release:
        // the last one also follows the old 300 ms retry and its acknowledgement.
        AtomicReference<Exception> writerError = new AtomicReference<>();
        Thread nativeWriter = new Thread(() -> {
            try {
            for (int n = 0; n < 3; n++) {
                int offset = 150 + n * 200;
                until(releasedAt + offset);
                audio.setStreamVolume(3, Math.max(minimum, expected - n - 1), 0);
                traces.put(new JSONObject().put("sequence", "scheduled native write")
                        .put("offset", offset)
                        .put("actual_offset", SystemClock.uptimeMillis() - releasedAt)
                        .put("actual", audio.getStreamVolume(3)));
            }
            } catch (Exception exception) { writerError.set(exception); }
        }, "native-volume-fixture");
        nativeWriter.start();
        nativeWriter.join(2000);
        if (nativeWriter.isAlive()) throw new IllegalStateException("Native fixture writer did not finish");
        if (writerError.get() != null) throw writerError.get();
        until(releasedAt + 1400);
        trace("staggered native writes settled");
        check("staggered native writes settle to the complete raw count",
                audio.getStreamVolume(3) == expected);
        String settledState = invoke(diagnostics).toString();
        check("settling leaves no unconfirmed correction",
                settledState.contains("pending correction=none")
                        && !settledState.contains("correction state=UNCONFIRMED"));
        // Once the correction has expired, a passive preset stays passive.
        set(origin);
        Thread.sleep(450);
        check("expired settling cannot overwrite a later passive preset",
                audio.getStreamVolume(3) == origin);
    }
    private void settlingCancellation(SharedPreferences prefs) throws Exception {
        for (String condition : new String[]{"disabled", "reader lost", "mode changed"}) {
            reset();
            press(114, 1);
            press(114, 0);
            runOnMainSync(() -> {
                if (condition.equals("disabled")) prefs.edit().putBoolean("enabled", false).commit();
                else if (condition.equals("mode changed"))
                    prefs.edit().putString("operating_mode", "ASSISTANT")
                            .putBoolean("shizuku_enabled", false).commit();
                else try { field(reader, "devices").setInt(reader, 0); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            });
            Thread.sleep(100);
            set(origin - 2);
            Thread.sleep(1200);
            check(condition + " cancels a settling retry", audio.getStreamVolume(3) == origin - 2);
            // Reader replacement belongs to this fixture; mode-change case is
            // last, because switching the real writer disconnects that reader.
            prefs.edit().putBoolean("enabled", true).commit();
            if (!condition.equals("mode changed")) field(reader, "devices").setInt(reader, 1);
        }
    }
    private void rejectedEarlyWrite() throws Exception {
        reset();
        Method result = method("handleBackendResult", String.class,
                field(controller, "pendingCorrection").getType(), int.class, int.class);
        AtomicReference<Boolean> retained = new AtomicReference<>();
        runOnMainSync(() -> {
            try {
                long now = SystemClock.uptimeMillis();
                raw.invoke(controller, node, 114, 1, now, now, false, -1);
                now = SystemClock.uptimeMillis();
                raw.invoke(controller, node, 114, 0, now, now, false, -1);
                Object correction = field(controller, "pendingCorrection").get(controller);
                if (correction == null) throw new IllegalStateException("Released correction required");
                audio.setStreamVolume(3, origin - 2, 0);
                // Synthetic expected-current rejection after its one immediate
                // retry; the selected writer and Android volume remain real.
                result.invoke(controller, "synthetic rejection", correction, -1, 1);
                retained.set(field(controller, "pendingCorrection").get(controller) == correction);
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        });
        check("early rejected write retains the pending settling observation", retained.get());
        Thread.sleep(1400);
        trace("early expected-current rejection settled");
        check("early rejected write still reaches the configured target",
                audio.getStreamVolume(3) == target(1));
    }
    private int count(long duration, int timeout, int delay) {
        return Math.min(100, 1 + (duration < timeout ? 0 : 1 + (int)((duration - timeout) / delay)));
    }
    private void reset() throws Exception {
        invoke(cancel); set(origin); Thread.sleep(400); invoke(cancel);
        runOnMainSync(() -> {
            displayedFrames.clear(); systemFrames.clear(); frameStates.clear(); rowBounds.clear();
        });
    }
    private void observeFrames() throws Exception {
        Object overlay = field(controller, "overlay").get(controller);
        View panel = (View) field(overlay, "panel").get(overlay);
        View row = (View) field(overlay, "systemView").get(overlay);
        Field displayed = field(overlay, "displayedVolume");
        Field system = field(overlay, "systemVolume");
        Field state = field(overlay, "correctionState");
        android.view.ViewTreeObserver.OnPreDrawListener observer = () -> {
            try {
                displayedFrames.add(displayed.getInt(overlay));
                systemFrames.add(system.getInt(overlay));
                frameStates.add(state.get(overlay).toString());
                int[] xy = new int[2];
                row.getLocationOnScreen(xy);
                rowBounds.add(xy[0] + ":" + xy[1] + ":" + row.getWidth() + ":" + row.getHeight());
            } catch (Exception exception) { throw new IllegalStateException(exception); }
            return true;
        };
        runOnMainSync(() -> panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.getViewTreeObserver().addOnPreDrawListener(observer);
            }
            @Override public void onViewDetachedFromWindow(View view) {
                if (view.getViewTreeObserver().isAlive())
                    view.getViewTreeObserver().removeOnPreDrawListener(observer);
            }
        }));
    }
    private void checkFrames(String sequence) throws Exception {
        AtomicReference<Boolean> readback = new AtomicReference<>(), targets = new AtomicReference<>(),
                stable = new AtomicReference<>(), confirmed = new AtomicReference<>();
        runOnMainSync(() -> {
            readback.set(!displayedFrames.isEmpty() && displayedFrames.equals(systemFrames));
            targets.set(displayedFrames.stream().allMatch(value -> {
                for (int n = 2; n <= 100; n++) if (value == target(n)) return true;
                return false;
            }));
            stable.set(!rowBounds.isEmpty() && rowBounds.stream().allMatch(rowBounds.get(0)::equals));
            // SETTLING also holds an actual acknowledged readback while the
            // late-native-write guard runs; it has no pending status text.
            confirmed.set(frameStates.stream().allMatch(value ->
                    value.equals("CONFIRMED") || value.equals("SETTLING")));
            try {
                traces.put(new JSONObject().put("sequence", sequence + " frames")
                        .put("displayed", new JSONArray(displayedFrames))
                        .put("system", new JSONArray(systemFrames))
                        .put("states", new JSONArray(frameStates)));
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        });
        check(sequence + " renders actual confirmed system row", readback.get());
        check(sequence + " renders only cumulative targets", targets.get());
        check(sequence + " keeps system row fixed", stable.get());
        check(sequence + " never renders a pending frame", confirmed.get());
    }
    private void set(int value) { runOnMainSync(() -> audio.setStreamVolume(3, value, 0)); }
    private long press(int code, int value) throws Exception {
        if (code != 116) {
            Method ready = reader.getClass().getDeclaredMethod(root ? "inputReady" : "ready");
            ready.setAccessible(true);
            if (!(boolean) ready.invoke(reader))
                throw new IllegalStateException("Synthetic input readiness lost before edge");
        }
        AtomicReference<Long> time = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try {
                // These edges deliberately model fresh callbacks. Queue delay
                // in the instrumentation thread must not turn one into a stale
                // edge; the explicit stale-DOWN case above tests that gate.
                long now = SystemClock.uptimeMillis();
                time.set(now);
                raw.invoke(controller, node, code, value, now, now, false, -1);
            } catch (Exception exception) { failure.set(exception); }
        });
        if (failure.get() != null) throw failure.get();
        return time.get();
    }
    private void until(long at) throws Exception {
        long remaining = at - SystemClock.uptimeMillis();
        if (remaining > 0) Thread.sleep(remaining);
    }
    private void trace(String sequence) throws Exception {
        traces.put(new JSONObject().put("sequence", sequence).put("actual", audio.getStreamVolume(3))
                .put("service", invoke(diagnostics)));
    }
    private Field field(Object obj, String name) throws Exception {
        Field field = obj.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private Method method(String name, Class<?>... args) throws Exception {
        Method method = controller.getClass().getDeclaredMethod(name, args);
        method.setAccessible(true); return method;
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
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private void check(String name, boolean pass) throws Exception {
        tests.put(new JSONObject().put("test", name).put("pass", pass));
        failed |= !pass;
    }
}
