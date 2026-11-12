package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.maintenance.MaintenanceViewModel.CloudDirectoryState.WebDavSetup

@Composable
fun WebDavSetupDialog(
    state: WebDavSetup,
    onConnect: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }
    val focusManager: FocusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = { if (!state.isConnecting) onDismiss() },
        title = {
            Text(
                text = stringResource(CoreUiStrings.webdav),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .clearFocusOnTap(focusManager)
            ) {
                // The dialog is taller than a 300-320 dp watch screen, so an error printed after
                // the fields sits below the fold and looks like "connect did nothing". Keep it at
                // the top, where it is on screen as soon as the dialog opens.
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CoreUiStrings.webdav_server_url)) },
                    placeholder = {
                        Text(
                            text = stringResource(CoreUiStrings.webdav_server_url_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    enabled = !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CoreUiStrings.webdav_username)) },
                    singleLine = true,
                    enabled = !state.isConnecting
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CoreUiStrings.webdav_password)) },
                    singleLine = true,
                    enabled = !state.isConnecting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = if (state.hasStoredPassword) {
                        { Text(stringResource(CoreUiStrings.webdav_keep_password_hint)) }
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(baseUrl, username, password) },
                enabled = !state.isConnecting
            ) {
                Text(
                    stringResource(
                        if (state.isConnecting) CoreUiStrings.webdav_connecting else CoreUiStrings.webdav_connect
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isConnecting) {
                Text(stringResource(CoreUiStrings.cancel))
            }
        }
    )
}
