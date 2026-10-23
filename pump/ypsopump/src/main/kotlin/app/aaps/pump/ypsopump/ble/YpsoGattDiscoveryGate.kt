package app.aaps.pump.ypsopump.ble

/**
 * Allows one successful GATT service discovery per BLE link.
 *
 * A duplicate Android callback must not replace the read allowlist or
 * reset an in-flight read's replay/timeout bookkeeping. A new link must
 * explicitly reset this guard after the previous GATT instance is closed.
 * This is diagnostic transport state, not application authentication.
 */
internal class YpsoGattDiscoveryGate {
    private var accepted = false

    @Synchronized
    fun claimSuccessfulDiscovery(): Boolean {
        if (accepted) return false
        accepted = true
        return true
    }

    @Synchronized
    fun resetForNewLink() {
        accepted = false
    }
}
