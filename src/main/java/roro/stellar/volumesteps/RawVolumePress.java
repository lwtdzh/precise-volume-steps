package roro.stellar.volumesteps;

/** Release authorizes an off-screen press; power chords and stale input cancel it. */
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

    int up(String device, int key, long time, long now) {
        if (key == RawVolumeKeyParser.POWER) {
            power = false;
            return 0;
        }
        if (!device.equals(node) || key != code) return 0;
        long duration = time - downAt;
        int count = canceled || now - time < 0 || now - time > 250
                || duration < 0 || duration > 10000 ? 0
                : 1 + (repeats > 0 ? repeats
                        : duration < 500 ? 0 : 1 + (int) ((duration - 500) / 100));
        node = null;
        return Math.min(100, count);
    }

    void cancel() { node = null; canceled = true; }
}
