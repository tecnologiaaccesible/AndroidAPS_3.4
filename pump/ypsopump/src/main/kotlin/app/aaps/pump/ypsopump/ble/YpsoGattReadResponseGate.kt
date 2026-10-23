package app.aaps.pump.ypsopump.ble

import java.util.UUID

/**
 * Correlates a diagnostic GATT read callback with the one allowlisted read
 * actually requested. This is transport bookkeeping, not pump authentication.
 * It never accepts protected status/history or authorizes a write.
 */
internal class YpsoGattReadResponseGate {
    private var expectedUuid: UUID? = null
    // Monotonic per-instance request IDs prevent an old timeout from
    // cancelling a newer read of the same characteristic after reconnect.
    private var lastReadId = 0L
    private var pendingReadId: Long? = null

    @Synchronized
    fun beginRead(uuid: UUID): Boolean {
        if (expectedUuid != null || uuid !in YpsoBleUuids.readableCharacteristics ||
            lastReadId == Long.MAX_VALUE) return false
        lastReadId++
        expectedUuid = uuid
        pendingReadId = lastReadId
        return true
    }

    @Synchronized
    fun consume(uuid: UUID): Boolean {
        if (expectedUuid != uuid) return false
        expectedUuid = null
        pendingReadId = null
        return true
    }

    /** Capture before issuing the Android GATT read; never exposes data. */
    @Synchronized
    fun currentReadId(): Long? = pendingReadId

    /** An expired request is discarded, never retried on the same GATT link. */
    @Synchronized
    fun expire(readId: Long): Boolean {
        if (pendingReadId != readId) return false
        expectedUuid = null
        pendingReadId = null
        return true
    }

    @Synchronized
    fun reset() {
        expectedUuid = null
        pendingReadId = null
    }
}
