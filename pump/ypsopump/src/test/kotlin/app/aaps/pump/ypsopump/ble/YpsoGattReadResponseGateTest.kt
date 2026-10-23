package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class YpsoGattReadResponseGateTest {
    @Test fun onlyRequestedCharacteristicCanBeConsumedOnce() {
        val gate = YpsoGattReadResponseGate()
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        assertFalse(gate.consume(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.manufacturer))
        assertFalse(gate.consume(YpsoBleUuids.manufacturer))
    }

    @Test fun rejectsProtectedOrUnknownCharacteristic() {
        val gate = YpsoGattReadResponseGate()
        val authorization = UUID.fromString("669a0c20-0008-969e-e211-fcbeb2147bc5")
        assertFalse(gate.beginRead(authorization))
        assertFalse(gate.consume(authorization))
        assertFalse(gate.beginRead(UUID.fromString("00000000-0000-0000-0000-000000000001")))
    }

    @Test fun resetRejectsLateCallbackAfterDisconnect() {
        val gate = YpsoGattReadResponseGate()
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        gate.reset()
        assertFalse(gate.consume(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.model))
    }

    @Test fun permitsOnlyOneInFlightRead() {
        val gate = YpsoGattReadResponseGate()
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        assertFalse(gate.beginRead(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
    }
}
