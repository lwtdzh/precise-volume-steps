package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** No main Activity or connect() call. The runner starts/grants the real server. */
public final class ShizukuReconnectInstrumentation extends Instrumentation {
    private static final String MAIN = "roro.stellar.volumesteps";
    private final List<JSONObject> tests = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean enabled, legacy;
    private String mode, scenario;
    private Object controller;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (!"goldfish".equals(android.os.Build.HARDWARE)
                && !"ranchu".equals(android.os.Build.HARDWARE)) {
            Bundle result = new Bundle();
            result.putString("error", "Use a disposable emulator");
            finish(1, result);
            return;
        }
        scenario = arguments.getString("scenario", "silent");
        prefs = getTargetContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("volume_step_settings", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean("enabled", false);
        legacy = prefs.getBoolean("shizuku_enabled", false);
        mode = prefs.getString("operating_mode", null);
        prefs.edit().putBoolean("enabled", false).putString("operating_mode", "SHIZUKU")
                .putBoolean("shizuku_enabled", true).commit();
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean passed = false;
        Object listener = null;
        Class<?> shizuku = null, listenerType = null;
        try {
            shizuku = Class.forName("rikka.shizuku.Shizuku", true,
                    getTargetContext().getClassLoader());
            Method ping = shizuku.getMethod("pingBinder");
            Method permission = shizuku.getMethod("checkSelfPermission");
            if (!"delayed".equals(scenario)) {
                if (!(boolean) ping.invoke(null)) {
                    Class<?> api = shizuku;
                    android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                    Bundle data = new Bundle();
                    data.putBinder("binder", new android.os.Binder() {
                        @Override protected boolean onTransact(int code, android.os.Parcel request,
                                                               android.os.Parcel reply, int flags) {
                            android.os.IBinder binder = request.readStrongBinder();
                            main.post(() -> {
                                try {
                                    api.getMethod("onBinderReceived", android.os.IBinder.class, String.class)
                                            .invoke(null, binder, MAIN);
                                } catch (Exception exception) { throw new RuntimeException(exception); }
                            });
                            return true;
                        }
                    });
                    getTargetContext().sendBroadcast(new Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                            .setComponent(new android.content.ComponentName("moe.shizuku.privileged.api",
                                    "moe.shizuku.manager.receiver.ShizukuReceiver"))
                            .putExtra("data", data));
                }
                long until = SystemClock.uptimeMillis() + 5000;
                while (!(boolean) ping.invoke(null) && SystemClock.uptimeMillis() < until)
                    Thread.sleep(100);
            }
            boolean running = (boolean) ping.invoke(null);
            require("fixture starts with requested real server state",
                    running != "delayed".equals(scenario));
            if ("silent".equals(scenario))
                require("real permission is initially denied", (int) permission.invoke(null) == -1);
            AtomicInteger callbacks = new AtomicInteger();
            listenerType = Class.forName("rikka.shizuku.Shizuku$OnRequestPermissionResultListener",
                    true, getTargetContext().getClassLoader());
            listener = Proxy.newProxyInstance(listenerType.getClassLoader(),
                    new Class<?>[]{listenerType}, (proxy, method, args) -> {
                        if ("onRequestPermissionResult".equals(method.getName())) callbacks.incrementAndGet();
                        if ("equals".equals(method.getName())) return proxy == args[0];
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        return null;
                    });
            shizuku.getMethod("addRequestPermissionResultListener", listenerType).invoke(null, listener);
            int before = getTargetContext().getSystemService(android.media.AudioManager.class)
                    .getStreamVolume(3);
            runOnMainSync(() -> {
                for (android.app.ActivityManager.AppTask task : getTargetContext()
                        .getSystemService(android.app.ActivityManager.class).getAppTasks())
                    task.finishAndRemoveTask();
                prefs.edit().putBoolean("enabled", true).commit();
                getTargetContext().startForegroundService(
                        new Intent().setClassName(MAIN, MAIN + ".VolumeKeeperService"));
            });
            Method getController = Class.forName(MAIN + ".VolumeKeeperService", true,
                    getTargetContext().getClassLoader()).getDeclaredMethod("controller");
            getController.setAccessible(true);
            for (int i = 0; i < 50 && controller == null; i++) {
                controller = getController.invoke(null);
                Thread.sleep(100);
            }
            require("background keeper starts without Activity", controller != null);
            if ("missed".equals(scenario) || "stalled".equals(scenario)) {
                require("initial real user services connect", awaitConnected(15000));
                if ("missed".equals(scenario)) {
                    Class<?> api = shizuku;
                    runOnMainSync(() -> {
                        try {
                            api.getMethod("onBinderReceived", android.os.IBinder.class, String.class)
                                    .invoke(null, null, MAIN);
                        } catch (Exception exception) { throw new RuntimeException(exception); }
                    });
                } else {
                    runOnMainSync(() -> {
                        try {
                            for (String name : new String[]{"shizuku", "rawKeys"}) {
                                Object bridge = field(controller, name);
                                Method disconnect = bridge.getClass().getDeclaredMethod("disconnect");
                                disconnect.setAccessible(true);
                                disconnect.invoke(bridge);
                                Method newConnection = bridge.getClass().getDeclaredMethod("newConnection");
                                newConnection.setAccessible(true);
                                Field connection = bridge.getClass().getDeclaredField("connection");
                                connection.setAccessible(true);
                                connection.set(bridge, newConnection.invoke(bridge));
                                Field binding = bridge.getClass().getDeclaredField("binding");
                                binding.setAccessible(true);
                                binding.setBoolean(bridge, true);
                                ((android.os.Handler) field(bridge, "handler")).postDelayed(
                                        (Runnable) field(bridge, "bindingTimeout"), 10000);
                            }
                        } catch (Exception exception) { throw new RuntimeException(exception); }
                    });
                }
            } else {
                require("both services wait before startup or grant", !connected());
            }
            Bundle phase = new Bundle();
            phase.putString("phase", "waiting-" + scenario);
            sendStatus(0, phase);
            long begin = SystemClock.uptimeMillis();
            require("both real Binders recover automatically", awaitConnected(30000));
            result.putLong("recovery_ms", SystemClock.uptimeMillis() - begin);
            require("permission grant needed no request callback", callbacks.get() == 0);
            require("selected mode remains Shizuku",
                    "SHIZUKU".equals(prefs.getString("operating_mode", "")));
            require("reconnection leaves volume unchanged", before == getTargetContext()
                    .getSystemService(android.media.AudioManager.class).getStreamVolume(3));
            require("no app Activity launched", getTargetContext().getSystemService(
                    android.app.ActivityManager.class).getAppTasks().stream()
                    .noneMatch(task -> task.getTaskInfo().topActivity != null
                            && MAIN.equals(task.getTaskInfo().topActivity.getPackageName())));
            Object volume = field(controller, "shizuku"), keys = field(controller, "rawKeys");
            Object oldVolume = field(volume, "connection"), oldKeys = field(keys, "connection");
            long[] inspection = {0};
            runOnMainSync(() -> {
                try {
                    inspection[0] = (long) field(controller, "lastShizukuInspection");
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            Thread.sleep(4300);
            boolean[] idle = {false};
            runOnMainSync(() -> {
                try {
                    idle[0] = inspection[0] == (long) field(controller, "lastShizukuInspection")
                            && oldVolume == field(volume, "connection")
                            && oldKeys == field(keys, "connection");
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            require("connected services stop periodic Shizuku inspection", idle[0] && connected());
            // A stale service callback must not replace a connection after mode exit.
            runOnMainSync(() -> prefs.edit().putString("operating_mode", "ASSISTANT")
                    .putBoolean("shizuku_enabled", false).commit());
            Thread.sleep(500);
            runOnMainSync(() -> {
                ((android.content.ServiceConnection) oldVolume).onServiceConnected(
                        null, new android.os.Binder());
                ((android.content.ServiceConnection) oldKeys).onServiceConnected(
                        null, new android.os.Binder());
            });
            require("mode exit rejects stale service delivery", field(volume, "remote") == null
                    && field(keys, "remote") == null);
            Thread.sleep(2300);
            require("background inspection respects assistant mode",
                    field(volume, "connection") == null && field(keys, "connection") == null);
            passed = true;
        } catch (Exception exception) {
            result.putString("error", exception.toString());
        } finally {
            try {
                if (listener != null) shizuku.getMethod("removeRequestPermissionResultListener",
                        listenerType).invoke(null, listener);
                runOnMainSync(() -> {
                    getTargetContext().stopService(new Intent().setClassName(
                            MAIN, MAIN + ".VolumeKeeperService"));
                    SharedPreferences.Editor restore = prefs.edit().putBoolean("enabled", enabled)
                            .putBoolean("shizuku_enabled", legacy);
                    if (mode == null) restore.remove("operating_mode");
                    else restore.putString("operating_mode", mode);
                    restore.commit();
                });
                if (enabled) getTargetContext().startForegroundService(
                        new Intent().setClassName(MAIN, MAIN + ".VolumeKeeperService"));
            } catch (Exception exception) {
                passed = false;
                result.putString("restore_error", exception.toString());
            }
            result.putString("tests", new JSONArray(tests).toString());
            finish(passed ? 0 : 1, result);
        }
    }

    private boolean awaitConnected(long timeout) throws Exception {
        long until = SystemClock.uptimeMillis() + timeout;
        do {
            if (connected()) return true;
            Thread.sleep(100);
        } while (SystemClock.uptimeMillis() < until);
        return false;
    }

    private boolean connected() {
        boolean[] result = {false};
        runOnMainSync(() -> {
            try {
                Object volume = field(field(controller, "shizuku"), "remote");
                Object keys = field(field(controller, "rawKeys"), "remote");
                result[0] = volume instanceof android.os.IInterface
                        && ((android.os.IInterface) volume).asBinder().isBinderAlive()
                        && keys instanceof android.os.IInterface
                        && ((android.os.IInterface) keys).asBinder().isBinderAlive();
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        return result[0];
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private void require(String name, boolean condition) throws Exception {
        tests.add(new JSONObject().put("test", name).put("pass", condition));
        if (!condition) throw new IllegalStateException(name);
    }
}
