package roro.stellar.volumesteps.compatibility;

import android.content.Context;
import android.content.pm.PackageManager;
import android.app.KeyguardManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Locked/screen-off InputManager state reader. A listener death stops all work. */
final class InputDumpMonitor {
    private static final int POLL_MS = 25;
    private static final int MAX_DUMP_BYTES = 2 * 1024 * 1024;
    private final Context context;
    private final ExecutorService loop = Executors.newSingleThreadExecutor();
    private final ExecutorService pipeReader = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private final Set<String> reported = new LinkedHashSet<>();
    private ICompatibilityKeyListener listener;
    private IBinder input;
    private boolean running;
    private boolean closed;
    private boolean prewarm;
    private long runGeneration;
    private Map<String, Set<String>> previous;
    private long previousObservedAt;
    private String error;
    private final IBinder.DeathRecipient death = this::listenerDied;

    InputDumpMonitor(Context context) {
        this.context = context;
    }

    boolean available() {
        return !closed && context.checkSelfPermission("android.permission.DUMP")
                == PackageManager.PERMISSION_GRANTED;
    }

    boolean register(ICompatibilityKeyListener candidate) {
        if (candidate == null || !available()) return false;
        synchronized (lock) {
            if (closed) return false;
            clearListenerLocked();
            listener = candidate;
            try {
                candidate.asBinder().linkToDeath(death, 0);
            } catch (RemoteException exception) {
                listener = null;
                return false;
            }
            error = null;
            if (shouldReadInput()) {
                startLocked();
            }
            return true;
        }
    }

    void unregister(ICompatibilityKeyListener candidate) {
        synchronized (lock) {
            if (listener == null || candidate == null
                    || listener.asBinder() != candidate.asBinder()) return;
            clearListenerLocked();
        }
    }

    void screenChanged(boolean interactive) {
        synchronized (lock) {
            if (interactive && !prewarm
                    && !context.getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                stopLocked();
            } else if (listener != null) {
                startLocked();
            }
        }
    }

    void setPrewarm(boolean enabled) {
        synchronized (lock) {
            prewarm = enabled;
            if (shouldReadInput()) {
                if (listener != null) startLocked();
            } else {
                stopLocked();
            }
        }
    }

    private boolean shouldReadInput() {
        return prewarm || !context.getSystemService(PowerManager.class).isInteractive()
                || context.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    boolean active() {
        synchronized (lock) {
            return running && listener != null && error == null;
        }
    }

    String diagnostics() {
        synchronized (lock) {
            return "available=" + available() + ", listener=" + (listener != null)
                    + ", active=" + running + ", prewarm=" + prewarm
                    + ", error=" + error;
        }
    }

    void close() {
        synchronized (lock) {
            closed = true;
            clearListenerLocked();
        }
        loop.shutdownNow();
        pipeReader.shutdownNow();
    }

    private void listenerDied() {
        synchronized (lock) {
            clearListenerLocked();
        }
    }

    private void startLocked() {
        if (running || closed || listener == null || !available()) return;
        running = true;
        long token = ++runGeneration;
        previous = null;
        previousObservedAt = 0;
        reported.clear();
        loop.execute(() -> readLoop(token));
    }

    private void stopLocked() {
        if (!running && reported.isEmpty()) return;
        running = false;
        runGeneration++;
        releaseReportedLocked(SystemClock.uptimeMillis());
        previous = null;
        previousObservedAt = 0;
    }

    private void clearListenerLocked() {
        stopLocked();
        if (listener != null) {
            listener.asBinder().unlinkToDeath(death, 0);
        }
        listener = null;
    }

    private void readLoop(long token) {
        try {
            IBinder service = inputService();
            while (true) {
                synchronized (lock) {
                    if (!running || closed || listener == null
                            || runGeneration != token) return;
                    if (!shouldReadInput()) {
                        stopLocked();
                        return;
                    }
                }
                long before = SystemClock.uptimeMillis();
                Map<String, Set<String>> current = InputDumpKeyState.parse(dump(service));
                long observedAt = SystemClock.uptimeMillis();
                synchronized (lock) {
                    if (!running || closed || listener == null
                            || runGeneration != token) return;
                    if (previous == null) {
                        previous = current;
                        previousObservedAt = observedAt;
                        for (InputDumpKeyState.Edge edge : InputDumpKeyState.initial(
                                current, reported, before, observedAt)) {
                            if (!sendLocked(edge)) {
                                throw new IllegalStateException("Listener disconnected");
                            }
                        }
                    } else {
                        dispatchChangesLocked(previous, current, previousObservedAt, observedAt);
                        if (!running) throw new IllegalStateException("Listener disconnected");
                        previous = current;
                        previousObservedAt = observedAt;
                    }
                }
                SystemClock.sleep(Math.max(1, POLL_MS - (observedAt - before)));
            }
        } catch (Exception exception) {
            synchronized (lock) {
                if (!running || closed || runGeneration != token) return;
                error = exception.getClass().getSimpleName();
                running = false;
                releaseReportedLocked(SystemClock.uptimeMillis());
                previous = null;
                previousObservedAt = 0;
                reportErrorLocked();
            }
        }
    }

    private void dispatchChangesLocked(Map<String, Set<String>> oldState,
                                       Map<String, Set<String>> newState,
                                       long observedAfter, long observedAt) {
        for (InputDumpKeyState.Edge edge : InputDumpKeyState.changes(
                oldState, newState, reported, observedAfter, observedAt)) {
            if (!sendLocked(edge)) return;
        }
    }

    private void releaseReportedLocked(long observedAt) {
        for (InputDumpKeyState.Edge edge : InputDumpKeyState.releases(reported, observedAt)) {
            sendLocked(edge);
        }
    }

    private boolean sendLocked(InputDumpKeyState.Edge edge) {
        try {
            listener.onKey(edge.code(), edge.value(), edge.observedAfter(), edge.observedAt());
            return true;
        } catch (RemoteException exception) {
            error = "listener disconnected";
            return false;
        }
    }

    private void reportErrorLocked() {
        if (listener == null) return;
        try {
            listener.onError(error);
        } catch (RemoteException ignored) {
            clearListenerLocked();
        }
    }

    private IBinder inputService() throws Exception {
        if (input != null && input.isBinderAlive()) return input;
        input = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "input");
        if (input == null) throw new IllegalStateException("Input service unavailable");
        return input;
    }

    private String dump(IBinder service) throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        Future<byte[]> read = pipeReader.submit(() -> {
            try (ParcelFileDescriptor.AutoCloseInputStream stream =
                         new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_DUMP_BYTES) {
                        throw new IllegalStateException("Input dump too large");
                    }
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            }
        });
        try {
            service.dumpAsync(pipe[1].getFileDescriptor(), new String[0]);
            pipe[1].close();
            return new String(read.get(2, TimeUnit.SECONDS),
                    java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            pipe[0].close();
            pipe[1].close();
            read.cancel(true);
        }
    }
}
