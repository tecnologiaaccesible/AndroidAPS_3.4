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

    @Test fun formatsPumpFirmwareAsReadableText() {
        val firmware = byteArrayOf(86, 48, 53, 46, 48, 50, 46, 48, 52, 0)
        assertEquals("V05.02.04", YpsoBleUuids.formatReadOnlyValue(firmware))
        assertEquals("1.7", YpsoBleUuids.formatReadOnlyValue(byteArrayOf(49, 46, 55, 0)))
    }

    @Test fun preservesUnexpectedBinaryDataAsNumbers() {
        assertEquals("Sin datos", YpsoBleUuids.formatReadOnlyValue(byteArrayOf()))
        assertEquals("0.255.10", YpsoBleUuids.formatReadOnlyValue(byteArrayOf(0, -1, 10)))
    }

    @Test fun allReadOnlyCharacteristicsHaveDistinctIdentifiers() {
        assertEquals(9, YpsoBleUuids.readableCharacteristics.size)
    }
}
