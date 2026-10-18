package app.aaps.pump.ypsopump.auth

/**
 * Verifier-first receive pipeline for offline protocol fixtures.
 *
 * The caller must provide a trusted implementation of [MessageAuthenticator]
 * using a validated, peer-bound session key and AEAD verification.
 * The default denies all messages. This code never authenticates a pump,
 * sends BLE traffic, or permits insulin delivery.
 */
class YpsoVerifiedReceivePipeline(
    private val authenticator: MessageAuthenticator = RejectAllMessages
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

    @Synchronized
    fun beginExternallyVerifiedSession(id: String) {
        require(id.isNotBlank())
        sequenceGuard.invalidate()
        sessionId = id
        sequenceGuard.beginVerifiedSession(id)
    }

    @Synchronized
    fun invalidate() {
        sessionId = null
        sequenceGuard.invalidate()
    }

    /**
     * Authenticate before advancing replay state, so invalid tags do not consume counters.
     * Empty tags are rejected independently of any verifier implementation.
     */
    @Synchronized
    fun accept(id: String, sequence: Long, payload: ByteArray, tag: ByteArray): Boolean {
        if (id.isBlank() || id != sessionId || sequence <= 0L || tag.isEmpty()) return false
        if (!authenticator.verify(id, sequence, payload.copyOf(), tag.copyOf())) return false
        return sequenceGuard.acceptAuthenticatedCounter(id, sequence)
    }
}
