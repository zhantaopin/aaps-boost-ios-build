package app.aaps.ui.compose.overview.boost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.objects.extensions.tickerFlow
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.ThreeButtonDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.loopSheet.LoopActionBottomSheet
import app.aaps.ui.compose.loopSheet.LoopActionViewModel
import app.aaps.ui.compose.main.MainDrawer
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetHost
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.notificationsSheet.NotificationFab
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.scenes.SceneIcons
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import kotlinx.coroutines.launch

/**
 * The Boost V2 overview as a navigation destination: the screen, the main overview's drawer, its
 * notification button and sheet, and the dialogs its action buttons open.
 *
 * Every action reuses what the main overview already has. Navigation goes through [onNavigate], so
 * each treatment dialog keeps its own protection level. The button visibility comes from
 * [TreatmentViewModel], which reads the same `OverviewShow*Button` preferences as the main overview's
 * treatment sheet. Accept-temp opens the main overview's loop sheet and enacts through
 * [MainViewModel.performLoopAccept]. Automation user actions and QuickWizard entries go through
 * the same confirmation and execution path as the main overview's scenes and treatment sheets.
 */
@Composable
fun BoostOverviewHost(
    boostViewModel: BoostV2ViewModel,
    graphViewModel: GraphViewModel,
    mainViewModel: MainViewModel,
    treatmentViewModel: TreatmentViewModel,
    loopActionViewModel: LoopActionViewModel,
    scenesViewModel: ScenesViewModel,
    manageViewModel: ManageViewModel,
    panelPlugins: List<PluginBase>,
    notificationManager: NotificationManager,
    pumpCommunicationStatus: PumpCommunicationStatus,
    onNavigate: (NavigationRequest) -> Unit,
    onBoostDetails: () -> Unit,
    onActionsError: (String, String) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit
) {
    val treatmentState by treatmentViewModel.uiState.collectAsStateWithLifecycle()
    val loopState by loopActionViewModel.uiState.collectAsStateWithLifecycle()
    val scenesState by scenesViewModel.uiState.collectAsStateWithLifecycle()
    val pumpStatusBanner by pumpCommunicationStatus.statusBannerFlow.collectAsStateWithLifecycle()
    val notifications by notificationManager.notifications.collectAsStateWithLifecycle()
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showNotificationSheet by rememberSaveable { mutableStateOf(false) }
    var showLoopActionSheet by rememberSaveable { mutableStateOf(false) }
    val manageSheetState = ManageSheetHost(
        manageViewModel = manageViewModel,
        isSimpleMode = mainState.isSimpleMode,
        onNavigate = onNavigate,
        onActionsError = onActionsError
    )

    // 3.4 listed a user action only while the loop was not suspended and the pump and profile were
    // ready; the scenes view model expresses the same conditions as a non-null activation reason.
    val actions = remember(treatmentState, loopState, scenesState) {
        BoostActionsState(
            showInsulin = treatmentState.showInsulin,
            showCalculator = treatmentState.showCalculator,
            showCarbs = treatmentState.showCarbs,
            showTreatment = treatmentState.showTreatment,
            showCalibration = treatmentState.showCalibration,
            showCgm = treatmentState.showCgm,
            isDexcomSource = treatmentState.isDexcomSource,
            acceptTempAvailable = loopState.actionAvailable,
            quickWizardItems = treatmentState.quickWizardItems
                .filter { it.isEnabled }
                .map { BoostQuickWizardButton(guid = it.guid, text = it.buttonText) },
            userActions = scenesState.items
                .filter { it.activationReason == null }
                .map { BoostUserActionButton(eventId = it.eventId, title = it.title, icon = it.firstActionIcon?.icon) }
        )
    }
    // The panel's scene tab doubles as the "a scene is running" light.
    val activeScene by mainViewModel.activeSceneState.collectAsStateWithLifecycle()
    val sceneExpired by mainViewModel.sceneExpired.collectAsStateWithLifecycle()
    val sceneChainTargetName by mainViewModel.activeSceneChainTargetName.collectAsStateWithLifecycle()
    val masterReachable by mainViewModel.masterReachable.collectAsStateWithLifecycle()
    var showSceneSheet by rememberSaveable { mutableStateOf(false) }
    // A scene that ends while its sheet is open leaves no state behind: close the sheet with it.
    LaunchedEffect(activeScene) { if (activeScene == null) showSceneSheet = false }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawer(
                drawerState = drawerState,
                versionName = mainViewModel.versionName,
                // Closed before navigating, as in MainScreen: opened mid-slide, Back from that screen left the app.
                onNavigate = { request ->
                    scope.launch {
                        drawerState.close()
                        onNavigate(request)
                    }
                },
                isTreatmentsEnabled = mainState.isProfileLoaded,
                showExit = mainViewModel.showExit
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BoostV2Route(
                viewModel = boostViewModel,
                graphViewModel = graphViewModel,
                pumpStatus = pumpStatusBanner?.text ?: "",
                actions = actions,
                activeScene = activeScene != null,
                onSceneClick = { showSceneSheet = true },
                modifier = Modifier.systemBarsPadding(),
                panelTabs = {
                    BoostPanelTabs(
                        plugins = panelPlugins,
                        isTreatmentsEnabled = mainState.isProfileLoaded,
                        activeScene = activeScene != null,
                        actionsLabel = stringResource(CoreUiStrings.manage),
                        onActions = { manageSheetState.show() },
                        onElement = { element -> onNavigate(NavigationRequest.Element(element)) },
                        onPlugin = { plugin -> onNavigate(NavigationRequest.Plugin(plugin::class.simpleName.orEmpty())) }
                    )
                },
                onMenu = { scope.launch { drawerState.open() } },
                onBoostDetails = onBoostDetails,
                onElement = { element -> onNavigate(NavigationRequest.Element(element)) },
                onAcceptTemp = { showLoopActionSheet = true },
                onQuickWizard = { guid -> onNavigate(NavigationRequest.QuickWizard(guid)) },
                onUserAction = { eventId -> mainViewModel.requestAutomationConfirmation(eventId) }
            )

            NotificationFab(
                notificationCount = notifications.size,
                highestLevel = notifications.minByOrNull { it.level.ordinal }?.level,
                onClick = { showNotificationSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }

    if (showNotificationSheet && notifications.isNotEmpty()) {
        NotificationBottomSheet(
            notifications = notifications,
            onDismissSheet = { showNotificationSheet = false },
            onDismissNotification = { notification -> notificationManager.dismiss(NotificationHandle(notification.instanceKey)) },
            onNotificationActionClick = onNotificationActionClick
        )
    }

    if (showLoopActionSheet) {
        LoopActionBottomSheet(
            state = loopState,
            onPerform = { mainViewModel.performLoopAccept() },
            onDismiss = { showLoopActionSheet = false }
        )
    }

    // Tapping the scene light in the top bar opens the running scene: what it is, how long is left, how to stop it.
    val runningScene = activeScene
    if (showSceneSheet && runningScene != null) {
        BoostSceneSheet(
            state = runningScene,
            expired = sceneExpired,
            chainTargetName = sceneChainTargetName,
            endEnabled = masterReachable,
            formatDuration = mainViewModel::formatDuration,
            onEnd = {
                showSceneSheet = false
                mainViewModel.requestSceneDeactivation()
            },
            onDismissExpired = {
                showSceneSheet = false
                mainViewModel.dismissExpiredScene()
            },
            onClose = { showSceneSheet = false }
        )
    }

    // The confirmation the main overview shows for automation actions, rendered here because the
    // main overview is not composed while this screen is open.
    val actionConfirmation by mainViewModel.actionConfirmation.collectAsStateWithLifecycle()
    actionConfirmation?.let { confirmation ->
        val secondaryAction = confirmation.secondaryAction
        val secondaryLabel = confirmation.secondaryLabel
        if (secondaryAction != null && secondaryLabel != null) {
            ThreeButtonDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                primaryLabel = confirmation.confirmLabel ?: stringResource(CoreUiStrings.ok),
                onPrimary = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                secondaryLabel = secondaryLabel,
                onSecondary = { mainViewModel.executeConfirmableAction(secondaryAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        } else {
            OkCancelDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                onConfirm = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        }
    }
}

/**
 * What the top bar's scene light opens: the running scene's name, time left, progress and End.
 *
 * Boost keeps its own dark palette here — the default overview's banner is a Material card and would
 * read as a light box on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoostSceneSheet(
    state: ActiveSceneState,
    expired: Boolean,
    chainTargetName: String?,
    endEnabled: Boolean,
    formatDuration: (Long) -> String,
    onEnd: () -> Unit,
    onDismissExpired: () -> Unit,
    onClose: () -> Unit
) {
    val dateUtil = LocalDateUtil.current
    // Ticker for the countdown — 30s matches the default overview's banner; the text is minute-grain.
    var now by remember { mutableLongStateOf(dateUtil.now()) }
    LaunchedEffect(Unit) { tickerFlow(30_000L).collect { now = dateUtil.now() } }

    val remainingMs = state.remainingMs(now)
    val totalMs = state.durationMs
    val status = when {
        expired                                              -> stringResource(CoreUiStrings.scene_ended)
        remainingMs != null && remainingMs > 0 && totalMs > 0 -> formatDuration(remainingMs)
        else                                                 -> stringResource(CoreUiStrings.scene_active)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BoostV2Colors.cardBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = SceneIcons.fromKey(state.scene.icon).icon,
                    contentDescription = null,
                    tint = BoostV2Colors.accent
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = state.scene.name,
                        color = BoostV2Colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = status,
                        color = if (expired) BoostV2Colors.textSecondary else BoostV2Colors.accent,
                        fontSize = 13.sp
                    )
                }
            }
            if (!expired && remainingMs != null && totalMs > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 1f - remainingMs.toFloat() / totalMs.toFloat() },
                    color = BoostV2Colors.accent,
                    trackColor = BoostV2Colors.stroke,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
            if (!expired && chainTargetName != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(CoreUiStrings.scene_chain_indicator, chainTargetName),
                    color = BoostV2Colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            state.chainSkipReason?.takeIf { expired }?.let { reason ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(CoreUiStrings.scene_chain_skipped_line, reason),
                    color = BoostV2Colors.amber,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (expired) {
                    FilledTonalButton(onClick = onDismissExpired) {
                        Text(stringResource(CoreUiStrings.close))
                    }
                } else if (LocalClientRole.current.allows(ElementType.SCENE.role)) {
                    FilledTonalButton(onClick = onEnd, enabled = endEnabled) {
                        Text(stringResource(CoreUiStrings.scene_deactivate))
                    }
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(CoreUiStrings.close))
                }
            }
        }
    }
}
