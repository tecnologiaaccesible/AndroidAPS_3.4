package app.aaps.pump.ypsopump.ble

import java.util.UUID

/**
 * Experimental characteristic inventory derived from SandraK82/ypsopump-research
 * (MIT). Self-contained so the read-only YpsoPump Lab can compile without
 * importing AAPS pump-driver packages. No BLE commands are sent from here.
 *
 * Research mappings are hypotheses; GATT properties must be checked at runtime.
 */
object GattAttributes {
    val SCAN_FILTER_SERVICE: UUID = UUID.fromString("669a0c20-0008-969e-e211-ffffffffffff")
    val GENERAL_SERVICE: UUID = UUID.fromString("669a0c20-0008-969e-e211-eeeeeeeeeeee")
    val DATA_CHAR_A: UUID = UUID.fromString("669a0c20-0008-969e-e211-fcff0000000a")
    val DATA_CHAR_B: UUID = UUID.fromString("669a0c20-0008-969e-e211-fcff0000000b")
    val CONTROL_CHAR: UUID = UUID.fromString("669a0c20-0008-969e-e211-fcff000000ff")
    val PUMP_SPECIFIC: UUID = UUID.fromString("669a0c20-0008-969e-e211-fcbeb0147bc5")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Diagnostic correlation only. Valid index range is the 33 research indices 0..32. */
    class CharacteristicMap {
        private val commandToUuid = mutableMapOf<Int, UUID>()
        private val uuidToCommand = mutableMapOf<UUID, Int>()

        fun put(commandIndex: Int, uuid: UUID) {
            require(commandIndex in 0..32) { "Unknown research command index" }
            commandToUuid[commandIndex] = uuid
            uuidToCommand[uuid] = commandIndex
        }

        fun getUuid(commandIndex: Int): UUID? = commandToUuid[commandIndex]
        fun getCommand(uuid: UUID): Int? = uuidToCommand[uuid]
        val isEmpty: Boolean get() = commandToUuid.isEmpty()
    }
}
