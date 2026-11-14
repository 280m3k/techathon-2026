package com.example.esp32test.data.model

import kotlinx.serialization.Serializable

/**
 * NFC Hardware status on the device.
 */
@Serializable
enum class NfcStatus {
    ENABLED,
    DISABLED,
    UNAVAILABLE
}
