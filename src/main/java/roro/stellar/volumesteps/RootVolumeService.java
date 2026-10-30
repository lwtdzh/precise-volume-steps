package roro.stellar.volumesteps;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import com.topjohnwu.superuser.ipc.RootService;

/** libsu starts this service under UID 0; Binder accepts only this app's UID. */
public final class RootVolumeService extends RootService {
    private VolumeShellService volume;
    private VolumeKeyShellService keys;
    private int owner;

    @Override public void onCreate() {
        owner = getApplicationInfo().uid;
        try { volume = new VolumeShellService(true); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        keys = new VolumeKeyShellService(true, volume);
    }

    private long authorize() {
        if (Binder.getCallingUid() != owner)
            throw new SecurityException("Only the owning app may use root volume");
        return Binder.clearCallingIdentity();
    }

    private final IPrivilegedVolume.Stub binder = new IPrivilegedVolume.Stub() {
        @Override public int adjust(int stream, int target, int before, long expiry) {
            long token = authorize();
            try { return volume.adjust(stream, target, before, expiry); }
            finally { Binder.restoreCallingIdentity(token); }
        }
        @Override public void listen(IVolumeKeyListener listener) throws RemoteException {
            long token = authorize();
            try { keys.listen(listener); } finally { Binder.restoreCallingIdentity(token); }
        }
        @Override public void rescan() {
            long token = authorize();
            try { keys.rescan(); } finally { Binder.restoreCallingIdentity(token); }
        }
        @Override public void armBluetoothMask(int origin, long expiry) {
            long token = authorize();
            try { keys.armBluetoothMask(origin, expiry); }
            finally { Binder.restoreCallingIdentity(token); }
        }
        @Override public String setup() {
            long token = authorize();
            try { return PrivilegedSetup.run(owner / 100000); }
            finally { Binder.restoreCallingIdentity(token); }
        }
    };

    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onDestroy() { if (keys != null) keys.stop(); }
}
