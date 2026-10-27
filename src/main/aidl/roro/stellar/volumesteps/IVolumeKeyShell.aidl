package roro.stellar.volumesteps;
import roro.stellar.volumesteps.IVolumeKeyListener;

/** Read-only input listener; callers cannot supply commands or device paths. */
interface IVolumeKeyShell {
    void listen(IVolumeKeyListener listener) = 0;
    void rescan() = 1;
    void destroy() = 16777114;
}
