package com.example.esp32test.network

import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcTagData

/**
 * Network response object containing status details of an ESP32 HTTP request.
 */
data class Esp32ApiResponse(
    val isSuccessful: Boolean,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Service interface for transmitting NFC tag payloads over HTTP/REST to an ESP32 device.
 */
interface Esp32ApiService {

    /**
     * Sends NFC tag data to an ESP32 HTTP endpoint using POST or GET.
     *
     * @param url Full target URL endpoint.
     * @param tagData NFC tag data payload.
     * @param method HTTP method (POST or GET).
     * @param timeoutMillis Timeout limit for connect and read operations in milliseconds.
     * @return [Esp32ApiResponse] indicating outcome and response data or error.
     */
    suspend fun sendNfcData(
        url: String,
        tagData: NfcTagData,
        method: HttpMethod = HttpMethod.POST,
        timeoutMillis: Long = 5000L
    ): Esp32ApiResponse
}
