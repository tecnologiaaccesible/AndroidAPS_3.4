package app.aaps.pump.ypsopump.ble

/**
 * Standalone deterministic fixture validator. This is NOT a YpsoPump frame
 * decoder or a cryptographic implementation. The made-up format exists only
 * to exercise limits, state isolation and failures without touching hardware.
 */
object YpsoOfflineFixtureValidator {
    const val MAX_SIMULATED_PAYLOAD = 64

    sealed class Result {
        data class Accepted(val counter: Long, val payloadSize: Int) : Result()
        data class Rejected(val reason: Reason) : Result()
    }

    enum class Reason {
        WRONG_MAGIC, TRUNCATED, LENGTH_MISMATCH, PAYLOAD_TOO_LONG, INVALID_COUNTER
    }

    /**
     * Synthetic frame: ASCII 'L','A','B', 1 unsigned payload-length byte,
     * 1 unsigned simulated counter byte, followed by length bytes.
     */
    fun validate(bytes: ByteArray): Result {
        if (bytes.size < 3 || bytes[0] != 'L'.code.toByte() ||
            bytes[1] != 'A'.code.toByte() || bytes[2] != 'B'.code.toByte()
        ) return Result.Rejected(Reason.WRONG_MAGIC)
        if (bytes.size < 5) return Result.Rejected(Reason.TRUNCATED)
        val length = bytes[3].toInt() and 0xff
        if (length > MAX_SIMULATED_PAYLOAD) return Result.Rejected(Reason.PAYLOAD_TOO_LONG)
        if (bytes.size != 5 + length) return Result.Rejected(Reason.LENGTH_MISMATCH)
        val counter = bytes[4].toInt() and 0xff
        if (counter == 0) return Result.Rejected(Reason.INVALID_COUNTER)
        return Result.Accepted(counter.toLong(), length)
    }

    fun apply(snapshot: YpsoOfflineSessionModel.Snapshot, bytes: ByteArray): YpsoOfflineSessionModel.Snapshot =
        when (val result = validate(bytes)) {
            is Result.Rejected -> YpsoOfflineSessionModel.transition(snapshot, YpsoOfflineSessionModel.Event.InvalidMessage)
            is Result.Accepted -> YpsoOfflineSessionModel.transition(
                snapshot, YpsoOfflineSessionModel.Event.SimulatedInboundCounter(result.counter)
            )
        }
}
