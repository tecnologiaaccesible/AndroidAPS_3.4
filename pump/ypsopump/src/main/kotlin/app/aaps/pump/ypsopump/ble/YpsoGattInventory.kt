package app.aaps.pump.ypsopump.ble

import java.util.UUID

/**
 * GATT metadata inventory. Does not read characteristic values, write commands,
 * subscribe to notifications or disclose Bluetooth addresses / pump serial numbers.
 *
 * Flags are capability declarations only: they do NOT prove authorization.
 */
object YpsoGattInventory {

    data class Characteristic(
        val uuid: UUID,
        val properties: Int,
        val descriptorCount: Int
    )

    data class Service(
        val uuid: UUID,
        val characteristics: List<Characteristic>
    )

    fun propertiesText(properties: Int): String {
        val flags = listOf(
            0x01 to "difusión",
            0x02 to "lectura",
            0x04 to "escritura sin respuesta",
            0x08 to "escritura con respuesta",
            0x10 to "notificaciones",
            0x20 to "indicaciones",
            0x40 to "escritura firmada",
            0x80 to "propiedades extendidas"
        ).filter { (bit, _) -> properties and bit != 0 }
            .map { (_, name) -> name }
        return if (flags.isEmpty()) "ninguna (0x${properties.toString(16)})"
               else flags.joinToString(", ")
    }

    fun format(services: List<Service>): String = buildString {
        appendLine("MAPA GATT: metadatos únicamente (sin leer valores)")
        appendLine("Servicios: ${services.size}")
        appendLine("Las propiedades no implican autenticación ni permiso efectivo.")
        services.sortedBy { it.uuid.toString() }.forEachIndexed { index, service ->
            appendLine()
            appendLine("Servicio ${index + 1}: ${service.uuid}")
            appendLine("Características: ${service.characteristics.size}")
            service.characteristics.sortedBy { it.uuid.toString() }.forEach { characteristic ->
                appendLine("  ${characteristic.uuid}")
                appendLine("    ${propertiesText(characteristic.properties)}; descriptores: ${characteristic.descriptorCount}")
            }
        }
        appendLine()
        appendLine("FIN DEL MAPA GATT. No se han enviado comandos.")
    }
}
