package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YpsoOfflineSessionModelTest {
    private val model = YpsoOfflineSessionModel

    @Test fun bondingAndAttestationAreNotAuthentication() {
        val start = YpsoOfflineSessionModel.Snapshot()
        val connected = model.transition(start, YpsoOfflineSessionModel.Event.Connected)
        val bonded = model.transition(connected, YpsoOfflineSessionModel.Event.BluetoothBonded)
        assertEquals(YpsoOfflineSessionModel.State.BONDED, bonded.state)
        val attestation = model.transition(bonded, YpsoOfflineSessionModel.Event.ServerAttestationObserved)
        assertEquals(YpsoOfflineSessionModel.State.BONDED, attestation.state)
        assertFalse(model.permitsPhysicalPumpCommand(attestation))
    }

    @Test fun simulatedSuccessIsNotHardwareAuthorization() {
        var state = model.transition(YpsoOfflineSessionModel.Snapshot(), YpsoOfflineSessionModel.Event.Connected)
        state = model.transition(state, YpsoOfflineSessionModel.Event.BluetoothBonded)
        state = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedKeyExchangeSucceeded)
        assertEquals(YpsoOfflineSessionModel.State.SIMULATED_AUTHENTICATED, state.state)
        assertFalse(model.permitsPhysicalPumpCommand(state))
        state = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedInboundCounter(1))
        assertEquals(1L, state.lastInboundCounter)
        state = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedInboundCounter(2))
        assertEquals(2L, state.lastInboundCounter)
        assertFalse(model.permitsPhysicalPumpCommand(state))
    }

    @Test fun replayedOrNegativeCountersAreRejected() {
        var state = model.transition(YpsoOfflineSessionModel.Snapshot(), YpsoOfflineSessionModel.Event.Connected)
        state = model.transition(state, YpsoOfflineSessionModel.Event.BluetoothBonded)
        state = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedKeyExchangeSucceeded)
        state = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedInboundCounter(2))
        val replayed = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedInboundCounter(2))
        assertEquals(YpsoOfflineSessionModel.State.BLOCKED, replayed.state)
        val negative = model.transition(state, YpsoOfflineSessionModel.Event.SimulatedInboundCounter(-1))
        assertEquals(YpsoOfflineSessionModel.State.BLOCKED, negative.state)
    }

    @Test fun malformedMessageBlocksAndDisconnectClearsState() {
        val invalid = model.transition(YpsoOfflineSessionModel.Snapshot(), YpsoOfflineSessionModel.Event.InvalidMessage)
        assertEquals(YpsoOfflineSessionModel.State.BLOCKED, invalid.state)
        val disconnected = model.transition(invalid, YpsoOfflineSessionModel.Event.Disconnected)
        assertEquals(YpsoOfflineSessionModel.Snapshot(), disconnected)
        assertFalse(model.permitsPhysicalPumpCommand(disconnected))
    }

    @Test fun neverAcceptSimulatedSuccessBeforeBonding() {
        val state = model.transition(YpsoOfflineSessionModel.Snapshot(), YpsoOfflineSessionModel.Event.SimulatedKeyExchangeSucceeded)
        assertEquals(YpsoOfflineSessionModel.State.DISCONNECTED, state.state)
        assertFalse(model.permitsPhysicalPumpCommand(state))
    }
}
