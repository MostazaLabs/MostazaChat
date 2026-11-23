package com.example.media

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object RemoteBackupClient {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024

    fun validateRouteInput(rawRoute: String): Result<String> {
        val trimmed = rawRoute.trim()
        if (trimmed.isEmpty()) return Result.success("")
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) {
            return Result.failure(IllegalArgumentException("Esquema de ruta no permitido por seguridad"))
        }
        val parsed = try {
            URI(trimmed)
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("Formato de ruta o URL inválido"))
        }
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val allowed = setOf("https", "http", "content", "smb", "ftp", "sftp")
        if (scheme !in allowed) {
            return Result.failure(
                IllegalArgumentException("Usa una ruta https://, http://, smb://, ftp://, sftp:// o selector del sistema")
            )
        }
        if (scheme in setOf("https", "http") && parsed.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Falta el host o dominio en la URL"))
        }
        return Result.success(trimmed.take(500))
    }

    fun isHttpUrl(route: String): Boolean {
        val scheme = try {
            URI(route.trim()).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }
        return scheme == "https" || scheme == "http"
    }

    suspend fun fetchRemoteText(
        routeUrl: String,
        username: String = "",
        secret: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val validated = validateRouteInput(routeUrl).getOrThrow()
            if (!isHttpUrl(validated)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Para descarga directa usa una URL https:// o http:// (para SMB/FTP/SFTP o nubes locales usa el selector de archivos)")
                )
            }

            val connection = (URL(validated).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MostazaChat/1.0")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                buildAuthorizationHeader(username, secret)?.let {
                    setRequestProperty("Authorization", it)
                }
            }

            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val hint = when (code) {
                        401, 403 -> "Autorización denegada ($code). Revisa usuario, contraseña o token."
                        404 -> "Archivo no encontrado (404) en esa ruta."
                        in 300..399 -> "La ruta devolvió una redirección ($code). Usa la URL final directa."
                        else -> "El servidor respondió con código HTTP $code."
                    }
                    return@withContext Result.failure(IllegalStateException(hint))
                }

                val content = BufferedInputStream(connection.inputStream).use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw IllegalStateException("El archivo remoto supera el límite de 15 MB")
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
                if (content.isBlank()) {
                    Result.failure(IllegalStateException("El archivo remoto está vacío"))
                } else {
                    Result.success(content)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            val message = e.localizedMessage.orEmpty()
            if (message.contains("CLEARTEXT", ignoreCase = true)) {
                Result.failure(
                    IllegalStateException("Android bloqueó HTTP sin cifrar para este servidor. Usa https:// o elige el archivo mediante el selector del sistema.")
                )
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadRemoteBackup(
        routeUrl: String,
        jsonPayload: String,
        username: String = "",
        secret: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val validated = validateRouteInput(routeUrl).getOrThrow()
            if (!isHttpUrl(validated)) {
                return@withContext Result.failure(
                    IllegalArgumentException("La subida directa requiere una URL https:// o WebDAV (para otras nubes usa 'Guardar copia en nube / archivo')")
                )
            }

            val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
            val connection = (URL(validated).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("User-Agent", "MostazaChat/1.0")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                buildAuthorizationHeader(username, secret)?.let {
                    setRequestProperty("Authorization", it)
                }
            }

            try {
                connection.outputStream.use { out ->
                    out.write(bytes)
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    Result.success(Unit)
                } else {
                    val hint = when (code) {
                        401, 403 -> "Acceso denegado ($code). Verifica las credenciales WebDAV/remotas."
                        405 -> "El servidor no acepta subidas PUT en esta ruta (405). Usa un endpoint WebDAV o el selector de archivos."
                        else -> "No se pudo subir la copia (HTTP $code)."
                    }
                    Result.failure(IllegalStateException(hint))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            val message = e.localizedMessage.orEmpty()
            if (message.contains("CLEARTEXT", ignoreCase = true)) {
                Result.failure(
                    IllegalStateException("Android bloqueó HTTP sin cifrar. Usa https:// o guarda la copia con el selector del sistema.")
                )
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun readTextFromContentUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo seleccionado"))
            if (text.isBlank()) {
                Result.failure(IllegalStateException("El archivo seleccionado está vacío"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeTextToContentUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("No se pudo escribir en la ubicación elegida"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildAuthorizationHeader(username: String, secret: String): String? {
        val cleanUser = username.trim()
        val cleanSecret = secret.trim()
        if (cleanUser.isEmpty() && cleanSecret.isEmpty()) return null
        if (cleanUser.isNotEmpty()) {
            val token = Base64.encodeToString("$cleanUser:$cleanSecret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "Basic $token"
        }
        return if (cleanSecret.startsWith("Bearer ", ignoreCase = true) ||
            cleanSecret.startsWith("Basic ", ignoreCase = true)
        ) {
            cleanSecret
        } else {
            "Bearer $cleanSecret"
        }
    }
}
