package roro.stellar.volumesteps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ScreenOffVolumeTransitionTest {
    private static int direction(int before, int after) {
        return ScreenOffVolumeTransition.direction(true, true, true, true,
                true, true, true, false, before, after, 0, 150);
    }

    @Test public void acceptsSingleAndDoubleNativeCoarseSteps() {
        assertEquals(1, direction(60, 70));
        assertEquals(-1, direction(60, 50));
        assertEquals(1, direction(60, 80));
        assertEquals(-1, direction(60, 40));
    }

    @Test public void acceptsClippedEndpointTransition() {
        assertEquals(1, direction(144, 150));
        assertEquals(-1, direction(6, 0));
    }

    @Test public void rejectsSmallPassiveAndLargeExternalChanges() {
        assertEquals(0, direction(60, 61));
        assertEquals(0, direction(60, 59));
        assertEquals(0, direction(60, 90));
    }

    @Test public void requiresOrdinaryVivoPlaybackWithReadyHelper() {
        assertEquals(0, ScreenOffVolumeTransition.direction(true, false, true, true,
                true, true, true, false, 60, 70, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, false, true,
                true, true, true, false, 60, 70, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, false,
                true, true, true, false, 60, 70, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                true, false, true, false, 60, 70, 0, 150));
    }

    @Test public void rejectsInteractiveCallBluetoothAndOtherStreams() {
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                false, true, true, false, 60, 70, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                true, true, false, false, 60, 70, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                true, true, true, true, 60, 70, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(false, true, true, true,
                true, true, true, false, 60, 70, 0, 150));
    }

    @Test public void rejectsInvalidRangesAndReadbacks() {
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                true, true, true, false, -1, 10, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                true, true, true, false, 10, 151, 0, 150));
        assertEquals(0, ScreenOffVolumeTransition.direction(true, true, true, true,
                true, true, true, false, 10, 20, 20, 20));
    }
}
