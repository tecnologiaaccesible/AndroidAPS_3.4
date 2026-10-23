package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class YpsoGattReadResponseGateTest {
    @Test fun onlyRequestedCharacteristicCanBeConsumedOnce() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        assertFalse(gate.consume(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.manufacturer))
        assertFalse(gate.consume(YpsoBleUuids.manufacturer))
    }

    @Test fun rejectsProtectedOrUnknownCharacteristic() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        val authorization = UUID.fromString("669a0c20-0008-969e-e211-fcbeb2147bc5")
        assertFalse(gate.beginRead(authorization))
        assertFalse(gate.consume(authorization))
        assertFalse(gate.beginRead(UUID.fromString("00000000-0000-0000-0000-000000000001")))
    }

    @Test fun resetRejectsLateCallbackAfterDisconnect() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        gate.reset()
        assertFalse(gate.consume(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.model))
    }

    @Test fun permitsOnlyOneInFlightRead() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        assertFalse(gate.beginRead(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
    }

    @Test fun expiredReadRejectsLateResponse() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        val request = gate.currentReadId()!!
        assertTrue(gate.expire(request))
        assertFalse(gate.consume(YpsoBleUuids.manufacturer))
        assertFalse(gate.expire(request))
    }

    @Test fun oldTimeoutCannotCancelNextRead() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        val first = gate.currentReadId()!!
        assertTrue(gate.consume(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
        val second = gate.currentReadId()!!
        assertFalse(gate.expire(first))
        assertTrue(second > first)
        assertTrue(gate.consume(YpsoBleUuids.model))
    }

    @Test fun resetInvalidatesOldTimeoutEvenForSameUuid() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        val old = gate.currentReadId()!!
        gate.reset()
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        val fresh = gate.currentReadId()!!
        assertTrue(fresh > old)
        assertFalse(gate.expire(old))
        assertTrue(gate.consume(YpsoBleUuids.manufacturer))
    }

    @Test fun noTimeoutTokenAfterCallbackOrReset() {
        val gate = YpsoGattReadResponseGate().apply {
            configureAllowedReads(YpsoBleUuids.readableCharacteristics.keys)
        }
        assertTrue(gate.beginRead(YpsoBleUuids.model))
        assertTrue(gate.consume(YpsoBleUuids.model))
        assertTrue(gate.currentReadId() == null)
        assertTrue(gate.beginRead(YpsoBleUuids.firmware))
        gate.reset()
        assertTrue(gate.currentReadId() == null)
    }
    @Test fun noReadsAreAllowedBeforeInventoryConfigurationOrAfterReset() {
        val gate = YpsoGattReadResponseGate()
        assertFalse(gate.beginRead(YpsoBleUuids.manufacturer))
        gate.configureAllowedReads(setOf(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.manufacturer))
        gate.reset()
        assertFalse(gate.beginRead(YpsoBleUuids.manufacturer))
        gate.configureAllowedReads(setOf(YpsoBleUuids.model))
        assertFalse(gate.beginRead(YpsoBleUuids.manufacturer))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
    }

    @Test fun inventoryCannotGrantUnknownOrProtectedCharacteristic() {
        val gate = YpsoGattReadResponseGate()
        val protectedUuid = UUID.fromString("669a0c20-0008-969e-e211-fcbeb2147bc5")
        gate.configureAllowedReads(setOf(YpsoBleUuids.firmware, protectedUuid))
        assertFalse(gate.beginRead(protectedUuid))
        assertFalse(gate.beginRead(YpsoBleUuids.serial))
        assertTrue(gate.beginRead(YpsoBleUuids.firmware))
    }

    @Test fun inventoryCannotChangeDuringPendingRead() {
        val gate = YpsoGattReadResponseGate()
        gate.configureAllowedReads(setOf(YpsoBleUuids.model))
        assertTrue(gate.beginRead(YpsoBleUuids.model))
        var rejected = false
        try {
            gate.configureAllowedReads(setOf(YpsoBleUuids.serial))
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(gate.consume(YpsoBleUuids.model))
        assertFalse(gate.beginRead(YpsoBleUuids.serial))
    }

}
