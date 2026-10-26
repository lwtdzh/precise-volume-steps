package roro.stellar.volumesteps;

import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.net.Uri;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import roro.stellar.volumesteps.compatibility.IVolumeCompatibility;

/** Optional companion, held through a provider connection from the foreground keeper. */
final class CompatibilityVolumeBridge {
    static final String PACKAGE = "com.vivo.finevolumesteps.helper";
    private final Context context;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private volatile IVolumeCompatibility remote;
    private ContentProviderClient client;
    private boolean binding;
    private boolean closed;
    private boolean receiverRegistered;
    private String error;
    private long retryMs = 1000;
    private final Runnable reconnect = this::connect;
    private final IBinder.DeathRecipient death = this::binderDied;

    private void binderDied() {
        handler.post(() -> {
            if (closed) return;
            disconnect();
            error = "helper disconnected";
            handler.postDelayed(reconnect, retryMs);
            changed.run();
        });
    }
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (intent.getData() != null && PACKAGE.equals(intent.getData().getSchemeSpecificPart())) {
                disconnect();
                connect();
                changed.run();
            }
        }
    };
    CompatibilityVolumeBridge(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
        if (supportedDevice()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(packageChanges, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(packageChanges, filter);
            }
            receiverRegistered = true;
            connect();
        }
    }

    static boolean supportedDevice() {
        return "vivo".equalsIgnoreCase(Build.MANUFACTURER)
                || "iqoo".equalsIgnoreCase(Build.MANUFACTURER);
    }

    void connect() {
        if (closed || binding || ready() || !supportedDevice()) return;
        try {
            context.getPackageManager().getApplicationInfo(PACKAGE, 0);
            if (context.getPackageManager().checkSignatures(context.getPackageName(), PACKAGE)
                    != PackageManager.SIGNATURE_MATCH) {
                error = "signature mismatch";
                return;
            }
            binding = true;
            long token = connectionGeneration.incrementAndGet();
            worker.execute(() -> acquire(token));
        } catch (PackageManager.NameNotFoundException exception) {
            error = "not installed";
        } catch (RuntimeException exception) {
            binding = false;
            error = exception.getClass().getSimpleName();
        }
    }

    private void acquire(long token) {
        ContentProviderClient acquired = null;
        IBinder binder = null;
        String failure = null;
        try {
            acquired = context.getContentResolver().acquireUnstableContentProviderClient(
                    Uri.parse("content://" + PACKAGE + ".volume"));
            if (acquired == null) throw new IllegalStateException("Provider unavailable");
            Bundle response = acquired.call("connect", null, null);
            binder = response == null ? null : response.getBinder("volume");
            if (binder == null) throw new IllegalStateException("Volume binder unavailable");
        } catch (RemoteException | RuntimeException exception) {
            failure = exception.toString();
        }
        final ContentProviderClient held = acquired;
        final IBinder connected = binder;
        final String problem = failure;
        handler.post(() -> {
            if (closed || connectionGeneration.get() != token) {
                if (held != null) held.close();
                return;
            }
            binding = false;
            try {
                if (problem != null) throw new IllegalStateException(problem);
                connected.linkToDeath(death, 0);
                client = held;
                remote = IVolumeCompatibility.Stub.asInterface(connected);
                error = null;
                retryMs = 1000;
            } catch (RemoteException | RuntimeException exception) {
                if (held != null) held.close();
                error = exception.toString();
                handler.postDelayed(reconnect, retryMs);
                retryMs = Math.min(60000, retryMs * 2);
            }
            changed.run();
        });
    }

    boolean ready() {
        IVolumeCompatibility service = remote;
        return !closed && service != null && service.asBinder().isBinderAlive();
    }

    void invalidate() {
        generation.incrementAndGet();
    }

    void adjust(int stream, int target, int expectedBefore, long expiresAt, IntConsumer result) {
        IVolumeCompatibility service = remote;
        long token = generation.incrementAndGet();
        worker.execute(() -> {
            if (generation.get() != token) return;
            int actual;
            try {
                actual = service == null ? -2
                        : service.adjust(stream, target, expectedBefore, expiresAt);
            } catch (RemoteException | RuntimeException exception) {
                actual = -2;
            }
            final int value = actual;
            handler.post(() -> {
                if (!closed && generation.get() == token) result.accept(value);
            });
        });
    }

    int status() {
        if (!supportedDevice()) return R.string.compatibility_not_applicable;
        if (ready()) return R.string.compatibility_ready;
        if (binding) return R.string.compatibility_connecting;
        return "not installed".equals(error) ? R.string.compatibility_not_installed
                : R.string.compatibility_failed;
    }

    String diagnostics() {
        return "supported=" + supportedDevice() + ", ready=" + ready()
                + ", binding=" + binding + ", error=" + error;
    }

    private void disconnect() {
        invalidate();
        connectionGeneration.incrementAndGet();
        handler.removeCallbacks(reconnect);
        if (remote != null) {
            try {
                remote.asBinder().unlinkToDeath(death, 0);
            } catch (RuntimeException ignored) { }
        }
        remote = null;
        binding = false;
        if (client != null) client.close();
        client = null;
    }

    void close() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) context.unregisterReceiver(packageChanges);
        disconnect();
        worker.shutdown();
    }
}
