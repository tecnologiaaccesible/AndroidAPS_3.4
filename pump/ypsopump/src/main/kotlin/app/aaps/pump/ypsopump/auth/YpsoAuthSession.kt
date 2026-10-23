package app.aaps.pump.ypsopump.auth

/**
 * Offline-only session coordinator for a future manufacturer-authorized credential provider.
 * This class has no Android, BLE, network, persistence, or insulin-delivery dependencies.
 * No implementation in this branch authenticates a physical YpsoPump.
 */
class YpsoAuthSession(
    private val provider: AuthorizedExchangeProvider = UnavailableExchangeProvider,
    private val clockMillis: () -> Long,
    private val sessionTtlMillis: Long = 15 * 60_000L,
    private val authorizationTimeoutMillis: Long = 30_000L
) {
    init { require(sessionTtlMillis > 0 && authorizationTimeoutMillis > 0) }

    enum class Status { DISCONNECTED, LINKED, AWAITING_AUTHORIZATION, AUTHORIZED_SIMULATION, EXPIRED, FAILED }
    enum class Failure { PROVIDER_UNAVAILABLE, AUTHENTICATION_REJECTED, INVALID_SESSION, CLOCK_ROLLBACK, AUTHORIZATION_TIMEOUT }

    data class State(
        val status: Status = Status.DISCONNECTED,
        val expiresAtMillis: Long? = null,
        val failure: Failure? = null
    )

    /** Tokens are deliberately opaque; implementations must never log or export secrets. */
    interface AuthorizedExchangeProvider {
        fun available(): Boolean
    }

    private object UnavailableExchangeProvider : AuthorizedExchangeProvider {
        override fun available(): Boolean = false
    }

    private var snapshot = State()
    private var lastClockMillis: Long? = null

    fun state(): State {
        val now = clockMillis()
        val previous = lastClockMillis
        if (previous != null && now < previous) {
            snapshot = State(Status.FAILED, failure = Failure.CLOCK_ROLLBACK)
        } else if (snapshot.status == Status.AWAITING_AUTHORIZATION &&
            (snapshot.expiresAtMillis == null || now >= snapshot.expiresAtMillis!!)
        ) {
            snapshot = State(Status.FAILED, failure = Failure.AUTHORIZATION_TIMEOUT)
        } else if (snapshot.status == Status.AUTHORIZED_SIMULATION &&
            (snapshot.expiresAtMillis == null || now >= snapshot.expiresAtMillis!!)
        ) {
            snapshot = State(Status.EXPIRED)
        }
        lastClockMillis = now
        return snapshot
    }

    fun connect(): State {
        disconnect()
        snapshot = State(Status.LINKED)
        return state()
    }

    /** Fail closed if an authorized provider is absent or throws unexpectedly. */
    private fun providerIsAvailable(): Boolean = try {
        provider.available()
    } catch (_: Exception) {
        false
    }

    fun beginAuthentication(): State {
        if (state().status != Status.LINKED) {
            snapshot = State(Status.FAILED, failure = Failure.INVALID_SESSION)
        } else if (!providerIsAvailable()) {
            snapshot = State(Status.FAILED, failure = Failure.PROVIDER_UNAVAILABLE)
        } else {
            val now = clockMillis()
            snapshot = if (now > Long.MAX_VALUE - authorizationTimeoutMillis) {
                State(Status.FAILED, failure = Failure.INVALID_SESSION)
            } else {
                State(Status.AWAITING_AUTHORIZATION, expiresAtMillis = now + authorizationTimeoutMillis)
            }
        }
        return state()
    }

    /**
     * Synthetic test hook, deliberately not exposed as real authentication.
     * Even this state never grants hardware-control permission.
     */
    internal fun acceptSyntheticFixture(): State {
        if (state().status != Status.AWAITING_AUTHORIZATION) {
            snapshot = State(Status.FAILED, failure = Failure.INVALID_SESSION)
        } else {
            val now = clockMillis()
            snapshot = if (now > Long.MAX_VALUE - sessionTtlMillis) {
                State(Status.FAILED, failure = Failure.INVALID_SESSION)
            } else {
                State(Status.AUTHORIZED_SIMULATION, expiresAtMillis = now + sessionTtlMillis)
            }
        }
        return state()
    }

    fun rejectAuthentication(): State {
        snapshot = State(Status.FAILED, failure = Failure.AUTHENTICATION_REJECTED)
        return state()
    }

    fun disconnect(): State {
        snapshot = State()
        lastClockMillis = null
        return snapshot
    }

    /** Always false until a separately audited, authorized real implementation exists. */
    fun permitsPhysicalPumpOperation(): Boolean = false
}
