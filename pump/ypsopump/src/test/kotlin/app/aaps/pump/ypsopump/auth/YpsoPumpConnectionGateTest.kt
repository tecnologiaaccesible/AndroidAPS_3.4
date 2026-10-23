package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class YpsoPumpConnectionGateTest {
    private val testLink = Any()
    @Test fun bluetoothAloneNeverAuthorizes() {
        val gate = YpsoPumpConnectionGate()
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.currentState())
        gate.updateBleLink(true, testLink)
        assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.currentState())
        assertFalse(gate.isPumpInitialized())
        assertFalse(gate.mayAdministerInsulin())
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_UNAVAILABLE, gate.tryAuthorization())
        gate.updateBleLink(false, null)
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.currentState())
    }

    @Test fun providerApprovalCannotSubstituteForValidatedSession() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.AuthorizedByProvider
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true, testLink)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_UNAVAILABLE, gate.tryAuthorization())
        assertFalse(gate.isPumpInitialized())
        assertFalse(gate.mayAdministerInsulin())
    }

    @Test fun rejectedProviderHasDistinctStatus() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.Rejected
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true, testLink)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.tryAuthorization())
    }

    @Test fun configurationExceptionFailsClosed() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured(): Boolean = error("simulated configuration failure")
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.Unavailable
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true, testLink)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_UNAVAILABLE, gate.tryAuthorization())
        assertFalse(gate.isPumpInitialized())
        assertFalse(gate.mayAdministerInsulin())
    }

    @Test fun requestExceptionFailsClosedAndDisconnectResetsState() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result =
                error("simulated authorization failure")
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true, testLink)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_UNAVAILABLE, gate.tryAuthorization())
        assertFalse(gate.isPumpInitialized())
        assertFalse(gate.mayAdministerInsulin())
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.updateBleLink(false, null))
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.tryAuthorization())
    }

    @Test fun disconnectDuringAuthorizationDoesNotWaitForProvider() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return AuthorizedAuthenticationGateway.Result.Rejected
            }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newFixedThreadPool(2)
        try {
            gate.updateBleLink(true, testLink)
            val pending = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Gateway was not reached", entered.await(5, TimeUnit.SECONDS))
            val disconnect = pool.submit<YpsoPumpConnectionGate.State> { gate.updateBleLink(false, null) }
            assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, disconnect.get(2, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, pending.get(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.currentState())
            assertFalse(gate.mayAdministerInsulin())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun staleAuthorizationCannotChangeReplacementLink() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return AuthorizedAuthenticationGateway.Result.Rejected
            }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newFixedThreadPool(2)
        try {
            gate.updateBleLink(true, testLink)
            val pending = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Gateway was not reached", entered.await(5, TimeUnit.SECONDS))
            assertEquals(
                YpsoPumpConnectionGate.State.DISCONNECTED,
                pool.submit<YpsoPumpConnectionGate.State> { gate.updateBleLink(false, null) }.get(2, TimeUnit.SECONDS)
            )
            assertEquals(
                YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED,
                pool.submit<YpsoPumpConnectionGate.State> { gate.updateBleLink(true, testLink) }.get(2, TimeUnit.SECONDS)
            )
            release.countDown()
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, pending.get(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.currentState())
            assertFalse(gate.mayAdministerInsulin())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun olderProviderResponseCannotOverrideNewerRejection() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result =
                if (calls.incrementAndGet() == 1) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    AuthorizedAuthenticationGateway.Result.AuthorizedByProvider
                } else {
                    AuthorizedAuthenticationGateway.Result.Rejected
                }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newSingleThreadExecutor()
        try {
            gate.updateBleLink(true, testLink)
            val older = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("First request was not reached", entered.await(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.tryAuthorization())
            release.countDown()
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, older.get(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.currentState())
            assertFalse(gate.mayAdministerInsulin())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun repeatedBlePollingPreservesRejectionAndNeverReportsTherapeuticConnection() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.Rejected
        }
        val gate = YpsoPumpConnectionGate(gateway)
        assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.updateBleLink(true, testLink))
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.tryAuthorization())
        repeat(3) {
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.updateBleLink(true, testLink))
            assertFalse(gate.isPumpConnected())
            assertFalse(gate.isPumpInitialized())
            assertFalse(gate.mayAdministerInsulin())
        }
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.updateBleLink(false, null))
        assertFalse(gate.isPumpConnected())
    }

    @Test fun repeatedBlePollingDoesNotInvalidatePendingAuthorizationCheck() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return AuthorizedAuthenticationGateway.Result.Rejected
            }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newSingleThreadExecutor()
        try {
            gate.updateBleLink(true, testLink)
            val pending = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Provider was not reached", entered.await(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.updateBleLink(true, testLink))
            release.countDown()
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, pending.get(5, TimeUnit.SECONDS))
            assertFalse(gate.isPumpConnected())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun missingLinkIdentityFailsClosed() {
        val gate = YpsoPumpConnectionGate()
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.updateBleLink(true, null))
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.tryAuthorization())
        assertFalse(gate.isPumpConnected())
        assertFalse(gate.mayAdministerInsulin())
    }

    @Test fun replacementIdentityInvalidatesPriorRejectionWithoutDisconnectedPoll() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.Rejected
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true, testLink)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.tryAuthorization())
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.updateBleLink(true, testLink))
        assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.updateBleLink(true, Any()))
        assertFalse(gate.isPumpConnected())
        assertFalse(gate.mayAdministerInsulin())
    }

    @Test fun oldProviderResultCannotAffectReplacementIdentityWithoutDisconnectedPoll() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return AuthorizedAuthenticationGateway.Result.Rejected
            }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newSingleThreadExecutor()
        try {
            gate.updateBleLink(true, testLink)
            val oldRequest = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Provider was not reached", entered.await(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.updateBleLink(true, Any()))
            release.countDown()
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, oldRequest.get(5, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.currentState())
            assertFalse(gate.mayAdministerInsulin())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun concurrentAuthorizationAttemptsIssueOnlyOneProviderRequestPerLink() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result {
                calls.incrementAndGet()
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return AuthorizedAuthenticationGateway.Result.Rejected
            }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newSingleThreadExecutor()
        try {
            gate.updateBleLink(true, testLink)
            val pending = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Provider request did not start", entered.await(3, TimeUnit.SECONDS))
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.tryAuthorization())
            assertEquals("A duplicate provider request was started", 1, calls.get())
            release.countDown()
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, pending.get(5, TimeUnit.SECONDS))
            // A completed request does not permanently prevent an explicit retry.
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.tryAuthorization())
            assertEquals(2, calls.get())
            assertFalse(gate.isPumpConnected())
            assertFalse(gate.mayAdministerInsulin())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun staleProviderCompletionCannotUnlockPendingRequestOnReplacementLink() {
        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val secondRelease = CountDownLatch(1)
        val calls = AtomicInteger()
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization(): AuthorizedAuthenticationGateway.Result {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstEntered.countDown()
                        firstRelease.await(5, TimeUnit.SECONDS)
                    }
                    2 -> {
                        secondEntered.countDown()
                        secondRelease.await(5, TimeUnit.SECONDS)
                    }
                    else -> error("Unexpected duplicate provider request")
                }
                return AuthorizedAuthenticationGateway.Result.Rejected
            }
        }
        val gate = YpsoPumpConnectionGate(gateway)
        val pool = Executors.newFixedThreadPool(2)
        try {
            gate.updateBleLink(true, testLink)
            val oldRequest = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue(firstEntered.await(3, TimeUnit.SECONDS))
            gate.updateBleLink(true, Any())
            val newRequest = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue(secondEntered.await(3, TimeUnit.SECONDS))
            firstRelease.countDown()
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, oldRequest.get(5, TimeUnit.SECONDS))
            // The old callback must not release the new link's in-flight guard.
            assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.tryAuthorization())
            assertEquals(2, calls.get())
            secondRelease.countDown()
            assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, newRequest.get(5, TimeUnit.SECONDS))
            assertFalse(gate.isPumpInitialized())
            assertFalse(gate.mayAdministerInsulin())
        } finally {
            firstRelease.countDown()
            secondRelease.countDown()
            pool.shutdownNow()
        }
    }
}
