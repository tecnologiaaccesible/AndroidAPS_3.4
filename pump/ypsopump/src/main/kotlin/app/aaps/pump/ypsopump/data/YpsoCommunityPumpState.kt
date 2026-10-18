package app.aaps.pump.ypsopump.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapted from SandraK82/ypsopump-research, aaps-driver YpsoPumpState.kt
 * (MIT license). Experimental data model for AAPS 3.4.2.6.
 *
 * SAFETY: No delivery-capable Pump plugin is registered by this class.
 * Values must not be treated as real measurements until verified by an
 * authenticated connection; reset clears potentially stale pump data.
 */
@Singleton
class YpsoCommunityPumpState @Inject constructor() {
    enum class Connection { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }
    @Volatile var connection: Connection = Connection.DISCONNECTED
        private set
    @Volatile var firmwareVersion: String = ""
        private set
    @Volatile var batteryPercent: Int? = null
        private set
    @Volatile var reservoirUnits: Double? = null
        private set
    @Volatile var activeBasalRate: Double? = null
        private set
    @Volatile var lastStatusTime: Long = 0
        private set

    /** Only called by an eventual validated status decoder, never from mock UI. */
    fun setUnverifiedConnectionState(next: Connection) {
        connection = next
        if (next == Connection.DISCONNECTED) clearMeasurements()
    }

    fun clearMeasurements() {
        firmwareVersion = ""
        batteryPercent = null
        reservoirUnits = null
        activeBasalRate = null
        lastStatusTime = 0
    }

    fun hasVerifiedStatus(): Boolean = false
    fun permitsPhysicalPumpCommand(): Boolean = false
}
