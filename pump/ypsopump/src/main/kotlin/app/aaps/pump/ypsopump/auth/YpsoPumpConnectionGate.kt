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

    @Synchronized
    fun updateBleLink(connected: Boolean): State {
        linked = connected
        status = if (connected) State.LINKED_UNAUTHENTICATED else State.DISCONNECTED
        return status
    }

    @Synchronized
    fun tryAuthorization(): State {
        if (!linked) return State.DISCONNECTED.also { status = it }
        status = when {
            !gateway.isConfigured() -> State.AUTHENTICATION_UNAVAILABLE
            gateway.requestAuthorization() == AuthorizedAuthenticationGateway.Result.Rejected -> State.AUTHENTICATION_REJECTED
            else -> State.AUTHENTICATION_UNAVAILABLE
        }
        return status
    }

    @Synchronized
    fun currentState(): State = status

    /** Even a provider acknowledgement does not establish a validated pump session. */
    fun isPumpInitialized(): Boolean = false
    fun mayAdministerInsulin(): Boolean = false
}
