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
    @Volatile private var status = State.DISCONNECTED
    // Link transitions and newer requests invalidate callbacks from older requests.
    // An identity token cannot wrap around and is not an authentication credential.
    private var requestToken: Any = Any()

    @Synchronized
    fun updateBleLink(connected: Boolean): State {
        requestToken = Any()
        linked = connected
        status = if (connected) State.LINKED_UNAUTHENTICATED else State.DISCONNECTED
        return status
    }

    fun tryAuthorization(): State {
        val expectedToken = synchronized(this) {
            if (!linked) {
                status = State.DISCONNECTED
                return status
            }
            Any().also { requestToken = it }
        }
        // Do not hold the BLE link lock while invoking a potentially slow
        // manufacturer-authorized provider. Fail closed on all provider errors.
        val result = try {
            when {
                !gateway.isConfigured() -> State.AUTHENTICATION_UNAVAILABLE
                gateway.requestAuthorization() == AuthorizedAuthenticationGateway.Result.Rejected -> State.AUTHENTICATION_REJECTED
                else -> State.AUTHENTICATION_UNAVAILABLE
            }
        } catch (_: Exception) {
            State.AUTHENTICATION_UNAVAILABLE
        }
        return synchronized(this) {
            // Never apply a stale result to a disconnected or replacement link,
            // nor let an older request overwrite the newer provider outcome.
            if (linked && requestToken === expectedToken) status = result
            status
        }
    }

    @Synchronized
    fun currentState(): State = status

    /** Even a provider acknowledgement does not establish a validated pump session. */
    fun isPumpInitialized(): Boolean = false
    fun mayAdministerInsulin(): Boolean = false
}
