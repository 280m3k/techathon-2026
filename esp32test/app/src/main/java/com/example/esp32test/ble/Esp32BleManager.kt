package com.example.esp32test.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.network.Esp32ApiServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Result of a BLE transmission attempt.
 */
data class Esp32BleResult(
    val isSuccessful: Boolean,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Manager interface for BLE scanning, connecting, service discovery, and writing NFC data to ESP32.
 */
interface Esp32BleManager {
    suspend fun writeNfcData(
        context: Context,
        nfcTagData: NfcTagData,
        deviceFilter: String,
        serviceUuid: String,
        characteristicUuid: String,
        timeoutMillis: Long = 15000L
    ): Esp32BleResult
}

/**
 * Implementation of [Esp32BleManager] using Android BLE APIs.
 */
class Esp32BleManagerImpl : Esp32BleManager {

    companion object {
        private const val TAG = "Esp32Ble"
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeNfcData(
        context: Context,
        nfcTagData: NfcTagData,
        deviceFilter: String,
        serviceUuid: String,
        characteristicUuid: String,
        timeoutMillis: Long
    ): Esp32BleResult {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
            ?: run {
                Log.e(TAG, "Bluetooth not supported on this device")
                return Esp32BleResult(isSuccessful = false, errorMessage = "Bluetooth not supported on this device")
            }

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            return Esp32BleResult(isSuccessful = false, errorMessage = "Bluetooth is disabled")
        }

        if (!BlePermissions.hasAll(context)) {
            Log.e(TAG, "Missing Bluetooth runtime permissions")
            return Esp32BleResult(
                isSuccessful = false,
                errorMessage = "Bluetooth permission not granted. Allow \"Nearby devices\" (or Location on Android 11 and below) for this app, then retry."
            )
        }

        val jsonPayload = Esp32ApiServiceImpl.createJsonPayload(nfcTagData)
        val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE Scanner unavailable")
            return Esp32BleResult(isSuccessful = false, errorMessage = "BLE Scanner unavailable")
        }

        val targetServiceUuid = try {
            UUID.fromString(serviceUuid.trim())
        } catch (e: Exception) {
            Log.w(TAG, "Invalid serviceUuid '$serviceUuid', falling back to default UART service UUID", e)
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        }

        val targetCharUuid = try {
            UUID.fromString(characteristicUuid.trim())
        } catch (e: Exception) {
            Log.w(TAG, "Invalid characteristicUuid '$characteristicUuid', falling back to default UART characteristic UUID", e)
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        }

        // Clean and parse deviceFilter (e.g. handle ble://ESP32/api/nfc, ble://ESP32, ESP32, ESP32-1, MAC address, etc.)
        var cleanedFilter = deviceFilter.trim()
        if (cleanedFilter.startsWith("ble://", ignoreCase = true) || cleanedFilter.startsWith("ble:/", ignoreCase = true)) {
            cleanedFilter = cleanedFilter.replace(Regex("^ble://+", RegexOption.IGNORE_CASE), "")
            val slashIndex = cleanedFilter.indexOf('/')
            if (slashIndex != -1) {
                cleanedFilter = cleanedFilter.substring(0, slashIndex)
            }
            cleanedFilter = cleanedFilter.trim()
        }
        // The same settings field holds the Wi-Fi IP; an IPv4 address is never a BLE device name.
        if (cleanedFilter.isEmpty() || Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$").matches(cleanedFilter)) {
            cleanedFilter = "ESP32"
        }

        Log.d(TAG, "1. BLE transmission starting: targetServiceUuid=$targetServiceUuid, targetCharUuid=$targetCharUuid, deviceFilter=$cleanedFilter, payloadSize=${payloadBytes.size} bytes, timeoutMillis=$timeoutMillis")

        // Progress tracking so a timeout can say WHICH step stalled.
        val stage = java.util.concurrent.atomic.AtomicReference("scanning")
        val seenDevices = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                var gatt: BluetoothGatt? = null
                var scanCallback: ScanCallback? = null
                val isCompleted = AtomicBoolean(false)
                val serviceDiscoveryInitiated = AtomicBoolean(false)

                fun finish(result: Esp32BleResult) {
                    if (!isCompleted.compareAndSet(false, true)) {
                        Log.d(TAG, "finish() already called, ignoring duplicate completion: $result")
                        return
                    }
                    Log.d(TAG, "Finishing BLE transmission with result: success=${result.isSuccessful}, message=${result.errorMessage ?: result.responseMessage}")
                    try {
                        scanCallback?.let {
                            scanner.stopScan(it)
                            Log.d(TAG, "2. BLE scan stopped.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping scan", e)
                    }
                    try {
                        gatt?.disconnect()
                        gatt?.close()
                        Log.d(TAG, "Gatt connection closed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing gatt", e)
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        Log.d(TAG, "4. onConnectionStateChange: status=$status, newState=$newState, gatt=$g, profileState=${if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else if (newState == BluetoothProfile.STATE_DISCONNECTED) "DISCONNECTED" else "OTHER($newState)"}")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                stage.set("negotiating MTU / discovering services")
                                Log.d(TAG, "Connected to ESP32 GATT server. Requesting MTU 512...")
                                val mtuRequested = g.requestMtu(512)
                                Log.d(TAG, "requestMtu(512) returned: $mtuRequested")

                                // Fallback timer in case onMtuChanged is never called by system stack
                                CoroutineScope(Dispatchers.IO).launch {
                                    delay(2000L)
                                    if (!serviceDiscoveryInitiated.get() && gatt != null && !isCompleted.get()) {
                                        if (serviceDiscoveryInitiated.compareAndSet(false, true)) {
                                            Log.d(TAG, "MTU callback timeout fallback (2s). Discovering services...")
                                            try {
                                                val discSuccess = g.discoverServices()
                                                Log.d(TAG, "discoverServices() (fallback) returned: $discSuccess")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error discovering services in fallback", e)
                                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "Service discovery exception: ${e.message}"))
                                            }
                                        }
                                    }
                                }
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                Log.d(TAG, "Disconnected from ESP32 GATT server.")
                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "BLE device disconnected"))
                            }
                        } else {
                            Log.e(TAG, "GATT connection state change error status: $status")
                            finish(Esp32BleResult(isSuccessful = false, errorMessage = "BLE connection error (status $status)"))
                        }
                    }

                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                        Log.d(TAG, "4. onMtuChanged: status=$status, mtu=$mtu, gatt=$g")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "MTU successfully negotiated: $mtu.")
                        } else {
                            Log.w(TAG, "MTU negotiation failed (status $status), proceeding with default MTU.")
                        }
                        if (serviceDiscoveryInitiated.compareAndSet(false, true)) {
                            Log.d(TAG, "Discovering services after MTU change...")
                            try {
                                val discSuccess = g.discoverServices()
                                Log.d(TAG, "discoverServices() returned: $discSuccess")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error discovering services on MTU changed", e)
                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "Service discovery exception: ${e.message}"))
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        Log.d(TAG, "4. onServicesDiscovered: status=$status, gatt=$g")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val services = g.services
                            val servicesDescription = services?.joinToString(prefix = "[", postfix = "]") { service ->
                                val chars = service.characteristics?.joinToString(prefix = "{", postfix = "}") { char ->
                                    "uuid=${char.uuid}, properties=${char.properties}, writeType=${char.writeType}"
                                } ?: "no characteristics"
                                "service=${service.uuid}, chars=$chars"
                            } ?: "none"
                            Log.d(TAG, "Discovered services and characteristics: $servicesDescription")

                            var service = g.getService(targetServiceUuid)
                            if (service == null) {
                                service = g.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
                                if (service != null) {
                                    Log.d(TAG, "Using fallback UART service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
                                }
                            }
                            if (service == null) {
                                val discoveredUuids = services?.joinToString { it.uuid.toString() } ?: "none"
                                Log.e(TAG, "Target BLE Service not found: $targetServiceUuid (Discovered: $discoveredUuids)")
                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "Target BLE Service not found: $targetServiceUuid (Discovered: $discoveredUuids)"))
                                return
                            }

                            var characteristic = service.getCharacteristic(targetCharUuid)
                            if (characteristic == null) {
                                characteristic = service.getCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
                                if (characteristic != null) {
                                    Log.d(TAG, "Using fallback UART characteristic UUID: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
                                }
                            }
                            if (characteristic == null) {
                                val discoveredChars = service.characteristics?.joinToString { it.uuid.toString() } ?: "none"
                                Log.e(TAG, "Target BLE Characteristic not found: $targetCharUuid (Discovered: $discoveredChars)")
                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "Target BLE Characteristic not found: $targetCharUuid (Discovered: $discoveredChars)"))
                                return
                            }

                            stage.set("writing (no write response from ESP32)")
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            Log.d(TAG, "Writing payload (${payloadBytes.size} bytes) to characteristic: ${characteristic.uuid} with writeType=${characteristic.writeType}")

                            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                g.writeCharacteristic(characteristic, payloadBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
                            } else {
                                @Suppress("DEPRECATION")
                                characteristic.value = payloadBytes
                                @Suppress("DEPRECATION")
                                g.writeCharacteristic(characteristic)
                            }

                            Log.d(TAG, "writeCharacteristic initiation success: $success")
                            if (!success) {
                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "Failed to initiate characteristic write"))
                            }
                        } else {
                            Log.e(TAG, "Service discovery failed with status: $status")
                            finish(Esp32BleResult(isSuccessful = false, errorMessage = "Service discovery failed (status $status)"))
                        }
                    }

                    override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        Log.d(TAG, "4. onCharacteristicWrite: status=$status, characteristic=${characteristic.uuid}")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "NFC tag data successfully written to ESP32 BLE characteristic!")
                            finish(Esp32BleResult(isSuccessful = true, responseCode = 200, responseMessage = "BLE Write Success"))
                        } else {
                            Log.e(TAG, "Characteristic write failed with status: $status")
                            finish(Esp32BleResult(isSuccessful = false, errorMessage = "Characteristic write failed (status $status)"))
                        }
                    }
                }

                scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device
                        val deviceName = result.scanRecord?.deviceName
                            ?: try { device.name } catch (_: SecurityException) { null }
                            ?: ""
                        val deviceAddress = device.address
                        val rssi = result.rssi
                        val serviceUuids = result.scanRecord?.serviceUuids
                        val matchesUuid = serviceUuids?.any { it.uuid == targetServiceUuid } == true

                        // NOTE: unnamed devices must NOT match, otherwise we connect to any random
                        // nearby BLE gadget (TV, watch, tracker) and then stall.
                        val matchesName = deviceName.isNotEmpty() &&
                            deviceName.contains(cleanedFilter, ignoreCase = true)
                        seenDevices.add(if (deviceName.isEmpty()) "<unnamed>" else deviceName)

                        val matchesAddress = deviceAddress.equals(cleanedFilter, ignoreCase = true)

                        val matches = matchesAddress || matchesName || matchesUuid

                        Log.d(TAG, "2. Scanned device: name='$deviceName', address=$deviceAddress, rssi=$rssi, serviceUuids=$serviceUuids | matchesName=$matchesName, matchesAddress=$matchesAddress, matchesUuid=$matchesUuid, matches=$matches (filter=$cleanedFilter)")

                        if (matches) {
                            stage.set("connecting")
                            Log.d(TAG, "2. Matching BLE device found: name='$deviceName', address=$deviceAddress, rssi=$rssi")
                            try {
                                scanner.stopScan(this)
                                scanCallback = null
                                Log.d(TAG, "2. BLE scan stopped on matching device.")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error stopping scan on match", e)
                            }

                            Log.d(TAG, "3. Calling connectGatt on device: name='$deviceName', address=$deviceAddress")
                            try {
                                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error connecting to gatt", e)
                                finish(Esp32BleResult(isSuccessful = false, errorMessage = "Failed to connect to device: ${e.message}"))
                            }
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        Log.e(TAG, "2. BLE scan failed with error: $errorCode")
                        finish(Esp32BleResult(isSuccessful = false, errorMessage = "BLE scan failed (error $errorCode)"))
                    }
                }

                if (BluetoothAdapter.checkBluetoothAddress(cleanedFilter)) {
                    Log.d(TAG, "Device filter is a valid Bluetooth MAC address: $cleanedFilter")
                    try {
                        val device = bluetoothAdapter.getRemoteDevice(cleanedFilter)
                        Log.d(TAG, "3. Calling connectGatt for MAC address: $cleanedFilter")
                        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error connecting to MAC address", e)
                        finish(Esp32BleResult(isSuccessful = false, errorMessage = "Failed to connect to MAC address: ${e.message}"))
                    }
                } else {
                    Log.d(TAG, "2. Starting BLE scan with filter: '$cleanedFilter'...")
                    try {
                        scanner.startScan(
                            null,
                            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                            scanCallback
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start BLE scan", e)
                        finish(Esp32BleResult(isSuccessful = false, errorMessage = "Failed to start BLE scan: ${e.message}"))
                    }
                }

                continuation.invokeOnCancellation {
                    Log.w(TAG, "BLE operation coroutine cancelled/timed out.")
                    finish(Esp32BleResult(isSuccessful = false, errorMessage = "BLE operation cancelled or timed out"))
                }
            }
        } ?: run {
            Log.e(TAG, "BLE Transmission timed out after ${timeoutMillis}ms at stage '${stage.get()}'")
            val detail = when (stage.get()) {
                "scanning" -> "no device matching '$cleanedFilter' was advertising. Saw: " +
                    (synchronized(seenDevices) { seenDevices.take(8).joinToString().ifEmpty { "no BLE devices at all (is Location switched on? is the ESP32 advertising?)" } })
                else -> "stalled while ${stage.get()}"
            }
            Esp32BleResult(isSuccessful = false, errorMessage = "BLE timed out after ${timeoutMillis / 1000}s: $detail")
        }
    }
}
