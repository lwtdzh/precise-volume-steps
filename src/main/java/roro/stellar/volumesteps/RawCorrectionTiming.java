package roro.stellar.volumesteps;

/** Timing rules for reducing an audible native coarse step without losing late-write safety. */
final class RawCorrectionTiming {
    private RawCorrectionTiming() {}

    static boolean shouldApplyBeforeSettle(boolean companionInput, boolean playbackActive,
                                           boolean wirelessRoute, int initial,
                                           int valueAtRelease, int lastDirection) {
        return companionInput && playbackActive && wirelessRoute
                && initial >= 0 && valueAtRelease >= 0 && initial != valueAtRelease
                && Integer.signum(valueAtRelease - initial) == lastDirection;
    }

    static boolean shouldApplyAtRelease(boolean companionInput, boolean playbackActive,
                                        boolean wirelessRoute, boolean nativeStepMasked,
                                        int initial, int valueAtRelease, int lastDirection) {
        return companionInput && playbackActive && wirelessRoute
                && initial >= 0 && valueAtRelease >= 0 && (nativeStepMasked || shouldApplyBeforeSettle(
                companionInput, playbackActive, wirelessRoute,
                initial, valueAtRelease, lastDirection));
    }

    static boolean shouldApplyOnLitKeyguard(boolean interactive, boolean locked,
                                            boolean matchedRelease, int initial,
                                            int current, int direction) {
        return interactive && locked && matchedRelease && initial >= 0 && current >= 0
                && (direction == -1 || direction == 1)
                && (current == initial || Integer.signum(current - initial) == direction);
    }

    static boolean shouldApplyArmedMask(long now, long expiresAt,
                                        int origin, int current, int direction) {
        return expiresAt > now && expiresAt - now <= 500
                && origin >= 0 && current >= 0
                && (direction == -1 || direction == 1)
                && (current == origin || Integer.signum(current - origin) == direction);
    }

    static boolean shouldRetryArmedMask(long now, long expiresAt,
                                        int origin, int current, int direction) {
        return current != origin
                && shouldApplyArmedMask(now, expiresAt, origin, current, direction);
    }

    static boolean shouldHoldConfirmation(long now, long stabilizeUntil,
                                          boolean newerGestureWaiting) {
        return !newerGestureWaiting && stabilizeUntil > now;
    }

    static boolean shouldRetryAfterSettle(long now, long stabilizeUntil,
                                          boolean alreadyRetried, boolean newerGestureWaiting,
                                          int origin, int current, int target,
                                          boolean matchingDirection) {
        return !alreadyRetried && !newerGestureWaiting && stabilizeUntil > 0
                && now >= stabilizeUntil && current != target && current != origin
                && matchingDirection;
    }
}
