package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AvatarPresets
import com.example.data.model.FakeChat
import com.example.data.model.GroupMember
import com.example.data.model.GroupMembersCodec
import com.example.data.model.WallpaperTheme
import com.example.media.MediaImportHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

fun FakeChat.toGroupMember(isAdmin: Boolean = false): GroupMember = GroupMember(
    chatId = id,
    name = contactName.trim().take(60),
    statusText = statusText.trim().ifBlank { if (online) "en línea" else "hace un momento" }.take(80),
    avatarEmoji = avatarEmoji,
    avatarColorHex = avatarColorHex,
    isAdmin = isAdmin,
    isMe = false
)

fun defaultMeGroupMember(): GroupMember = GroupMember(
    chatId = null,
    name = "Tú",
    statusText = "en línea",
    avatarEmoji = "TÚ",
    avatarColorHex = AvatarPresets.colors.first(),
    isAdmin = true,
    isMe = true
)

fun resolveMemberAvatarBase64(
    member: GroupMember,
    availableChats: List<FakeChat>
): String? {
    if (member.isMe) return null
    val byId = member.chatId?.let { id -> availableChats.firstOrNull { it.id == id } }
    if (byId?.avatarImageBase64 != null) return byId.avatarImageBase64
    return availableChats.firstOrNull {
        !it.groupChat && it.contactName.equals(member.name, ignoreCase = true)
    }?.avatarImageBase64
}

@Composable
fun GroupMembersSection(
    members: List<GroupMember>,
    availableChats: List<FakeChat>,
    onMembersChanged: (List<GroupMember>) -> Unit,
    modifier: Modifier = Modifier,
    groupTitle: String = "",
    groupAvatarEmoji: String = "👥",
    groupAvatarColorHex: String = AvatarPresets.colors.first(),
    groupAvatarImageBase64: String? = null
) {
    var showPickFromChatsDialog by remember { mutableStateOf(false) }
    var editingMemberPair by remember { mutableStateOf<Pair<Int, GroupMember>?>(null) }
    var showAddManualDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Miembros del grupo (${members.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { showPickFromChatsDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("add_members_from_chats_button")
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("De chats")
                }
                TextButton(
                    onClick = { showAddManualDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("add_manual_member_button")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manual")
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("group_members_section")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("group_members_header_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarBadge(
                        emoji = groupAvatarEmoji,
                        colorHex = groupAvatarColorHex,
                        online = false,
                        size = 46.dp,
                        imageBase64 = groupAvatarImageBase64
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = groupTitle.trim().ifBlank { "Grupo sin título" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (members.size == 1) "Grupo · 1 miembro" else "Grupo · ${members.size} miembros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                if (members.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No hay miembros configurados todavía.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showPickFromChatsDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Añadir desde tus chats")
                        }
                    }
                } else {
                    members.forEachIndexed { index, member ->
                        val avatarPhoto = resolveMemberAvatarBase64(member, availableChats)
                        GroupMemberRow(
                            member = member,
                            avatarImageBase64 = avatarPhoto,
                            onEdit = { editingMemberPair = index to member },
                            onToggleAdmin = {
                                val updated = members.toMutableList()
                                updated[index] = member.copy(isAdmin = !member.isAdmin)
                                onMembersChanged(updated)
                            },
                            onRemove = {
                                val updated = members.toMutableList()
                                updated.removeAt(index)
                                onMembersChanged(updated)
                            }
                        )
                        if (index < members.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(start = 70.dp, end = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPickFromChatsDialog) {
        PickChatsForGroupDialog(
            availableChats = availableChats.filter { !it.groupChat },
            existingMembers = members,
            onDismiss = { showPickFromChatsDialog = false },
            onAddSelectedChats = { selectedChats ->
                showPickFromChatsDialog = false
                val updated = members.toMutableList()
                selectedChats.forEach { chat ->
                    val alreadyPresent = updated.any { m ->
                        (m.chatId != null && m.chatId == chat.id) ||
                            (!m.isMe && m.name.equals(chat.contactName, ignoreCase = true))
                    }
                    if (!alreadyPresent) {
                        updated.add(chat.toGroupMember())
                    }
                }
                onMembersChanged(updated)
            }
        )
    }

    if (showAddManualDialog) {
        GroupMemberFormDialog(
            title = "Añadir miembro al grupo",
            confirmLabel = "Añadir",
            initialName = "",
            initialStatus = "en línea",
            initialAdmin = false,
            onDismiss = { showAddManualDialog = false },
            onConfirm = { name, status, isAdmin ->
                showAddManualDialog = false
                val clean = name.trim().take(60)
                if (clean.isNotEmpty()) {
                    val colorHex = AvatarPresets.colors[abs(clean.hashCode()) % AvatarPresets.colors.size]
                    val newMember = GroupMember(
                        chatId = null,
                        name = clean,
                        statusText = status.trim().ifBlank { "en línea" }.take(80),
                        avatarEmoji = deriveContactInitials(clean),
                        avatarColorHex = colorHex,
                        isAdmin = isAdmin,
                        isMe = clean.equals("Tú", ignoreCase = true) || clean.equals("Yo", ignoreCase = true)
                    )
                    onMembersChanged(members + newMember)
                }
            }
        )
    }

    editingMemberPair?.let { (index, target) ->
        GroupMemberFormDialog(
            title = "Editar miembro",
            confirmLabel = "Guardar",
            initialName = target.name,
            initialStatus = target.statusText,
            initialAdmin = target.isAdmin,
            onDismiss = { editingMemberPair = null },
            onConfirm = { name, status, isAdmin ->
                editingMemberPair = null
                val clean = name.trim().take(60)
                if (clean.isNotEmpty() && index in members.indices) {
                    val updated = members.toMutableList()
                    updated[index] = target.copy(
                        name = clean,
                        statusText = status.trim().ifBlank { "en línea" }.take(80),
                        avatarEmoji = if (target.chatId == null) deriveContactInitials(clean) else target.avatarEmoji,
                        isAdmin = isAdmin
                    )
                    onMembersChanged(updated)
                }
            }
        )
    }
}

@Composable
private fun GroupMemberRow(
    member: GroupMember,
    avatarImageBase64: String?,
    onEdit: () -> Unit,
    onToggleAdmin: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showMenu = true }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("group_member_row_${member.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvatarBadge(
            emoji = member.avatarEmoji,
            colorHex = member.avatarColorHex,
            online = member.statusText.equals("en línea", ignoreCase = true),
            size = 44.dp,
            imageBase64 = avatarImageBase64
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = member.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (member.isAdmin) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            ) {
                Text(
                    text = "Admin. del grupo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Opciones de miembro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Editar nombre o estado") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(if (member.isAdmin) "Quitar Admin. del grupo" else "Designar Admin. del grupo")
                    },
                    leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onToggleAdmin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Quitar del grupo") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onRemove()
                    }
                )
            }
        }
    }
}

