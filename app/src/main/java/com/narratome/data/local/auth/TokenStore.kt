package com.narratome.data.local.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_token")

/**
 * Session tokens encrypted with Android Keystore AES-GCM before storage in [DataStore].
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.tokenDataStore

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USERNAME = stringPreferencesKey("username")
    }

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username.asStateFlow()

    init {
        // Load initial values synchronously for immediate access on app start
        runBlocking {
            val prefs = dataStore.data.first()
            val storedToken = prefs[Keys.TOKEN]
            val token = storedToken?.let(::decryptToken)
            _token.value = token
            _username.value = prefs[Keys.USERNAME]
            if (!storedToken.isNullOrBlank() && !storedToken.startsWith(TOKEN_PREFIX) && token != null) {
                dataStore.edit { it[Keys.TOKEN] = encryptToken(token) }
            }
        }
    }

    fun getToken(): String? = _token.value

    suspend fun setSession(username: String, token: String) {
        dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = encryptToken(token)
            prefs[Keys.USERNAME] = username
        }
        _token.value = token
        _username.value = username
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
        _token.value = null
        _username.value = null
    }

    private fun encryptToken(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return "$TOKEN_PREFIX${cipher.iv.encodeBase64()}:${ciphertext.encodeBase64()}"
    }

    private fun decryptToken(stored: String): String? {
        if (!stored.startsWith(TOKEN_PREFIX)) return stored
        val parts = stored.removePrefix(TOKEN_PREFIX).split(':', limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, parts[0].decodeBase64()),
            )
            cipher.doFinal(parts[1].decodeBase64()).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun ByteArray.encodeBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "narratome_auth_token"
        const val TOKEN_PREFIX = "v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
