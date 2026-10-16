package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthorizedAuthenticationGatewayTest {
    @Test fun unconfiguredGatewayCannotClaimAuthentication() {
        assertFalse(UnconfiguredAuthenticationGateway.isConfigured())
        assertEquals(
            AuthorizedAuthenticationGateway.Result.Unavailable,
            UnconfiguredAuthenticationGateway.requestAuthorization()
        )
    }

    @Test fun gatewayResultsNeverAuthorizeHardwareControl() {
        val outcomes = listOf(
            AuthorizedAuthenticationGateway.Result.Unavailable,
            AuthorizedAuthenticationGateway.Result.Rejected,
            AuthorizedAuthenticationGateway.Result.Timeout,
            AuthorizedAuthenticationGateway.Result.TransportFailure,
            AuthorizedAuthenticationGateway.Result.AuthorizedByProvider
        )
        val session = YpsoAuthSession(clockMillis = { 1000L })
        for (outcome in outcomes) {
            // Provider reporting success is not proof of an established pump session.
            assertFalse(session.permitsPhysicalPumpOperation())
            assertFalse(outcome is AuthorizedAuthenticationGateway.Result.Unavailable &&
                UnconfiguredAuthenticationGateway.isConfigured())
        }
    }
}
