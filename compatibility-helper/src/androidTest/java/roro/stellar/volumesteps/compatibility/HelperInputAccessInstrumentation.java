package roro.stellar.volumesteps.compatibility;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only capability evidence in the real helper UID; never grants permissions. */
public final class HelperInputAccessInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context helper = getTargetContext();
            if (Process.myUid() != helper.getApplicationInfo().uid
                    || !"com.vivo.finevolumesteps.helper".equals(helper.getPackageName())) {
                throw new IllegalStateException("Must run in the installed helper's own UID");
            }
            JSONObject evidence = new JSONObject()
                    .put("package", helper.getPackageName())
                    .put("uid", Process.myUid())
                    .put("input", "permission checks and open/close only; no keys injected or read");
            JSONArray permissions = new JSONArray();
            for (String permission : new String[]{
                    "android.permission.MONITOR_INPUT",
                    "android.permission.LISTEN_FOR_KEY_ACTIVITY",
                    "android.permission.MANAGE_KEY_GESTURES",
                    "android.permission.USE_BIOMETRIC_INTERNAL",
                    "android.permission.READ_INPUT_STATE",
                    "android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER",
                    "android.permission.MODIFY_PHONE_STATE",
                    "android.permission.STATUS_BAR_SERVICE",
                    "android.permission.MODIFY_AUDIO_ROUTING",
                    "android.permission.FILTER_EVENTS"}) {
                JSONObject item = new JSONObject().put("permission", permission)
                        .put("granted", helper.checkSelfPermission(permission)
                                == PackageManager.PERMISSION_GRANTED);
                try {
                    PermissionInfo info = helper.getPackageManager().getPermissionInfo(permission, 0);
                    item.put("protection_level", info.protectionLevel);
                } catch (PackageManager.NameNotFoundException absent) {
                    item.put("defined", false);
                }
                permissions.put(item);
            }
            evidence.put("permissions", permissions);
            JSONArray nodes = new JSONArray();
            String requested = arguments.getString("nodes", "");
            if (requested.isEmpty()) throw new IllegalArgumentException("Explicit volume nodes required");
            for (String node : requested.split(",")) {
                if (!node.matches("/dev/input/event[0-9]+")) {
                    throw new IllegalArgumentException("Invalid input node");
                }
                JSONObject item = new JSONObject().put("node", node);
                try (FileInputStream ignored = new FileInputStream(node)) {
                    item.put("readable", true);
                } catch (Exception denied) {
                    item.put("readable", false).put("error", denied.toString());
                }
                nodes.put(item);
            }
            evidence.put("nodes", nodes);
            JSONArray files = new JSONArray();
            Set<String> allowedFiles = Set.of(
                    "/proc/interrupts",
                    "/sys/devices/platform/gpio-keys/wakeup/wakeup5/event_count",
                    "/sys/kernel/irq/114/hwirq");
            String requestedFiles = arguments.getString("files", "");
            if (!requestedFiles.isEmpty()) {
                for (String path : requestedFiles.split(",")) {
                    if (!allowedFiles.contains(path)) {
                        throw new IllegalArgumentException("Invalid diagnostic file");
                    }
                    JSONObject item = new JSONObject().put("path", path);
                    try (FileInputStream input = new FileInputStream(path)) {
                        ByteArrayOutputStream content = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int total = 0;
                        int count;
                        while (total < 65536
                                && (count = input.read(buffer, 0,
                                Math.min(buffer.length, 65536 - total))) != -1) {
                            content.write(buffer, 0, count);
                            total += count;
                        }
                        item.put("readable", true)
                                .put("content", content.toString(StandardCharsets.UTF_8));
                    } catch (Exception denied) {
                        item.put("readable", false).put("error", denied.toString());
                    }
                    files.put(item);
                }
            }
            evidence.put("files", files);
            if ("true".equals(arguments.getString("vivo_events", "false"))) {
                evidence.put("vivo_events", inspectVivoEvents(helper));
            }
            result.putString("evidence", evidence.toString());
        } catch (Exception error) {
            result.putString("error", error.toString());
        }
        finish(result.containsKey("error") ? 1 : 0, result);
    }

    private JSONObject inspectVivoEvents(Context helper) throws Exception {
        JSONObject evidence = new JSONObject().put("event", "POWER_ID_INPUT")
                .put("calling_uid", Process.myUid());
        AtomicInteger received = new AtomicInteger();
        String descriptor = "vivo.app.pem.IVivoEventCallback";
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(descriptor);
                    return true;
                }
                if (code == FIRST_CALL_TRANSACTION) {
                    data.enforceInterface(descriptor);
                    String event = data.readString();
                    data.readString();
                    if ("POWER_ID_INPUT".equals(event)) received.incrementAndGet();
                    return true;
                }
                return false;
            }
        };
        callback.attachInterface(new IInterface() {
            @Override public IBinder asBinder() { return callback; }
        }, descriptor);
        IBinder service = null;
        boolean attempted = false;
        try {
            service = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "vivo_event_service");
            evidence.put("service_found", service != null);
            if (service == null) return evidence.put("registration_attempted", false);
            evidence.put("service_descriptor", service.getInterfaceDescriptor());
            if (!"vivo.app.pem.IVivoEventService".equals(service.getInterfaceDescriptor())) {
                throw new IllegalStateException("Unexpected event service");
            }
            attempted = true;
            Parcel request = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                request.writeInterfaceToken("vivo.app.pem.IVivoEventService");
                request.writeString("PreciseVolumeStepsReadOnlyProbe");
                request.writeStringArray(new String[]{"POWER_ID_INPUT"});
                request.writeStrongBinder(callback);
                if (!service.transact(1, request, reply, 0)) {
                    throw new IllegalStateException("Registration transaction unhandled");
                }
                reply.readException();
            } finally {
                reply.recycle();
                request.recycle();
            }
            evidence.put("registration_returned", true);
        } catch (Exception error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? ((InvocationTargetException) error).getCause() : error;
            evidence.put("registration_returned", false).put("error", cause.toString());
        } finally {
            if (attempted && service != null) {
                Parcel request = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    request.writeInterfaceToken("vivo.app.pem.IVivoEventService");
                    request.writeStrongBinder(callback);
                    if (!service.transact(2, request, reply, 0)) {
                        throw new IllegalStateException("Unregister transaction unhandled");
                    }
                    reply.readException();
                    evidence.put("unregister_returned", true);
                } catch (Exception error) {
                    evidence.put("unregister_returned", false).put("cleanup_error", error.toString());
                } finally {
                    reply.recycle();
                    request.recycle();
                }
            }
        }
        return evidence.put("registration_attempted", attempted)
                .put("callbacks_received", received.get());
    }
}
