package app.aaps.pump.ypsopump.auth

/**
 * Strict, offline parser for a 64-byte YpsoPump key-exchange fixture, as
 * described by SandraK82/ypsopump-research (MIT).
 *
 * Parsing is NOT authorization. This class never generates credentials,
 * transmits messages or stores secret material.
 */
object YpsoOfflineChallenge {
    const val SIZE = 64
    const val FIELD_SIZE = 32

    data class Parsed(val challenge: ByteArray, val pumpPublicKey: ByteArray) {
        fun cleared(): Boolean = challenge.all { it == 0.toByte() } &&
            pumpPublicKey.all { it == 0.toByte() }
    }

    fun parse(bytes: ByteArray): Parsed {
        require(bytes.size == SIZE) { "Expected 64-byte key exchange payload" }
        return Parsed(bytes.copyOfRange(0, FIELD_SIZE), bytes.copyOfRange(FIELD_SIZE, SIZE))
    }
}
