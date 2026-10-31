package roro.stellar.volumesteps;

import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated raw-input service with a key-triggered, pre-armed Bluetooth mask. */
public final class VolumeKeyShellService extends IVolumeKeyShell.Stub {
    private final ExecutorService discovery = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean scanQueued = new AtomicBoolean();
    private boolean scanAgain;
    private final Map<String, java.lang.Process> readers = new HashMap<>();
    private volatile IVolumeKeyListener listener;
    private volatile boolean closed;
    private volatile java.lang.Process metadataProcess;
    private final VolumeShellService volume;
    private volatile int bluetoothMaskOrigin = -1;
    private volatile long bluetoothMaskExpiresAt;
    private final IBinder.DeathRecipient died = this::stop;

    public VolumeKeyShellService() {
        this(Process.myUid() == 0);
    }

    VolumeKeyShellService(boolean root) {
        this(root, null);
    }

    VolumeKeyShellService(boolean root, VolumeShellService existingVolume) {
        if (Process.myUid() != (root ? 0 : 2000))
            throw new SecurityException("Unexpected input service UID");
        try {
            volume = existingVolume == null ? new VolumeShellService(root) : existingVolume;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override public synchronized void listen(IVolumeKeyListener value) throws RemoteException {
        if (listener != null) listener.asBinder().unlinkToDeath(died, 0);
        listener = value;
        if (value == null) {
            stop();
        } else {
            value.asBinder().linkToDeath(died, 0);
            rescan();
        }
    }

    @Override public synchronized void rescan() {
        if (closed) return;
        if (scanQueued.compareAndSet(false, true)) {
            discovery.execute(() -> {
                try { discover(); } finally {
                    synchronized (this) {
                        scanQueued.set(false);
                        if (scanAgain) {
                            scanAgain = false;
                            rescan();
                        }
                    }
                }
            });
        } else {
            scanAgain = true;
        }
    }

    @Override public synchronized void armBluetoothMask(int origin, long expiresAt) {
        long remaining = expiresAt - SystemClock.uptimeMillis();
        if (origin < 0 || remaining <= 0 || remaining > 500
                || !volume.armBluetooth(origin, expiresAt)) {
            bluetoothMaskOrigin = -1;
            bluetoothMaskExpiresAt = 0;
            return;
        }
        bluetoothMaskOrigin = origin;
        bluetoothMaskExpiresAt = expiresAt;
    }

    private void discover() {
        java.lang.Process metadata = null;
        ScheduledFuture<?> timeout = null;
        try {
            metadata = new ProcessBuilder("/system/bin/getevent", "-lp")
                    .redirectErrorStream(true).start();
            metadataProcess = metadata;
            java.lang.Process running = metadata;
            timeout = deadlines.schedule(running::destroy, 2, TimeUnit.SECONDS);
            StringBuilder output = new StringBuilder();
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    metadata.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (output.length() + size > 131072) throw new IOException("capabilities too large");
                    output.append(buffer, 0, size);
                }
            }
            if (!metadata.waitFor(2, TimeUnit.SECONDS)) throw new IOException("device discovery timeout");
            if (metadata.exitValue() != 0) throw new IOException("device discovery denied");
            List<String> nodes = RawVolumeKeyParser.nodes(output.toString());
            synchronized (this) {
                if (closed) return;
                readers.entrySet().removeIf(entry -> {
                    if (nodes.contains(entry.getKey()) && entry.getValue().isAlive()) return false;
                    entry.getValue().destroy();
                    return true;
                });
                for (String node : nodes) {
                    if (readers.containsKey(node)) continue;
                    java.lang.Process reader = new ProcessBuilder(
                            "/system/bin/getevent", "-lt", node)
                            .redirectErrorStream(true).start();
                    readers.put(node, reader);
                    new Thread(() -> read(node, reader), "volume-key-reader").start();
                }
                status(readers.size(), nodes.isEmpty() ? "no readable volume-key device" : null);
            }
        } catch (IOException | InterruptedException exception) {
            status(0, exception.getClass().getSimpleName());
        } finally {
            if (timeout != null) timeout.cancel(false);
            if (metadata != null) metadata.destroy();
            metadataProcess = null;
        }
    }

    private void read(String node, java.lang.Process reader) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                reader.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = lines.readLine()) != null) {
                RawVolumeKeyParser.Event event = RawVolumeKeyParser.event(line);
                IVolumeKeyListener sink = listener;
                if (event == null && line.contains("KEY_VOLUME")) {
                    status(0, "volume input format not recognized");
                }
                if (event != null && sink != null) {
                    int maskedOrigin = maskBluetoothAtKey(event);
                    sink.onKey(node, event.code(), event.value(), event.time(), maskedOrigin);
                }
            }
        } catch (IOException | RemoteException ignored) {
            // No raw text is retained, logged, or passed to the application.
        } finally {
            reader.destroy();
            synchronized (this) {
                if (readers.get(node) == reader) {
                    readers.remove(node);
                    status(readers.size(), "input reader stopped");
                }
            }
        }
    }

    private int maskBluetoothAtKey(RawVolumeKeyParser.Event event) {
        if (event.value() != 1
                || (event.code() != RawVolumeKeyParser.UP
                && event.code() != RawVolumeKeyParser.DOWN)) {
            return -1;
        }
        int origin = bluetoothMaskOrigin;
        long expiresAt = bluetoothMaskExpiresAt;
        if (origin < 0) return -3;
        if (SystemClock.uptimeMillis() >= expiresAt) return -4;
        int direction = event.code() == RawVolumeKeyParser.UP ? 1 : -1;
        int result = volume.maskBluetooth(origin, direction, expiresAt);
        return result;
    }

    private void status(int devices, String error) {
        IVolumeKeyListener sink = listener;
        if (sink != null && !closed) {
            try { sink.onStatus(devices, error); } catch (RemoteException ignored) { stop(); }
        }
    }

    synchronized void stop() {
        closed = true;
        if (metadataProcess != null) metadataProcess.destroy();
        for (java.lang.Process reader : readers.values()) reader.destroy();
        readers.clear();
        bluetoothMaskOrigin = -1;
        bluetoothMaskExpiresAt = 0;
        if (listener != null) listener.asBinder().unlinkToDeath(died, 0);
        listener = null;
        discovery.shutdownNow();
        deadlines.shutdownNow();
    }

    @Override public void destroy() {
        stop();
        System.exit(0);
    }
}
