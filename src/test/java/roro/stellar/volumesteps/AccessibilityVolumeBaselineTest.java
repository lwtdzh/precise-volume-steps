package roro.stellar.volumesteps;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AccessibilityVolumeBaselineTest {
    @Test public void delayedCallbackUsesTransitionAfterOriginalKeyTime() {
        assertEquals(6, AccessibilityVolumeBaseline.beforeKey(16, 16, 6, 1005, 1000, 1));
        assertEquals(74, AccessibilityVolumeBaseline.beforeKey(64, 64, 74, 1200, 1000, -1));
    }

    @Test public void presetBeforeOriginalKeyTimeRemainsTheOrigin() {
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, 16, 6, 999, 1000, 1));
    }

    @Test public void unobservedOrSubsequentChangeCannotBeRecovered() {
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, 6, 3, 999, 1000, 1));
        assertEquals(26, AccessibilityVolumeBaseline.beforeKey(26, 16, 6, 1005, 1000, 1));
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, 16, 6, 1251, 1000, 1));
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, 16, 26, 1005, 1000, 1));
    }

    @Test public void failedReadsAndConfirmedSameSampleCannotInventAChange() {
        assertEquals(-1, AccessibilityVolumeBaseline.beforeKey(-1, 16, 6, 1005, 1000, 1));
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, -1, 6, 1005, 1000, 1));
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, 16, -1, 1005, 1000, 1));
        assertEquals(16, AccessibilityVolumeBaseline.beforeKey(16, 16, 16, 1005, 1000, 1));
    }
}
