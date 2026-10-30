package roro.stellar.volumesteps;
import roro.stellar.volumesteps.IVolumeKeyListener;

/** Fixed key listener and bounded Bluetooth mask; no paths or commands. */
interface IVolumeKeyShell {
    void listen(IVolumeKeyListener listener) = 0;
    void rescan() = 1;
    void armBluetoothMask(int origin, long expiresAt) = 2;
    void destroy() = 16777114;
}
