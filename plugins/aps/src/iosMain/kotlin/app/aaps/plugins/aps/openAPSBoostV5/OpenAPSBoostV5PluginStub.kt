package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginOpenAPS
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * iOS-only stub for Boost V5 APS plugin.
 *
 * The real [OpenAPSBoostV5Plugin] lives in androidMain — it depends on Android-only APIs
 * (ExecutorService, Context.assets for the risk/meal ML models, the old Boost engine that also
 * delegates to Android components) and the iOS framework never compiles those source sets.
 *
 * This stub exists purely so that on an iOS follower build (`config.APS = false`):
 *   1. Configuration → APS category lists "Boost V5" alongside SMB / AMA / AutoISF (IntKey 236)
 *   2. The user can select it — the selection goes through PluginType.APS.selectionSyncs = true
 *      and lands on the master (Android) where the *real* OpenAPSBoostV5Plugin (also IntKey 236)
 *      is swapped in as the active dosing engine.
 *   3. invoke() is never called on iOS — the loop is disabled on a follower.
 *
 * Why the same IntKey (236)? Because PluginType.APS stores the active selection as an int key
 * (see ActivePluginSelectionType.Aps), and both platforms need the same number so the sync
 * round-trip — iOS writes 236 → Nightscout → master reads 236 → master looks up IntKey 236 →
 * finds the real plugin — works without any platform-specific translation.
 */
@ContributesIntoMap(AppScope::class, binding = binding<app.aaps.core.interfaces.plugin.PluginBase>())
@MetroIntKey(236)
@SingleIn(AppScope::class)
@Inject
class OpenAPSBoostV5PluginStub @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    notificationManager: NotificationManager,
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.APS)
        .icon(IcPluginOpenAPS)
        .pluginName("Boost V5")
        .preferencesVisibleInSimpleMode(false)
        .showInList { true }
        .description("Boost V5 — Observe-Confirm-Commit dosing engine (iOS client: display only, engine runs on master)"),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, notificationManager
), APS {

    override val algorithm = APSResult.Algorithm.BOOST
    override var lastAPSResult: APSResult? = null
    override var lastAPSRun: Long = 0L

    /**
     * Never called on an iOS follower — CalculationLoop skips [APS.invoke] while config.APS is
     * false. Included only to satisfy the [APS] interface so this stub can be a selectable
     * entry in Configuration's APS category.
     */
    override suspend fun invoke(initiator: String, tempBasalFallback: Boolean) {
        // no-op on iOS client; master runs the engine
    }
}
