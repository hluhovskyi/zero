package com.hluhovskyi.zero.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SecureKeyValueStore] backed by [EncryptedSharedPreferences] (Keystore-wrapped). The master key
 * is device-bound, so `zero_secure_prefs` must stay out of Auto Backup — see docs/agents/backup.md.
 */
internal class AndroidSecureKeyValueStore(
    private val context: Context,
) : SecureKeyValueStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun put(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).commit()
    }

    override suspend fun remove(key: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).commit()
    }

    companion object {
        const val FILE_NAME = "zero_secure_prefs"
    }
}
