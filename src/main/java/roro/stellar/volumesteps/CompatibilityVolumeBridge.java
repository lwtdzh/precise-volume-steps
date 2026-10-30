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
import roro.stellar.volumesteps.compatibility.ICompatibilityKeyListener;
import roro.stellar.volumesteps.compatibility.IVolumeCompatibility;

/** Optional companion, held through a provider connection from the foreground keeper. */
final class CompatibilityVolumeBridge {
    static final String PACKAGE = CompatibilityGrantContract.HELPER_PACKAGE;
    private final Context context;
    private final Runnable changed;
    private final KeyCallback keyCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private volatile IVolumeCompatibility remote;
    private ContentProviderClient client;
    private boolean binding;
    private boolean closed;
    private boolean receiverRegistered;
    private boolean inputChecked;
    private boolean inputAvailable;
    private boolean inputRegistered;
    private boolean inputActive;
    private boolean inputCheckQueued;
    private boolean inputPrewarm;
    private Boolean inputPrewarmApplied;
    private boolean inputPrewarmQueued;
    private boolean inputPrewarmSupported = true;
    private String error;
    private String inputError;
    private long retryMs = 1000;
    private final Runnable reconnect = this::connect;
    private final Runnable inputHealth = this::refreshInput;
    private final IBinder.DeathRecipient death = this::binderDied;
    private final ICompatibilityKeyListener inputListener =
            new ICompatibilityKeyListener.Stub() {
                @Override public void onKey(int code, int value,
                                            long observedAfter, long observedAt) {
                    handler.post(() -> {
                        if (!closed && inputRegistered
                                && VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT) {
                            keyCallback.onKey(code, value, observedAfter, observedAt);
                        }
                    });
                }

                @Override public void onError(String message) {
                    handler.post(() -> {
                        if (closed) return;
                        inputRegistered = false;
                        inputError = message;
                        changed.run();
                        handler.postDelayed(CompatibilityVolumeBridge.this::refreshInput, 1000);
                    });
                }
            };

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
    CompatibilityVolumeBridge(Context context, Runnable changed, KeyCallback keyCallback) {
        this.context = context;
        this.changed = changed;
        this.keyCallback = keyCallback;
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
        if (VolumeStepSettings.mode(context) != OperatingMode.ASSISTANT) {
            disconnect();
            return;
        }
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
                refreshInput();
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
        return !closed && VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT
                && service != null && service.asBinder().isBinderAlive();
    }

    boolean inputReady() {
        return ready() && inputChecked && inputAvailable && inputRegistered
                && VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT;
    }

    void refreshInput() {
        if (VolumeStepSettings.mode(context) != OperatingMode.ASSISTANT) {
            disconnect();
            return;
        }
        if (remote == null) {
            connect();
            return;
        }
        if (closed || inputCheckQueued) return;
        IVolumeCompatibility service = remote;
        boolean desired = service != null && VolumeStepSettings.mode(context) == OperatingMode.ASSISTANT;
        boolean registeredBefore = inputRegistered;
        inputCheckQueued = true;
        worker.execute(() -> {
            boolean available = false;
            boolean registered = registeredBefore;
            boolean active = false;
            String problem = null;
            try {
                if (service != null) {
                    available = service.inputAvailable();
                    if (desired && available && !registeredBefore) {
                        registered = service.registerInputListener(inputListener);
                    } else if (registeredBefore && (!desired || !available)) {
                        service.unregisterInputListener(inputListener);
                        registered = false;
                    }
                    active = service.inputActive();
                }
            } catch (RemoteException | RuntimeException exception) {
                problem = exception.getClass().getSimpleName();
                registered = false;
            }
            final boolean checkedValue = service != null;
            final boolean availableValue = available;
            final boolean registeredValue = registered;
            final boolean activeValue = active;
            final String errorValue = problem;
            handler.post(() -> {
                inputCheckQueued = false;
                if (closed) return;
                if (remote != service) {
                    refreshInput();
                    return;
                }
                inputChecked = checkedValue;
                inputAvailable = availableValue;
                inputRegistered = registeredValue;
                inputActive = activeValue;
                inputError = errorValue;
                applyInputPrewarm();
                changed.run();
                handler.removeCallbacks(inputHealth);
                handler.postDelayed(inputHealth, 2000);
            });
        });
    }

