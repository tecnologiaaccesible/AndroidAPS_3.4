package app.aaps.pump.ypsopump.ble

/**
 * Offline-only parser of the ten-byte bolus notification fields described by
 * vicktor/ypsomed-pump YpsoProtocolConstants.kt.
 * The source reports a 13-byte wire notification; this parser deliberately
 * accepts only the verified-in-fixtures ten-byte inner structure, not raw BLE.
 * No pump connection or command dispatch.
 */
object YpsoOfflineBolusNotification {
    enum class State { IDLE, DELIVERING, CANCELLED, COMPLETED, UNKNOWN }
    data class Channel(val rawStatus: Int, val status: State, val sequence: Long)
    data class Notification(val fast: Channel, val slow: Channel)

    private fun uint32Le(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 4) result = result or ((data[offset + i].toLong() and 0xffL) shl (8 * i))
        return result
    }

    private fun channel(data: ByteArray, offset: Int): Channel {
        val raw = data[offset].toInt() and 0xff
        val state = when (raw) {
            0 -> State.IDLE
            1 -> State.DELIVERING
            3 -> State.CANCELLED
            4 -> State.COMPLETED
            else -> State.UNKNOWN
        }
        return Channel(raw, state, uint32Le(data, offset + 1))
    }

    fun decodeInnerPayload(data: ByteArray): Notification {
        require(data.size == 10) { "Expected 10-byte inner bolus notification" }
        return Notification(channel(data, 0), channel(data, 5))
    }
}
