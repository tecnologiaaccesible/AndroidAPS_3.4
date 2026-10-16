package app.aaps.pump.ypsopump.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Public HChaCha20 test vector from draft-irtf-cfrg-xchacha. */
class HChaCha20LabTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun matchesPublishedHChaCha20Vector() {
        val key = bytes("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = bytes("000000090000004a0000000031415927")
        val expected = bytes("82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc")
        assertArrayEquals(expected, HChaCha20Lab.derive(key, nonce))
    }

    @Test fun rejectsIncorrectInputLengths() {
        for ((keySize, nonceSize) in listOf(31 to 16, 33 to 16, 32 to 15, 32 to 17)) {
            var failed = false
            try {
                HChaCha20Lab.derive(ByteArray(keySize), ByteArray(nonceSize))
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertEquals(true, failed)
        }
    }

    @Test fun keepsCallerInputsUnmodified() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(16) { (it + 4).toByte() }
        val originalKey = key.copyOf()
        val originalNonce = nonce.copyOf()
        HChaCha20Lab.derive(key, nonce)
        assertArrayEquals(originalKey, key)
        assertArrayEquals(originalNonce, nonce)
    }
}
