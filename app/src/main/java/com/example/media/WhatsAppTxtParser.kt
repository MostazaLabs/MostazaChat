package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class ParsedWhatsAppMessage(
    val dateLabel: String,
    val timeLabel: String,
    val senderName: String,
    val text: String,
    val isSystem: Boolean
)

data class ParsedWhatsAppChat(
    val suggestedTitle: String,
    val participants: List<String>,
    val isLikelyGroup: Boolean,
    val messages: List<ParsedWhatsAppMessage>,
    val wasTruncated: Boolean
)

data class PendingTxtImport(
    val messages: List<ParsedWhatsAppMessage>,
    val participants: List<String>,
    val selectedMe: String?,
    val replaceExisting: Boolean = true,
    val fileCount: Int = 1
)

object WhatsAppTxtParser {

    private val ANDROID_HEADER_REGEX = Regex(
        """^(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}),?\s+(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:[ap]\.?\s*m\.?|am|pm))?)\s*[-–]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val IOS_HEADER_REGEX = Regex(
        """^\[(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}),?\s+(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:[ap]\.?\s*m\.?|am|pm))?)\]\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val INVISIBLE_CHARS_REGEX = Regex("[\u200E\u200F\u202A\u202B\u202C\u202D\u202E\uFEFF]")

    private val TELEGRAM_HEADER_REGEX = Regex(
        """^(.{1,60}?),\s+\[(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4})\s+(\d{1,2}:\d{2}(?::\d{2})?(?:\s*(?:am|pm))?)\]$""",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_COLON_LINE_REGEX = Regex(
        """^([A-Za-zÀ-ÖØ-öø-ÿ0-9 ._()-]{1,40}):\s+(.+)$"""
    )

    private const val MAX_IMPORT_MESSAGES = 2000
    private const val MAX_ZIP_TEXT_BYTES = 8 * 1024 * 1024

    suspend fun parseFromUri(context: Context, uri: Uri): Result<ParsedWhatsAppChat> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = queryFileName(context, uri)
                val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IllegalArgumentException("No se pudo abrir el archivo"))

                val isZip = fileName.lowercase(Locale.ROOT).endsWith(".zip") ||
                    mime.contains("zip", ignoreCase = true) ||
                    (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte())

                if (isZip) {
                    val (entryName, textFromZip) = extractTxtFromZipBytes(rawBytes)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("El archivo .zip no contiene ningún chat .txt compatible")
                        )
                    val titleHint = if (fileName.isNotBlank() && !entryName.contains("WhatsApp", ignoreCase = true)) {
                        fileName.removeSuffix(".zip").removeSuffix(".ZIP")
                    } else {
                        entryName
                    }
                    return@withContext parseText(textFromZip, titleHint)
                }

                val rawText = rawBytes.toString(Charsets.UTF_8)
                parseConversationText(rawText, fileName)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun parseSharedIntent(context: Context, intent: Intent?): Result<ParsedWhatsAppChat>? {
        if (intent == null) return null
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val singleUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (singleUri != null) uris.add(singleUri)
        } else {
            @Suppress("DEPRECATION")
            val multiUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!multiUris.isNullOrEmpty()) uris.addAll(multiUris)
        }

        val chatUri = uris.firstOrNull { uri ->
            val name = queryFileName(context, uri).lowercase(Locale.ROOT)
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty().lowercase(Locale.ROOT)
            name.endsWith(".txt") || name.endsWith(".zip") || mime.startsWith("text/") || mime.contains("zip")
        } ?: uris.firstOrNull()

        if (chatUri != null) {
            val parsedResult = parseFromUri(context, chatUri)
            if (parsedResult.isSuccess) return parsedResult
        }

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (extraText.isNotEmpty()) {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
            return parseConversationText(extraText, subject)
        }
        return null
    }

    private fun extractTxtFromZipBytes(zipBytes: ByteArray): Pair<String, String>? {
        return ZipInputStream(zipBytes.inputStream()).use { zis ->
            var fallbackEntry: Pair<String, String>? = null
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    if (safeName.lowercase(Locale.ROOT).endsWith(".txt")) {
                        val content = readLimitedEntryText(zis)
                        if (safeName.equals("_chat.txt", ignoreCase = true) ||
                            safeName.contains("WhatsApp", ignoreCase = true)
                        ) {
                            return safeName to content
                        }
                        if (fallbackEntry == null) {
                            fallbackEntry = safeName to content
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            fallbackEntry
        }
    }

    private fun readLimitedEntryText(zis: ZipInputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < MAX_ZIP_TEXT_BYTES) {
            val read = zis.read(buf, 0, minOf(buf.size, MAX_ZIP_TEXT_BYTES - total))
            if (read <= 0) break
            out.write(buf, 0, read)
            total += read
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    fun parseConversationText(rawText: String, fileName: String = ""): Result<ParsedWhatsAppChat> {
        val strictResult = parseText(rawText, fileName)
        if (strictResult.isSuccess) return strictResult

        val telegramResult = parseTelegramCopiedText(rawText, fileName)
        if (telegramResult != null) return Result.success(telegramResult)

        val genericResult = parseGenericColonText(rawText, fileName)
        if (genericResult != null) return Result.success(genericResult)

        return strictResult
    }

    private fun parseTelegramCopiedText(rawText: String, fileName: String): ParsedWhatsAppChat? {
        val lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val parsed = mutableListOf<ParsedWhatsAppMessage>()
        val participantCounts = linkedMapOf<String, Int>()
        var currentSender = ""
        var currentDate = ""
        var currentTime = ""
        val currentBody = StringBuilder()

        fun flush() {
            if (currentSender.isNotBlank() && currentBody.isNotBlank()) {
                val text = currentBody.toString().trim().take(2000)
                participantCounts[currentSender] = (participantCounts[currentSender] ?: 0) + 1
                parsed.add(
                    ParsedWhatsAppMessage(
                        dateLabel = currentDate.ifBlank { "Hoy" },
                        timeLabel = currentTime.ifBlank { "12:00" },
                        senderName = currentSender,
                        text = text,
                        isSystem = false
                    )
                )
            }
            currentBody.clear()
        }

        for (rawLine in lines) {
            val clean = rawLine.replace(INVISIBLE_CHARS_REGEX, "").trimEnd()
            val match = TELEGRAM_HEADER_REGEX.matchEntire(clean.trim())
            if (match != null) {
                flush()
                currentSender = match.groupValues[1].trim().take(60)
                currentDate = match.groupValues[2].trim().take(40)
                currentTime = formatShortTime(match.groupValues[3])
            } else if (currentSender.isNotBlank() && clean.isNotBlank()) {
                if (currentBody.isNotEmpty()) currentBody.append('\n')
                currentBody.append(clean.trim())
            }
        }
        flush()

        if (parsed.isEmpty()) return null
        val wasTruncated = parsed.size > MAX_IMPORT_MESSAGES
        val finalMessages = if (wasTruncated) parsed.takeLast(MAX_IMPORT_MESSAGES) else parsed
        val participants = participantCounts.entries.sortedByDescending { it.value }.map { it.key }
        val cleanTitle = deriveTitleFromFileName(fileName)
        val suggested = when {
            cleanTitle.isNotBlank() -> cleanTitle
            participants.isNotEmpty() -> participants.first()
            else -> "Chat importado"
        }.take(60)
        return ParsedWhatsAppChat(
            suggestedTitle = suggested,
            participants = participants,
            isLikelyGroup = participants.size > 2,
            messages = finalMessages,
            wasTruncated = wasTruncated
        )
    }

    private fun parseGenericColonText(rawText: String, fileName: String): ParsedWhatsAppChat? {
        val lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val parsed = mutableListOf<ParsedWhatsAppMessage>()
        val participantCounts = linkedMapOf<String, Int>()
        var minuteCounter = 0

        for (rawLine in lines) {
            val line = rawLine.replace(INVISIBLE_CHARS_REGEX, "").trim()
            if (line.isBlank()) continue
            val match = GENERIC_COLON_LINE_REGEX.matchEntire(line)
            if (match != null) {
                val sender = match.groupValues[1].trim().take(60)
                val body = match.groupValues[2].trim().take(2000)
                if (sender.isNotEmpty() && body.isNotEmpty()) {
                    val h = 12 + (minuteCounter / 60) % 12
                    val m = minuteCounter % 60
                    val timeLabel = String.format(Locale.US, "%02d:%02d", h, m)
                    minuteCounter++
                    participantCounts[sender] = (participantCounts[sender] ?: 0) + 1
                    parsed.add(
                        ParsedWhatsAppMessage(
                            dateLabel = "Hoy",
                            timeLabel = timeLabel,
                            senderName = sender,
                            text = body,
                            isSystem = false
                        )
                    )
                    continue
                }
            }
            if (parsed.isNotEmpty()) {
                val last = parsed.removeAt(parsed.lastIndex)
                parsed.add(last.copy(text = "${last.text}\n$line".take(2000)))
            }
        }

        if (parsed.isEmpty()) return null
        val wasTruncated = parsed.size > MAX_IMPORT_MESSAGES
        val finalMessages = if (wasTruncated) parsed.takeLast(MAX_IMPORT_MESSAGES) else parsed
        val participants = participantCounts.entries.sortedByDescending { it.value }.map { it.key }
        val cleanTitle = deriveTitleFromFileName(fileName)
        val suggested = when {
            cleanTitle.isNotBlank() -> cleanTitle
            participants.isNotEmpty() -> participants.first()
            else -> "Chat importado"
        }.take(60)
        return ParsedWhatsAppChat(
            suggestedTitle = suggested,
            participants = participants,
            isLikelyGroup = participants.size > 2,
            messages = finalMessages,
            wasTruncated = wasTruncated
        )
    }

    suspend fun parseFromRawResource(
        context: Context,
        @RawRes rawResId: Int,
        fileName: String
    ): Result<ParsedWhatsAppChat> = withContext(Dispatchers.IO) {
        try {
            val rawText = context.resources.openRawResource(rawResId)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseText(rawText, fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseText(rawText: String, fileName: String = ""): Result<ParsedWhatsAppChat> {
        val normalizedLines = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')

        val parsed = mutableListOf<ParsedWhatsAppMessage>()
        val participantCounts = linkedMapOf<String, Int>()

        for (rawLine in normalizedLines) {
            val line = rawLine
                .replace(INVISIBLE_CHARS_REGEX, "")
                .replace('\u00A0', ' ')
                .trimEnd()

            val match = ANDROID_HEADER_REGEX.matchEntire(line.trimStart())
                ?: IOS_HEADER_REGEX.matchEntire(line.trimStart())

            if (match != null) {
                val rawDate = match.groupValues[1].trim().take(40)
                val rawTime = formatShortTime(match.groupValues[2])
                val remainder = match.groupValues[3]
                val colonIndex = remainder.indexOf(": ")

                if (colonIndex > 0) {
                    val sender = remainder.substring(0, colonIndex).trim().take(60)
                    val body = remainder.substring(colonIndex + 2).trim().ifEmpty { " " }
                    if (sender.isNotEmpty()) {
                        participantCounts[sender] = (participantCounts[sender] ?: 0) + 1
                        parsed.add(
                            ParsedWhatsAppMessage(
                                dateLabel = rawDate,
                                timeLabel = rawTime,
                                senderName = sender,
                                text = body,
                                isSystem = false
                            )
                        )
                        continue
                    }
                }

                val sysText = remainder.trim()
                if (sysText.isNotEmpty()) {
                    parsed.add(
                        ParsedWhatsAppMessage(
                            dateLabel = rawDate,
                            timeLabel = rawTime,
                            senderName = "",
                            text = sysText,
                            isSystem = true
                        )
                    )
                }
            } else if (parsed.isNotEmpty() && line.isNotBlank()) {
                val last = parsed.removeAt(parsed.lastIndex)
                val combined = "${last.text}\n$line".take(2000)
                parsed.add(last.copy(text = combined))
            }
        }

        if (parsed.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("El archivo no tiene el formato de exportación .txt de WhatsApp")
            )
        }

        val wasTruncated = parsed.size > MAX_IMPORT_MESSAGES
        val finalMessages = if (wasTruncated) {
            parsed.takeLast(MAX_IMPORT_MESSAGES)
        } else {
            parsed
        }

        val participants = participantCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val cleanFileTitle = deriveTitleFromFileName(fileName)
        val suggestedTitle = when {
            cleanFileTitle.isNotBlank() -> cleanFileTitle
            participants.isNotEmpty() -> participants.first()
            else -> "Chat de WhatsApp"
        }.take(60)

        return Result.success(
            ParsedWhatsAppChat(
                suggestedTitle = suggestedTitle,
                participants = participants,
                isLikelyGroup = participants.size > 2,
                messages = finalMessages,
                wasTruncated = wasTruncated
            )
        )
    }

    private fun formatShortTime(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        // Strip seconds (:ss) if present while keeping optional AM/PM suffix
        val withoutSeconds = clean.replace(Regex(":(\\d{2}):\\d{2}"), ":$1")
        return withoutSeconds.ifBlank { "12:00" }.take(24)
    }

    private fun deriveTitleFromFileName(fileName: String): String {
        if (fileName.isBlank()) return ""
        var title = fileName.trim()
        if (title.lowercase(Locale.ROOT).endsWith(".txt")) {
            title = title.dropLast(4)
        }
        if (!title.contains(' ') && title.contains('_')) {
            title = title.replace('_', ' ')
        }
        val prefixes = listOf(
            "Chat de WhatsApp con ",
            "WhatsApp Chat with ",
            "Conversación de WhatsApp con "
        )
        for (prefix in prefixes) {
            if (title.startsWith(prefix, ignoreCase = true)) {
                title = title.drop(prefix.length)
                break
            }
        }
        return title.trim()
    }

    private fun queryFileName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx).orEmpty() else ""
                } else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
