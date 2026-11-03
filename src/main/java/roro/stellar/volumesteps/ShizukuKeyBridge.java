package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import rikka.shizuku.Shizuku;

/** Optional read-only connection, selected by the existing Shizuku setup action. */
final class ShizukuKeyBridge {
    private static final long BIND_TIMEOUT_MS = 10000;
    interface Callback {
        void onKey(String node, int code, int value, long time, int maskedOrigin);
    }
    private final Context context;
    private final Callback callback;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final InputManager input;
    private final Shizuku.UserServiceArgs args;
    private IVolumeKeyShell remote;
    private boolean binding;
    private boolean closed;
    private int devices;
    private String error;
    private long generation;
    private ServiceConnection connection;
    private final Runnable bindingTimeout = this::bindingTimedOut;

    private void bindingTimedOut() {
        disconnect();
        error = "input connection timed out";
        changed.run();
    }
    private final Shizuku.OnBinderReceivedListener received = this::connect;
    private final Shizuku.OnBinderDeadListener dead = this::binderDied;
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> connect();
    private final InputManager.InputDeviceListener inputs = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { rescan(); }
        @Override public void onInputDeviceRemoved(int id) { rescan(); }
        @Override public void onInputDeviceChanged(int id) { rescan(); }
    };

    private ServiceConnection newConnection() {
        return new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (connection != this) return;
                if (closed || !VolumeStepSettings.isShizukuEnabled(context)) {
                    disconnect();
                    return;
                }
                handler.removeCallbacks(bindingTimeout);
                remote = IVolumeKeyShell.Stub.asInterface(binder);
                long token = ++generation;
                try {
                    remote.listen(new IVolumeKeyListener.Stub() {
                        @Override public void onKey(String node, int code, int value, long time,
                                                    int maskedOrigin) {
                            handler.post(() -> {
                                if (!closed && generation == token
                                        && VolumeStepSettings.isShizukuEnabled(context)) {
                                    callback.onKey(node, code, value, time, maskedOrigin);
                                }
                            });
                        }
                        @Override public void onStatus(int count, String failure) {
                            handler.post(() -> {
                                if (closed || generation != token) return;
                                devices = count;
                                error = failure;
                                changed.run();
                            });
                        }
                    });
                } catch (RemoteException | RuntimeException exception) { binderDied(); }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                if (connection == this) binderDied();
            }
        };
    }

    ShizukuKeyBridge(Context context, Callback callback, Runnable changed) {
        this.context = context;
        this.callback = callback;
        this.changed = changed;
        input = context.getSystemService(InputManager.class);
        int version = 1;
        try {
            version = (int) context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) { }
        args = new Shizuku.UserServiceArgs(new ComponentName(context, VolumeKeyShellService.class))
                .tag("volume-keys").version(version).daemon(false).debuggable(false)
                .processNameSuffix("volume_keys");
        input.registerInputDeviceListener(inputs, handler);
        Shizuku.addBinderDeadListener(dead, handler);
        Shizuku.addRequestPermissionResultListener(permission, handler);
        Shizuku.addBinderReceivedListenerSticky(received, handler);
    }

    void connect() {
        if (closed) return;
        if (!VolumeStepSettings.isShizukuEnabled(context)) { disconnect(); return; }
        try {
            if (ShizukuVolumeBridge.authorizationStatus() != R.string.shizuku_authorized) {
                if (binding) {
                    disconnect();
                    changed.run();
                }
                return;
            }
            if (remote != null && !remote.asBinder().isBinderAlive()) disconnect();
            if (!binding) {
                error = null;
                binding = true;
                connection = newConnection();
                handler.postDelayed(bindingTimeout, BIND_TIMEOUT_MS);
                Shizuku.bindUserService(args, connection);
            }
        } catch (RuntimeException exception) {
            disconnect();
            error = exception.getClass().getSimpleName();
            changed.run();
        }
    }

    private void rescan() {
        if (remote != null && !closed) {
            try { remote.rescan(); } catch (RemoteException | RuntimeException exception) { binderDied(); }
        }
    }

    void armBluetoothMask(int origin, long expiresAt) {
        IVolumeKeyShell service = remote;
        if (service != null && !closed) {
            try { service.armBluetoothMask(origin, expiresAt); }
            catch (RemoteException | RuntimeException exception) { binderDied(); }
        }
    }

    boolean connected() {
        return !closed && remote != null && remote.asBinder().isBinderAlive();
    }

    boolean ready() {
        return error == null && devices > 0 && connected()
                && VolumeStepSettings.isShizukuEnabled(context);
    }

    private void binderDied() {
        disconnect();
        error = "input connection stopped";
        changed.run();
    }

    private void disconnect() {
        handler.removeCallbacks(bindingTimeout);
        ++generation;
        remote = null;
        devices = 0;
        ServiceConnection previous = connection;
        connection = null;
        if (binding) {
            binding = false;
            try { Shizuku.unbindUserService(args, previous, true); }
            catch (RuntimeException ignored) { }
        }
    }

    String diagnostics() {
        return "ready=" + ready() + ", devices=" + devices + ", binding=" + binding + ", error=" + error;
    }

    void close() {
        closed = true;
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        input.unregisterInputDeviceListener(inputs);
        disconnect();
        handler.removeCallbacksAndMessages(null);
    }
}
