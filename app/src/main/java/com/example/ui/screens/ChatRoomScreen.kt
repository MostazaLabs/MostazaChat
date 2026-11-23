package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AvatarPresets
import com.example.data.model.CallType
import com.example.data.model.FakeChat
import com.example.data.model.FakeMessage
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.data.model.TickStatus
import com.example.data.model.UiState
import com.example.data.model.WallpaperTheme
import com.example.media.MediaImportHelper
import com.example.ui.components.AvatarBadge
import com.example.ui.components.ChatEditorSheet
import com.example.ui.components.ContactAvatarPreviewDialog
import com.example.ui.components.GalleryAccessConsentDialog
import com.example.ui.components.TickStatusIcon
import com.example.ui.components.VerifiedIcon
import com.example.ui.theme.BubbleMeDark
import com.example.ui.theme.BubbleMeLight
import com.example.ui.theme.BubbleOtherDark
import com.example.ui.theme.BubbleOtherLight
import com.example.ui.viewmodel.ChatRoomViewModel
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    viewModel: ChatRoomViewModel,
    myDefaultName: String,
    onBack: () -> Unit,
    onStartFakeCall: (FakeChat, CallType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val composerRole by viewModel.composerRole.collectAsStateWithLifecycle()
    val composerTick by viewModel.composerTick.collectAsStateWithLifecycle()
    val customTime by viewModel.customTime.collectAsStateWithLifecycle()
    val customDateLabel by viewModel.customDateLabel.collectAsStateWithLifecycle()
    val customGroupSender by viewModel.customGroupSenderName.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val screenshotMode by viewModel.screenshotMode.collectAsStateWithLifecycle()
    val isSimulatingTyping by viewModel.isSimulatingTyping.collectAsStateWithLifecycle()
    val isImportingMedia by viewModel.isImportingMedia.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showChatSettings by remember { mutableStateOf(false) }
    var showContactAvatarPreview by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<FakeMessage?>(null) }
    var viewingMediaMessage by remember { mutableStateOf<FakeMessage?>(null) }
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var showGalleryConsent by remember { mutableStateOf(false) }
    var showMediaSearchSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showDelayedReplyDialog by remember { mutableStateOf(false) }

    val fallbackMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingMediaUri = uri
        }
    }

    val mediaChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = MediaImportHelper.extractPickedUri(result.data)
            if (uri != null) {
                pendingMediaUri = uri
            }
        }
    }

    val launchChatMediaPicker = {
        try {
            mediaChooserLauncher.launch(
                MediaImportHelper.createMediaChooserIntent(
                    allowVideo = true,
                    chooserTitle = "Elegir foto, GIF o vídeo"
                )
            )
        } catch (_: Exception) {
            try {
                fallbackMediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            } catch (_: Exception) {
            }
        }
    }

    val chat = (chatState as? UiState.Success)?.data
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    val wallpaperTheme = chat?.parsedWallpaper ?: WallpaperTheme.CLASSIC
    val isDarkWallpaper = wallpaperTheme == WallpaperTheme.MIDNIGHT || isSystemInDarkTheme()
    val bgColors = wallpaperColors(wallpaperTheme, isDarkWallpaper)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                title = {
                    if (chat != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { showContactAvatarPreview = true }
                                    .testTag("chat_header_avatar_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarBadge(
                                    emoji = chat.avatarEmoji,
                                    colorHex = chat.avatarColorHex,
                                    online = chat.online,
                                    size = 40.dp,
                                    imageBase64 = chat.avatarImageBase64
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { if (!screenshotMode) showChatSettings = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = chat.contactName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (chat.verified) {
                                        VerifiedIcon(size = 15.dp)
                                    }
                                }
                                Text(
                                    text = if (isSimulatingTyping) "escribiendo..." else chat.statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSimulatingTyping) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text("Cargando...")
                    }
                },
                actions = {
                    if (chat != null) {
                        IconButton(
                            onClick = { onStartFakeCall(chat, CallType.VIDEO) },
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = stringResource(R.string.cd_video_call)
                            )
                        }
                        IconButton(
                            onClick = { onStartFakeCall(chat, CallType.VOICE) },
                            modifier = Modifier.testTag("chat_voice_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = stringResource(R.string.cd_voice_call)
                            )
                        }
                        if (!screenshotMode) {
                            IconButton(
                                onClick = { showMediaSearchSheet = true },
                                modifier = Modifier.testTag("chat_search_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.cd_chat_media_search)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showTopMenu = true },
                                    modifier = Modifier.testTag("chat_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Más opciones"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Archivos y galería") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                                        },
                                        onClick = {
                                            showTopMenu = false
                                            showMediaSearchSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Configurar chat y perfil") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Settings, contentDescription = null)
                                        },
                                        onClick = {
                                            showTopMenu = false
                                            showChatSettings = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Modo captura") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                                        },
                                        onClick = {
                                            showTopMenu = false
                                            viewModel.toggleScreenshotMode()
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Brush.verticalGradient(bgColors))
                .drawBehind {
                    val dotColor = if (isDarkWallpaper) {
                        Color.White.copy(alpha = 0.04f)
                    } else {
                        Color.Black.copy(alpha = 0.04f)
                    }
                    val step = 52f
                    var x = 24f
                    while (x < size.width) {
                        var y = 24f
                        while (y < size.height) {
                            drawCircle(color = dotColor, radius = 2.4f, center = Offset(x, y))
                            y += step
                        }
                        x += step
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isImportingMedia) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (errorBanner != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = errorBanner ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearErrorBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (messagesState is UiState.Loading || chatState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = 14.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                                val prevDate = if (index > 0) messages[index - 1].dateLabel else null
                                if (msg.dateLabel.isNotBlank() && msg.dateLabel != prevDate) {
                                    DateDividerPill(
                                        label = msg.dateLabel,
                                        isDark = isDarkWallpaper
                                    )
                                }

                                MessageBubbleItem(
                                    message = msg,
                                    isGroup = chat?.groupChat == true,
                                    isDarkWallpaper = isDarkWallpaper,
                                    screenshotMode = screenshotMode,
                                    onTap = {
                                        if (!screenshotMode) editingMessage = msg
                                    },
                                    onOpenMedia = {
                                        viewingMediaMessage = msg
                                    }
                                )
                            }

                            if (isSimulatingTyping) {
                                item(key = "typing_indicator") {
                                    TypingIndicatorBubble(isDark = isDarkWallpaper)
                                }
                            }
                        }
                    }

                    if (screenshotMode) {
                        SmallFloatingActionButton(
                            onClick = { viewModel.toggleScreenshotMode() },
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .testTag("exit_screenshot_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = "Salir de Modo Captura"
                            )
                        }
                    }
                }

                // Bottom Composer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (replyTarget != null) {
                            val target = replyTarget
                            if (target != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Reply,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Respondiendo a: ${target.text}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.setReplyTarget(null) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Cancelar respuesta")
                                        }
                                    }
                                }
                            }
                        }

                        if (!screenshotMode) {
                            // Dual Role Switcher + Studio Tools
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = composerRole == SenderRole.ME,
                                    onClick = { viewModel.setComposerRole(SenderRole.ME) },
                                    label = { Text("Tú (Envía)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("role_me_chip")
                                )
                                FilterChip(
                                    selected = composerRole == SenderRole.OTHER,
                                    onClick = { viewModel.setComposerRole(SenderRole.OTHER) },
                                    label = { Text(chat?.contactName?.take(14) ?: "Contacto") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    modifier = Modifier.testTag("role_other_chip")
                                )
                                AssistChip(
                                    onClick = { showTimeDialog = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Schedule, contentDescription = null, Modifier.size(16.dp))
                                    },
                                    label = {
                                        Text(customTime.ifBlank { "Hora auto" })
                                    }
                                )
                                if (composerRole == SenderRole.ME) {
                                    AssistChip(
                                        onClick = {
                                            val next = when (composerTick) {
                                                TickStatus.SENT -> TickStatus.DELIVERED
                                                TickStatus.DELIVERED -> TickStatus.READ
                                                TickStatus.READ -> TickStatus.SENT
                                            }
                                            viewModel.setComposerTick(next)
                                        },
                                        leadingIcon = {
                                            TickStatusIcon(tickStatus = composerTick, size = 16.dp)
                                        },
                                        label = {
                                            Text(
                                                when (composerTick) {
                                                    TickStatus.SENT -> "1 check"
                                                    TickStatus.DELIVERED -> "2 checks"
                                                    TickStatus.READ -> "Leído"
                                                }
                                            )
                                        }
                                    )
                                }
                                AssistChip(
                                    onClick = { showVoiceDialog = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Mic, contentDescription = null, Modifier.size(16.dp))
                                    },
                                    label = { Text("Audio") }
                                )
                                AssistChip(
                                    onClick = { showDelayedReplyDialog = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, Modifier.size(16.dp))
                                    },
                                    label = { Text("Auto 3s") }
                                )
                            }

                            if (chat?.groupChat == true && composerRole == SenderRole.OTHER) {
                                OutlinedTextField(
                                    value = customGroupSender,
                                    onValueChange = { if (it.length <= 40) viewModel.setCustomGroupSenderName(it) },
                                    label = { Text("Miembro del grupo que habla") },
                                    placeholder = { Text("Ej. Carlos, Ana...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!screenshotMode) {
                                IconButton(
                                    onClick = {
                                        if (MediaImportHelper.hasGalleryAccessConsent(context)) {
                                            launchChatMediaPicker()
                                        } else {
                                            showGalleryConsent = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("attach_media_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = stringResource(R.string.cd_attach_media),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { if (it.length <= 2000) inputText = it },
                                placeholder = {
                                    Text(
                                        if (screenshotMode) "Mensaje"
                                        else if (composerRole == SenderRole.ME) "Escribe como Tú..."
                                        else "Escribe como ${chat?.contactName ?: "Contacto"}..."
                                    )
                                },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_input")
                            )

                            Surface(
                                shape = CircleShape,
                                color = if (composerRole == SenderRole.OTHER && !screenshotMode) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = Color.White,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = inputText.isNotBlank()) {
                                        viewModel.sendMessage(
                                            text = inputText,
                                            messageType = MessageType.TEXT,
                                            myDefaultName = myDefaultName,
                                            contactName = chat?.contactName ?: "Contacto",
                                            isGroup = chat?.groupChat == true
                                        )
                                        inputText = ""
                                    }
                                    .testTag("send_message_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = stringResource(R.string.cd_send_message)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChatSettings && chat != null) {
        ChatEditorSheet(
            title = "Configurar chat",
            confirmLabel = "Guardar cambios",
            initialContactName = chat.contactName,
            initialGroupChat = chat.groupChat,
            initialStatusText = chat.statusText,
            initialOnline = chat.online,
            initialVerified = chat.verified,
            initialAvatarEmoji = chat.avatarEmoji,
            initialAvatarColorHex = chat.avatarColorHex,
            initialAvatarImageBase64 = chat.avatarImageBase64,
            initialWallpaper = chat.parsedWallpaper,
            initialUnreadCount = chat.unreadCount,
            initialPinned = chat.pinned,
            isEditingExistingChat = true,
            myDefaultName = myDefaultName,
            onContactImportError = { msg -> viewModel.emitError(msg) },
            onDismiss = { showChatSettings = false },
            onSave = { name, isGroup, status, online, verified, emoji, color, avatarPhoto, clearPhoto, wallpaper, unread, pinned, pendingTxt ->
                showChatSettings = false
                viewModel.updateChatSettings(
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
                    pendingTxtImport = pendingTxt
                )
            }
        )
    }

    if (showContactAvatarPreview && chat != null) {
        ContactAvatarPreviewDialog(
            chat = chat,
            onDismiss = { showContactAvatarPreview = false },
            onOpenChat = { showContactAvatarPreview = false },
            onStartVoiceCall = {
                showContactAvatarPreview = false
                onStartFakeCall(chat, CallType.VOICE)
            },
            onStartVideoCall = {
                showContactAvatarPreview = false
                onStartFakeCall(chat, CallType.VIDEO)
            },
            onOpenInfo = {
                showContactAvatarPreview = false
                if (!screenshotMode) showChatSettings = true
            }
        )
    }

    if (showGalleryConsent) {
        GalleryAccessConsentDialog(
            purposeText = "MostazaChat abrirá el selector seguro de Android para que elijas únicamente las fotos o vídeos que quieras añadir al chat.",
            onConfirm = {
                MediaImportHelper.setGalleryAccessConsentGranted(context)
                showGalleryConsent = false
                launchChatMediaPicker()
            },
            onDismiss = { showGalleryConsent = false }
        )
    }

    pendingMediaUri?.let { uri ->
        MediaComposerDialog(
            uri = uri,
            initialRole = composerRole,
            contactName = chat?.contactName ?: "Contacto",
            myDefaultName = myDefaultName,
            onDismiss = { pendingMediaUri = null },
            onSend = { caption, chosenRole ->
                pendingMediaUri = null
                viewModel.sendMediaMessage(
                    context = context,
                    uri = uri,
                    caption = caption,
                    roleOverride = chosenRole,
                    myDefaultName = myDefaultName,
                    contactName = chat?.contactName ?: "Contacto",
                    isGroup = chat?.groupChat == true
                )
            }
        )
    }

    viewingMediaMessage?.let { mediaMsg ->
        MediaViewerDialog(
            message = mediaMsg,
            onDismiss = { viewingMediaMessage = null },
            onEditMessage = { editingMessage = mediaMsg }
        )
    }

    if (showMediaSearchSheet) {
        ChatMediaAndSearchSheet(
            messages = messages,
            myDefaultName = myDefaultName,
            contactName = chat?.contactName ?: "Contacto",
            onDismiss = { showMediaSearchSheet = false },
            onOpenMedia = { msg ->
                showMediaSearchSheet = false
                viewingMediaMessage = msg
            },
            onJumpToMessage = { index ->
                showMediaSearchSheet = false
                scope.launch {
                    listState.scrollToItem(index)
                }
            }
        )
    }

    editingMessage?.let { msg ->
        MessageStudioSheet(
            message = msg,
            contactName = chat?.contactName ?: "Contacto",
            myDefaultName = myDefaultName,
            onDismiss = { editingMessage = null },
            onReply = {
                editingMessage = null
                viewModel.setReplyTarget(msg)
            },
            onSave = { newText, newTime, newDate, newRole, newSender, newTick, newReaction, mediaUri, clearMedia ->
                editingMessage = null
                viewModel.updateMessage(
                    message = msg,
                    newText = newText,
                    newTime = newTime,
                    newDateLabel = newDate,
                    newRole = newRole,
                    newSenderName = newSender,
                    newTick = newTick,
                    newReaction = newReaction,
                    context = context,
                    replacementMediaUri = mediaUri,
                    clearMedia = clearMedia
                )
            },
            onDelete = {
                editingMessage = null
                viewModel.deleteMessage(msg.id)
            }
        )
    }

    if (showVoiceDialog) {
        VoiceNoteDialog(
            onDismiss = { showVoiceDialog = false },
            onConfirm = { duration, caption ->
                showVoiceDialog = false
                viewModel.sendMessage(
                    text = caption.ifBlank { "Nota de voz simulada" },
                    messageType = MessageType.VOICE,
                    voiceDuration = duration.ifBlank { "0:14" },
                    myDefaultName = myDefaultName,
                    contactName = chat?.contactName ?: "Contacto",
                    isGroup = chat?.groupChat == true
                )
            }
        )
    }

    if (showTimeDialog) {
        TimeDateDialog(
            initialTime = customTime,
            initialDateLabel = customDateLabel,
            onDismiss = { showTimeDialog = false },
            onConfirm = { newTime, newDate ->
                showTimeDialog = false
                viewModel.setCustomTime(newTime)
                viewModel.setCustomDateLabel(newDate)
            }
        )
    }

    if (showDelayedReplyDialog) {
        DelayedReplyDialog(
            contactName = chat?.contactName ?: "Contacto",
            onDismiss = { showDelayedReplyDialog = false },
            onSchedule = { replyText, seconds ->
                showDelayedReplyDialog = false
                viewModel.scheduleDelayedContactReply(
                    replyText = replyText,
                    delaySeconds = seconds,
                    contactName = chat?.contactName ?: "Contacto"
                )
            }
        )
    }
}

@Composable
private fun DateDividerPill(label: String, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Color(0xFF1F2C34) else Color(0xFFE2EDE9),
            contentColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: FakeMessage,
    isGroup: Boolean,
    isDarkWallpaper: Boolean,
    screenshotMode: Boolean,
    onTap: () -> Unit,
    onOpenMedia: () -> Unit
) {
    val role = message.parsedSenderRole
    if (role == SenderRole.SYSTEM || message.parsedMessageType == MessageType.SYSTEM) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                modifier = Modifier.clickable(enabled = !screenshotMode, onClick = onTap)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    val isMe = role == SenderRole.ME
    val bubbleColor = when {
        isMe && isDarkWallpaper -> BubbleMeDark
        isMe -> BubbleMeLight
        isDarkWallpaper -> BubbleOtherDark
        else -> BubbleOtherLight
    }
    val textColor = if (isDarkWallpaper) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val subTextColor = if (isDarkWallpaper) Color(0xFF94A3B8) else Color(0xFF475569)
    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .padding(bottom = if (message.reactionEmoji != null) 12.dp else 0.dp)
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                shadowElevation = 1.dp,
                modifier = Modifier.combinedClickable(
                    enabled = !screenshotMode || message.parsedMessageType == MessageType.IMAGE || message.parsedMessageType == MessageType.VIDEO,
                    onClick = {
                        when (message.parsedMessageType) {
                            MessageType.IMAGE, MessageType.VIDEO -> onOpenMedia()
                            else -> if (!screenshotMode) onTap()
                        }
                    },
                    onLongClick = { if (!screenshotMode) onTap() }
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isMe && isGroup && message.senderName.isNotBlank()) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!message.replyToText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDarkWallpaper) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = message.replyToSender ?: "Mensaje",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = message.replyToText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    when (message.parsedMessageType) {
                        MessageType.VOICE -> {
                            VoiceMessageContent(
                                duration = message.voiceDuration ?: "0:14",
                                textColor = textColor,
                                subTextColor = subTextColor
                            )
                        }
                        MessageType.IMAGE, MessageType.VIDEO -> {
                            MediaBubbleContent(
                                message = message,
                                textColor = textColor
                            )
                        }
                        else -> {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = message.displayTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor
                        )
                        if (isMe) {
                            TickStatusIcon(tickStatus = message.parsedTickStatus, size = 14.dp)
                        }
                    }
                }
            }

            if (!message.reactionEmoji.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(if (isMe) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(horizontal = 6.dp)
                        .clickable(enabled = !screenshotMode, onClick = onTap)
                ) {
                    Text(
                        text = message.reactionEmoji,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceMessageContent(
    duration: String,
    textColor: Color,
    subTextColor: Color
) {
    var playing by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable { playing = !playing }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir audio simulado",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.75f),
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.labelMedium,
            color = subTextColor
        )
    }
}

@Composable
private fun MediaBubbleContent(
    message: FakeMessage,
    textColor: Color
) {
    val isVideo = message.parsedMessageType == MessageType.VIDEO
    val isGif = !isVideo && MediaImportHelper.isGifFile(null, message.mediaFileName, message.localMediaPath)
    val localExists = remember(message.localMediaPath) {
        val path = message.localMediaPath
        !path.isNullOrBlank() && File(path).exists()
    }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = MediaImportHelper.decodeBase64ToImageBitmap(message.mediaPreviewBase64),
        message.id,
        message.localMediaPath,
        message.mediaPreviewBase64
    ) {
        if (!isVideo && localExists) {
            val loaded = MediaImportHelper.loadLocalImageBitmap(message.localMediaPath)
            if (loaded != null) value = loaded
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .width(232.dp)
                .heightIn(min = 148.dp, max = 216.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            val img = bitmap
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = message.mediaFileName ?: message.text,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(44.dp)
                )
            }

            if (isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Reproducir vídeo",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(13.dp))
                        Text(
                            text = message.mediaDuration ?: "0:05",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else if (isGif) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "GIF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (message.text.isNotBlank() && message.text != "📷 Foto" && message.text != "🎬 Vídeo" && message.text != "🎞️ GIF") {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble(isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) BubbleOtherDark else BubbleOtherLight,
        shadowElevation = 1.dp
    ) {
        Text(
            text = "escribiendo...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun isOmittedMediaPlaceholder(rawText: String): Boolean {
    val clean = rawText
        .replace(Regex("[\u200E\u200F\u202A-\u202E\uFEFF]"), "")
        .trim()
        .lowercase(Locale.ROOT)
    return clean.contains("multimedia omitido") ||
        clean.contains("media omitted") ||
        clean.contains("imagen omitida") ||
        clean.contains("image omitted") ||
        clean.contains("gif omitido") ||
        clean.contains("sticker omitido") ||
        clean.contains("vídeo omitido") ||
        clean.contains("video omitido") ||
        clean.contains("audio omitido")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageStudioSheet(
    message: FakeMessage,
    contactName: String,
    myDefaultName: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onSave: (
        text: String,
        time: String,
        dateLabel: String,
        role: SenderRole,
        senderName: String,
        tick: TickStatus,
        reaction: String?,
        replacementMediaUri: Uri?,
        clearMedia: Boolean
    ) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isOmittedPlaceholder = remember(message.id, message.text) {
        isOmittedMediaPlaceholder(message.text)
    }
    val hasExistingMedia = remember(message.id, message.messageType) {
        message.parsedMessageType == MessageType.IMAGE || message.parsedMessageType == MessageType.VIDEO
    }

    var text by remember(message.id) { mutableStateOf(message.text) }
    var displayTime by remember(message.id) { mutableStateOf(message.displayTime) }
    var dateLabel by remember(message.id) { mutableStateOf(message.dateLabel) }
    var role by remember(message.id) { mutableStateOf(message.parsedSenderRole) }
    var senderName by remember(message.id) { mutableStateOf(message.senderName) }
    var tick by remember(message.id) { mutableStateOf(message.parsedTickStatus) }
    var reaction by remember(message.id) { mutableStateOf(message.reactionEmoji) }
    var pendingMediaUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var clearExistingMedia by remember(message.id) { mutableStateOf(false) }
    var showMediaConsent by remember { mutableStateOf(false) }

    val fallbackMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingMediaUri = uri
            clearExistingMedia = false
            if (isOmittedMediaPlaceholder(text) || text == "📷 Foto" || text == "🎞️ GIF" || text == "🎬 Vídeo") {
                text = ""
            }
        }
    }

    val mediaChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = MediaImportHelper.extractPickedUri(result.data)
            if (uri != null) {
                pendingMediaUri = uri
                clearExistingMedia = false
                if (isOmittedMediaPlaceholder(text) || text == "📷 Foto" || text == "🎞️ GIF" || text == "🎬 Vídeo") {
                    text = ""
                }
            }
        }
    }

    val openGallerySafely = {
        try {
            mediaChooserLauncher.launch(
                MediaImportHelper.createMediaChooserIntent(
                    allowVideo = true,
                    chooserTitle = "Reemplazar con foto, GIF o vídeo"
                )
            )
        } catch (_: Exception) {
            try {
                fallbackMediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            } catch (_: Exception) {
            }
        }
    }

    val selectedIsVideo = remember(pendingMediaUri) {
        val uri = pendingMediaUri ?: return@remember false
        MediaImportHelper.isVideoUri(context, uri)
    }

    val selectedIsGif by produceState(initialValue = false, pendingMediaUri, selectedIsVideo) {
        val uri = pendingMediaUri
        value = if (uri == null || selectedIsVideo) false else withContext(Dispatchers.IO) {
            MediaImportHelper.isGifUri(context, uri)
        }
    }

    val selectedPreviewBitmap by produceState<ImageBitmap?>(initialValue = null, pendingMediaUri, selectedIsVideo) {
        val uri = pendingMediaUri
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            try {
                if (selectedIsVideo) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val frame = retriever.getFrameAtTime(400_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                        frame?.asImageBitmap()
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val existingPreviewBitmap by produceState<ImageBitmap?>(
        initialValue = if (hasExistingMedia) MediaImportHelper.decodeBase64ToImageBitmap(message.mediaPreviewBase64) else null,
        message.id,
        hasExistingMedia,
        clearExistingMedia
    ) {
        if (hasExistingMedia && !clearExistingMedia && message.parsedMessageType == MessageType.IMAGE && !message.localMediaPath.isNullOrBlank()) {
            val loaded = MediaImportHelper.loadLocalImageBitmap(message.localMediaPath)
            if (loaded != null) value = loaded
        }
    }

    val willHaveMedia = pendingMediaUri != null || (hasExistingMedia && !clearExistingMedia)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Editar mensaje", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(onClick = onReply) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Citar")
                }
            }

            if (pendingMediaUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = selectedPreviewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Vista previa seleccionada",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = if (selectedIsVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            if (selectedIsGif) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.72f),
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = "GIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else if (selectedIsVideo) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    selectedIsVideo -> "Vídeo seleccionado"
                                    selectedIsGif -> "GIF seleccionado"
                                    else -> "Foto seleccionada"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Se reemplazará al pulsar «Guardar».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                pendingMediaUri = null
                                if (isOmittedPlaceholder && text.isBlank()) {
                                    text = message.text
                                }
                            }
                        ) {
                            Text("Quitar")
                        }
                    }
                }
            } else if (hasExistingMedia && !clearExistingMedia) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = existingPreviewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Multimedia actual",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = if (message.parsedMessageType == MessageType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (MediaImportHelper.isGifFile(null, message.mediaFileName, message.localMediaPath)) {
                                    "GIF adjunto"
                                } else if (message.parsedMessageType == MessageType.VIDEO) {
                                    "Vídeo adjunto"
                                } else {
                                    "Foto adjunta"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Puedes cambiarlo o quitarlo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                clearExistingMedia = true
                                if (text == "📷 Foto" || text == "🎞️ GIF" || text == "🎬 Vídeo") {
                                    text = "<Multimedia omitido>"
                                }
                            }
                        ) {
                            Text("Quitar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (isOmittedPlaceholder && !willHaveMedia) {
                FilledTonalButton(
                    onClick = {
                        if (MediaImportHelper.hasGalleryAccessConsent(context)) {
                            openGallerySafely()
                        } else {
                            showMediaConsent = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("replace_omitted_media_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reemplazar por foto, GIF o vídeo")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (MediaImportHelper.hasGalleryAccessConsent(context)) {
                            openGallerySafely()
                        } else {
                            showMediaConsent = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("attach_edit_media_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (willHaveMedia) "Cambiar foto, GIF o vídeo"
                        else "Reemplazar por foto, GIF o vídeo"
                    )
                }
            }

            Text("Reacción rápida", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarPresets.quickReactions.forEach { emoji ->
                    val selected = reaction == emoji
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                reaction = if (selected) null else emoji
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                label = {
                    Text(
                        if (willHaveMedia) "Comentario / pie de foto (opcional)"
                        else "Contenido del mensaje"
                    )
                },
                placeholder = {
                    if (willHaveMedia) Text("Añade un texto debajo de la foto o GIF...")
                },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayTime,
                    onValueChange = { if (it.length <= 24) displayTime = it },
                    label = { Text("Hora") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = { if (it.length <= 40) dateLabel = it },
                    label = { Text("Cabecera fecha") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Autor del mensaje", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = role == SenderRole.ME,
                    onClick = {
                        role = SenderRole.ME
                        senderName = myDefaultName
                    },
                    label = { Text("Tú (Enviado)") }
                )
                FilterChip(
                    selected = role == SenderRole.OTHER,
                    onClick = {
                        role = SenderRole.OTHER
                        if (senderName == myDefaultName || senderName.isBlank()) {
                            senderName = contactName
                        }
                    },
                    label = { Text(contactName) }
                )
                FilterChip(
                    selected = role == SenderRole.SYSTEM,
                    onClick = { role = SenderRole.SYSTEM },
                    label = { Text("Sistema") }
                )
            }

            if (role == SenderRole.ME) {
                Text("Estado de lectura", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TickStatus.entries.forEach { t ->
                        FilterChip(
                            selected = tick == t,
                            onClick = { tick = t },
                            label = { Text(t.labelEs) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        onSave(
                            text,
                            displayTime,
                            dateLabel,
                            role,
                            senderName,
                            tick,
                            reaction,
                            pendingMediaUri,
                            clearExistingMedia
                        )
                    },
                    enabled = text.isNotBlank() || willHaveMedia,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Guardar")
                }
            }
        }
    }

    if (showMediaConsent) {
        GalleryAccessConsentDialog(
            purposeText = "MostazaChat abrirá el selector seguro de Android para que elijas únicamente la foto, GIF o vídeo con el que reemplazar este mensaje.",
            onConfirm = {
                MediaImportHelper.setGalleryAccessConsentGranted(context)
                showMediaConsent = false
                openGallerySafely()
            },
            onDismiss = { showMediaConsent = false }
        )
    }
}

@Composable
private fun VoiceNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (duration: String, caption: String) -> Unit
) {
    var duration by remember { mutableStateOf("0:18") }
    var caption by remember { mutableStateOf("Nota de voz") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insertar nota de voz simulada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { if (it.length <= 16) duration = it },
                    label = { Text("Duración (ej. 0:24)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 100) caption = it },
                    label = { Text("Vista previa en lista") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(duration, caption) }) {
                Text("Insertar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun TimeDateDialog(
    initialTime: String,
    initialDateLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var time by remember { mutableStateOf(initialTime) }
    var dateLabel by remember { mutableStateOf(initialDateLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hora y fecha simuladas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = time,
                    onValueChange = { if (it.length <= 24) time = it },
                    label = { Text("Hora (vacío = hora real)") },
                    placeholder = { Text("Ej. 23:59") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = { if (it.length <= 40) dateLabel = it },
                    label = { Text("Etiqueta de fecha") },
                    placeholder = { Text("HOY, AYER, 12 nov...") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(time, dateLabel) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DelayedReplyDialog(
    contactName: String,
    onDismiss: () -> Unit,
    onSchedule: (replyText: String, seconds: Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Respuesta automática de $contactName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Mostrará 'escribiendo...' y enviará el mensaje automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    label = { Text("Mensaje que responderá") },
                    placeholder = { Text("¡Voy saliendo para allá!") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5).forEach { sec ->
                        FilterChip(
                            selected = seconds == sec,
                            onClick = { seconds = sec },
                            label = { Text("${sec}s") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSchedule(text, seconds) },
                enabled = text.isNotBlank()
            ) {
                Text("Programar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun wallpaperColors(theme: WallpaperTheme, isDark: Boolean): List<Color> {
    return when (theme) {
        WallpaperTheme.CLASSIC -> if (isDark) {
            listOf(Color(0xFF0A1412), Color(0xFF10201C))
        } else {
            listOf(Color(0xFFEBE5DB), Color(0xFFE5DDD5))
        }
        WallpaperTheme.MIDNIGHT -> listOf(Color(0xFF0B1120), Color(0xFF172554))
        WallpaperTheme.MINT -> if (isDark) {
            listOf(Color(0xFF042F2E), Color(0xFF064E3B))
        } else {
            listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5))
        }
        WallpaperTheme.OCEAN -> if (isDark) {
            listOf(Color(0xFF0C1E33), Color(0xFF0F2942))
        } else {
            listOf(Color(0xFFE0F2FE), Color(0xFFDBEAFE))
        }
        WallpaperTheme.CORAL -> if (isDark) {
            listOf(Color(0xFF2D1215), Color(0xFF3B151B))
        } else {
            listOf(Color(0xFFFFF1F2), Color(0xFFFFE4E6))
        }
    }
}
