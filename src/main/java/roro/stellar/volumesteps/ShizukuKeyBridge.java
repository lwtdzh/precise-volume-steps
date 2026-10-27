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
    interface Callback { void onKey(String node, int code, int value, long time); }
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
    private final Shizuku.OnBinderReceivedListener received = this::connect;
    private final Shizuku.OnBinderDeadListener dead = this::binderDied;
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> connect();
    private final InputManager.InputDeviceListener inputs = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { rescan(); }
        @Override public void onInputDeviceRemoved(int id) { rescan(); }
        @Override public void onInputDeviceChanged(int id) { rescan(); }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed || !VolumeStepSettings.isShizukuEnabled(context)) { disconnect(); return; }
            remote = IVolumeKeyShell.Stub.asInterface(binder);
            long token = ++generation;
            try {
                remote.listen(new IVolumeKeyListener.Stub() {
                    @Override public void onKey(String node, int code, int value, long time) {
                        handler.post(() -> {
                            if (!closed && generation == token
                                    && VolumeStepSettings.isShizukuEnabled(context)) {
                                callback.onKey(node, code, value, time);
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
        @Override public void onServiceDisconnected(ComponentName name) { binderDied(); }
    };

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
            if (!Shizuku.pingBinder() || Shizuku.isPreV11() || Shizuku.getUid() != 2000
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
            if (!binding) {
                binding = true;
                Shizuku.bindUserService(args, connection);
            }
        } catch (RuntimeException exception) {
            binding = false;
            error = exception.getClass().getSimpleName();
        }
    }

    private void rescan() {
        if (remote != null && !closed) {
            try { remote.rescan(); } catch (RemoteException | RuntimeException exception) { binderDied(); }
        }
    }

    boolean ready() {
        return !closed && error == null && devices > 0
                && remote != null && remote.asBinder().isBinderAlive()
                && VolumeStepSettings.isShizukuEnabled(context);
    }

    private void binderDied() {
        ++generation;
        remote = null;
        binding = false;
        devices = 0;
        error = "input connection stopped";
        changed.run();
    }

    private void disconnect() {
        ++generation;
        remote = null;
        devices = 0;
        if (binding) {
            binding = false;
            try { Shizuku.unbindUserService(args, connection, true); }
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
