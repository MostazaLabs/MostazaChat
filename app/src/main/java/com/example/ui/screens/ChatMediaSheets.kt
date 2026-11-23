package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FakeMessage
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.media.MediaImportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class MediaSearchTab(val labelEs: String) {
    ALL("Todo"),
    PHOTOS("Fotos"),
    VIDEOS("Vídeos"),
    MESSAGES("Mensajes")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaComposerDialog(
    uri: Uri,
    initialRole: SenderRole,
    contactName: String,
    myDefaultName: String,
    onDismiss: () -> Unit,
    onSend: (caption: String, role: SenderRole) -> Unit
) {
    val context = LocalContext.current
    val isVideo = remember(uri) {
        MediaImportHelper.isVideoUri(context, uri)
    }
    var caption by remember { mutableStateOf("") }
    var selectedRole by remember(initialRole) {
        mutableStateOf(if (initialRole == SenderRole.OTHER) SenderRole.OTHER else SenderRole.ME)
    }

    val isGif by produceState(initialValue = false, uri, isVideo) {
        value = if (isVideo) false else withContext(Dispatchers.IO) {
            MediaImportHelper.isGifUri(context, uri)
        }
    }

    val previewBitmap by produceState<ImageBitmap?>(initialValue = null, uri, isVideo) {
        value = withContext(Dispatchers.IO) {
            try {
                if (isVideo) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isVideo -> "Enviar vídeo al chat"
                    isGif -> "Enviar GIF al chat"
                    else -> "Enviar foto al chat"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Vista previa",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    if (isVideo) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text("¿Quién envía este archivo?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedRole == SenderRole.ME,
                        onClick = { selectedRole = SenderRole.ME },
                        label = { Text("$myDefaultName (Tú)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    FilterChip(
                        selected = selectedRole == SenderRole.OTHER,
                        onClick = { selectedRole = SenderRole.OTHER },
                        label = { Text(contactName.take(18)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 500) caption = it },
                    label = { Text("Comentario / pie de foto (opcional)") },
                    placeholder = {
                        Text(
                            when {
                                isVideo -> "Mira este vídeo..."
                                isGif -> "Mira este GIF..."
                                else -> "Mira esta foto..."
                            }
                        )
                    },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(caption, selectedRole) },
                modifier = Modifier.testTag("confirm_send_media_button")
            ) {
                Text("Enviar")
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
fun MediaViewerDialog(
    message: FakeMessage,
    onDismiss: () -> Unit,
    onEditMessage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = message.parsedMessageType == MessageType.VIDEO
    val localFileExists = remember(message.localMediaPath) {
        val path = message.localMediaPath
        !path.isNullOrBlank() && File(path).exists()
    }
    val isGif = remember(message.mediaFileName, message.localMediaPath, isVideo) {
        !isVideo && MediaImportHelper.isGifFile(null, message.mediaFileName, message.localMediaPath)
    }
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = MediaImportHelper.decodeBase64ToImageBitmap(message.mediaPreviewBase64),
        message.id,
        message.localMediaPath,
        message.mediaPreviewBase64
    ) {
        if (!isVideo && !isGif && localFileExists) {
            val loaded = MediaImportHelper.loadLocalImageBitmap(message.localMediaPath)
            if (loaded != null) value = loaded
        }
    }

    var zoomScale by remember(message.id) { mutableStateOf(1f) }
    var panOffset by remember(message.id) { mutableStateOf(Offset.Zero) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF060B0A),
            contentColor = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar visor", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = if (message.parsedSenderRole == SenderRole.ME) "Tú" else message.senderName.ifBlank { "Contacto" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "${message.dateLabel} • ${message.displayTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (!isVideo && (imageBitmap != null || (isGif && localFileExists))) {
                        IconButton(
                            onClick = {
                                if (zoomScale > 1.05f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.5f
                                    panOffset = Offset.Zero
                                }
                            },
                            modifier = Modifier.testTag("media_viewer_zoom_button")
                        ) {
                            Icon(
                                imageVector = if (zoomScale > 1.05f) Icons.Rounded.ZoomOutMap else Icons.Rounded.ZoomIn,
                                contentDescription = if (zoomScale > 1.05f) "Restablecer zoom" else "Ampliar imagen",
                                tint = Color.White
                            )
                        }
                    }
                    if (!isVideo) {
                        IconButton(onClick = {
                            scope.launch {
                                val res = MediaImportHelper.saveChatPhotoToGallery(
                                    context = context,
                                    localMediaPath = message.localMediaPath,
                                    mediaPreviewBase64 = message.mediaPreviewBase64
                                )
                                Toast.makeText(
                                    context,
                                    if (res.isSuccess) "Imagen guardada en la galería" else "No se pudo guardar en galería",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(Icons.Rounded.Download, contentDescription = "Guardar en galería", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        onDismiss()
                        onEditMessage()
                    }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar mensaje", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { viewerSize = it }
                        .then(
                            if (!isVideo && (imageBitmap != null || (isGif && localFileExists))) {
                                Modifier
                                    .pointerInput(message.id) {
                                        detectTapGestures(
                                            onDoubleTap = { tap ->
                                                if (zoomScale > 1.05f) {
                                                    zoomScale = 1f
                                                    panOffset = Offset.Zero
                                                } else {
                                                    val targetScale = 2.5f
                                                    val maxX = (viewerSize.width * (targetScale - 1f)) / 2f
                                                    val maxY = (viewerSize.height * (targetScale - 1f)) / 2f
                                                    val cx = viewerSize.width / 2f
                                                    val cy = viewerSize.height / 2f
                                                    val dx = ((cx - tap.x) * (targetScale - 1f)).coerceIn(-maxX, maxX)
                                                    val dy = ((cy - tap.y) * (targetScale - 1f)).coerceIn(-maxY, maxY)
                                                    zoomScale = targetScale
                                                    panOffset = Offset(dx, dy)
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(message.id) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                            if (newScale <= 1.01f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                val maxX = (viewerSize.width * (newScale - 1f)) / 2f
                                                val maxY = (viewerSize.height * (newScale - 1f)) / 2f
                                                val newX = (panOffset.x + pan.x).coerceIn(-maxX, maxX)
                                                val newY = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                                zoomScale = newScale
                                                panOffset = Offset(newX, newY)
                                            }
                                        }
                                    }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo && localFileExists) {
                        val videoPath = message.localMediaPath.orEmpty()
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(videoPath)
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .heightIn(min = 240.dp, max = 520.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else if (isGif && localFileExists) {
                        AnimatedGifPlayer(
                            filePath = message.localMediaPath.orEmpty(),
                            fallbackBitmap = imageBitmap,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                                .testTag("media_viewer_gif")
                        )
                    } else if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap!!,
                            contentDescription = message.text,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                                .testTag("media_viewer_image")
                        )
                        if (isVideo) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Vista previa sincronizada desde otro móvil (${message.mediaDuration ?: "0:05"}). El vídeo original está en el dispositivo emisor.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Archivo multimedia no disponible en este dispositivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                if (message.text.isNotBlank() && message.text != "📷 Foto" && message.text != "🎬 Vídeo" && message.text != "🎞️ GIF") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedGifPlayer(
    filePath: String,
    fallbackBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        var decodedDrawable by remember(filePath) { mutableStateOf<Drawable?>(null) }

        LaunchedEffect(filePath) {
            decodedDrawable = withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) return@withContext null
                    val source = ImageDecoder.createSource(file)
                    ImageDecoder.decodeDrawable(source)
                } catch (_: Exception) {
                    null
                }
            }
        }

        DisposableEffect(decodedDrawable) {
            val anim = decodedDrawable as? AnimatedImageDrawable
            if (anim != null) {
                anim.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                anim.start()
            }
            onDispose {
                anim?.stop()
            }
        }

        val currentDrawable = decodedDrawable
        if (currentDrawable != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = false
                    }
                },
                update = { imageView ->
                    if (imageView.drawable !== currentDrawable) {
                        imageView.setImageDrawable(currentDrawable)
                        (currentDrawable as? AnimatedImageDrawable)?.apply {
                            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            start()
                        }
                    }
                },
                modifier = modifier
            )
        } else if (fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap,
                contentDescription = "Cargando GIF",
                contentScale = ContentScale.Fit,
                modifier = modifier
            )
        }
    } else {
        AndroidView(
            factory = { ctx -> LegacyGifMovieView(ctx, filePath) },
            update = { view -> view.setGifPath(filePath) },
            modifier = modifier
        )
    }
}

@Suppress("DEPRECATION")
private class LegacyGifMovieView(
    context: Context,
    initialPath: String
) : View(context) {
    private var currentPath: String = ""
    private var movie: Movie? = null
    private var movieStartMs: Long = 0L

    init {
        setGifPath(initialPath)
    }

    fun setGifPath(path: String) {
        if (currentPath == path && movie != null) return
        currentPath = path
        movie = try {
            val file = File(path)
            if (file.exists()) Movie.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
        }
        movieStartMs = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = movie ?: return
        val w = m.width().coerceAtLeast(1)
        val h = m.height().coerceAtLeast(1)
        val now = SystemClock.uptimeMillis()
        if (movieStartMs == 0L) movieStartMs = now
        val duration = m.duration().let { if (it <= 0) 1000 else it }
        val relTime = ((now - movieStartMs) % duration).toInt()
        m.setTime(relTime)

        val scale = minOf(width.toFloat() / w.toFloat(), height.toFloat() / h.toFloat())
        val dx = (width - w * scale) / 2f
        val dy = (height - h * scale) / 2f
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        m.draw(canvas, 0f, 0f)
        canvas.restore()

        if (isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatMediaAndSearchSheet(
    messages: List<FakeMessage>,
    myDefaultName: String,
    contactName: String,
    onDismiss: () -> Unit,
    onOpenMedia: (FakeMessage) -> Unit,
    onJumpToMessage: (index: Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(MediaSearchTab.ALL) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val indexedMessages = remember(messages) {
        messages.mapIndexed { idx, msg -> idx to msg }
    }

    val mediaItems = remember(indexedMessages, query, activeTab) {
        val q = query.trim()
        indexedMessages.filter { (_, msg) ->
            val type = msg.parsedMessageType
            val isMediaType = when (activeTab) {
                MediaSearchTab.ALL -> type == MessageType.IMAGE || type == MessageType.VIDEO
                MediaSearchTab.PHOTOS -> type == MessageType.IMAGE
                MediaSearchTab.VIDEOS -> type == MessageType.VIDEO
                MediaSearchTab.MESSAGES -> false
            }
            val matchesQuery = q.isEmpty() ||
                msg.text.contains(q, ignoreCase = true) ||
                (msg.mediaFileName?.contains(q, ignoreCase = true) == true) ||
                msg.senderName.contains(q, ignoreCase = true)
            isMediaType && matchesQuery
        }.reversed()
    }

    val textMatches = remember(indexedMessages, query, activeTab) {
        val q = query.trim()
        if (activeTab == MediaSearchTab.PHOTOS || activeTab == MediaSearchTab.VIDEOS) {
            emptyList()
        } else {
            indexedMessages.filter { (_, msg) ->
                val matchesTab = if (activeTab == MediaSearchTab.MESSAGES) {
                    msg.parsedMessageType == MessageType.TEXT || msg.parsedMessageType == MessageType.VOICE
                } else {
                    true
                }
                val matchesQuery = q.isEmpty() ||
                    msg.text.contains(q, ignoreCase = true) ||
                    (msg.mediaFileName?.contains(q, ignoreCase = true) == true) ||
                    msg.senderName.contains(q, ignoreCase = true)
                matchesTab && matchesQuery
            }.reversed()
        }
    }

    val totalPhotos = remember(messages) { messages.count { it.parsedMessageType == MessageType.IMAGE } }
    val totalVideos = remember(messages) { messages.count { it.parsedMessageType == MessageType.VIDEO } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 640.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Archivos y búsqueda del chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$totalPhotos fotos • $totalVideos vídeos • ${messages.size} mensajes totales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar por palabra, archivo o autor...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_media_search_input")
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaSearchTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.labelEs) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (mediaItems.isNotEmpty()) {
                    item(key = "media_section_header") {
                        Text(
                            text = "Galería del chat (${mediaItems.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val chunked = mediaItems.chunked(3)
                    items(chunked.indices.toList(), key = { "grid_row_$it" }) { rowIdx ->
                        val rowItems = chunked[rowIdx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (_, msg) ->
                                MediaGridThumbnailTile(
                                    message = msg,
                                    myDefaultName = myDefaultName,
                                    contactName = contactName,
                                    onClick = { onOpenMedia(msg) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else if (activeTab == MediaSearchTab.PHOTOS || activeTab == MediaSearchTab.VIDEOS) {
                    item(key = "empty_media") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (query.isBlank()) "Todavía no hay archivos multimedia subidos en este chat."
                                else "No hay fotos ni vídeos que coincidan con \"$query\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                if (textMatches.isNotEmpty() && (activeTab == MediaSearchTab.MESSAGES || (activeTab == MediaSearchTab.ALL && query.isNotBlank()))) {
                    item(key = "msgs_section_header") {
                        Text(
                            text = "Mensajes encontrados (${textMatches.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(textMatches, key = { (_, msg) -> "search_${msg.id}" }) { (index, msg) ->
                        val senderLabel = when (msg.parsedSenderRole) {
                            SenderRole.ME -> "$myDefaultName (Tú)"
                            SenderRole.OTHER -> msg.senderName.ifBlank { contactName }
                            SenderRole.SYSTEM -> "Sistema"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onJumpToMessage(index) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = senderLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.parsedSenderRole == SenderRole.ME) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    }
                                )
                                Text(
                                    text = "${msg.dateLabel} • ${msg.displayTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                } else if (activeTab == MediaSearchTab.MESSAGES && textMatches.isEmpty()) {
                    item(key = "empty_msgs") {
                        Text(
                            text = "No se encontraron mensajes para \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridThumbnailTile(
    message: FakeMessage,
    myDefaultName: String,
    contactName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = message.parsedMessageType == MessageType.VIDEO
    val isGif = !isVideo && MediaImportHelper.isGifFile(null, message.mediaFileName, message.localMediaPath)
    val previewBitmap = remember(message.mediaPreviewBase64) {
        MediaImportHelper.decodeBase64ToImageBitmap(message.mediaPreviewBase64)
    }
    val senderTag = if (message.parsedSenderRole == SenderRole.ME) myDefaultName else message.senderName.ifBlank { contactName }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick)
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = message.mediaFileName ?: message.text,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )
        }

        if (isVideo) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = message.mediaDuration ?: "0:05",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else if (isGif) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = "GIF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        ) {
            Text(
                text = senderTag.take(10),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
