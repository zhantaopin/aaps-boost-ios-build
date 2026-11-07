package app.aaps.core.ui.compose.pump

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Gate composable that ensures Bluetooth runtime permissions
 * are granted before rendering its [content].
 *
 * Android 12+ (API 31+) uses BLUETOOTH_SCAN / BLUETOOTH_CONNECT.
 * Below API 31 these constants do not exist at runtime — queries/requests
 * would always return DENIED. Older Android uses BLUETOOTH (runtime) + FINE_LOCATION
 * (BLE requires location on 6-11). BLUETOOTH_ADMIN is declared in Manifest only.
 */
@Composable
fun BluetoothPermissionsHost(
    deniedContent: @Composable (requestAgain: () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    fun allGranted(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var requested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        requested = true
    }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(permissions)
        }
    }

    if (granted) {
        content()
    } else if (requested) {
        deniedContent { launcher.launch(permissions) }
    }
    // else: request in flight, render nothing
}
