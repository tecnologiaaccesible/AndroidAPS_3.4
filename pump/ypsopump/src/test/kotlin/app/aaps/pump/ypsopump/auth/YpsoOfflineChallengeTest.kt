package app.aaps.pump.ypsopump.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YpsoOfflineChallengeTest {
    @Test fun separatesChallengeAndPublicKeyWithoutSharingInputStorage() {
        val fixture = ByteArray(64) { it.toByte() }
        val parsed = YpsoOfflineChallenge.parse(fixture)
        assertArrayEquals(ByteArray(32) { it.toByte() }, parsed.challenge)
        assertArrayEquals(ByteArray(32) { (it + 32).toByte() }, parsed.pumpPublicKey)
        fixture.fill(0)
        assertEquals(0, parsed.challenge[0].toInt())
        assertEquals(32, parsed.pumpPublicKey[0].toInt())
        assertFalse(parsed.cleared())
    }

    @Test fun rejectsAllIncorrectLengths() {
        for (size in listOf(0, 32, 63, 65, 116)) {
            var rejected = false
            try { YpsoOfflineChallenge.parse(ByteArray(size)) }
            catch (_: IllegalArgumentException) { rejected = true }
            assert(rejected) { "Expected rejection for $size bytes" }
        }
    }
}
