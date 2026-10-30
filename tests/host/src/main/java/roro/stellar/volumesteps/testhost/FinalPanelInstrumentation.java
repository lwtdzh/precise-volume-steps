package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Emulator-only delayed acknowledgements; actual system audio and frame values. */
public final class FinalPanelInstrumentation extends Instrumentation {
    private Object controller;
    private Object overlay;
    private Method onKey;
    private Method diagnostics;
    private Method cancel;
    private View panel;
    private Field displayed;
    private Field systemReadback;
    private Field state;
    private UiAutomation automation;
    private final ArrayList<Integer> frames = new ArrayList<>();
    private final ArrayList<Integer> systemFrames = new ArrayList<>();
    private final ArrayList<String> systemBounds = new ArrayList<>();
    private final ArrayList<String> states = new ArrayList<>();
    private final JSONArray tests = new JSONArray();
    private String packageName;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences settings = null;
        boolean shizuku = false;
        String operatingMode = null;
        int saved = -1;
        String appOp = null;
        try {
            if (!Build.FINGERPRINT.contains("generic") && !Build.MODEL.contains("SDK")
                    && !Build.MODEL.contains("sdk")) {
                throw new IllegalStateException("Only disposable AOSP emulators are supported");
            }
            Context target = getTargetContext();
            packageName = target.getPackageName();
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AudioManager audio = target.getSystemService(AudioManager.class);
            saved = audio.getStreamVolume(3);
            settings = target.createDeviceProtectedStorageContext()
                    .getSharedPreferences("volume_step_settings", 0);
            shizuku = settings.getBoolean("shizuku_enabled", false);
            operatingMode = settings.getString("operating_mode", null);
            SharedPreferences preferences = settings;
            runOnMainSync(() -> {
                preferences.edit().putString("operating_mode", "ASSISTANT")
                        .putBoolean("shizuku_enabled", false).commit();
                target.startForegroundService(new Intent().setClassName(
                        packageName, packageName + ".VolumeKeeperService"));
            });
            Method get = target.getClassLoader().loadClass(packageName + ".VolumeKeeperService")
                    .getDeclaredMethod("controller");
            get.setAccessible(true);
            for (int n = 0; n < 50 && controller == null; n++) {
                controller = get.invoke(null);
                if (controller == null) Thread.sleep(100);
            }
            if (controller == null) throw new IllegalStateException("Controller unavailable");
            onKey = method(controller, "onKey", KeyEvent.class);
            diagnostics = method(controller, "diagnostics");
            cancel = method(controller, "cancelPending");
            overlay = field(controller, "overlay").get(controller);
            panel = (View) field(overlay, "panel").get(overlay);
            displayed = field(overlay, "displayedVolume");
            systemReadback = field(overlay, "systemVolume");
            View systemView = (View) field(overlay, "systemView").get(overlay);
            state = field(overlay, "correctionState");
            runOnMainSync(() -> panel.getViewTreeObserver().addOnPreDrawListener(() -> {
                if (panel.isAttachedToWindow()) {
                    try {
                        frames.add(displayed.getInt(overlay));
                        systemFrames.add(systemReadback.getInt(overlay));
                        int[] xy = new int[2];
                        systemView.getLocationOnScreen(xy);
                        systemBounds.add(xy[0] + ":" + xy[1] + ":"
                                + systemView.getWidth() + ":" + systemView.getHeight());
                        states.add(state.get(overlay).toString());
                    } catch (Exception exception) { throw new RuntimeException(exception); }
                }
                return true;
            }));
            invoke(cancel);
            Matcher mode = Pattern.compile("AUDIO_MEDIA_VOLUME: (\\w+)").matcher(
                    shell("appops get " + packageName + " AUDIO_MEDIA_VOLUME"));
            appOp = mode.find() ? mode.group(1) : "default";
            shell("appops set " + packageName + " AUDIO_MEDIA_VOLUME ignore");
            int max = audio.getStreamMaxVolume(3);
            int steps = settings.getInt("steps", 30);
            int baseline = max / 3;
            setVolume(baseline);
            Thread.sleep(300);
            int first = next(baseline, max, steps);
            int second = target(baseline, max, steps, 2);
            pressUp();
            setVolume(Math.min(max, baseline + 10));
            waitPending(first);
            Thread.sleep(180);
            check("first panel hidden while correction is pending",
                    report().contains("overlay=attached=false") && frames.isEmpty());
            setVolume(first);
            waitDisplayed(first, "CONFIRMED");
            check("first rendered frame contains confirmed final volume",
                    !frames.isEmpty() && frames.get(0) == first);
            check("first system row reads final Android volume",
                    !systemFrames.isEmpty() && systemFrames.get(0) == first);
            pressUp();
            setVolume(Math.min(max, first + 10));
            waitPending(second);
            Thread.sleep(180);
            check("visible panel holds previous final value during next correction",
                    report().contains("displayed volume=" + first)
                            && frames.stream().allMatch(value -> value == first));
            check("system row skips native intermediate value during correction",
                    systemFrames.stream().allMatch(value -> value == first));
            setVolume(second);
            waitDisplayed(second, "CONFIRMED");
            check("second correction renders only the new final value",
                    frames.stream().allMatch(value -> value == first || value == second));
            check("system row renders only actual final readbacks",
                    systemFrames.stream().allMatch(value -> value == first || value == second));
            int failedNative = Math.min(max, second + 10);
            pressUp();
            setVolume(failedNative);
            waitPending(target(baseline, max, steps, 3));
            Thread.sleep(180);
            check("rejected write holds last final value until outcome",
                    report().contains("displayed volume=" + second));
            check("system row holds last readback until failed write settles",
                    systemFrames.stream().allMatch(value -> value == first || value == second));
            waitDisplayed(failedNative, "UNCONFIRMED");
            check("failed outcome displays actual readback once",
                    frames.stream().allMatch(value -> value == first
                            || value == second || value == failedNative));
            check("failed system readback is actual rather than requested target",
                    systemFrames.contains(failedNative)
                            && systemFrames.stream().allMatch(value -> value == first
                                    || value == second || value == failedNative));
            check("system text bounds never move during volume updates",
                    !systemBounds.isEmpty() && systemBounds.stream()
                            .allMatch(systemBounds.get(0)::equals));
            check("no rendered frame contains pending correction text",
                    states.stream().noneMatch("PENDING"::equals));
            result.putString("frames", new JSONArray(frames).toString());
            result.putString("system_frames", new JSONArray(systemFrames).toString());
            result.putString("states", new JSONArray(states).toString());
            result.putString("input", "synthetic key callbacks; actual audio readback; delayed shell acknowledgements");
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            try { result.putString("diagnostics", report()); } catch (Exception ignored) { }
        } finally {
            try {
                if (cancel != null) invoke(cancel);
                if (appOp != null) shell("appops set " + packageName + " AUDIO_MEDIA_VOLUME " + appOp);
                if (saved >= 0) setVolume(saved);
                if (settings != null) {
                    SharedPreferences preferences = settings;
                    boolean enabled = shizuku;
                    String savedMode = operatingMode;
                    runOnMainSync(() -> {
                        SharedPreferences.Editor edit = preferences.edit().putBoolean("shizuku_enabled", enabled);
                        if (savedMode == null) edit.remove("operating_mode");
                        else edit.putString("operating_mode", savedMode);
                        edit.commit();
                    });
                }
            } catch (Exception exception) {
                result.putString("restore_error", exception.toString());
            }
        }
        result.putString("tests", tests.toString());
        finish(result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }

