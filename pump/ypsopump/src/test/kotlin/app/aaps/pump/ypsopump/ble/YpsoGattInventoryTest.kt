package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class YpsoGattInventoryTest {

    @Test fun decodesPublishedPropertiesWithoutAssumingAuthentication() {
        assertEquals("lectura", YpsoGattInventory.propertiesText(0x02))
        assertEquals("escritura con respuesta", YpsoGattInventory.propertiesText(0x08))
        assertEquals("lectura, notificaciones", YpsoGattInventory.propertiesText(0x12))
        assertEquals("ninguna (0x0)", YpsoGattInventory.propertiesText(0))
    }

    @Test fun inventoriesUuidAndFlagsOnly() {
        val uuid = UUID.fromString("669a0c20-0008-969e-e211-fcbeb2147bc5")
        val svc = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val rendered = YpsoGattInventory.format(
            listOf(
                YpsoGattInventory.Service(
                    svc,
                    listOf(YpsoGattInventory.Characteristic(uuid, 0x08, 0))
                )
            )
        )
        assertTrue(rendered.contains("Servicios: 1"))
        assertTrue(rendered.contains(uuid.toString()))
        assertTrue(rendered.contains("escritura con respuesta"))
        assertTrue(rendered.contains("descriptores: 0"))
        assertTrue(rendered.contains("No se han enviado comandos"))
        assertFalse(rendered.contains("PIN"))
        assertFalse(rendered.contains("Clave compartida"))
    }

    @Test fun emptyInventoryIsReadable() {
        val rendered = YpsoGattInventory.format(emptyList())
        assertTrue(rendered.contains("Servicios: 0"))
    }
}
