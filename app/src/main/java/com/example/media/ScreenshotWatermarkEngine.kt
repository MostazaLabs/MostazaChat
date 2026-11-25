package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32

data class WatermarkPayload(
    val app: String = "MostazaFake",
    val fakeOrigin: Boolean = true,
    val synthesized: String = "wholly_or_partially",
    val screen: String = "chat",
    val chatId: String? = null,
    val capturedAtIso: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
    val captureId: String = UUID.randomUUID().toString().take(12)
) {
    fun toCompactJson(): String {
        fun esc(v: String): String = buildString(v.length + 4) {
            for (ch in v) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
        }
        return buildString {
            append("{\"app\":\"")
            append(esc(app))
            append("\",\"fake\":")
            append(fakeOrigin)
            append(",\"synthesized\":\"")
            append(esc(synthesized))
            append("\",\"screen\":\"")
            append(esc(screen.take(32)))
            append('"')
            chatId?.takeIf { it.isNotBlank() }?.let {
                append(",\"chatId\":\"")
                append(esc(it.take(36)))
                append('"')
            }
            append(",\"ts\":\"")
            append(esc(capturedAtIso))
            append("\",\"id\":\"")
            append(esc(captureId))
            append("\"}")
        }
    }
}

data class ExtractedWatermark(
    val detected: Boolean,
    val spectralPilotScore: Double,
    val payloadJson: String?,
    val payloadApp: String?,
    val isFakeOrigin: Boolean,
    val synthesizedState: String?,
    val capturedAtIso: String?,
    val metadataFields: Map<String, String>
)

object ScreenshotWatermarkEngine {

    private val MAGIC = byteArrayOf(0x4D, 0x46, 0x4B, 0x31) // "MFK1"
    private const val TILE_W = 128
    private const val TILE_H = 32
    private const val BITS_PER_TILE = TILE_W * TILE_H // 4096 bits = 512 bytes max packet
    private const val MAX_JSON_BYTES = 380

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun buildPacket(payload: WatermarkPayload): ByteArray {
        val jsonBytes = payload.toCompactJson().toByteArray(Charsets.UTF_8)
        val trimmed = if (jsonBytes.size <= MAX_JSON_BYTES) jsonBytes else jsonBytes.copyOf(MAX_JSON_BYTES)
        val body = ByteArray(2 + trimmed.size)
        body[0] = ((trimmed.size shr 8) and 0xFF).toByte()
        body[1] = (trimmed.size and 0xFF).toByte()
        System.arraycopy(trimmed, 0, body, 2, trimmed.size)

        val crc = CRC32().apply { update(body) }.value
        val packet = ByteArray(MAGIC.size + body.size + 4)
        System.arraycopy(MAGIC, 0, packet, 0, MAGIC.size)
        System.arraycopy(body, 0, packet, MAGIC.size, body.size)
        val crcOffset = MAGIC.size + body.size
        packet[crcOffset] = ((crc shr 24) and 0xFF).toByte()
        packet[crcOffset + 1] = ((crc shr 16) and 0xFF).toByte()
        packet[crcOffset + 2] = ((crc shr 8) and 0xFF).toByte()
        packet[crcOffset + 3] = (crc and 0xFF).toByte()
        return packet
    }

