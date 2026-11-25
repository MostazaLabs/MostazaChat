package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.BuildConfig
import com.example.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

data class ImportedChatMedia(
    val messageType: MessageType,
    val previewBase64: String?,
    val localPath: String,
    val fileName: String,
    val durationText: String?
)

object MediaImportHelper {

    const val AVATAR_MAX_BASE64_CHARS = 46_000
    private const val MEDIA_PREFS_NAME = "mostazachat_media_prefs"
    private const val KEY_GALLERY_CONSENT_GRANTED = "gallery_picker_consent_granted"

    fun hasGalleryAccessConsent(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(MEDIA_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GALLERY_CONSENT_GRANTED, false)
    }

    fun setGalleryAccessConsentGranted(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(MEDIA_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GALLERY_CONSENT_GRANTED, true).apply()
    }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeAvatarBase64(base64: String?, maxChars: Int = AVATAR_MAX_BASE64_CHARS): String? {
        val clean = base64?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (clean.length <= maxChars) return clean
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            encodeSquareThumbnail(decoded, sizePx = 280, maxChars = maxChars)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadLocalImageBitmap(path: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxDim / sample > 2048) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun extractContactPhotoBase64(
        context: Context,
        contactUri: Uri,
        contactId: Long? = null,
        photoUriStr: String? = null,
        photoThumbUriStr: String? = null
    ): String? {
        val resolver = context.contentResolver
        val resolvedUri = try {
            ContactsContract.Contacts.lookupContact(resolver, contactUri) ?: contactUri
        } catch (_: Exception) {
            contactUri
        }

        val bitmap = tryOpenBitmap {
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, resolvedUri, true)
        } ?: tryOpenBitmap {
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, resolvedUri, false)
        } ?: tryOpenBitmap {
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, contactUri, true)
        } ?: tryOpenBitmap {
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, contactUri, false)
        } ?: photoUriStr?.takeIf { it.isNotBlank() }?.let { raw ->
            tryOpenBitmap { resolver.openInputStream(Uri.parse(raw)) }
        } ?: photoThumbUriStr?.takeIf { it.isNotBlank() }?.let { raw ->
            tryOpenBitmap { resolver.openInputStream(Uri.parse(raw)) }
        } ?: queryContactPhotoBlob(resolver, resolvedUri)
            ?: contactId?.let { queryDataPhotoBlob(resolver, it) }
            ?: return null

        return encodeSquareThumbnail(bitmap, sizePx = 300, maxChars = AVATAR_MAX_BASE64_CHARS)
    }

    private fun queryContactPhotoBlob(
        resolver: android.content.ContentResolver,
        contactUri: Uri
    ): Bitmap? {
        return try {
            val photoDirUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
            resolver.query(
                photoDirUri,
                arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bytes = cursor.getBlob(0)
                    if (bytes != null && bytes.isNotEmpty()) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDataPhotoBlob(
        resolver: android.content.ContentResolver,
        contactId: Long
    ): Bitmap? {
        return try {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bytes = cursor.getBlob(0)
                    if (bytes != null && bytes.isNotEmpty()) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun importAvatarFromGallery(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeUriBitmap(context, uri, targetMaxDim = 640) ?: return@withContext null
        encodeSquareThumbnail(bitmap, sizePx = 300, maxChars = AVATAR_MAX_BASE64_CHARS)
    }

    fun createMediaChooserIntent(
        allowVideo: Boolean,
        chooserTitle: String = if (allowVideo) "Elegir foto, GIF o vídeo" else "Elegir foto"
    ): Intent {
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            if (allowVideo) {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
        }
        return Intent.createChooser(getContentIntent, chooserTitle)
    }

    fun extractPickedUri(data: Intent?): Uri? {
        if (data == null) return null
        return data.data ?: data.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    fun isVideoUri(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime?.startsWith("video/", ignoreCase = true) == true) return true
        val name = queryDisplayName(context, uri).ifBlank { uri.lastPathSegment.orEmpty() }
            .trim()
            .lowercase(Locale.ROOT)
        return name.endsWith(".mp4") ||
            name.endsWith(".mkv") ||
            name.endsWith(".webm") ||
            name.endsWith(".3gp") ||
            name.endsWith(".mov") ||
            name.endsWith(".m4v") ||
            name.endsWith(".avi")
    }

    fun isGifFile(mimeType: String?, fileName: String?, localPath: String? = null): Boolean {
        if (mimeType.equals("image/gif", ignoreCase = true)) return true
        if (fileName?.trim()?.lowercase(Locale.ROOT)?.endsWith(".gif") == true) return true
        if (localPath?.trim()?.lowercase(Locale.ROOT)?.endsWith(".gif") == true) return true
        return false
    }

    fun isGifUri(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val displayName = queryDisplayName(context, uri).ifBlank { uri.lastPathSegment.orEmpty() }
        if (isGifFile(mime, displayName)) return true
        return startsWithGifHeader(context, uri)
    }

    private fun startsWithGifHeader(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(6)
                val read = input.read(header)
                if (read < 6) return false
                val sig = String(header, Charsets.US_ASCII)
                sig == "GIF87a" || sig == "GIF89a"
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun importChatMediaFromGallery(context: Context, uri: Uri): ImportedChatMedia? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val fileName = queryDisplayName(context, uri)
        val mediaDir = File(context.filesDir, "chat_media").apply { mkdirs() }

        if (isVideoUri(context, uri)) {
            val destFile = File(mediaDir, "vid_${UUID.randomUUID()}.mp4")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                } ?: return@withContext null
            } catch (_: Exception) {
                return@withContext null
            }

            val retriever = MediaMetadataRetriever()
            var durationStr = "0:05"
            var thumbBase64: String? = null
            try {
                retriever.setDataSource(destFile.absolutePath)
                val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val totalSec = (durMs / 1000L).toInt().coerceAtLeast(1)
                durationStr = String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
                val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                if (frame != null) {
                    thumbBase64 = encodeScaledPreview(frame, maxDim = 480, maxChars = 110_000)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            ImportedChatMedia(
                messageType = MessageType.VIDEO,
                previewBase64 = thumbBase64,
                localPath = destFile.absolutePath,
                fileName = fileName.ifBlank { "video_${System.currentTimeMillis()}.mp4" },
                durationText = durationStr
            )
        } else if (isGifFile(mime, fileName) || startsWithGifHeader(context, uri)) {
            val destFile = File(mediaDir, "gif_${UUID.randomUUID()}.gif")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                } ?: return@withContext null
            } catch (_: Exception) {
                return@withContext null
            }
            val firstFrameBitmap = decodeUriBitmap(context, uri, targetMaxDim = 480)
            val previewBase64 = firstFrameBitmap?.let { encodeScaledPreview(it, maxDim = 480, maxChars = 110_000) }
            val resolvedGifName = when {
                fileName.isBlank() -> "gif_${System.currentTimeMillis()}.gif"
                fileName.lowercase(Locale.ROOT).endsWith(".gif") -> fileName
                else -> "$fileName.gif"
            }
            ImportedChatMedia(
                messageType = MessageType.IMAGE,
                previewBase64 = previewBase64,
                localPath = destFile.absolutePath,
                fileName = resolvedGifName,
                durationText = null
            )
        } else {
            val destFile = File(mediaDir, "img_${UUID.randomUUID()}.jpg")
            val fullBitmap = decodeUriBitmap(context, uri, targetMaxDim = 1440) ?: return@withContext null
            try {
                FileOutputStream(destFile).use { out ->
                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
            } catch (_: Exception) {
                return@withContext null
            }
            val previewBase64 = encodeScaledPreview(fullBitmap, maxDim = 480, maxChars = 110_000)
            ImportedChatMedia(
                messageType = MessageType.IMAGE,
                previewBase64 = previewBase64,
                localPath = destFile.absolutePath,
                fileName = fileName.ifBlank { "imagen_${System.currentTimeMillis()}.jpg" },
                durationText = null
            )
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.trim()?.take(120) ?: "" else ""
                } else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun tryOpenBitmap(block: () -> java.io.InputStream?): Bitmap? {
        return try {
            block()?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeUriBitmap(context: Context, uri: Uri, targetMaxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (maxDim / sample > targetMaxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeSquareThumbnail(source: Bitmap, sizePx: Int, maxChars: Int): String? {
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, x, y, side, side)
        val candidateSizes = listOf(sizePx, 220, 160, 120).distinct().filter { it <= sizePx }
        for (dim in candidateSizes) {
            val scaled = Bitmap.createScaledBitmap(cropped, dim, dim, true)
            val encoded = compressToBase64(scaled, maxChars)
            if (encoded != null) return encoded
        }
        return null
    }

    private fun encodeScaledPreview(source: Bitmap, maxDim: Int, maxChars: Int): String? {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h).toFloat())
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, targetW, targetH, true) else source
        return compressToBase64(scaled, maxChars)
    }

    suspend fun saveChatPhotoToGallery(
        context: Context,
        localMediaPath: String?,
        mediaPreviewBase64: String?,
        displayName: String = "MostazaFake_Foto_${System.currentTimeMillis()}.jpg"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val localFile = localMediaPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
            if (localFile != null && isGifFile(null, localFile.name, localFile.absolutePath)) {
                val gifDisplayName = "MostazaFake_GIF_${System.currentTimeMillis()}.gif"
                return@withContext saveRawImageFileToGallery(context, localFile, gifDisplayName, "image/gif")
            }
            val bitmap = localFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?: mediaPreviewBase64?.takeIf { it.isNotBlank() }?.let { raw ->
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                ?: return@withContext Result.failure(IllegalStateException("No hay imagen disponible para guardar"))
            saveBitmapToGalleryInternal(context, bitmap, displayName, asPng = false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncDebugTestGalleryImagesIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext 0
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(MEDIA_PREFS_NAME, Context.MODE_PRIVATE)
        val assetFiles = runCatching {
            appContext.assets.list("test_gallery")?.sorted().orEmpty()
        }.getOrDefault(emptyList())
        if (assetFiles.isEmpty()) return@withContext 0

        var savedCount = 0
        assetFiles.forEach { fileName ->
            val prefKey = "synced_debug_gallery_v1_$fileName"
            if (!prefs.getBoolean(prefKey, false)) {
                val mimeType = when {
                    fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    else -> "image/png"
                }
                val result = runCatching {
                    appContext.assets.open("test_gallery/$fileName").use { input ->
                        saveRawImageStreamToGallery(
                            context = appContext,
                            inputStream = input,
                            displayName = "MostazaTest_$fileName",
                            mimeType = mimeType
                        ).getOrThrow()
                    }
                }
                if (result.isSuccess) {
                    prefs.edit().putBoolean(prefKey, true).apply()
                    savedCount++
                }
            }
        }
        savedCount
    }

    fun savePngBytesToGallery(
        context: Context,
        pngBytes: ByteArray,
        displayName: String = "MostazaFake_Captura_${System.currentTimeMillis()}.png"
    ): Result<Uri> {
        return pngBytes.inputStream().use { input ->
            saveRawImageStreamToGallery(context, input, displayName, "image/png")
        }
    }

    private fun saveRawImageFileToGallery(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Result<Uri> {
        return sourceFile.inputStream().use { input ->
            saveRawImageStreamToGallery(context, input, displayName, mimeType)
        }
    }

    private fun saveRawImageStreamToGallery(
        context: Context,
        inputStream: InputStream,
        displayName: String,
        mimeType: String
    ): Result<Uri> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MostazaFake")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IllegalStateException("MediaStore rechazó el archivo"))
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                inputStream.copyTo(out)
            } ?: throw IllegalStateException("No se pudo abrir el flujo de salida")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            Result.failure(e)
        }
    }

    private fun saveBitmapToGalleryInternal(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        asPng: Boolean = true
    ): Result<Uri> {
        val resolver = context.contentResolver
        val mimeType = if (asPng) "image/png" else "image/jpeg"
        val format = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MostazaFake")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IllegalStateException("MediaStore rechazó el archivo"))
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(format, 95, out)) {
                    throw IllegalStateException("Error al comprimir la imagen")
                }
            } ?: throw IllegalStateException("No se pudo abrir el flujo de salida")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            Result.failure(e)
        }
    }

    private fun compressToBase64(bitmap: Bitmap, maxChars: Int): String? {
        for (quality in listOf(76, 58, 40, 26)) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= maxChars) return encoded
        }
        return null
    }
}
