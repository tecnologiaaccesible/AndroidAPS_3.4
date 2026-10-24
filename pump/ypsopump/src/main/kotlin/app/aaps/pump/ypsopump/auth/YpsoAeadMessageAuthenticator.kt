package app.aaps.pump.ypsopump.auth

import app.aaps.pump.ypsopump.crypto.XChaCha20Poly1305Lab
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * OFFLINE ONLY: connect an AEAD integrity check to YpsoVerifiedReceivePipeline.
 *
 * The framing below is an internal test format, NOT a claim about the YpsoPump
 * wire protocol. Do not construct a live authenticator until a manufacturer-
 * authorized handshake has established peer identity, session keys and the
 * exact nonce/AAD format. Never use this to enable pump administration.
 *
 * The 24-byte nonce must be generated uniquely under the authenticated session
 * key by a trusted transport; it is supplied here as a fixed session prefix
 * (16 bytes) followed by the unsigned little-endian frame sequence (8 bytes).
 * AEAD associated data binds session ID and sequence unambiguously.
 */
class YpsoAeadMessageAuthenticator(
    sessionKey: ByteArray,
    noncePrefix: ByteArray
) : YpsoVerifiedReceivePipeline.MessageAuthenticator {
    private val key = sessionKey.copyOf()
    private val prefix = noncePrefix.copyOf()

    init {
        require(key.size == 32)
        require(prefix.size == 16)
    }

    override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
        if (sessionId.isBlank() || sequence <= 0L || tag.size != 16) return false
        val nonce = prefix + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(sequence).array()
        val aad = associatedData(sessionId, sequence)
        return try {
            XChaCha20Poly1305Lab.open(key, nonce, payload + tag, aad)
            true
        } catch (_: Exception) {
            false
        } finally {
            nonce.fill(0)
            aad.fill(0)
        }
    }

    /** Test-only fixture encoder; not a pump command or live transport frame. */
    internal fun encodeFixture(sessionId: String, sequence: Long, plaintext: ByteArray): ByteArray {
        require(sequence > 0 && sessionId.isNotBlank())
        val nonce = prefix + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(sequence).array()
        val aad = associatedData(sessionId, sequence)
        return try {
            XChaCha20Poly1305Lab.seal(key, nonce, plaintext, aad)
        } finally {
            nonce.fill(0)
            aad.fill(0)
        }
    }

    private fun associatedData(id: String, sequence: Long): ByteArray {
        val encoded = id.toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= 4096)
        return byteArrayOf(0x59, 0x50, 0x53, 0x4f) +
            ByteBuffer.allocate(4 + encoded.size + 8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(encoded.size).put(encoded).putLong(sequence).array()
    }
}
