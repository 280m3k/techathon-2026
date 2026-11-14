package com.example.esp32test.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionStatus
import com.example.esp32test.ui.theme.Esp32testTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransmissionHistoryCard(
    history: List<TransmissionLog>,
    onClearHistoryClick: () -> Unit,
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Transmission History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (history.isNotEmpty()) {
                    IconButton(onClick = onClearHistoryClick) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear Transmission History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (history.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transmissions recorded",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transmitted tag data logs will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    history.forEachIndexed { index, log ->
                        TransmissionLogItem(log = log)
                        if (index < history.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransmissionLogItem(
    log: TransmissionLog,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chipContainerColor = when (log.status) {
                        TransmissionStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        TransmissionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        TransmissionStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    val chipContentColor = when (log.status) {
                        TransmissionStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                        TransmissionStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                        TransmissionStatus.PENDING -> MaterialTheme.colorScheme.onTertiaryContainer
                    }

                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = log.status.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (log.status) {
                                    TransmissionStatus.SUCCESS -> Icons.Rounded.CheckCircle
                                    TransmissionStatus.FAILED -> Icons.Rounded.ErrorOutline
                                    TransmissionStatus.PENDING -> Icons.Rounded.HourglassEmpty
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = chipContentColor
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = chipContainerColor,
                            labelColor = chipContentColor
                        )
                    )

                    val isBle = log.targetUrl.startsWith("ble://", ignoreCase = true)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (isBle) "BLE" else "Wi-Fi",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBle) Icons.Rounded.Bluetooth else Icons.Rounded.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                val formattedTime = androidx.compose.runtime.remember(log.timestamp) {
                    try {
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        sdf.format(Date(log.timestamp))
                    } catch (_: Exception) {
                        log.timestamp.toString()
                    }
                }
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Target: ${log.targetUrl}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tag UID: ${log.nfcTagData.tagUid} | Payload: \"${log.nfcTagData.payloadText.take(50)}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            log.responseMessage?.let { response ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Response: $response",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (log.status == TransmissionStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Preview(name = "Transmission History Empty", showBackground = true)
@Composable
private fun TransmissionHistoryEmptyPreview() {
    Esp32testTheme {
        TransmissionHistoryCard(
            history = emptyList(),
            onClearHistoryClick = {}
        )
    }
}

@Preview(name = "Transmission History Populated", showBackground = true)
@Composable
private fun TransmissionHistoryPopulatedPreview() {
    Esp32testTheme {
        TransmissionHistoryCard(
            history = listOf(
                TransmissionLog(
                    nfcTagData = NfcTagData("04:A2:8F:12", "Payload #1"),
                    targetUrl = "http://192.168.1.100/api/nfc",
                    status = TransmissionStatus.SUCCESS,
                    responseCode = 200,
                    responseMessage = "OK"
                ),
                TransmissionLog(
                    nfcTagData = NfcTagData("04:B3:9E:34", "Payload #2"),
                    targetUrl = "ble://ESP32_BLE/api/nfc",
                    status = TransmissionStatus.FAILED,
                    responseCode = 404,
                    responseMessage = "BLE Characteristic not found"
                )
            ),
            onClearHistoryClick = {}
        )
    }
}
