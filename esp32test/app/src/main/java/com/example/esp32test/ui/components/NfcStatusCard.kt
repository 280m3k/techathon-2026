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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.esp32test.data.model.NfcStatus
import com.example.esp32test.ui.theme.Esp32testTheme

@Composable
fun NfcStatusCard(
    nfcStatus: NfcStatus,
    onOpenNfcSettings: () -> Unit,
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
                        imageVector = when (nfcStatus) {
                            NfcStatus.ENABLED -> Icons.Rounded.Nfc
                            NfcStatus.DISABLED -> Icons.Rounded.Nfc
                            NfcStatus.UNAVAILABLE -> Icons.Rounded.Warning
                        },
                        contentDescription = "NFC Status Icon",
                        tint = when (nfcStatus) {
                            NfcStatus.ENABLED -> MaterialTheme.colorScheme.primary
                            NfcStatus.DISABLED -> MaterialTheme.colorScheme.tertiary
                            NfcStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "NFC Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val chipContainerColor = when (nfcStatus) {
                    NfcStatus.ENABLED -> MaterialTheme.colorScheme.primaryContainer
                    NfcStatus.DISABLED -> MaterialTheme.colorScheme.tertiaryContainer
                    NfcStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
                }
                val chipContentColor = when (nfcStatus) {
                    NfcStatus.ENABLED -> MaterialTheme.colorScheme.onPrimaryContainer
                    NfcStatus.DISABLED -> MaterialTheme.colorScheme.onTertiaryContainer
                    NfcStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
                }

                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = when (nfcStatus) {
                                NfcStatus.ENABLED -> "Enabled"
                                NfcStatus.DISABLED -> "Disabled"
                                NfcStatus.UNAVAILABLE -> "Unavailable"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (nfcStatus) {
                                NfcStatus.ENABLED -> Icons.Rounded.CheckCircle
                                NfcStatus.DISABLED -> Icons.Rounded.Nfc
                                NfcStatus.UNAVAILABLE -> Icons.Rounded.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = chipContentColor
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = chipContainerColor,
                        labelColor = chipContentColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val tipText = when (nfcStatus) {
                NfcStatus.ENABLED -> "Reader Mode active. Hold an NFC tag near the back of your device to scan."
                NfcStatus.DISABLED -> "NFC hardware is currently disabled. Enable NFC in system settings to read tags."
                NfcStatus.UNAVAILABLE -> "This device does not have NFC hardware support. You can test features using Simulated Tag Scan."
            }

            Text(
                text = tipText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (nfcStatus == NfcStatus.DISABLED) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenNfcSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open NFC System Settings")
                }
            }
        }
    }
}

@Preview(name = "NFC Enabled Light", showBackground = true)
@Composable
private fun NfcStatusCardEnabledPreview() {
    Esp32testTheme {
        NfcStatusCard(
            nfcStatus = NfcStatus.ENABLED,
            onOpenNfcSettings = {}
        )
    }
}

@Preview(name = "NFC Disabled Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun NfcStatusCardDisabledPreview() {
    Esp32testTheme {
        NfcStatusCard(
            nfcStatus = NfcStatus.DISABLED,
            onOpenNfcSettings = {}
        )
    }
}

@Preview(name = "NFC Unavailable", showBackground = true)
@Composable
private fun NfcStatusCardUnavailablePreview() {
    Esp32testTheme {
        NfcStatusCard(
            nfcStatus = NfcStatus.UNAVAILABLE,
            onOpenNfcSettings = {}
        )
    }
}