    private void waitPending(int target) throws Exception {
        long end = SystemClock.uptimeMillis() + 800;
        while (!report().contains("pending correction=3:" + target)) {
            if (SystemClock.uptimeMillis() >= end) throw new IllegalStateException(
                    "Pending correction not observed: " + target + "\n" + report());
            Thread.sleep(10);
        }
    }
    private void waitDisplayed(int value, String correction) throws Exception {
        long end = SystemClock.uptimeMillis() + 1600;
        while (true) {
            String data = report();
            if (data.contains("displayed volume=" + value)
                    && data.contains("correction state=" + correction)
                    && data.contains("overlay=attached=true")) {
                Thread.sleep(60);
                return;
            }
            if (SystemClock.uptimeMillis() >= end) throw new IllegalStateException(
                    "Final display not observed: " + value + "\n" + data);
            Thread.sleep(10);
        }
    }
    private void pressUp() throws Exception {
        long now = SystemClock.uptimeMillis();
        invoke(onKey, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0));
        invoke(onKey, new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0));
    }
    private void setVolume(int value) throws Exception {
        shell("cmd media_session volume --stream 3 --set " + value);
    }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private String report() throws Exception { return (String) invoke(diagnostics); }
    private static Field field(Object owner, String name) throws Exception {
        Field result = owner.getClass().getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    private static Method method(Object owner, String name, Class<?>... args) throws Exception {
        Method result = owner.getClass().getDeclaredMethod(name, args);
        result.setAccessible(true);
        return result;
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
    private static int next(int value, int max, int steps) {
        return target(value, max, steps, 1);
    }
    private static int target(int origin, int max, int steps, int position) {
        return Math.max(0, Math.min(max, origin + (int) Math.round(
                (double) position * max / Math.min(max, steps))));
    }
}
