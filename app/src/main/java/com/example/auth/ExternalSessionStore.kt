package com.example.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

object AndroidKeystoreSecretCipher : SecretCipher {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "mostazachat_backup_aes_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = getOrCreateKey() ?: return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    override fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        val key = getOrCreateKey() ?: return ""
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH_BYTES) return ""
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val encrypted = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

object ExternalSessionStore {
    private const val PREFS_NAME = "mostazachat_external_session_prefs"
    private const val KEY_ACTIVE = "ext_active"
    private const val KEY_PROVIDER = "ext_provider"
    private const val KEY_ACCOUNT_LABEL = "ext_account_label"
    private const val KEY_REMOTE_ROUTE = "ext_remote_route"
    private const val KEY_SAVED_DOC_URI = "ext_saved_doc_uri"
    private const val KEY_AUTH_USERNAME_ENC = "ext_auth_username_enc"
    private const val KEY_AUTH_SECRET_ENC = "ext_auth_secret_enc"

    fun load(
        context: Context,
        cipher: SecretCipher = AndroidKeystoreSecretCipher
    ): ExternalSessionConfig? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val provider = BackupProvider.fromName(prefs.getString(KEY_PROVIDER, null)) ?: return null
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val accountLabel = prefs.getString(KEY_ACCOUNT_LABEL, "").orEmpty()
        val remoteRoute = prefs.getString(KEY_REMOTE_ROUTE, "").orEmpty()
        val savedDocUri = prefs.getString(KEY_SAVED_DOC_URI, "").orEmpty()
        val encUser = prefs.getString(KEY_AUTH_USERNAME_ENC, "").orEmpty()
        val encSecret = prefs.getString(KEY_AUTH_SECRET_ENC, "").orEmpty()

        return ExternalSessionConfig(
            active = active,
            provider = provider,
            accountLabel = accountLabel,
            remoteRoute = remoteRoute,
            savedDocumentUri = savedDocUri,
            authUsername = cipher.decrypt(encUser),
            authSecret = cipher.decrypt(encSecret)
        )
    }

    fun save(
        context: Context,
        config: ExternalSessionConfig,
        cipher: SecretCipher = AndroidKeystoreSecretCipher
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ACTIVE, config.active)
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_ACCOUNT_LABEL, config.accountLabel.trim().take(80))
            .putString(KEY_REMOTE_ROUTE, config.remoteRoute.trim().take(500))
            .putString(KEY_SAVED_DOC_URI, config.savedDocumentUri.trim().take(500))
            .putString(KEY_AUTH_USERNAME_ENC, cipher.encrypt(config.authUsername.trim().take(120)))
            .putString(KEY_AUTH_SECRET_ENC, cipher.encrypt(config.authSecret.trim().take(300)))
            .apply()
    }

    fun deactivateSession(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
