package roro.stellar.volumesteps;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public final class CompatibilityGrantContractTest {
    @Test public void exposesOnlyTheFixedHelperDumpGrant() {
        assertEquals(
                "adb shell pm grant com.vivo.finevolumesteps.helper android.permission.DUMP",
                CompatibilityGrantContract.ADB_COMMAND);
        assertArrayEquals(new String[]{
                "/system/bin/pm",
                "grant",
                "com.vivo.finevolumesteps.helper",
                "android.permission.DUMP"
        }, CompatibilityGrantContract.shellCommand());
    }

    @Test public void callersCannotMutateLaterShellCommands() {
        String[] first = CompatibilityGrantContract.shellCommand();
        first[2] = "caller.supplied.package";
        String[] second = CompatibilityGrantContract.shellCommand();
        assertNotSame(first, second);
        assertEquals("com.vivo.finevolumesteps.helper", second[2]);
    }
}
