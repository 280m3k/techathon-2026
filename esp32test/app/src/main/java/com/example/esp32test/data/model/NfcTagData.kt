package com.example.esp32test.data.model

import kotlinx.serialization.Serializable

/**
 * Data model representing extracted NFC tag information.
 *
 * @param tagUid Hexadecimal string representation of the NFC tag UID (e.g. "04:A2:8F:12").
 * @param payloadText Combined readable payload text extracted from NDEF records.
 * @param records List of individual NDEF record payload strings.
 * @param techList Supported technology classes of the tag (e.g. ["Ndef", "NfcA"]).
 * @param timestamp Time when the tag was scanned in milliseconds since epoch.
 */
@Serializable
data class NfcTagData(
    val tagUid: String,
    val payloadText: String,
    val records: List<String> = emptyList(),
    val techList: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
