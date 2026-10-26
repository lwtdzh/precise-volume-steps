package roro.stellar.volumesteps;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class VolumeDisplayTest {
    @Test public void normalizesOneHundredFiftyNativeSteps() {
        assertEquals(50, VolumeDisplay.percent(75, 150));
        assertEquals(20, VolumeDisplay.percent(30, 150));
        assertEquals(49, VolumeDisplay.percent(73, 150));
        assertEquals(100, VolumeDisplay.percent(150, 150));
    }

    @Test public void usesSameScaleForDifferentNativeRanges() {
        assertEquals(60, VolumeDisplay.percent(9, 15));
        assertEquals(60, VolumeDisplay.percent(90, 150));
        assertEquals(60, VolumeDisplay.percent(60, 100));
        assertEquals(60, VolumeDisplay.percent(30, 50));
        assertEquals(50, VolumeDisplay.percent(25, 50));
        assertEquals(100, VolumeDisplay.percent(50, 50));
        assertEquals(0, VolumeDisplay.percent(0, 50));
        assertEquals(50, VolumeDisplay.percent(128, 255));
    }

    @Test public void showsPositionWithinConfiguredStepCount() {
        assertEquals(15, VolumeDisplay.settingLevel(75, 0, 150, 30));
        assertEquals(75, VolumeDisplay.settingLevel(75, 0, 150, 150));
        assertEquals(3, VolumeDisplay.settingLevel(9, 0, 15, 5));
        assertEquals(0, VolumeDisplay.settingLevel(1, 1, 10, 30));
    }

    @Test public void handlesMissingRangeAndOutOfRangeValues() {
        assertEquals(0, VolumeDisplay.percent(1, 0));
        assertEquals(0, VolumeDisplay.percent(-1, 150));
        assertEquals(100, VolumeDisplay.percent(151, 150));
    }
}
