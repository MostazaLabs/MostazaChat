package com.example

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.BackupProvider
import com.example.auth.ExternalSessionConfig
import com.example.auth.ExternalSessionStore
import com.example.auth.ProviderCategory
import com.example.auth.SecretCipher
import com.example.data.local.LocalChatDatabase
import com.example.data.local.LocalChatMockRepository
import com.example.data.model.WallpaperTheme
import com.example.media.RemoteBackupClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiCloudAndRemoteBackupTest {

    private lateinit var context: Context
    private lateinit var db: LocalChatDatabase
    private lateinit var repo: LocalChatMockRepository

    private val testCipher = object : SecretCipher {
        override fun encrypt(plainText: String): String =
            if (plainText.isEmpty()) "" else Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        override fun decrypt(cipherText: String): String =
            if (cipherText.isEmpty()) "" else String(Base64.decode(cipherText, Base64.NO_WRAP), Charsets.UTF_8)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ExternalSessionStore.clearAll(context)
        db = Room.inMemoryDatabaseBuilder(context, LocalChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LocalChatMockRepository(db.chatDao(), LocalChatMockRepository.DEFAULT_GUEST_USER_ID)
    }

    @After
    fun tearDown() {
        db.close()
        ExternalSessionStore.clearAll(context)
    }

    @Test
    fun includesAllRequestedCloudAndRemoteProviders() {
        val cloudProviders = BackupProvider.byCategory(ProviderCategory.CLOUD)
        val remoteProviders = BackupProvider.byCategory(ProviderCategory.REMOTE)

        assertTrue(cloudProviders.contains(BackupProvider.DROPBOX))
        assertTrue(cloudProviders.contains(BackupProvider.GOOGLE_DRIVE))
        assertTrue(cloudProviders.contains(BackupProvider.ONEDRIVE))
        assertTrue(cloudProviders.contains(BackupProvider.BOX))
        assertTrue(cloudProviders.contains(BackupProvider.FACEBOOK_META))
        assertTrue(cloudProviders.contains(BackupProvider.PROTON_MAIL))
        assertTrue(cloudProviders.contains(BackupProvider.TERABOX))
        assertTrue(cloudProviders.contains(BackupProvider.INTERNXT))

        assertTrue(remoteProviders.contains(BackupProvider.PERSONAL_REMOTE))
        assertTrue(remoteProviders.contains(BackupProvider.LOCAL_NETWORK))
        assertTrue(remoteProviders.contains(BackupProvider.SMB))
        assertTrue(remoteProviders.contains(BackupProvider.FTP))
        assertTrue(remoteProviders.contains(BackupProvider.SFTP))
        assertTrue(remoteProviders.contains(BackupProvider.WEBDAV))
    }

    @Test
    fun validatesBackupRoutesSafely() {
        assertTrue(RemoteBackupClient.validateRouteInput("https://dav.internxt.com/backup.json").isSuccess)
        assertTrue(RemoteBackupClient.validateRouteInput("smb://192.168.1.25/Backups").isSuccess)
        assertTrue(RemoteBackupClient.validateRouteInput("ftp://192.168.1.25:21/chats").isSuccess)
        assertTrue(RemoteBackupClient.validateRouteInput("sftp://user@192.168.1.25/chats").isSuccess)
        assertTrue(RemoteBackupClient.validateRouteInput("").isSuccess)

        assertTrue(RemoteBackupClient.validateRouteInput("javascript:alert(1)").isFailure)
        assertTrue(RemoteBackupClient.validateRouteInput("file:///data/data/app/db").isFailure)
        assertTrue(RemoteBackupClient.validateRouteInput("data:text/plain,abc").isFailure)
        assertTrue(RemoteBackupClient.validateRouteInput("https://").isFailure)
    }

    @Test
    fun persistsAndDeactivatesExternalSessionConfig() {
        val config = ExternalSessionConfig(
            active = true,
            provider = BackupProvider.PROTON_MAIL,
            accountLabel = "pablo@proton.me",
            remoteRoute = "https://drive.proton.me/respaldos/mostaza.json",
            authUsername = "pablo@proton.me",
            authSecret = "mi_token_secreto_123"
        )
        ExternalSessionStore.save(context, config, testCipher)

        val loaded = ExternalSessionStore.load(context, testCipher)
        assertNotNull(loaded)
        assertTrue(loaded!!.active)
        assertEquals(BackupProvider.PROTON_MAIL, loaded.provider)
        assertEquals("pablo@proton.me", loaded.accountLabel)
        assertEquals("https://drive.proton.me/respaldos/mostaza.json", loaded.remoteRoute)
        assertEquals("mi_token_secreto_123", loaded.authSecret)

        ExternalSessionStore.deactivateSession(context)
        val afterExit = ExternalSessionStore.load(context, testCipher)
        assertFalse(afterExit!!.active)
    }

    @Test
    fun uploadsAndDownloadsBackupViaRealHttpWebDavEndpoint() = runTest {
        val storedBody = AtomicReference<String>("")
        val expectedAuth = "Basic " + Base64.encodeToString("mostaza:clave99".toByteArray(), Base64.NO_WRAP)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/backup.json") { exchange ->
            val auth = exchange.requestHeaders.getFirst("Authorization")
            if (auth != expectedAuth) {
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
                return@createContext
            }
            when (exchange.requestMethod) {
                "PUT" -> {
                    val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    storedBody.set(body)
                    exchange.sendResponseHeaders(201, -1)
                }
                "GET" -> {
                    val bytes = storedBody.get().toByteArray(Charsets.UTF_8)
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                else -> exchange.sendResponseHeaders(405, -1)
            }
            exchange.close()
        }
        server.start()

        try {
            val port = server.address.port
            val endpoint = "http://127.0.0.1:$port/backup.json"

            // Create local chat, export JSON, upload via PUT
            val chatId = repo.createChat(
                userId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID,
                contactName = "Carlos Remoto",
                groupChat = false,
                statusText = "en línea",
                online = true,
                verified = false,
                avatarEmoji = "☁️",
                avatarColorHex = "#0F766E",
                avatarImageBase64 = null,
                wallpaperTheme = WallpaperTheme.CLASSIC,
                unreadCount = 0L,
                pinned = false
            ).getOrThrow()

            val exportedJson = repo.exportWorkspaceToJson(LocalChatMockRepository.DEFAULT_GUEST_USER_ID).getOrThrow()
            RemoteBackupClient.uploadRemoteBackup(
                routeUrl = endpoint,
                jsonPayload = exportedJson,
                username = "mostaza",
                secret = "clave99"
            ).getOrThrow()

            assertTrue(storedBody.get().contains("Carlos Remoto"))

            // Wrong credentials fail cleanly with HTTP 401
            val wrongAuth = RemoteBackupClient.fetchRemoteText(endpoint, "mostaza", "incorrecta")
            assertTrue(wrongAuth.isFailure)

            // Delete local chat, fetch via GET from remote endpoint and restore
            repo.deleteChat(LocalChatMockRepository.DEFAULT_GUEST_USER_ID, chatId).getOrThrow()
            assertTrue(repo.observeChats(LocalChatMockRepository.DEFAULT_GUEST_USER_ID).first().isEmpty())

            val downloadedJson = RemoteBackupClient.fetchRemoteText(endpoint, "mostaza", "clave99").getOrThrow()
            val restored = repo.importWorkspaceFromJson(LocalChatMockRepository.DEFAULT_GUEST_USER_ID, downloadedJson).getOrThrow()
            assertEquals(1, restored)
            assertEquals("Carlos Remoto", repo.observeChats(LocalChatMockRepository.DEFAULT_GUEST_USER_ID).first().first().contactName)
        } finally {
            server.stop(0)
        }
    }
}
