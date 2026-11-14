package com.example.esp32test.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.esp32test.data.model.Esp32Settings
import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.NfcStatus
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.NordicRune
import com.example.esp32test.data.model.TransportMode
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionState
import com.example.esp32test.data.model.TransmissionStatus
import com.example.esp32test.ui.components.DetectedRuneCard
import com.example.esp32test.ui.components.Esp32SettingsCard
import com.example.esp32test.ui.components.LastScannedTagCard
import com.example.esp32test.ui.components.NfcStatusCard
import com.example.esp32test.ui.components.ScannedIntegerArraysCard
import com.example.esp32test.ui.components.TransmissionHistoryCard
import com.example.esp32test.ui.components.TransmissionStatusBanner
import com.example.esp32test.ui.theme.Esp32testTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onIpAddressChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onEndpointPathChanged: (String) -> Unit,
    onHttpMethodChanged: (HttpMethod) -> Unit,
    onAutoTransmitToggled: (Boolean) -> Unit,
    onTransportModeChanged: (TransportMode) -> Unit,
    onBleServiceUuidChanged: (String) -> Unit,
    onBleCharacteristicUuidChanged: (String) -> Unit,
    onScanBleClick: () -> Unit,
    onDisconnectBleClick: () -> Unit,
    onSimulateScanClick: () -> Unit,
    onTransmitClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissBannerClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onClearScanArraysClick: () -> Unit = {},
    onDismissRuneClick: () -> Unit = {},
    onSimulateRuneClick: (NordicRune) -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Nfc,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "ESP32 NFC Gateway",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 600.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uiState.detectedRune != null) {
                            DetectedRuneCard(
                                rune = uiState.detectedRune,
                                onDismissClick = onDismissRuneClick
                            )
                        }

                        NfcStatusCard(
                            nfcStatus = uiState.nfcStatus,
                            onOpenNfcSettings = onOpenNfcSettings
                        )

                        TransmissionStatusBanner(
                            transmissionState = uiState.transmissionState,
                            onRetryClick = onRetryClick,
                            onDismissClick = onDismissBannerClick
                        )

                        Esp32SettingsCard(
                            settings = uiState.settings,
                            isBleScanning = uiState.isBleScanning,
                            isBleConnected = uiState.isBleConnected,
                            onIpAddressChanged = onIpAddressChanged,
                            onPortChanged = onPortChanged,
                            onEndpointPathChanged = onEndpointPathChanged,
                            onHttpMethodChanged = onHttpMethodChanged,
                            onAutoTransmitToggled = onAutoTransmitToggled,
                            onTransportModeChanged = onTransportModeChanged,
                            onBleServiceUuidChanged = onBleServiceUuidChanged,
                            onBleCharacteristicUuidChanged = onBleCharacteristicUuidChanged,
                            onScanBleClick = onScanBleClick,
                            onDisconnectBleClick = onDisconnectBleClick
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LastScannedTagCard(
                            tagData = uiState.currentTag,
                            isTransmitting = uiState.transmissionState is TransmissionState.Transmitting,
                            onTransmitClick = onTransmitClick,
                            onSimulateScanClick = onSimulateScanClick
                        )

                        ScannedIntegerArraysCard(
                            currentScanArray = uiState.currentScanArray,
                            savedScanArrays = uiState.savedScanArrays,
                            onClearScanArraysClick = onClearScanArraysClick,
                            onSimulateRuneClick = onSimulateRuneClick
                        )

                        TransmissionHistoryCard(
                            history = uiState.transmissionHistory,
                            onClearHistoryClick = onClearHistoryClick
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.detectedRune != null) {
                        DetectedRuneCard(
                            rune = uiState.detectedRune,
                            onDismissClick = onDismissRuneClick
                        )
                    }

                    NfcStatusCard(
                        nfcStatus = uiState.nfcStatus,
                        onOpenNfcSettings = onOpenNfcSettings
                    )

                    TransmissionStatusBanner(
                        transmissionState = uiState.transmissionState,
                        onRetryClick = onRetryClick,
                        onDismissClick = onDismissBannerClick
                    )

                    Esp32SettingsCard(
                        settings = uiState.settings,
                        isBleScanning = uiState.isBleScanning,
                        isBleConnected = uiState.isBleConnected,
                        onIpAddressChanged = onIpAddressChanged,
                        onPortChanged = onPortChanged,
                        onEndpointPathChanged = onEndpointPathChanged,
                        onHttpMethodChanged = onHttpMethodChanged,
                        onAutoTransmitToggled = onAutoTransmitToggled,
                        onTransportModeChanged = onTransportModeChanged,
                        onBleServiceUuidChanged = onBleServiceUuidChanged,
                        onBleCharacteristicUuidChanged = onBleCharacteristicUuidChanged,
                        onScanBleClick = onScanBleClick,
                        onDisconnectBleClick = onDisconnectBleClick
                    )

                    LastScannedTagCard(
                        tagData = uiState.currentTag,
                        isTransmitting = uiState.transmissionState is TransmissionState.Transmitting,
                        onTransmitClick = onTransmitClick,
                        onSimulateScanClick = onSimulateScanClick
                    )

                    ScannedIntegerArraysCard(
                        currentScanArray = uiState.currentScanArray,
                        savedScanArrays = uiState.savedScanArrays,
                        onClearScanArraysClick = onClearScanArraysClick,
                        onSimulateRuneClick = onSimulateRuneClick
                    )

                    TransmissionHistoryCard(
                        history = uiState.transmissionHistory,
                        onClearHistoryClick = onClearHistoryClick
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Preview(name = "Dashboard Phone Portrait", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun DashboardScreenCompactPreview() {
    Esp32testTheme {
        DashboardScreen(
            uiState = MainUiState(
                nfcStatus = NfcStatus.ENABLED,
                settings = Esp32Settings(
                    ipAddress = "192.168.1.100",
                    port = "80",
                    endpointPath = "/api/nfc",
                    httpMethod = HttpMethod.POST,
                    autoTransmit = true
                ),
                currentTag = NfcTagData(
                    tagUid = "04:A2:8F:12",
                    payloadText = "Sample Tag Payload",
                    techList = listOf("NfcA", "Ndef")
                ),
                transmissionState = TransmissionState.Success(
                    responseCode = 200,
                    responseMessage = "OK",
                    log = TransmissionLog(
                        nfcTagData = NfcTagData("04:A2:8F:12", "Sample Tag Payload"),
                        targetUrl = "http://192.168.1.100/api/nfc",
                        status = TransmissionStatus.SUCCESS
                    )
                ),
                transmissionHistory = listOf(
                    TransmissionLog(
                        nfcTagData = NfcTagData("04:A2:8F:12", "Sample Tag Payload"),
                        targetUrl = "http://192.168.1.100/api/nfc",
                        status = TransmissionStatus.SUCCESS,
                        responseCode = 200,
                        responseMessage = "OK"
                    )
                )
            ),
            onIpAddressChanged = {},
            onPortChanged = {},
            onEndpointPathChanged = {},
            onHttpMethodChanged = {},
            onAutoTransmitToggled = {},
            onTransportModeChanged = {},
            onBleServiceUuidChanged = {},
            onBleCharacteristicUuidChanged = {},
            onScanBleClick = {},
            onDisconnectBleClick = {},
            onSimulateScanClick = {},
            onTransmitClick = {},
            onRetryClick = {},
            onDismissBannerClick = {},
            onClearHistoryClick = {},
            onOpenNfcSettings = {}
        )
    }
}

@Preview(name = "Dashboard Tablet Landscape", showBackground = true, widthDp = 840, heightDp = 600)
@Composable
private fun DashboardScreenWidePreview() {
    Esp32testTheme {
        DashboardScreen(
            uiState = MainUiState(
                nfcStatus = NfcStatus.ENABLED,
                settings = Esp32Settings(
                    ipAddress = "ESP32_BLE",
                    autoTransmit = false,
                    transportMode = TransportMode.BLE
                ),
                currentTag = NfcTagData(
                    tagUid = "04:A2:8F:12",
                    payloadText = "Sample Tag Payload",
                    techList = listOf("NfcA", "Ndef")
                ),
                isBleConnected = true
            ),
            onIpAddressChanged = {},
            onPortChanged = {},
            onEndpointPathChanged = {},
            onHttpMethodChanged = {},
            onAutoTransmitToggled = {},
            onTransportModeChanged = {},
            onBleServiceUuidChanged = {},
            onBleCharacteristicUuidChanged = {},
            onScanBleClick = {},
            onDisconnectBleClick = {},
            onSimulateScanClick = {},
            onTransmitClick = {},
            onRetryClick = {},
            onDismissBannerClick = {},
            onClearHistoryClick = {},
            onOpenNfcSettings = {}
        )
    }
}
