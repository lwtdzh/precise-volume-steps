package roro.stellar.volumesteps;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RawVolumeKeyTest {
    @Test public void discoversOnlyDedicatedVolumeNodes() {
        String text = """
                add device 1: /dev/input/event0
                  KEY (0001): KEY_VOLUMEUP
                add device 2: /dev/input/event1
                  KEY (0001): KEY_VOLUMEDOWN KEY_POWER
                add device 3: /dev/input/event2
                  KEY (0001): KEY_VOLUMEUP KEY_VOLUMEDOWN
                  SW  (0005): SW_HEADPHONE_INSERT
                add device 4: /dev/input/event3
                  KEY (0001): KEY_VOLUMEUP KEY_A
                add device 5: /dev/input/event4
                  KEY (0001): KEY_VOLUMEDOWN
                  ABS  (0003): ABS_MT_POSITION_X
                add device 6: /dev/input/event5
                  KEY (0001): KEY_VOLUMEDOWN
                  REL (0002): REL_X
                add device 7: /dev/input/event6
                  KEY (0001): KEY_POWER
                add device 8: /dev/input/event7
                  KEY (0001): KEY_POWER KEY_WAKEUP
                """;
        assertEquals(List.of("/dev/input/event0", "/dev/input/event1", "/dev/input/event6"),
                RawVolumeKeyParser.nodes(text));
    }

    @Test public void powerOnlyDeviceCannotAdvertiseVolumeInputReadiness() {
        assertTrue(RawVolumeKeyParser.nodes("""
                add device 1: /dev/input/event0
                  KEY (0001): KEY_POWER
                """).isEmpty());
    }

    @Test public void parsesOnlyExplicitVolumeAndPowerEvents() {
        var event = RawVolumeKeyParser.event("[  123.456789] EV_KEY KEY_VOLUMEDOWN DOWN");
        assertNotNull(event);
        assertEquals(114, event.code());
        assertEquals(1, event.value());
        assertEquals(123456, event.time());
        assertEquals(0, RawVolumeKeyParser.event("[123.499999] EV_KEY KEY_VOLUMEUP UP").value());
        assertEquals(2, RawVolumeKeyParser.event("[123.499999] EV_KEY KEY_POWER REPEAT").value());
        assertNull(RawVolumeKeyParser.event("[123.499999] EV_KEY KEY_A DOWN"));
        assertNull(RawVolumeKeyParser.event("[123.499999] EV_ABS ABS_MT_POSITION_X 00000010"));
        assertNull(RawVolumeKeyParser.event("could not open /dev/input/event1"));
    }

    @Test public void releasesAndRepeatsAreCountedOnce() {
        RawVolumePress press = new RawVolumePress();
        assertEquals(0, press.up("volume", 114, 1040, 1041));
        assertTrue(press.down("volume", 114, 1000, 1001, true));
        assertEquals(1, press.up("volume", 114, 1040, 1041));
        assertEquals(0, press.up("volume", 114, 1040, 1041));
        assertTrue(press.down("volume", 115, 2000, 2001, true));
        press.repeat("volume", 115);
        press.repeat("volume", 115);
        assertEquals(3, press.up("volume", 115, 2700, 2701));
    }

    @Test public void powerChordsAndStaleOrUnmatchedKeysCannotAuthorizeWrites() {
        RawVolumePress press = new RawVolumePress();
        assertFalse(press.down("volume", 114, 1000, 1300, true));
        assertFalse(press.down("volume", 114, 1000, 1001, false));
        assertTrue(press.down("volume", 114, 2000, 2001, true));
        assertEquals(0, press.up("other", 114, 2040, 2041));
        press.down("power", 116, 2010, 2011, false);
        assertEquals(0, press.up("volume", 114, 2040, 2041));
        assertFalse(press.down("volume", 114, 2100, 2101, true));
        press.up("power", 116, 2200, 2201);
        assertTrue(press.down("volume", 114, 2300, 2301, true));
        assertEquals(0, press.up("volume", 114, 2340, 2700));
        assertTrue(press.down("volume", 114, 3000, 3001, true));
        press.cancel();
        assertEquals(0, press.up("volume", 114, 3040, 3041));
    }

    @Test public void simultaneousVolumeKeysCancelAndLongHoldIsBounded() {
        RawVolumePress press = new RawVolumePress();
        assertTrue(press.down("up", 115, 1000, 1001, true));
        assertFalse(press.down("down", 114, 1010, 1011, true));
        assertEquals(0, press.up("up", 115, 1040, 1041));
        assertTrue(press.down("up", 115, 2000, 2001, true));
        assertEquals(4, press.up("up", 115, 2700, 2701));
        assertTrue(press.down("up", 115, 3000, 3001, true));
        assertEquals(0, press.up("up", 115, 14000, 14001));
    }
}
