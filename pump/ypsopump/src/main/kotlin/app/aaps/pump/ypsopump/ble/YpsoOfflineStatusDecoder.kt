package app.aaps.pump.ypsopump.ble

/**
 * Research-only decoder for the first status byte described in
 * vicktor/ypsomed-pump YpsoProtocolConstants.kt.
 * Never interprets a status as authorization to control a physical pump.
 */
object YpsoOfflineStatusDecoder {
    enum class DeliveryMode {
        STOPPED, BASAL, TEMPORARY_BASAL, FAST_BOLUS, EXTENDED_BOLUS,
        BOLUS_AND_BASAL, PRIMING, PAUSED, UNKNOWN
    }

    data class Status(val rawCode: Int, val mode: DeliveryMode)

    fun decode(payload: ByteArray): Status {
        require(payload.isNotEmpty()) { "Empty status message" }
        val code = payload[0].toInt() and 0xff
        val mode = when (code) {
            0 -> DeliveryMode.STOPPED
            1 -> DeliveryMode.BASAL
            2 -> DeliveryMode.TEMPORARY_BASAL
            3 -> DeliveryMode.FAST_BOLUS
            4 -> DeliveryMode.EXTENDED_BOLUS
            5 -> DeliveryMode.BOLUS_AND_BASAL
            6 -> DeliveryMode.PRIMING
            7 -> DeliveryMode.PAUSED
            else -> DeliveryMode.UNKNOWN
        }
        return Status(code, mode)
    }
}
