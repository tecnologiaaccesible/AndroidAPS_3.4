package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

class YpsoAuthSessionTest {
    private class FakeClock(var now: Long = 1000L) {
        fun read(): Long = now
    }
    private class AvailableProvider : YpsoAuthSession.AuthorizedExchangeProvider {
        override fun available(): Boolean = true
    }

    @Test fun unavailableProviderFailsClosed() {
        val clock = FakeClock()
        val session = YpsoAuthSession(clockMillis = clock::read)
        assertEquals(YpsoAuthSession.Status.LINKED, session.connect().status)
        val failure = session.beginAuthentication()
        assertEquals(YpsoAuthSession.Status.FAILED, failure.status)
        assertEquals(YpsoAuthSession.Failure.PROVIDER_UNAVAILABLE, failure.failure)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun syntheticSessionExpiresAtDeadline() {
        val clock = FakeClock()
        val session = YpsoAuthSession(AvailableProvider(), clock::read, 100L)
        session.connect()
        assertEquals(YpsoAuthSession.Status.AWAITING_AUTHORIZATION, session.beginAuthentication().status)
        assertEquals(YpsoAuthSession.Status.AUTHORIZED_SIMULATION, session.acceptSyntheticFixture().status)
        clock.now = 1099L
        assertEquals(YpsoAuthSession.Status.AUTHORIZED_SIMULATION, session.state().status)
        clock.now = 1100L
        assertEquals(YpsoAuthSession.Status.EXPIRED, session.state().status)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun invalidSequencesAndRejectionAreBlocked() {
        val clock = FakeClock()
        val session = YpsoAuthSession(AvailableProvider(), clock::read)
        assertEquals(YpsoAuthSession.Failure.INVALID_SESSION, session.beginAuthentication().failure)
        session.connect()
        assertEquals(YpsoAuthSession.Failure.INVALID_SESSION, session.acceptSyntheticFixture().failure)
        session.connect()
        session.beginAuthentication()
        assertEquals(YpsoAuthSession.Failure.AUTHENTICATION_REJECTED, session.rejectAuthentication().failure)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun disconnectClearsAuthorizationAndClockRollbackFailsClosed() {
        val clock = FakeClock()
        val session = YpsoAuthSession(AvailableProvider(), clock::read, 100L)
        session.connect()
        session.beginAuthentication()
        session.acceptSyntheticFixture()
        clock.now = 999L
        assertEquals(YpsoAuthSession.Failure.CLOCK_ROLLBACK, session.state().failure)
        assertEquals(YpsoAuthSession.Status.DISCONNECTED, session.disconnect().status)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun pendingAuthorizationTimesOutAndLateSuccessIsRejected() {
        val clock = FakeClock()
        val session = YpsoAuthSession(AvailableProvider(), clock::read, 100L, 30L)
        session.connect()
        assertEquals(YpsoAuthSession.Status.AWAITING_AUTHORIZATION, session.beginAuthentication().status)
        clock.now = 1029L
        assertEquals(YpsoAuthSession.Status.AWAITING_AUTHORIZATION, session.state().status)
        clock.now = 1030L
        assertEquals(YpsoAuthSession.Failure.AUTHORIZATION_TIMEOUT, session.state().failure)
        assertEquals(YpsoAuthSession.Failure.INVALID_SESSION, session.acceptSyntheticFixture().failure)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun disconnectBeforeAuthorizationResponseInvalidatesPendingRequest() {
        val clock = FakeClock()
        val session = YpsoAuthSession(AvailableProvider(), clock::read)
        session.connect()
        session.beginAuthentication()
        session.disconnect()
        assertEquals(YpsoAuthSession.Failure.INVALID_SESSION, session.acceptSyntheticFixture().failure)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun providerAvailabilityExceptionFailsClosed() {
        val clock = FakeClock()
        val provider = object : YpsoAuthSession.AuthorizedExchangeProvider {
            override fun available(): Boolean = throw IllegalStateException("simulated provider failure")
        }
        val session = YpsoAuthSession(provider, clock::read)
        session.connect()
        val failure = session.beginAuthentication()
        assertEquals(YpsoAuthSession.Status.FAILED, failure.status)
        assertEquals(YpsoAuthSession.Failure.PROVIDER_UNAVAILABLE, failure.failure)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun providerRecoveryRequiresNewConnection() {
        val clock = FakeClock()
        var throwOnCheck = true
        val provider = object : YpsoAuthSession.AuthorizedExchangeProvider {
            override fun available(): Boolean {
                if (throwOnCheck) throw IllegalStateException("simulated transient failure")
                return true
            }
        }
        val session = YpsoAuthSession(provider, clock::read)
        session.connect()
        assertEquals(YpsoAuthSession.Failure.PROVIDER_UNAVAILABLE, session.beginAuthentication().failure)
        throwOnCheck = false
        assertEquals(YpsoAuthSession.Failure.INVALID_SESSION, session.beginAuthentication().failure)
        session.connect()
        assertEquals(YpsoAuthSession.Status.AWAITING_AUTHORIZATION, session.beginAuthentication().status)
        assertFalse(session.permitsPhysicalPumpOperation())
    }

    @Test fun rejectsNonpositiveAuthorizationTimeout() {
        var rejected = false
        try {
            YpsoAuthSession(clockMillis = { 0L }, authorizationTimeoutMillis = 0)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
    }

    @Test fun rejectsNonpositiveTtl() {
        var rejected = false
        try {
            YpsoAuthSession(clockMillis = { 0L }, sessionTtlMillis = 0)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
    }

    @Test fun disconnectDuringProviderCheckCannotReviveSession() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = object : YpsoAuthSession.AuthorizedExchangeProvider {
            override fun available(): Boolean {
                entered.countDown()
                return release.await(5, TimeUnit.SECONDS)
            }
        }
        val session = YpsoAuthSession(provider, FakeClock()::read)
        val executor = Executors.newSingleThreadExecutor()
        try {
            session.connect()
            val pending = executor.submit<YpsoAuthSession.State> { session.beginAuthentication() }
            assertTrue("Provider was not reached", entered.await(5, TimeUnit.SECONDS))
            assertEquals(YpsoAuthSession.Status.DISCONNECTED, session.disconnect().status)
            release.countDown()
            assertEquals(YpsoAuthSession.Status.DISCONNECTED, pending.get(5, TimeUnit.SECONDS).status)
            assertEquals(YpsoAuthSession.Status.DISCONNECTED, session.state().status)
            assertFalse(session.permitsPhysicalPumpOperation())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun staleProviderCheckCannotAuthorizeReplacementLink() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var calls = 0
        val provider = object : YpsoAuthSession.AuthorizedExchangeProvider {
            override fun available(): Boolean {
                synchronized(this) { calls++ }
                if (calls == 1) {
                    entered.countDown()
                    return release.await(5, TimeUnit.SECONDS)
                }
                return true
            }
        }
        val session = YpsoAuthSession(provider, FakeClock()::read)
        val executor = Executors.newSingleThreadExecutor()
        try {
            session.connect()
            val pending = executor.submit<YpsoAuthSession.State> { session.beginAuthentication() }
            assertTrue("Provider was not reached", entered.await(5, TimeUnit.SECONDS))
            session.disconnect()
            assertEquals(YpsoAuthSession.Status.LINKED, session.connect().status)
            release.countDown()
            assertEquals(YpsoAuthSession.Status.LINKED, pending.get(5, TimeUnit.SECONDS).status)
            assertEquals(YpsoAuthSession.Status.LINKED, session.state().status)
            assertEquals(YpsoAuthSession.Status.AWAITING_AUTHORIZATION, session.beginAuthentication().status)
            assertFalse(session.permitsPhysicalPumpOperation())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
