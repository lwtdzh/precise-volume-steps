package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import rikka.shizuku.Shizuku;

/** Service-owned connection; never asks for permission without a setup action. */
final class ShizukuVolumeBridge {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicLong generation = new AtomicLong();
    private final Shizuku.UserServiceArgs args;
    private final Runnable changed;
    private volatile IVolumeShell remote;
    private boolean binding;
    private boolean closed;
    private String error;
    private final Shizuku.OnBinderReceivedListener received = this::connect;
    private final Shizuku.OnBinderDeadListener dead = this::binderDied;
    private final Shizuku.OnRequestPermissionResultListener permission =
            (code, result) -> connect();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed || !VolumeStepSettings.isShizukuEnabled(context)) {
                disconnect();
                return;
            }
            remote = IVolumeShell.Stub.asInterface(binder);
            error = null;
            changed.run();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            invalidate();
            remote = null;
            binding = false;
            error = "helper disconnected";
            changed.run();
        }
    };

    ShizukuVolumeBridge(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
        int version = 9;
        try {
            version = (int) context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) { }
        args = new Shizuku.UserServiceArgs(new ComponentName(context, VolumeShellService.class))
                .tag("volume-correction").version(version).daemon(false)
                .debuggable(false).processNameSuffix("volume_shell");
        Shizuku.addBinderDeadListener(dead, handler);
        Shizuku.addRequestPermissionResultListener(permission, handler);
        Shizuku.addBinderReceivedListenerSticky(received, handler);
    }

    void connect() {
        if (closed) {
            return;
        }
        if (!VolumeStepSettings.isShizukuEnabled(context)) {
            disconnect();
            return;
        }
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11() || Shizuku.getUid() != 2000
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (!binding) {
                error = null;
                binding = true;
                Shizuku.bindUserService(args, connection);
            }
        } catch (RuntimeException exception) {
            binding = false;
            error = exception.getClass().getSimpleName();
        }
        changed.run();
    }

    boolean ready() {
        IVolumeShell service = remote;
        return !closed && VolumeStepSettings.isShizukuEnabled(context)
                && service != null && service.asBinder().isBinderAlive();
    }

    void invalidate() {
        generation.incrementAndGet();
    }

    private void binderDied() {
        invalidate();
        remote = null;
        binding = false;
        error = null;
        changed.run();
    }

    void adjust(int stream, int target, int expectedBefore, long expiresAt,
                IntConsumer result) {
        IVolumeShell service = remote;
        long token = generation.incrementAndGet();
        worker.execute(() -> {
            if (generation.get() != token) {
                return;
            }
            int actual;
            try {
                actual = service == null ? VolumeShellService.FAILED
                        : service.adjust(stream, target, expectedBefore, expiresAt);
            } catch (RemoteException | RuntimeException exception) {
                actual = VolumeShellService.FAILED;
            }
            final int value = actual;
            handler.post(() -> {
                if (!closed && generation.get() == token) {
                    result.accept(value);
                }
            });
        });
    }

    String diagnostics() {
        return "enabled=" + VolumeStepSettings.isShizukuEnabled(context)
                + ", ready=" + ready() + ", binding=" + binding + ", error=" + error;
    }

    int status() {
        if (!VolumeStepSettings.isShizukuEnabled(context)) {
            return R.string.shizuku_off;
        }
        try {
            if (!Shizuku.pingBinder()) {
                return R.string.shizuku_not_running;
            }
            if (Shizuku.isPreV11() || Shizuku.getUid() != 2000) {
                return R.string.shizuku_unsupported;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return R.string.shizuku_permission_required;
            }
        } catch (RuntimeException exception) {
            return R.string.shizuku_not_running;
        }
        return ready() ? R.string.shizuku_ready : error == null
                ? R.string.shizuku_connecting : R.string.shizuku_failed;
    }

    private void disconnect() {
        invalidate();
        remote = null;
        if (binding) {
            binding = false;
            try {
                Shizuku.unbindUserService(args, connection, true);
            } catch (RuntimeException ignored) { }
        }
    }

    void close() {
        closed = true;
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        disconnect();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }
}