    /**
     * Embeds two complementary invisible watermarks into [pixels] (ARGB ints):
     * 1. Blue-channel LSB carries tiled redundant MFK1 payload + CRC32 (max +/-1 per pixel).
     * 2. Green-channel LSB carries a 4x4 checkerboard spectral pilot signal (max +/-1 per pixel)
     *    that yields correlation ~1.0 under statistical/frequency analysis and ~0.0 on natural images.
     */
    fun embedIntoPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        payload: WatermarkPayload
    ) {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return
        val packet = buildPacket(payload)
        val packetBits = packet.size * 8

        for (y in 0 until height) {
            val ly = y % TILE_H
            val rowOffset = y * width
            val pilotY = (y shr 2) and 1
            for (x in 0 until width) {
                val lx = x % TILE_W
                val bitIdx = ly * TILE_W + lx
                val byteIdx = (bitIdx ushr 3) % packet.size
                val bitInByte = 7 - (bitIdx and 7)
                val blueBit = if (bitIdx < packetBits) {
                    (packet[byteIdx].toInt() ushr bitInByte) and 1
                } else {
                    // Deterministic pad bit for unused tail of tile
                    ((lx xor ly) and 1)
                }

                val pilotBit = ((x shr 2) and 1) xor pilotY
                val argb = pixels[rowOffset + x]
                val masked = argb and 0xFFFFFEFE.toInt()
                pixels[rowOffset + x] = masked or (pilotBit shl 8) or blueBit
            }
        }
    }

    fun extractFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int
    ): Pair<String?, Double> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null to 0.0

        val bucketSignedSum = LongArray(16)
        var total = 0L
        val onesVotes = IntArray(BITS_PER_TILE)
        val totalVotes = IntArray(BITS_PER_TILE)

        for (y in 0 until height) {
            val ly = y % TILE_H
            val rowOffset = y * width
            val pilotY = (y shr 2) and 1
            val ry = y and 3
            for (x in 0 until width) {
                val argb = pixels[rowOffset + x]
                val blueBit = argb and 1
                val greenBit = (argb ushr 8) and 1
                val expectedPilot = ((x shr 2) and 1) xor pilotY
                val bucketIdx = (ry shl 2) or (x and 3)
                if (greenBit == expectedPilot) {
                    bucketSignedSum[bucketIdx]++
                } else {
                    bucketSignedSum[bucketIdx]--
                }
                total++

                val bitIdx = ly * TILE_W + (x % TILE_W)
                onesVotes[bitIdx] += blueBit
                totalVotes[bitIdx]++
            }
        }

        val pilotScore = if (total > 0L) {
            var bestAbsSum = 0L
            for (oy in 0..3) {
                for (ox in 0..3) {
                    var sum = 0L
                    for (ry in 0..3) {
                        val flipY = (ry + oy) >= 4
                        for (rx in 0..3) {
                            val flip = flipY xor ((rx + ox) >= 4)
                            val v = bucketSignedSum[(ry shl 2) or rx]
                            sum += if (flip) -v else v
                        }
                    }
                    val absSum = kotlin.math.abs(sum)
                    if (absSum > bestAbsSum) bestAbsSum = absSum
                }
            }
            bestAbsSum.toDouble() / total.toDouble()
        } else {
            0.0
        }

        // Try exact tile alignment first; if cropped, slide small offsets up to tile dimensions
        val headerBits = 6 * 8 // MAGIC (4) + LENGTH (2)
        if (width * height < headerBits) return null to pilotScore

        val majorityBits = ByteArray(BITS_PER_TILE / 8)
        for (bitIdx in 0 until BITS_PER_TILE) {
            val votes = totalVotes[bitIdx]
            if (votes > 0 && onesVotes[bitIdx] * 2 >= votes) {
                val byteIdx = bitIdx ushr 3
                val shift = 7 - (bitIdx and 7)
                majorityBits[byteIdx] = (majorityBits[byteIdx].toInt() or (1 shl shift)).toByte()
            }
        }

        val decoded = parsePacket(majorityBits)
        if (decoded != null) return decoded to pilotScore

        // Crop-resilient fallback: test shift offsets if image was cropped from top-left
        val shiftDecoded = tryCroppedTileDecode(pixels, width, height)
        return shiftDecoded to pilotScore
    }

    private fun tryCroppedTileDecode(pixels: IntArray, width: Int, height: Int): String? {
        if (width < TILE_W && height < TILE_H) return null
        val sampleH = minOf(height, TILE_H * 3)
        val sampleW = minOf(width, TILE_W * 3)
        val headerBytes = ByteArray(6)

        for (dy in 0 until minOf(TILE_H, height)) {
            for (dx in 0 until minOf(TILE_W, width)) {
                // Quick check on first 4 magic bytes at offset (dx, dy)
                if (dx + 32 > width) continue
                var magicOk = true
                for (b in 0 until 4) {
                    var value = 0
                    for (bit in 0 until 8) {
                        val px = dx + b * 8 + bit
                        value = (value shl 1) or (pixels[dy * width + px] and 1)
                    }
                    headerBytes[b] = value.toByte()
                    if (headerBytes[b] != MAGIC[b]) {
                        magicOk = false
                        break
                    }
                }
                if (!magicOk) continue

                // Majority vote across sampled grid with shift (dx, dy)
                val ones = IntArray(BITS_PER_TILE)
                val counts = IntArray(BITS_PER_TILE)
                for (y in 0 until sampleH) {
                    val ly = ((y - dy) % TILE_H + TILE_H) % TILE_H
                    val rowOff = y * width
                    for (x in 0 until sampleW) {
                        val lx = ((x - dx) % TILE_W + TILE_W) % TILE_W
                        val idx = ly * TILE_W + lx
                        ones[idx] += (pixels[rowOff + x] and 1)
                        counts[idx]++
                    }
                }
                val candidate = ByteArray(BITS_PER_TILE / 8)
                for (i in 0 until BITS_PER_TILE) {
                    if (counts[i] > 0 && ones[i] * 2 >= counts[i]) {
                        candidate[i ushr 3] = (candidate[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
                    }
                }
                val parsed = parsePacket(candidate)
                if (parsed != null) return parsed
            }
        }
        return null
    }

    private fun parsePacket(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        for (i in MAGIC.indices) {
            if (bytes[i] != MAGIC[i]) return null
        }
        val length = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        if (length <= 0 || length > MAX_JSON_BYTES || 4 + 2 + length + 4 > bytes.size) return null

        val body = bytes.copyOfRange(4, 4 + 2 + length)
        val expectedCrc = CRC32().apply { update(body) }.value
        val crcOffset = 4 + 2 + length
        val actualCrc = ((bytes[crcOffset].toLong() and 0xFF) shl 24) or
            ((bytes[crcOffset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[crcOffset + 2].toLong() and 0xFF) shl 8) or
            (bytes[crcOffset + 3].toLong() and 0xFF)

        if (expectedCrc != actualCrc) return null
        return String(bytes, 6, length, Charsets.UTF_8)
    }

    fun embedWatermark(source: Bitmap, payload: WatermarkPayload): Bitmap {
        val width = source.width
        val height = source.height
        val mutable = if (source.config == Bitmap.Config.ARGB_8888 && source.isMutable) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }
        val pixels = IntArray(width * height)
        mutable.getPixels(pixels, 0, width, 0, 0, width, height)
        embedIntoPixels(pixels, width, height, payload)
        mutable.setPixels(pixels, 0, width, 0, 0, width, height)
        return mutable
    }

    fun extractWatermark(bitmap: Bitmap): ExtractedWatermark {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(argb.width * argb.height)
        argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
        val (jsonStr, spectralScore) = extractFromPixels(pixels, argb.width, argb.height)
        val json = jsonStr?.let { runCatching { JSONObject(it) }.getOrNull() }
        val detected = json != null || spectralScore >= 0.72
        return ExtractedWatermark(
            detected = detected,
            spectralPilotScore = spectralScore,
            payloadJson = jsonStr,
            payloadApp = json?.optString("app")?.takeIf { it.isNotBlank() },
            isFakeOrigin = json?.optBoolean("fake", false) == true,
            synthesizedState = json?.optString("synthesized")?.takeIf { it.isNotBlank() },
            capturedAtIso = json?.optString("ts")?.takeIf { it.isNotBlank() },
            metadataFields = emptyMap()
        )
    }

    fun encodePngWithSyntheticMetadata(
        bitmap: Bitmap,
        payload: WatermarkPayload
    ): ByteArray {
        val baseOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baseOut)
        return injectPngMetadataChunks(baseOut.toByteArray(), payload)
    }

    fun injectPngMetadataChunks(pngBytes: ByteArray, payload: WatermarkPayload): ByteArray {
        if (pngBytes.size < PNG_SIGNATURE.size) return pngBytes
        for (i in PNG_SIGNATURE.indices) {
            if (pngBytes[i] != PNG_SIGNATURE[i]) return pngBytes
        }

        val iendOffset = findChunkOffset(pngBytes, "IEND") ?: return pngBytes
        val out = ByteArrayOutputStream(pngBytes.size + 2048)
        out.write(pngBytes, 0, iendOffset)

        writeTextChunk(out, "Software", "MostazaFake (MostazaLabs)")
        writeTextChunk(out, "Source", "FAKE_SIMULATED_ORIGIN")
        writeTextChunk(
            out,
            "Disclaimer",
            "CONVERSACION FICTICIA NO REAL. Captura de MostazaFake; el contenido ha sido total o parcialmente sintetizado."
        )
        writeTextChunk(
            out,
            "MostazaFake-Origin",
            "app=MostazaFake;fake=true;synthesized=${payload.synthesized};watermark=MFK1;id=${payload.captureId}"
        )
        writeITXtChunk(
            out,
            keyword = "Description",
            text = "Captura ficticia generada en MostazaFake: origen falso y conversación total o parcialmente sintetizada (NO real)."
        )
        writeITXtChunk(
            out,
            keyword = "XML:com.adobe.xmp",
            text = buildXmpPacket(payload)
        )

        out.write(pngBytes, iendOffset, pngBytes.size - iendOffset)
        return out.toByteArray()
    }

    fun readPngMetadata(pngBytes: ByteArray): Map<String, String> {
        if (pngBytes.size < PNG_SIGNATURE.size) return emptyMap()
        for (i in PNG_SIGNATURE.indices) {
            if (pngBytes[i] != PNG_SIGNATURE[i]) return emptyMap()
        }
        val result = linkedMapOf<String, String>()
        var pos = PNG_SIGNATURE.size
        while (pos + 12 <= pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, pos, 4).int
            if (length < 0 || pos + 12 + length > pngBytes.size) break
            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            when (type) {
                "tEXt" -> {
                    val nul = findByte(pngBytes, dataStart, dataStart + length, 0)
                    if (nul > dataStart) {
                        val key = String(pngBytes, dataStart, nul - dataStart, Charsets.ISO_8859_1)
                        val valLen = (dataStart + length) - (nul + 1)
                        if (valLen >= 0) {
                            result[key] = String(pngBytes, nul + 1, valLen, Charsets.ISO_8859_1)
                        }
                    }
                }
                "iTXt" -> {
                    val nulKey = findByte(pngBytes, dataStart, dataStart + length, 0)
                    if (nulKey > dataStart && nulKey + 3 <= dataStart + length) {
                        val key = String(pngBytes, dataStart, nulKey - dataStart, Charsets.UTF_8)
                        val compressionFlag = pngBytes[nulKey + 1].toInt()
                        if (compressionFlag == 0) {
                            val nulLang = findByte(pngBytes, nulKey + 3, dataStart + length, 0)
                            if (nulLang >= nulKey + 3) {
                                val nulTrans = findByte(pngBytes, nulLang + 1, dataStart + length, 0)
                                if (nulTrans >= nulLang + 1) {
                                    val textStart = nulTrans + 1
                                    val textLen = (dataStart + length) - textStart
                                    if (textLen >= 0) {
                                        result[key] = String(pngBytes, textStart, textLen, Charsets.UTF_8)
                                    }
                                }
                            }
                        }
                    }
                }
                "IEND" -> break
            }
            pos += 12 + length
        }
        return result
    }

    suspend fun saveWatermarkedScreenshotToGallery(
        context: Context,
        rawBitmap: Bitmap,
        payload: WatermarkPayload
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val watermarked = embedWatermark(rawBitmap, payload)
            val pngBytes = encodePngWithSyntheticMetadata(watermarked, payload)
            val fileName = "MostazaFake_Captura_${System.currentTimeMillis()}.png"
            MediaImportHelper.savePngBytesToGallery(
                context = context,
                pngBytes = pngBytes,
                displayName = fileName
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectUriWatermark(context: Context, uri: Uri): Result<ExtractedWatermark> = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("No se pudo leer el archivo seleccionado"))
            val metadata = readPngMetadata(rawBytes)
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return@withContext Result.failure(IllegalStateException("El archivo no es una imagen válida"))
            val extracted = extractWatermark(bitmap)
            val hasMetadataOrigin = metadata.containsKey("MostazaFake-Origin") ||
                metadata["Source"] == "FAKE_SIMULATED_ORIGIN"
            Result.success(
                extracted.copy(
                    detected = extracted.detected || hasMetadataOrigin,
                    metadataFields = metadata
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildXmpPacket(payload: WatermarkPayload): String {
        val safeTs = escapeXml(payload.capturedAtIso)
        val safeScreen = escapeXml(payload.screen)
        val safeId = escapeXml(payload.captureId)
        return """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:Iptc4xmpExt="http://iptc.org/std/Iptc4xmpExt/2008-02-29/"
    xmlns:mostazafake="https://mostazalabs.app/ns/mostazafake/1.0/">
   <xmp:CreatorTool>MostazaFake (MostazaLabs)</xmp:CreatorTool>
   <xmp:CreateDate>$safeTs</xmp:CreateDate>
   <Iptc4xmpExt:DigitalSourceType>http://cv.iptc.org/newscodes/digitalsourcetype/compositeSynthetic</Iptc4xmpExt:DigitalSourceType>
   <dc:description>Captura de origen falso generada en MostazaFake. La conversación ha sido total o parcialmente sintetizada y NO representa un chat real.</dc:description>
   <mostazafake:originFake>true</mostazafake:originFake>
   <mostazafake:synthesized>wholly_or_partially</mostazafake:synthesized>
   <mostazafake:screen>$safeScreen</mostazafake:screen>
   <mostazafake:captureId>$safeId</mostazafake:captureId>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="r"?>"""
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun findChunkOffset(pngBytes: ByteArray, targetType: String): Int? {
        var pos = PNG_SIGNATURE.size
        while (pos + 12 <= pngBytes.size) {
            val length = ByteBuffer.wrap(pngBytes, pos, 4).int
            if (length < 0 || pos + 12 + length > pngBytes.size) return null
            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            if (type == targetType) return pos
            pos += 12 + length
        }
        return null
    }

    private fun findByte(bytes: ByteArray, start: Int, end: Int, target: Byte): Int {
        for (i in start until end) {
            if (bytes[i] == target) return i
        }
        return -1
    }

    private fun writeTextChunk(out: ByteArrayOutputStream, keyword: String, text: String) {
        val kBytes = keyword.toByteArray(Charsets.ISO_8859_1)
        val vBytes = text.toByteArray(Charsets.ISO_8859_1)
        val data = ByteArray(kBytes.size + 1 + vBytes.size)
        System.arraycopy(kBytes, 0, data, 0, kBytes.size)
        data[kBytes.size] = 0
        System.arraycopy(vBytes, 0, data, kBytes.size + 1, vBytes.size)
        writePngChunk(out, "tEXt", data)
    }

    private fun writeITXtChunk(out: ByteArrayOutputStream, keyword: String, text: String) {
        val kBytes = keyword.toByteArray(Charsets.UTF_8)
        val vBytes = text.toByteArray(Charsets.UTF_8)
        // keyword + 0 + compressionFlag(0) + compressionMethod(0) + langTag("") + 0 + translatedKeyword("") + 0 + text
        val data = ByteArray(kBytes.size + 5 + vBytes.size)
        System.arraycopy(kBytes, 0, data, 0, kBytes.size)
        data[kBytes.size] = 0
        data[kBytes.size + 1] = 0
        data[kBytes.size + 2] = 0
        data[kBytes.size + 3] = 0
        data[kBytes.size + 4] = 0
        System.arraycopy(vBytes, 0, data, kBytes.size + 5, vBytes.size)
        writePngChunk(out, "iTXt", data)
    }

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val lenBytes = ByteBuffer.allocate(4).putInt(data.size).array()
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value
        val crcBytes = ByteBuffer.allocate(4).putInt(crc.toInt()).array()
        out.write(lenBytes)
        out.write(typeBytes)
        out.write(data)
        out.write(crcBytes)
    }
}
