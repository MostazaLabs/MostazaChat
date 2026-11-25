package com.example.safety

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ReportCategory(val labelEs: String, val descriptionEs: String) {
    ABUSE_OR_ILLEGAL(
        labelEs = "Contenido ilegal o abuso",
        descriptionEs = "Explotación, actividades ilegales o daño grave"
    ),
    NSFW_NON_CONSENSUAL(
        labelEs = "Contenido sexual (+18) no deseado",
        descriptionEs = "Material explícito visible sin filtro de edad"
    ),
    HARASSMENT_OR_HATE(
        labelEs = "Acoso, odio o amenazas",
        descriptionEs = "Insultos graves, discriminación o intimidación"
    ),
    IMPERSONATION_FRAUD(
        labelEs = "Suplantación o uso engañoso",
        descriptionEs = "Uso de chats ficticios para engañar o estafar"
    ),
    VIOLENCE_OR_SELF_HARM(
        labelEs = "Violencia gráfica o autolesión",
        descriptionEs = "Contenido violento extremo o incitación al daño"
    )
}

data class SafetySettings(
    val ageNoticeAccepted: Boolean = false,
    val isAdultVerified: Boolean = false,
    val sensitiveFilterEnabled: Boolean = true,
    val developerEmail: String = ""
)

data class IncidentReport(
    val id: String,
    val category: ReportCategory,
    val details: String,
    val chatId: String?,
    val chatTitle: String?,
    val messageId: String?,
    val messageExcerpt: String?,
    val createdAtIso: String,
    val hidContentLocally: Boolean
)

object SensitiveContentFilter {

    private val sensitiveKeywords = setOf(
        // Adult / explicit
        "porno", "pornografia", "pornografico", "xxx", "nsfw", "sexo explicito",
        "follar", "follamos", "follando", "desnudate", "desnuda", "desnudo",
        "penetracion", "orgasmo", "masturbacion", "masturbar", "erotico", "erotica",
        "puta", "prostituta", "escort", "nude", "nudes", "onlyfans",
        // Extreme violence / self-harm
        "suicidio", "suicidarme", "suicidate", "autolesion", "cortarme las venas",
        "matar", "matarte", "asesinar", "asesinato", "decapitar", "violacion", "violar",
        "tortura", "sangre", "bomba casera", "terrorismo",
        // Drugs / illegal
        "cocaina", "heroina", "metanfetamina", "fentanilo", "narcotrafico"
    )

    private val keywordPatterns: List<Regex> = sensitiveKeywords.map { term ->
        Regex("\\b${Regex.escape(normalize(term))}\\b", RegexOption.IGNORE_CASE)
    }

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun containsSensitiveContent(text: String?): Boolean {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return false
        val normalized = normalize(clean)
        return keywordPatterns.any { it.containsMatchIn(normalized) }
    }

    fun maskIfFiltered(text: String, filterEnabled: Boolean): String {
        if (!filterEnabled) return text
        return if (containsSensitiveContent(text)) {
            "🔒 Contenido oculto por filtro de edad (+18)"
        } else {
            text
        }
    }
}

object SafetyPrefsStore {
    private const val PREFS_NAME = "mostazafake_safety_prefs"
    private const val KEY_AGE_NOTICE_ACCEPTED = "age_notice_accepted"
    private const val KEY_IS_ADULT_VERIFIED = "is_adult_verified"
    private const val KEY_SENSITIVE_FILTER_ENABLED = "sensitive_filter_enabled"
    private const val KEY_DEVELOPER_EMAIL = "developer_email"
    private const val KEY_REPORTS_JSON = "incident_reports_json"

    private val _settings = MutableStateFlow(SafetySettings())
    val settings: StateFlow<SafetySettings> = _settings.asStateFlow()

