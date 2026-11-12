package app.aaps.core.interfaces.pump

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * Bluetooth permission group with API-level version mapping.
 *
 * Android 12+ (API 31+) split Bluetooth into [Manifest.permission.BLUETOOTH_CONNECT]
 * and [Manifest.permission.BLUETOOTH_SCAN]. Below API 31 these constants do not exist
 * at runtime, so queries/requests would always return DENIED and pump drivers would
 * silently refuse to work. For older Android we ask for the legacy BLUETOOTH runtime
 * permission plus location (BLE requires location on 6-11).
 */
@SuppressLint("InlinedApi")
internal actual fun bluetoothPermissionGroup(): PermissionGroup? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PermissionGroup(
        permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
        rationaleTitle = InterfacesStrings.permission_bluetooth_title,
        rationaleDescription = InterfacesStrings.permission_bluetooth_description,
    )
} else {
    // Android 8-11: BLUETOOTH is a runtime permission that covers everything, and
    // BLUETOOTH_ADMIN + location are the legacy requirements.
    PermissionGroup(
        permissions = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
        rationaleTitle = InterfacesStrings.permission_bluetooth_title,
        rationaleDescription = InterfacesStrings.permission_bluetooth_description,
    )
}
