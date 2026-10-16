package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun rejectsNonpositiveTtl() {
        var rejected = false
        try {
            YpsoAuthSession(clockMillis = { 0L }, sessionTtlMillis = 0)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
    }
}
