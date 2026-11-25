package com.example.llm

import com.example.data.model.FakeChat
import com.example.data.model.FakeMessage
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

data class LlmTurnMessage(
    val role: String,
    val content: String
)

data class GeneratedLlmReply(
    val text: String,
    val speakerName: String
)

object ExternalLlmClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val MAX_RESPONSE_BYTES = 1024 * 1024 // 1 MB

    fun validateEndpointUrl(rawUrl: String, allowHttp: Boolean): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("La URL del endpoint LLM no puede estar vacía"))
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) {
            return Result.failure(IllegalArgumentException("Esquema de URL no permitido"))
        }
        val parsed = try {
            URI(trimmed)
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("Formato de URL inválido"))
        }
        val scheme = parsed.scheme?.lowercase(Locale.ROOT).orEmpty()
        val allowedSchemes = if (allowHttp) setOf("https", "http") else setOf("https")
        if (scheme !in allowedSchemes) {
            val msg = if (allowHttp) {
                "Usa una URL https:// o http:// válida"
            } else {
                "Este proveedor requiere conexión cifrada https://"
            }
            return Result.failure(IllegalArgumentException(msg))
        }
        if (parsed.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Falta el dominio o IP en la URL"))
        }
        if (!parsed.rawUserInfo.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("No incluyas usuario/contraseña dentro de la URL"))
        }
        return Result.success(trimmed.take(300))
    }

    fun buildChatTurns(
        chat: FakeChat,
        chatConfig: ChatAiConfig,
        recentMessages: List<FakeMessage>,
        myDefaultName: String,
        preferredGroupSpeaker: String?,
        sensitiveFilterEnabled: Boolean,
        contextLimit: Int
    ): List<LlmTurnMessage> {
        val members = if (chat.groupChat) {
            chat.resolvedGroupMembers(myDefaultName).filter { !it.isMe }.map { it.name }.distinct()
        } else {
            emptyList()
        }
        val activeSpeaker = preferredGroupSpeaker?.trim()?.takeIf { it.isNotEmpty() }
            ?: members.firstOrNull()
            ?: chat.contactName

        val systemPrompt = buildString {
            append("Estás participando en una conversación de mensajería ficticia / roleplay dentro de MostazaFake. ")
            if (chat.groupChat) {
                append("Es un grupo llamado \"${chat.contactName}\". ")
                if (members.isNotEmpty()) {
                    append("Participantes: ${members.joinToString(", ")}. ")
                }
                append("Responde únicamente como el participante \"$activeSpeaker\". ")
            } else {
                append("Tu personaje es \"${chat.contactName}\"")
                if (chat.statusText.isNotBlank()) {
                    append(" (estado/bio: ${chat.statusText})")
                }
                append(". ")
            }
            if (chatConfig.personaPrompt.isNotBlank()) {
                append("Personalidad y contexto del personaje: ${chatConfig.personaPrompt.trim()}. ")
            }
            append("Escribe mensajes naturales y directos estilo WhatsApp/chat móvil, sin prefijos como '${activeSpeaker}:' ni explicaciones fuera de personaje. ")
            if (sensitiveFilterEnabled) {
                append("Mantén un tono apto para todos los públicos, sin contenido sexual explícito, violencia gráfica ni autolesión.")
            }
        }

        val turns = mutableListOf(LlmTurnMessage(role = "system", content = systemPrompt))
        val slice = recentMessages
            .filter { it.parsedMessageType != MessageType.SYSTEM }
            .takeLast(contextLimit.coerceIn(4, 30))

        for (msg in slice) {
            val contentText = when (msg.parsedMessageType) {
                MessageType.IMAGE -> if (msg.text.isNotBlank()) "[Imagen] ${msg.text}" else "[Imagen enviada]"
                MessageType.VIDEO -> if (msg.text.isNotBlank()) "[Vídeo] ${msg.text}" else "[Vídeo enviado]"
                MessageType.VOICE -> "[Nota de voz ${msg.voiceDuration ?: ""}] ${msg.text}".trim()
                MessageType.CALL_EVENT -> "[Evento de llamada: ${msg.text}]"
                else -> msg.text
            }.trim()
            if (contentText.isEmpty()) continue

            when (msg.parsedSenderRole) {
                SenderRole.ME -> turns += LlmTurnMessage(role = "user", content = contentText)
                SenderRole.OTHER -> {
                    val labeled = if (chat.groupChat && msg.senderName.isNotBlank()) {
                        "${msg.senderName}: $contentText"
                    } else {
                        contentText
                    }
                    turns += LlmTurnMessage(role = "assistant", content = labeled)
                }
                SenderRole.SYSTEM -> Unit
            }
        }

        if (turns.lastOrNull()?.role != "user") {
            val cue = if (chat.groupChat) {
                "Continúa la conversación enviando el siguiente mensaje breve como $activeSpeaker."
            } else {
                "Continúa la conversación enviando el siguiente mensaje breve de ${chat.contactName}."
            }
            turns += LlmTurnMessage(role = "user", content = cue)
        }
        return turns
    }

    fun buildRequestPayloadJson(
        model: String,
        turns: List<LlmTurnMessage>,
        temperature: Float,
        maxTokens: Int = 420
    ): String {
        val clampedTemp = temperature.coerceIn(0.1f, 1.4f)
        val clampedTokens = maxTokens.coerceIn(8, 2048)
        return buildString(512) {
            append("{\"model\":\"")
            append(jsonEscape(model.trim()))
            append("\",\"temperature\":")
            append(String.format(Locale.US, "%.2f", clampedTemp))
            append(",\"max_tokens\":")
            append(clampedTokens)
            append(",\"stream\":false,\"messages\":[")
            turns.forEachIndexed { idx, turn ->
                if (idx > 0) append(',')
                append("{\"role\":\"")
                append(jsonEscape(turn.role))
                append("\",\"content\":\"")
                append(jsonEscape(turn.content))
                append("\"}")
            }
            append("]}")
        }
    }

    fun cleanAssistantReply(rawText: String, speakerName: String): String {
        var cleaned = rawText.trim()
        val prefix = "$speakerName:"
        if (cleaned.startsWith(prefix, ignoreCase = true)) {
            cleaned = cleaned.substring(prefix.length).trimStart()
        }
        if (cleaned.length >= 2 &&
            ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
                (cleaned.startsWith("«") && cleaned.endsWith("»")))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        return cleaned.take(1800)
    }

    suspend fun generateCharacterReply(
        settings: LlmGlobalSettings,
        chat: FakeChat,
        chatConfig: ChatAiConfig,
        recentMessages: List<FakeMessage>,
        myDefaultName: String,
        preferredGroupSpeaker: String?,
        sensitiveFilterEnabled: Boolean
    ): Result<GeneratedLlmReply> {
        if (!settings.isConfiguredAndEnabled) {
            return Result.failure(
                IllegalStateException("Configura y activa primero un proveedor LLM en el menú derecho")
            )
        }
        val members = if (chat.groupChat) {
            chat.resolvedGroupMembers(myDefaultName).filter { !it.isMe }.map { it.name }.distinct()
        } else {
            emptyList()
        }
        val speakerName = preferredGroupSpeaker?.trim()?.takeIf { it.isNotEmpty() }
            ?: members.firstOrNull()
            ?: chat.contactName

        val turns = buildChatTurns(
            chat = chat,
            chatConfig = chatConfig,
            recentMessages = recentMessages,
            myDefaultName = myDefaultName,
            preferredGroupSpeaker = speakerName,
            sensitiveFilterEnabled = sensitiveFilterEnabled,
            contextLimit = settings.contextMessageLimit
        )
        val payloadJson = buildRequestPayloadJson(
            model = settings.effectiveModel,
            turns = turns,
            temperature = chatConfig.temperature,
            maxTokens = 420
        )
        return executeCompletionRequest(settings, payloadJson).map { rawReply ->
            val cleaned = cleanAssistantReply(rawReply, speakerName)
            GeneratedLlmReply(
                text = cleaned.ifBlank { rawReply.trim() },
                speakerName = speakerName
            )
        }
    }

    suspend fun testConnection(settings: LlmGlobalSettings): Result<String> {
        val payloadJson = buildRequestPayloadJson(
            model = settings.effectiveModel,
            turns = listOf(
                LlmTurnMessage("system", "Responde únicamente con: OK"),
                LlmTurnMessage("user", "Ping")
            ),
            temperature = 0.2f,
            maxTokens = 16
        )
        return executeCompletionRequest(settings, payloadJson).map { reply ->
            "Conexión correcta con ${settings.provider.displayName} (${settings.effectiveModel}): ${reply.trim().take(40)}"
        }
    }

    private suspend fun executeCompletionRequest(
        settings: LlmGlobalSettings,
        payloadJson: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = validateEndpointUrl(
                rawUrl = settings.effectiveEndpointUrl,
                allowHttp = settings.provider.allowsHttp
            ).getOrThrow()

            if (settings.provider.requiresApiKey && settings.apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Falta tu API key para ${settings.provider.displayName}")
                )
            }

            val bytes = payloadJson.toByteArray(Charsets.UTF_8)
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MostazaFake/1.0")
                if (settings.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
                }
                if (settings.provider == LlmProvider.OPENROUTER) {
                    setRequestProperty("HTTP-Referer", "https://mostazafake.app")
                    setRequestProperty("X-Title", "MostazaFake")
                }
            }

            try {
                connection.outputStream.use { out ->
                    out.write(bytes)
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val rawBody = if (stream != null) {
                    BufferedInputStream(stream).use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            total += read
                            if (total > MAX_RESPONSE_BYTES) {
                                throw IllegalStateException("La respuesta del servidor supera el límite de 1 MB")
                            }
                            out.write(buf, 0, read)
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                } else {
                    ""
                }

                if (code !in 200..299) {
                    val serverMsg = extractErrorMessage(rawBody)
                    val friendly = when (code) {
                        401, 403 -> "Clave API rechazada ($code). Revisa tu token en ${settings.provider.displayName}."
                        404 -> "Endpoint o modelo no encontrado (404). Revisa la URL y el nombre del modelo."
                        429 -> "Límite de peticiones o saldo agotado (429) en ${settings.provider.displayName}."
                        in 500..599 -> "El servidor del LLM devolvió un error temporal ($code)."
                        else -> "Error HTTP $code del proveedor LLM."
                    }
                    val fullMsg = if (!serverMsg.isNullOrBlank()) "$friendly ($serverMsg)" else friendly
                    return@withContext Result.failure(IllegalStateException(fullMsg))
                }

                val text = extractCompletionText(rawBody)
                if (text.isNullOrBlank()) {
                    Result.failure(IllegalStateException("El modelo devolvió una respuesta vacía"))
                } else {
                    Result.success(text)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage.orEmpty()
            if (msg.contains("CLEARTEXT", ignoreCase = true)) {
                Result.failure(
                    IllegalStateException(
                        "Android bloqueó HTTP sin cifrar para esa IP. Usa https:// (p. ej. túnel Tailscale/Cloudflare) o localhost 127.0.0.1 / 10.0.2.2."
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    private fun extractCompletionText(rawBody: String): String? {
        return try {
            val root = JSONObject(rawBody)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.optJSONObject(0)
                val messageObj = first?.optJSONObject("message")
                val content = messageObj?.optString("content").orEmpty()
                if (content.isNotBlank()) return content
                val deltaText = first?.optString("text").orEmpty()
                if (deltaText.isNotBlank()) return deltaText
            }
            // Fallback for Ollama native /api/chat shape
            val ollamaMsg = root.optJSONObject("message")?.optString("content").orEmpty()
            if (ollamaMsg.isNotBlank()) return ollamaMsg
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractErrorMessage(rawBody: String): String? {
        return try {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.take(180)
                ?: root.optString("message").takeIf { it.isNotBlank() }?.take(180)
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonEscape(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    append(ch)
                }
            }
        }
    }
}
