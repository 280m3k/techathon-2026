package com.example.esp32test.ui

import android.content.Context
import com.example.esp32test.data.model.Esp32Settings
import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcStatus
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransportMode
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionState
import com.example.esp32test.data.model.TransmissionStatus
import com.example.esp32test.data.repository.Esp32Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeEsp32Repository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeEsp32Repository()
        viewModel = MainViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val state = viewModel.uiState.value
        assertEquals(NfcStatus.UNAVAILABLE, state.nfcStatus)
        assertEquals("192.168.1.100", state.settings.ipAddress)
        assertEquals("80", state.settings.port)
        assertEquals("/api/nfc", state.settings.endpointPath)
        assertEquals(HttpMethod.POST, state.settings.httpMethod)
        assertEquals(false, state.settings.autoTransmit)
        assertEquals(TransportMode.WIFI, state.settings.transportMode)
        assertNull(state.currentTag)
        assertEquals(TransmissionState.Idle, state.transmissionState)
        assertTrue(state.transmissionHistory.isEmpty())
        assertFalse(state.isBleScanning)
        assertFalse(state.isBleConnected)
    }

    @Test
    fun testUpdateSettings() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.updateIpAddress("192.168.1.200")
        viewModel.updatePort("8080")
        viewModel.updateEndpointPath("/custom/nfc")
        viewModel.updateHttpMethod(HttpMethod.GET)
        viewModel.toggleAutoTransmit(true)

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("192.168.1.200", state.settings.ipAddress)
        assertEquals("8080", state.settings.port)
        assertEquals("/custom/nfc", state.settings.endpointPath)
        assertEquals(HttpMethod.GET, state.settings.httpMethod)
        assertEquals(true, state.settings.autoTransmit)
    }

    @Test
    fun testTransportModeAndBleSettings() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.updateTransportMode(TransportMode.BLE)
        viewModel.updateIpAddress("ESP32_CUSTOM")
        viewModel.updateBleServiceUuid("0000FF00-0000-1000-8000-00805F9B34FB")
        viewModel.updateBleCharacteristicUuid("0000FF01-0000-1000-8000-00805F9B34FB")

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransportMode.BLE, state.settings.transportMode)
        assertEquals("ESP32_CUSTOM", state.settings.ipAddress)
        assertEquals("0000FF00-0000-1000-8000-00805F9B34FB", state.settings.bleServiceUuid)
        assertEquals("0000FF01-0000-1000-8000-00805F9B34FB", state.settings.bleCharacteristicUuid)
    }

    @Test
    fun testBleScanAndDisconnect() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.updateTransportMode(TransportMode.BLE)
        viewModel.startBleScan()

        assertTrue(viewModel.uiState.value.isBleScanning)

        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBleScanning)
        assertTrue(viewModel.uiState.value.isBleConnected)

        viewModel.disconnectBle()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBleConnected)
    }

    @Test
    fun testOnTagScannedManualTransmit() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.toggleAutoTransmit(false)
        val tag = NfcTagData(tagUid = "04:A2:8F:12", payloadText = "Manual Payload")

        viewModel.onTagScanned(tagData = tag)
        testScheduler.advanceUntilIdle()

        assertEquals(tag, viewModel.uiState.value.currentTag)
        assertEquals(0, fakeRepository.sendCount)

        viewModel.transmitTagData()
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.sendCount)
        assertEquals("04:A2:8F:12", fakeRepository.lastSentTag?.tagUid)
    }

    @Test
    fun testOnTagScannedAutoTransmit() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.toggleAutoTransmit(true)
        val tag = NfcTagData(tagUid = "04:BB:CC:DD", payloadText = "Auto Payload")

        viewModel.onTagScanned(tagData = tag)
        testScheduler.advanceUntilIdle()

        assertEquals(tag, viewModel.uiState.value.currentTag)
        assertEquals(1, fakeRepository.sendCount)
        assertEquals("04:BB:CC:DD", fakeRepository.lastSentTag?.tagUid)
    }

    @Test
    fun testSimulateTagScan() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.simulateTagScan()
        testScheduler.advanceUntilIdle()

        val currentTag = viewModel.uiState.value.currentTag
        assertNotNull(currentTag)
        assertTrue(currentTag!!.tagUid.isNotEmpty())
        assertTrue(currentTag.payloadText.contains("ESP32 NFC Test Tag"))
    }

    @Test
    fun testRetryLastTransmission() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val tag = NfcTagData(tagUid = "04:11:22:33", payloadText = "Retry Test")
        viewModel.onTagScanned(tagData = tag)
        testScheduler.advanceUntilIdle()

        viewModel.transmitTagData()
        testScheduler.advanceUntilIdle()
        assertEquals(1, fakeRepository.sendCount)

        viewModel.retryLastTransmission()
        testScheduler.advanceUntilIdle()
        assertEquals(2, fakeRepository.sendCount)
    }

    @Test
    fun testClearHistoryAndResetState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val tag = NfcTagData(tagUid = "04:55:66:77", payloadText = "Clear Test")
        viewModel.onTagScanned(tagData = tag)
        viewModel.transmitTagData()
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.transmissionHistory.size)

        viewModel.clearHistory()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.transmissionHistory.isEmpty())

        viewModel.resetTransmissionState()
        testScheduler.advanceUntilIdle()
        assertEquals(TransmissionState.Idle, viewModel.uiState.value.transmissionState)
    }

    @Test
    fun testUpdateNfcStatus() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.updateNfcStatus(NfcStatus.ENABLED)
        testScheduler.advanceUntilIdle()

        assertEquals(NfcStatus.ENABLED, viewModel.uiState.value.nfcStatus)
    }

    private class FakeEsp32Repository : Esp32Repository {
        var sendCount = 0
        var lastSentTag: NfcTagData? = null

        private val _state = MutableStateFlow<TransmissionState>(TransmissionState.Idle)
        override val transmissionState: StateFlow<TransmissionState> = _state.asStateFlow()

        private val _logs = MutableStateFlow<List<TransmissionLog>>(emptyList())
        override val transmissionLogs: StateFlow<List<TransmissionLog>> = _logs.asStateFlow()

        override fun formatTargetUrl(ipOrUrl: String, defaultPath: String): String {
            return "http://$ipOrUrl$defaultPath"
        }

        override suspend fun sendNfcData(
            nfcTagData: NfcTagData,
            targetIpOrUrl: String,
            method: HttpMethod,
            timeoutMillis: Long
        ): TransmissionLog {
            sendCount++
            lastSentTag = nfcTagData
            val log = TransmissionLog(
                nfcTagData = nfcTagData,
                targetUrl = targetIpOrUrl,
                status = TransmissionStatus.SUCCESS,
                responseCode = 200,
                responseMessage = "OK"
            )
            _state.value = TransmissionState.Success(200, "OK", log)
            _logs.value = listOf(log) + _logs.value
            return log
        }

        override suspend fun sendNfcData(
            context: Context?,
            nfcTagData: NfcTagData,
            settings: Esp32Settings,
            timeoutMillis: Long
        ): TransmissionLog {
            val url = if (settings.transportMode == TransportMode.WIFI) {
                formatTargetUrl(settings.ipAddress, settings.endpointPath)
            } else {
                "ble://${settings.ipAddress.ifEmpty { "ESP32" }}${settings.endpointPath}"
            }
            return sendNfcData(nfcTagData, url, settings.httpMethod, timeoutMillis)
        }

        override fun resetTransmissionState() {
            _state.value = TransmissionState.Idle
        }

        override fun clearTransmissionLogs() {
            _logs.value = emptyList()
        }
    }
}