@Composable
private fun PickChatsForGroupDialog(
    availableChats: List<FakeChat>,
    existingMembers: List<GroupMember>,
    onDismiss: () -> Unit,
    onAddSelectedChats: (List<FakeChat>) -> Unit
) {
    val selectableChats = remember(availableChats, existingMembers) {
        availableChats.filter { chat ->
            existingMembers.none { m ->
                (m.chatId != null && m.chatId == chat.id) ||
                    (!m.isMe && m.name.equals(chat.contactName, ignoreCase = true))
            }
        }
    }
    var search by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(selectableChats, search) {
        val q = search.trim()
        if (q.isEmpty()) selectableChats
        else selectableChats.filter { it.contactName.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir desde tus chats") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectableChats.isEmpty()) {
                    Text(
                        text = "Todos tus chats individuales ya están en el grupo o no hay otros chats creados todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Buscar chat...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filtered.forEach { chat ->
                            val isSelected = chat.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        selectedIds = if (isSelected) selectedIds - chat.id else selectedIds + chat.id
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AvatarBadge(
                                    emoji = chat.avatarEmoji,
                                    colorHex = chat.avatarColorHex,
                                    online = chat.online,
                                    size = 38.dp,
                                    imageBase64 = chat.avatarImageBase64
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chat.contactName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = chat.statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + chat.id else selectedIds - chat.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = selectableChats.filter { it.id in selectedIds }
                    onAddSelectedChats(chosen)
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Añadir (${selectedIds.size})")
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
private fun GroupMemberFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialStatus: String,
    initialAdmin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, status: String, isAdmin: Boolean) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var status by remember(initialStatus) { mutableStateOf(initialStatus) }
    var isAdmin by remember(initialAdmin) { mutableStateOf(initialAdmin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Sarah Aalvik") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { if (it.length <= 80) status = it },
                    label = { Text("Estado / última vez") },
                    placeholder = { Text("en línea, hace 9 minutos...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdmin = !isAdmin }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Admin. del grupo",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = isAdmin, onCheckedChange = { isAdmin = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, status, isAdmin) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreatorSheet(
    availableChats: List<FakeChat>,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateGroup: (
        groupName: String,
        statusText: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String?,
        wallpaperTheme: WallpaperTheme,
        members: List<GroupMember>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val individualChats = remember(availableChats) { availableChats.filter { !it.groupChat } }

    var groupName by remember { mutableStateOf("") }
    var avatarEmoji by remember { mutableStateOf("👥") }
    var avatarColorHex by remember { mutableStateOf(AvatarPresets.colors.first()) }
    var avatarImageBase64 by remember { mutableStateOf<String?>(null) }
    var wallpaper by remember { mutableStateOf(WallpaperTheme.CLASSIC) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedChatIds by remember { mutableStateOf(setOf<String>()) }
    var manualMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var includeMe by remember { mutableStateOf(true) }
    var showAddManualDialog by remember { mutableStateOf(false) }
    var showGalleryConsent by remember { mutableStateOf(false) }

    val selectedChatMembers = remember(individualChats, selectedChatIds) {
        individualChats.filter { it.id in selectedChatIds }.map { it.toGroupMember() }
    }
    val allMembers = remember(includeMe, selectedChatMembers, manualMembers) {
        buildList {
            if (includeMe) add(defaultMeGroupMember())
            addAll(selectedChatMembers)
            addAll(manualMembers)
        }
    }

    val filteredChats = remember(individualChats, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) individualChats
        else individualChats.filter {
            it.contactName.contains(q, ignoreCase = true) ||
                it.statusText.contains(q, ignoreCase = true)
        }
    }

    val avatarFallbackPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = MediaImportHelper.importAvatarFromGallery(context, uri)
                if (encoded != null) {
                    avatarImageBase64 = encoded
                } else {
                    onError("No se pudo procesar la imagen del grupo")
                }
            }
        }
    }

    val avatarChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = MediaImportHelper.extractPickedUri(result.data)
            if (uri != null) {
                scope.launch {
                    val encoded = MediaImportHelper.importAvatarFromGallery(context, uri)
                    if (encoded != null) {
                        avatarImageBase64 = encoded
                    } else {
                        onError("No se pudo procesar la imagen del grupo")
                    }
                }
            }
        }
    }

    val launchAvatarPicker = {
        try {
            avatarChooserLauncher.launch(
                MediaImportHelper.createMediaChooserIntent(
                    allowVideo = false,
                    chooserTitle = "Elegir foto del grupo"
                )
            )
        } catch (_: Exception) {
            try {
                avatarFallbackPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (_: Exception) {
                onError("No se pudo abrir el selector de galería")
            }
        }
    }

    InWindowModalSheet(
        onDismiss = onDismiss
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
                AvatarBadge(
                    emoji = avatarEmoji,
                    colorHex = avatarColorHex,
                    online = false,
                    size = 56.dp,
                    imageBase64 = avatarImageBase64
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nuevo grupo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Selecciona chats presentes en la app o añade miembros",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = groupName,
                onValueChange = { if (it.length <= 60) groupName = it },
                label = { Text("Nombre del grupo") },
                placeholder = { Text("Ej. Wildxgs, Familia, Viaje...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (MediaImportHelper.hasGalleryAccessConsent(context)) {
                            launchAvatarPicker()
                        } else {
                            showGalleryConsent = true
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (avatarImageBase64 != null) "Cambiar foto" else "Foto del grupo")
                }
                if (avatarImageBase64 != null) {
                    OutlinedButton(
                        onClick = { avatarImageBase64 = null },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Quitar foto",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarPresets.colors.forEach { hex ->
                    val selected = hex == avatarColorHex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Participantes (${allMembers.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = { showAddManualDialog = true },
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("group_creator_add_manual_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir manual")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (includeMe) {
                    InputChip(
                        selected = true,
                        onClick = { includeMe = false },
                        label = { Text("Tú (Admin)") },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Quitar Tú", modifier = Modifier.size(16.dp))
                        }
                    )
                } else {
                    FilterChip(
                        selected = false,
                        onClick = { includeMe = true },
                        label = { Text("+ Incluir Tú") }
                    )
                }
                selectedChatMembers.forEach { member ->
                    InputChip(
                        selected = true,
                        onClick = {
                            member.chatId?.let { selectedChatIds = selectedChatIds - it }
                        },
                        label = { Text(member.name.take(18)) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Quitar ${member.name}", modifier = Modifier.size(16.dp))
                        }
                    )
                }
                manualMembers.forEachIndexed { idx, member ->
                    InputChip(
                        selected = true,
                        onClick = {
                            manualMembers = manualMembers.toMutableList().also { it.removeAt(idx) }
                        },
                        label = { Text(member.name.take(18)) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Quitar ${member.name}", modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar en tus chats...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_search_chats_input")
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (individualChats.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No hay chats individuales guardados aún.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Puedes crear chats primero o usar «Añadir manual» arriba para agregar miembros.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (filteredChats.isEmpty()) {
                    Text(
                        text = "Ningún chat coincide con «$searchQuery».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        filteredChats.forEachIndexed { idx, chat ->
                            val isSelected = chat.id in selectedChatIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable {
                                        selectedChatIds = if (isSelected) {
                                            selectedChatIds - chat.id
                                        } else {
                                            selectedChatIds + chat.id
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("group_pick_chat_${chat.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AvatarBadge(
                                    emoji = chat.avatarEmoji,
                                    colorHex = chat.avatarColorHex,
                                    online = chat.online,
                                    size = 42.dp,
                                    imageBase64 = chat.avatarImageBase64
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chat.contactName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = chat.statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedChatIds = if (checked) {
                                            selectedChatIds + chat.id
                                        } else {
                                            selectedChatIds - chat.id
                                        }
                                    }
                                )
                            }
                            if (idx < filteredChats.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(start = 66.dp, end = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

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
                        val cleanTitle = groupName.trim().take(60)
                        val finalMembers = allMembers.ifEmpty { listOf(defaultMeGroupMember()) }
                        val summary = GroupMembersCodec.buildStatusSummary(finalMembers)
                        val finalEmoji = if (avatarEmoji == "👥" && cleanTitle.isNotEmpty()) {
                            deriveContactInitials(cleanTitle)
                        } else {
                            avatarEmoji
                        }
                        onCreateGroup(
                            cleanTitle,
                            summary,
                            finalEmoji,
                            avatarColorHex,
                            avatarImageBase64,
                            wallpaper,
                            finalMembers
                        )
                    },
                    enabled = groupName.trim().isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_group_confirm_button")
                ) {
                    Text("Crear grupo (${allMembers.size})", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showAddManualDialog) {
        GroupMemberFormDialog(
            title = "Añadir participante",
            confirmLabel = "Añadir",
            initialName = "",
            initialStatus = "en línea",
            initialAdmin = false,
            onDismiss = { showAddManualDialog = false },
            onConfirm = { name, status, isAdmin ->
                showAddManualDialog = false
                val clean = name.trim().take(60)
                if (clean.isNotEmpty()) {
                    val colorHex = AvatarPresets.colors[abs(clean.hashCode()) % AvatarPresets.colors.size]
                    manualMembers = manualMembers + GroupMember(
                        chatId = null,
                        name = clean,
                        statusText = status.trim().ifBlank { "en línea" }.take(80),
                        avatarEmoji = deriveContactInitials(clean),
                        avatarColorHex = colorHex,
                        isAdmin = isAdmin,
                        isMe = false
                    )
                }
            }
        )
    }

    if (showGalleryConsent) {
        GalleryAccessConsentDialog(
            purposeText = "MostazaChat te pedirá elegir una única foto para el grupo mediante el selector seguro de Android.",
            onConfirm = {
                MediaImportHelper.setGalleryAccessConsentGranted(context)
                showGalleryConsent = false
                launchAvatarPicker()
            },
            onDismiss = { showGalleryConsent = false }
        )
    }
}
