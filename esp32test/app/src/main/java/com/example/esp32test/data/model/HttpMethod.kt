package com.example.esp32test.data.model

import kotlinx.serialization.Serializable

/**
 * HTTP methods supported for ESP32 NFC data transmission.
 */
@Serializable
enum class HttpMethod {
    POST,
    GET
}
