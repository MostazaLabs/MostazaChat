package com.example.llm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class LlmProvider(
    val displayName: String,
    val shortBadge: String,
    val defaultUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val allowsHttp: Boolean,
    val hintEs: String
) {
    OPENROUTER(
        displayName = "OpenRouter",
        shortBadge = "OR",
        defaultUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        requiresApiKey = true,
        allowsHttp = false,
        hintEs = "Da acceso a decenas de modelos (incluidos gratuitos y sin filtro estricto de roleplay)"
    ),
    OPENAI(
        displayName = "OpenAI (ChatGPT)",
        shortBadge = "GPT",
        defaultUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        requiresApiKey = true,
        allowsHttp = false,
        hintEs = "Modelos oficiales GPT con tu propia clave de OpenAI"
    ),
    GROQ(
        displayName = "Groq",
        shortBadge = "GQ",
        defaultUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "llama-3.3-70b-versatile",
        requiresApiKey = true,
        allowsHttp = false,
        hintEs = "Inferencia muy rápida con modelos Llama y Mixtral"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        shortBadge = "DS",
        defaultUrl = "https://api.deepseek.com/chat/completions",
        defaultModel = "deepseek-chat",
        requiresApiKey = true,
        allowsHttp = false,
        hintEs = "Modelos DeepSeek compatibles con formato OpenAI"
    ),
    OLLAMA_LOCAL(
        displayName = "Ollama / LM Studio (Local)",
        shortBadge = "LOC",
        defaultUrl = "http://127.0.0.1:11434/v1/chat/completions",
        defaultModel = "llama3.1",
        requiresApiKey = false,
        allowsHttp = true,
        hintEs = "Conecta un modelo local en tu equipo (127.0.0.1, 10.0.2.2 o túnel https://)"
    ),
    CUSTOM(
        displayName = "URL personalizada",
        shortBadge = "API",
        defaultUrl = "https://tu-servidor.com/v1/chat/completions",
        defaultModel = "default",
        requiresApiKey = false,
        allowsHttp = true,
        hintEs = "Cualquier servidor o proxy compatible con /v1/chat/completions"
    );

    companion object {
        fun fromName(raw: String?): LlmProvider =
            entries.firstOrNull { it.name == raw } ?: OPENROUTER
    }
}

data class LlmGlobalSettings(
    val enabled: Boolean = false,
    val provider: LlmProvider = LlmProvider.OPENROUTER,
    val customEndpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val contextMessageLimit: Int = 16
) {
    val effectiveEndpointUrl: String
        get() = customEndpointUrl.trim().ifBlank { provider.defaultUrl }

    val effectiveModel: String
        get() = model.trim().ifBlank { provider.defaultModel }

    val isConfiguredAndEnabled: Boolean
        get() = enabled &&
            effectiveEndpointUrl.isNotBlank() &&
            effectiveModel.isNotBlank() &&
            (!provider.requiresApiKey || apiKey.trim().isNotEmpty())
}

data class ChatAiConfig(
    val enabled: Boolean = false,
    val personaPrompt: String = "",
    val autoReplyOnSend: Boolean = false,
    val temperature: Float = 0.85f
)

object LlmSettingsStore {
    private const val PREFS_NAME = "mostazafake_llm_prefs"
    private const val KEY_ENABLED = "llm_enabled"
    private const val KEY_PROVIDER = "llm_provider"
    private const val KEY_ENDPOINT_URL = "llm_endpoint_url"
    private const val KEY_API_KEY = "llm_api_key"
    private const val KEY_MODEL = "llm_model"
    private const val KEY_CONTEXT_LIMIT = "llm_context_limit"
    private const val KEY_CHAT_CONFIGS_JSON = "llm_chat_configs_json"

    private val _globalSettings = MutableStateFlow(LlmGlobalSettings())
    val globalSettings: StateFlow<LlmGlobalSettings> = _globalSettings.asStateFlow()

