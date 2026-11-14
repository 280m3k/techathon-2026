package com.example.esp32test.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions needed for BLE scanning + GATT connections.
 *
 * Declaring them in the manifest is not enough: they are "dangerous" permissions and must be
 * granted by the user at runtime, otherwise startScan()/connectGatt() throw SecurityException
 * (Android 12+) or the scan silently returns nothing (Android 11 and below).
 */
object BlePermissions {

    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAll(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
