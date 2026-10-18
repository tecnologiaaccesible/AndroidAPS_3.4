package app.aaps.pump.ypsopump.ble

/**
 * Strict offline-only decoder of the 20-byte BLE frame format described by
 * vicktor/ypsomed-pump YpsoFraming.kt.
 *
 * This intentionally provides no encoding, BLE writes or pump commands.
 * The external format is a research hypothesis pending verification on a pump.
 */
object YpsoOfflineFrameReader {
    const val MAX_FRAME_BYTES = 20
    const val MAX_FRAMES = 15
    const val MAX_PAYLOAD_BYTES = 19 * MAX_FRAMES

    /** Rejects missing, duplicate, reordered, oversized and truncated frames. */
    fun reassemble(frames: List<ByteArray>): ByteArray {
        require(frames.isNotEmpty() && frames.size <= MAX_FRAMES) { "Invalid frame count" }
        val first = frames.first()
        require(first.isNotEmpty()) { "Empty BLE frame" }
        val declaredCount = first[0].toInt() and 0x0f
        val isEmptySignal = frames.size == 1 && first.size == 1 && first[0] == 0x10.toByte()
        if (isEmptySignal) return ByteArray(0)
        require(declaredCount in 1..MAX_FRAMES && frames.size == declaredCount) {
            "Missing or invalid frame count"
        }
        val result = ArrayList<Byte>(minOf(declaredCount * 19, MAX_PAYLOAD_BYTES))
        for ((index, frame) in frames.withIndex()) {
            require(frame.size in 2..MAX_FRAME_BYTES) { "Invalid frame length" }
            val header = frame[0].toInt() and 0xff
            val number = header ushr 4
            val total = header and 0x0f
            require(number == index + 1 && total == declaredCount) { "Out-of-sequence BLE frame" }
            if (index != frames.lastIndex) {
                require(frame.size == MAX_FRAME_BYTES) { "Truncated non-final frame" }
            }
            for (i in 1 until frame.size) result.add(frame[i])
        }
        return result.toByteArray()
    }
}
