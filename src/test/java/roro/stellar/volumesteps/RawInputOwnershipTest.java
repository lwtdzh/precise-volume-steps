package roro.stellar.volumesteps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RawInputOwnershipTest {
    @Test public void shizukuOwnsUnlockedAndLockedKeysWhenReady() {
        assertTrue(RawInputOwnership.owns(
                OperatingMode.SHIZUKU, OperatingMode.SHIZUKU, true, false));
        assertTrue(RawInputOwnership.owns(
                OperatingMode.SHIZUKU, OperatingMode.SHIZUKU, true, true));
    }

    @Test public void rootOwnsUnlockedAndLockedKeysWhenReady() {
        assertTrue(RawInputOwnership.owns(
                OperatingMode.ROOT, OperatingMode.ROOT, true, false));
        assertTrue(RawInputOwnership.owns(
                OperatingMode.ROOT, OperatingMode.ROOT, true, true));
    }

    @Test public void assistantRawInputRemainsLockedOrScreenOffOnly() {
        assertFalse(RawInputOwnership.owns(
                OperatingMode.ASSISTANT, OperatingMode.ASSISTANT, true, false));
        assertTrue(RawInputOwnership.owns(
                OperatingMode.ASSISTANT, OperatingMode.ASSISTANT, true, true));
    }

    @Test public void unreadyAndUnselectedSourcesNeverOwnKeys() {
        assertFalse(RawInputOwnership.owns(
                OperatingMode.SHIZUKU, OperatingMode.SHIZUKU, false, false));
        assertFalse(RawInputOwnership.owns(
                OperatingMode.SHIZUKU, OperatingMode.ROOT, true, false));
        assertFalse(RawInputOwnership.owns(
                OperatingMode.ROOT, OperatingMode.SHIZUKU, true, true));
    }
}
