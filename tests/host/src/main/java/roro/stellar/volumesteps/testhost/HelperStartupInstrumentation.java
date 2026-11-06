package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.KeyguardManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Emulator only: real helper monitor/power lifecycle, strictly parsed synthetic dumps. */
public final class HelperStartupInstrumentation extends Instrumentation {
    private final JSONArray tests = new JSONArray();
    private Object monitor;
    private Method screen, close, register, unregister;
    private final AtomicInteger samples = new AtomicInteger();
    private final AtomicInteger downs = new AtomicInteger();
    private final AtomicInteger ups = new AtomicInteger();
    private final AtomicReference<String> state = new AtomicReference<>("<none>");
    private Object listener;
    private boolean failed;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean awake = true, locked = false;
        try {
            if (!android.os.Build.MODEL.toLowerCase().contains("sdk"))
                throw new IllegalStateException("Disposable emulator only");
            awake = getTargetContext().getSystemService(PowerManager.class).isInteractive();
            locked = getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked();
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    .adoptShellPermissionIdentity("android.permission.DUMP");
            ClassLoader loader = getTargetContext().getClassLoader();
            Class<?> type = loader.loadClass(
                    "roro.stellar.volumesteps.compatibility.InputDumpMonitor");
            Class<?> callback = loader.loadClass(
                    "roro.stellar.volumesteps.compatibility.ICompatibilityKeyListener");
            Constructor<?> constructor = type.getDeclaredConstructor(
                    android.content.Context.class, Supplier.class);
            constructor.setAccessible(true);
            monitor = constructor.newInstance(getTargetContext(), (Supplier<String>) () -> {
                samples.incrementAndGet();
                return dump(state.get());
            });
            register = method(type, "register", callback);
            unregister = method(type, "unregister", callback);
            screen = method(type, "screenChanged", boolean.class);
            close = method(type, "close");
            Binder binder = new Binder();
            listener = Proxy.newProxyInstance(loader, new Class<?>[]{callback}, (obj, method, args) -> {
                if (method.getName().equals("asBinder")) return binder;
                if (method.getName().equals("onKey") && (int) args[0] == 114) {
                    if ((int) args[1] == 1) downs.incrementAndGet();
                    if ((int) args[1] == 0) ups.incrementAndGet();
                }
                return null;
            });
            check("registered with actual DUMP permission", (boolean) register.invoke(monitor, listener));
            Thread.sleep(300);
            int beforeSleep = samples.get();
            check("idle unlocked helper retains a key baseline", beforeSleep >= 2);
            // Key begins as SCREEN_OFF is processed. A cold first sample must not count as DOWN.
            shell("input keyevent 223");
            state.set("KEY_VOLUMEDOWN");
            screen.invoke(monitor, false);
            Thread.sleep(120);
            state.set("<none>");
            Thread.sleep(120);
            check("immediate screen-off first press has matching down and up",
                    downs.get() == 1 && ups.get() == 1);
            for (int i = 0; i < 3; i++) {
                state.set("KEY_VOLUMEDOWN");
                Thread.sleep(65);
                state.set("<none>");
                Thread.sleep(65);
            }
            shell("input keyevent 224");
            shell("wm dismiss-keyguard");
            screen.invoke(monitor, true);
            Thread.sleep(100);
            check("all four screen-off taps retain paired edges across wake",
                    downs.get() == 4 && ups.get() == 4);
            int atWake = samples.get();
            Thread.sleep(150);
            check("wake does not discard the next baseline", samples.get() > atWake);
            unregister.invoke(monitor, listener);
            Thread.sleep(60);
            int atStop = samples.get();
            Thread.sleep(100);
            check("unregister stops sampling", samples.get() == atStop);
            // A press already held when a listener registers is still unproven.
            state.set("KEY_VOLUMEDOWN");
            int oldDowns = downs.get();
            register.invoke(monitor, listener);
            Thread.sleep(120);
            state.set("<none>");
            Thread.sleep(120);
            check("initial held volume never manufactures a down", downs.get() == oldDowns);
            result.putString("diagnostics", "samples=" + samples + ", downs=" + downs + ", ups=" + ups);
        } catch (Exception exception) { result.putString("error", exception.toString()); }
        finally {
            try {
                if (monitor != null) close.invoke(monitor);
                getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                        .dropShellPermissionIdentity();
                shell(awake ? "input keyevent 224" : "input keyevent 223");
                if (awake && !locked) shell("wm dismiss-keyguard");
            } catch (Exception exception) { result.putString("restore_error", exception.toString()); }
        }
        result.putString("tests", tests.toString());
        finish(failed || result.containsKey("error") || result.containsKey("restore_error") ? 1 : 0, result);
    }
    private static String dump(String pressed) {
        return "INPUT MANAGER\nEvent Hub State:\n  Devices:\n"
                + "    7: mtk-pmic-keys\n      Path: /dev/input/event1\n      Enabled: true\n"
                + "      SysfsDevicePath: /sys/devices/platform/pmic/mtk-pmic-keys\n"
                + "      KeyState (pressed): " + pressed + "\n"
                + "    8: gpio-keys\n      Path: /dev/input/event0\n      Enabled: true\n"
                + "      SysfsDevicePath: /sys/devices/platform/gpio-keys\n"
                + "      KeyState (pressed): <none>\nInput Reader State (Nums of device: 2):\n";
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
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
        failed |= !pass;
    }
}
