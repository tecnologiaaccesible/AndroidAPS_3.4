package app.aaps.pump.ypsopump.auth

/**
 * Conservative bridge between BLE transport and pump application authentication.
 * Bluetooth GATT pairing must never be treated as permission to dose insulin.
 * No implementation of the authorized provider is bundled in this branch.
 */
class YpsoPumpConnectionGate(
    private val gateway: AuthorizedAuthenticationGateway = UnconfiguredAuthenticationGateway
) {
    enum class State { DISCONNECTED, LINKED_UNAUTHENTICATED, AUTHENTICATION_UNAVAILABLE, AUTHENTICATION_REJECTED }

    private var linked = false
    // An opaque transport identity changes on each actual GATT connection.
    // It is not an authentication credential and must never grant pump control.
    private var linkIdentity: Any? = null
    @Volatile private var status = State.DISCONNECTED
    // Link transitions and newer requests invalidate callbacks from older requests.
    // An identity token cannot wrap around and is not an authentication credential.
    private var requestToken: Any = Any()
    // A manufacturer authorization challenge must not be started twice in parallel
    // for the same GATT link. Replaced links may start independently while old
    // requests are still unwinding; their callbacks remain token-isolated.
    private var authorizationInFlight = false

    @Synchronized
    fun updateBleLink(connected: Boolean, identity: Any?): State {
        // A missing identity is not a valid diagnostic link. A replacement GATT
        // connection invalidates old provider responses even if polling never
        // observed a disconnected interval.
        val validLink = connected && identity != null
        if (linked == validLink && (!validLink || linkIdentity === identity)) return status
        requestToken = Any()
        authorizationInFlight = false
        linked = validLink
        linkIdentity = if (validLink) identity else null
        status = if (validLink) State.LINKED_UNAUTHENTICATED else State.DISCONNECTED
        return status
    }

    fun tryAuthorization(): State {
        val (expectedToken, expectedLinkIdentity) = synchronized(this) {
            if (!linked) {
                status = State.DISCONNECTED
                return status
            }
            // The local link token is mandatory but does not authenticate the pump.
            val activeLinkIdentity = linkIdentity ?: return State.DISCONNECTED
            // Coalesce repeated UI requests while a provider operation is pending.
            // In particular, do not issue concurrent authentication challenges.
            if (authorizationInFlight) return status
            authorizationInFlight = true
            val token = Any().also { requestToken = it }
            token to activeLinkIdentity
        }
        // Do not hold the BLE link lock while invoking a potentially slow
        // manufacturer-authorized provider. Fail closed on all provider errors.
        val result = try {
            when {
                !gateway.isConfigured() -> State.AUTHENTICATION_UNAVAILABLE
                gateway.requestAuthorization(expectedLinkIdentity) == AuthorizedAuthenticationGateway.Result.Rejected -> State.AUTHENTICATION_REJECTED
                else -> State.AUTHENTICATION_UNAVAILABLE
            }
        } catch (_: Exception) {
            State.AUTHENTICATION_UNAVAILABLE
        }
        return synchronized(this) {
            // Never apply a stale result to a disconnected or replacement link,
            // nor let an older request overwrite the newer provider outcome.
            if (linked && linkIdentity === expectedLinkIdentity && requestToken === expectedToken) {
                authorizationInFlight = false
                status = result
            }
            status
        }
    }

    @Synchronized
    fun currentState(): State = status

    /** Even a provider acknowledgement does not establish a validated pump session. */
    fun isPumpInitialized(): Boolean = false
    /** A diagnostic BLE link is not a validated AAPS pump connection. */
    fun isPumpConnected(): Boolean = false
    fun mayAdministerInsulin(): Boolean = false
}
