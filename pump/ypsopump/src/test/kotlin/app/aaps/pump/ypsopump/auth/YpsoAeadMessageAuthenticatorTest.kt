package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoAeadMessageAuthenticatorTest {
    @Test fun acceptsValidFixtureAndRejectsTamperingOrWrongSession() {
        val auth = YpsoAeadMessageAuthenticator(ByteArray(32) { it.toByte() }, ByteArray(16) { (it + 20).toByte() })
        val sealed = auth.encodeFixture("test-session", 1L, "status".toByteArray())
        val payload = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        assertTrue(auth.verify("test-session", 1L, payload, tag))
        assertFalse(auth.verify("different", 1L, payload, tag))
        assertFalse(auth.verify("test-session", 2L, payload, tag))
        assertFalse(auth.verify("test-session", 1L, payload, tag.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }))
        assertFalse(auth.verify("test-session", 1L, payload.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }, tag))
    }

    @Test fun pipelineRejectsReplayWithAeadVerifier() {
        val link = Any()
        val auth = YpsoAeadMessageAuthenticator(ByteArray(32) { it.toByte() }, ByteArray(16) { it.toByte() })
        val pipeline = YpsoVerifiedReceivePipeline(auth) { link }
        pipeline.beginExternallyVerifiedSession("session", link)
        val sealed = auth.encodeFixture("session", 5L, "read-only".toByteArray())
        val payload = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        assertTrue(pipeline.accept("session", 5L, payload, tag))
        assertFalse(pipeline.accept("session", 5L, payload, tag))
        pipeline.invalidate()
        assertFalse(pipeline.accept("session", 6L, payload, tag))
    }
}
