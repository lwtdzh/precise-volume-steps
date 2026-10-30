package roro.stellar.volumesteps;

/** Identifies the native coarse media transition used by Vivo screen-off keys. */
final class ScreenOffVolumeTransition {
    private static final int NATIVE_INTERVALS = 15;

    private ScreenOffVolumeTransition() {}

    static int direction(boolean mediaStream, boolean ordinaryMode,
            boolean supportedDevice, boolean companionReady, boolean nonInteractive,
            boolean playbackActive, boolean normalAudioMode, boolean a2dpActive,
            int before, int after, int minimum, int maximum) {
        if (!mediaStream || !ordinaryMode || !supportedDevice || !companionReady
                || !nonInteractive || !playbackActive || !normalAudioMode || a2dpActive
                || minimum >= maximum || before < minimum || before > maximum
                || after < minimum || after > maximum || before == after) {
            return 0;
        }
        int delta = after - before;
        int magnitude = Math.abs(delta);
        int coarseStep = Math.max(1,
                ((maximum - minimum) + NATIVE_INTERVALS - 1) / NATIVE_INTERVALS);
        if (magnitude > coarseStep * 2) {
            return 0;
        }
        boolean clippedAtEndpoint = after == minimum || after == maximum;
        if (magnitude < coarseStep && !clippedAtEndpoint) {
            return 0;
        }
        return Integer.signum(delta);
    }
}
