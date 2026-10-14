package app.aaps.pump.ypsopump

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Separate GENERAL diagnostic plugin: never registers as an insulin pump driver.
 */
@Singleton
class YpsoLabPlugin @Inject constructor(
    logger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(YpsoLabFragment::class.java.name)
        .pluginName(R.string.ypsolab_title)
        .shortName(R.string.ypsolab_short_name)
        .description(R.string.ypsolab_description)
        .enableByDefault(true)
        .visibleByDefault(true),
    logger, rh
)
