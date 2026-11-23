package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.media.ParsedWhatsAppChat
import com.example.media.WhatsAppTxtParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingCloneSheet(
    onDismiss: () -> Unit,
    onPickExportFiles: () -> Unit,
    onParsedConversation: (ParsedWhatsAppChat) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pastedText by remember { mutableStateOf("") }
    var optionalTitle by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Message,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = "Clonar desde app de mensajería",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "WhatsApp, WhatsApp Business, Telegram o SMS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    onPickExportFiles()
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("clone_pick_export_file_button")
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Elegir archivo exportado (.txt o .zip)")
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Exportar directo desde WhatsApp",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1. Abre un chat en WhatsApp.\n2. Pulsa ⋮ → Más → Exportar chat.\n3. Elige MostazaChat en la hoja de compartir para clonarlo al instante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val pm = context.packageManager
                            val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
                                ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
                                ?: pm.getLaunchIntentForPackage("org.telegram.messenger")
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                                onDismiss()
                            } else {
                                onError("No se encontró WhatsApp ni Telegram instalado en este dispositivo")
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("clone_open_whatsapp_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir WhatsApp para exportar")
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "O pega una conversación copiada",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Copia mensajes en WhatsApp, Telegram o notas (con fecha o formato «Nombre: mensaje») y pégalos abajo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipText = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                    if (clipText.isBlank()) {
                        onError("El portapapeles está vacío")
                    } else {
                        pastedText = clipText
                    }
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("clone_paste_clipboard_button")
            ) {
                Icon(Icons.Rounded.ContentPasteGo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pegar del portapapeles")
            }

            OutlinedTextField(
                value = optionalTitle,
                onValueChange = { if (it.length <= 60) optionalTitle = it },
                label = { Text("Nombre del chat (opcional)") },
                placeholder = { Text("Ej. Sofía, Grupo Trabajo") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clone_paste_title_input")
            )

            OutlinedTextField(
                value = pastedText,
                onValueChange = { pastedText = it },
                label = { Text("Conversación pegada") },
                placeholder = {
                    Text("Ej.\n12/05/24 10:15 - Sofía: ¡Hola!\n12/05/24 10:16 - Yo: ¿Qué tal?")
                },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clone_paste_text_input")
            )

            Button(
                onClick = {
                    WhatsAppTxtParser.parseConversationText(pastedText, optionalTitle.trim())
                        .onSuccess { parsed ->
                            onParsedConversation(parsed)
                        }
                        .onFailure { err ->
                            onError(
                                err.localizedMessage
                                    ?: "No se detectaron mensajes válidos. Usa formato WhatsApp, Telegram o «Nombre: mensaje»."
                            )
                        }
                },
                enabled = pastedText.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("clone_confirm_paste_button")
            ) {
                Text("Importar texto pegado", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
