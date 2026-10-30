package roro.stellar.volumesteps;
import roro.stellar.volumesteps.IVolumeKeyListener;

/** Fixed volume/input/setup operations; no caller-supplied commands. */
interface IPrivilegedVolume {
    int adjust(int stream, int target, int expectedBefore, long expiresAt);
    void listen(IVolumeKeyListener listener);
    void rescan();
    void armBluetoothMask(int origin, long expiresAt);
    String setup();
}
