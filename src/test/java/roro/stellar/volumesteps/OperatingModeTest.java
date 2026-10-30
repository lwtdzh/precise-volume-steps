package roro.stellar.volumesteps;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class OperatingModeTest {
    @Test public void preservesSelectedBackendDuringUpgrade() {
        assertEquals(OperatingMode.ASSISTANT, OperatingMode.stored(null, false));
        assertEquals(OperatingMode.SHIZUKU, OperatingMode.stored(null, true));
        assertEquals(OperatingMode.ROOT, OperatingMode.stored("ROOT", true));
        assertEquals(OperatingMode.ASSISTANT, OperatingMode.stored("ASSISTANT", true));
        assertEquals(OperatingMode.SHIZUKU, OperatingMode.stored("invalid", true));
    }
}
