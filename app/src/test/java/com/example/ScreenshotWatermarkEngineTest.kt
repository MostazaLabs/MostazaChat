package com.example

import com.example.media.ScreenshotWatermarkEngine
import com.example.media.WatermarkPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.abs

class ScreenshotWatermarkEngineTest {

    @Test
    fun embedAndExtractPixels_recoversPayloadAndHighSpectralScore_withImperceptibleDelta() {
        val width = 256
        val height = 128
        val original = IntArray(width * height) { i ->
            val r = (i * 13) and 0xFF
            val g = (i * 29) and 0xFF
            val b = (i * 47) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val watermarked = original.copyOf()
        val payload = WatermarkPayload(
            screen = "chat_room",
            chatId = "chat-abc-123",
            capturedAtIso = "2026-11-25T15:30:00Z",
            captureId = "cap123456789"
        )

        ScreenshotWatermarkEngine.embedIntoPixels(watermarked, width, height, payload)

        // Verify imperceptibility: alpha & red unchanged, green & blue differ by at most 1/255
        for (i in original.indices) {
            val a0 = (original[i] ushr 24) and 0xFF
            val r0 = (original[i] ushr 16) and 0xFF
            val g0 = (original[i] ushr 8) and 0xFF
            val b0 = original[i] and 0xFF

            val a1 = (watermarked[i] ushr 24) and 0xFF
            val r1 = (watermarked[i] ushr 16) and 0xFF
            val g1 = (watermarked[i] ushr 8) and 0xFF
            val b1 = watermarked[i] and 0xFF

            assertEquals(a0, a1)
            assertEquals(r0, r1)
            assertTrue(abs(g0 - g1) <= 1)
            assertTrue(abs(b0 - b1) <= 1)
        }

        val (extractedJson, spectralScore) = ScreenshotWatermarkEngine.extractFromPixels(watermarked, width, height)
        assertNotNull(extractedJson)
        assertTrue(extractedJson!!.contains("\"app\":\"MostazaFake\""))
        assertTrue(extractedJson.contains("\"fake\":true"))
        assertTrue(extractedJson.contains("\"synthesized\":\"wholly_or_partially\""))
        assertTrue(extractedJson.contains("\"chatId\":\"chat-abc-123\""))
        assertTrue("Expected spectral score >= 0.99, got $spectralScore", spectralScore >= 0.99)
    }

    @Test
    fun unwatermarkedPixels_returnNullPayloadAndNearZeroSpectralScore() {
        val width = 256
        val height = 128
        val clean = IntArray(width * height) { i ->
            val r = (i * 7 + 11) and 0xFF
            val g = (i * 19 + 3) and 0xFF
            val b = (i * 31 + 5) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val (extractedJson, spectralScore) = ScreenshotWatermarkEngine.extractFromPixels(clean, width, height)
        assertNull(extractedJson)
        assertTrue("Expected near-zero spectral score, got $spectralScore", abs(spectralScore) < 0.2)
    }

    @Test
    fun croppedWatermarkedImage_stillDecodesPayloadViaShiftSearch() {
        val width = 320
        val height = 160
        val pixels = IntArray(width * height) { 0xFF203040.toInt() }
        val payload = WatermarkPayload(
            screen = "chat_room",
            chatId = "cropped-test",
            capturedAtIso = "2026-11-25T16:00:00Z",
            captureId = "crop00112233"
        )
        ScreenshotWatermarkEngine.embedIntoPixels(pixels, width, height, payload)

        // Crop with arbitrary non-aligned top-left offset (17, 11)
        val cropX = 17
        val cropY = 11
        val cropW = 220
        val cropH = 110
        val cropped = IntArray(cropW * cropH)
        for (y in 0 until cropH) {
            for (x in 0 until cropW) {
                cropped[y * cropW + x] = pixels[(y + cropY) * width + (x + cropX)]
            }
        }

        val (decoded, croppedSpectral) = ScreenshotWatermarkEngine.extractFromPixels(cropped, cropW, cropH)
        assertNotNull("Expected cropped image watermark to still decode", decoded)
        assertTrue(decoded!!.contains("\"chatId\":\"cropped-test\""))
        assertTrue("Expected cropped spectral >= 0.99, got $croppedSpectral", croppedSpectral >= 0.99)
    }

    @Test
    fun injectPngMetadataChunks_writesAndReadsSyntheticOriginAndXmp() {
        val minimalPng = buildMinimalPngBytes()
        val payload = WatermarkPayload(
            screen = "chat_room",
            chatId = "meta-chat",
            capturedAtIso = "2026-11-25T16:05:00Z",
            captureId = "meta99887766"
        )

        val stampedPng = ScreenshotWatermarkEngine.injectPngMetadataChunks(minimalPng, payload)
        val meta = ScreenshotWatermarkEngine.readPngMetadata(stampedPng)

        assertEquals("MostazaFake (MostazaLabs)", meta["Software"])
        assertEquals("FAKE_SIMULATED_ORIGIN", meta["Source"])
        assertTrue(meta["Disclaimer"]?.contains("FICTICIA") == true)
        assertTrue(meta["MostazaFake-Origin"]?.contains("fake=true") == true)
        assertTrue(meta["MostazaFake-Origin"]?.contains("synthesized=wholly_or_partially") == true)
        assertTrue(meta["Description"]?.contains("origen falso") == true)
        val xmp = meta["XML:com.adobe.xmp"].orEmpty()
        assertTrue(xmp.contains("compositeSynthetic"))
        assertTrue(xmp.contains("<mostazafake:originFake>true</mostazafake:originFake>"))
        assertTrue(xmp.contains("<mostazafake:synthesized>wholly_or_partially</mostazafake:synthesized>"))
    }

    private fun buildMinimalPngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteBuffer.allocate(13)
            .putInt(1)
            .putInt(1)
            .put(8)
            .put(6)
            .put(0)
            .put(0)
            .put(0)
            .array()
        writeChunk(out, "IHDR", ihdr)
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(typeBytes)
        out.write(data)
        out.write(ByteBuffer.allocate(4).putInt(crc).array())
    }
}
