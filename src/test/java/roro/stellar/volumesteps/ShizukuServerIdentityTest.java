package roro.stellar.volumesteps;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ShizukuServerIdentityTest {
    @Test public void acceptsOfficialRootAndAdbServers() {
        assertTrue(ShizukuServerIdentity.supported(0));
        assertTrue(ShizukuServerIdentity.supported(2000));
    }

    @Test public void rejectsSystemApplicationAndInvalidUids() {
        for (int uid : new int[] {-1, 1000, 10000, 10234, 102000}) {
            assertFalse(ShizukuServerIdentity.supported(uid));
        }
    }
}
