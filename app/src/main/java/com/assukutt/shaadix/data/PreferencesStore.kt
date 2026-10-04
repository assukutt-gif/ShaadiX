package com.assukutt.shaadix.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsStore by preferencesDataStore(name = "shaadix_settings")
private val Context.secureTokenStore by preferencesDataStore(name = "shaadix_secure_tokens")

class PreferencesStore(private val context: Context) {
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val fcmTokenKey = stringPreferencesKey("fcm_device_token")

    val onboardingComplete: Flow<Boolean> = context.settingsStore.data.map { it[onboardingKey] ?: false }
    val darkTheme: Flow<Boolean> = context.settingsStore.data.map { it[darkThemeKey] ?: false }

    suspend fun finishOnboarding() { context.settingsStore.edit { it[onboardingKey] = true } }
    suspend fun setDarkTheme(enabled: Boolean) { context.settingsStore.edit { it[darkThemeKey] = enabled } }
    suspend fun saveFcmToken(token:String){context.settingsStore.edit{it[fcmTokenKey]=token}}
}

/** DataStore holds only AES-GCM ciphertext; the encryption key is generated in Android Keystore. */
class SecureTokenStore(private val context: Context) {
    private val accessKey = stringPreferencesKey("access_token_ciphertext")
    private val refreshKey = stringPreferencesKey("refresh_token_ciphertext")
    private val alias = "shaadix.jwt.aes.v1"

    suspend fun save(accessToken: String, refreshToken: String) {
        context.secureTokenStore.edit {
            it[accessKey] = encrypt(accessToken)
            it[refreshKey] = encrypt(refreshToken)
        }
    }

    suspend fun accessToken(): String? = context.secureTokenStore.data.first()[accessKey]?.let(::decrypt)
    suspend fun refreshToken(): String? = context.secureTokenStore.data.first()[refreshKey]?.let(::decrypt)
    suspend fun clear() { context.secureTokenStore.edit { it.remove(accessKey); it.remove(refreshKey) } }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(doFinal(encrypted), Charsets.UTF_8)
        }
    }.getOrNull()
}

