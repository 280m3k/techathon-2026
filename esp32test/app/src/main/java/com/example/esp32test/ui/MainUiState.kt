package com.example.esp32test.ui

import com.example.esp32test.data.model.Esp32Settings
import com.example.esp32test.data.model.NfcStatus
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.NordicRune
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionState

/**
 * UI State for the Main ESP32 NFC Gateway Dashboard.
 */
data class MainUiState(
    val nfcStatus: NfcStatus = NfcStatus.UNAVAILABLE,
    val settings: Esp32Settings = Esp32Settings(),
    val currentTag: NfcTagData? = null,
    val transmissionState: TransmissionState = TransmissionState.Idle,
    val transmissionHistory: List<TransmissionLog> = emptyList(),
    val isBleScanning: Boolean = false,
    val isBleConnected: Boolean = false,
    val currentScanArray: List<Int> = emptyList(),
    val savedScanArrays: List<List<Int>> = emptyList(),
    val detectedRune: NordicRune? = null
)
