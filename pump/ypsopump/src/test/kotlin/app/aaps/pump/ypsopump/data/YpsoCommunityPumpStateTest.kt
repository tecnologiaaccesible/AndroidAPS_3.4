package app.aaps.pump.ypsopump.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class YpsoCommunityPumpStateTest {
    @Test fun connectionNeverGrantsInsulinPermissions() {
        val state = YpsoCommunityPumpState()
        state.setUnverifiedConnectionState(YpsoCommunityPumpState.Connection.CONNECTED)
        assertEquals(YpsoCommunityPumpState.Connection.CONNECTED, state.connection)
        assertFalse(state.hasVerifiedStatus())
        assertFalse(state.permitsPhysicalPumpCommand())
        assertNull(state.reservoirUnits)
        assertNull(state.activeBasalRate)
    }

    @Test fun disconnectClearsMeasurements() {
        val state = YpsoCommunityPumpState()
        state.setUnverifiedConnectionState(YpsoCommunityPumpState.Connection.CONNECTED)
        state.setUnverifiedConnectionState(YpsoCommunityPumpState.Connection.DISCONNECTED)
        assertEquals(YpsoCommunityPumpState.Connection.DISCONNECTED, state.connection)
        assertNull(state.batteryPercent)
        assertEquals(0L, state.lastStatusTime)
    }
}
