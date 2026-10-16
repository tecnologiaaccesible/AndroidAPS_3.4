package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoProtocolCatalogTest {

    @Test fun exactly33ResearchCommandsWithUniqueIndices() {
        val commands = YpsoProtocolCatalog.researchCommands
        assertEquals(33, commands.size)
        assertEquals((0..32).toList(), commands.map { it.index })
        assertEquals(33, commands.map { it.id }.distinct().size)
    }

    @Test fun researchGroupsHaveExpectedSizes() {
        val commands = YpsoProtocolCatalog.researchCommands
        assertEquals(5, commands.count { it.group == YpsoProtocolCatalog.Group.BASE })
        assertEquals(5, commands.count { it.group == YpsoProtocolCatalog.Group.SETTINGS })
        assertEquals(16, commands.count { it.group == YpsoProtocolCatalog.Group.HISTORY })
        assertEquals(7, commands.count { it.group == YpsoProtocolCatalog.Group.CONTROL })
    }

    @Test fun catalogDoesNotDispatchAnyPumpCommand() {
        assertTrue(YpsoProtocolCatalog.researchCommands.isNotEmpty())
        YpsoProtocolCatalog.researchCommands.forEach {
            assertFalse("Command must remain disabled: ${it.id}", YpsoProtocolCatalog.canDispatchCommand(it))
        }
    }

    @Test fun observedUuidDoesNotProveAuthentication() {
        assertEquals(
            "669a0c20-0008-969e-e211-fcbeb2147bc5",
            YpsoProtocolCatalog.observedAuthorizationUuid.toString()
        )
        assertEquals(
            YpsoProtocolCatalog.Validation.UUID_OBSERVED,
            YpsoProtocolCatalog.validationForObservedAuthorization()
        )
    }
}
