package roro.stellar.volumesteps;

final class VolumeStepQuantizer {
    private VolumeStepQuantizer() {}

    static int nextIndex(int current, int min, int max, int requestedSteps, int direction) {
        if (max <= min || direction == 0) {
            return current;
        }

        int range = max - min;
        int steps = Math.max(1, Math.min(requestedSteps, range));
        int low = 0;
        int high = steps + 1;
        while (low < high) {
            int middle = (low + high) / 2;
            int index = min + (int) Math.round((double) middle * range / steps);
            boolean beyond = direction > 0 ? index > current : index >= current;
            if (beyond) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        int nextLevel = direction > 0 ? Math.min(low, steps) : Math.max(0, low - 1);
        return min + (int) Math.round((double) nextLevel * range / steps);
    }

    static int advanceIndex(
            int current,
            int min,
            int max,
            int requestedSteps,
            int direction,
            int pressCount
    ) {
        int result = current;
        for (int press = 0; press < pressCount; press++) {
            result = nextIndex(result, min, max, requestedSteps, direction);
        }
        return result;
    }

    static int correctedIndex(
            int initial, int observed, int min, int max, int steps,
            int direction, int pressCount
    ) {
        if (initial < min || initial > max || (observed - initial) * direction <= 0) {
            return observed;
        }
        return advanceIndex(initial, min, max, steps, direction, pressCount);
    }
}
