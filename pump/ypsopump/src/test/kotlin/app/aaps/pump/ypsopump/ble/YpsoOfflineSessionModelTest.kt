package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YpsoOfflineSessionModelTest {
    private val model = YpsoOfflineSessionModel

    @Test fun bondingAndAttestationAreNotAuthentication() {
        val start = model.Snapshot()
        val connected = model.transition(start, model.Event.Connected)
        val bonded = model.transition(connected, model.Event.BluetoothBonded)
        assertEquals(model.State.BONDED, bonded.state)
        val attestation = model.transition(bonded, model.Event.ServerAttestationObserved)
        assertEquals(model.State.BONDED, attestation.state)
        assertFalse(model.permitsPhysicalPumpCommand(attestation))
    }

    @Test fun simulatedSuccessIsNotHardwareAuthorization() {
        var state = model.transition(model.Snapshot(), model.Event.Connected)
        state = model.transition(state, model.Event.BluetoothBonded)
        state = model.transition(state, model.Event.SimulatedKeyExchangeSucceeded)
        assertEquals(model.State.SIMULATED_AUTHENTICATED, state.state)
        assertFalse(model.permitsPhysicalPumpCommand(state))
        state = model.transition(state, model.Event.SimulatedInboundCounter(1))
        assertEquals(1L, state.lastInboundCounter)
        state = model.transition(state, model.Event.SimulatedInboundCounter(2))
        assertEquals(2L, state.lastInboundCounter)
        assertFalse(model.permitsPhysicalPumpCommand(state))
    }

    @Test fun replayedOrNegativeCountersAreRejected() {
        var state = model.transition(model.Snapshot(), model.Event.Connected)
        state = model.transition(state, model.Event.BluetoothBonded)
        state = model.transition(state, model.Event.SimulatedKeyExchangeSucceeded)
        state = model.transition(state, model.Event.SimulatedInboundCounter(2))
        val replayed = model.transition(state, model.Event.SimulatedInboundCounter(2))
        assertEquals(model.State.BLOCKED, replayed.state)
        val negative = model.transition(state, model.Event.SimulatedInboundCounter(-1))
        assertEquals(model.State.BLOCKED, negative.state)
    }

    @Test fun malformedMessageBlocksAndDisconnectClearsState() {
        val invalid = model.transition(model.Snapshot(), model.Event.InvalidMessage)
        assertEquals(model.State.BLOCKED, invalid.state)
        val disconnected = model.transition(invalid, model.Event.Disconnected)
        assertEquals(model.Snapshot(), disconnected)
        assertFalse(model.permitsPhysicalPumpCommand(disconnected))
    }

    @Test fun neverAcceptSimulatedSuccessBeforeBonding() {
        val state = model.transition(model.Snapshot(), model.Event.SimulatedKeyExchangeSucceeded)
        assertEquals(model.State.DISCONNECTED, state.state)
        assertFalse(model.permitsPhysicalPumpCommand(state))
    }
}
