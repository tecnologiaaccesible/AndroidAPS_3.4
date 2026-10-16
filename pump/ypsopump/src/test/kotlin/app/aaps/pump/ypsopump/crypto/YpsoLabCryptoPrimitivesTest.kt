package app.aaps.pump.ypsopump.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Public, non-secret NIST/RFC-derived fixtures; never any pump credential. */
class YpsoLabCryptoPrimitivesTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun sha256AbcMatchesKnownDigest() {
        assertArrayEquals(
            hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            YpsoLabCryptoPrimitives.sha256("abc".toByteArray(Charsets.US_ASCII))
        )
    }

    @Test fun hmacSha256Rfc4231CaseOne() {
        val key = ByteArray(20) { 0x0b.toByte() }
        assertArrayEquals(
            hex("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"),
            YpsoLabCryptoPrimitives.hmacSha256(key, "Hi There".toByteArray(Charsets.US_ASCII))
        )
    }

    @Test fun equalityChecksContentsWithoutReferenceIdentity() {
        assertTrue(YpsoLabCryptoPrimitives.constantTimeEquals(byteArrayOf(1,2,3), byteArrayOf(1,2,3)))
        assertFalse(YpsoLabCryptoPrimitives.constantTimeEquals(byteArrayOf(1,2,3), byteArrayOf(1,2,4)))
        assertFalse(YpsoLabCryptoPrimitives.constantTimeEquals(byteArrayOf(1,2,3), byteArrayOf(1,2)))
    }
}
