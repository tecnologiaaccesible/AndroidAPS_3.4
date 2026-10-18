package app.aaps.pump.ypsopump.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class XChaCha20Poly1305LabTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(24) { (it + 31).toByte() }
    private val aad = "Ypso offline fixture".toByteArray()
    private val message = "No physical pump commands".toByteArray()

    @Test fun authenticatesRoundTripAndChangesCiphertextWhenNonceChanges() {
        val sealed = XChaCha20Poly1305Lab.seal(key, nonce, message, aad)
        assertFalse(sealed.copyOfRange(0, message.size).contentEquals(message))
        assertArrayEquals(message, XChaCha20Poly1305Lab.open(key, nonce, sealed, aad))
        val otherNonce = nonce.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        assertFalse(sealed.contentEquals(XChaCha20Poly1305Lab.seal(key, otherNonce, message, aad)))
    }

    @Test fun rejectsModifiedCiphertextTagAadAndNonce() {
        val sealed = XChaCha20Poly1305Lab.seal(key, nonce, message, aad)
        fun mustFail(block: () -> Unit) {
            var failed = false
            try { block() } catch (_: GeneralSecurityException) { failed = true }
            assertTrue("Tampering must be rejected", failed)
        }
        mustFail {
            val corrupted = sealed.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
            XChaCha20Poly1305Lab.open(key, nonce, corrupted, aad)
        }
        mustFail {
            val corrupted = sealed.copyOf().apply {
                val last = lastIndex
                this[last] = (this[last].toInt() xor 1).toByte()
            }
            XChaCha20Poly1305Lab.open(key, nonce, corrupted, aad)
        }
        mustFail { XChaCha20Poly1305Lab.open(key, nonce, sealed, "wrong AAD".toByteArray()) }
        mustFail {
            val wrongNonce = nonce.copyOf().apply { this[23] = (this[23].toInt() xor 1).toByte() }
            XChaCha20Poly1305Lab.open(key, wrongNonce, sealed, aad)
        }
    }

    @Test fun rejectsInvalidParameterSizes() {
        var failed = false
        try { XChaCha20Poly1305Lab.seal(ByteArray(31), nonce, message, aad) }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        failed = false
        try { XChaCha20Poly1305Lab.open(key, nonce, ByteArray(15), aad) }
        catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
