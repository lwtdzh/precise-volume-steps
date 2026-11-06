package roro.stellar.volumesteps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RawCorrectionTimingTest {
    @Test public void ownedInteractiveReleaseCanCorrectBeforeSettle() {
        assertTrue(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, true, 105, 95, -1));
        assertTrue(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, true, 105, 105, -1));
        assertTrue(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, true, 105, 115, 1));
        assertFalse(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                false, true, true, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, false, true, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, false, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, true, 105, 115, -1));
        assertFalse(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, true, -1, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyOnInteractiveScreen(
                true, true, true, 105, 95, 0));
    }

    @Test public void appliesEarlyOnlyToObservedCompanionBluetoothPlaybackStep() {
        assertTrue(RawCorrectionTiming.shouldApplyBeforeSettle(
                true, true, true, 105, 95, -1));
        assertTrue(RawCorrectionTiming.shouldApplyBeforeSettle(
                true, true, true, 90, 100, 1));

        assertFalse(RawCorrectionTiming.shouldApplyBeforeSettle(
                false, true, true, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyBeforeSettle(
                true, false, true, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyBeforeSettle(
                true, true, false, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyBeforeSettle(
                true, true, true, 105, 105, -1));
        assertFalse(RawCorrectionTiming.shouldApplyBeforeSettle(
                true, true, true, 105, 95, 1));
    }

    @Test public void appliesAtReleaseAfterNativeStepWasMasked() {
        assertTrue(RawCorrectionTiming.shouldApplyAtRelease(
                true, true, true, true, 105, 105, -1));
        assertFalse(RawCorrectionTiming.shouldApplyAtRelease(
                true, true, true, false, 105, 105, -1));
        assertFalse(RawCorrectionTiming.shouldApplyAtRelease(
                false, true, true, true, 105, 105, -1));
        assertFalse(RawCorrectionTiming.shouldApplyAtRelease(
                true, false, true, true, 105, 105, -1));
        assertFalse(RawCorrectionTiming.shouldApplyAtRelease(
                true, true, false, true, 105, 105, -1));
    }

    @Test public void armedMaskRequiresFreshMatchingKeyDirection() {
        assertTrue(RawCorrectionTiming.shouldApplyArmedMask(
                100, 400, 105, 95, -1));
        assertTrue(RawCorrectionTiming.shouldApplyArmedMask(
                100, 400, 105, 105, 1));
        assertFalse(RawCorrectionTiming.shouldApplyArmedMask(
                400, 400, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyArmedMask(
                100, 601, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldApplyArmedMask(
                100, 400, 105, 95, 1));
        assertFalse(RawCorrectionTiming.shouldApplyArmedMask(
                100, 400, -1, 0, 1));
        assertFalse(RawCorrectionTiming.shouldApplyArmedMask(
                100, 400, 105, 95, 0));
    }

    @Test public void armedMaskRetriesOneObservedSameDirectionRace() {
        assertTrue(RawCorrectionTiming.shouldRetryArmedMask(
                100, 400, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldRetryArmedMask(
                100, 400, 105, 105, -1));
        assertFalse(RawCorrectionTiming.shouldRetryArmedMask(
                400, 400, 105, 95, -1));
        assertFalse(RawCorrectionTiming.shouldRetryArmedMask(
                100, 400, 105, 95, 1));
    }

    @Test public void holdsConfirmationOnlyUntilStableOrNextGesture() {
        assertTrue(RawCorrectionTiming.shouldHoldConfirmation(200, 300, false));
        assertFalse(RawCorrectionTiming.shouldHoldConfirmation(300, 300, false));
        assertFalse(RawCorrectionTiming.shouldHoldConfirmation(200, 300, true));
    }

    @Test public void staggeredReadbacksDelayOneRetryWithinTheOriginalDeadline() {
        long until = 300;
        for (long changedAt : new long[]{150, 350, 550}) {
            until = RawCorrectionTiming.quietUntil(changedAt, until, 1000, 300);
            assertFalse(RawCorrectionTiming.shouldRetryAfterSettle(
                    changedAt, until, false, false, 12, 4, 5, true));
        }
        assertEquals(850, until);
        assertTrue(RawCorrectionTiming.shouldRetryAfterSettle(
                850, until, false, false, 12, 4, 5, true));
        // The retry's actual readback must also be watched; it cannot extend
        // either the one-second lifetime or the one-retry allowance.
        until = RawCorrectionTiming.quietUntil(880, until, 1000, 300);
        assertEquals(1000, until);
        assertTrue(RawCorrectionTiming.shouldHoldConfirmation(990, until, false));
        assertFalse(RawCorrectionTiming.shouldHoldConfirmation(1000, until, false));
        assertFalse(RawCorrectionTiming.shouldRetryAfterSettle(
                1000, until, true, false, 12, 4, 5, true));
        assertEquals(1000, RawCorrectionTiming.quietUntil(1100, until, 1000, 300));
        assertEquals(0, RawCorrectionTiming.quietUntil(100, 0, 1000, 300));
    }

    @Test public void retriesOneMatchingLateNativeWrite() {
        assertTrue(RawCorrectionTiming.shouldRetryAfterSettle(
                300, 300, false, false, 105, 95, 102, true));
        assertFalse(RawCorrectionTiming.shouldRetryAfterSettle(
                299, 300, false, false, 105, 95, 102, true));
        assertFalse(RawCorrectionTiming.shouldRetryAfterSettle(
                300, 300, true, false, 105, 95, 102, true));
        assertFalse(RawCorrectionTiming.shouldRetryAfterSettle(
                300, 300, false, true, 105, 95, 102, true));
        assertFalse(RawCorrectionTiming.shouldRetryAfterSettle(
                300, 300, false, false, 105, 110, 102, false));
    }
}
