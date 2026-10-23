package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class YpsoVerifiedReceivePipelineTest {
    private val link = Any()
    private var liveLink: Any? = link
    @Test fun defaultAlwaysRejects() {
        val pipeline = YpsoVerifiedReceivePipeline(currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }

    @Test fun verificationMustPrecedeReplayAdvance() {
        var verified = false
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = verified
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
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
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
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
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        val payload = byteArrayOf(1)
        val tag = byteArrayOf(2)
        assertFalse(pipeline.accept("session-1", 1, payload, tag)) // No active session
        pipeline.beginExternallyVerifiedSession("session-1", link)
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
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        assertTrue(pipeline.accept("session-1", 1, payload, tag))
        assertArrayEquals(byteArrayOf(1, 2, 3), payload)
        assertArrayEquals(byteArrayOf(4, 5, 6), tag)
    }

    @Test fun rejectsNonIncreasingAndOverflowedCountersAtLongMaximum() {
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = true
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        val payload = byteArrayOf(1)
        val tag = byteArrayOf(1)
        assertTrue(pipeline.accept("session-1", Long.MAX_VALUE - 1, payload, tag))
        assertTrue(pipeline.accept("session-1", Long.MAX_VALUE, payload, tag))
        assertFalse(pipeline.accept("session-1", Long.MAX_VALUE, payload, tag))
        assertFalse(pipeline.accept("session-1", Long.MAX_VALUE - 1, payload, tag))
        assertFalse(pipeline.accept("session-1", Long.MIN_VALUE, payload, tag))
        assertFalse(pipeline.accept("session-1", 0, payload, tag))
    }


    @Test fun verifiedSessionCannotBeRestartedToResetReplayCounter() {
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = true
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        val payload = byteArrayOf(1)
        val tag = byteArrayOf(2)
        pipeline.beginExternallyVerifiedSession("session-1", link)
        assertTrue(pipeline.accept("session-1", 7L, payload, tag))
        var rejected = false
        try {
            pipeline.beginExternallyVerifiedSession("session-1", link)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(pipeline.accept("session-1", 7L, payload, tag))
        assertTrue(pipeline.accept("session-1", 8L, payload, tag))

        pipeline.invalidate()
        rejected = false
        try {
            pipeline.beginExternallyVerifiedSession("session-2", link)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(pipeline.accept("session-2", 1L, payload, tag))

        // The caller must independently verify unique session/key binding.
        val freshPipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        freshPipeline.beginExternallyVerifiedSession("session-2", link)
        assertTrue(freshPipeline.accept("session-2", 1L, payload, tag))
    }

    @Test fun missingTrustedLinkSourceCannotArmSession() {
        val pipeline = YpsoVerifiedReceivePipeline()
        var rejected = false
        try {
            pipeline.beginExternallyVerifiedSession("session-1", link)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }

    @Test fun replacementGattIdentityPermanentlyInvalidatesOfflineReceiveSession() {
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = true
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        assertTrue(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        liveLink = Any()
        assertFalse(pipeline.accept("session-1", 2, byteArrayOf(1), byteArrayOf(1)))
        liveLink = link
        assertFalse(pipeline.accept("session-1", 2, byteArrayOf(1), byteArrayOf(1)))
        var rejected = false
        try {
            pipeline.beginExternallyVerifiedSession("session-1", link)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test fun disconnectDuringAuthenticatorCallNeverAcceptsMessage() {
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
                liveLink = null
                return true
            }
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        liveLink = link
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }

    @Test fun linkIdentityProviderFailureInvalidatesOfflineReceiveSession() {
        var fail = false
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = true
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = {
            if (fail) error("simulated link state failure")
            liveLink
        })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        fail = true
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
        fail = false
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }

    @Test fun disconnectDoesNotWaitForBlockedAuthenticatorAndRejectsLateSuccess() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val accepted = AtomicBoolean(true)
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return true
            }
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        val receiver = Thread {
            try { accepted.set(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1))) }
            finally { finished.countDown() }
        }
        val disconnector = Thread {
            try { pipeline.invalidate() }
            finally { disconnected.countDown() }
        }
        receiver.start()
        try {
            assertTrue("Verifier never started", entered.await(3, TimeUnit.SECONDS))
            disconnector.start()
            assertTrue("Disconnect blocked behind verifier", disconnected.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            receiver.join(3000)
            if (disconnector.state != Thread.State.NEW) disconnector.join(3000)
        }
        assertTrue(finished.await(3, TimeUnit.SECONDS))
        assertFalse("Stale verifier success was accepted", accepted.get())
        assertFalse(pipeline.accept("session-1", 2, byteArrayOf(1), byteArrayOf(1)))
    }

    @Test fun simultaneousAuthenticatedDuplicatesAdvanceCounterOnlyOnce() {
        val bothEntered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val accepted = AtomicInteger(0)
        val verifier = object : YpsoVerifiedReceivePipeline.MessageAuthenticator {
            override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
                bothEntered.countDown()
                return release.await(5, TimeUnit.SECONDS)
            }
        }
        val pipeline = YpsoVerifiedReceivePipeline(verifier, currentLinkIdentity = { liveLink })
        pipeline.beginExternallyVerifiedSession("session-1", link)
        val threads = (1..2).map {
            Thread {
                if (pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1))) accepted.incrementAndGet()
            }
        }
        threads.forEach { it.start() }
        try {
            assertTrue("Verifier calls were serialized", bothEntered.await(3, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            threads.forEach { it.join(3000) }
        }
        assertEquals(1, accepted.get())
        assertFalse(pipeline.accept("session-1", 1, byteArrayOf(1), byteArrayOf(1)))
    }
}
