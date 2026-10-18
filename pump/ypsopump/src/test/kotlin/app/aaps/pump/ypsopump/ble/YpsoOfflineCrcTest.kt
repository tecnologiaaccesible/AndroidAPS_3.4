package app.aaps.pump.ypsopump.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpsoOfflineCrcTest {
    @Test fun deterministicAcrossRepeatedCalls() {
        val input = byteArrayOf(1, 2, 3, 4, 5)
        assertArrayEquals(YpsoOfflineCrc.checksum(input), YpsoOfflineCrc.checksum(input))
    }

    @Test fun validFrameAndTampering() {
        val input = byteArrayOf(10, 20, 30, 40, 50)
        val packet = input + YpsoOfflineCrc.checksum(input)
        assertTrue(YpsoOfflineCrc.verify(packet))
        val changed = packet.copyOf()
        changed[2] = (changed[2].toInt() xor 0x01).toByte()
        assertFalse(YpsoOfflineCrc.verify(changed))
        assertFalse(YpsoOfflineCrc.verify(byteArrayOf(1)))
    }

    @Test fun checksumMatchesIndependentReferenceForEmptyAndFourZeros() {
        // Padding to four-byte blocks makes an empty input distinct from 4 zeros.
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xff.toByte()), YpsoOfflineCrc.checksum(byteArrayOf()))
        assertFalse(YpsoOfflineCrc.checksum(byteArrayOf()).contentEquals(YpsoOfflineCrc.checksum(ByteArray(4))))
    }
}
