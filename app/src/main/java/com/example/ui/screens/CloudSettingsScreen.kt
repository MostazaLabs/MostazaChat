package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.ExternalSessionConfig
import com.example.auth.GoogleSignInButton
import com.example.auth.signOut
import com.example.data.model.UiState
import com.example.llm.LlmSettingsSectionCard
import com.example.media.ParsedWhatsAppChat
import com.example.media.WhatsAppTxtParser
import com.example.safety.AdultAgeVerificationDialog
import com.example.safety.ReportIncidentDialog
import com.example.safety.SafetyPrefsStore
import com.example.safety.ScreenshotWatermarkVerifierDialog
import com.example.ui.components.BackupProviderPickerSheet
import com.example.ui.components.ProviderBadge
import com.example.ui.components.WhatsAppImportDialog
import com.example.ui.viewmodel.ChatsHomeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    viewModel: ChatsHomeViewModel,
    userEmail: String?,
    userDisplayName: String,
    isGuestMode: Boolean = false,
    externalSession: ExternalSessionConfig? = null,
    onUpdateExternalSession: (config: ExternalSessionConfig, importImmediately: Boolean) -> Unit = { _, _ -> },
    onExitGuestMode: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    SafetyPrefsStore.ensureInitialized(context)
    val safetySettings by SafetyPrefsStore.settings.collectAsStateWithLifecycle()
    val incidentReports by SafetyPrefsStore.reports.collectAsStateWithLifecycle()

    var defaultMyName by remember { mutableStateOf(profile?.defaultMyName ?: "Yo") }
    var developerEmailInput by remember { mutableStateOf(safetySettings.developerEmail) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var parsedWhatsAppDraft by remember { mutableStateOf<ParsedWhatsAppChat?>(null) }
    var isParsingWhatsAppTxt by remember { mutableStateOf(false) }
    var showExternalProviderSheet by remember { mutableStateOf(false) }
    var showAdultVerifyDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showWatermarkVerifierDialog by remember { mutableStateOf(false) }
    var showReportHistory by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.defaultMyName) {
        profile?.defaultMyName?.let { defaultMyName = it }
    }
    LaunchedEffect(safetySettings.developerEmail) {
        developerEmailInput = safetySettings.developerEmail
    }

    // SAF CreateDocument launcher: lets user save directly to Google Drive, Downloads, or share to PC!
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("Copia JSON guardada correctamente (compatible con Drive y PC)")
            } catch (e: Exception) {
                onShowMessage("Error al escribir el archivo: ${e.localizedMessage}")
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                }
                if (!text.isNullOrBlank()) {
                    viewModel.importWorkspaceJson(text)
                }
            } catch (e: Exception) {
                onShowMessage("No se pudo leer el archivo JSON: ${e.localizedMessage}")
            }
        }
    }

    val openWhatsAppTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isParsingWhatsAppTxt = true
                WhatsAppTxtParser.parseFromUri(context, uri)
                    .onSuccess { parsed -> parsedWhatsAppDraft = parsed }
                    .onFailure { err ->
                        onShowMessage(err.localizedMessage ?: "No se pudo leer el archivo .txt de WhatsApp")
                    }
                isParsingWhatsAppTxt = false
            }
        }
    }

    val chatCount = (chatsState as? UiState.Success)?.data?.size ?: 0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isGuestMode) "Modo Local y Respaldo" else "Nube y Respaldo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isBusy || isParsingWhatsAppTxt) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                // Prominent Presentation & Transparency Banner right at the top of the right menu
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("presentation_context_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TheaterComedy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "MostazaFake",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = if (safetySettings.isAdultVerified && !safetySettings.sensitiveFilterEnabled) {
                                            "+18 • Sin filtro"
                                        } else {
                                            "+18 • Filtro activo"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "App para crear conversaciones ficticias y simular chats NO reales.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Cualquier chat, contacto o roleplay generado aquí es ficción creada por el usuario. Las capturas incorporan marca de agua invisible y metadatos de origen sintético.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Desarrollado por MostazaLabs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Cloud or Local Account Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGuestMode) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeExt = externalSession?.takeIf { it.active }
                            if (isGuestMode && activeExt != null) {
                                ProviderBadge(provider = activeExt.provider, size = 54)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isGuestMode) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.primary
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGuestMode) Icons.Rounded.PhoneAndroid else Icons.Rounded.CloudDone,
                                        contentDescription = if (isGuestMode) "Modo invitado local" else "Sesión sincronizada",
                                        tint = if (isGuestMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isGuestMode && activeExt != null -> "Sesión: ${activeExt.provider.displayName}"
                                        isGuestMode -> "Modo Invitado Local"
                                        else -> userDisplayName.ifBlank { "Cuenta conectada" }
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isGuestMode && activeExt != null) {
                                    Text(
                                        text = activeExt.accountLabel.ifBlank { "Almacenamiento local + origen externo" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (isGuestMode) {
                                    Text(
                                        text = "Sin cuenta de Google vinculada",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isGuestMode) {
                                        "$chatCount conversaciones guardadas en este teléfono"
                                    } else {
                                        "$chatCount conversaciones sincronizadas en tiempo real"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isGuestMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Multi-device & efficiency explanation
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isGuestMode) {
                                    "Guardado únicamente en este teléfono"
                                } else {
                                    "Recuperación automática al cambiar de móvil"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isGuestMode) {
                                    "En modo invitado local tus chats, fotos y llamadas permanecen en la memoria de este dispositivo. Si cambias de móvil o desinstalas la app no habrá copia automática en la nube, salvo que exportes un respaldo .json o vincules tu cuenta de Google."
                                } else {
                                    "Cuando instales esta APK en otro teléfono, inicia sesión con la misma cuenta de Google y todas tus conversaciones, mensajes y llamadas aparecerán automáticamente."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isGuestMode) {
                                Spacer(Modifier.height(4.dp))
                                GoogleSignInButton(
                                    onAuthSuccess = {
                                        onShowMessage("Sesión de Google activada con sincronización en la nube")
                                    },
                                    onAuthError = { msg -> onShowMessage(msg) }
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Modo ahorro máximo activo de serie (sin animaciones de transición).",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Age Verification, Sensitive Filter, Reports & Watermark Verification Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("safety_moderation_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Edad (+18), Seguridad y Capturas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Filtro de edad y contenido sensible",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (safetySettings.sensitiveFilterEnabled) {
                                            "Activo: oculta y bloquea contenido adulto o violento"
                                        } else {
                                            "Desactivado (+18 verificado) para roleplay sin filtros"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = safetySettings.sensitiveFilterEnabled,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            SafetyPrefsStore.setSensitiveFilterEnabled(context, true)
                                            onShowMessage("Filtro de contenido sensible activado")
                                        } else {
                                            val changed = SafetyPrefsStore.setSensitiveFilterEnabled(context, false)
                                            if (!changed) {
                                                showAdultVerifyDialog = true
                                            } else {
                                                onShowMessage("Filtro sensible desactivado (+18 confirmado)")
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("settings_sensitive_filter_switch")
                                )
                            }

                            if (!safetySettings.isAdultVerified) {
                                OutlinedButton(
                                    onClick = { showAdultVerifyDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Confirmar mayoría de edad (+18)")
                                }
                            }

                            HorizontalDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Marca de agua invisible en capturas (siempre activa)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Cada captura genera copia PNG con señal invisible + metadatos de origen falso y conversación sintetizada.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { showWatermarkVerifierDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_screenshot_watermark_button")
                            ) {
                                Icon(Icons.Rounded.ImageSearch, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Verificar marca de agua y metadatos")
                            }

                            HorizontalDivider()

                            OutlinedTextField(
                                value = developerEmailInput,
                                onValueChange = { if (it.length <= 120) developerEmailInput = it },
                                label = { Text("Correo para envío opcional de reportes") },
                                placeholder = { Text("soporte@tudominio.com") },
                                singleLine = true,
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            SafetyPrefsStore.setDeveloperEmail(context, developerEmailInput)
                                            onShowMessage("Correo de reportes guardado")
                                        }
                                    ) {
                                        Text("Guardar")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = { showReportDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_report_incident_button")
                            ) {
                                Icon(Icons.Rounded.Flag, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reportar contenido o incidente (${incidentReports.size})")
                            }

                            if (incidentReports.isNotEmpty()) {
                                TextButton(
                                    onClick = { showReportHistory = !showReportHistory },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        if (showReportHistory) "Ocultar historial de reportes"
                                        else "Ver historial (${incidentReports.size})"
                                    )
                                }
                            }

                            if (showReportHistory && incidentReports.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    incidentReports.take(5).forEach { rep ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "${rep.id} • ${rep.category.labelEs}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${rep.createdAtIso}${rep.chatTitle?.let { " • $it" } ?: ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { SafetyPrefsStore.clearReports(context) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Limpiar historial local")
                                    }
                                }
                            }
                        }
                    }
                }

                // Optional External LLM Connectivity Card
                item {
                    LlmSettingsSectionCard(onShowMessage = onShowMessage)
                }

                // Always-on Power Saving Mode Card (Telegram-inspired, permanently enabled)
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("power_saving_status_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Ahorro de energía",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "Siempre activo",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Reduce siempre el uso de energía y las animaciones, sin importar el nivel de batería. Desactivado de forma nativa para máximo rendimiento:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val disabledEffects = listOf(
                                "Stickers y emojis animados",
                                "Efectos en chats y partículas",
                                "Animación en llamadas",
                                "Autorreproducir vídeos y GIF",
                                "Transiciones suaves de pantalla"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                disabledEffects.forEach { effectLabel ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = effectLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Desactivado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Studio Preferences
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Tu nombre en los chats",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedTextField(
                                value = defaultMyName,
                                onValueChange = { if (it.length <= 40) defaultMyName = it },
                                label = { Text("Nombre predeterminado ('Tú')") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { viewModel.updateDefaultMyName(defaultMyName) },
                                enabled = defaultMyName.isNotBlank() && !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Guardar preferencia")
                            }
                        }
                    }
                }

                // Multi-Cloud & Personal Remote Backup Source Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Otras nubes y remoto personal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Configura Dropbox, Drive, OneDrive, Box, Facebook (Meta), ProtonMail, TeraBox, Internxt o tu servidor WebDAV/Red Local/SMB/FTP/SFTP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val configured = externalSession?.takeIf { it.active }
                            if (configured != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ProviderBadge(provider = configured.provider, size = 36)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = configured.provider.displayName +
                                                    if (configured.accountLabel.isNotBlank()) " • ${configured.accountLabel}" else "",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = when {
                                                    configured.remoteRoute.isNotBlank() -> configured.remoteRoute
                                                    configured.savedDocumentUri.isNotBlank() -> "Archivo seleccionado del sistema listo para importar"
                                                    else -> "Sin ruta fija (usa el selector de archivos del proveedor)"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                if (configured.hasHttpRoute || configured.savedDocumentUri.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            viewModel.importFromExternalSession(
                                                context = context,
                                                config = configured,
                                                onImportedChat = onOpenChat
                                            )
                                        },
                                        enabled = !isBusy && !isParsingWhatsAppTxt,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("sync_download_external_button")
                                    ) {
                                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Descargar / importar desde esta ruta")
                                    }
                                }

                                if (configured.hasHttpRoute) {
                                    OutlinedButton(
                                        onClick = { viewModel.uploadBackupToExternalRoute(configured) },
                                        enabled = !isBusy && !isParsingWhatsAppTxt,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("sync_upload_external_button")
                                    ) {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Subir copia (.json) a esta ruta")
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = { showExternalProviderSheet = true },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("choose_backup_provider_button")
                            ) {
                                Icon(Icons.Rounded.Computer, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (configured != null) "Cambiar nube o ruta remota"
                                    else "Elegir nube o remoto (Nube / Remoto)"
                                )
                            }
                        }
                    }
                }

                // Drive / PC JSON Backup Export & Import
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Copia portátil para nubes, gestor de archivos o PC",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isGuestMode) {
                                    "Exporta o abre tu archivo .json desde Google Drive, Dropbox, OneDrive, Box, Proton, TeraBox, Internxt o tu ordenador."
                                } else {
                                    "Además de la nube automática, puedes guardar o cargar un archivo .json desde cualquier nube instalada o desde tu ordenador."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    viewModel.prepareExportJson { json ->
                                        pendingExportJson = json
                                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                        createBackupLauncher.launch("mostazafake_backup_$stamp.json")
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("export_json_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Exportar historial (.json)")
                            }
                            OutlinedButton(
                                onClick = {
                                    openBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("import_json_button")
                            ) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Importar respaldo (.json)")
                            }
                            OutlinedButton(
                                onClick = {
                                    openWhatsAppTxtLauncher.launch(arrayOf("text/plain", "application/zip", "*/*"))
                                },
                                enabled = !isBusy && !isParsingWhatsAppTxt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("cloud_import_whatsapp_txt_button")
                            ) {
                                Icon(Icons.Rounded.Description, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Importar chat de WhatsApp (.txt / .zip)")
                            }
                        }
                    }
                }

                // Sign Out / Exit Guest Mode & Brand Footer
                item {
                    if (isGuestMode) {
                        OutlinedButton(
                            onClick = onExitGuestMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("exit_guest_mode_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (externalSession?.active == true) {
                                    "Cerrar sesión (${externalSession.provider.displayName})"
                                } else {
                                    "Salir del modo invitado"
                                }
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cerrar sesión de Google")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "MostazaFake",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "App de conversaciones ficticias • MostazaLabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    parsedWhatsAppDraft?.let { parsed ->
        WhatsAppImportDialog(
            parsedChat = parsed,
            myDefaultName = profile?.defaultMyName ?: "Yo",
            userDisplayName = userDisplayName,
            onDismiss = { parsedWhatsAppDraft = null },
            onConfirmImport = { title, myParticipant, isGroup ->
                parsedWhatsAppDraft = null
                viewModel.importWhatsAppChat(
                    parsed = parsed,
                    chatTitle = title,
                    myParticipantName = myParticipant,
                    isGroup = isGroup,
                    onImported = onOpenChat
                )
            }
        )
    }

    if (showExternalProviderSheet) {
        BackupProviderPickerSheet(
            title = "Configurar nube o remoto",
            confirmButtonLabel = "Guardar origen",
            initialConfig = externalSession,
            onDismiss = { showExternalProviderSheet = false },
            onConfirmConfig = { newConfig, importImmediately ->
                showExternalProviderSheet = false
                onUpdateExternalSession(newConfig, importImmediately)
                if (importImmediately) {
                    viewModel.importFromExternalSession(
                        context = context,
                        config = newConfig,
                        onImportedChat = onOpenChat
                    )
                } else {
                    onShowMessage("Origen actualizado: ${newConfig.provider.displayName}")
                }
            }
        )
    }

    if (showAdultVerifyDialog) {
        AdultAgeVerificationDialog(
            onConfirmedAdult = {
                showAdultVerifyDialog = false
                SafetyPrefsStore.confirmAdultAccess(context, enableSensitiveFilter = false)
                onShowMessage("Mayoría de edad (+18) confirmada. Filtro sensible desactivado.")
            },
            onDismiss = { showAdultVerifyDialog = false }
        )
    }

    if (showReportDialog) {
        ReportIncidentDialog(
            developerEmail = safetySettings.developerEmail,
            canHideContentLocally = false,
            onDismiss = { showReportDialog = false },
            onSubmitReport = { category, details, _ ->
                SafetyPrefsStore.addReport(
                    context = context,
                    category = category,
                    details = details,
                    hidContentLocally = false
                )
            }
        )
    }

    if (showWatermarkVerifierDialog) {
        ScreenshotWatermarkVerifierDialog(
            onDismiss = { showWatermarkVerifierDialog = false }
        )
    }
}
