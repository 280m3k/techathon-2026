package com.example.esp32test.data.repository

import android.content.Context
import com.example.esp32test.ble.Esp32BleManager
import com.example.esp32test.ble.Esp32BleManagerImpl
import com.example.esp32test.data.model.Esp32Settings
import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransportMode
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionState
import com.example.esp32test.data.model.TransmissionStatus
import com.example.esp32test.network.Esp32ApiService
import com.example.esp32test.network.Esp32ApiServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementation of [Esp32Repository] supporting both Wi-Fi and Bluetooth Low Energy (BLE) transports.
 */
class Esp32RepositoryImpl(
    private val apiService: Esp32ApiService = Esp32ApiServiceImpl(),
    private val bleManager: Esp32BleManager = Esp32BleManagerImpl()
) : Esp32Repository {

    private val _transmissionState = MutableStateFlow<TransmissionState>(TransmissionState.Idle)
    override val transmissionState: StateFlow<TransmissionState> = _transmissionState.asStateFlow()

    private val _transmissionLogs = MutableStateFlow<List<TransmissionLog>>(emptyList())
    override val transmissionLogs: StateFlow<List<TransmissionLog>> = _transmissionLogs.asStateFlow()

    override fun formatTargetUrl(ipOrUrl: String, defaultPath: String): String {
        val trimmed = ipOrUrl.trim()
        if (trimmed.isEmpty()) {
            return "http://192.168.1.100$defaultPath"
        }

        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "http://$trimmed"
        } else {
            trimmed
        }

        val schemeEndIndex = withScheme.indexOf("://") + 3
        val pathStartIndex = withScheme.indexOf('/', startIndex = schemeEndIndex)

        return when {
            pathStartIndex == -1 -> "$withScheme$defaultPath"
            pathStartIndex == (withScheme.length - 1) -> "${withScheme.dropLast(1)}$defaultPath"
            else -> withScheme
        }
    }

    override suspend fun sendNfcData(
        nfcTagData: NfcTagData,
        targetIpOrUrl: String,
        method: HttpMethod,
        timeoutMillis: Long
    ): TransmissionLog {
        val settings = Esp32Settings(
            ipAddress = targetIpOrUrl,
            httpMethod = method,
            transportMode = TransportMode.WIFI
        )
        return sendNfcData(null, nfcTagData, settings, timeoutMillis)
    }

    override suspend fun sendNfcData(
        context: Context?,
        nfcTagData: NfcTagData,
        settings: Esp32Settings,
        timeoutMillis: Long
    ): TransmissionLog {
        val targetUrl = if (settings.transportMode == TransportMode.WIFI) {
            formatTargetUrl(settings.ipAddress, settings.endpointPath)
        } else {
            val deviceFilter = settings.ipAddress.ifEmpty { "ESP32" }
            "ble://$deviceFilter${settings.endpointPath}"
        }

        val pendingLog = TransmissionLog(
            nfcTagData = nfcTagData,
            targetUrl = targetUrl,
            status = TransmissionStatus.PENDING
        )

        _transmissionState.value = TransmissionState.Transmitting(pendingLog)

        val finalLog: TransmissionLog
        if (settings.transportMode == TransportMode.WIFI) {
            val response = apiService.sendNfcData(
                url = targetUrl,
                tagData = nfcTagData,
                method = settings.httpMethod,
                timeoutMillis = timeoutMillis
            )
            if (response.isSuccessful) {
                val responseCode = response.responseCode ?: 200
                val responseMessage = response.responseMessage ?: "OK"

                finalLog = pendingLog.copy(
                    status = TransmissionStatus.SUCCESS,
                    responseCode = responseCode,
                    responseMessage = responseMessage
                )

                _transmissionState.value = TransmissionState.Success(
                    responseCode = responseCode,
                    responseMessage = responseMessage,
                    log = finalLog
                )
            } else {
                val errorMessage = response.errorMessage ?: "Wi-Fi transmission failed"
                val responseCode = response.responseCode

                finalLog = pendingLog.copy(
                    status = TransmissionStatus.FAILED,
                    responseCode = responseCode,
                    responseMessage = errorMessage
                )

                _transmissionState.value = TransmissionState.Failed(
                    errorMessage = errorMessage,
                    responseCode = responseCode,
                    log = finalLog
                )
            }
        } else {
            // BLE mode
            if (context == null) {
                val errorMessage = "Context required for BLE transmission"
                finalLog = pendingLog.copy(
                    status = TransmissionStatus.FAILED,
                    responseMessage = errorMessage
                )
                _transmissionState.value = TransmissionState.Failed(errorMessage, null, finalLog)
            } else {
                val bleResult = bleManager.writeNfcData(
                    context = context,
                    nfcTagData = nfcTagData,
                    deviceFilter = settings.ipAddress.ifEmpty { "ESP32" },
                    serviceUuid = settings.bleServiceUuid,
                    characteristicUuid = settings.bleCharacteristicUuid,
                    timeoutMillis = timeoutMillis
                )

                if (bleResult.isSuccessful) {
                    val responseCode = bleResult.responseCode ?: 200
                    val responseMessage = bleResult.responseMessage ?: "BLE Write Success"

                    finalLog = pendingLog.copy(
                        status = TransmissionStatus.SUCCESS,
                        responseCode = responseCode,
                        responseMessage = responseMessage
                    )

                    _transmissionState.value = TransmissionState.Success(
                        responseCode = responseCode,
                        responseMessage = responseMessage,
                        log = finalLog
                    )
                } else {
                    val errorMessage = bleResult.errorMessage ?: "BLE transmission failed"
                    val responseCode = bleResult.responseCode

                    finalLog = pendingLog.copy(
                        status = TransmissionStatus.FAILED,
                        responseCode = responseCode,
                        responseMessage = errorMessage
                    )

                    _transmissionState.value = TransmissionState.Failed(
                        errorMessage = errorMessage,
                        responseCode = responseCode,
                        log = finalLog
                    )
                }
            }
        }

        _transmissionLogs.update { currentLogs ->
            listOf(finalLog) + currentLogs
        }

        return finalLog
    }

    override fun resetTransmissionState() {
        _transmissionState.value = TransmissionState.Idle
    }

    override fun clearTransmissionLogs() {
        _transmissionLogs.value = emptyList()
    }
}
