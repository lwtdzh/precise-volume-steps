package roro.stellar.volumesteps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PrivilegedAccessibilityRecoveryTest {
    @Test public void privilegedModeRequestsOnlyWhenMissingAndReady() {
        assertTrue(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.SHIZUKU, false, false, false, true));
        assertTrue(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.ROOT, false, false, false, true));
        assertFalse(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.SHIZUKU, true, false, false, true));
        assertFalse(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.ROOT, false, true, false, true));
        assertFalse(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.SHIZUKU, false, false, true, true));
        assertFalse(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.ROOT, false, false, false, false));
    }

    @Test public void assistantNeverRequestsPrivilegedAccessibility() {
        assertFalse(PrivilegedAccessibilityRecovery.shouldRequest(
                OperatingMode.ASSISTANT, false, false, false, true));
    }
}
