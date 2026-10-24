package app.aaps.pump.ypsopump.ble

import java.util.UUID

/** Offline GATT metadata comparison against one observed off-body V05.02.04 pump.
 * Does not read values, issue commands, or establish authentication.
 */
object YpsoGattEvidence {
    private fun uuid(s: String) = UUID.fromString(s)
    private val referenceServices = linkedMapOf(
        uuid("00001800-0000-1000-8000-00805f9b34fb") to 4,
        uuid("00001801-0000-1000-8000-00805f9b34fb") to 1,
        uuid("fb349b5f-8000-0080-0010-0000adde0000") to 6,
        uuid("fb349b5f-8000-0080-0010-0000dec00000") to 25,
        uuid("fb349b5f-8000-0080-0010-0000efbe0000") to 10,
        uuid("fb349b5f-8000-0080-0010-0000feda0000") to 6,
        uuid("fb349b5f-8000-0080-0010-0000feda0002") to 4
    )
    // Flags directly observed on an off-body test pump, not permissions.
    private val referenceProperties = linkedMapOf(
        uuid("669a0c20-0008-969e-e211-fcbeb2147bc5") to 0x08,
        uuid("669a0c20-0008-969e-e211-fcbee28b7bc5") to 0x12,
        uuid("669a0c20-0008-969e-e211-fcbee58b7bc5") to 0x10,
        uuid("669a0c20-0008-969e-e211-fcff0000000b") to 0x08,
        uuid("669a0c20-0008-969e-e211-fcff000000ff") to 0x02,
        uuid("00002a05-0000-1000-8000-00805f9b34fb") to 0x20
    )
    private const val propertyMask = 0x3e // read, write, notify, indicate

    fun differences(services: List<YpsoGattInventory.Service>): List<String> {
        val issues = mutableListOf<String>()
        val byService = services.groupBy { it.uuid }
        for ((id, expected) in referenceServices) {
            val found = byService[id]
            when {
                found == null -> issues.add("Servicio de referencia ausente: $id")
                found.size != 1 -> issues.add("Servicio duplicado: $id")
                found.single().characteristics.size != expected ->
                    issues.add("Características en $id: ${found.single().characteristics.size}; referencia: $expected")
            }
        }
        val byCharacteristic = services.flatMap { it.characteristics }.groupBy { it.uuid }
        for ((id, expected) in referenceProperties) {
            val found = byCharacteristic[id]
            when {
                found == null -> issues.add("Característica de referencia ausente: $id")
                found.size != 1 -> issues.add("Característica duplicada: $id")
                (((found.single().properties xor expected) and propertyMask) != 0) ->
                    issues.add("Propiedades distintas en $id: " +
                        "0x${(found.single().properties and propertyMask).toString(16)}; " +
                        "referencia: 0x${expected.toString(16)}")
            }
        }
        return issues
    }

    fun format(services: List<YpsoGattInventory.Service>): String = buildString {
        appendLine("COMPARACIÓN ORIENTATIVA DE METADATOS GATT")
        appendLine("Referencia: observación fuera del cuerpo, master V05.02.04.")
        appendLine("No contiene valores, números de serie, MAC ni credenciales.")
        val issues = differences(services)
        if (issues.isEmpty()) appendLine("Sin discrepancias en los campos observados.")
        else {
            appendLine("Discrepancias de metadatos: ${issues.size}")
            issues.forEach { appendLine("- $it") }
        }
        appendLine("La coincidencia NO acredita identidad, autorización ni sesión cifrada.")
    }
}
