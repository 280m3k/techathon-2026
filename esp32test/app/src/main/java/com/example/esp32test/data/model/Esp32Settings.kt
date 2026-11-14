package com.example.esp32test.data.model

import kotlinx.serialization.Serializable

/**
 * Transport mode for transmitting data to ESP32.
 */
@Serializable
enum class TransportMode {
    WIFI,
    BLE
}

/**
 * Configuration settings for the target ESP32 device endpoint.
 *
 * @param ipAddress IP address / host for Wi-Fi or device name / MAC address filter for BLE.
 * @param port Network port for the HTTP server (e.g. "80" or "8080").
 * @param endpointPath Path of the API endpoint (e.g. "/api/nfc").
 * @param httpMethod HTTP method to use for transmission (POST or GET).
 * @param autoTransmit Automatically transmit tag data to ESP32 upon scan.
 * @param transportMode Transport protocol mode (WIFI or BLE).
 * @param bleServiceUuid Target BLE service UUID for NFC transmission.
 * @param bleCharacteristicUuid Target BLE characteristic UUID for writing NFC data.
 */
@Serializable
data class Esp32Settings(
    val ipAddress: String = "192.168.1.100",
    val port: String = "80",
    val endpointPath: String = "/api/nfc",
    val httpMethod: HttpMethod = HttpMethod.POST,
    val autoTransmit: Boolean = false,
    val transportMode: TransportMode = TransportMode.WIFI,
    val bleServiceUuid: String = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    val bleCharacteristicUuid: String = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
) {
    /**
     * Formats target host, port, and endpoint path or BLE description into a display string.
     */
    fun getFullHostWithPort(): String {
        if (transportMode == TransportMode.BLE) {
            return "BLE Device: ${ipAddress.ifEmpty { "ESP32" }}"
        }
        val cleanIp = ipAddress.trim()
        val cleanPort = port.trim()
        val cleanPath = if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"

        if (cleanIp.isEmpty()) return cleanPath

        val hostAndPort = if (cleanPort.isNotEmpty() && cleanPort != "80") {
            "$cleanIp:$cleanPort"
        } else {
            cleanIp
        }
        return "$hostAndPort$cleanPath"
    }
}
