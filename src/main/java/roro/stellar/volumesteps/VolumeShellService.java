package roro.stellar.volumesteps;

import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import java.lang.reflect.Method;

/** Shizuku user service. Uses the server UID and its matching package identity. */
public final class VolumeShellService extends IVolumeShell.Stub {
    static final int STALE = -1;
    static final int FAILED = -2;
    private final Object audio;
    private final Method getVolume;
    private final Method getMin;
    private final Method getMax;
    private final Method setVolume;
    private final BluetoothVolumeSync bluetoothSync;
    private final int[] minimum = new int[6];
    private final int[] maximum = new int[6];

    public VolumeShellService() throws ReflectiveOperationException {
        this(Process.myUid() == 0);
    }

    VolumeShellService(boolean root) throws ReflectiveOperationException {
        if (Process.myUid() != (root ? 0 : 2000))
            throw new SecurityException("Unexpected volume service UID");
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "audio");
        audio = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        Class<?> type = Class.forName("android.media.IAudioService");
        getVolume = type.getMethod("getStreamVolume", int.class);
        getMin = type.getMethod("getStreamMinVolume", int.class);
        getMax = type.getMethod("getStreamMaxVolume", int.class);
        setVolume = type.getMethod("setStreamVolume",
                int.class, int.class, int.class, String.class);
        for (int stream = 0; stream < minimum.length; stream++) refreshRange(stream);
        bluetoothSync = BluetoothVolumeSync.create(audio, type);
    }

    private void refreshRange(int stream) throws ReflectiveOperationException {
        int min = (Integer) getMin.invoke(audio, stream);
        int max = (Integer) getMax.invoke(audio, stream);
        if (min < 0 || max < min) throw new IllegalStateException("Invalid native range");
        minimum[stream] = min;
        maximum[stream] = max;
    }

    synchronized boolean armBluetooth(int origin, long expiresAt) {
        try {
            long remaining = expiresAt - SystemClock.uptimeMillis();
            if (remaining <= 0 || remaining > 500 || bluetoothSync == null) return false;
            if (origin < minimum[3] || origin > maximum[3]) refreshRange(3);
            return origin >= minimum[3] && origin <= maximum[3]
                    && bluetoothSync.arm(3, origin, expiresAt);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    synchronized int maskBluetooth(int origin, int direction, long expiresAt) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("direction");
        try {
            long remaining = expiresAt - SystemClock.uptimeMillis();
            if (remaining <= 0 || remaining > 500 || bluetoothSync == null) return STALE;
            int current = (Integer) getVolume.invoke(audio, 3);
            if (!RawCorrectionTiming.shouldApplyArmedMask(
                    SystemClock.uptimeMillis(), expiresAt, origin, current, direction)) return STALE;
            if (origin < minimum[3] || origin > maximum[3]) refreshRange(3);
            if (origin < minimum[3] || origin > maximum[3]) {
                return STALE;
            }
            if (!bluetoothSync.dispatchArmed(3, origin, current, expiresAt)) {
                current = (Integer) getVolume.invoke(audio, 3);
                if (!RawCorrectionTiming.shouldApplyArmedMask(
                        SystemClock.uptimeMillis(), expiresAt,
                        origin, current, direction)
                        || !bluetoothSync.dispatchArmed(
                        3, origin, current, expiresAt)) return STALE;
            }
            current = (Integer) getVolume.invoke(audio, 3);
            if (!RawCorrectionTiming.shouldApplyArmedMask(
                    SystemClock.uptimeMillis(), expiresAt, origin, current, direction)) {
                return STALE;
            }
            setVolume.invoke(audio, 3, origin, 0,
                    Process.myUid() == 0 ? "android" : "com.android.shell");
            int accepted = (Integer) getVolume.invoke(audio, 3);
            if (RawCorrectionTiming.shouldRetryArmedMask(
                    SystemClock.uptimeMillis(), expiresAt,
                    origin, accepted, direction)) {
                setVolume.invoke(audio, 3, origin, 0,
                        Process.myUid() == 0 ? "android" : "com.android.shell");
                accepted = (Integer) getVolume.invoke(audio, 3);
            }
            if (accepted == origin) bluetoothSync.dispatch(3, accepted, expiresAt);
            return accepted;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return FAILED;
        }
    }

    @Override public synchronized int adjust(int stream, int target, int expectedBefore,
                                             long expiresAt) {
        if (stream != 3 && stream != 2 && stream != 4 && stream != 5
                && stream != 1 && stream != 0) {
            throw new IllegalArgumentException("Unsupported stream");
        }
        try {
            long remaining = expiresAt - SystemClock.uptimeMillis();
            if (remaining <= 0 || remaining > 1000
                    || (Integer) getVolume.invoke(audio, stream) != expectedBefore) {
                return STALE;
            }
            // AudioService ranges are loaded when this service connects. The
            // main controller also plans from current public ranges. Refresh
            // expanded ranges on demand; AudioService still clamps every write.
            if (target < minimum[stream] || target > maximum[stream]
                    || expectedBefore < minimum[stream] || expectedBefore > maximum[stream])
                refreshRange(stream);
            int safe = Math.max(minimum[stream], Math.min(maximum[stream], target));
            // A refresh must not turn a queued request into a late write.
            if (SystemClock.uptimeMillis() >= expiresAt
                    || (Integer) getVolume.invoke(audio, stream) != expectedBefore) {
                return STALE;
            }
            setVolume.invoke(audio, stream, safe, 0,
                    Process.myUid() == 0 ? "android" : "com.android.shell");
            int accepted = (Integer) getVolume.invoke(audio, stream);
            if (accepted == safe && bluetoothSync != null)
                bluetoothSync.dispatch(stream, accepted, expiresAt);
            return (Integer) getVolume.invoke(audio, stream);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // The caller reads back the real volume. Never repeat an uncertain write.
            return FAILED;
        }
    }

    @Override public void destroy() {
        System.exit(0);
    }
}
