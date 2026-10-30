package roro.stellar.volumesteps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Uses su directly. The selected mode controls binding, input and every write. */
final class RootVolumeBridge {
    private final Context context;
    private final Runnable changed;
    private final ShizukuKeyBridge.Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicLong writes = new AtomicLong();
    private IPrivilegedVolume remote;
    private boolean binding, closed;
    private long generation;
    private int devices;
    private String error = "点击「授权 Root 并完成设置」";
    private Consumer<String> setupResult;
    private final InputManager input;
    private final InputManager.InputDeviceListener inputs = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { rescan(); }
        @Override public void onInputDeviceRemoved(int id) { rescan(); }
        @Override public void onInputDeviceChanged(int id) { rescan(); }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed || !selected()) { disconnect(); return; }
            remote = IPrivilegedVolume.Stub.asInterface(binder);
            error = null;
            long token = ++generation;
            try {
                remote.listen(new IVolumeKeyListener.Stub() {
                    @Override public void onKey(String node, int code, int value, long time,
                                                int maskedOrigin) {
                        handler.post(() -> {
                            if (!closed && generation == token && selected())
                                callback.onKey(node, code, value, time, maskedOrigin);
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
            } catch (RemoteException exception) { failed("Root 服务连接中断"); return; }
            if (setupResult != null) setup(setupResult);
            changed.run();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            failed("Root 服务已断开，点击重新连接");
        }
    };

    RootVolumeBridge(Context context, ShizukuKeyBridge.Callback callback, Runnable changed) {
        this.context = context;
        this.callback = callback;
        this.changed = changed;
        input = context.getSystemService(InputManager.class);
        input.registerInputDeviceListener(inputs, handler);
    }
    private boolean selected() { return VolumeStepSettings.mode(context) == OperatingMode.ROOT; }

    void connect() {
        if (!selected()) { disconnect(); return; }
        // Reconnect an already authorized root shell without showing a startup prompt.
        Shell shell = Shell.getCachedShell();
        if (shell != null && shell.isRoot()) bind();
        else if (VolumeStepSettings.preferences(context).getBoolean("root_authorized", false)
                && !closed && !binding) {
            long token = generation;
            Shell.getShell(value -> handler.post(() -> {
                if (closed || !selected() || generation != token) return;
                if (value.isRoot()) bind();
                else failed("Root 权限不可用，请在 Root 管理器中重新授权");
            }));
        }
    }

    void authorizeAndSetup(Consumer<String> result) {
        if (closed || !selected()) return;
        if (setupResult != null) return;
        setupResult = result;
        error = "正在请求 Root 授权";
        changed.run();
        long token = generation;
        Shell cached = Shell.getCachedShell();
        if (cached != null && !cached.isRoot()) {
            try { cached.close(); } catch (java.io.IOException ignored) { }
        }
        Shell.getShell(shell -> handler.post(() -> {
            if (closed || !selected() || generation != token) return;
            if (!shell.isRoot()) { failed("未取得 Root 权限，请在 Root 管理器中允许本应用"); return; }
            VolumeStepSettings.preferences(context).edit().putBoolean("root_authorized", true).apply();
            if (ready()) setup(result); else bind();
        }));
        handler.postDelayed(() -> {
            if (!closed && generation == token && setupResult != null && !binding && remote == null)
                failed("Root 授权超时，请检查 Root 管理器后重试");
        }, 30000);
    }

    private void bind() {
        if (closed || binding || !selected()) return;
        binding = true;
        error = "正在连接 Root";
        RootService.bind(new Intent(context, RootVolumeService.class), connection);
        long token = generation;
        handler.postDelayed(() -> {
            if (!closed && generation == token && binding && remote == null)
                failed("Root 服务启动失败，请检查 Root 管理器");
        }, 15000);
    }

    private void setup(Consumer<String> result) {
        IPrivilegedVolume service = remote;
        long token = generation;
        worker.execute(() -> {
            String report;
            try { report = service.setup(); }
            catch (RemoteException | RuntimeException exception) { report = "自动授权失败，请使用权限指南"; }
            String message = report;
            handler.post(() -> {
                if (!closed && generation == token && selected()) {
                    setupResult = null;
                    result.accept(message);
                    changed.run();
                }
            });
        });
    }
    boolean ready() { return !closed && selected() && remote != null && remote.asBinder().isBinderAlive(); }
    boolean inputReady() { return ready() && devices > 0 && error == null; }
    void invalidate() { writes.incrementAndGet(); }
    void adjust(int stream, int target, int before, long expiry, IntConsumer result) {
        IPrivilegedVolume service = remote;
        long token = writes.incrementAndGet();
        worker.execute(() -> {
            if (writes.get() != token || !selected()) return;
            int actual;
            try { actual = service == null ? -2 : service.adjust(stream, target, before, expiry); }
            catch (RemoteException | RuntimeException exception) { actual = -2; }
            int value = actual;
            handler.post(() -> {
                if (!closed && selected() && writes.get() == token) result.accept(value);
            });
        });
    }
    private void rescan() {
        if (ready()) {
            try { remote.rescan(); } catch (RemoteException exception) { failed("Root 输入已断开"); }
        }
    }
    void armBluetoothMask(int origin, long expiresAt) {
        IPrivilegedVolume service = remote;
        if (service != null && !closed && selected()) {
            try { service.armBluetoothMask(origin, expiresAt); }
            catch (RemoteException | RuntimeException exception) { failed("Root 输入已断开"); }
        }
    }
    String status() {
        return ready() ? inputReady() ? "Root 已连接 · 息屏按键监听可用"
                : "Root 已连接 · 息屏按键监听尚未就绪" : error;
    }
    String diagnostics() { return status() + ", devices=" + devices; }
    private void failed(String message) {
        Consumer<String> result = setupResult;
        disconnect();
        error = message;
        if (result != null) result.accept(message);
        changed.run();
    }
    private void disconnect() {
        ++generation;
        invalidate();
        remote = null;
        devices = 0;
        setupResult = null;
        if (binding) {
            binding = false;
            RootService.unbind(connection);
        }
    }
    void close() {
        closed = true;
        disconnect();
        input.unregisterInputDeviceListener(inputs);
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }
}
