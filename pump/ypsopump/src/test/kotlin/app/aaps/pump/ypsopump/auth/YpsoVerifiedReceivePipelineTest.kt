package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoVerifiedReceivePipelineTest {
    @Test fun defaultAlwaysRejects() {
        val pipeline = YpsoVerifiedReceivePipeline()
        pipeline.beginExternallyVerifiedSession("session-1")
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }

    @Test fun verificationMustPrecedeReplayAdvance() {
        var verified = false
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = verified
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier)
        pipeline.beginExternallyVerifiedSession("session-1")
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        verified = true
        assertTrue(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        assertFalse(pipeline.accept("session-2", 2, byteArrayOf(1), byteArrayOf(1)))
        assertFalse(pipeline.accept("session-1", 2, byteArrayOf(1), byteArrayOf()))
        pipeline.invalidate()
        assertFalse(pipeline.accept("session-1", 2, byteArrayOf(1), byteArrayOf(1)))
    }
}
