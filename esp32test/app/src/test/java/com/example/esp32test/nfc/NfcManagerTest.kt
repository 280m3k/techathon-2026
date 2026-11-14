package com.example.esp32test.nfc

import com.example.esp32test.data.model.NfcTagData
import com.example.esp32test.data.model.TransmissionLog
import com.example.esp32test.data.model.TransmissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcManagerTest {

    @Test
    fun testBytesToHexString() {
        val bytes = byteArrayOf(0x04.toByte(), 0xA2.toByte(), 0x8F.toByte(), 0x12.toByte())
        val hexDefault = NfcManager.bytesToHexString(bytes)
        assertEquals("04:A2:8F:12", hexDefault)

        val hexDash = NfcManager.bytesToHexString(bytes, separator = "-")
        assertEquals("04-A2-8F-12", hexDash)

        val hexEmpty = NfcManager.bytesToHexString(byteArrayOf())
        assertEquals("", hexEmpty)
    }

    @Test
    fun testParseTextPayloadUtf8() {
        // Status byte: 0x02 (UTF-8, 2-byte language code "en")
        val languageCode = "en".toByteArray(Charsets.US_ASCII)
        val textBytes = "ESP32 NFC Test Payload".toByteArray(Charsets.UTF_8)
        val statusByte = (languageCode.size and 0x3F).toByte()

        val payload = byteArrayOf(statusByte) + languageCode + textBytes

        val parsedText = NfcManager.parseTextPayload(payload)
        assertEquals("ESP32 NFC Test Payload", parsedText)
    }

    @Test
    fun testParseTextPayloadSpanish() {
        val languageCode = "es".toByteArray(Charsets.US_ASCII)
        val textBytes = "Hola Mundo NFC".toByteArray(Charsets.UTF_8)
        val statusByte = (languageCode.size and 0x3F).toByte()

        val payload = byteArrayOf(statusByte) + languageCode + textBytes

        val parsedText = NfcManager.parseTextPayload(payload)
        assertEquals("Hola Mundo NFC", parsedText)
    }

    @Test
    fun testParseTextPayloadEmpty() {
        assertEquals("", NfcManager.parseTextPayload(ByteArray(0)))
    }

    @Test
    fun testTryDecodePayloadToTextMimePlain() {
        val payload = "Custom plain text message string".toByteArray(Charsets.UTF_8)
        val decoded = NfcManager.tryDecodePayloadToText(payload, "text/plain")
        assertEquals("Custom plain text message string", decoded)
    }

    @Test
    fun testTryDecodePayloadToTextJson() {
        val jsonPayload = "{\"device\":\"esp32\",\"status\":\"ok\"}".toByteArray(Charsets.UTF_8)
        val decoded = NfcManager.tryDecodePayloadToText(jsonPayload, "application/json")
        assertEquals("{\"device\":\"esp32\",\"status\":\"ok\"}", decoded)
    }

    @Test
    fun testTryDecodePayloadToTextArbitraryBinary() {
        val binaryPayload = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFE.toByte())
        val decoded = NfcManager.tryDecodePayloadToText(binaryPayload, "application/octet-stream")
        // Should fall back to hex or non-blank representation
        assertTrue(decoded.isNotBlank())
    }

    @Test
    fun testParseUriPayloadWithPrefix() {
        // Prefix byte 0x04 -> "https://"
        val uriBody = "esp32.local/data".toByteArray(Charsets.UTF_8)
        val payload = byteArrayOf(0x04.toByte()) + uriBody

        val parsedUri = NfcManager.parseUriPayload(payload)
        assertEquals("https://esp32.local/data", parsedUri)
    }

    @Test
    fun testParseUriPayloadWithYouTubePrefix() {
        // Prefix byte 0x02 -> "https://www."
        val uriBody = "youtube.com/watch?v=dQw4w9WgXcQ".toByteArray(Charsets.UTF_8)
        val payload = byteArrayOf(0x02.toByte()) + uriBody

        val parsedUri = NfcManager.parseUriPayload(payload)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", parsedUri)
    }

    @Test
    fun testParseUriPayloadWithRawYouTubeUrlBytes() {
        // Raw URL bytes without prefix byte (e.g. starting with 'h' of "https://...")
        val rawUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val payload = rawUrl.toByteArray(Charsets.UTF_8)

        val parsedUri = NfcManager.parseUriPayload(payload)
        assertEquals(rawUrl, parsedUri)
    }

    @Test
    fun testParseUriPayloadWithMultiplePrefixes() {
        // 0x01 -> "http://www."
        val payload01 = byteArrayOf(0x01.toByte()) + "example.com".toByteArray(Charsets.UTF_8)
        assertEquals("http://www.example.com", NfcManager.parseUriPayload(payload01))

        // 0x03 -> "http://"
        val payload03 = byteArrayOf(0x03.toByte()) + "example.com".toByteArray(Charsets.UTF_8)
        assertEquals("http://example.com", NfcManager.parseUriPayload(payload03))

        // 0x04 -> "https://"
        val payload04 = byteArrayOf(0x04.toByte()) + "example.com".toByteArray(Charsets.UTF_8)
        assertEquals("https://example.com", NfcManager.parseUriPayload(payload04))
    }

    @Test
    fun testParseSmartPosterPayloadWithUriBytes() {
        // Test smart poster fallback with URI payload bytes starting with 0x02
        val uriPayload = byteArrayOf(0x02.toByte()) + "youtube.com/watch?v=dQw4w9WgXcQ".toByteArray(Charsets.UTF_8)
        val parsed = NfcManager.parseSmartPosterPayload(uriPayload)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", parsed)
    }

    @Test
    fun testNfcTagDataModel() {
        val tagData = NfcTagData(
            tagUid = "04:A2:8F:12",
            payloadText = "Test Data Payload",
            records = listOf("Test Data Payload"),
            techList = listOf("Ndef", "NfcA")
        )

        assertEquals("04:A2:8F:12", tagData.tagUid)
        assertEquals("Test Data Payload", tagData.payloadText)
        assertEquals(1, tagData.records.size)
        assertEquals(2, tagData.techList.size)
        assertTrue(tagData.timestamp > 0)
    }

    @Test
    fun testTransmissionLogModel() {
        val tagData = NfcTagData(
            tagUid = "04:A2:8F:12",
            payloadText = "Payload to transmit"
        )
        val log = TransmissionLog(
            nfcTagData = tagData,
            targetUrl = "http://192.168.1.100/api/nfc",
            status = TransmissionStatus.SUCCESS,
            responseCode = 200,
            responseMessage = "OK"
        )

        assertNotNull(log.logId)
        assertEquals("04:A2:8F:12", log.nfcTagData.tagUid)
        assertEquals("http://192.168.1.100/api/nfc", log.targetUrl)
        assertEquals(TransmissionStatus.SUCCESS, log.status)
        assertEquals(200, log.responseCode)
        assertEquals("OK", log.responseMessage)
    }

    @Test
    fun testExtractIntegerFromText() {
        assertEquals(42, NfcManager.extractIntegerFromText("42"))
        assertEquals(-99, NfcManager.extractIntegerFromText("-99"))
        assertEquals(26, NfcManager.extractIntegerFromText("0x1A"))
        assertEquals(123, NfcManager.extractIntegerFromText("ESP32 Tag #123"))
        assertEquals(0, NfcManager.extractIntegerFromText(""))
        assertEquals("nonumeric".hashCode(), NfcManager.extractIntegerFromText("nonumeric"))
    }
}
