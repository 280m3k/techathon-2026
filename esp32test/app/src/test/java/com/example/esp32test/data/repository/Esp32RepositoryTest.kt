package com.example.esp32test.data.repository

import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransmissionState
import com.example.esp32test.data.model.TransmissionStatus
import com.example.esp32test.network.Esp32ApiResponse
import com.example.esp32test.network.Esp32ApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Esp32RepositoryTest {

    private lateinit var repository: Esp32RepositoryImpl

    @Before
    fun setUp() {
        repository = Esp32RepositoryImpl(FakeEsp32ApiService())
    }

    @Test
    fun testFormatTargetUrl() {
        assertEquals(
            "http://192.168.1.100/api/nfc",
            repository.formatTargetUrl("192.168.1.100")
        )
        assertEquals(
            "http://192.168.1.100:8080/api/nfc",
            repository.formatTargetUrl("192.168.1.100:8080")
        )
        assertEquals(
            "http://192.168.1.100/custom/path",
            repository.formatTargetUrl("192.168.1.100/custom/path")
        )
        assertEquals(
            "http://192.168.1.100/api/nfc",
            repository.formatTargetUrl("http://192.168.1.100/api/nfc")
        )
        assertEquals(
            "https://esp32.local/api/nfc",
            repository.formatTargetUrl("https://esp32.local/api/nfc")
        )
        assertEquals(
            "http://192.168.1.100/api/nfc",
            repository.formatTargetUrl("")
        )
        assertEquals(
            "http://192.168.1.50/api/nfc",
            repository.formatTargetUrl("  192.168.1.50/ ")
        )
    }

    @Test
    fun testSendNfcDataSuccessStateAndLog() = runTest {
        val fakeService = FakeEsp32ApiService().apply {
            nextResponse = Esp32ApiResponse(
                isSuccessful = true,
                responseCode = 200,
                responseMessage = "{\"status\":\"ok\"}"
            )
        }
        val repo = Esp32RepositoryImpl(fakeService)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Tag Payload")
        val log = repo.sendNfcData(
            nfcTagData = tagData,
            targetIpOrUrl = "192.168.1.100",
            method = HttpMethod.POST
        )

        assertEquals(TransmissionStatus.SUCCESS, log.status)
        assertEquals(200, log.responseCode)
        assertEquals("{\"status\":\"ok\"}", log.responseMessage)
        assertEquals("http://192.168.1.100/api/nfc", log.targetUrl)

        val currentState = repo.transmissionState.value
        assertTrue(currentState is TransmissionState.Success)
        val successState = currentState as TransmissionState.Success
        assertEquals(200, successState.responseCode)
        assertEquals("{\"status\":\"ok\"}", successState.responseMessage)

        val logs = repo.transmissionLogs.value
        assertEquals(1, logs.size)
        assertEquals(log.logId, logs[0].logId)
    }

    @Test
    fun testSendNfcDataFailureStateAndLog() = runTest {
        val fakeService = FakeEsp32ApiService().apply {
            nextResponse = Esp32ApiResponse(
                isSuccessful = false,
                responseCode = 500,
                errorMessage = "HTTP Error 500: Internal Server Error"
            )
        }
        val repo = Esp32RepositoryImpl(fakeService)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Tag Payload")
        val log = repo.sendNfcData(
            nfcTagData = tagData,
            targetIpOrUrl = "http://192.168.1.100/api/nfc",
            method = HttpMethod.POST
        )

        assertEquals(TransmissionStatus.FAILED, log.status)
        assertEquals(500, log.responseCode)
        assertEquals("HTTP Error 500: Internal Server Error", log.responseMessage)

        val currentState = repo.transmissionState.value
        assertTrue(currentState is TransmissionState.Failed)
        val failedState = currentState as TransmissionState.Failed
        assertEquals(500, failedState.responseCode)
        assertEquals("HTTP Error 500: Internal Server Error", failedState.errorMessage)

        val logs = repo.transmissionLogs.value
        assertEquals(1, logs.size)
        assertEquals(log.logId, logs[0].logId)
    }

    @Test
    fun testResetStateAndClearLogs() = runTest {
        val fakeService = FakeEsp32ApiService().apply {
            nextResponse = Esp32ApiResponse(
                isSuccessful = true,
                responseCode = 200,
                responseMessage = "OK"
            )
        }
        val repo = Esp32RepositoryImpl(fakeService)

        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Test")
        repo.sendNfcData(tagData, "192.168.1.100")

        assertEquals(1, repo.transmissionLogs.value.size)
        assertTrue(repo.transmissionState.value is TransmissionState.Success)

        repo.resetTransmissionState()
        assertEquals(TransmissionState.Idle, repo.transmissionState.value)

        repo.clearTransmissionLogs()
        assertTrue(repo.transmissionLogs.value.isEmpty())
    }

    @Test
    fun testBleTransmissionWithoutContext() = runTest {
        val repo = Esp32RepositoryImpl()
        val tagData = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "BLE Test")
        val settings = com.example.esp32test.data.model.Esp32Settings(
            ipAddress = "ESP32",
            transportMode = com.example.esp32test.data.model.TransportMode.BLE
        )

        val log = repo.sendNfcData(context = null, nfcTagData = tagData, settings = settings)

        assertEquals(TransmissionStatus.FAILED, log.status)
        assertEquals("Context required for BLE transmission", log.responseMessage)
        assertTrue(log.targetUrl.startsWith("ble://"))
    }

    private class FakeEsp32ApiService : Esp32ApiService {
        var nextResponse = Esp32ApiResponse(isSuccessful = true, responseCode = 200, responseMessage = "OK")

        override suspend fun sendNfcData(
            url: String,
            tagData: NfcTagData,
            method: HttpMethod,
            timeoutMillis: Long
        ): Esp32ApiResponse {
            return nextResponse
        }
    }
}
