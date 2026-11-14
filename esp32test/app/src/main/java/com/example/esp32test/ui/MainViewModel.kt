package com.example.esp32test.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcStatus
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.NordicRune
import com.example.esp32test.data.model.TransportMode
import com.example.esp32test.data.repository.Esp32Repository
import com.example.esp32test.data.repository.Esp32RepositoryImpl
import com.example.esp32test.nfc.NfcManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: Esp32Repository = Esp32RepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.transmissionState.collect { state ->
                _uiState.update { it.copy(transmissionState = state) }
            }
        }
        viewModelScope.launch {
            repository.transmissionLogs.collect { logs ->
                _uiState.update { it.copy(transmissionHistory = logs) }
            }
        }
    }

    fun checkNfcStatus(context: Context) {
        val status = when {
            !NfcManager.isNfcSupported(context) -> NfcStatus.UNAVAILABLE
            !NfcManager.isNfcEnabled(context) -> NfcStatus.DISABLED
            else -> NfcStatus.ENABLED
        }
        _uiState.update { it.copy(nfcStatus = status) }
    }

    fun updateNfcStatus(status: NfcStatus) {
        _uiState.update { it.copy(nfcStatus = status) }
    }

    fun updateIpAddress(ip: String) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(ipAddress = ip))
        }
    }

    fun updatePort(port: String) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(port = port))
        }
    }

    fun updateEndpointPath(path: String) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(endpointPath = path))
        }
    }

    fun updateHttpMethod(method: HttpMethod) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(httpMethod = method))
        }
    }

    fun toggleAutoTransmit(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(autoTransmit = enabled))
        }
    }

    fun updateTransportMode(mode: TransportMode) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(transportMode = mode))
        }
    }

    fun updateBleServiceUuid(uuid: String) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(bleServiceUuid = uuid))
        }
    }

    fun updateBleCharacteristicUuid(uuid: String) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(bleCharacteristicUuid = uuid))
        }
    }

    fun startBleScan(context: Context? = null) {
        _uiState.update { it.copy(isBleScanning = true) }
        viewModelScope.launch {
            delay(1000L)
            _uiState.update { it.copy(isBleScanning = false, isBleConnected = true) }
        }
    }

    private var batchTimerJob: Job? = null

    fun disconnectBle() {
        _uiState.update { it.copy(isBleConnected = false, isBleScanning = false) }
    }

    fun onTagScanned(context: Context? = null, tagData: NfcTagData) {
        val parsedInt = NfcManager.extractIntegerFromText(tagData.payloadText)

        batchTimerJob?.cancel()

        _uiState.update { state ->
            state.copy(
                currentTag = tagData,
                currentScanArray = state.currentScanArray + parsedInt
            )
        }

        batchTimerJob = viewModelScope.launch {
            delay(5000L)
            _uiState.update { state ->
                if (state.currentScanArray.isNotEmpty()) {
                    val completedArray = state.currentScanArray
                    val matchedRune = NordicRune.matchPattern(completedArray)
                    state.copy(
                        savedScanArrays = state.savedScanArrays + listOf(completedArray),
                        currentScanArray = emptyList(),
                        detectedRune = matchedRune ?: state.detectedRune
                    )
                } else {
                    state
                }
            }
        }

        if (_uiState.value.settings.autoTransmit) {
            transmitTagData(context, tagData)
        }
    }

    fun dismissRune() {
        _uiState.update { it.copy(detectedRune = null) }
    }

    fun clearScanArrays() {
        batchTimerJob?.cancel()
        _uiState.update { state ->
            state.copy(
                currentScanArray = emptyList(),
                savedScanArrays = emptyList(),
                detectedRune = null
            )
        }
    }

    fun simulateRuneScan(rune: NordicRune, context: Context? = null) {
        viewModelScope.launch {
            rune.pattern.forEachIndexed { index, number ->
                val mockTagData = NfcTagData(
                    tagUid = "04:RU:NE:%02X".format(number),
                    payloadText = number.toString(),
                    records = listOf("Rune Record #$number"),
                    techList = listOf("NfcA", "Ndef")
                )
                onTagScanned(context, mockTagData)
                if (index < rune.pattern.size - 1) {
                    delay(200L)
                }
            }
        }
    }

    fun simulateTagScan(context: Context? = null) {
        val randomNum = (1000..9999).random()
        val mockBytes = byteArrayOf(
            0x04.toByte(),
            (10..99).random().toByte(),
            (10..99).random().toByte(),
            (10..99).random().toByte()
        )
        val mockUid = NfcManager.bytesToHexString(mockBytes)
        val mockTagData = NfcTagData(
            tagUid = mockUid,
            payloadText = "ESP32 NFC Test Tag #$randomNum",
            records = listOf("NFC Record #$randomNum"),
            techList = listOf("NfcA", "Ndef")
        )
        onTagScanned(context, mockTagData)
    }

    fun transmitTagData(context: Context? = null, tagData: NfcTagData? = null) {
        val tagToTransmit = tagData ?: _uiState.value.currentTag ?: return
        val currentSettings = _uiState.value.settings

        viewModelScope.launch {
            repository.sendNfcData(
                context = context,
                nfcTagData = tagToTransmit,
                settings = currentSettings
            )
        }
    }

    fun retryLastTransmission(context: Context? = null) {
        val tagToRetry = _uiState.value.currentTag
            ?: repository.transmissionLogs.value.firstOrNull()?.nfcTagData
            ?: return
        transmitTagData(context, tagToRetry)
    }

    fun clearHistory() {
        repository.clearTransmissionLogs()
    }

    fun resetTransmissionState() {
        repository.resetTransmissionState()
    }
}
