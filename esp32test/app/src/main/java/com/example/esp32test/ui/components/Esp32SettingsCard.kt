package com.example.esp32test.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.esp32test.data.model.Esp32Settings
import com.example.esp32test.data.model.HttpMethod
import com.example.esp32test.data.model.TransportMode
import com.example.esp32test.ui.theme.Esp32testTheme

@Composable
fun Esp32SettingsCard(
    settings: Esp32Settings,
    isBleScanning: Boolean = false,
    isBleConnected: Boolean = false,
    onIpAddressChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onEndpointPathChanged: (String) -> Unit,
    onHttpMethodChanged: (HttpMethod) -> Unit,
    onAutoTransmitToggled: (Boolean) -> Unit,
    onTransportModeChanged: (TransportMode) -> Unit,
    onBleServiceUuidChanged: (String) -> Unit,
    onBleCharacteristicUuidChanged: (String) -> Unit,
    onScanBleClick: () -> Unit = {},
    onDisconnectBleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (settings.transportMode == TransportMode.WIFI) Icons.Rounded.Router else Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ESP32 Target Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure transport and endpoint for tag transmission",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (settings.transportMode == TransportMode.BLE) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (isBleConnected) "Connected" else if (isBleScanning) "Scanning..." else "Disconnected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBleConnected) Icons.Rounded.CheckCircle else Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isBleConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isBleConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport Mode Selection
            Text(
                text = "Transmission Transport",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransportMode.entries.forEach { mode ->
                    val selected = settings.transportMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onTransportModeChanged(mode) },
                        label = {
                            Text(
                                text = if (mode == TransportMode.WIFI) "Wi-Fi (HTTP)" else "Bluetooth LE",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == TransportMode.WIFI) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (settings.transportMode == TransportMode.WIFI) {
                // Wi-Fi settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = settings.ipAddress,
                        onValueChange = onIpAddressChanged,
                        modifier = Modifier.weight(2f),
                        label = { Text("IP / Host") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Lan,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = settings.port,
                        onValueChange = onPortChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text("Port") },
                        placeholder = { Text("80") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = settings.endpointPath,
                    onValueChange = onEndpointPathChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Endpoint Path") },
                    placeholder = { Text("/api/nfc") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "HTTP Request Method",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HttpMethod.entries.forEach { method ->
                        val selected = settings.httpMethod == method
                        FilterChip(
                            selected = selected,
                            onClick = { onHttpMethodChanged(method) },
                            label = {
                                Text(
                                    text = method.name,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Http,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            } else {
                // BLE settings
                OutlinedTextField(
                    value = settings.ipAddress,
                    onValueChange = onIpAddressChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("BLE Device Name or MAC Filter") },
                    placeholder = { Text("ESP32") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = settings.bleServiceUuid,
                    onValueChange = onBleServiceUuidChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("BLE Service UUID") },
                    placeholder = { Text("6E400001-B5A3-F393-E0A9-E50E24DCCA9E") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = settings.bleCharacteristicUuid,
                    onValueChange = onBleCharacteristicUuidChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("BLE Characteristic UUID") },
                    placeholder = { Text("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onScanBleClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBleScanning) "Scanning..." else "Scan BLE")
                    }

                    OutlinedButton(
                        onClick = onDisconnectBleClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Auto-transmit on scan",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically transmit tag payload when detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.autoTransmit,
                        onCheckedChange = onAutoTransmitToggled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target: ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (settings.transportMode == TransportMode.WIFI) "http://${settings.getFullHostWithPort()}" else settings.getFullHostWithPort(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview(name = "ESP32 Settings Card Wi-Fi Light", showBackground = true)
@Composable
private fun Esp32SettingsCardWifiPreview() {
    Esp32testTheme {
        Esp32SettingsCard(
            settings = Esp32Settings(
                ipAddress = "192.168.1.100",
                port = "80",
                endpointPath = "/api/nfc",
                httpMethod = HttpMethod.POST,
                autoTransmit = true,
                transportMode = TransportMode.WIFI
            ),
            onIpAddressChanged = {},
            onPortChanged = {},
            onEndpointPathChanged = {},
            onHttpMethodChanged = {},
            onAutoTransmitToggled = {},
            onTransportModeChanged = {},
            onBleServiceUuidChanged = {},
            onBleCharacteristicUuidChanged = {}
        )
    }
}

@Preview(name = "ESP32 Settings Card BLE Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun Esp32SettingsCardBleDarkPreview() {
    Esp32testTheme {
        Esp32SettingsCard(
            settings = Esp32Settings(
                ipAddress = "ESP32_BLE_NFC",
                autoTransmit = false,
                transportMode = TransportMode.BLE
            ),
            isBleConnected = true,
            onIpAddressChanged = {},
            onPortChanged = {},
            onEndpointPathChanged = {},
            onHttpMethodChanged = {},
            onAutoTransmitToggled = {},
            onTransportModeChanged = {},
            onBleServiceUuidChanged = {},
            onBleCharacteristicUuidChanged = {},
            onScanBleClick = {},
            onDisconnectBleClick = {}
        )
    }
}
