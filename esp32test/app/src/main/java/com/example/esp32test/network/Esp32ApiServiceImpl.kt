package com.example.esp32test.network

import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcTagData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Default implementation of [Esp32ApiService] using OkHttp3 HTTP client.
 */
class Esp32ApiServiceImpl(
    private val client: OkHttpClient = OkHttpClient()
) : Esp32ApiService {

    override suspend fun sendNfcData(
        url: String,
        tagData: NfcTagData,
        method: HttpMethod,
        timeoutMillis: Long
    ): Esp32ApiResponse = withContext(Dispatchers.IO) {
        val callClient = client.newBuilder()
            .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        val request = try {
            buildRequest(url, tagData, method)
        } catch (e: Exception) {
            return@withContext Esp32ApiResponse(
                isSuccessful = false,
                errorMessage = "Invalid URL or request format: ${e.message}"
            )
        }

        try {
            callClient.newCall(request).execute().use { response ->
                val responseBody = response.body
                val bodyString = responseBody?.string() ?: ""
                if (response.isSuccessful) {
                    Esp32ApiResponse(
                        isSuccessful = true,
                        responseCode = response.code,
                        responseMessage = bodyString.ifEmpty { "OK" }
                    )
                } else {
                    Esp32ApiResponse(
                        isSuccessful = false,
                        responseCode = response.code,
                        responseMessage = bodyString,
                        errorMessage = "HTTP Error ${response.code}: ${response.message.ifEmpty { "Bad Request/Server Error" }}"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Esp32ApiResponse(
                isSuccessful = false,
                errorMessage = "Connection timed out after ${timeoutMillis}ms: ${e.message}"
            )
        } catch (e: ConnectException) {
            Esp32ApiResponse(
                isSuccessful = false,
                errorMessage = "Failed to connect to ESP32 at $url: ${e.message}"
            )
        } catch (e: UnknownHostException) {
            Esp32ApiResponse(
                isSuccessful = false,
                errorMessage = "Unknown host or IP address: ${e.message}"
            )
        } catch (e: IOException) {
            Esp32ApiResponse(
                isSuccessful = false,
                errorMessage = "Network I/O error: ${e.message}"
            )
        } catch (e: Exception) {
            Esp32ApiResponse(
                isSuccessful = false,
                errorMessage = "Unexpected error: ${e.message}"
            )
        }
    }

    private fun buildRequest(url: String, tagData: NfcTagData, method: HttpMethod): Request {
        return when (method) {
            HttpMethod.POST -> {
                val jsonPayload = createJsonPayload(tagData)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonPayload.toRequestBody(mediaType)
                Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
            }
            HttpMethod.GET -> {
                val httpUrl = url.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Malformed URL: $url")

                val urlBuilder = httpUrl.newBuilder()
                    .addQueryParameter("tagUid", tagData.tagUid)
                    .addQueryParameter("payloadText", tagData.payloadText)
                    .addQueryParameter("timestamp", tagData.timestamp.toString())

                if (tagData.records.isNotEmpty()) {
                    urlBuilder.addQueryParameter("records", tagData.records.joinToString(","))
                }
                if (tagData.techList.isNotEmpty()) {
                    urlBuilder.addQueryParameter("techList", tagData.techList.joinToString(","))
                }

                Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()
            }
        }
    }

    companion object {
        /**
         * Formats [NfcTagData] into a standard JSON string.
         */
        fun createJsonPayload(tagData: NfcTagData): String {
            val recordsJson = tagData.records.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
            val techJson = tagData.techList.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" }
            return """{"tagUid":"${escapeJson(tagData.tagUid)}","payloadText":"${escapeJson(tagData.payloadText)}","records":$recordsJson,"techList":$techJson,"timestamp":${tagData.timestamp}}"""
        }

        private fun escapeJson(value: String): String {
            return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\u000C", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }
}
