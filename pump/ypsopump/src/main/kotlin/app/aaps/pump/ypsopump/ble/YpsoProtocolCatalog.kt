package app.aaps.pump.ypsopump.ble

import java.util.UUID

/**
 * Offline reference catalog only. No GATT, cryptography, key material or dosing.
 * Names are from SandraK82/ypsopump-research (YpsoCommandCodes 0..32);
 * UUID assignments to individual commands are NOT inferred without evidence.
 */
object YpsoProtocolCatalog {

    enum class Group { BASE, SETTINGS, HISTORY, CONTROL }

    enum class Validation {
        /** Defined in a research document; not exercised on this firmware. */
        RESEARCH_ONLY,
        /** UUID and GATT advertised capabilities found on firmware V05.02.04. */
        UUID_OBSERVED
    }

    data class ResearchCommand(val index: Int, val id: String, val group: Group)

    val researchCommands: List<ResearchCommand> = listOf(
        ResearchCommand(0, "PUMP_BASE_SERVICE_VERSION", Group.BASE),
        ResearchCommand(1, "MASTER_SOFTWARE_VERSION", Group.BASE),
        ResearchCommand(2, "SUPERVISOR_SOFTWARE_VERSION", Group.BASE),
        ResearchCommand(3, "AUTHORIZATION_PASSWORD", Group.BASE),
        ResearchCommand(4, "PUMP_COUNTRY_CODE", Group.BASE),
        ResearchCommand(5, "SETTINGS_SERVICE_VERSION", Group.SETTINGS),
        ResearchCommand(6, "SETTING_ID", Group.SETTINGS),
        ResearchCommand(7, "SETTING_VALUE", Group.SETTINGS),
        ResearchCommand(8, "SYSTEM_DATE", Group.SETTINGS),
        ResearchCommand(9, "SYSTEM_TIME", Group.SETTINGS),
        ResearchCommand(10, "HISTORY_SERVICE_VERSION", Group.HISTORY),
        ResearchCommand(11, "ALARM_ENTRY_COUNT", Group.HISTORY),
        ResearchCommand(12, "ALARM_ENTRY_INDEX", Group.HISTORY),
        ResearchCommand(13, "ALARM_ENTRY_VALUE", Group.HISTORY),
        ResearchCommand(14, "EVENT_ENTRY_COUNT", Group.HISTORY),
        ResearchCommand(15, "EVENT_ENTRY_INDEX", Group.HISTORY),
        ResearchCommand(16, "EVENT_ENTRY_VALUE", Group.HISTORY),
        ResearchCommand(17, "SYSTEM_ENTRY_COUNT", Group.HISTORY),
        ResearchCommand(18, "COMPLAINT_ENTRY_COUNT", Group.HISTORY),
        ResearchCommand(19, "SYSTEM_ENTRY_INDEX", Group.HISTORY),
        ResearchCommand(20, "COMPLAINT_ENTRY_INDEX", Group.HISTORY),
        ResearchCommand(21, "SYSTEM_ENTRY_VALUE", Group.HISTORY),
        ResearchCommand(22, "COMPLAINT_ENTRY_VALUE", Group.HISTORY),
        ResearchCommand(23, "COUNTER_ID", Group.HISTORY),
        ResearchCommand(24, "COUNTER_VALUE", Group.HISTORY),
        ResearchCommand(25, "CLEAR_HISTORY_PASSWORD", Group.HISTORY),
        ResearchCommand(26, "CONTROL_SERVICE_VERSION", Group.CONTROL),
        ResearchCommand(27, "START_STOP_BOLUS", Group.CONTROL),
        ResearchCommand(28, "GET_BOLUS_STATUS", Group.CONTROL),
        ResearchCommand(29, "START_STOP_TBR", Group.CONTROL),
        ResearchCommand(30, "GET_SYSTEM_STATUS", Group.CONTROL),
        ResearchCommand(31, "BOLUS_STATUS_NOTIFICATION", Group.CONTROL),
        ResearchCommand(32, "VIRTUAL_CHAR", Group.CONTROL)
    )

    val observedAuthorizationUuid: UUID =
        UUID.fromString("669a0c20-0008-969e-e211-fcbeb2147bc5")

    /**
     * A GATT characteristic advertising WRITE is not an authenticated command,
     * and must never be called solely based on its name/capabilities.
     */
    fun canDispatchCommand(command: ResearchCommand): Boolean = false

    fun validationForObservedAuthorization(): Validation = Validation.UUID_OBSERVED
}
