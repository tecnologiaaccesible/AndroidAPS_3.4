package app.aaps.pump.ypsopump.auth

/**
 * Verifier-first receive pipeline for offline protocol fixtures.
 *
 * The caller must provide a trusted implementation of [MessageAuthenticator]
 * using a validated, peer-bound session key and AEAD verification.
 * The default denies all messages. This code never authenticates a pump,
 * sends BLE traffic, or permits insulin delivery. The live-link callback must be
 * supplied by a trusted transport owner and change identity on every GATT link.
 */
class YpsoVerifiedReceivePipeline(
    private val authenticator: MessageAuthenticator = RejectAllMessages,
    private val currentLinkIdentity: () -> Any? = { null }
) {
    interface MessageAuthenticator {
        /** Must verify tag, sequence associated data, and session binding. */
        fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean
    }

    private object RejectAllMessages : MessageAuthenticator {
        override fun verify(sessionId: String, sequence: Long, payload: ByteArray, tag: ByteArray) = false
    }

    private val sequenceGuard = YpsoAuthenticatedSequenceGuard()
    private var sessionId: String? = null
    // An opaque GATT connection identity, not a credential or peer-authentication proof.
    private var boundLinkIdentity: Any? = null

    private fun sameLiveLink(expected: Any?): Boolean = try {
        expected != null && currentLinkIdentity() === expected
    } catch (_: Exception) {
        false
    }
    // A pipeline must not reset a previously accepted receive counter.
    // Each independently verified cryptographic session needs a fresh pipeline.
    private var sessionEverStarted = false

    /**
     * A trusted caller must separately verify the peer and session transcript.
     * This additionally binds offline receive state to the live GATT connection.
     * The default link provider returns null, so an unconfigured pipeline cannot arm.
     */
    @Synchronized
    fun beginExternallyVerifiedSession(id: String, linkIdentity: Any) {
        require(id.isNotBlank())
        check(!sessionEverStarted) { "Receive pipeline is single-use; create a new instance for each verified session" }
        check(sameLiveLink(linkIdentity)) { "Verified session requires the current GATT link" }
        sessionEverStarted = true
        sequenceGuard.invalidate()
        boundLinkIdentity = linkIdentity
        sessionId = id
        sequenceGuard.beginVerifiedSession(id)
    }

    @Synchronized
    fun invalidate() {
        sessionId = null
        boundLinkIdentity = null
        sequenceGuard.invalidate()
    }

    /**
     * Authenticate before advancing replay state, so invalid tags do not consume counters.
     * Empty tags are rejected independently of any verifier implementation.
     */
    @Synchronized
    fun accept(id: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
        // A disconnect or replacement link irreversibly invalidates this session.
        // Check both before and after verification: the link can change mid-call.
        if (sessionId != null && !sameLiveLink(boundLinkIdentity)) {
            invalidate()
            return false
        }
        if (id.isBlank() || id != sessionId || sequence <= 0L || tag.isEmpty()) return false
        // Verifier failures must never advance replay state or escape diagnostics.
        val verified = try {
            authenticator.verify(id, sequence, payload.copyOf(), tag.copyOf())
        } catch (_: Exception) {
            false
        }
        if (!sameLiveLink(boundLinkIdentity)) {
            invalidate()
            return false
        }
        if (!verified) return false
        return sequenceGuard.acceptAuthenticatedCounter(id, sequence)
    }
}
