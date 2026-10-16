package app.aaps.pump.ypsopump.ble

/**
 * Pure offline test model. It is NOT a YpsoPump wire-protocol implementation,
 * does not accept or verify real crypto, and has no BLE/insulin-command access.
 * A fixture can model session state transitions; no input authorizes real pump control.
 */
object YpsoOfflineSessionModel {

    sealed class Event {
        data object Connected : Event()
        data object BluetoothBonded : Event()
        data object ServerAttestationObserved : Event()
        data object SimulatedKeyExchangeSucceeded : Event()
        data class SimulatedInboundCounter(val counter: Long) : Event()
        data object Disconnected : Event()
        data object InvalidMessage : Event()
    }

    enum class State { DISCONNECTED, CONNECTED, BONDED, SIMULATED_AUTHENTICATED, BLOCKED }

    data class Snapshot(val state: State = State.DISCONNECTED, val lastInboundCounter: Long? = null)

    /**
     * State machine is deliberately restrictive. No real pump authorization is inferred.
     * "ServerAttestationObserved" alone cannot progress authentication.
     */
    fun transition(current: Snapshot, event: Event): Snapshot = when (event) {
        Event.Disconnected -> Snapshot()
        Event.InvalidMessage -> Snapshot(State.BLOCKED)
        Event.Connected -> if (current.state == State.DISCONNECTED) Snapshot(State.CONNECTED) else current
        Event.BluetoothBonded -> if (current.state == State.CONNECTED) Snapshot(State.BONDED) else current
        Event.ServerAttestationObserved -> current
        Event.SimulatedKeyExchangeSucceeded ->
            if (current.state == State.BONDED) Snapshot(State.SIMULATED_AUTHENTICATED) else current
        is Event.SimulatedInboundCounter -> {
            if (current.state != State.SIMULATED_AUTHENTICATED ||
                event.counter < 0 ||
                (current.lastInboundCounter != null && event.counter <= current.lastInboundCounter)
            ) Snapshot(State.BLOCKED)
            else current.copy(lastInboundCounter = event.counter)
        }
    }

    /** Never authorizes an operation on physical hardware, even in simulated state. */
    fun permitsPhysicalPumpCommand(snapshot: Snapshot): Boolean = false
}