    private val _chatConfigs = MutableStateFlow<Map<String, ChatAiConfig>>(emptyMap())
    val chatConfigs: StateFlow<Map<String, ChatAiConfig>> = _chatConfigs.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _globalSettings.value = LlmGlobalSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                provider = LlmProvider.fromName(prefs.getString(KEY_PROVIDER, null)),
                customEndpointUrl = prefs.getString(KEY_ENDPOINT_URL, "").orEmpty(),
                apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
                model = prefs.getString(KEY_MODEL, "").orEmpty(),
                contextMessageLimit = prefs.getInt(KEY_CONTEXT_LIMIT, 16).coerceIn(4, 30)
            )
            _chatConfigs.value = decodeChatConfigs(prefs.getString(KEY_CHAT_CONFIGS_JSON, "{}").orEmpty())
            initialized = true
        }
    }

    fun saveGlobalSettings(context: Context, updated: LlmGlobalSettings) {
        ensureInitialized(context)
        val clean = updated.copy(
            customEndpointUrl = updated.customEndpointUrl.trim().take(300),
            apiKey = updated.apiKey.trim().take(300),
            model = updated.model.trim().take(120),
            contextMessageLimit = updated.contextMessageLimit.coerceIn(4, 30)
        )
        _globalSettings.value = clean
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, clean.enabled)
            .putString(KEY_PROVIDER, clean.provider.name)
            .putString(KEY_ENDPOINT_URL, clean.customEndpointUrl)
            .putString(KEY_API_KEY, clean.apiKey)
            .putString(KEY_MODEL, clean.model)
            .putInt(KEY_CONTEXT_LIMIT, clean.contextMessageLimit)
            .apply()
    }

    fun getChatConfig(chatId: String): ChatAiConfig =
        _chatConfigs.value[chatId] ?: ChatAiConfig()

    fun saveChatConfig(context: Context, chatId: String, config: ChatAiConfig) {
        if (chatId.isBlank()) return
        ensureInitialized(context)
        val clean = config.copy(
            personaPrompt = config.personaPrompt.trim().take(1200),
            temperature = config.temperature.coerceIn(0.1f, 1.4f)
        )
        val mutable = _chatConfigs.value.toMutableMap()
        if (!clean.enabled && clean.personaPrompt.isBlank() && !clean.autoReplyOnSend) {
            mutable.remove(chatId)
        } else {
            mutable[chatId] = clean
        }
        persistChatConfigs(context, mutable)
    }

    fun deleteChatConfig(context: Context, chatId: String) {
        if (chatId.isBlank()) return
        ensureInitialized(context)
        val mutable = _chatConfigs.value.toMutableMap()
        if (mutable.remove(chatId) != null) {
            persistChatConfigs(context, mutable)
        }
    }

    private fun persistChatConfigs(context: Context, map: Map<String, ChatAiConfig>) {
        _chatConfigs.value = map
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CHAT_CONFIGS_JSON, encodeChatConfigs(map)).apply()
    }

    private fun encodeChatConfigs(map: Map<String, ChatAiConfig>): String {
        val root = JSONObject()
        map.forEach { (chatId, cfg) ->
            root.put(
                chatId,
                JSONObject().apply {
                    put("enabled", cfg.enabled)
                    put("personaPrompt", cfg.personaPrompt)
                    put("autoReplyOnSend", cfg.autoReplyOnSend)
                    put("temperature", cfg.temperature.toDouble())
                }
            )
        }
        return root.toString()
    }

    private fun decodeChatConfigs(raw: String): Map<String, ChatAiConfig> {
        return try {
            val root = JSONObject(raw)
            val result = linkedMapOf<String, ChatAiConfig>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val chatId = keys.next()
                val obj = root.optJSONObject(chatId) ?: continue
                result[chatId] = ChatAiConfig(
                    enabled = obj.optBoolean("enabled", false),
                    personaPrompt = obj.optString("personaPrompt", ""),
                    autoReplyOnSend = obj.optBoolean("autoReplyOnSend", false),
                    temperature = obj.optDouble("temperature", 0.85).toFloat().coerceIn(0.1f, 1.4f)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
