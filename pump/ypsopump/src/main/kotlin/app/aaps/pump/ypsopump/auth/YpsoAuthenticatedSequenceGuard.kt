package app.aaps.pump.ypsopump.auth

/**
 * Strict receive-counter guard for offline protocol testing.
 *
 * This is NOT a MAC verifier or a replacement for authenticated encryption.
 * Callers must authenticate the message, including its counter, before calling
 * acceptAuthenticatedCounter. Do not persist or log counters as credentials.
 *
 * The guard deliberately cannot grant permission for pump control.
 */
class YpsoAuthenticatedSequenceGuard {
    private var currentSession: String? = null
    private var lastCounter: Long? = null

    @Synchronized
    fun beginVerifiedSession(sessionId: String) {
        require(sessionId.isNotBlank()) { "A nonblank session identifier is required" }
        currentSession = sessionId
        lastCounter = null
    }

    @Synchronized
    fun invalidate() {
        currentSession = null
        lastCounter = null
    }

    /**
     * Accept only authenticated, strictly increasing positive counters.
     * Reject duplicates, out-of-order values and cross-session messages.
     * Caller must independently validate AEAD authentication and session binding.
     */
    @Synchronized
    fun acceptAuthenticatedCounter(sessionId: String, counter: Long): Boolean {
        if (sessionId.isBlank() || sessionId != currentSession || counter <= 0L) return false
        val previous = lastCounter
        if (previous != null && counter <= previous) return false
        lastCounter = counter
        return true
    }
}
