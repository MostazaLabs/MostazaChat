package com.example.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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

    private const val MAX_IMPORT_MESSAGES = 2000

    suspend fun parseFromUri(context: Context, uri: Uri): Result<ParsedWhatsAppChat> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = queryFileName(context, uri)
                val rawText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: return@withContext Result.failure(IllegalArgumentException("No se pudo abrir el archivo .txt"))

                parseText(rawText, fileName)
            } catch (e: Exception) {
                Result.failure(e)
            }
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
