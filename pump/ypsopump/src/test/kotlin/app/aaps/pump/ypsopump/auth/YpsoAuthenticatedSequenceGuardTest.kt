package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoAuthenticatedSequenceGuardTest {
    @Test fun rejectsReplayAndOutOfOrderCounters() {
        val guard = YpsoAuthenticatedSequenceGuard()
        assertFalse(guard.acceptAuthenticatedCounter("s1", 1))
        guard.beginVerifiedSession("s1")
        assertTrue(guard.acceptAuthenticatedCounter("s1", 1))
        assertFalse(guard.acceptAuthenticatedCounter("s1", 1))
        assertFalse(guard.acceptAuthenticatedCounter("s1", 0))
        assertTrue(guard.acceptAuthenticatedCounter("s1", 3))
        assertFalse(guard.acceptAuthenticatedCounter("s1", 2))
    }

    @Test fun rejectsCrossSessionAndInvalidatedMessages() {
        val guard = YpsoAuthenticatedSequenceGuard()
        guard.beginVerifiedSession("session-a")
        assertFalse(guard.acceptAuthenticatedCounter("session-b", 1))
        assertTrue(guard.acceptAuthenticatedCounter("session-a", 10))
        guard.invalidate()
        assertFalse(guard.acceptAuthenticatedCounter("session-a", 11))
        guard.beginVerifiedSession("session-b")
        assertTrue(guard.acceptAuthenticatedCounter("session-b", 1))
        assertFalse(guard.acceptAuthenticatedCounter("session-a", 12))
    }

    @Test fun rejectsNonpositiveCountersAndBlankSessionId() {
        val guard = YpsoAuthenticatedSequenceGuard()
        guard.beginVerifiedSession("s")
        assertFalse(guard.acceptAuthenticatedCounter("s", -1))
        assertFalse(guard.acceptAuthenticatedCounter("s", 0))
        assertFalse(guard.acceptAuthenticatedCounter("", 1))
    }
}
