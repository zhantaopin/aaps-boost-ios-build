package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.maintenance.CloudAuthKind
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudProviderInfo
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.ErrorDialog
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.maintenance.MaintenanceViewModel.CloudDirectoryState

@Composable
fun CloudDirectorySheet(
    state: CloudDirectoryState,
    onProviderClick: (CloudProviderInfo) -> Unit,
    onEditWebDav: () -> Unit,
    onWebDavConnect: (String, String, String) -> Unit,
    onConfirmClear: () -> Unit,
    onCancelClear: () -> Unit,
    onReauthorize: () -> Unit,
    onDismiss: () -> Unit,
    onDismissWebDav: () -> Unit
) {
    when (state) {
        is CloudDirectoryState.Hidden -> { /* nothing */
        }

        is CloudDirectoryState.Shown -> {
            CloudDirectoryDialog(
                info = state.info,
                providers = state.providers,
                onProviderClick = onProviderClick,
                onEditWebDav = onEditWebDav,
                onDismiss = onDismiss
            )
        }

        is CloudDirectoryState.WebDavSetup -> {
            WebDavSetupDialog(
                state = state,
                onConnect = onWebDavConnect,
                onDismiss = onDismissWebDav
            )
        }

        is CloudDirectoryState.ConfirmClear -> {
            OkCancelDialog(
                title = stringResource(CoreUiStrings.clear_cloud_settings),
                message = stringResource(CoreUiStrings.clear_cloud_settings_message),
                onConfirm = onConfirmClear,
                onDismiss = onCancelClear
            )
        }

        is CloudDirectoryState.Reauthorize -> {
            ErrorDialog(
                title = stringResource(CoreUiStrings.cloud_connection_failed),
                message = stringResource(CoreUiStrings.cloud_reauthorize_message),
                positiveButton = stringResource(CoreUiStrings.reauthorize),
                onPositive = onReauthorize,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun CloudDirectoryDialog(
    info: CloudDirectoryInfo,
    providers: List<CloudProviderInfo>,
    onProviderClick: (CloudProviderInfo) -> Unit,
    onEditWebDav: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(CoreUiStrings.select_storage_type),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                providers.forEach { provider ->
                    ProviderRow(
                        provider = provider,
                        onClick = { onProviderClick(provider) },
                        onEdit = onEditWebDav
                    )
                }
                if (info.isCloudActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = info.cloudPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
private fun ProviderRow(
    provider: CloudProviderInfo,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = provider.icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = provider.isActive,
                onClick = onClick
            )
        }

        if (provider.hasCredentials) {
            val statusColor = if (provider.hasConnectionError)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
            val statusIcon = if (provider.hasConnectionError)
                Icons.Default.Warning
            else
                Icons.Default.CheckCircle
            Row(
                modifier = Modifier.padding(start = 44.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = statusColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = provider.authorizedStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
        }

        if (provider.authKind == CloudAuthKind.USERNAME_PASSWORD && provider.hasCredentials) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.padding(start = 34.dp)
            ) {
                Text(stringResource(CoreUiStrings.webdav_edit))
            }
        }
    }
}
