package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class YpsoGattEvidenceTest {
    private fun uuid(s: String) = UUID.fromString(s)

    @Test fun flagsReferenceServiceCountDifference() {
        val services = listOf(YpsoGattInventory.Service(
            uuid("fb349b5f-8000-0080-0010-0000dec00000"),
            listOf(YpsoGattInventory.Characteristic(
                uuid("00002a29-0000-1000-8000-00805f9b34fb"), 0x02, 0))
        ))
        val issues = YpsoGattEvidence.differences(services)
        assertTrue(issues.any { it.contains("referencia: 25") })
        assertTrue(issues.any { it.contains("Servicio de referencia ausente") })
    }

    @Test fun flagsReadVersusWriteContradictionWithoutSendingCommands() {
        val id = uuid("669a0c20-0008-969e-e211-fcff0000000b")
        val services = listOf(YpsoGattInventory.Service(
            uuid("fb349b5f-8000-0080-0010-0000feda0002"),
            listOf(YpsoGattInventory.Characteristic(id, 0x02, 0))
        ))
        assertTrue(YpsoGattEvidence.differences(services).any {
            it.contains(id.toString()) && it.contains("referencia: 0x8")
        })
    }

    @Test fun reportIsDeterministicAndContainsMetadataOnly() {
        val services = listOf(YpsoGattInventory.Service(
            uuid("00001801-0000-1000-8000-00805f9b34fb"),
            listOf(YpsoGattInventory.Characteristic(
                uuid("00002a05-0000-1000-8000-00805f9b34fb"), 0x20, 1))
        ))
        val report = YpsoGattEvidence.format(services)
        assertEquals(report, YpsoGattEvidence.format(services))
        assertTrue(report.contains("NO acredita identidad"))
        assertFalse(report.contains("00:11:22:33:44:55"))
        assertFalse(report.contains("SN-123456"))
    }
}
