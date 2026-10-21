package app.aaps.pump.ypsopump.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Independently published XChaCha20-Poly1305 vector:
 * https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-xchacha-02#appendix-A.1
 * An Internet-Draft test vector, not a YpsoPump-specific protocol fixture.
 */
class XChaChaPublishedVectorTest {
    private fun hex(value: String): ByteArray =
        value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun matchesPublishedCiphertextAndAuthenticationTag() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("404142434445464748494a4b4c4d4e4f5051525354555657")
        val aad = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext = hex(
            "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f66" +
            "202739393a204966204920636f756c64206f6666657220796f75206f6e6c79206f6e6520" +
            "74697020666f7220746865206675747572652c2073756e73637265656e20776f756c6420" +
            "62652069742e"
        )
        val ciphertextAndTag = hex(
            "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
            "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
            "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
            "21f9664c97637da9768812f615c68b13b52ec0875924c1c7987947deafd8780acf49"
        )
        assertArrayEquals(ciphertextAndTag, XChaCha20Poly1305Lab.seal(key, nonce, plaintext, aad))
        assertArrayEquals(plaintext, XChaCha20Poly1305Lab.open(key, nonce, ciphertextAndTag, aad))
    }
}
