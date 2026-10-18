package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
