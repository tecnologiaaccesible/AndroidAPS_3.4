package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoOfflineFixtureValidatorTest {
    private val validator = YpsoOfflineFixtureValidator
    private val model = YpsoOfflineSessionModel

    private fun frame(counter: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        byteArrayOf('L'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), payload.size.toByte(), counter.toByte()) + payload

    private fun authenticated(): YpsoOfflineSessionModel.Snapshot {
        var snapshot = model.transition(model.Snapshot(), model.Event.Connected)
        snapshot = model.transition(snapshot, model.Event.BluetoothBonded)
        return model.transition(snapshot, model.Event.SimulatedKeyExchangeSucceeded)
    }

    @Test fun acceptsWellFormedSyntheticFrameOnlyInSimulatedState() {
        val result = validator.validate(frame(2, byteArrayOf(1, 2, 3)))
        assertEquals(validator.Result.Accepted(2, 3), result)
        val snapshot = validator.apply(authenticated(), frame(2))
        assertEquals(2L, snapshot.lastInboundCounter)
        assertFalse(model.permitsPhysicalPumpCommand(snapshot))
    }

    @Test fun rejectsTruncationAndIncorrectSize() {
        assertEquals(validator.Result.Rejected(validator.Reason.TRUNCATED), validator.validate(byteArrayOf(76, 65, 66, 0)))
        assertEquals(validator.Result.Rejected(validator.Reason.LENGTH_MISMATCH), validator.validate(byteArrayOf(76, 65, 66, 2, 1)))
    }

    @Test fun rejectsWrongMagicZeroCounterAndExcessiveLength() {
        assertEquals(validator.Result.Rejected(validator.Reason.WRONG_MAGIC), validator.validate(byteArrayOf(0, 0, 0, 0, 1)))
        assertEquals(validator.Result.Rejected(validator.Reason.INVALID_COUNTER), validator.validate(frame(0)))
        assertEquals(validator.Result.Rejected(validator.Reason.PAYLOAD_TOO_LONG),
            validator.validate(byteArrayOf(76, 65, 66, 65, 1)))
    }

    @Test fun failsClosedAfterMalformedOrReplayedFrame() {
        val base = authenticated()
        val malformed = validator.apply(base, frame(1, byteArrayOf(5)).copyOfRange(0, 5))
        assertEquals(model.State.BLOCKED, malformed.state)
        val first = validator.apply(base, frame(3))
        val replay = validator.apply(first, frame(3))
        assertEquals(model.State.BLOCKED, replay.state)
        assertFalse(model.permitsPhysicalPumpCommand(replay))
    }
}
