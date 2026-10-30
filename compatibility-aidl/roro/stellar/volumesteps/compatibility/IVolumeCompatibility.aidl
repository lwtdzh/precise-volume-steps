package roro.stellar.volumesteps.compatibility;

import roro.stellar.volumesteps.compatibility.ICompatibilityKeyListener;

/** Scoped volume writes and optional exact physical-key observation. */
interface IVolumeCompatibility {
    int adjust(int stream, int target, int expectedBefore, long expiresAt);
    boolean registerInputListener(ICompatibilityKeyListener listener);
    void unregisterInputListener(ICompatibilityKeyListener listener);
    boolean inputAvailable();
    boolean inputActive();
    void setInputPrewarm(boolean enabled);
}
