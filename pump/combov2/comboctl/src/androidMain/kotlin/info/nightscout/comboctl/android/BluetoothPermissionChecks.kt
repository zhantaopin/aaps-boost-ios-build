package info.nightscout.comboctl.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import info.nightscout.comboctl.base.BluetoothPermissionException

private val bluetoothPermissionsForSdk: List<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN")
    else
        listOf("android.permission.BLUETOOTH", "android.permission.ACCESS_FINE_LOCATION")

private val scanPermissionForSdk: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        "android.permission.BLUETOOTH_SCAN"
    else
        "android.permission.BLUETOOTH"

class AndroidBluetoothPermissionException(val missingPermissions: List<String>) :
    BluetoothPermissionException("Missing Bluetooth permissions: ${missingPermissions.joinToString(", ")}")

internal fun <T> checkForConnectPermission(androidContext: Context, block: () -> T) =
    checkForPermissions(androidContext, bluetoothPermissionsForSdk, block)

internal fun <T> checkForPermissions(androidContext: Context, permissions: List<String>, block: () -> T): T {
    return run {
        val missingPermissions = permissions
            .filter {
                ContextCompat.checkSelfPermission(androidContext, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missingPermissions.isEmpty())
            block.invoke()
        else
            throw AndroidBluetoothPermissionException(missingPermissions)
    }
}

internal fun runIfScanPermissionGranted(androidContext: Context, block: () -> Unit): Boolean {
    return if (ContextCompat.checkSelfPermission(androidContext, scanPermissionForSdk) == PackageManager.PERMISSION_GRANTED) {
        block.invoke()
        true
    } else
        false
}
