package roro.stellar.volumesteps;

/** Selects which raw key source owns a physical press in each operating mode. */
final class RawInputOwnership {
    private RawInputOwnership() {}

    static boolean owns(OperatingMode selectedMode, OperatingMode sourceMode,
                        boolean sourceReady, boolean nonInteractive) {
        return sourceReady && selectedMode == sourceMode
                && (sourceMode != OperatingMode.ASSISTANT || nonInteractive);
    }
}
