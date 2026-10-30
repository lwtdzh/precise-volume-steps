package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

/** One setup action, released after completion or timeout. */
final class SetupShellBridge {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private Consumer<String> pending;
    private boolean binding, closed;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            ISetupShell remote = ISetupShell.Stub.asInterface(binder);
            worker.execute(() -> {
                String report;
                try { report = remote.setup(Process.myUid() / 100000); }
                catch (Exception exception) { report = "自动授权失败，请使用权限指南"; }
                String result = report;
                handler.post(() -> finish(result));
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { finish("Shizuku 已断开"); }
    };
    SetupShellBridge(Context context) {
        int version;
        try { version = (int) context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode(); }
        catch (Exception exception) { version = 28; }
        args = new Shizuku.UserServiceArgs(new ComponentName(context, SetupShellService.class))
                .tag("app-permission-setup").version(version).daemon(false).debuggable(false)
                .processNameSuffix("permission_setup");
    }
    void setup(Consumer<String> result) {
        if (closed || binding) return;
        pending = result;
        binding = true;
        try {
            Shizuku.bindUserService(args, connection);
            handler.postDelayed(() -> finish("自动授权超时，请使用权限指南"), 45000);
        } catch (RuntimeException exception) { finish("Shizuku 授权服务启动失败"); }
    }
    private void finish(String result) {
        Consumer<String> callback = pending;
        pending = null;
        handler.removeCallbacksAndMessages(null);
        if (binding) {
            binding = false;
            try { Shizuku.unbindUserService(args, connection, true); } catch (RuntimeException ignored) { }
        }
        if (!closed && callback != null) callback.accept(result);
    }
    void close() {
        closed = true;
        finish("");
        worker.shutdownNow();
    }
}
