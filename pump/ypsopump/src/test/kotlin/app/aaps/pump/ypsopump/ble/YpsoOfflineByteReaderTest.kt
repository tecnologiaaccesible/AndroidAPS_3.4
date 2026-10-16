package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class YpsoOfflineByteReaderTest {
    private val reader = YpsoOfflineByteReader

    @Test fun decodesExplicitEndiannessAndUnsignedFields() {
        val data = byteArrayOf(0x78, 0x56, 0x34, 0x12, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        assertEquals(0x5678, reader.u16Le(data, 0))
        assertEquals(0x12345678L, reader.u32Le(data, 0))
        assertEquals(0x78563412L, reader.u32Be(data, 0))
        assertEquals(0xffffffffL, reader.u32Le(data, 4))
        assertEquals(0x78563412ffffffffuL, reader.u64Be(data, 0))
    }

    @Test fun readsIndependentCopyWithoutChangingSource() {
        val source = byteArrayOf(1, 2, 3, 4)
        val extracted = reader.slice(source, 1, 2)
        assertArrayEquals(byteArrayOf(2, 3), extracted)
        extracted[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), source)
    }

    @Test fun rejectsOutOfBoundsAndOverflowingOffsets() {
        val source = byteArrayOf(1, 2, 3)
        val attempts = listOf<() -> Any>(
            { reader.u16Le(source, -1) },
            { reader.u32Le(source, 0) },
            { reader.u32Be(source, Int.MAX_VALUE) },
            { reader.u64Be(source, 0) },
            { reader.slice(source, 0, Int.MAX_VALUE) },
            { reader.slice(source, 1, -1) }
        )
        attempts.forEach { attempt ->
            var rejected = false
            try {
                attempt()
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertEquals(true, rejected)
        }
    }
}
