package app.aaps.plugins.aps.openAPSBoostV5

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcPluginOpenAPS
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.aps.ApsStrings
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
 * (ExecutorService, Context.assets for the risk/meal ML models, the old Boost engine) and the
 * iOS framework never compiles those source sets.
 *
 * This stub does three things on an iOS follower build (`config.APS = false`):
 *   1. Configuration → APS category lists "Boost V5" alongside SMB / AMA / AutoISF (IntKey 236)
 *   2. The user can select it — the selection goes through PluginType.APS.selectionSyncs = true
 *      and lands on the master (Android) where the *real* OpenAPSBoostV5Plugin (also IntKey 236)
 *      is swapped in as the active dosing engine.
 *   3. The user can edit *all* Boost V5 algorithm parameters here — every key is Bidirectional
 *      so edits round-trip to the master and take effect on the next loop cycle.
 *
 * Why the same IntKey (236)? Because PluginType.APS stores the active selection as an int key
 * (see ActivePluginSelectionType.Aps), and both platforms need the same number so the sync
 * round-trip — iOS writes 236 → Nightscout → master reads 236 → master looks up IntKey 236 →
 * finds the real plugin — works without any platform-specific translation.
 *
 * invoke() is never called on iOS — CalculationLoop skips [APS.invoke] while config.APS is false.
 *
 * Engine-level settings (Dynamic ISF, night mode, activity/HR, safety, sensor checks) live in
 * OpenAPSBoostPlugin (old Boost engine, androidMain). We inline every Boost-specific key that
 * has Bidirectional sync here so iOS has the same parameter surface as Android master. Android-
 * only intent keys (HealthConnect permission, BoostSensorCheckScreen) are skipped — iOS cannot
 * launch Android activities. Generic OpenAPS keys (ApsSmbMaxIob, ApsUseAutosens, …) that are
 * already reachable from SMB/AutoISF plugins are also skipped to avoid duplication.
 */
