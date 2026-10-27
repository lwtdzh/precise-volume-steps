import android.os.IBinder;
import java.lang.reflect.Method;

/** Test-only shell preset with flags=0; never part of a distributed application. */
public final class ShellVolumePreset {
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000 || args.length != 1) {
            throw new IllegalArgumentException("Shell UID and one native media value required");
        }
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "audio");
        Class<?> type = Class.forName("android.media.IAudioService");
        Object audio = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        Method read = type.getMethod("getStreamVolume", int.class);
        int before = (Integer) read.invoke(audio, 3);
        int min = (Integer) type.getMethod("getStreamMinVolume", int.class).invoke(audio, 3);
        int max = (Integer) type.getMethod("getStreamMaxVolume", int.class).invoke(audio, 3);
        int value = Integer.parseInt(args[0]);
        if (value < min || value > max) throw new IllegalArgumentException("Outside native range");
        type.getMethod("setStreamVolume", int.class, int.class, int.class, String.class)
                .invoke(audio, 3, value, 0, "com.android.shell");
        System.out.println("before=" + before + " requested=" + value
                + " actual=" + read.invoke(audio, 3));
    }
}
