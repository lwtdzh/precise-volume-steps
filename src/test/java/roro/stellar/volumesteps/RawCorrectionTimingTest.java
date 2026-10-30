package roro.stellar.volumesteps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RawCorrectionTimingTest {
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
