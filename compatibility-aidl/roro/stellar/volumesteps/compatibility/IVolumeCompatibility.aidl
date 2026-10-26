package roro.stellar.volumesteps.compatibility;

/** One short-lived volume adjustment, using the helper's own Android UID. */
interface IVolumeCompatibility {
    int adjust(int stream, int target, int expectedBefore, long expiresAt);
}
