package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoOfflineBolusNotificationTest {
    @Test fun readsTwoChannelsAndUnsignedLittleEndianSequences() {
        val data = byteArrayOf(
            4, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            3, 0x78, 0x56, 0x34, 0x12
        )
        val result = YpsoOfflineBolusNotification.decodeInnerPayload(data)
        assertEquals(YpsoOfflineBolusNotification.State.COMPLETED, result.fast.status)
        assertEquals(4294967295L, result.fast.sequence)
        assertEquals(YpsoOfflineBolusNotification.State.CANCELLED, result.slow.status)
        assertEquals(0x12345678L, result.slow.sequence)
    }

    @Test fun reportsUnknownRatherThanAssumingCompletion() {
        val result = YpsoOfflineBolusNotification.decodeInnerPayload(byteArrayOf(2, 0, 0, 0, 0, 127, 0, 0, 0, 0))
        assertEquals(YpsoOfflineBolusNotification.State.UNKNOWN, result.fast.status)
        assertEquals(YpsoOfflineBolusNotification.State.UNKNOWN, result.slow.status)
    }

    @Test fun rejectsTruncatedOrExtendedPayloads() {
        for (length in listOf(0, 9, 11, 13)) {
            var failed = false
            try { YpsoOfflineBolusNotification.decodeInnerPayload(ByteArray(length)) }
            catch (_: IllegalArgumentException) { failed = true }
            assertTrue("Length $length must be rejected", failed)
        }
    }
}
