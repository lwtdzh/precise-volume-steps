package roro.stellar.volumesteps.compatibility;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public final class InputDumpKeyStateTest {
    @Test public void parsesExactPhysicalStates() {
        var state = InputDumpKeyState.parse(dump(
                "KEY_POWER, KEY_VOLUMEDOWN", "KEY_VOLUMEUP"));
        assertEquals(Set.of("KEY_POWER", "KEY_VOLUMEDOWN"),
                state.get(InputDumpKeyState.PMIC));
        assertEquals(Set.of("KEY_VOLUMEUP"), state.get(InputDumpKeyState.GPIO));
        assertEquals(114, InputDumpKeyState.code("KEY_VOLUMEDOWN"));
        assertEquals(115, InputDumpKeyState.code("KEY_VOLUMEUP"));
        assertEquals(116, InputDumpKeyState.code("KEY_POWER"));
    }

    @Test public void rejectsMissingVirtualOrUnexpectedDevices() {
        assertThrows(IllegalArgumentException.class, () ->
                InputDumpKeyState.parse(dump("<none>", "KEY_A")));
        assertThrows(IllegalArgumentException.class, () ->
                InputDumpKeyState.parse(dump("<none>", "<none>")
                        .replace("/sys/devices/platform/gpio-keys", "/sys/devices/virtual")));
        assertThrows(IllegalArgumentException.class, () ->
                InputDumpKeyState.parse(dump("<none>", "<none>")
                        .replace("    8: gpio-keys\n", "")));
    }

    @Test public void emitsExactEdgesAndReleasesTrackedHolds() {
        var reported = new LinkedHashSet<String>();
        var none = InputDumpKeyState.parse(dump("<none>", "<none>"));
        var power = InputDumpKeyState.parse(dump("KEY_POWER", "<none>"));
        var down = InputDumpKeyState.parse(dump("KEY_VOLUMEDOWN", "<none>"));
        var up = InputDumpKeyState.parse(dump("<none>", "KEY_VOLUMEUP"));

        var powerDown = InputDumpKeyState.initial(power, reported, 10, 20);
        assertEquals(1, powerDown.size());
        assertEquals(new InputDumpKeyState.Edge("mtk-pmic-keys", 116, 1, 10, 20),
                powerDown.get(0));
        assertEquals(116, InputDumpKeyState.changes(power, none, reported, 20, 30)
                .get(0).code());
        assertEquals(114, InputDumpKeyState.changes(none, down, reported, 30, 40)
                .get(0).code());
        assertEquals(114, InputDumpKeyState.releases(reported, 50).get(0).code());
        assertTrue(reported.isEmpty());
        assertEquals(115, InputDumpKeyState.changes(none, up, reported, 60, 70)
                .get(0).code());

        reported.clear();
        var initialVolume = InputDumpKeyState.initial(down, reported, 80, 90);
        assertTrue(initialVolume.isEmpty());
        assertEquals(114, InputDumpKeyState.releases(reported, 100).get(0).code());
    }

    private static String dump(String pmic, String gpio) {
        return """
                INPUT MANAGER
                Event Hub State:
                  Devices:
                    7: mtk-pmic-keys
                      Path: /dev/input/event1
                      Enabled: true
                      SysfsDevicePath: /sys/devices/platform/pmic/mtk-pmic-keys
                      KeyState (pressed): %s
                    8: gpio-keys
                      Path: /dev/input/event0
                      Enabled: true
                      SysfsDevicePath: /sys/devices/platform/gpio-keys
                      KeyState (pressed): %s
                Input Reader State (Nums of device: 2):
                """.formatted(pmic, gpio);
    }
}
