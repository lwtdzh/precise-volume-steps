package roro.stellar.volumesteps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

public final class VolumeStepHistoryTest {
    @Test public void reversesEveryNativeOriginOnUnevenGrids() {
        for (int max : new int[]{15, 50, 120, 150, 255}) {
            for (int steps : new int[]{7, 37, 50, 64, 100}) {
                for (int initial = 1; initial < max; initial++) {
                    for (int direction : new int[]{1, -1}) {
                        VolumeStepHistory history = new VolumeStepHistory();
                        VolumeStepHistory.Plan outward = history.plan(initial, 0, max, steps,
                                Arrays.asList(direction));
                        history.confirm(outward);
                        assertEquals(initial, history.plan(outward.target(), 0, max, steps,
                                Arrays.asList(-direction)).target());
                    }
                }
            }
        }
    }

    @Test public void immediateOppositeKeysInSameBatchRestoreOffGridOrigin() {
        VolumeStepHistory history = new VolumeStepHistory();
        assertEquals(74, history.plan(74, 0, 150, 64, Arrays.asList(1, -1)).target());
        assertEquals(74, history.plan(74, 0, 150, 64, Arrays.asList(-1, 1)).target());
    }

    @Test public void retracesMultipleConfirmedPresses() {
        VolumeStepHistory history = new VolumeStepHistory();
        VolumeStepHistory.Plan up = history.plan(74, 0, 150, 64, Arrays.asList(1, 1, 1));
        history.confirm(up);
        assertEquals(74, history.plan(up.target(), 0, 150, 64, Arrays.asList(-1, -1, -1)).target());
    }

    @Test public void failedWriteAndExternalChangesDoNotBecomeHistory() {
        VolumeStepHistory history = new VolumeStepHistory();
        history.plan(74, 0, 150, 64, Arrays.asList(1));
        assertEquals(69, history.plan(71, 0, 150, 64, Arrays.asList(-1)).target());
        VolumeStepHistory.Plan confirmed = history.plan(74, 0, 150, 64, Arrays.asList(1));
        history.confirm(confirmed);
        assertEquals(69, history.plan(71, 0, 150, 64, Arrays.asList(-1)).target());
    }

    @Test public void fiftyIntervalsMoveThreeUnitsFromAnyInteriorOrigin() {
        for (int origin = 3; origin <= 147; origin++) {
            VolumeStepHistory history = new VolumeStepHistory();
            VolumeStepHistory.Plan down = history.plan(origin, 0, 150, 50, Arrays.asList(-1));
            assertEquals(origin - 3, down.target());
            history.confirm(down);
            VolumeStepHistory.Plan back = history.plan(down.target(), 0, 150, 50, Arrays.asList(1));
            assertEquals(origin, back.target());
            history.confirm(back);
            assertEquals(origin + 3, history.plan(origin, 0, 150, 50, Arrays.asList(1)).target());
        }
    }

    @Test public void fourteenPercentSequenceHasNoExtraHistoryStops() {
        VolumeStepHistory history = new VolumeStepHistory();
        int actual = 21;
        for (int expected : new int[]{24, 27, 30}) {
            VolumeStepHistory.Plan up = history.plan(actual, 0, 150, 50, Arrays.asList(1));
            assertEquals(expected, up.target());
            history.confirm(up);
            actual = up.target();
        }
    }

    @Test public void incrementsStayWithinRoundedIntervalAcrossNativeRanges() {
        for (int min : new int[]{0, 1, 5}) {
            for (int range : new int[]{7, 15, 50, 100, 120, 150, 255}) {
                for (int requested = 5; requested <= 150; requested++) {
                    int max = min + range;
                    int intervals = Math.min(requested, range);
                    int smallest = range / intervals;
                    int largest = (range + intervals - 1) / intervals;
                    for (int origin : new int[]{min, min + range / 3, min + range / 2, max}) {
                        for (int direction : new int[]{1, -1}) {
                            VolumeStepHistory history = new VolumeStepHistory();
                            int actual = origin;
                            for (int n = 0; n <= intervals; n++) {
                                VolumeStepHistory.Plan move = history.plan(actual, min, max,
                                        requested, Arrays.asList(direction));
                                int next = move.target();
                                if (next > min && next < max) {
                                    assertTrue(Math.abs(next - actual) >= smallest);
                                    assertTrue(Math.abs(next - actual) <= largest);
                                }
                                assertTrue(direction * (next - actual) >= 0);
                                assertTrue(next >= min && next <= max);
                                history.confirm(move);
                                actual = next;
                            }
                            assertEquals(direction > 0 ? max : min, actual);
                        }
                    }
                }
            }
        }
    }

    @Test public void unevenPatternIsIdenticalWhenRetracedAndRevisited() {
        for (int max : new int[]{50, 100, 120, 150, 255}) {
            for (int steps : new int[]{7, 37, 64}) {
                VolumeStepHistory history = new VolumeStepHistory();
                int actual = max / 3;
                int[] visited = new int[5];
                visited[0] = actual;
                for (int n = 1; n < visited.length; n++) {
                    VolumeStepHistory.Plan move = history.plan(actual, 0, max, steps, Arrays.asList(1));
                    history.confirm(move);
                    visited[n] = actual = move.target();
                }
                for (int n = visited.length - 2; n >= 0; n--) {
                    VolumeStepHistory.Plan move = history.plan(actual, 0, max, steps, Arrays.asList(-1));
                    assertEquals(visited[n], move.target());
                    history.confirm(move);
                    actual = move.target();
                }
                for (int n = 1; n < visited.length; n++) {
                    VolumeStepHistory.Plan move = history.plan(actual, 0, max, steps, Arrays.asList(1));
                    assertEquals(visited[n], move.target());
                    history.confirm(move);
                    actual = move.target();
                }
            }
        }
    }
}
