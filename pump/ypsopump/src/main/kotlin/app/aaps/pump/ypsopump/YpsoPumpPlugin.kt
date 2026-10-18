package app.aaps.pump.ypsopump

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.ypsopump.ble.YpsoBleDiagnostics
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Experimental YpsoPump AAPS 3.4 adapter. Based on architectural investigation of
 * SandraK82/ypsopump-research (MIT). Not authorized or validated for therapy.
 * Unlike the community prototype, it cannot claim to be connected, initialized,
 * or delivering insulin before an authenticated and tested transport is integrated.
 */
@Singleton
class YpsoPumpPlugin @Inject constructor(
    logger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val bleDiagnostics: YpsoBleDiagnostics,
    private val results: Provider<PumpEnactResult>
) : PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(YpsoLabFragment::class.java.name)
        .pluginName(R.string.ypsopump_experimental)
        .shortName(R.string.ypsopump_experimental)
        .description(R.string.ypsopump_experimental_description),
    ownPreferences = emptyList(),
    aapsLogger = logger,
    rh = rh,
    preferences = preferences,
    commandQueue = commandQueue
), Pump {
    override val pumpDescription: PumpDescription = PumpDescription().apply {
        isBolusCapable = false
        isTempBasalCapable = false
        isExtendedBolusCapable = false
        isSetBasalProfileCapable = false
    }
    override val lastDataTime: Long = 0L
    override val lastBolusTime: Long? = null
    override val lastBolusAmount: Double? = null
    override val baseBasalRate: Double = 0.0
    override val reservoirLevel: Double = -1.0
    override val batteryLevel: Int? = null
    override val isFakingTempsByExtendedBoluses: Boolean = false

    override fun isInitialized() = false
    override fun isSuspended() = true
    override fun isBusy() = true
    // BLE link state is observable, but initialization remains false until auth + status validation.
    override fun isConnected() = bleDiagnostics.linkConnected
    override fun isConnecting() = false
    override fun isHandshakeInProgress() = false
    override fun connect(reason: String) = Unit
    override fun disconnect(reason: String) = Unit
    override fun stopConnecting() = Unit
    override fun getPumpStatus(reason: String) = Unit

    private fun blocked(): PumpEnactResult =
        results.get().success(false).enacted(false).comment("YpsoPump experimental: delivery disabled; authenticated transport not validated")

    override fun setNewBasalProfile(profile: Profile) = blocked()
    override fun isThisProfileSet(profile: Profile) = false
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo) = blocked()
    override fun stopBolusDelivering() = Unit
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType) = blocked()
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType) = blocked()
    override fun cancelTempBasal(enforceNew: Boolean) = blocked()
    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int) = blocked()
    override fun cancelExtendedBolus() = blocked()
    override fun manufacturer() = ManufacturerType.Ypsomed
    override fun model() = PumpType.YPSOPUMP
    override fun serialNumber() = "not-authenticated"
    override fun loadTDDs() = blocked()
    override fun canHandleDST() = false
}
