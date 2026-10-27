package roro.stellar.volumesteps;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RawVolumeBaselineTest {
    @Test public void policyWriteBeforeCallbackAndPollUsesLastRealSample() {
        assertEquals(115, RawVolumeBaseline.beforeKey(105, 115, 118, 500, 1000, -1));
        assertEquals(74, RawVolumeBaseline.beforeKey(84, 74, 71, 500, 1000, 1));
    }

    @Test public void policyWriteAlreadyObservedAfterKeyUsesPreviousSample() {
        assertEquals(115, RawVolumeBaseline.beforeKey(105, 105, 115, 1003, 1000, -1));
        assertEquals(115, RawVolumeBaseline.beforeKey(95, 105, 115, 1003, 1000, -1));
    }

    @Test public void observedPassivePresetBeforeKeyBecomesNewOrigin() {
        assertEquals(104, RawVolumeBaseline.beforeKey(104, 104, 115, 990, 1000, -1));
        assertEquals(104, RawVolumeBaseline.beforeKey(94, 104, 115, 990, 1000, -1));
    }

    @Test public void oppositeChangeAndFailedReadDoNotInventBaseline() {
        assertEquals(125, RawVolumeBaseline.beforeKey(125, 115, 118, 500, 1000, -1));
        assertEquals(105, RawVolumeBaseline.beforeKey(105, -1, -1, 0, 1000, -1));
        assertEquals(-1, RawVolumeBaseline.beforeKey(-1, 115, 118, 500, 1000, -1));
        assertEquals(105, RawVolumeBaseline.beforeKey(105, 105, 115, 1300, 1000, -1));
    }
}
