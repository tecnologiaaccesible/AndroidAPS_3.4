package app.aaps.pump.ypsopump.ble

/**
 * Offline-only checksum experiment based on the public vicktor/ypsomed-pump
 * YpsoCrc reference. The algorithm and packet placement have not been verified
 * against this pump's firmware. This is NOT authentication or an AEAD integrity tag.
 *
 * Does not encode commands or interact with Bluetooth.
 */
object YpsoOfflineCrc {
    private const val POLYNOMIAL = 0x04C11DB7L

    fun checksum(payload: ByteArray): ByteArray {
        var crc = 0xffffffffL
        val blockCount = (payload.size + 3) / 4
        for (block in 0 until blockCount) {
            for (position in 0..3) {
                val src = block * 4 + (3 - position)
                val byte = if (src < payload.size) payload[src].toInt() and 0xff else 0
                crc = crc xor (byte.toLong() shl 24)
                repeat(8) {
                    crc = if ((crc and 0x80000000L) != 0L) {
                        ((crc shl 1) and 0xffffffffL) xor POLYNOMIAL
                    } else {
                        (crc shl 1) and 0xffffffffL
                    }
                }
            }
        }
        return byteArrayOf((crc and 0xff).toByte(), ((crc ushr 8) and 0xff).toByte())
    }

    fun verify(frame: ByteArray): Boolean {
        if (frame.size < 2) return false
        val payload = frame.copyOfRange(0, frame.size - 2)
        val expected = checksum(payload)
        return expected[0] == frame[frame.size - 2] && expected[1] == frame[frame.size - 1]
    }
}