    void setInputPrewarm(boolean enabled) {
        if (VolumeStepSettings.mode(context) != OperatingMode.ASSISTANT) enabled = false;
        if (inputPrewarm == enabled && inputPrewarmApplied != null) return;
        inputPrewarm = enabled;
        applyInputPrewarm();
    }

    private void applyInputPrewarm() {
        IVolumeCompatibility service = remote;
        if (closed || service == null || inputPrewarmQueued || !inputPrewarmSupported
                || inputPrewarmApplied != null
                && inputPrewarmApplied.booleanValue() == inputPrewarm) {
            return;
        }
        boolean enabled = inputPrewarm;
        inputPrewarmQueued = true;
        worker.execute(() -> {
            boolean success = true;
            try {
                service.setInputPrewarm(enabled);
            } catch (RemoteException | RuntimeException exception) {
                success = false;
            }
            boolean applied = success;
            handler.post(() -> {
                inputPrewarmQueued = false;
                if (closed || remote != service) return;
                if (applied) {
                    inputPrewarmApplied = enabled;
                } else {
                    // Helper 1.2 and older do not expose this optional method.
                    inputPrewarmSupported = false;
                }
                changed.run();
                if (inputPrewarmSupported && inputPrewarm != enabled) {
                    applyInputPrewarm();
                }
            });
        });
    }

    void invalidate() {
        generation.incrementAndGet();
    }

    void adjust(int stream, int target, int expectedBefore, long expiresAt, IntConsumer result) {
        IVolumeCompatibility service = remote;
        long token = generation.incrementAndGet();
        worker.execute(() -> {
            if (generation.get() != token || VolumeStepSettings.mode(context) != OperatingMode.ASSISTANT) return;
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
        if (ready()) {
            if (VolumeStepSettings.isShizukuEnabled(context)) {
                return R.string.compatibility_ready;
            }
            if (!inputChecked) return R.string.compatibility_connecting;
            if (inputError != null) return R.string.compatibility_input_failed;
            if (!inputAvailable) return R.string.compatibility_input_permission_required;
            return inputRegistered ? R.string.compatibility_input_ready
                    : R.string.compatibility_input_failed;
        }
        if (binding) return R.string.compatibility_connecting;
        return "not installed".equals(error) ? R.string.compatibility_not_installed
                : R.string.compatibility_failed;
    }

    String diagnostics() {
        return "supported=" + supportedDevice() + ", ready=" + ready()
                + ", binding=" + binding + ", error=" + error
                + ", input checked=" + inputChecked + ", available=" + inputAvailable
                + ", registered=" + inputRegistered + ", active=" + inputActive
                + ", prewarm=" + inputPrewarm + ", input error=" + inputError;
    }

    private void disconnect() {
        invalidate();
        connectionGeneration.incrementAndGet();
        handler.removeCallbacks(reconnect);
        handler.removeCallbacks(inputHealth);
        inputCheckQueued = false;
        if (remote != null) {
            try {
                remote.unregisterInputListener(inputListener);
            } catch (RemoteException | RuntimeException ignored) { }
            try {
                remote.asBinder().unlinkToDeath(death, 0);
            } catch (RuntimeException ignored) { }
        }
        remote = null;
        binding = false;
        inputChecked = false;
        inputAvailable = false;
        inputRegistered = false;
        inputActive = false;
        inputError = null;
        inputPrewarmApplied = null;
        inputPrewarmQueued = false;
        inputPrewarmSupported = true;
        if (VolumeStepSettings.mode(context) != OperatingMode.ASSISTANT) {
            inputPrewarm = false;
        }
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

    interface KeyCallback {
        void onKey(int code, int value, long observedAfter, long observedAt);
    }
}
