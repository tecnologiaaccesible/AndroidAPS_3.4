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
    // Invalidate results from authenticators still running when the link is closed.
    private var sessionEpoch = 0L

    private data class PendingFrame(val linkIdentity: Any, val epoch: Long)

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
        sessionEpoch++
        sessionEverStarted = true
        sequenceGuard.invalidate()
        boundLinkIdentity = linkIdentity
        sessionId = id
        sequenceGuard.beginVerifiedSession(id)
    }

    @Synchronized
    fun invalidate() {
        sessionEpoch++
        sessionId = null
        boundLinkIdentity = null
        sequenceGuard.invalidate()
    }

    /**
     * Verify frames without holding the pipeline monitor across the external
     * authenticator. A disconnect must be able to invalidate the session even
     * if the authenticator blocks. Only the final, link-bound replay check may
     * advance the counter; stale results are discarded.
     */
    fun accept(id: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
        val pending = synchronized(this) {
            if (sessionId != null && !sameLiveLink(boundLinkIdentity)) invalidate()
            if (id.isBlank() || id != sessionId || sequence <= 0L || tag.isEmpty()) {
                null
            } else {
                boundLinkIdentity?.let { PendingFrame(it, sessionEpoch) }
            }
        } ?: return false

        // Untrusted verifier code must not block disconnect or session teardown.
        val verified = try {
            authenticator.verify(id, sequence, payload.copyOf(), tag.copyOf())
        } catch (_: Exception) {
            false
        }

        return synchronized(this) {
            if (pending.epoch != sessionEpoch || id != sessionId) return@synchronized false
            // Check again after verification: the GATT link may have changed.
            if (!sameLiveLink(pending.linkIdentity)) {
                invalidate()
                return@synchronized false
            }
            if (!verified) return@synchronized false
            sequenceGuard.acceptAuthenticatedCounter(id, sequence)
        }
    }
}
