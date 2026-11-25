package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.BackupProvider
import com.example.auth.ExternalSessionConfig
import com.example.auth.ProviderCategory
import com.example.media.RemoteBackupClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupProviderPickerSheet(
    title: String = "Elegir nube o remoto",
    confirmButtonLabel: String = "Conectar y continuar",
    initialConfig: ExternalSessionConfig? = null,
    onDismiss: () -> Unit,
    onConfirmConfig: (config: ExternalSessionConfig, importImmediately: Boolean) -> Unit
) {
    val context = LocalContext.current

    var selectedCategory by remember(initialConfig) {
        mutableStateOf(initialConfig?.provider?.category ?: ProviderCategory.CLOUD)
    }
    var selectedProvider by remember { mutableStateOf<BackupProvider?>(null) }

    var accountLabel by remember { mutableStateOf(initialConfig?.accountLabel.orEmpty()) }
    var remoteRoute by remember { mutableStateOf(initialConfig?.remoteRoute.orEmpty()) }
    var savedDocUri by remember { mutableStateOf(initialConfig?.savedDocumentUri.orEmpty()) }
    var pickedFileLabel by remember { mutableStateOf("") }
    var authUsername by remember { mutableStateOf(initialConfig?.authUsername.orEmpty()) }
    var authSecret by remember { mutableStateOf(initialConfig?.authSecret.orEmpty()) }
    var showSecret by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            savedDocUri = uri.toString()
            pickedFileLabel = querySafDisplayName(context, uri).ifBlank { uri.lastPathSegment ?: "Archivo seleccionado" }
            validationError = null
        }
    }

    InWindowModalSheet(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(bottom = 20.dp)
        ) {
            val activeProvider = selectedProvider
            if (activeProvider == null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Text(
                    text = "Selecciona dónde guardas tus copias o facilita la ruta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(8.dp))

                // 2-Tab Header inspired by Cx Explorer (Nube | Remoto), zero transition animation
                Row(modifier = Modifier.fillMaxWidth()) {
                    ProviderCategory.entries.forEach { category ->
                        val isSelected = selectedCategory == category
                        val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCategory = category }
                                .padding(top = 8.dp)
                                .testTag("tab_provider_${category.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (category == ProviderCategory.CLOUD) Icons.Rounded.Cloud else Icons.Rounded.Computer,
                                contentDescription = category.labelEs,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = category.labelEs,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tint
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(3.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                    )
                            )
                        }
                    }
                }
                HorizontalDivider()

                val providers = remember(selectedCategory) {
                    BackupProvider.byCategory(selectedCategory)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.forEach { provider ->
                        ProviderSelectionRow(
                            provider = provider,
                            isCurrentlyActive = initialConfig?.active == true && initialConfig.provider == provider,
                            onClick = {
                                selectedProvider = provider
                                validationError = null
                                if (initialConfig?.provider != provider) {
                                    accountLabel = ""
                                    remoteRoute = ""
                                    savedDocUri = ""
                                    pickedFileLabel = ""
                                    authUsername = ""
                                    authSecret = ""
                                } else {
                                    accountLabel = initialConfig.accountLabel
                                    remoteRoute = initialConfig.remoteRoute
                                    savedDocUri = initialConfig.savedDocumentUri
                                    authUsername = initialConfig.authUsername
                                    authSecret = initialConfig.authSecret
                                }
                            }
                        )
                    }
                }
            } else {
                // Provider Route & Account Configuration Form
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                selectedProvider = null
                                validationError = null
                            },
                            modifier = Modifier.testTag("provider_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver a la lista de proveedores"
                            )
                        }
                        ProviderBadge(provider = activeProvider, size = 40)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeProvider.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeProvider.subtitleEs,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (validationError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = validationError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = accountLabel,
                        onValueChange = { if (it.length <= 80) accountLabel = it },
                        label = { Text("Cuenta, correo o alias (opcional)") },
                        placeholder = {
                            Text(
                                when (activeProvider) {
                                    BackupProvider.PROTON_MAIL -> "tu_usuario@proton.me"
                                    BackupProvider.FACEBOOK_META -> "Tu perfil de Meta / Facebook"
                                    else -> "Ej. Mi cuenta ${activeProvider.displayName}"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_account_input")
                    )

                    OutlinedTextField(
                        value = remoteRoute,
                        onValueChange = {
                            if (it.length <= 500) {
                                remoteRoute = it
                                validationError = null
                            }
                        },
                        label = { Text("Ruta o URL del respaldo (opcional)") },
                        placeholder = { Text(activeProvider.routeHint) },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_route_input")
                    )

                    if (activeProvider.supportsAuthFields) {
                        OutlinedTextField(
                            value = authUsername,
                            onValueChange = { if (it.length <= 120) authUsername = it },
                            label = { Text("Usuario WebDAV / servidor (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = authSecret,
                            onValueChange = { if (it.length <= 300) authSecret = it },
                            label = { Text("Contraseña o Token Bearer (opcional)") },
                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showSecret) "Ocultar clave" else "Mostrar clave"
                                    )
                                }
                            },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // SAF File Picker integration directly for Drive/Dropbox/OneDrive/Box/Proton/TeraBox/Internxt/SMB/FTP/etc.
                    OutlinedButton(
                        onClick = {
                            try {
                                safDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            } catch (_: Exception) {
                                validationError = "No se pudo abrir el selector de archivos del sistema"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("provider_pick_saf_button")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (savedDocUri.isNotBlank()) {
                                "Archivo elegido: ${pickedFileLabel.ifBlank { "Copia lista" }}"
                            } else {
                                "Elegir copia .json/.txt desde ${activeProvider.displayName}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    activeProvider.portalUrl?.let { portal ->
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(portal))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    validationError = "No hay navegador disponible para abrir $portal"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir web / cuenta de ${activeProvider.displayName}")
                        }
                    }

                    val validateAndBuildConfig: () -> ExternalSessionConfig? = {
                        val routeCheck = RemoteBackupClient.validateRouteInput(remoteRoute)
                        if (routeCheck.isFailure) {
                            validationError = routeCheck.exceptionOrNull()?.localizedMessage ?: "Ruta inválida"
                            null
                        } else {
                            ExternalSessionConfig(
                                active = true,
                                provider = activeProvider,
                                accountLabel = accountLabel.trim(),
                                remoteRoute = routeCheck.getOrDefault(""),
                                savedDocumentUri = savedDocUri,
                                authUsername = authUsername.trim(),
                                authSecret = authSecret.trim()
                            )
                        }
                    }

                    val canImportNow = RemoteBackupClient.isHttpUrl(remoteRoute) || savedDocUri.isNotBlank()
                    if (canImportNow) {
                        Button(
                            onClick = {
                                validateAndBuildConfig()?.let { onConfirmConfig(it, true) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("provider_connect_and_import_button")
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Conectar e importar copia ahora")
                        }
                    }

                    if (canImportNow) {
                        OutlinedButton(
                            onClick = {
                                validateAndBuildConfig()?.let { onConfirmConfig(it, false) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("provider_confirm_button")
                        ) {
                            Text(confirmButtonLabel)
                        }
                    } else {
                        Button(
                            onClick = {
                                validateAndBuildConfig()?.let { onConfirmConfig(it, false) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("provider_confirm_button")
                        ) {
                            Icon(Icons.Rounded.Storage, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(confirmButtonLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSelectionRow(
    provider: BackupProvider,
    isCurrentlyActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentlyActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("provider_row_${provider.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderBadge(provider = provider, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = provider.subtitleEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Configurar ${provider.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProviderBadge(
    provider: BackupProvider,
    size: Int = 36
) {
    val bgColor = remember(provider.accentColorHex) {
        try {
            Color(android.graphics.Color.parseColor(provider.accentColorHex))
        } catch (_: Exception) {
            Color(0xFF0F766E)
        }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = provider.shortBadge,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun querySafDisplayName(context: android.content.Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx).orEmpty() else ""
            } else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }
}
