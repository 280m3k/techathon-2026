package com.example.esp32test

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.esp32test.ble.BlePermissions
import com.example.esp32test.data.model.TransportMode
import com.example.esp32test.nfc.NfcManager
import com.example.esp32test.ui.DashboardScreen
import com.example.esp32test.ui.MainViewModel
import com.example.esp32test.ui.theme.Esp32testTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private fun ensureBlePermissions() {
        if (!BlePermissions.hasAll(this)) {
            blePermissionLauncher.launch(BlePermissions.required())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureBlePermissions()
        setContent {
            Esp32testTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                DashboardScreen(
                    uiState = uiState,
                    onIpAddressChanged = viewModel::updateIpAddress,
                    onPortChanged = viewModel::updatePort,
                    onEndpointPathChanged = viewModel::updateEndpointPath,
                    onHttpMethodChanged = viewModel::updateHttpMethod,
                    onAutoTransmitToggled = viewModel::toggleAutoTransmit,
                    onTransportModeChanged = { mode ->
                        if (mode == TransportMode.BLE) ensureBlePermissions()
                        viewModel.updateTransportMode(mode)
                    },
                    onBleServiceUuidChanged = viewModel::updateBleServiceUuid,
                    onBleCharacteristicUuidChanged = viewModel::updateBleCharacteristicUuid,
                    onScanBleClick = {
                        ensureBlePermissions()
                        viewModel.startBleScan(this)
                    },
                    onDisconnectBleClick = viewModel::disconnectBle,
                    onSimulateScanClick = { viewModel.simulateTagScan(this) },
                    onTransmitClick = {
                        ensureBlePermissions()
                        viewModel.transmitTagData(this)
                    },
                    onRetryClick = {
                        ensureBlePermissions()
                        viewModel.retryLastTransmission(this)
                    },
                    onDismissBannerClick = viewModel::resetTransmissionState,
                    onClearHistoryClick = viewModel::clearHistory,
                    onOpenNfcSettings = {
                        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNfcStatus(this)
        NfcManager.enableReaderMode(this, { tag ->
            val tagData = NfcManager.parseTag(tag)
            runOnUiThread {
                viewModel.onTagScanned(this, tagData)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        NfcManager.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tagData = NfcManager.parseTagFromIntent(intent)
        if (tagData != null) {
            viewModel.onTagScanned(this, tagData)
        }
    }
}
