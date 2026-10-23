package app.aaps.pump.ypsopump.ble

import java.util.UUID

/**
 * Correlates a diagnostic GATT read callback with the one allowlisted read
 * actually requested. This is transport bookkeeping, not pump authentication.
 * It never accepts protected status/history or authorizes a write.
 */
internal class YpsoGattReadResponseGate {
    private var expectedUuid: UUID? = null

    @Synchronized
    fun beginRead(uuid: UUID): Boolean {
        if (expectedUuid != null || uuid !in YpsoBleUuids.readableCharacteristics) return false
        expectedUuid = uuid
        return true
    }

    @Synchronized
    fun consume(uuid: UUID): Boolean {
        if (expectedUuid != uuid) return false
        expectedUuid = null
        return true
    }

    @Synchronized
    fun reset() {
        expectedUuid = null
    }
}
