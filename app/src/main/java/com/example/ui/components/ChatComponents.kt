package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AvatarPresets
import com.example.data.model.FakeChat
import com.example.data.model.TickStatus
import com.example.data.model.WallpaperTheme
import com.example.media.MediaImportHelper
import com.example.ui.theme.OnlineDotGreen
import com.example.ui.theme.TickReadColor
import com.example.ui.theme.VerifiedBadgeBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class ImportedContact(
    val displayName: String,
    val initials: String,
    val colorHex: String,
    val avatarImageBase64: String? = null
)

fun deriveContactInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "👤"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
    }
}

suspend fun readContactFromUri(context: Context, contactUri: Uri): ImportedContact? = withContext(Dispatchers.IO) {
    return@withContext try {
        val resolver = context.contentResolver
        val resolvedUri = try {
            ContactsContract.Contacts.lookupContact(resolver, contactUri) ?: contactUri
        } catch (_: Exception) {
            contactUri
        }
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )
        val queryUri = resolvedUri
        val result = resolver.query(queryUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idxId = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val idxName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val idxPhoto = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val idxThumb = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                val contactId = if (idxId >= 0 && !cursor.isNull(idxId)) cursor.getLong(idxId) else null
                val rawName = if (idxName >= 0) cursor.getString(idxName) else null
                val rawPhotoUri = if (idxPhoto >= 0) cursor.getString(idxPhoto) else null
                val rawThumbUri = if (idxThumb >= 0) cursor.getString(idxThumb) else null
                val cleanName = rawName?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: return@use null
                val initials = deriveContactInitials(cleanName)
                val colorHex = AvatarPresets.colors[abs(cleanName.hashCode()) % AvatarPresets.colors.size]
                val photoBase64 = MediaImportHelper.extractContactPhotoBase64(
                    context = context,
                    contactUri = resolvedUri,
                    contactId = contactId,
                    photoUriStr = rawPhotoUri,
                    photoThumbUriStr = rawThumbUri
                )
                ImportedContact(cleanName, initials, colorHex, photoBase64)
            } else null
        }
        result ?: if (resolvedUri != contactUri) {
            resolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idxId = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val idxName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val idxPhoto = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val idxThumb = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                    val contactId = if (idxId >= 0 && !cursor.isNull(idxId)) cursor.getLong(idxId) else null
                    val rawName = if (idxName >= 0) cursor.getString(idxName) else null
                    val rawPhotoUri = if (idxPhoto >= 0) cursor.getString(idxPhoto) else null
                    val rawThumbUri = if (idxThumb >= 0) cursor.getString(idxThumb) else null
                    val cleanName = rawName?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: return@use null
                    val initials = deriveContactInitials(cleanName)
                    val colorHex = AvatarPresets.colors[abs(cleanName.hashCode()) % AvatarPresets.colors.size]
                    val photoBase64 = MediaImportHelper.extractContactPhotoBase64(
                        context = context,
                        contactUri = contactUri,
                        contactId = contactId,
                        photoUriStr = rawPhotoUri,
                        photoThumbUriStr = rawThumbUri
                    )
                    ImportedContact(cleanName, initials, colorHex, photoBase64)
                } else null
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun parseColorSafe(hex: String, fallback: Color = Color(0xFF00796B)): Color {
    return try {
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(normalized))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun AvatarBadge(
    emoji: String,
    colorHex: String,
    online: Boolean = false,
    size: Dp = 50.dp,
    imageBase64: String? = null,
    modifier: Modifier = Modifier
) {
    val baseColor = remember(colorHex) { parseColorSafe(colorHex) }
    val photoBitmap = remember(imageBase64) { MediaImportHelper.decodeBase64ToImageBitmap(imageBase64) }
    val display = emoji.ifBlank { "💬" }
    val isInitials = remember(display) {
        display.length <= 3 && display.all { it.isLetterOrDigit() }
    }
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(baseColor, baseColor.copy(alpha = 0.72f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = "Foto de perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = display,
                    color = if (isInitials) Color.White else Color.Unspecified,
                    fontWeight = if (isInitials) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isInitials) (size.value * 0.36f).sp else (size.value * 0.44f).sp
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(OnlineDotGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun ContactAvatarPreviewDialog(
    chat: FakeChat,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val photoBitmap = remember(chat.avatarImageBase64) {
        MediaImportHelper.decodeBase64ToImageBitmap(chat.avatarImageBase64)
    }
    val baseColor = remember(chat.avatarColorHex) { parseColorSafe(chat.avatarColorHex) }
    val display = chat.avatarEmoji.ifBlank { "💬" }
    val isInitials = remember(display) {
        display.length <= 3 && display.all { it.isLetterOrDigit() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 310.dp)
                .testTag("contact_avatar_preview_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(baseColor, baseColor.copy(alpha = 0.78f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "Foto de perfil de ${chat.contactName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = display,
                            color = if (isInitials) Color.White else Color.Unspecified,
                            fontWeight = if (isInitials) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isInitials) 78.sp else 92.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = chat.contactName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (chat.verified) {
                            VerifiedIcon(size = 16.dp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.testTag("avatar_preview_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Message,
                            contentDescription = "Abrir chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onStartVoiceCall,
                        modifier = Modifier.testTag("avatar_preview_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = "Llamada simulada",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onStartVideoCall,
                        modifier = Modifier.testTag("avatar_preview_video_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Videocam,
                            contentDescription = "Videollamada simulada",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenInfo,
                        modifier = Modifier.testTag("avatar_preview_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Editar contacto",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryAccessConsentDialog(
    purposeText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Permiso seguro de galería") },
        text = {
            Text(
                text = purposeText,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_gallery_permission_button")
            ) {
                Text("Permitir y elegir")
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
fun VerifiedIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = "Verificado",
        tint = VerifiedBadgeBlue,
        modifier = modifier.size(size)
    )
}

@Composable
fun TickStatusIcon(
    tickStatus: TickStatus,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    val icon = if (tickStatus == TickStatus.SENT) Icons.Rounded.Check else Icons.Rounded.DoneAll
    val tint = if (tickStatus == TickStatus.READ) TickReadColor else MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        imageVector = icon,
        contentDescription = tickStatus.labelEs,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatEditorSheet(
    title: String,
    confirmLabel: String,
    initialContactName: String = "",
    initialGroupChat: Boolean = false,
    initialStatusText: String = "en línea",
    initialOnline: Boolean = true,
    initialVerified: Boolean = false,
    initialAvatarEmoji: String = "😎",
    initialAvatarColorHex: String = AvatarPresets.colors.first(),
    initialAvatarImageBase64: String? = null,
    initialWallpaper: WallpaperTheme = WallpaperTheme.CLASSIC,
    initialUnreadCount: Long = 0L,
    initialPinned: Boolean = false,
    allowContactImport: Boolean = false,
    onContactImportError: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (
        contactName: String,
        groupChat: Boolean,
        statusText: String,
        online: Boolean,
        verified: Boolean,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String?,
        clearAvatarPhoto: Boolean,
        wallpaperTheme: WallpaperTheme,
        unreadCount: Long,
        pinned: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contactName by remember(initialContactName) { mutableStateOf(initialContactName) }
    var groupChat by remember(initialGroupChat) { mutableStateOf(initialGroupChat) }
    var statusText by remember(initialStatusText) { mutableStateOf(initialStatusText) }
    var online by remember(initialOnline) { mutableStateOf(initialOnline) }
    var verified by remember(initialVerified) { mutableStateOf(initialVerified) }
    var avatarEmoji by remember(initialAvatarEmoji) { mutableStateOf(initialAvatarEmoji) }
    var avatarColorHex by remember(initialAvatarColorHex) { mutableStateOf(initialAvatarColorHex) }
    var avatarImageBase64 by remember(initialAvatarImageBase64) { mutableStateOf(initialAvatarImageBase64) }
    var clearAvatarPhoto by remember(initialAvatarImageBase64) { mutableStateOf(false) }
    var wallpaper by remember(initialWallpaper) { mutableStateOf(initialWallpaper) }
    var unreadText by remember(initialUnreadCount) { mutableStateOf(initialUnreadCount.toString()) }
    var pinned by remember(initialPinned) { mutableStateOf(initialPinned) }
    var showGalleryConsent by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = readContactFromUri(context, uri)
                if (imported != null) {
                    contactName = imported.displayName
                    avatarEmoji = imported.initials
                    avatarColorHex = imported.colorHex
                    avatarImageBase64 = imported.avatarImageBase64
                    clearAvatarPhoto = imported.avatarImageBase64 == null
                    groupChat = false
                    if (statusText.isBlank()) statusText = "en línea"
                } else {
                    onContactImportError("No se pudo leer el contacto seleccionado")
                }
            }
        }
    }

    val launchContactPickerSafely = {
        try {
            contactPickerLauncher.launch(null)
        } catch (_: Exception) {
            onContactImportError("No se encontró la app de Contactos en este dispositivo")
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchContactPickerSafely()
    }

    val avatarPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = MediaImportHelper.importAvatarFromGallery(context, uri)
                if (encoded != null) {
                    avatarImageBase64 = encoded
                    clearAvatarPhoto = false
                } else {
                    onContactImportError("No se pudo procesar la foto seleccionada")
                }
            }
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AvatarBadge(
                    emoji = avatarEmoji,
                    colorHex = avatarColorHex,
                    online = online,
                    size = 58.dp,
                    imageBase64 = avatarImageBase64
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Personaliza el perfil simulado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (allowContactImport) {
                OutlinedButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            launchContactPickerSafely()
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_import_contact_button")
                ) {
                    Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clonar desde tu agenda de contactos")
                }
            }

            OutlinedTextField(
                value = contactName,
                onValueChange = { if (it.length <= 60) contactName = it },
                label = { Text("Nombre del contacto o grupo") },
                placeholder = { Text("Ej. Mamá, Jefe, Grupo Viaje") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_name_input")
            )

            OutlinedTextField(
                value = statusText,
                onValueChange = { if (it.length <= 80) statusText = it },
                label = { Text("Estado / subtítulo") },
                placeholder = { Text("en línea, últ. vez hoy a las 14:10...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_status_input")
            )

            Text(
                text = "Foto de perfil y avatar",
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showGalleryConsent = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("pick_avatar_photo_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (avatarImageBase64 != null) "Cambiar foto" else "Foto de galería")
                }
                if (avatarImageBase64 != null) {
                    OutlinedButton(
                        onClick = {
                            avatarImageBase64 = null
                            clearAvatarPhoto = true
                        },
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("remove_avatar_photo_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Quitar foto",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Quitar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarPresets.emojis.forEach { emoji ->
                    val selected = emoji == avatarEmoji
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { avatarEmoji = emoji }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvatarPresets.colors.forEach { hex ->
                    val selected = hex == avatarColorHex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseColorSafe(hex))
                            .then(
                                if (selected) Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    CircleShape
                                ) else Modifier
                            )
                            .clickable { avatarColorHex = hex }
                    )
                }
            }

            Text(
                text = "Tema del fondo de chat",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WallpaperTheme.entries.forEach { themeOption ->
                    FilterChip(
                        selected = wallpaper == themeOption,
                        onClick = { wallpaper = themeOption },
                        label = { Text(themeOption.labelEs) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = unreadText,
                    onValueChange = { raw ->
                        if (raw.all { it.isDigit() } && raw.length <= 4) unreadText = raw
                    },
                    label = { Text("No leídos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    SwitchRow(
                        label = "Es grupo",
                        checked = groupChat,
                        onCheckedChange = {
                            groupChat = it
                            if (it && statusText == "en línea") {
                                statusText = "Tú, Sofía, Carlos, Ana"
                            }
                        }
                    )
                }
            }

            SwitchRow(
                label = "Punto verde (en línea)",
                checked = online,
                onCheckedChange = { online = it }
            )
            SwitchRow(
                label = "Insignia de verificado",
                checked = verified,
                onCheckedChange = { verified = it }
            )
            SwitchRow(
                label = "Fijar arriba en la lista",
                checked = pinned,
                onCheckedChange = { pinned = it }
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val unread = unreadText.toLongOrNull()?.coerceIn(0L, 9999L) ?: 0L
                        onSave(
                            contactName.trim(),
                            groupChat,
                            statusText.trim(),
                            online,
                            verified,
                            avatarEmoji,
                            avatarColorHex,
                            avatarImageBase64,
                            clearAvatarPhoto,
                            wallpaper,
                            unread,
                            pinned
                        )
                    },
                    enabled = contactName.trim().isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_chat_button")
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showGalleryConsent) {
        GalleryAccessConsentDialog(
            purposeText = "MostazaChat te pedirá elegir una única foto de perfil mediante el selector seguro de Android. No accederemos al resto de tu galería.",
            onConfirm = {
                showGalleryConsent = false
                try {
                    avatarPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (_: Exception) {
                    onContactImportError("No se pudo abrir el selector de galería")
                }
            },
            onDismiss = { showGalleryConsent = false }
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
