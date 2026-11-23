package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AvatarPresets
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.FakeCallRecord
import com.example.data.model.UiState
import com.example.ui.components.AvatarBadge
import com.example.ui.components.readContactFromUri
import com.example.ui.viewmodel.CallsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CallsTabScreen(
    viewModel: CallsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val scheduledSeconds by viewModel.scheduledSecondsLeft.collectAsStateWithLifecycle()

    var callerName by remember { mutableStateOf("Mamá") }
    var selectedEmoji by remember { mutableStateOf("❤️") }
    var selectedColor by remember { mutableStateOf(AvatarPresets.colors[2]) }
    var selectedPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var selectedCallType by remember { mutableStateOf(CallType.VOICE) }
    var selectedDelay by remember { mutableStateOf(0) }

    val pickCallerContact = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            val imported = readContactFromUri(context, uri)
            if (imported != null) {
                callerName = imported.displayName
                selectedEmoji = imported.initials
                selectedColor = imported.colorHex
                selectedPhotoBase64 = imported.avatarImageBase64
            } else {
                viewModel.emitStatusMessage("No se pudo leer el contacto seleccionado")
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Llamadas Simuladas",
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
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AvatarBadge(
                                    emoji = selectedEmoji,
                                    colorHex = selectedColor,
                                    online = true,
                                    size = 48.dp,
                                    imageBase64 = selectedPhotoBase64
                                )
                                Column {
                                    Text(
                                        text = "Lanzador de llamada falsa",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Haz que suene ahora o tras unos segundos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = callerName,
                                onValueChange = { if (it.length <= 60) callerName = it },
                                label = { Text("Nombre en pantalla") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("call_name_input")
                            )

                            OutlinedButton(
                                onClick = {
                                    try {
                                        pickCallerContact.launch(null)
                                    } catch (_: Exception) {
                                        viewModel.emitStatusMessage("No se encontró la app de Contactos en este dispositivo")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("call_import_contact_button")
                            ) {
                                Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Elegir contacto de la agenda")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AvatarPresets.emojis.take(8).forEach { emoji ->
                                    FilterChip(
                                        selected = selectedEmoji == emoji,
                                        onClick = { selectedEmoji = emoji },
                                        label = { Text(emoji) }
                                    )
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CallType.entries.forEach { type ->
                                    FilterChip(
                                        selected = selectedCallType == type,
                                        onClick = { selectedCallType = type },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (type == CallType.VOICE) Icons.Rounded.Call else Icons.Rounded.Videocam,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(type.labelEs) }
                                    )
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Ahora", 5 to "En 5s", 10 to "En 10s").forEach { (sec, label) ->
                                    FilterChip(
                                        selected = selectedDelay == sec,
                                        onClick = { selectedDelay = sec },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Timer, contentDescription = null, Modifier.size(16.dp))
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            if (scheduledSeconds != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Llamada entrante en ${scheduledSeconds}s...",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        OutlinedButton(onClick = { viewModel.cancelScheduledCall() }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Cancelar")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.launchFakeCall(
                                            contactName = callerName.trim().ifBlank { "Contacto" },
                                            avatarEmoji = selectedEmoji,
                                            avatarColorHex = selectedColor,
                                            avatarImageBase64 = selectedPhotoBase64,
                                            callType = selectedCallType,
                                            delaySeconds = selectedDelay
                                        )
                                    },
                                    enabled = callerName.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("launch_fake_call_button")
                                ) {
                                    Icon(Icons.Rounded.AddIcCall, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (selectedDelay == 0) "Iniciar llamada falsa"
                                        else "Programar en ${selectedDelay}s"
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Historial sincronizado en la nube",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                when (val state = callsState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Aún no hay llamadas simuladas registradas. Inicia una arriba para probar la pantalla de llamada.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(state.data, key = { it.id }) { call ->
                                FakeCallRowCard(
                                    call = call,
                                    onReplay = {
                                        viewModel.launchFakeCall(
                                            contactName = call.contactName,
                                            avatarEmoji = call.avatarEmoji,
                                            avatarColorHex = call.avatarColorHex,
                                            avatarImageBase64 = call.avatarImageBase64,
                                            callType = call.parsedCallType,
                                            delaySeconds = 0
                                        )
                                    },
                                    onDelete = { viewModel.deleteCallRecord(call.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FakeCallRowCard(
    call: FakeCallRecord,
    onReplay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarBadge(
                emoji = call.avatarEmoji,
                colorHex = call.avatarColorHex,
                online = false,
                size = 46.dp,
                imageBase64 = call.avatarImageBase64
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val dirIcon = when (call.parsedDirection) {
                        CallDirection.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived
                        CallDirection.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade
                        CallDirection.MISSED -> Icons.AutoMirrored.Rounded.CallMissed
                    }
                    val dirTint = if (call.parsedDirection == CallDirection.MISSED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = dirIcon,
                        contentDescription = null,
                        tint = dirTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${call.displayTime} • ${call.durationText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onReplay) {
                Icon(
                    imageVector = if (call.parsedCallType == CallType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Repetir llamada",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar registro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
