package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcSettingsOff
import app.aaps.core.ui.compose.stringResource
import kotlinx.coroutines.delay

/**
 * Main top bar: menu on the left, the current time in the middle, settings on the right.
 *
 * The search field the bar used to carry is gone on every device; the search pipeline is still
 * wired, it simply has no entry point. On a 320dp watch the freed width goes to the clock.
 *
 * @param onMenuClick Called when menu button is clicked
 * @param onPreferencesClick Called when preferences button is clicked
 * @param isSimpleMode When true, shows a non-interactive simple-mode indicator next to Settings
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    onMenuClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    isSimpleMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    var now by remember { mutableLongStateOf(dateUtil.now()) }
    LaunchedEffect(Unit) {
        // Wake exactly on the minute, then every minute: no 1s polling on a watch.
        while (true) {
            delay(60_000L - (dateUtil.now() % 60_000L))
            now = dateUtil.now()
        }
    }

    TopAppBar(
        title = {
            Text(
                text = dateUtil.timeString(now),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(CoreUiStrings.open_navigation)
                )
            }
        },
        actions = {
            IconButton(onClick = onPreferencesClick) {
                // In simple mode the gear shows "crossed" (IcSettingsOff) to signal the mode;
                // the button action (open settings) is unchanged.
                Icon(
                    imageVector = if (isSimpleMode) IcSettingsOff else Icons.Default.Settings,
                    contentDescription = stringResource(CoreUiStrings.settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        windowInsets = WindowInsets(0),
        modifier = modifier
    )
}
