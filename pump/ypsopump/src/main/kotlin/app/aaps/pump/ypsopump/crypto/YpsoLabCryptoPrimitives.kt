package app.aaps.pump.ypsopump.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline cryptographic reference checks using standard JCA operations.
 * These primitives are NOT YpsoPump authentication, key exchange, or packet
 * encryption. They have no Bluetooth, network, credential or dosing access.
 */
object YpsoLabCryptoPrimitives {
    fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    /** Constant-work equality for two public-test fixture outputs of equal length. */
    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean =
        MessageDigest.isEqual(left, right)
}
