package app.aaps.pump.ypsopump.auth

/**
 * Contract for a manufacturer-authorized app-layer authentication integration.
 *
 * There is intentionally no implementation: a Bluetooth bond, fabricated credentials,
 * a mock provider, or an unverified server response cannot establish pump trust.
 * An authorized implementation must be independently reviewed and tested before use.
 *
 * No keys, passcodes, tokens, challenges, pump identifiers, or payloads are logged.
 */
interface AuthorizedAuthenticationGateway {
    /** True only if a manufacturer-authorized backend/client is configured. */
    fun isConfigured(): Boolean

    /**
     * Outcome of a provider-side authorization request. The coordinator may display
     * these results, but none grant permission to read protected data or control insulin.
     */
    sealed class Result {
        data object Unavailable : Result()
        data object Rejected : Result()
        data object Timeout : Result()
        data object TransportFailure : Result()
        data object AuthorizedByProvider : Result()
    }

    /**
     * A caller must supply the exact in-process GATT link identity associated with
     * this request. This opaque token is NOT authenticated pump identity and must
     * never be serialized, logged, or treated as manufacturer authorization.
     * No implementation is bundled; this signature accepts no bypass secrets.
     */
    fun requestAuthorization(linkIdentity: Any): Result
}

/** Production-safe default: never attempts a network request or BLE write. */
object UnconfiguredAuthenticationGateway : AuthorizedAuthenticationGateway {
    override fun isConfigured() = false
    override fun requestAuthorization(linkIdentity: Any): AuthorizedAuthenticationGateway.Result =
        AuthorizedAuthenticationGateway.Result.Unavailable
}
