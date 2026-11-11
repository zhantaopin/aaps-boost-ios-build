package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.logging.LogLevel
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.dialogs.QueryAnyPasswordDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.maintenance.MaintenanceViewModel.ExportState

/**
 * Encapsulates all maintenance-related dialogs and sheets from MainScreen:
 * - Maintenance bottom sheet with export/import/cleanup actions
 * - Log settings bottom sheet
 * - Cloud directory sheet
 * - 5 confirmation dialogs (reset APS, reset DB, cleanup DB, send logs, export CSV)
 * - Export state machine dialogs (missing password, confirm, ask password)
 * - Cleanup result dialog
 *
 * Owns its own dialog state — MainScreen only needs to pass the ViewModel and visibility flag.
 */
@Composable
fun MaintenanceDialogs(
    maintenanceViewModel: MaintenanceViewModel,
    showMaintenanceSheet: Boolean,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onSnackbar: suspend (String) -> Unit,
) {
    // Confirmation dialog states
    var showLogSettings by remember { mutableStateOf(false) }
    var showLogLevel by remember { mutableStateOf(false) }
    var showConfirmResetAps by remember { mutableStateOf(false) }
    var showConfirmResetDb by remember { mutableStateOf(false) }
    var showConfirmCleanupDb by remember { mutableStateOf(false) }
    var showConfirmExportCsv by remember { mutableStateOf(false) }
    var showConfirmSendLogs by remember { mutableStateOf(false) }
    var cleanupResultText by remember { mutableStateOf<String?>(null) }

    // ViewModel state
    val exportState by maintenanceViewModel.exportState.collectAsStateWithLifecycle()
    val cloudDirectoryState by maintenanceViewModel.cloudDirectoryState.collectAsStateWithLifecycle()
    val exportConfig by maintenanceViewModel.exportConfig.collectAsStateWithLifecycle()
    val isDirectoryAccessGranted by maintenanceViewModel.isDirectoryAccessGranted.collectAsStateWithLifecycle()
    val directoryPath by maintenanceViewModel.directoryPath.collectAsStateWithLifecycle()
    val logLevel by maintenanceViewModel.logLevel.collectAsStateWithLifecycle()

    // Collect maintenance events
    LaunchedEffect(Unit) {
        maintenanceViewModel.events.collect { event ->
            when (event) {
                is MaintenanceEvent.RecreateActivity -> onRecreateActivity()
                is MaintenanceEvent.CleanupResult -> cleanupResultText = event.result
                is MaintenanceEvent.Snackbar -> onSnackbar(event.message)
                is MaintenanceEvent.Error -> onSnackbar(event.message)
                is MaintenanceEvent.LaunchBrowser -> onLaunchBrowser(event.url)
                is MaintenanceEvent.BringToForeground -> onBringToForeground()
                is MaintenanceEvent.CloseSheet -> onMaintenanceSheetDismiss()
            }
        }
    }

    // Maintenance bottom sheet
    if (showMaintenanceSheet) {
        LaunchedEffect(Unit) {
            maintenanceViewModel.refreshExportConfig()
        }

        MaintenanceBottomSheet(
            onDismiss = onMaintenanceSheetDismiss,
            onLogSettingsClick = { showLogSettings = true },
            logLevel = logLevel,
            onLogLevelClick = { showLogLevel = true },
            onSendLogsClick = { showConfirmSendLogs = true },
            onDeleteLogsClick = { maintenanceViewModel.deleteLogs() },
            onDirectoryClick = {
                maintenanceViewModel.logSelectDirectory()
                onDirectoryClick()
            },
            onCloudDirectoryClick = { maintenanceViewModel.showCloudDirectory() },
            onClearCloudClick = { maintenanceViewModel.requestClearCloud() },
            onExportSettingsClick = { maintenanceViewModel.startExport() },
            // No audit entry here: opening the picker is not an import. ImportViewModel logs
            // IMPORT_SETTINGS when an import is actually applied, and logging it here as well wrote
            // the entry twice for a real import and once for one the user walked away from.
            onImportSettingsClick = { source -> onImportSettingsNavigate(source) },
            onExportCsvClick = { showConfirmExportCsv = true },
            onResetApsResultsClick = { showConfirmResetAps = true },
            onCleanupDbClick = { showConfirmCleanupDb = true },
            onResetDbClick = { showConfirmResetDb = true },
            exportConfig = exportConfig,
            isDirectoryAccessGranted = isDirectoryAccessGranted,
            directoryPath = directoryPath,
            onToggleSettingsLocal = { maintenanceViewModel.toggleSettingsLocal(it) },
            onToggleSettingsCloud = { maintenanceViewModel.toggleSettingsCloud(it) },
            onToggleLogEmail = { maintenanceViewModel.toggleLogEmail(it) },
            onToggleLogCloud = { maintenanceViewModel.toggleLogCloud(it) },
            onToggleCsvLocal = { maintenanceViewModel.toggleCsvLocal(it) },
            onToggleCsvCloud = { maintenanceViewModel.toggleCsvCloud(it) }
        )
    }

    // Log level dialog
    if (showLogLevel) {
        LogLevelDialog(
            current = logLevel,
            onSelect = { level ->
                showLogLevel = false
                maintenanceViewModel.setLogLevel(level)
                // The sheet stayed open behind this dialog; choosing a level is the end of the trip.
                onMaintenanceSheetDismiss()
            },
            onDismiss = { showLogLevel = false }
        )
    }

    // Log settings bottom sheet
    if (showLogSettings) {
        LogSettingBottomSheet(
            logElements = maintenanceViewModel.logElements,
            onDismiss = { showLogSettings = false },
            onToggle = { element, enabled -> maintenanceViewModel.toggleLogElement(element, enabled) },
            onResetToDefaults = { maintenanceViewModel.resetLogDefaults() }
        )
    }

    // Cloud directory dialog
    CloudDirectorySheet(
        state = cloudDirectoryState,
        onProviderClick = { maintenanceViewModel.onProviderClick(it) },
        onEditWebDav = { maintenanceViewModel.showWebDavSetup() },
        onWebDavConnect = { url, user, password ->
            maintenanceViewModel.connectWebDav(url, user, password)
        },
        onConfirmClear = { maintenanceViewModel.confirmClearCloud() },
        onCancelClear = { maintenanceViewModel.cancelClearCloud() },
        onReauthorize = { maintenanceViewModel.reauthorize() },
        onDismiss = { maintenanceViewModel.dismissCloudDirectory() },
        onDismissWebDav = { maintenanceViewModel.dismissWebDavSetup() }
    )

    // Confirmation dialogs
    if (showConfirmResetAps) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.reset_aps_results_confirm),
            onConfirm = {
                showConfirmResetAps = false
                maintenanceViewModel.resetApsResults()
            },
            onDismiss = { showConfirmResetAps = false }
        )
    }

    if (showConfirmResetDb) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.reset_db_confirm),
            onConfirm = {
                showConfirmResetDb = false
                maintenanceViewModel.resetDatabases()
            },
            onDismiss = { showConfirmResetDb = false }
        )
    }

    if (showConfirmCleanupDb) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.cleanup_db_confirm),
            onConfirm = {
                showConfirmCleanupDb = false
                maintenanceViewModel.cleanupDatabases()
            },
            onDismiss = { showConfirmCleanupDb = false }
        )
    }

    if (showConfirmSendLogs) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.value_question, stringResource(CoreUiStrings.send_logs)),
            onConfirm = {
                showConfirmSendLogs = false
                maintenanceViewModel.sendLogs()
            },
            onDismiss = { showConfirmSendLogs = false }
        )
    }

    if (showConfirmExportCsv) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.value_question, stringResource(CoreUiStrings.ue_export_to_csv)),
            onConfirm = {
                showConfirmExportCsv = false
                maintenanceViewModel.exportCsv()
            },
            onDismiss = { showConfirmExportCsv = false }
        )
    }

    // Export settings dialog chain
    when (exportState) {
        is ExportState.MasterPasswordMissing -> {
            OkDialog(
                title = stringResource(CoreUiStrings.nav_export),
                message = stringResource(CoreUiStrings.master_password_missing),
                onDismiss = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.ConfirmExport         -> {
            val confirmState = exportState as ExportState.ConfirmExport
            OkCancelDialog(
                title = stringResource(CoreUiStrings.export_to),
                message = stringResource(CoreUiStrings.value_question, confirmState.fileName) + "\n\n" +
                    stringResource(CoreUiStrings.password_preferences_encrypt_prompt),
                onConfirm = { maintenanceViewModel.onExportConfirmed() },
                onDismiss = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.AskPassword           -> {
            val askState = exportState as ExportState.AskPassword
            QueryAnyPasswordDialog(
                title = stringResource(StringKey.ProtectionMasterPassword.title),
                passwordExplanation = stringResource(CoreUiStrings.password_preferences_encrypt_prompt),
                errorMessage = if (askState.wrongPassword) stringResource(CoreUiStrings.wrongpassword) else null,
                onConfirm = { password -> maintenanceViewModel.onExportPasswordEntered(password) },
                onCancel = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.Idle                  -> { /* no dialog */
        }
    }

    // Cleanup result dialog
    cleanupResultText?.let { result ->
        OkDialog(
            title = stringResource(CoreUiStrings.result),
            message = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(CoreUiStrings.cleared_entries)) }
                appendLine()
                append(result)
            },
            onDismiss = { cleanupResultText = null }
        )
    }
}

/**
 * The four levels are a straight choice, so tapping one both selects and closes - no OK button to
 * forget. OFF carries an explicit note about what it means, because "off" is exactly the setting
 * people expect to be able to reverse and be surprised when a log they needed turns out empty.
 */
@Composable
private fun LogLevelDialog(
    current: LogLevel,
    onSelect: (LogLevel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(CoreUiStrings.log_level),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            // Scrollable for the 454 px watches: the OFF note below the four rows does not fit next
            // to them in the dialog's text slot there, and it is the part worth reading twice.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LogLevel.entries.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(level) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == level,
                            onClick = { onSelect(level) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = logLevelLabel(level),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(CoreUiStrings.log_level_off_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiStrings.cancel))
            }
        }
    )
}

@Composable
internal fun logLevelLabel(level: LogLevel): String = when (level) {
    LogLevel.OFF   -> stringResource(CoreUiStrings.log_level_off)
    LogLevel.WARN  -> stringResource(CoreUiStrings.log_level_warn)
    LogLevel.INFO  -> stringResource(CoreUiStrings.log_level_info)
    LogLevel.DEBUG -> stringResource(CoreUiStrings.log_level_debug)
}
