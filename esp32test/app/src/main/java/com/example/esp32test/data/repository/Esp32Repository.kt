package com.example.esp32test.data.repository

import android.content.Context
import com.example.esp32test.data.model.Esp32Settings
import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for orchestrating ESP32 transmission requests (Wi-Fi and BLE), maintaining state,
 * formatting target URLs, and storing transmission history.
 */
interface Esp32Repository {

    /**
     * Flow of current transmission state ([TransmissionState.Idle], [TransmissionState.Transmitting],
     * [TransmissionState.Success], [TransmissionState.Failed]).
     */
    val transmissionState: StateFlow<TransmissionState>

    /**
     * Flow of list of recorded transmission logs.
     */
    val transmissionLogs: StateFlow<List<TransmissionLog>>

    /**
     * Normalizes an IP address or URL into a fully-qualified HTTP/HTTPS target URL.
     *
     * @param ipOrUrl Raw IP address or URL provided by user (e.g. "192.168.1.100").
     * @param defaultPath Default endpoint path to append if missing (default: "/api/nfc").
     * @return Normalized URL string (e.g. "http://192.168.1.100/api/nfc").
     */
    fun formatTargetUrl(ipOrUrl: String, defaultPath: String = "/api/nfc"): String

    /**
     * Transmits [nfcTagData] to the ESP32 endpoint at [targetIpOrUrl] via Wi-Fi (legacy method).
     *
     * @param nfcTagData NFC tag data payload.
     * @param targetIpOrUrl Target ESP32 IP address or URL.
     * @param method HTTP method (POST or GET).
     * @param timeoutMillis Network timeout in milliseconds.
     * @return [TransmissionLog] detailing the result of this transmission attempt.
     */
    suspend fun sendNfcData(
        nfcTagData: NfcTagData,
        targetIpOrUrl: String,
        method: HttpMethod = HttpMethod.POST,
        timeoutMillis: Long = 5000L
    ): TransmissionLog

    /**
     * Transmits [nfcTagData] to ESP32 according to [Esp32Settings] (supports Wi-Fi and BLE).
     *
     * @param context Android context required for BLE operations (nullable for Wi-Fi).
     * @param nfcTagData NFC tag data payload.
     * @param settings ESP32 configuration settings including transport mode.
     * @param timeoutMillis Operation timeout in milliseconds.
     * @return [TransmissionLog] detailing the result of this transmission attempt.
     */
    suspend fun sendNfcData(
        context: Context?,
        nfcTagData: NfcTagData,
        settings: Esp32Settings,
        timeoutMillis: Long = 20000L
    ): TransmissionLog

    /**
     * Resets state to [TransmissionState.Idle].
     */
    fun resetTransmissionState()

    /**
     * Clears all stored transmission logs.
     */
    fun clearTransmissionLogs()
}
