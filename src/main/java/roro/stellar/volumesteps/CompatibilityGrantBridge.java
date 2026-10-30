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
import rikka.shizuku.Shizuku;

/** Binds one ephemeral shell service without selecting the Shizuku volume backend. */
final class CompatibilityGrantBridge {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private Callback pending;
    private boolean binding;
    private boolean closed;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            ICompatibilityGrantShell remote = ICompatibilityGrantShell.Stub.asInterface(binder);
            worker.execute(() -> {
                int result;
                try {
                    result = remote.grantInputPermission();
                } catch (RemoteException | RuntimeException exception) {
                    result = CompatibilityGrantShellService.FAILED;
                }
                boolean granted = result == CompatibilityGrantShellService.GRANTED
                        && isGranted(context);
                handler.post(() -> finish(granted, granted ? null : "grant rejected"));
            });
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            finish(false, "grant service disconnected");
        }
    };

    CompatibilityGrantBridge(Context context) {
        this.context = context;
        int version = 1;
        try {
            version = (int) context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) { }
        args = new Shizuku.UserServiceArgs(
                new ComponentName(context, CompatibilityGrantShellService.class))
                .tag("compatibility-input-grant")
                .version(version)
                .daemon(false)
                .debuggable(false)
                .processNameSuffix("compatibility_grant");
    }

    void grant(Callback callback) {
        if (closed || binding || pending != null) {
            callback.onResult(false, "grant already in progress");
            return;
        }
        pending = callback;
        binding = true;
        try {
            Shizuku.bindUserService(args, connection);
        } catch (RuntimeException exception) {
            finish(false, exception.getClass().getSimpleName());
        }
    }

    void close() {
        closed = true;
        pending = null;
        unbind();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    static boolean isGranted(Context context) {
        return context.getPackageManager().checkPermission(
                CompatibilityGrantContract.PERMISSION,
                CompatibilityGrantContract.HELPER_PACKAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void finish(boolean success, String error) {
        if (closed && pending == null) return;
        Callback callback = pending;
        pending = null;
        unbind();
        if (callback != null && !closed) callback.onResult(success, error);
    }

    private void unbind() {
        if (!binding) return;
        binding = false;
        try {
            Shizuku.unbindUserService(args, connection, true);
        } catch (RuntimeException ignored) { }
    }

    interface Callback {
        void onResult(boolean success, String error);
    }
}
