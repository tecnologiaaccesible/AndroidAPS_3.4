package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoOfflineStatusDecoderTest {
    @Test fun knownCodesUsePublishedResearchMapping() {
        val expected = listOf(
            YpsoOfflineStatusDecoder.DeliveryMode.STOPPED,
            YpsoOfflineStatusDecoder.DeliveryMode.BASAL,
            YpsoOfflineStatusDecoder.DeliveryMode.TEMPORARY_BASAL,
            YpsoOfflineStatusDecoder.DeliveryMode.FAST_BOLUS,
            YpsoOfflineStatusDecoder.DeliveryMode.EXTENDED_BOLUS,
            YpsoOfflineStatusDecoder.DeliveryMode.BOLUS_AND_BASAL,
            YpsoOfflineStatusDecoder.DeliveryMode.PRIMING,
            YpsoOfflineStatusDecoder.DeliveryMode.PAUSED
        )
        expected.forEachIndexed { code, mode ->
            val result = YpsoOfflineStatusDecoder.decode(byteArrayOf(code.toByte()))
            assertEquals(code, result.rawCode)
            assertEquals(mode, result.mode)
        }
    }

    @Test fun unknownCodesFailToUnknownRatherThanInventingDeliveryState() {
        val status = YpsoOfflineStatusDecoder.decode(byteArrayOf(0xff.toByte()))
        assertEquals(255, status.rawCode)
        assertEquals(YpsoOfflineStatusDecoder.DeliveryMode.UNKNOWN, status.mode)
    }

    @Test fun emptyPayloadIsRejected() {
        var rejected = false
        try { YpsoOfflineStatusDecoder.decode(byteArrayOf()) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
