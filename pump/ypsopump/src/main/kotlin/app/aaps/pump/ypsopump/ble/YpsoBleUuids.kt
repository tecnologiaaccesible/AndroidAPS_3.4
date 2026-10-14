package app.aaps.pump.ypsopump.ble

import java.util.UUID

/**
 * Research UUIDs from SandraK82/ypsopump-research/ypsopump-test.
 * Device and firmware compatibility has not been validated in this AAPS integration.
 */
object YpsoBleUuids {
    val manufacturer = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val model = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val serial = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    val firmware = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val software = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

    val masterVersion = UUID.fromString("669a0c20-0008-969e-e211-fcbeb0147bc5")
    val baseVersion = UUID.fromString("669a0c20-0008-969e-e211-fcbee23b7bc5")
    val settingsVersion = UUID.fromString("669a0c20-0008-969e-e211-fcbee33b7bc5")
    val historyVersion = UUID.fromString("669a0c20-0008-969e-e211-fcbee43b7bc5")

    val readableCharacteristics = linkedMapOf(
        manufacturer to "Fabricante",
        model to "Modelo",
        serial to "Número de serie",
        firmware to "Firmware",
        software to "Software",
        masterVersion to "Versión maestra",
        baseVersion to "Versión base",
        settingsVersion to "Versión de configuración",
        historyVersion to "Versión de historial"
    )

    fun isCandidateName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.startsWith("YpsoPump_", ignoreCase = true) ||
            name.contains("YpsoPump", ignoreCase = true)
    }
}
