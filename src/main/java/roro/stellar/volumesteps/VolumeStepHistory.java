package roro.stellar.volumesteps;

import java.util.List;

/** Rounded offsets from a confirmed native origin preserve spacing and reversal. */
final class VolumeStepHistory {
    private Plan confirmed;

    Plan plan(int initial, int min, int max, int steps, List<Integer> directions) {
        Plan next = confirmed != null && confirmed.target() == initial
                && confirmed.min == min && confirmed.max == max && confirmed.steps == steps
                ? new Plan(confirmed) : new Plan(initial, min, max, steps);
        for (int direction : directions) next.move(direction);
        return next;
    }

    void confirm(Plan plan) {
        confirmed = plan;
    }

    void clear() {
        confirmed = null;
    }

    VolumeStepHistory copy() {
        VolumeStepHistory snapshot = new VolumeStepHistory();
        snapshot.confirmed = confirmed == null ? null : new Plan(confirmed);
        return snapshot;
    }

    static final class Plan {
        private final int min;
        private final int max;
        private final int steps;
        private final int origin;
        private int cursor;

        Plan(int initial, int min, int max, int steps) {
            this.min = min;
            this.max = max;
            this.steps = steps;
            origin = initial;
        }

        Plan(Plan source) {
            min = source.min;
            max = source.max;
            steps = source.steps;
            origin = source.origin;
            cursor = source.cursor;
        }

        int target() {
            return targetAt(cursor);
        }

        private int targetAt(int position) {
            if (max <= min) return origin;
            int intervals = Math.max(1, Math.min(steps, max - min));
            long offset = Math.round(position * (double) (max - min) / intervals);
            return (int) Math.max(min, Math.min(max, origin + offset));
        }

        private void move(int direction) {
            int next = cursor + Integer.signum(direction);
            // Limit presses do not advance the logical position. One opposite
            // press retraces the preceding rounded point, including a clipped endpoint.
            if (targetAt(next) != target()) cursor = next;
        }
    }
}
