package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AvatarPresets
import com.example.data.model.CallType
import com.example.data.model.FakeChat
import com.example.data.model.SenderRole
import com.example.data.model.UiState
import com.example.media.ParsedWhatsAppChat
import com.example.media.WhatsAppTxtParser
import com.example.ui.components.AvatarBadge
import com.example.ui.components.ChatEditorSheet
import com.example.ui.components.ContactAvatarPreviewDialog
import com.example.ui.components.ImportedContact
import com.example.ui.components.MessagingCloneSheet
import com.example.ui.components.TickStatusIcon
import com.example.ui.components.VerifiedIcon
import com.example.ui.components.WhatsAppImportDialog
import com.example.ui.components.readContactFromUri
import com.example.ui.viewmodel.ChatFilter
import com.example.ui.viewmodel.ChatsHomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: ChatsHomeViewModel,
    isGuestMode: Boolean = false,
    localModeLabel: String = "Local",
    sharedIntent: Intent? = null,
    onConsumeSharedIntent: () -> Unit = {},
    onOpenChat: (String) -> Unit,
    onStartFakeCall: (FakeChat, CallType) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var showSearchBar by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showMessagingCloneSheet by remember { mutableStateOf(false) }
    var importedContactDraft by remember { mutableStateOf<ImportedContact?>(null) }
    var parsedWhatsAppDraft by remember { mutableStateOf<ParsedWhatsAppChat?>(null) }
    var isParsingWhatsAppTxt by remember { mutableStateOf(false) }
    var previewingAvatarChat by remember { mutableStateOf<FakeChat?>(null) }
    var editingChatFromPreview by remember { mutableStateOf<FakeChat?>(null) }

    LaunchedEffect(sharedIntent) {
        val intent = sharedIntent ?: return@LaunchedEffect
        isParsingWhatsAppTxt = true
        val result = WhatsAppTxtParser.parseSharedIntent(context, intent)
        isParsingWhatsAppTxt = false
        onConsumeSharedIntent()
        if (result == null) {
            viewModel.emitStatusMessage("El contenido compartido no contiene mensajes compatibles (.txt, .zip o texto).")
        } else {
            result
                .onSuccess { parsed -> parsedWhatsAppDraft = parsed }
                .onFailure { err ->
                    viewModel.emitStatusMessage(
                        err.localizedMessage ?: "No se pudo leer el chat compartido"
                    )
                }
        }
    }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = readContactFromUri(context, uri)
                if (imported != null) {
                    importedContactDraft = imported
                    showCreateSheet = true
                } else {
                    viewModel.emitStatusMessage("No se pudo leer el contacto seleccionado")
                }
            }
        }
    }

    val pickWhatsAppTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isParsingWhatsAppTxt = true
                WhatsAppTxtParser.parseFromUri(context, uri)
                    .onSuccess { parsed -> parsedWhatsAppDraft = parsed }
                    .onFailure { err ->
                        viewModel.emitStatusMessage(
                            err.localizedMessage ?: "No se pudo leer el archivo .txt de WhatsApp"
                        )
                    }
                isParsingWhatsAppTxt = false
            }
        }
    }

    val launchPickContactOnly = {
        try {
            pickContactLauncher.launch(null)
        } catch (_: Exception) {
            viewModel.emitStatusMessage("No se encontró la app de Contactos en este dispositivo")
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchPickContactOnly()
    }

    val launchContactPicker = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchPickContactOnly()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val launchWhatsAppTxtPicker = {
        try {
            pickWhatsAppTxtLauncher.launch(arrayOf("text/plain", "application/zip", "*/*"))
        } catch (_: Exception) {
            viewModel.emitStatusMessage("No se pudo abrir el selector de archivos")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "MostazaChat",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isGuestMode) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = if (isGuestMode) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGuestMode) Icons.Rounded.PhoneAndroid else Icons.Rounded.CloudDone,
                                    contentDescription = if (isGuestMode) "Modo local ($localModeLabel)" else "Nube activa",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isGuestMode) localModeLabel else "Nube",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMessagingCloneSheet = true },
                        modifier = Modifier.testTag("import_whatsapp_txt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = "Clonar chat desde app de mensajería o archivo"
                        )
                    }
                    IconButton(
                        onClick = launchContactPicker,
                        modifier = Modifier.testTag("import_contact_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Contacts,
                            contentDescription = stringResource(R.string.cd_import_contact)
                        )
                    }
                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) viewModel.setSearchQuery("")
                        },
                        modifier = Modifier.testTag("search_chats_button")
                    ) {
                        Icon(
                            imageVector = if (showSearchBar) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    importedContactDraft = null
                    showCreateSheet = true
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = stringResource(R.string.cd_new_chat)
                    )
                },
                text = { Text("Nuevo chat") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter)
            ) {
                if (isBusy || isParsingWhatsAppTxt) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (showSearchBar) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Buscar contacto o mensaje...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_chats_input")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.labelEs) },
                            modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                        )
                    }
                }

                when (val state = chatsState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    is UiState.Success -> {
                        val filtered = remember(state.data, searchQuery, activeFilter) {
                            state.data.filter { chat ->
                                val matchesFilter = when (activeFilter) {
                                    ChatFilter.ALL -> true
                                    ChatFilter.CHATS -> !chat.groupChat
                                    ChatFilter.GROUPS -> chat.groupChat
                                    ChatFilter.PINNED -> chat.pinned
                                }
                                val q = searchQuery.trim()
                                val matchesSearch = q.isEmpty() ||
                                    chat.contactName.contains(q, ignoreCase = true) ||
                                    chat.lastMessagePreview.contains(q, ignoreCase = true)
                                matchesFilter && matchesSearch
                            }
                        }

                        if (state.data.isEmpty()) {
                            EmptyChatsState(
                                isBusy = isBusy || isParsingWhatsAppTxt,
                                isGuestMode = isGuestMode,
                                onCreateNew = {
                                    importedContactDraft = null
                                    showCreateSheet = true
                                },
                                onImportContact = launchContactPicker,
                                onCloneFromMessagingApp = { showMessagingCloneSheet = true },
                                onCreateSample = {
                                    viewModel.createSampleChat(onCreated = onOpenChat)
                                }
                            )
                        } else if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No hay conversaciones que coincidan con tu filtro.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered, key = { it.id }) { chat ->
                                    ChatListCard(
                                        chat = chat,
                                        onClick = { onOpenChat(chat.id) },
                                        onAvatarClick = { previewingAvatarChat = chat },
                                        onEditChat = { editingChatFromPreview = chat },
                                        onTogglePin = { viewModel.togglePinned(chat) },
                                        onDelete = { viewModel.deleteChat(chat.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        val draft = importedContactDraft
        ChatEditorSheet(
            title = if (draft != null) "Clonar contacto" else "Nueva conversación",
            confirmLabel = "Crear chat",
            initialContactName = draft?.displayName ?: "",
            initialAvatarEmoji = draft?.initials ?: "😎",
            initialAvatarColorHex = draft?.colorHex ?: AvatarPresets.colors.first(),
            initialAvatarImageBase64 = draft?.avatarImageBase64,
            isEditingExistingChat = false,
            myDefaultName = userProfile?.defaultMyName ?: "Yo",
            userDisplayName = userProfile?.displayName ?: "",
            allowContactImport = true,
            onContactImportError = { msg -> viewModel.emitStatusMessage(msg) },
            onDismiss = {
                showCreateSheet = false
                importedContactDraft = null
            },
            onSave = { name, isGroup, status, online, verified, emoji, color, avatarPhoto, _, wallpaper, unread, pinned, pendingTxt ->
                showCreateSheet = false
                importedContactDraft = null
                viewModel.createChat(
                    contactName = name,
                    groupChat = isGroup,
                    statusText = status,
                    online = online,
                    verified = verified,
                    avatarEmoji = emoji,
                    avatarColorHex = color,
                    avatarImageBase64 = avatarPhoto,
                    wallpaperTheme = wallpaper,
                    unreadCount = unread,
                    pinned = pinned,
                    pendingTxtImport = pendingTxt,
                    onCreated = onOpenChat
                )
            }
        )
    }

    parsedWhatsAppDraft?.let { parsed ->
        WhatsAppImportDialog(
            parsedChat = parsed,
            myDefaultName = userProfile?.defaultMyName ?: "Yo",
            userDisplayName = userProfile?.displayName ?: "",
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
    previewingAvatarChat?.let { targetChat ->
        ContactAvatarPreviewDialog(
            chat = targetChat,
            onDismiss = { previewingAvatarChat = null },
            onOpenChat = {
                previewingAvatarChat = null
                onOpenChat(targetChat.id)
            },
            onStartVoiceCall = {
                previewingAvatarChat = null
                onStartFakeCall(targetChat, CallType.VOICE)
            },
            onStartVideoCall = {
                previewingAvatarChat = null
                onStartFakeCall(targetChat, CallType.VIDEO)
            },
            onOpenInfo = {
                previewingAvatarChat = null
                editingChatFromPreview = targetChat
            }
        )
    }

    editingChatFromPreview?.let { editChat ->
        ChatEditorSheet(
            title = "Configurar contacto",
            confirmLabel = "Guardar cambios",
            initialContactName = editChat.contactName,
            initialGroupChat = editChat.groupChat,
            initialStatusText = editChat.statusText,
            initialOnline = editChat.online,
            initialVerified = editChat.verified,
            initialAvatarEmoji = editChat.avatarEmoji,
            initialAvatarColorHex = editChat.avatarColorHex,
            initialAvatarImageBase64 = editChat.avatarImageBase64,
            initialWallpaper = editChat.parsedWallpaper,
            initialUnreadCount = editChat.unreadCount,
            initialPinned = editChat.pinned,
            isEditingExistingChat = true,
            myDefaultName = userProfile?.defaultMyName ?: "Yo",
            userDisplayName = userProfile?.displayName ?: "",
            onContactImportError = { msg -> viewModel.emitStatusMessage(msg) },
            onDismiss = { editingChatFromPreview = null },
            onSave = { name, isGroup, status, online, verified, emoji, color, avatarPhoto, clearPhoto, wallpaper, unread, pinned, pendingTxt ->
                editingChatFromPreview = null
                viewModel.updateChatSettings(
                    chatId = editChat.id,
                    contactName = name,
                    groupChat = isGroup,
                    statusText = status,
                    online = online,
                    verified = verified,
                    avatarEmoji = emoji,
                    avatarColorHex = color,
                    avatarImageBase64 = avatarPhoto,
                    clearAvatarPhoto = clearPhoto,
                    wallpaperTheme = wallpaper,
                    unreadCount = unread,
                    pinned = pinned,
                    pendingTxtImport = pendingTxt,
                    onTxtImported = onOpenChat
                )
            }
        )
    }

    if (showMessagingCloneSheet) {
        MessagingCloneSheet(
            onDismiss = { showMessagingCloneSheet = false },
            onPickExportFiles = launchWhatsAppTxtPicker,
            onParsedConversation = { parsed ->
                showMessagingCloneSheet = false
                parsedWhatsAppDraft = parsed
            },
            onError = { msg -> viewModel.emitStatusMessage(msg) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListCard(
    chat: FakeChat,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onEditChat: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "Opciones de chat",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                }
            )
            .testTag("chat_item_${chat.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarBadge(
                emoji = chat.avatarEmoji,
                colorHex = chat.avatarColorHex,
                online = chat.online,
                size = 54.dp,
                imageBase64 = chat.avatarImageBase64,
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onAvatarClick,
                        onLongClickLabel = "Opciones de chat",
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    )
                    .testTag("chat_avatar_${chat.id}")
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = chat.contactName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chat.verified) {
                            VerifiedIcon(size = 16.dp)
                        }
                        if (chat.groupChat) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = "Grupo",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = chat.lastMessageTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (chat.parsedLastSender == SenderRole.ME) {
                            TickStatusIcon(tickStatus = chat.parsedLastTick, size = 15.dp)
                        }
                        Text(
                            text = chat.lastMessagePreview.ifBlank { chat.statusText },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (chat.pinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Fijado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        if (chat.unreadCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = chat.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Opciones de chat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (chat.pinned) "Desfijar" else "Fijar arriba") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.PushPin, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onTogglePin()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configurar contacto") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onEditChat()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar conversación") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
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
private fun EmptyChatsState(
    isBusy: Boolean,
    isGuestMode: Boolean,
    onCreateNew: () -> Unit,
    onImportContact: () -> Unit,
    onCloneFromMessagingApp: () -> Unit,
    onCreateSample: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_chatmock_hero),
                contentDescription = stringResource(R.string.cd_hero_illustration),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Tu estudio MostazaChat está listo",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isGuestMode) {
                "Clona contactos de tu agenda, importa chats desde WhatsApp/Telegram o créalos desde cero. Todo se guarda localmente en este teléfono."
            } else {
                "Clona contactos de tu agenda, importa chats desde WhatsApp/Telegram o créalos desde cero con sincronización en la nube."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onImportContact,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("empty_import_contact_button")
        ) {
            Icon(Icons.Rounded.Contacts, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Clonar desde tu agenda de contactos")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCloneFromMessagingApp,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("empty_clone_messaging_app_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Clonar chat desde app de mensajería")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCreateNew,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("empty_create_chat_button")
        ) {
            Icon(Icons.Rounded.AddComment, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Crear chat manualmente")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCreateSample,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("empty_sample_chat_button")
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Generar conversación de ejemplo")
        }
    }
}
