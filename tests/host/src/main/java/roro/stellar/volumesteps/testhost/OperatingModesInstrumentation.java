package roro.stellar.volumesteps.testhost;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Disposable rootable emulator: real su/Binder/AudioService and mode ownership. */
public final class OperatingModesInstrumentation extends Instrumentation {
    private final JSONArray tests = new JSONArray();
    private ClassLoader loader;
    private Context target;
    private SharedPreferences prefs;
    private AudioManager audio;
    private Object controller;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        String savedMode = null;
        boolean savedLegacy = false, savedEnabled = false;
        int savedVolume = -1;
        try {
            target = getTargetContext();
            loader = target.getClassLoader();
            loader.loadClass("com.topjohnwu.superuser.Shell")
                    .getField("enableVerboseLogging").setBoolean(null, true);
            result.putString("su_path", System.getenv("PATH"));
            prefs = target.createDeviceProtectedStorageContext().getSharedPreferences("volume_step_settings", 0);
            savedMode = prefs.getString("operating_mode", null);
            savedLegacy = prefs.getBoolean("shizuku_enabled", false);
            savedEnabled = prefs.getBoolean("enabled", false);
            audio = target.getSystemService(AudioManager.class);
            savedVolume = audio.getStreamVolume(3);
            runOnMainSync(() -> prefs.edit().putBoolean("enabled", true)
                    .putString("operating_mode", "ROOT").putBoolean("shizuku_enabled", false)
                    .putBoolean("permission_guide_shown_v1", true).commit());
            Class<?> keeper = loader.loadClass(target.getPackageName() + ".VolumeKeeperService");
            Method get = keeper.getDeclaredMethod("controller"); get.setAccessible(true);
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            for (int n = 0; n < 100 && controller == null; n++) {
                controller = get.invoke(null); Thread.sleep(50);
            }
            if (controller == null) throw new IllegalStateException("Controller did not start");
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> report = new AtomicReference<>();
            Consumer<String> callback = text -> { report.set(text); done.countDown(); };
            invoke(controller, "setupRoot", new Class<?>[]{Consumer.class}, callback);
            check("Root setup completes", done.await(35, TimeUnit.SECONDS));
            result.putString("setup_report", report.get());
            Object root = field(controller, "root");
            check("actual su root Binder is connected", (boolean) call(root, "ready"));
            check("Shizuku volume service stays disconnected", field(field(controller, "shizuku"), "remote") == null);
            check("assistant service stays disconnected", field(field(controller, "compatibility"), "remote") == null);
            check("notification permission readback", target.checkSelfPermission(
                    "android.permission.POST_NOTIFICATIONS") == 0);
            check("overlay permission readback", Settings.canDrawOverlays(target));
            check("battery exemption readback", target.getSystemService(android.os.PowerManager.class)
                    .isIgnoringBatteryOptimizations(target.getPackageName()));
            String accessibility = Settings.Secure.getString(target.getContentResolver(), "enabled_accessibility_services");
            check("own accessibility setting readback", accessibility != null
                    && accessibility.contains("FineVolumeAccessibilityService"));
            Class<?> setup = loader.loadClass(target.getPackageName() + ".PrivilegedSetup");
            Method merge = setup.getDeclaredMethod("mergeAccessibility", String.class); merge.setAccessible(true);
            String other = "org.example.reader/.Reader:org.example.switch/.Switch";
            String merged = (String) merge.invoke(null, other);
            check("permission merge preserves both other services", merged.startsWith(other + ":"));
            check("permission merge is idempotent", merged.equals(merge.invoke(null, merged)));
            Object remote = field(root, "remote");
            Method adjust = loader.loadClass(target.getPackageName() + ".IPrivilegedVolume")
                    .getMethod("adjust", int.class, int.class, int.class, long.class);
            int min = audio.getStreamMinVolume(3), max = audio.getStreamMaxVolume(3);
            int baseline = Math.max(min, Math.min(max, min + (max - min) / 3));
            int nativeBefore = audio.getStreamVolume(3);
            int readback = (int) adjust.invoke(remote, 3, baseline, nativeBefore, SystemClock.uptimeMillis() + 500);
            check("root writes actual system volume", readback == baseline && audio.getStreamVolume(3) == baseline);
            int alternative = baseline < max ? baseline + 1 : baseline - 1;
            int stale = (int) adjust.invoke(remote, 3, alternative, baseline + 2, SystemClock.uptimeMillis() + 500);
            check("root stale request writes nothing", stale == -1 && audio.getStreamVolume(3) == baseline);
            int expired = (int) adjust.invoke(remote, 3, alternative, baseline, SystemClock.uptimeMillis() - 1);
            check("root expired request writes nothing", expired == -1 && audio.getStreamVolume(3) == baseline);
            boolean rejected = false;
            try { adjust.invoke(remote, 7, 0, 0, SystemClock.uptimeMillis() + 500); }
            catch (ReflectiveOperationException exception) { rejected = true; }
            check("root refuses internal stream", rejected);
            int clampedMin = (int) adjust.invoke(remote, 3, Integer.MIN_VALUE, baseline,
                    SystemClock.uptimeMillis() + 500);
            check("range refresh clamps an extreme request to actual minimum",
                    clampedMin == min && audio.getStreamVolume(3) == min);
            int clampedMax = (int) adjust.invoke(remote, 3, Integer.MAX_VALUE, min,
                    SystemClock.uptimeMillis() + 500);
            check("range refresh clamps an extreme request to actual maximum",
                    clampedMax == max && audio.getStreamVolume(3) == max);
            int restored = (int) adjust.invoke(remote, 3, baseline, max,
                    SystemClock.uptimeMillis() + 500);
            check("cached-range write returns to exact baseline",
                    restored == baseline && audio.getStreamVolume(3) == baseline);

            Class<?> helper = loader.loadClass(target.getPackageName() + ".HelperApkProvider");
            Method prepare = helper.getDeclaredMethod("prepare", Context.class);
            prepare.setAccessible(true);
            java.io.File bundled = (java.io.File) prepare.invoke(null, target);
            android.content.pm.PackageInfo archive = target.getPackageManager().getPackageArchiveInfo(
                    bundled.getPath(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            check("bundled helper retains independent package identity", archive != null
                    && "com.vivo.finevolumesteps.helper".equals(archive.packageName));
            try (var asset = target.getAssets().open("volume-helper.apk");
                 var cached = new java.io.FileInputStream(bundled)) {
                check("installer receives exact bundled APK bytes",
                        java.util.Arrays.equals(asset.readAllBytes(), cached.readAllBytes()));
            }
            android.net.Uri uri = android.net.Uri.parse(
                    "content://roro.stellar.volumesteps.helper_apk/volume-helper.apk");
            try (var descriptor = target.getContentResolver().openFileDescriptor(uri, "r")) {
                check("installer URI can read verified APK", descriptor != null
                        && descriptor.getStatSize() == bundled.length());
            }
            boolean readOnly = false;
            try (var ignored = target.getContentResolver().openFileDescriptor(uri, "w")) { }
            catch (java.io.FileNotFoundException exception) { readOnly = true; }
            check("installer URI refuses writes", readOnly);
            android.content.pm.ProviderInfo provider = target.getPackageManager().resolveContentProvider(
                    "roro.stellar.volumesteps.helper_apk", 0);
            check("APK provider requires explicit URI access grant", provider != null
                    && !provider.exported && provider.grantUriPermissions);

            invoke(controller, "beginKey", new Class<?>[]{int.class, int.class, long.class,
                    boolean.class, boolean.class}, -1, 0, SystemClock.uptimeMillis(), true, false);
            Object held = field(controller, "gesture");
            invoke(controller, "finishRawRelease", new Class<?>[]{held.getClass()}, held);
            check("old release timer cannot complete a newer held press",
                    field(held, "releaseSnapshot") == null
                    && !(boolean) field(held, "released"));
            invoke(controller, "cancelPending", new Class<?>[]{});

            Activity activity = startActivitySync(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            int rootId = id("mode_root"), assistantId = id("mode_assistant"), shizukuId = id("mode_shizuku");
            check("three modes visible", activity.findViewById(rootId) != null
                    && activity.findViewById(assistantId) != null && activity.findViewById(shizukuId) != null);
            AtomicReference<Integer> switchCount = new AtomicReference<>();
            runOnMainSync(() -> switchCount.set(countSwitches(activity.getWindow().getDecorView())));
            check("home has one master switch", switchCount.get() == 1);
            runOnMainSync(() -> activity.findViewById(assistantId).performClick());
            Thread.sleep(600);
            check("assistant selection persists", "ASSISTANT".equals(prefs.getString("operating_mode", "")));
            check("leaving root disconnects root Binder", field(root, "remote") == null);
            check("leaving root makes root writes unavailable", !(boolean) call(root, "ready"));
            check("assistant setup action appears", activity.findViewById(id("mode_setup")) != null);
            runOnMainSync(() -> activity.findViewById(shizukuId).performClick());
            Thread.sleep(300);
            check("Shizuku selection persists with legacy preference", prefs.getBoolean("shizuku_enabled", false)
                    && "SHIZUKU".equals(prefs.getString("operating_mode", "")));
            check("Shizuku setup action appears", activity.findViewById(id("shizuku_setup")) != null);
            runOnMainSync(() -> activity.finish());
        } catch (Throwable exception) { result.putString("error", exception.toString()); }
        finally {
            try {
                if (savedVolume >= 0) audio.setStreamVolume(3, savedVolume, 0);
                if (prefs != null) {
                    String mode = savedMode;
                    boolean legacy = savedLegacy, enabled = savedEnabled;
                    runOnMainSync(() -> {
                        SharedPreferences.Editor edit = prefs.edit().putBoolean("enabled", enabled)
                                .putBoolean("shizuku_enabled", legacy);
                        if (mode == null) edit.remove("operating_mode"); else edit.putString("operating_mode", mode);
                        edit.commit();
                    });
                }
            } catch (Throwable exception) { result.putString("restore_error", exception.toString()); }
        }
        result.putString("tests", tests.toString());
        finish(0, result);
    }
    private int id(String name) { return target.getResources().getIdentifier(name, "id", target.getPackageName()); }
    private int countSwitches(View view) {
        int result = view instanceof Switch ? 1 : 0;
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) result += countSwitches(group.getChildAt(i));
        return result;
    }
    private Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private Object call(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
    private void invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { method.invoke(object, args); } catch (Throwable e) { error.set(e); } });
        if (error.get() != null) throw new IllegalStateException(error.get());
    }
    private void check(String name, boolean pass) throws Exception {
        tests.put(new JSONObject().put("name", name).put("pass", pass));
    }
}