@ContributesIntoMap(AppScope::class, binding = binding<app.aaps.core.interfaces.plugin.PluginBase>())
@MetroIntKey(236)
@SingleIn(AppScope::class)
@Inject
class OpenAPSBoostV5PluginStub(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    notificationManager: NotificationManager,
    config: Config,
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.APS)
        .icon(IcPluginOpenAPS)
        .pluginName(ApsStrings.openaps_boost_v5)
        .preferencesVisibleInSimpleMode(true)
        .showInList { true }
        .description(ApsStrings.description_boost_v5)
        // The only APS `.setDefault()` in the Android build sits on the real Boost V5 plugin, which
        // iosMain does not compile. Without a default here `PluginStore.verifySelectionInCategories`
        // throws "Default plugin not found" out of `ConfigBuilderImpl.initialize()` and the app dies
        // during startup. A follower is the only iOS build there is, so client-only covers it - and
        // it keeps this stub from quietly becoming the default engine of a hypothetical iOS master.
        .setDefault(config.AAPSCLIENT),
    ownPreferences = ALL_BOOST_BIDIRECTIONAL_KEYS,
    aapsLogger, rh, preferences, notificationManager
), APS {

    override val algorithm = APSResult.Algorithm.BOOST
    override var lastAPSResult: APSResult? = null
    override var lastAPSRun: Long = 0L

    override fun getPreferenceScreenContent(): PreferenceSubScreenDef = PreferenceSubScreenDef(
        key = "openapsboostv5_settings",
        title = ApsStrings.openaps_boost_v5,
        items = listOf<PreferenceItem>(
            // ── Essentials ────────────────────────────────────────────
            DoubleKey.ApsBoostV5Aggression,
            DoubleKey.ApsBoostV5HypoCaution,
            DoubleKey.ApsBoostMaxIob,
            DoubleKey.ApsMaxBasal,
            BooleanKey.ApsBoostNightModeEnabled,
            UnitDoubleKey.ApsBoostNightModeBgOffset,
            BooleanKey.ApsUseSmb,
            BooleanKey.ApsUseUam,
            // ── Advanced ─────────────────────────────────────────────
            PreferenceSubScreenDef(
                key = "boost_advanced_settings",
                title = CoreUiStrings.advanced_settings_title,
                items = listOf<PreferenceItem>(
                    // V5/V6 core knobs
                    DoubleKey.ApsBoostV5Sensitivity,
                    DoubleKey.ApsBoostV5ConfirmedCapU,
                    DoubleKey.ApsBoostV5CommittedCapU,
                    BooleanKey.ApsBoostV5FastCarbConfirm,
                    BooleanKey.ApsBoostV5AggressiveEarlyConfirm,
                    BooleanKey.ApsBoostV5ComposedFloorActive,
                    BooleanKey.ApsBoostV5VelocityBudgetActive,
                    DoubleKey.ApsBoostV5PrimerCapU,
                    BooleanKey.ApsBoostV5PrimerBolusMode,
                    BooleanKey.ApsBoostV5ConfirmTranche,
                    DoubleKey.ApsBoostV5TrancheFraction,
                    DoubleKey.ApsBoostV5TrancheThreshold,
                    BooleanKey.ApsBoostV6PreMealTarget,
                    DoubleKey.ApsBoostV6PreMealTargetMgdl,
                    DoubleKey.ApsBoostV6PreMealLeadMin,

                    // V1 SMB sizing (V6 caps base SMB on these)
                    PreferenceSubScreenDef(
                        key = "boost_v1_smb_sizing",
                        title = ApsStrings.boost_v1_smb_sizing_title,
                        items = listOf<PreferenceItem>(
                            DoubleKey.ApsBoostInsulinReqPct,
                            DoubleKey.ApsBoostScale,
                        )
                    ),

                    // Shared Boost engine controls
                    BooleanKey.ApsBoostEnableCircadianIsf,
                    BooleanKey.ApsBoostAllowWithHighTt,

                    // Dynamic ISF
                    PreferenceSubScreenDef(
                        key = "boost_dynisf_settings",
                        title = ApsStrings.boost_dynisf_title,
                        items = listOf(
                            BooleanKey.ApsBoostUseTdd,
                            BooleanKey.ApsBoostAdjustSensitivity,
                            BooleanKey.ApsBoostAutosensWhenNoTdd,
                            UnitDoubleKey.ApsBoostDynIsfNormalTarget,
                            DoubleKey.ApsBoostDynIsfVelocity,
                            UnitDoubleKey.ApsBoostDynIsfBgCap,
                            IntKey.ApsBoostDynIsfAdjustmentFactor,
                        )
                    ),

                    // Exercise: sensor sources + step count + HR + post-exercise recovery
                    PreferenceSubScreenDef(
                        key = "boost_exercise_settings",
                        title = ApsStrings.boost_settings_exercise,
                        items = listOf<PreferenceItem>(
                            PreferenceSubScreenDef(
                                key = "boost_sensor_advanced_settings",
                                title = ApsStrings.boost_sensor_advanced_title,
                                summary = ApsStrings.boost_sensor_advanced_summary,
                                items = listOf(
                                    BooleanKey.ApsBoostPhoneStepsEnabled,
                                    BooleanKey.ApsBoostWatchHrEnabled,
                                    IntKey.ApsBoostWatchHrIntervalMin,
                                )
                            ),
                            PreferenceSubScreenDef(
                                key = "boost_stepcount_settings",
                                title = ApsStrings.boost_stepcount_settings_title,
                                summary = ApsStrings.boost_stepcount_settings_summary,
                                items = listOf(
                                    IntKey.ApsBoostInactivitySteps,
                                    DoubleKey.ApsBoostInactivityPct,
                                    DoubleKey.ApsBoostSleepInHours,
                                    IntKey.ApsBoostSleepInSteps,
                                    IntKey.ApsBoostActivitySteps5,
                                    IntKey.ApsBoostActivitySteps15,
                                    IntKey.ApsBoostActivitySteps30,
                                    IntKey.ApsBoostActivitySteps60,
                                    DoubleKey.ApsBoostActivityPct,
                                )
                            ),
                            PreferenceSubScreenDef(
                                key = "boost_hr_integration_settings",
                                title = ApsStrings.boost_hr_integration_title,
                                summary = ApsStrings.boost_hr_integration_summary,
                                items = listOf(
                                    BooleanKey.ApsBoostHrIntegrationEnabled,
                                    IntKey.ApsBoostHrMaxBpm,
                                    IntKey.ApsBoostHrRestingBpm,
                                    IntKey.ApsBoostHrWindowMinutes,
                                    BooleanKey.ApsBoostHrStressDetection,
                                )
                            ),
                            PreferenceSubScreenDef(
                                key = "boost_post_exercise_recovery_settings",
                                title = ApsStrings.boost_post_exercise_recovery_title,
                                items = listOf(
                                    BooleanKey.ApsBoostPostExerciseRecoveryEnabled,
                                    DoubleKey.ApsBoostPostExerciseRecoveryHours,
                                    UnitDoubleKey.ApsBoostPostExerciseRecoveryTarget,
                                    DoubleKey.ApsBoostPostExerciseRecoveryScale,
                                    IntKey.ApsBoostPostExerciseMinDuration,
                                )
                            ),
                        )
                    ),

                    // Night mode
                    PreferenceSubScreenDef(
                        key = "boost_night_mode_settings",
                        title = ApsStrings.boost_night_mode_title,
                        items = listOf(
                            StringKey.ApsBoostNightModeStart,
                            StringKey.ApsBoostNightModeEnd,
                            BooleanKey.ApsBoostNightModeDisableWithCob,
                            BooleanKey.ApsBoostNightModeDisableWithLowTt,
                            BooleanKey.ApsBoostNightModeAutoBySleep,
                            IntKey.ApsBoostPreSleepLeadMin,
                            IntKey.ApsBoostSleepHysteresisMin,
                            IntKey.ApsBoostWakeHrHysteresisMin,
                            BooleanKey.ApsBoostHealthConnectHrEnabled,
                            IntKey.ApsBoostHealthConnectPollMin,
                            BooleanKey.ApsBoostActivityShadowEnabled,
                        )
                    ),

                    // Safety: cumulative 60-min SMB cap (the one safety key specific to Boost V5)
                    PreferenceSubScreenDef(
                        key = "boost_safety_settings",
                        title = ApsStrings.boost_settings_safety,
                        items = listOf(
                            DoubleKey.ApsBoostCumulativeSmbCap60Min,
                        )
                    ),

                    // Post-rescue trial
                    BooleanKey.ApsBoostPostRescueTightRampTrial,
                )
            )
        ),
        icon = pluginDescription.icon
    )

    /**
     * Never called on an iOS follower — CalculationLoop skips [APS.invoke] while config.APS is
     * false. Included only to satisfy the [APS] interface so this stub can be a selectable
     * entry in Configuration's APS category.
     */
    override suspend fun invoke(initiator: String, tempBasalFallback: Boolean) {
        // no-op on iOS client; master runs the engine
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = null

    companion object {
        /** Every Boost-specific key that has Bidirectional sync — iOS client edits these
         *  to configure the master Android device. Generic OpenAPS keys reachable from other
         *  plugins (SMB / AutoISF) are intentionally omitted. */
        private val ALL_BOOST_BIDIRECTIONAL_KEYS: List<app.aaps.core.keys.interfaces.NonPreferenceKey> = listOf(
            // Essentials
            DoubleKey.ApsBoostV5Aggression,
            DoubleKey.ApsBoostV5HypoCaution,
            DoubleKey.ApsBoostMaxIob,
            DoubleKey.ApsMaxBasal,
            BooleanKey.ApsBoostNightModeEnabled,
            UnitDoubleKey.ApsBoostNightModeBgOffset,
            BooleanKey.ApsUseSmb,
            BooleanKey.ApsUseUam,
            // V5/V6 core knobs
            DoubleKey.ApsBoostV5Sensitivity,
            DoubleKey.ApsBoostV5ConfirmedCapU,
            DoubleKey.ApsBoostV5CommittedCapU,
            BooleanKey.ApsBoostV5FastCarbConfirm,
            BooleanKey.ApsBoostV5AggressiveEarlyConfirm,
            BooleanKey.ApsBoostV5ComposedFloorActive,
            BooleanKey.ApsBoostV5VelocityBudgetActive,
            DoubleKey.ApsBoostV5PrimerCapU,
            BooleanKey.ApsBoostV5PrimerBolusMode,
            BooleanKey.ApsBoostV5ConfirmTranche,
            DoubleKey.ApsBoostV5TrancheFraction,
            DoubleKey.ApsBoostV5TrancheThreshold,
            BooleanKey.ApsBoostV6PreMealTarget,
            DoubleKey.ApsBoostV6PreMealTargetMgdl,
            DoubleKey.ApsBoostV6PreMealLeadMin,
            // V1 SMB sizing
            DoubleKey.ApsBoostInsulinReqPct,
            DoubleKey.ApsBoostScale,
            // Shared Boost engine controls
            BooleanKey.ApsBoostEnableCircadianIsf,
            BooleanKey.ApsBoostAllowWithHighTt,
            // Dynamic ISF
            BooleanKey.ApsBoostUseTdd,
            BooleanKey.ApsBoostAdjustSensitivity,
            BooleanKey.ApsBoostAutosensWhenNoTdd,
            UnitDoubleKey.ApsBoostDynIsfNormalTarget,
            DoubleKey.ApsBoostDynIsfVelocity,
            UnitDoubleKey.ApsBoostDynIsfBgCap,
            IntKey.ApsBoostDynIsfAdjustmentFactor,
            // Exercise: sensor advanced
            BooleanKey.ApsBoostPhoneStepsEnabled,
            BooleanKey.ApsBoostWatchHrEnabled,
            IntKey.ApsBoostWatchHrIntervalMin,
            // Exercise: step count
            IntKey.ApsBoostInactivitySteps,
            DoubleKey.ApsBoostInactivityPct,
            DoubleKey.ApsBoostSleepInHours,
            IntKey.ApsBoostSleepInSteps,
            IntKey.ApsBoostActivitySteps5,
            IntKey.ApsBoostActivitySteps15,
            IntKey.ApsBoostActivitySteps30,
            IntKey.ApsBoostActivitySteps60,
            DoubleKey.ApsBoostActivityPct,
            // Exercise: HR integration
            BooleanKey.ApsBoostHrIntegrationEnabled,
            IntKey.ApsBoostHrMaxBpm,
            IntKey.ApsBoostHrRestingBpm,
            IntKey.ApsBoostHrWindowMinutes,
            BooleanKey.ApsBoostHrStressDetection,
            // Exercise: post-exercise recovery
            BooleanKey.ApsBoostPostExerciseRecoveryEnabled,
            DoubleKey.ApsBoostPostExerciseRecoveryHours,
            UnitDoubleKey.ApsBoostPostExerciseRecoveryTarget,
            DoubleKey.ApsBoostPostExerciseRecoveryScale,
            IntKey.ApsBoostPostExerciseMinDuration,
            // Night mode
            StringKey.ApsBoostNightModeStart,
            StringKey.ApsBoostNightModeEnd,
            BooleanKey.ApsBoostNightModeDisableWithCob,
            BooleanKey.ApsBoostNightModeDisableWithLowTt,
            BooleanKey.ApsBoostNightModeAutoBySleep,
            IntKey.ApsBoostPreSleepLeadMin,
            IntKey.ApsBoostSleepHysteresisMin,
            IntKey.ApsBoostWakeHrHysteresisMin,
            BooleanKey.ApsBoostHealthConnectHrEnabled,
            IntKey.ApsBoostHealthConnectPollMin,
            BooleanKey.ApsBoostActivityShadowEnabled,
            // Safety
            DoubleKey.ApsBoostCumulativeSmbCap60Min,
            // Misc
            BooleanKey.ApsBoostPostRescueTightRampTrial,
        )
    }
}
