package roro.stellar.volumesteps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VolumeStepQuantizerTest {
    @Test
    public void movesUpToNextThirtyStepBoundary() {
        assertEquals(25, VolumeStepQuantizer.nextIndex(20, 0, 150, 30, 1));
        assertEquals(25, VolumeStepQuantizer.nextIndex(22, 0, 150, 30, 1));
    }

    @Test
    public void movesDownToPreviousThirtyStepBoundary() {
        assertEquals(15, VolumeStepQuantizer.nextIndex(20, 0, 150, 30, -1));
        assertEquals(20, VolumeStepQuantizer.nextIndex(22, 0, 150, 30, -1));
    }

    @Test
    public void respectsMinimumAndMaximum() {
        assertEquals(150, VolumeStepQuantizer.nextIndex(150, 0, 150, 30, 1));
        assertEquals(0, VolumeStepQuantizer.nextIndex(0, 0, 150, 30, -1));
    }

    @Test
    public void clampsStepCountToSmallStreamRange() {
        assertEquals(8, VolumeStepQuantizer.nextIndex(7, 1, 10, 30, 1));
        assertEquals(6, VolumeStepQuantizer.nextIndex(7, 1, 10, 30, -1));
    }

    @Test
    public void advancesRapidAndLongPressesOnOneLogicalTrack() {
        assertEquals(
                35,
                VolumeStepQuantizer.advanceIndex(20, 0, 150, 30, 1, 3)
        );
        assertEquals(
                5,
                VolumeStepQuantizer.advanceIndex(20, 0, 150, 30, -1, 3)
        );
    }

    @Test
    public void screenshotAfterSuccessfulCorrectionDoesNotAdvanceVolume() {
        int corrected = VolumeStepQuantizer.correctedIndex(6, 7, 0, 15, 5, 1, 1);
        assertEquals(9, corrected);
        assertEquals(9, VolumeStepQuantizer.correctedIndex(9, 9, 0, 15, 5, 1, 4));
        assertEquals(9, VolumeStepQuantizer.correctedIndex(9, 9, 0, 15, 5, -1, 4));
    }

    @Test
    public void interveningVolumeChangeInOppositeDirectionIsPreserved() {
        assertEquals(5, VolumeStepQuantizer.correctedIndex(6, 5, 0, 15, 5, 1, 1));
    }

    @Test
    public void roundedStepGridsReachBothLimitsWithoutSticking() {
        int current = 0;
        for (int level = 1; level <= 37; level++) {
            current = VolumeStepQuantizer.nextIndex(current, 0, 150, 37, 1);
            assertEquals((int) Math.round((double) level * 150 / 37), current);
        }
        assertEquals(150, current);
        for (int level = 36; level >= 0; level--) {
            current = VolumeStepQuantizer.nextIndex(current, 0, 150, 37, -1);
            assertEquals((int) Math.round((double) level * 150 / 37), current);
        }
        assertEquals(0, current);
    }
}
