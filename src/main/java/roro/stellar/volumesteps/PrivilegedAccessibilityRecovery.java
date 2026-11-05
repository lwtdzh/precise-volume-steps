package roro.stellar.volumesteps;

/** Bounded policy for restoring the optional lock-screen accessibility overlay. */
final class PrivilegedAccessibilityRecovery {
    private PrivilegedAccessibilityRecovery() {}

    static boolean shouldRequest(OperatingMode mode, boolean connected, boolean enabled,
                                 boolean alreadyRequested, boolean privilegedInputReady) {
        return mode != OperatingMode.ASSISTANT && !connected && !enabled
                && !alreadyRequested && privilegedInputReady;
    }
}
