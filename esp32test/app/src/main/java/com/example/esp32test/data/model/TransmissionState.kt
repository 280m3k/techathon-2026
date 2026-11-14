package com.example.esp32test.data.model

/**
 * Represents the current state of an NFC data transmission to an ESP32 endpoint.
 */
sealed class TransmissionState {
    /**
     * Idle state when no transmission is in progress.
     */
    object Idle : TransmissionState()

    /**
     * Transmission is currently active.
     */
    data class Transmitting(val log: TransmissionLog) : TransmissionState()

    /**
     * Transmission completed successfully.
     */
    data class Success(
        val responseCode: Int,
        val responseMessage: String,
        val log: TransmissionLog
    ) : TransmissionState()

    /**
     * Transmission failed due to network, HTTP, or timeout error.
     */
    data class Failed(
        val errorMessage: String,
        val responseCode: Int? = null,
        val log: TransmissionLog
    ) : TransmissionState()
}
