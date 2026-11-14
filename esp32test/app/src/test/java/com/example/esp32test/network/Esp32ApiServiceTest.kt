package com.example.esp32test.network

import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcTagData
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class Esp32ApiServiceTest {

    @Test
    fun testJsonPayloadFormatting() {
        val tagData = NfcTagData(
            tagUid = "04:A2:8F:12",
            payloadText = "Hello ESP32",
            records = listOf("Hello ESP32"),
            techList = listOf("Ndef", "NfcA"),
            timestamp = 1700000000000L
        )

        val json = Esp32ApiServiceImpl.createJsonPayload(tagData)

        assertTrue(json.contains("\"tagUid\":\"04:A2:8F:12\""))
        assertTrue(json.contains("\"payloadText\":\"Hello ESP32\""))
        assertTrue(json.contains("\"records\":[\"Hello ESP32\"]"))
        assertTrue(json.contains("\"techList\":[\"Ndef\",\"NfcA\"]"))
        assertTrue(json.contains("\"timestamp\":1700000000000"))
    }

    @Test
    fun testSendNfcDataPostSuccess() = runTest {
        var recordedMethod: String? = null
        var recordedContentType: String? = null
        var recordedBody: String? = null

        val mockInterceptor = Interceptor { chain ->
            val request = chain.request()
            recordedMethod = request.method
            recordedContentType = request.body?.contentType()?.toString()

            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            recordedBody = buffer.readUtf8()

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{\"status\":\"success\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val apiService = Esp32ApiServiceImpl(client)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Payload 1")
        val response = apiService.sendNfcData(
            url = "http://192.168.1.100/api/nfc",
            tagData = tagData,
            method = HttpMethod.POST
        )

        assertTrue(response.isSuccessful)
        assertEquals(200, response.responseCode)
        assertEquals("{\"status\":\"success\"}", response.responseMessage)
        assertEquals("POST", recordedMethod)
        assertNotNull(recordedContentType)
        assertTrue(recordedContentType!!.contains("application/json"))
        assertNotNull(recordedBody)
        assertTrue(recordedBody!!.contains("04:A2:8F:12"))
    }

    @Test
    fun testSendNfcDataGetSuccess() = runTest {
        var recordedUrl: String? = null

        val mockInterceptor = Interceptor { chain ->
            val request = chain.request()
            recordedUrl = request.url.toString()

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("GET OK".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val apiService = Esp32ApiServiceImpl(client)

        val tagData = NfcTagData(
            tagUid = "04:A2:8F:12",
            payloadText = "Get Payload",
            records = listOf("Rec1"),
            techList = listOf("NfcA")
        )

        val response = apiService.sendNfcData(
            url = "http://192.168.1.100/api/nfc",
            tagData = tagData,
            method = HttpMethod.GET
        )

        assertTrue(response.isSuccessful)
        assertEquals(200, response.responseCode)
        assertEquals("GET OK", response.responseMessage)
        val url = recordedUrl
        assertNotNull(url)
        assertTrue("URL should contain tagUid parameter: $url", url!!.contains("tagUid="))
        assertTrue("URL should contain payloadText parameter: $url", url.contains("payloadText="))
        assertTrue("URL should contain records parameter: $url", url.contains("records=Rec1"))
        assertTrue("URL should contain techList parameter: $url", url.contains("techList=NfcA"))
    }

    @Test
    fun testSendNfcDataHttpError() = runTest {
        val mockInterceptor = Interceptor { chain ->
            val request = chain.request()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("Endpoint Not Found".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val apiService = Esp32ApiServiceImpl(client)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Payload")
        val response = apiService.sendNfcData(
            url = "http://192.168.1.100/invalid",
            tagData = tagData,
            method = HttpMethod.POST
        )

        assertFalse(response.isSuccessful)
        assertEquals(404, response.responseCode)
        assertEquals("Endpoint Not Found", response.responseMessage)
        assertNotNull(response.errorMessage)
        assertTrue(response.errorMessage!!.contains("404"))
    }

    @Test
    fun testSendNfcDataTimeout() = runTest {
        val mockInterceptor = Interceptor {
            throw SocketTimeoutException("Read timed out")
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val apiService = Esp32ApiServiceImpl(client)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Payload")
        val response = apiService.sendNfcData(
            url = "http://192.168.1.100/api/nfc",
            tagData = tagData,
            timeoutMillis = 1000L
        )

        assertFalse(response.isSuccessful)
        assertNotNull(response.errorMessage)
        assertTrue(response.errorMessage!!.contains("timed out"))
    }

    @Test
    fun testSendNfcDataConnectException() = runTest {
        val mockInterceptor = Interceptor {
            throw ConnectException("Connection refused")
        }

        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val apiService = Esp32ApiServiceImpl(client)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Payload")
        val response = apiService.sendNfcData(
            url = "http://192.168.1.100/api/nfc",
            tagData = tagData
        )

        assertFalse(response.isSuccessful)
        assertNotNull(response.errorMessage)
        assertTrue(response.errorMessage!!.contains("Failed to connect"))
    }
}
