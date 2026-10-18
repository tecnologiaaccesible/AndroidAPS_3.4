package app.aaps.pump.ypsopump.crypto

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Offline XChaCha20-Poly1305 test implementation following the XChaCha draft:
 * HChaCha20(key, nonce[0..15]) and IETF ChaCha20-Poly1305 with nonce
 * 0x00000000 || nonce[16..23].
 *
 * Requires a runtime JCA provider offering ChaCha20-Poly1305.
 * Neither Android availability nor on-pump interoperability is established.
 * Not connected to Bluetooth, app authorization, or insulin commands.
 */
object XChaCha20Poly1305Lab {
    private const val KEY_SIZE = 32
    private const val NONCE_SIZE = 24
    private const val TAG_SIZE = 16

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        require(key.size == KEY_SIZE) { "Expected a 32-byte key" }
        require(nonce.size == NONCE_SIZE) { "Expected a 24-byte nonce" }
        val subKey = HChaCha20Lab.derive(key, nonce.copyOfRange(0, 16))
        val ietfNonce = ByteArray(12)
        nonce.copyInto(ietfNonce, destinationOffset = 4, startIndex = 16, endIndex = 24)
        return try {
            Cipher.getInstance("ChaCha20-Poly1305").apply {
                init(mode, SecretKeySpec(subKey, "ChaCha20"), IvParameterSpec(ietfNonce))
                updateAAD(aad)
            }
        } finally {
            subKey.fill(0)
            ietfNonce.fill(0)
        }
    }

    /** Caller must guarantee nonce uniqueness for each key; no nonce generation here. */
    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    /** Throws GeneralSecurityException for bad authentication tags. */
    @Throws(GeneralSecurityException::class)
    fun open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        require(ciphertextAndTag.size >= TAG_SIZE) { "Authentication tag missing" }
        return cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertextAndTag)
    }
}
