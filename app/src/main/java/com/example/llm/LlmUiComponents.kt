package com.example.llm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LlmSettingsSectionCard(
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LlmSettingsStore.ensureInitialized(context)
    val saved by LlmSettingsStore.globalSettings.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(saved.enabled) }
    var enabled by remember { mutableStateOf(saved.enabled) }
    var provider by remember { mutableStateOf(saved.provider) }
    var modelInput by remember { mutableStateOf(saved.model.ifBlank { saved.provider.defaultModel }) }
    var endpointInput by remember { mutableStateOf(saved.customEndpointUrl.ifBlank { saved.provider.defaultUrl }) }
    var apiKeyInput by remember { mutableStateOf(saved.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(saved) {
        enabled = saved.enabled
        provider = saved.provider
        modelInput = saved.model.ifBlank { saved.provider.defaultModel }
        endpointInput = saved.customEndpointUrl.ifBlank { saved.provider.defaultUrl }
        apiKeyInput = saved.apiKey
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("llm_settings_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { expanded = !expanded }
                    .testTag("llm_settings_expand_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (saved.isConfiguredAndEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Conexión a LLM externos (Opcional)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (saved.isConfiguredAndEnabled) {
                                "${saved.provider.displayName} • ${saved.effectiveModel}"
                            } else {
                                "Apagado — modo 100% manual"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (saved.isConfiguredAndEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (saved.isConfiguredAndEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Text(
                            text = if (saved.isConfiguredAndEnabled) "Activo" else "Opcional",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Contraer configuración LLM" else "Expandir configuración LLM"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Habilitar respuestas por LLM",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Si lo apagas, todos los chats siguen igual en modo manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                enabled = checked
                                LlmSettingsStore.saveGlobalSettings(
                                    context,
                                    LlmGlobalSettings(
                                        enabled = checked,
                                        provider = provider,
                                        customEndpointUrl = endpointInput,
                                        apiKey = apiKeyInput,
                                        model = modelInput,
                                        contextMessageLimit = saved.contextMessageLimit
                                    )
                                )
                            },
                            modifier = Modifier.testTag("llm_global_enable_switch")
                        )
                    }

                    Text(
                        text = "Proveedor externo",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LlmProvider.entries.forEach { option ->
                            FilterChip(
                                selected = provider == option,
                                onClick = {
                                    val prevProvider = provider
                                    provider = option
                                    if (modelInput.isBlank() || modelInput == prevProvider.defaultModel) {
                                        modelInput = option.defaultModel
                                    }
                                    if (endpointInput.isBlank() || endpointInput == prevProvider.defaultUrl) {
                                        endpointInput = option.defaultUrl
                                    }
                                    testResultText = null
                                },
                                label = { Text(option.displayName) },
                                modifier = Modifier.testTag("llm_provider_chip_${option.name}")
                            )
                        }
                    }

                    Text(
                        text = provider.hintEs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { if (it.length <= 120) modelInput = it },
                        label = { Text("Modelo") },
                        placeholder = { Text(provider.defaultModel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("llm_model_input")
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { if (it.length <= 300) apiKeyInput = it },
                        label = {
                            Text(if (provider.requiresApiKey) "Tu API Key (requerida)" else "API Key / Token (opcional)")
                        },
                        placeholder = { Text(if (provider.requiresApiKey) "sk-... / or-..." else "Opcional en servidor local") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showApiKey) "Ocultar clave" else "Mostrar clave"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("llm_api_key_input")
                    )
                    Text(
                        text = "Tu clave se guarda únicamente en este dispositivo y no se sube a copias ni servidores.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { if (it.length <= 300) endpointInput = it },
                        label = { Text("URL endpoint (/chat/completions)") },
                        placeholder = { Text(provider.defaultUrl) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("llm_endpoint_input")
                    )

                    testResultText?.let { (ok, msg) ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (ok) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val candidate = LlmGlobalSettings(
                                    enabled = true,
                                    provider = provider,
                                    customEndpointUrl = endpointInput,
                                    apiKey = apiKeyInput,
                                    model = modelInput,
                                    contextMessageLimit = saved.contextMessageLimit
                                )
                                scope.launch {
                                    isTesting = true
                                    testResultText = null
                                    ExternalLlmClient.testConnection(candidate)
                                        .onSuccess { msg -> testResultText = true to msg }
                                        .onFailure { err ->
                                            testResultText = false to (err.localizedMessage ?: "No se pudo conectar con el LLM")
                                        }
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("llm_test_connection_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (isTesting) "Probando..." else "Probar")
                        }

                        Button(
                            onClick = {
                                val next = LlmGlobalSettings(
                                    enabled = enabled,
                                    provider = provider,
                                    customEndpointUrl = endpointInput,
                                    apiKey = apiKeyInput,
                                    model = modelInput,
                                    contextMessageLimit = saved.contextMessageLimit
                                )
                                LlmSettingsStore.saveGlobalSettings(context, next)
                                onShowMessage(
                                    if (next.isConfiguredAndEnabled) "LLM conectado: ${provider.displayName}"
                                    else "Ajustes LLM guardados"
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("llm_save_settings_button")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptionalChatAiSection(
    config: ChatAiConfig,
    isGroup: Boolean,
    onConfigChanged: (ChatAiConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LlmSettingsStore.ensureInitialized(context)
    val globalSettings by LlmSettingsStore.globalSettings.collectAsStateWithLifecycle()
    var expanded by remember(config.enabled) { mutableStateOf(config.enabled) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (config.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { expanded = !expanded }
                    .testTag("chat_ai_section_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (config.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (isGroup) "IA en este grupo (Opcional)" else "Personaje con IA (Opcional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (config.enabled) {
                                if (config.autoReplyOnSend) "LLM activo + respuesta automática"
                                else "LLM activo (botón manual en el chat)"
                            } else {
                                "Desactivado — control manual habitual"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Ocultar opciones de IA" else "Mostrar opciones de IA"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Responder con LLM en este chat",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { checked ->
                                onConfigChanged(config.copy(enabled = checked))
                            },
                            modifier = Modifier.testTag("chat_ai_enable_switch")
                        )
                    }

                    if (config.enabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-responder cuando envíe como Tú",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Si lo desactivas, podrás pedir respuesta solo cuando pulses «Generar IA».",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.autoReplyOnSend,
                                onCheckedChange = { auto ->
                                    onConfigChanged(config.copy(autoReplyOnSend = auto))
                                },
                                modifier = Modifier.testTag("chat_ai_autoreply_switch")
                            )
                        }

                        OutlinedTextField(
                            value = config.personaPrompt,
                            onValueChange = { if (it.length <= 1000) onConfigChanged(config.copy(personaPrompt = it)) },
                            label = {
                                Text(
                                    if (isGroup) "Contexto y roles del grupo (opcional)"
                                    else "Personalidad o rol del personaje (opcional)"
                                )
                            },
                            placeholder = {
                                Text(
                                    if (isGroup) "Ej. Grupo de amigos planeando un viaje, tono humorístico..."
                                    else "Ej. Amiga sarcástica que responde corto con emojis..."
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_ai_persona_input")
                        )

                        if (!globalSettings.isConfiguredAndEnabled) {
                            Text(
                                text = "Nota: Activa tu proveedor/clave en «Conexión a LLM externos» del menú derecho para que genere respuestas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
