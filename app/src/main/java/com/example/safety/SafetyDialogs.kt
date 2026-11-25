package com.example.safety

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.media.ExtractedWatermark
import com.example.media.ScreenshotWatermarkEngine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@Composable
fun AgeAndFictionGateDialog(
    onConfirmAdult: (enableSensitiveFilter: Boolean) -> Unit,
    onConfirmMinorStrict: () -> Unit
) {
    var keepSensitiveFilter by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* Mandatory gate until user selects age/filter option */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "MostazaFake • Aviso de Edad (+18) y Ficción",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "App para crear conversaciones ficticias y simular chats NO reales. Ningún diálogo representa a una persona real salvo ficción explícita del usuario.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = "Si orientas los chats a roleplay con personajes o conectas modelos sin filtros, puede aparecer contenido sensible (+18). Confirma tu edad para continuar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Activar filtro de contenido sensible",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Oculta y bloquea texto explícito o violento",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepSensitiveFilter,
                            onCheckedChange = { keepSensitiveFilter = it },
                            modifier = Modifier.testTag("age_gate_filter_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmAdult(keepSensitiveFilter) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("age_gate_confirm_adult_button")
            ) {
                Text("Soy mayor de 18 años (+18)")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onConfirmMinorStrict,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("age_gate_minor_filter_button")
            ) {
                Text("Soy menor / Activar filtro estricto")
            }
        }
    )
}

@Composable
fun AdultAgeVerificationDialog(
    onConfirmedAdult: () -> Unit,
    onDismiss: () -> Unit
) {
    var birthYearInput by remember { mutableStateOf("") }
    var declarationChecked by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null) },
        title = { Text("Verificación de edad (+18)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Para desactivar el filtro de contenido sensible (+18) en roleplay sin filtros, confirma tu año de nacimiento.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = birthYearInput,
                    onValueChange = { raw ->
                        birthYearInput = raw.filter { it.isDigit() }.take(4)
                        errorText = null
                    },
                    label = { Text("Año de nacimiento (ej. 1998)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = declarationChecked,
                        onCheckedChange = {
                            declarationChecked = it
                            errorText = null
                        }
                    )
                    Text(
                        text = "Confirmo bajo mi responsabilidad que tengo 18 años o más.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val year = birthYearInput.toIntOrNull()
                    val age = if (year != null) currentYear - year else -1
                    when {
                        year == null || year < 1900 || year > currentYear ->
                            errorText = "Introduce un año de nacimiento válido."
                        age < 18 ->
                            errorText = "Debes tener al menos 18 años para desactivar el filtro."
                        !declarationChecked ->
                            errorText = "Marca la casilla de confirmación."
                        else -> onConfirmedAdult()
                    }
                }
            ) {
                Text("Confirmar +18")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ReportIncidentDialog(
    chatId: String? = null,
    chatTitle: String? = null,
    messageId: String? = null,
    messageExcerpt: String? = null,
    developerEmail: String,
    canHideContentLocally: Boolean,
    onDismiss: () -> Unit,
    onSubmitReport: (
        category: ReportCategory,
        details: String,
        hideContentLocally: Boolean
    ) -> IncidentReport
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(ReportCategory.ABUSE_OR_ILLEGAL) }
    var details by remember { mutableStateOf("") }
    var hideLocally by remember { mutableStateOf(canHideContentLocally) }
    var submittedReport by remember { mutableStateOf<IncidentReport?>(null) }
    var copyFeedback by remember { mutableStateOf<String?>(null) }

    val existingSubmitted = submittedReport
    if (existingSubmitted != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Incidente registrado (${existingSubmitted.id})") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (existingSubmitted.hidContentLocally) {
                            "El reporte se ha guardado en este dispositivo y el contenido señalado ha sido ocultado/eliminado."
                        } else {
                            "El reporte se ha guardado en el historial de seguridad de este dispositivo."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Aviso: MostazaFake no envía notificaciones automáticas ocultas a ningún servidor central. Si deseas hacer llegar este incidente al responsable/desarrollador, envíalo por correo o compártelo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (copyFeedback != null) {
                        Text(
                            text = copyFeedback ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val summary = SafetyPrefsStore.buildReportSummaryText(existingSubmitted)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Reporte MostazaFake", summary))
                            copyFeedback = "Informe copiado al portapapeles"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar informe completo")
                    }
                    Button(
                        onClick = {
                            val intent = SafetyPrefsStore.createEmailReportIntent(
                                report = existingSubmitted,
                                recipientEmail = developerEmail
                            )
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    copyFeedback = "No se encontró app de correo. Usa 'Copiar informe'."
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (developerEmail.isNotBlank()) "Enviar por correo a $developerEmail"
                            else "Enviar copia por correo"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Reportar contenido o incidente") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!chatTitle.isNullOrBlank()) {
                    Text(
                        text = "Chat: $chatTitle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!messageExcerpt.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "\"${messageExcerpt.take(120)}\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                ReportCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.labelEs,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = category.descriptionEs,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    label = { Text("Detalles adicionales (opcional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (canHideContentLocally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = hideLocally,
                            onCheckedChange = { hideLocally = it }
                        )
                        Text(
                            text = if (messageId != null) {
                                "Eliminar también este mensaje de mi chat"
                            } else {
                                "Eliminar esta conversación de mi dispositivo"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val created = onSubmitReport(selectedCategory, details, hideLocally)
                    submittedReport = created
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("submit_incident_report_button")
            ) {
                Text("Registrar reporte")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ScreenshotWatermarkVerifierDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isInspecting by remember { mutableStateOf(false) }
    var inspectedResult by remember { mutableStateOf<ExtractedWatermark?>(null) }
    var inspectError by remember { mutableStateOf<String?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isInspecting = true
                inspectError = null
                ScreenshotWatermarkEngine.inspectUriWatermark(context, uri)
                    .onSuccess { inspectedResult = it }
                    .onFailure { inspectError = it.localizedMessage ?: "Error al analizar la imagen" }
                isInspecting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ImageSearch, contentDescription = null) },
        title = { Text("Verificar marca de agua y metadatos") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Selecciona una captura guardada con MostazaFake para inspeccionar su señal invisible (LSB + patrón espectral) y sus metadatos PNG/XMP de origen sintético.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isInspecting,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.ImageSearch, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir captura de la galería")
                }

                if (isInspecting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (inspectError != null) {
                    Text(
                        text = inspectError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                inspectedResult?.let { res ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (res.detected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (res.detected) Icons.Rounded.Security else Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (res.detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (res.detected) {
                                        "Origen sintético / ficticio DETECTADO"
                                    } else {
                                        "Sin marca MostazaFake detectada"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Correlación señal invisible (canal verde): %.1f%%",
                                    (res.spectralPilotScore * 100.0).coerceIn(-100.0, 100.0)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            res.payloadJson?.let { json ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text(
                                    text = "Carga oculta en píxeles (LSB canal azul):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = json,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (res.metadataFields.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text(
                                    text = "Metadatos incrustados (${res.metadataFields.size} campos):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                res.metadataFields.forEach { (k, v) ->
                                    val preview = if (k == "XML:com.adobe.xmp") {
                                        "Paquete XMP completo (DigitalSourceType=compositeSynthetic, originFake=true)"
                                    } else {
                                        v
                                    }
                                    Text(
                                        text = "• $k: $preview",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
