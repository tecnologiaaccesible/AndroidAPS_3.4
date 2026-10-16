package app.aaps.pump.ypsopump.crypto

/**
 * Independent HChaCha20 core, intended for deterministic public-vector tests.
 * This is NOT XChaCha20-Poly1305 AEAD, YpsoPump authentication or pump control.
 * No key storage, Bluetooth, network, nonce generator or production session use.
 */
object HChaCha20Lab {
    private fun rotl(x: Int, n: Int) = (x shl n) or (x ushr (32 - n))

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = rotl(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = rotl(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = rotl(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = rotl(x[b] xor x[c], 7)
    }

    private fun leInt(bytes: ByteArray, start: Int): Int =
        (bytes[start].toInt() and 0xff) or
            ((bytes[start + 1].toInt() and 0xff) shl 8) or
            ((bytes[start + 2].toInt() and 0xff) shl 16) or
            ((bytes[start + 3].toInt() and 0xff) shl 24)

    private fun putLeInt(bytes: ByteArray, start: Int, value: Int) {
        for (i in 0..3) bytes[start + i] = (value ushr (i * 8)).toByte()
    }

    /** HChaCha20: 32-byte key + 16-byte input => 32-byte output. */
    fun derive(key: ByteArray, input: ByteArray): ByteArray {
        require(key.size == 32) { "HChaCha20 key must be 32 bytes" }
        require(input.size == 16) { "HChaCha20 input must be 16 bytes" }
        val x = intArrayOf(
            0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
            leInt(key, 0), leInt(key, 4), leInt(key, 8), leInt(key, 12),
            leInt(key, 16), leInt(key, 20), leInt(key, 24), leInt(key, 28),
            leInt(input, 0), leInt(input, 4), leInt(input, 8), leInt(input, 12)
        )
        repeat(10) {
            quarter(x, 0, 4, 8, 12)
            quarter(x, 1, 5, 9, 13)
            quarter(x, 2, 6, 10, 14)
            quarter(x, 3, 7, 11, 15)
            quarter(x, 0, 5, 10, 15)
            quarter(x, 1, 6, 11, 12)
            quarter(x, 2, 7, 8, 13)
            quarter(x, 3, 4, 9, 14)
        }
        val out = ByteArray(32)
        val indices = intArrayOf(0, 1, 2, 3, 12, 13, 14, 15)
        for (i in indices.indices) putLeInt(out, i * 4, x[indices[i]])
        x.fill(0)
        return out
    }
}
