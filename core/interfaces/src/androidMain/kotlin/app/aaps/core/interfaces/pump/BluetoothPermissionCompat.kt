package app.aaps.core.interfaces.pump

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bluetooth permission runtime query with API-level version mapping.
 *
 * API 31 (Android 12) below, [Manifest.permission.BLUETOOTH_CONNECT] /
 * [Manifest.permission.BLUETOOTH_SCAN] **exist as string constants but the runtime
 * permission manager always returns DENIED** for them because they were introduced
 * in API 31. Querying them before API 31 means every pump driver silently refuses
 * to work — a medical risk when the pump simply never connects.
 *
 * On pre-31 we therefore query the legacy runtime pair:
 * - CONNECT semantics  → [Manifest.permission.BLUETOOTH]
 * - SCAN   semantics  → [Manifest.permission.ACCESS_FINE_LOCATION]  (BLE on 6-11 needs it)
 */
@SuppressLint("InlinedApi")
object BluetoothPermissionCompat {

    fun connectGranted(ctx: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT
        else
            Manifest.permission.BLUETOOTH
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun scanGranted(ctx: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN
        else
            Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Permission strings for the runtime request dialog. Pump modules that collect
     * a list of permissions to request from the user should call this rather than
     * hardcoding BLUETOOTH_CONNECT/SCAN (which, again, would be auto-permanently-denied
     * on 8-11 without ever popping a dialog).
     */
    fun requestedPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
}
