package roro.stellar.volumesteps;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PermissionGuideStateTest {
    @Test public void screenOffHelperGrantDoesNotBlockOnScreenReadiness() {
        PermissionGuideState state = new PermissionGuideState(
                true, true, true, true, true, false);
        assertTrue(state.coreReady());
        assertFalse(state.helperOffDisplayReady());
    }

    @Test public void missingCorePermissionIsReportedIndependentlyOfHelper() {
        PermissionGuideState state = new PermissionGuideState(
                false, true, true, true, true, true);
        assertFalse(state.coreReady());
        assertTrue(state.helperOffDisplayReady());
    }

    @Test public void absentHelperRemainsOptional() {
        PermissionGuideState state = new PermissionGuideState(
                true, true, true, true, false, false);
        assertTrue(state.coreReady());
        assertFalse(state.helperOffDisplayReady());
    }
}
