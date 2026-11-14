package com.example.esp32test.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import com.example.esp32test.data.model.NfcTagData

/**
 * Helper class for managing NFC hardware state, parsing NFC intents, tags,
 * NDEF messages, NDEF records, and converting payloads to structured [NfcTagData].
 */
object NfcManager {

    private const val TAG = "NfcManager"

    /** RTD Smart Poster constant ("Sp") */
    val RTD_SMART_POSTER: ByteArray = byteArrayOf(0x53, 0x70)

    private fun safeLogD(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.d(TAG, message, throwable) else Log.d(TAG, message)
        } catch (_: Throwable) {}
    }

    private fun safeLogW(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
        } catch (_: Throwable) {}
    }

    private fun safeLogE(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
        } catch (_: Throwable) {}
    }

    private fun safeLogI(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.i(TAG, message, throwable) else Log.i(TAG, message)
        } catch (_: Throwable) {}
    }

    /**
     * Checks if the device supports NFC.
     */
    fun isNfcSupported(context: Context): Boolean {
        val adapter = getNfcAdapter(context)
        return adapter != null
    }

    /**
     * Checks if NFC is enabled on the device.
     */
    fun isNfcEnabled(context: Context): Boolean {
        val adapter = getNfcAdapter(context)
        return adapter != null && try { adapter.isEnabled } catch (_: Throwable) { false }
    }

    /**
     * Returns the default [NfcAdapter] instance for the given context.
     */
    fun getNfcAdapter(context: Context): NfcAdapter? {
        return try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (e: Throwable) {
            safeLogW("Error getting NfcAdapter", e)
            null
        }
    }

    /**
     * Extracts an integer value from a text string.
     * Parses direct integer strings, hex strings starting with "0x", or the primary embedded integer.
     * Fallbacks to the string's hash code or 0 if no integer representation is found.
     */
    fun extractIntegerFromText(text: String): Int {
        if (text.isBlank()) return 0
        val trimmed = text.trim()
        trimmed.toIntOrNull()?.let { return it }
        if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.drop(2).toIntOrNull(16)?.let { return it }
        }
        val numbers = Regex("""-?\d+""").findAll(trimmed).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.isNotEmpty()) {
            return numbers.last()
        }
        return text.hashCode()
    }

    /**
     * Converts a byte array (e.g. Tag UID) into a formatted hexadecimal string.
     *
     * @param bytes Byte array to format.
     * @param separator Delimiter between hex bytes (default is ":").
     * @return Hexadecimal string, e.g. "04:A2:8F:12".
     */
    fun bytesToHexString(bytes: ByteArray?, separator: String = ":"): String {
        if (bytes == null || bytes.isEmpty()) return ""
        return bytes.joinToString(separator) { "%02X".format(it) }
    }

    /**
     * Extracts a [Tag] object from an incoming NFC [Intent].
     */
    fun getTagFromIntent(intent: Intent): Tag? {
        val tag = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
            }
        } catch (e: Throwable) {
            safeLogD("Error extracting Tag from intent", e)
            null
        }
        safeLogD("getTagFromIntent: tag found = ${tag != null}, id = ${bytesToHexString(tag?.id)}")
        return tag
    }

    /**
     * Extracts an array of [NdefMessage] objects from an incoming NFC [Intent].
     */
    fun getNdefMessagesFromIntent(intent: Intent): Array<NdefMessage>? {
        val rawMessages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }
        } catch (e: Throwable) {
            safeLogD("Error extracting EXTRA_NDEF_MESSAGES from intent", e)
            null
        }

        if (rawMessages == null) {
            safeLogD("getNdefMessagesFromIntent: EXTRA_NDEF_MESSAGES is null")
            return null
        }

        val messages = rawMessages.filterIsInstance<NdefMessage>().toTypedArray()
        safeLogD("getNdefMessagesFromIntent: extracted ${messages.size} NdefMessage(s)")
        return if (messages.isNotEmpty()) messages else null
    }

    /**
     * Parses an NFC [Intent] and returns an [NfcTagData] object if valid tag data is present.
     */
    fun parseTagFromIntent(intent: Intent): NfcTagData? {
        safeLogD("parseTagFromIntent: action = ${intent.action}")
        val tag = getTagFromIntent(intent) ?: return null
        val ndefMessages = getNdefMessagesFromIntent(intent)
        return parseTag(tag, ndefMessages)
    }

    /**
     * Parses a [Tag] object and optional [NdefMessage]s into an [NfcTagData] instance.
     *
     * @param tag The scanned NFC tag.
     * @param ndefMessages Optional pre-parsed NDEF messages from intent or reader mode.
     * @return Parsed [NfcTagData] containing UID, payload text, records, and tech list.
     */
    fun parseTag(tag: Tag, ndefMessages: Array<NdefMessage>? = null): NfcTagData {
        val tagUid = bytesToHexString(tag.id)
        val techList = try {
            tag.techList?.map { it.substringAfterLast('.') } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        safeLogD("parseTag: tagUid = $tagUid, techList = $techList, provided ndefMessages = ${ndefMessages?.size}")

        val messages = ndefMessages ?: tryReadNdefFromTag(tag)
        val rawRecordTexts = mutableListOf<String>()
        val detailedRecordTexts = mutableListOf<String>()

        if (messages != null && messages.isNotEmpty()) {
            messages.forEach { message ->
                val records = try { message.records } catch (_: Throwable) { emptyArray() }
                records.forEach { record ->
                    val raw = parseNdefRecordRaw(record)
                    val detailed = parseNdefRecord(record)
                    if (raw.isNotBlank()) rawRecordTexts.add(raw)
                    if (detailed.isNotBlank()) detailedRecordTexts.add(detailed)
                }
            }
        } else {
            safeLogW("parseTag: No NdefMessages found from intent or tag.id = $tagUid")
        }

        var payloadText = rawRecordTexts.joinToString(separator = "\n")
        if (payloadText.isEmpty()) {
            payloadText = "NFC Tag UID: $tagUid (Tech: ${techList.joinToString()})"
            safeLogD("parseTag: payloadText was empty, assigned fallback: $payloadText")
        }

        val tagData = NfcTagData(
            tagUid = tagUid,
            payloadText = payloadText,
            records = detailedRecordTexts.ifEmpty { rawRecordTexts },
            techList = techList
        )
        safeLogI("parseTag result: uid=$tagUid, recordsCount=${detailedRecordTexts.size}, payloadTextLength=${payloadText.length}, payload='$payloadText'")
        detailedRecordTexts.forEach { rec ->
            safeLogI("  Record Detail: $rec")
        }
        return tagData
    }

    /**
     * Parses all records in an [NdefMessage] into a list of readable strings with record type descriptions.
     */
    fun parseNdefMessage(message: NdefMessage): List<String> {
        val recordStrings = mutableListOf<String>()
        val records = try {
            message.records
        } catch (e: Throwable) {
            safeLogE("Error getting records from NdefMessage", e)
            emptyArray()
        }
        safeLogD("parseNdefMessage: containing ${records.size} record(s)")
        for ((index, record) in records.withIndex()) {
            safeLogD("parseNdefMessage: record #$index tnf=${try { record.tnf } catch (_: Throwable) { -1 }}")
            val parsed = parseNdefRecord(record)
            if (parsed.isNotEmpty()) {
                recordStrings.add(parsed)
            }
        }
        return recordStrings
    }

    /**
     * Parses multiple [NdefMessage] objects into a single combined string payload.
     */
    fun parseNdefMessages(messages: Array<NdefMessage>): String {
        val recordStrings = mutableListOf<String>()
        for (message in messages) {
            val records = try { message.records } catch (_: Throwable) { emptyArray() }
            for (record in records) {
                val raw = parseNdefRecordRaw(record)
                if (raw.isNotBlank()) recordStrings.add(raw)
            }
        }
        return recordStrings.joinToString("\n")
    }

    /**
     * Parses a single [NdefRecord] into a clean raw payload string without record type prefix.
     */
    fun parseNdefRecordRaw(record: NdefRecord): String {
        val tnf = try { record.tnf } catch (_: Throwable) { -1 }
        val type = try { record.type } catch (_: Throwable) { null } ?: ByteArray(0)
        val payload = try { record.payload } catch (_: Throwable) { null } ?: ByteArray(0)
        val typeAscii = try { String(type, Charsets.US_ASCII) } catch (_: Throwable) { "" }

        val content = when (tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                when {
                    type.contentEquals(NdefRecord.RTD_TEXT) -> parseTextRecord(record)
                    type.contentEquals(NdefRecord.RTD_URI) -> parseUriRecord(record)
                    type.contentEquals(NdefRecord.RTD_SMART_POSTER) || type.contentEquals(RTD_SMART_POSTER) -> parseSmartPosterRecord(record)
                    else -> {
                        val text = tryDecodePayloadToText(payload, typeAscii)
                        if (text.isNotBlank()) text else parseUriPayload(payload)
                    }
                }
            }
            NdefRecord.TNF_ABSOLUTE_URI -> {
                val uri = try { record.toUri() } catch (_: Throwable) { null }
                if (uri != null && !uri.toString().isBlank()) uri.toString() else parseUriPayload(payload)
            }
            NdefRecord.TNF_MIME_MEDIA -> {
                tryDecodePayloadToText(payload, typeAscii)
            }
            NdefRecord.TNF_EXTERNAL_TYPE, NdefRecord.TNF_UNKNOWN, NdefRecord.TNF_UNCHANGED -> {
                val text = tryDecodePayloadToText(payload, typeAscii)
                if (text.isNotBlank()) text else parseUriPayload(payload)
            }
            else -> {
                val text = tryDecodePayloadToText(payload, typeAscii)
                if (text.isNotBlank()) text else bytesToHexString(payload, " ")
            }
        }

        val finalContent = if (content.isNotBlank()) content else bytesToHexString(payload, " ")
        return finalContent
    }

    /**
     * Parses a single [NdefRecord] into a human-readable String including record type description.
     */
    fun parseNdefRecord(record: NdefRecord): String {
        val tnf = try { record.tnf } catch (_: Throwable) { -1 }
        val type = try { record.type } catch (_: Throwable) { null } ?: ByteArray(0)
        val payload = try { record.payload } catch (_: Throwable) { null } ?: ByteArray(0)
        val typeHex = bytesToHexString(type, "")
        val typeAscii = try { String(type, Charsets.US_ASCII) } catch (_: Throwable) { "" }
        safeLogD("parseNdefRecord: tnf=$tnf, typeHex=$typeHex, typeAscii=$typeAscii, payloadSize=${payload.size}")

        val (recordTypeDesc, content) = try {
            when (tnf) {
                NdefRecord.TNF_WELL_KNOWN -> {
                    when {
                        type.contentEquals(NdefRecord.RTD_TEXT) -> {
                            val text = parseTextRecord(record)
                            val lang = try {
                                if (payload.isNotEmpty()) {
                                    val status = payload[0].toInt()
                                    val langLen = status and 0x3F
                                    if (payload.size >= 1 + langLen) {
                                        String(payload, 1, langLen, Charsets.US_ASCII)
                                    } else ""
                                } else ""
                            } catch (_: Throwable) { "" }
                            val typeDesc = if (lang.isNotBlank()) "Text ($lang)" else "Text"
                            typeDesc to text
                        }
                        type.contentEquals(NdefRecord.RTD_URI) -> {
                            "URI" to parseUriRecord(record)
                        }
                        type.contentEquals(NdefRecord.RTD_SMART_POSTER) || type.contentEquals(RTD_SMART_POSTER) -> {
                            "Smart Poster" to parseSmartPosterRecord(record)
                        }
                        else -> {
                            val typeName = if (typeAscii.isNotBlank()) typeAscii else typeHex
                            "Well-Known ($typeName)" to tryDecodePayloadToText(payload, typeAscii)
                        }
                    }
                }
                NdefRecord.TNF_ABSOLUTE_URI -> {
                    val uri = try { record.toUri() } catch (_: Throwable) { null }
                    val uriStr = if (uri != null && !uri.toString().isBlank()) uri.toString() else parseUriPayload(payload)
                    "Absolute URI" to uriStr
                }
                NdefRecord.TNF_MIME_MEDIA -> {
                    val mimeType = if (typeAscii.isNotBlank()) typeAscii else "application/octet-stream"
                    "MIME ($mimeType)" to tryDecodePayloadToText(payload, mimeType)
                }
                NdefRecord.TNF_EXTERNAL_TYPE -> {
                    val extType = if (typeAscii.isNotBlank()) typeAscii else typeHex
                    "External ($extType)" to tryDecodePayloadToText(payload, typeAscii)
                }
                NdefRecord.TNF_UNKNOWN -> {
                    "Unknown" to tryDecodePayloadToText(payload, typeAscii)
                }
                NdefRecord.TNF_UNCHANGED -> {
                    "Unchanged" to tryDecodePayloadToText(payload, typeAscii)
                }
                else -> {
                    "TNF $tnf" to tryDecodePayloadToText(payload, typeAscii)
                }
            }
        } catch (e: Throwable) {
            safeLogE("Error parsing NdefRecord", e)
            "Error" to bytesToHexString(payload, " ")
        }

        val finalContent = if (content.isNotBlank()) content else bytesToHexString(payload, " ")
        val formattedResult = "[$recordTypeDesc] $finalContent"
        safeLogI("parseNdefRecord result: type='$recordTypeDesc', content='$finalContent'")
        return formattedResult
    }

    /**
     * Attempts to decode arbitrary payload bytes into a readable string using text MIME types or encodings.
     */
    fun tryDecodePayloadToText(payload: ByteArray, typeAscii: String = ""): String {
        if (payload.isEmpty()) return ""

        val isTextType = typeAscii.startsWith("text/", ignoreCase = true) ||
                typeAscii.contains("json", ignoreCase = true) ||
                typeAscii.contains("xml", ignoreCase = true) ||
                typeAscii.contains("csv", ignoreCase = true) ||
                typeAscii.contains("plain", ignoreCase = true)

        val utf8Str = try {
            String(payload, Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }

        if (utf8Str.isNotBlank() && (isTextType || isPrintableText(utf8Str))) {
            return utf8Str
        }

        val isoStr = try {
            String(payload, Charsets.ISO_8859_1)
        } catch (_: Throwable) {
            ""
        }

        if (isoStr.isNotBlank() && (isTextType || isPrintableText(isoStr))) {
            return isoStr
        }

        if (utf8Str.isNotBlank()) {
            return utf8Str
        }

        return bytesToHexString(payload, " ")
    }

    private fun isPrintableText(str: String): Boolean {
        if (str.isBlank()) return false
        val printableCount = str.count { it in '\u0020'..'\u007E' || it == '\n' || it == '\r' || it == '\t' || it > '\u007F' }
        return (printableCount.toFloat() / str.length) >= 0.7f
    }

    /**
     * Parses a Smart Poster (`RTD_SMART_POSTER`) NDEF record whose payload contains an inner NDEF message.
     */
    fun parseSmartPosterRecord(record: NdefRecord): String {
        val payload = try { record.payload } catch (_: Throwable) { null } ?: return ""
        return parseSmartPosterPayload(payload)
    }

    /**
     * Parses raw Smart Poster payload bytes (which contains inner NDEF records or URI payload).
     */
    fun parseSmartPosterPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""

        return try {
            val innerMessage = NdefMessage(payload)
            val innerRecords = parseNdefMessage(innerMessage)
            if (innerRecords.isNotEmpty()) {
                innerRecords.joinToString(separator = "\n")
            } else {
                parseUriPayload(payload)
            }
        } catch (e: Throwable) {
            safeLogW("Failed to parse Smart Poster payload as NdefMessage, falling back to uri payload", e)
            parseUriPayload(payload)
        }
    }

    /**
     * Parses a raw NDEF Text record payload according to the NFC Forum specification.
     */
    fun parseTextPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""

        val statusByte = payload[0].toInt()
        val isUtf8 = (statusByte and 0x80) == 0
        val languageCodeLength = statusByte and 0x3F
        val charset = if (isUtf8) Charsets.UTF_8 else Charsets.UTF_16

        val textStartIndex = 1 + languageCodeLength
        if (textStartIndex > payload.size) {
            safeLogW("parseTextPayload: textStartIndex ($textStartIndex) > payload.size (${payload.size})")
            return String(payload, Charsets.UTF_8)
        }

        return String(payload, textStartIndex, payload.size - textStartIndex, charset)
    }

    /**
     * Parses an NDEF Text record according to the NFC Forum specification.
     */
    fun parseTextRecord(record: NdefRecord): String {
        val payload = try {
            record.payload
        } catch (_: Throwable) {
            ByteArray(0)
        }
        val parsed = parseTextPayload(payload)
        if (parsed.isBlank() && payload.isNotEmpty()) {
            return String(payload, Charsets.UTF_8)
        }
        return parsed
    }

    /**
     * Parses a raw NDEF URI record payload according to the NFC Forum specification.
     * Supports URI prefix codes (0x00 to 0x23). Remaining bytes `payload.sliceArray(1 until payload.size)` are the URI body.
     */
    fun parseUriPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""

        val prefixByte = payload[0].toInt()
        return if (prefixByte in 0x00..0x23) {
            val prefix = URI_PREFIX_MAP[prefixByte] ?: ""
            val uriBody = if (payload.size > 1) {
                String(payload.sliceArray(1 until payload.size), Charsets.UTF_8)
            } else ""
            prefix + uriBody
        } else {
            val rawStr = try {
                String(payload, Charsets.UTF_8)
            } catch (_: Throwable) {
                bytesToHexString(payload, " ")
            }
            if (rawStr.isNotBlank()) rawStr else bytesToHexString(payload, " ")
        }
    }

    /**
     * Parses an NDEF URI record according to the NFC Forum specification.
     */
    fun parseUriRecord(record: NdefRecord): String {
        val uri = try {
            record.toUri()
        } catch (e: Throwable) {
            safeLogD("parseUriRecord: record.toUri() threw exception", e)
            null
        }
        if (uri != null) {
            val uriStr = uri.toString()
            if (uriStr.isNotBlank()) {
                return uriStr
            }
        }
        val payload = try {
            record.payload
        } catch (_: Throwable) {
            ByteArray(0)
        }
        return parseUriPayload(payload)
    }

    /**
     * Enables NFC Reader Mode on an [Activity].
     */
    fun enableReaderMode(
        activity: Activity,
        onTagDiscovered: (Tag) -> Unit,
        flags: Int = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE
                // NOTE: do NOT add FLAG_READER_SKIP_NDEF_CHECK - it stops Android from adding the
                // Ndef tech to the Tag, so the tag's NDEF text/URI records could never be read.
    ) {
        val adapter = getNfcAdapter(activity)
        if (adapter == null) {
            safeLogW("enableReaderMode: NFC adapter is null")
            return
        }
        val extras = Bundle()
        extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        try {
            adapter.enableReaderMode(activity, { tag ->
                safeLogD("enableReaderMode: tag discovered callback triggered")
                onTagDiscovered(tag)
            }, flags, extras)
            safeLogD("enableReaderMode enabled successfully")
        } catch (e: Throwable) {
            safeLogE("Error enabling reader mode", e)
        }
    }

    /**
     * Disables NFC Reader Mode on an [Activity].
     */
    fun disableReaderMode(activity: Activity) {
        val adapter = getNfcAdapter(activity)
        if (adapter != null) {
            try {
                adapter.disableReaderMode(activity)
                safeLogD("disableReaderMode disabled successfully")
            } catch (e: Throwable) {
                safeLogE("Error disabling reader mode", e)
            }
        }
    }

    private fun tryReadNdefFromTag(tag: Tag): Array<NdefMessage>? {
        val ndef = try {
            Ndef.get(tag)
        } catch (e: Throwable) {
            safeLogD("tryReadNdefFromTag: Ndef.get(tag) threw exception", e)
            null
        }
        if (ndef == null) {
            safeLogD("tryReadNdefFromTag: Ndef tech not found on tag")
            return null
        }

        return try {
            ndef.connect()
            val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
            ndef.close()
            if (msg != null) {
                safeLogD("tryReadNdefFromTag: successfully read NdefMessage from tag")
                arrayOf(msg)
            } else {
                safeLogW("tryReadNdefFromTag: NdefMessage and cachedNdefMessage are both null")
                null
            }
        } catch (e: Throwable) {
            safeLogW("Could not read NDEF from tag", e)
            try {
                ndef.close()
            } catch (_: Throwable) {}
            null
        }
    }

    private val URI_PREFIX_MAP = mapOf(
        0x00 to "",
        0x01 to "http://www.",
        0x02 to "https://www.",
        0x03 to "http://",
        0x04 to "https://",
        0x05 to "tel:",
        0x06 to "mailto:",
        0x07 to "ftp://anonymous:anonymous@",
        0x08 to "ftp://ftp.",
        0x09 to "ftps://",
        0x0A to "sftp://",
        0x0B to "smb://",
        0x0C to "nfs://",
        0x0D to "ftp://",
        0x0E to "dav://",
        0x0F to "news:",
        0x10 to "telnet://",
        0x11 to "imap:",
        0x12 to "rtsp://",
        0x13 to "urn:",
        0x14 to "pop:",
        0x15 to "sip:",
        0x16 to "sips:",
        0x17 to "tftp:",
        0x18 to "btspp://",
        0x19 to "btl2cap://",
        0x1A to "btgoep://",
        0x1B to "tcpobex://",
        0x1C to "irdaobex://",
        0x1D to "file://",
        0x1E to "urn:epc:id:",
        0x1F to "urn:epc:tag:",
        0x20 to "urn:epc:pat:",
        0x21 to "urn:epc:raw:",
        0x22 to "urn:epc:",
        0x23 to "urn:nfc:"
    )
}
