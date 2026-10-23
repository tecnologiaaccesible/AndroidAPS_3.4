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
    @Test fun bluetoothAloneNeverAuthorizes() {
        val gate = YpsoPumpConnectionGate()
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.currentState())
        gate.updateBleLink(true)
        assertEquals(YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED, gate.currentState())
        assertFalse(gate.isPumpInitialized())
        assertFalse(gate.mayAdministerInsulin())
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_UNAVAILABLE, gate.tryAuthorization())
        gate.updateBleLink(false)
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.currentState())
    }

    @Test fun providerApprovalCannotSubstituteForValidatedSession() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured() = true
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.AuthorizedByProvider
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true)
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
        gate.updateBleLink(true)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_REJECTED, gate.tryAuthorization())
    }

    @Test fun configurationExceptionFailsClosed() {
        val gateway = object : AuthorizedAuthenticationGateway {
            override fun isConfigured(): Boolean = error("simulated configuration failure")
            override fun requestAuthorization() = AuthorizedAuthenticationGateway.Result.Unavailable
        }
        val gate = YpsoPumpConnectionGate(gateway)
        gate.updateBleLink(true)
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
        gate.updateBleLink(true)
        assertEquals(YpsoPumpConnectionGate.State.AUTHENTICATION_UNAVAILABLE, gate.tryAuthorization())
        assertFalse(gate.isPumpInitialized())
        assertFalse(gate.mayAdministerInsulin())
        assertEquals(YpsoPumpConnectionGate.State.DISCONNECTED, gate.updateBleLink(false))
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
            gate.updateBleLink(true)
            val pending = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Gateway was not reached", entered.await(5, TimeUnit.SECONDS))
            val disconnect = pool.submit<YpsoPumpConnectionGate.State> { gate.updateBleLink(false) }
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
            gate.updateBleLink(true)
            val pending = pool.submit<YpsoPumpConnectionGate.State> { gate.tryAuthorization() }
            assertTrue("Gateway was not reached", entered.await(5, TimeUnit.SECONDS))
            assertEquals(
                YpsoPumpConnectionGate.State.DISCONNECTED,
                pool.submit<YpsoPumpConnectionGate.State> { gate.updateBleLink(false) }.get(2, TimeUnit.SECONDS)
            )
            assertEquals(
                YpsoPumpConnectionGate.State.LINKED_UNAUTHENTICATED,
                pool.submit<YpsoPumpConnectionGate.State> { gate.updateBleLink(true) }.get(2, TimeUnit.SECONDS)
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
            gate.updateBleLink(true)
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
}
