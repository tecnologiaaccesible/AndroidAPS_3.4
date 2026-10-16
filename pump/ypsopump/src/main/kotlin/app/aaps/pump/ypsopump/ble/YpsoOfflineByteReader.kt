package app.aaps.pump.ypsopump.ble

/**
 * Bounds-checked binary reading for offline protocol research fixtures.
 *
 * Endianness must be chosen explicitly for each field. This code does not
 * decrypt, transmit, parse real pump commands, or grant access to hardware.
 */
object YpsoOfflineByteReader {
    fun u16Le(bytes: ByteArray, offset: Int): Int {
        requireRange(bytes, offset, 2)
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    fun u32Le(bytes: ByteArray, offset: Int): Long {
        requireRange(bytes, offset, 4)
        return (0 until 4).fold(0L) { value, i ->
            value or ((bytes[offset + i].toLong() and 0xffL) shl (i * 8))
        }
    }

    fun u32Be(bytes: ByteArray, offset: Int): Long {
        requireRange(bytes, offset, 4)
        return (0 until 4).fold(0L) { value, i ->
            (value shl 8) or (bytes[offset + i].toLong() and 0xffL)
        }
    }

    fun u64Be(bytes: ByteArray, offset: Int): ULong {
        requireRange(bytes, offset, 8)
        return (0 until 8).fold(0uL) { value, i ->
            (value shl 8) or bytes[offset + i].toUByte().toULong()
        }
    }

    fun slice(bytes: ByteArray, offset: Int, size: Int): ByteArray {
        requireRange(bytes, offset, size)
        return bytes.copyOfRange(offset, offset + size)
    }

    private fun requireRange(bytes: ByteArray, offset: Int, size: Int) {
        require(offset >= 0 && size >= 0 && offset <= bytes.size && size <= bytes.size - offset) {
            "Invalid byte range"
        }
    }
}
