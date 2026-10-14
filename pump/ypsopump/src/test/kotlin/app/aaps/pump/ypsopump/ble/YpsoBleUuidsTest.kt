package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoBleUuidsTest {
    @Test fun acceptsResearchDeviceNameVariants() {
        assertTrue(YpsoBleUuids.isCandidateName("YpsoPump_123"))
        assertTrue(YpsoBleUuids.isCandidateName("mylife YpsoPump"))
        assertTrue(YpsoBleUuids.isCandidateName("ypsopump test"))
    }

    @Test fun rejectsUnrelatedOrMissingDevices() {
        assertFalse(YpsoBleUuids.isCandidateName(null))
        assertFalse(YpsoBleUuids.isCandidateName(""))
        assertFalse(YpsoBleUuids.isCandidateName("Other BLE device"))
    }

    @Test fun allReadOnlyCharacteristicsHaveDistinctIdentifiers() {
        assertEquals(9, YpsoBleUuids.readableCharacteristics.size)
    }
}
