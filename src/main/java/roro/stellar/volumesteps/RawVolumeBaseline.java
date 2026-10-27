package roro.stellar.volumesteps;

/** Keep the last real sample when audio policy runs ahead of the raw callback. */
final class RawVolumeBaseline {
    static int beforeKey(int current, int observed, int previous, long changedAt,
                         long keyTime, int direction) {
        if (current < 0) return current;
        if (previous >= 0 && changedAt >= keyTime && changedAt - keyTime <= 250
                && Integer.signum(current - previous) == direction) {
            return previous;
        }
        // The controller has not observed this policy change yet. Its direct
        // snapshot can already contain the system's coarse key adjustment.
        if (observed >= 0 && current != observed
                && Integer.signum(current - observed) == direction) {
            return observed;
        }
        return current;
    }
}
