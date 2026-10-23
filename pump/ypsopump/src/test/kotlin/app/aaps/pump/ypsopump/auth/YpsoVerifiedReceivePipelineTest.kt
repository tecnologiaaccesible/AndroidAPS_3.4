package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

    @Test fun verifierExceptionFailsClosedWithoutConsumingCounter() {
        var shouldThrow = true
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
                if (shouldThrow) throw IllegalStateException("simulated verifier failure")
                return true
            }
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier)
        pipeline.beginExternallyVerifiedSession("session-1")
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        shouldThrow = false
        assertTrue(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }
    @Test fun rejectsMalformedFramesBeforeInvokingVerifier() {
        var verifierCalls = 0
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
                verifierCalls++
                return true
            }
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier)
        val payload = byteArrayOf(1)
        val tag = byteArrayOf(2)
        assertFalse(pipeline.accept("session-1", 1, payload, tag)) // No active session
        pipeline.beginExternallyVerifiedSession("session-1")
        assertFalse(pipeline.accept("", 1, payload, tag))
        assertFalse(pipeline.accept("session-2", 1, payload, tag))
        assertFalse(pipeline.accept("session-1", 0, payload, tag))
        assertFalse(pipeline.accept("session-1", -1, payload, tag))
        assertFalse(pipeline.accept("session-1", 1, payload, byteArrayOf()))
        assertEquals(0, verifierCalls)
        assertTrue(pipeline.accept("session-1", 1, payload, tag))
        assertEquals(1, verifierCalls)
        pipeline.invalidate()
        assertFalse(pipeline.accept("session-1", 2, payload, tag))
        assertEquals(1, verifierCalls)
    }

    @Test fun verifierReceivesDefensiveCopiesOfPayloadAndTag() {
        val payload = byteArrayOf(1, 2, 3)
        val tag = byteArrayOf(4, 5, 6)
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, receivedPayload: ByteArray, receivedTag: ByteArray): Boolean {
                assertFalse(payload === receivedPayload)
                assertFalse(tag === receivedTag)
                receivedPayload[0] = 99
                receivedTag[0] = 98
                return true
            }
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier)
        pipeline.beginExternallyVerifiedSession("session-1")
        assertTrue(pipeline.accept("session-1", 1, payload, tag))
        assertArrayEquals(byteArrayOf(1, 2, 3), payload)
        assertArrayEquals(byteArrayOf(4, 5, 6), tag)
    }

    @Test fun rejectsNonIncreasingAndOverflowedCountersAtLongMaximum() {
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = true
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier)
        pipeline.beginExternallyVerifiedSession("session-1")
        val payload = byteArrayOf(1)
        val tag = byteArrayOf(1)
        assertTrue(pipeline.accept("session-1", Long.MAX_VALUE - 1, payload, tag))
        assertTrue(pipeline.accept("session-1", Long.MAX_VALUE, payload, tag))
        assertFalse(pipeline.accept("session-1", Long.MAX_VALUE, payload, tag))
        assertFalse(pipeline.accept("session-1", Long.MAX_VALUE - 1, payload, tag))
        assertFalse(pipeline.accept("session-1", Long.MIN_VALUE, payload, tag))
        assertFalse(pipeline.accept("session-1", 0, payload, tag))
    }

}
