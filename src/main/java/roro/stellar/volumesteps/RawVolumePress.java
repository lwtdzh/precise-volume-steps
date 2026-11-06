package roro.stellar.volumesteps;

/** Tracks one fresh physical press; power chords and stale input cancel it. */
final class RawVolumePress {
    private String node;
    private int code;
    private long downAt;
    private int repeats;
    private boolean power;
    private boolean canceled;

    boolean down(String device, int key, long time, long now, boolean eligible) {
        if (key == RawVolumeKeyParser.POWER) {
            power = true;
            canceled = true;
            return false;
        }
        if (node != null) {
            canceled = true;
            return false;
        }
        if (!eligible || power || now - time < 0 || now - time > 250) return false;
        node = device;
        code = key;
        downAt = time;
        repeats = 0;
        canceled = false;
        return true;
    }

    void repeat(String device, int key) {
        if (device.equals(node) && key == code) repeats++;
    }

    int heldCount(long now, int repeatTimeoutMs, int repeatDelayMs) {
        return node == null || canceled || power ? 0
                : count(now - downAt, repeatTimeoutMs, repeatDelayMs);
    }

    int up(String device, int key, long time, long now,
           int repeatTimeoutMs, int repeatDelayMs) {
        if (key == RawVolumeKeyParser.POWER) {
            power = false;
            return 0;
        }
        if (!device.equals(node) || key != code) return 0;
        long duration = time - downAt;
        int count = canceled || now - time < 0 || now - time > 250
                ? 0 : count(duration, repeatTimeoutMs, repeatDelayMs);
        node = null;
        return count;
    }

    private int count(long duration, int repeatTimeoutMs, int repeatDelayMs) {
        if (duration < 0 || duration > 10000) return 0;
        int timeout = Math.max(0, repeatTimeoutMs);
        int delay = Math.max(1, repeatDelayMs);
        int estimatedRepeats = duration < timeout
                ? 0 : 1 + (int) ((duration - timeout) / delay);
        return Math.min(100, 1 + (repeats > 0 ? repeats : estimatedRepeats));
    }

    void cancel() { node = null; canceled = true; }
}
