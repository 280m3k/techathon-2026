package com.example.esp32test.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionState
import com.example.esp32test.data.model.TransmissionStatus
import com.example.esp32test.ui.theme.Esp32testTheme

@Composable
fun TransmissionStatusBanner(
    transmissionState: TransmissionState,
    onRetryClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = transmissionState !is TransmissionState.Idle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        when (transmissionState) {
            is TransmissionState.Idle -> { }

            is TransmissionState.Transmitting -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Transmitting to ESP32...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
            }

            is TransmissionState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transmission Successful (HTTP ${transmissionState.responseCode})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = transmissionState.responseMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = onDismissClick) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss Banner",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            is TransmissionState.Failed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Transmission Failed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = transmissionState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            IconButton(onClick = onDismissClick) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss Banner",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilledTonalButton(
                                onClick = onRetryClick,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Transmission")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Transmission Banner Transmitting", showBackground = true)
@Composable
private fun TransmissionStatusBannerTransmittingPreview() {
    Esp32testTheme {
        TransmissionStatusBanner(
            transmissionState = TransmissionState.Transmitting(
                log = TransmissionLog(
                    nfcTagData = NfcTagData("04:A2:8F:12", "Test Payload"),
                    targetUrl = "http://192.168.1.100/api/nfc",
                    status = TransmissionStatus.PENDING
                )
            ),
            onRetryClick = {},
            onDismissClick = {}
        )
    }
}

@Preview(name = "Transmission Banner Success", showBackground = true)
@Composable
private fun TransmissionStatusBannerSuccessPreview() {
    Esp32testTheme {
        TransmissionStatusBanner(
            transmissionState = TransmissionState.Success(
                responseCode = 200,
                responseMessage = "{\"status\":\"success\",\"message\":\"Tag processed\"}",
                log = TransmissionLog(
                    nfcTagData = NfcTagData("04:A2:8F:12", "Test Payload"),
                    targetUrl = "http://192.168.1.100/api/nfc",
                    status = TransmissionStatus.SUCCESS
                )
            ),
            onRetryClick = {},
            onDismissClick = {}
        )
    }
}

@Preview(name = "Transmission Banner Failed Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun TransmissionStatusBannerFailedPreview() {
    Esp32testTheme {
        TransmissionStatusBanner(
            transmissionState = TransmissionState.Failed(
                errorMessage = "Connection timed out to http://192.168.1.100/api/nfc",
                responseCode = null,
                log = TransmissionLog(
                    nfcTagData = NfcTagData("04:A2:8F:12", "Test Payload"),
                    targetUrl = "http://192.168.1.100/api/nfc",
                    status = TransmissionStatus.FAILED
                )
            ),
            onRetryClick = {},
            onDismissClick = {}
        )
    }
}
