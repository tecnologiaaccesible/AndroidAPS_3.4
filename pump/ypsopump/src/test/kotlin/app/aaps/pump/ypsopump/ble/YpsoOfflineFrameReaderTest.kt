package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoOfflineFrameReaderTest {
    @Test fun singleFrame() {
        assertArrayEquals(byteArrayOf(4, 5), YpsoOfflineFrameReader.reassemble(listOf(byteArrayOf(0x11, 4, 5))))
    }

    @Test fun multiFrame() {
        val first = byteArrayOf(0x12) + ByteArray(19) { it.toByte() }
        val second = byteArrayOf(0x22) + byteArrayOf(19, 20, 21)
        assertArrayEquals(ByteArray(22) { it.toByte() }, YpsoOfflineFrameReader.reassemble(listOf(first, second)))
    }

    @Test fun emptyMessageSentinel() {
        assertArrayEquals(byteArrayOf(), YpsoOfflineFrameReader.reassemble(listOf(byteArrayOf(0x10))))
    }

    @Test fun invalidFramesRejected() {
        val validFirst = byteArrayOf(0x12) + ByteArray(19)
        val cases = listOf(
            emptyList(),
            listOf(byteArrayOf()),
            listOf(byteArrayOf(0x11) + ByteArray(20)),
            listOf(byteArrayOf(0x11)),
            listOf(validFirst),
            listOf(validFirst, byteArrayOf(0x12, 4)),
            listOf(byteArrayOf(0x12, 1), byteArrayOf(0x22, 2)),
            listOf(validFirst, byteArrayOf(0x32, 3)),
            listOf(byteArrayOf(0x10, 1))
        )
        for (frames in cases) {
            var rejected = false
            try { YpsoOfflineFrameReader.reassemble(frames) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Malformed frame sequence must be rejected", rejected)
        }
    }
}
