package com.example.esp32test.data.model

import kotlinx.serialization.Serializable

/**
 * Status of the HTTP payload transmission to the ESP32 target device.
 */
@Serializable
enum class TransmissionStatus {
    SUCCESS,
    FAILED,
    PENDING
}
