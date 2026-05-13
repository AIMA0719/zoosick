package com.myinfocar.aicoachstock.data.local.secure

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PassphraseProvider {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
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

    override fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP))
            .apply()
        return fresh
    }

    private companion object {
        const val MASTER_KEY_ALIAS = "aicoachstock_master_key"
        const val FILE_NAME = "aicoachstock_secure_prefs"
        const val KEY_PASSPHRASE = "sqlcipher_passphrase_b64"
        const val PASSPHRASE_BYTES = 32
    }
}
