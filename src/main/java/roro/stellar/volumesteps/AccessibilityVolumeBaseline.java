package roro.stellar.volumesteps;

/** Recover only a real transition observed after the original accessibility key. */
final class AccessibilityVolumeBaseline {
    static int beforeKey(int current, int observed, int previous, long changedAt,
                         long keyTime, int direction) {
        if (current >= 0 && current == observed && previous >= 0
                && changedAt >= keyTime && changedAt - keyTime <= 250
                && Integer.signum(current - previous) == direction) {
            return previous;
        }
        return current;
    }
}