    private val _reports = MutableStateFlow<List<IncidentReport>>(emptyList())
    val reports: StateFlow<List<IncidentReport>> = _reports.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _settings.value = SafetySettings(
                ageNoticeAccepted = prefs.getBoolean(KEY_AGE_NOTICE_ACCEPTED, false),
                isAdultVerified = prefs.getBoolean(KEY_IS_ADULT_VERIFIED, false),
                sensitiveFilterEnabled = prefs.getBoolean(KEY_SENSITIVE_FILTER_ENABLED, true),
                developerEmail = prefs.getString(KEY_DEVELOPER_EMAIL, "").orEmpty()
            )
            _reports.value = decodeReports(prefs.getString(KEY_REPORTS_JSON, "[]").orEmpty())
            initialized = true
        }
    }

    fun confirmAdultAccess(
        context: Context,
        enableSensitiveFilter: Boolean
    ) {
        ensureInitialized(context)
        val updated = _settings.value.copy(
            ageNoticeAccepted = true,
            isAdultVerified = true,
            sensitiveFilterEnabled = enableSensitiveFilter
        )
        persistSettings(context, updated)
    }

    fun confirmMinorOrStrictFilter(context: Context) {
        ensureInitialized(context)
        val updated = _settings.value.copy(
            ageNoticeAccepted = true,
            isAdultVerified = false,
            sensitiveFilterEnabled = true
        )
        persistSettings(context, updated)
    }

    fun setSensitiveFilterEnabled(context: Context, enabled: Boolean): Boolean {
        ensureInitialized(context)
        val current = _settings.value
        if (!enabled && !current.isAdultVerified) {
            return false
        }
        persistSettings(context, current.copy(sensitiveFilterEnabled = enabled))
        return true
    }

    fun setDeveloperEmail(context: Context, email: String) {
        ensureInitialized(context)
        val clean = email.trim().take(120)
        persistSettings(context, _settings.value.copy(developerEmail = clean))
    }

    fun addReport(
        context: Context,
        category: ReportCategory,
        details: String,
        chatId: String? = null,
        chatTitle: String? = null,
        messageId: String? = null,
        messageExcerpt: String? = null,
        hidContentLocally: Boolean = false
    ): IncidentReport {
        ensureInitialized(context)
        val report = IncidentReport(
            id = "REP-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
            category = category,
            details = details.trim().take(600),
            chatId = chatId?.takeIf { it.isNotBlank() },
            chatTitle = chatTitle?.trim()?.take(80)?.takeIf { it.isNotEmpty() },
            messageId = messageId?.takeIf { it.isNotBlank() },
            messageExcerpt = messageExcerpt?.trim()?.take(180)?.takeIf { it.isNotEmpty() },
            createdAtIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            hidContentLocally = hidContentLocally
        )
        val updated = (listOf(report) + _reports.value).take(50)
        _reports.value = updated
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REPORTS_JSON, encodeReports(updated)).apply()
        return report
    }

    fun clearReports(context: Context) {
        ensureInitialized(context)
        _reports.value = emptyList()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REPORTS_JSON, "[]").apply()
    }

    fun buildReportSummaryText(report: IncidentReport): String {
        return buildString {
            appendLine("Reporte MostazaFake (${report.id})")
            appendLine("Fecha: ${report.createdAtIso}")
            appendLine("Motivo: ${report.category.labelEs}")
            report.chatTitle?.let { appendLine("Conversación: $it") }
            report.chatId?.let { appendLine("ID chat: $it") }
            report.messageExcerpt?.let { appendLine("Fragmento: \"$it\"") }
            if (report.details.isNotBlank()) {
                appendLine("Detalles: ${report.details}")
            }
            appendLine("Acción local: ${if (report.hidContentLocally) "Contenido ocultado/eliminado en el dispositivo" else "Solo registro de incidente"}")
        }.trim()
    }

    fun createEmailReportIntent(report: IncidentReport, recipientEmail: String): Intent {
        val cleanRecipient = recipientEmail.trim()
        val subject = "[MostazaFake Reporte] ${report.category.labelEs} (${report.id})"
        val body = buildReportSummaryText(report)
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(cleanRecipient)}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    private fun persistSettings(context: Context, updated: SafetySettings) {
        _settings.value = updated
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AGE_NOTICE_ACCEPTED, updated.ageNoticeAccepted)
            .putBoolean(KEY_IS_ADULT_VERIFIED, updated.isAdultVerified)
            .putBoolean(KEY_SENSITIVE_FILTER_ENABLED, updated.sensitiveFilterEnabled)
            .putString(KEY_DEVELOPER_EMAIL, updated.developerEmail)
            .apply()
    }

    private fun encodeReports(list: List<IncidentReport>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("category", r.category.name)
                    put("details", r.details)
                    r.chatId?.let { put("chatId", it) }
                    r.chatTitle?.let { put("chatTitle", it) }
                    r.messageId?.let { put("messageId", it) }
                    r.messageExcerpt?.let { put("messageExcerpt", it) }
                    put("createdAtIso", r.createdAtIso)
                    put("hidContentLocally", r.hidContentLocally)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeReports(raw: String): List<IncidentReport> {
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val catName = o.optString("category", ReportCategory.ABUSE_OR_ILLEGAL.name)
                    val category = ReportCategory.entries.firstOrNull { it.name == catName }
                        ?: ReportCategory.ABUSE_OR_ILLEGAL
                    add(
                        IncidentReport(
                            id = o.optString("id", "REP-$i"),
                            category = category,
                            details = o.optString("details", ""),
                            chatId = o.optString("chatId", "").takeIf { it.isNotBlank() },
                            chatTitle = o.optString("chatTitle", "").takeIf { it.isNotBlank() },
                            messageId = o.optString("messageId", "").takeIf { it.isNotBlank() },
                            messageExcerpt = o.optString("messageExcerpt", "").takeIf { it.isNotBlank() },
                            createdAtIso = o.optString("createdAtIso", ""),
                            hidContentLocally = o.optBoolean("hidContentLocally", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
