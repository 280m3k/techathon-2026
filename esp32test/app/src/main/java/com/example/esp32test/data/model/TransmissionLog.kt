package com.example.esp32test.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Data model for logging transmission attempts of NFC data to an ESP32 device.
 *
 * @param logId Unique identifier for this transmission log entry.
 * @param nfcTagData The NFC tag data payload sent or intended for transmission.
 * @param targetUrl Target ESP32 URL or IP address (e.g. "http://192.168.1.100/api/nfc").
 * @param status Status of transmission (SUCCESS, FAILED, PENDING).
 * @param responseCode Optional HTTP response code returned by the ESP32 server.
 * @param responseMessage Optional response message or error details.
 * @param timestamp Log entry timestamp in milliseconds since epoch.
 */
@Serializable
data class TransmissionLog(
    val logId: String = UUID.randomUUID().toString(),
    val nfcTagData: NfcTagData,
    val targetUrl: String,
    val status: TransmissionStatus,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
