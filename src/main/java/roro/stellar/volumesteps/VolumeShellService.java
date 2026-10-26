package roro.stellar.volumesteps;

import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import java.lang.reflect.Method;

/** Shizuku user service. Uses the shell UID and its own package identity. */
public final class VolumeShellService extends IVolumeShell.Stub {
    static final int STALE = -1;
    static final int FAILED = -2;
    private final Object audio;
    private final Method getVolume;
    private final Method getMin;
    private final Method getMax;
    private final Method setVolume;

    public VolumeShellService() throws ReflectiveOperationException {
        if (Process.myUid() != 2000) {
            throw new SecurityException("Only ADB-started Shizuku is supported");
        }
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
            int min = (Integer) getMin.invoke(audio, stream);
            int max = (Integer) getMax.invoke(audio, stream);
            int safe = Math.max(min, Math.min(max, target));
            // Reading a range must not turn a queued request into a late write.
            if (SystemClock.uptimeMillis() >= expiresAt
                    || (Integer) getVolume.invoke(audio, stream) != expectedBefore) {
                return STALE;
            }
            setVolume.invoke(audio, stream, safe, 0, "com.android.shell");
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
