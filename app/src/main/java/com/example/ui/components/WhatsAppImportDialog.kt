package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.media.ParsedWhatsAppChat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsAppImportDialog(
    parsedChat: ParsedWhatsAppChat,
    myDefaultName: String,
    userDisplayName: String,
    onDismiss: () -> Unit,
    onConfirmImport: (chatTitle: String, myParticipantName: String?, isGroup: Boolean) -> Unit
) {
    val defaultMe = remember(parsedChat, myDefaultName, userDisplayName) {
        parsedChat.participants.firstOrNull { name ->
            name.equals(myDefaultName, ignoreCase = true) ||
                name.equals(userDisplayName, ignoreCase = true) ||
                name.equals("Yo", ignoreCase = true) ||
                name.equals("Tú", ignoreCase = true)
        } ?: if (parsedChat.participants.size == 2) parsedChat.participants.lastOrNull() else null
    }

    var selectedMe by remember(parsedChat) { mutableStateOf(defaultMe) }
    var isGroup by remember(parsedChat) { mutableStateOf(parsedChat.isLikelyGroup) }
    var chatTitle by remember(parsedChat, defaultMe) {
        val otherName = parsedChat.participants.firstOrNull { it != defaultMe }
        val initial = if (!parsedChat.isLikelyGroup && otherName != null && parsedChat.suggestedTitle == "Chat de WhatsApp") {
            otherName
        } else {
            parsedChat.suggestedTitle
        }
        mutableStateOf(initial.take(60))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Importar chat de WhatsApp (.txt)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${parsedChat.messages.size} mensajes detectados",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${parsedChat.participants.size} participantes encontrados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (parsedChat.wasTruncated) {
                            Text(
                                text = "Se importarán los últimos 2.000 mensajes para mantener la app ligera.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = chatTitle,
                    onValueChange = { if (it.length <= 60) chatTitle = it },
                    label = { Text("Nombre de la conversación") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_import_title_input")
                )

                if (parsedChat.participants.isNotEmpty()) {
                    Text(
                        text = "¿Quién eres tú en este archivo?",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Tus mensajes aparecerán a la derecha y los demás a la izquierda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        parsedChat.participants.take(8).forEach { participant ->
                            FilterChip(
                                selected = selectedMe == participant,
                                onClick = {
                                    selectedMe = participant
                                    if (!isGroup && parsedChat.participants.size == 2) {
                                        val other = parsedChat.participants.firstOrNull { it != participant }
                                        if (other != null && chatTitle == participant) {
                                            chatTitle = other
                                        }
                                    }
                                },
                                label = { Text(participant.take(20)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                        FilterChip(
                            selected = selectedMe == null,
                            onClick = { selectedMe = null },
                            label = { Text("Ninguno") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mostrar como grupo",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isGroup,
                        onCheckedChange = { isGroup = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = chatTitle.trim().ifBlank { "Chat importado" }
                    onConfirmImport(cleanTitle, selectedMe, isGroup)
                },
                enabled = chatTitle.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_whatsapp_import_button")
            ) {
                Text("Crear copia")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
