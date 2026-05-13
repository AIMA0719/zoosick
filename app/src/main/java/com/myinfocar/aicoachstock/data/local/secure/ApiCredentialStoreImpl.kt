package com.myinfocar.aicoachstock.data.local.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.auth.ApiCredentials
import com.myinfocar.aicoachstock.domain.auth.KisEnv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiCredentialStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApiCredentialStore {

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

    private val emitter = MutableStateFlow<ApiCredentials?>(loadFromPrefs())

    override fun observeCredentials(): Flow<ApiCredentials?> = emitter

    override suspend fun current(): ApiCredentials? = emitter.value

    override suspend fun saveAppKey(appKey: String, appSecret: String, env: KisEnv) {
        prefs.edit()
            .putString(K_APP_KEY, appKey)
            .putString(K_APP_SECRET, appSecret)
            .putString(K_ENV, env.name)
            .apply()
        emitter.value = loadFromPrefs()
    }

    override suspend fun saveAccessToken(token: String, expiresAt: Instant) {
        prefs.edit()
            .putString(K_ACCESS_TOKEN, token)
            .putLong(K_ACCESS_TOKEN_EXP, expiresAt.toEpochMilli())
            .apply()
        emitter.value = loadFromPrefs()
    }

    override suspend fun saveApprovalKey(key: String, expiresAt: Instant) {
        prefs.edit()
            .putString(K_APPROVAL_KEY, key)
            .putLong(K_APPROVAL_KEY_EXP, expiresAt.toEpochMilli())
            .apply()
        emitter.value = loadFromPrefs()
    }

    override suspend fun saveAccount(accountNo: String, productCode: String) {
        prefs.edit()
            .putString(K_ACCOUNT_NO, accountNo)
            .putString(K_PRODUCT_CODE, productCode)
            .apply()
        emitter.value = loadFromPrefs()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
        emitter.value = null
    }

    private fun loadFromPrefs(): ApiCredentials? {
        val appKey = prefs.getString(K_APP_KEY, null) ?: return null
        val appSecret = prefs.getString(K_APP_SECRET, null) ?: return null
        val envName = prefs.getString(K_ENV, null) ?: return null
        return ApiCredentials(
            appKey = appKey,
            appSecret = appSecret,
            env = runCatching { KisEnv.valueOf(envName) }.getOrNull() ?: return null,
            accessToken = prefs.getString(K_ACCESS_TOKEN, null),
            accessTokenExpiresAt = prefs.getLong(K_ACCESS_TOKEN_EXP, 0L)
                .takeIf { it > 0L }?.let(Instant::ofEpochMilli),
            approvalKey = prefs.getString(K_APPROVAL_KEY, null),
            approvalKeyExpiresAt = prefs.getLong(K_APPROVAL_KEY_EXP, 0L)
                .takeIf { it > 0L }?.let(Instant::ofEpochMilli),
            accountNo = prefs.getString(K_ACCOUNT_NO, null),
            productCode = prefs.getString(K_PRODUCT_CODE, null),
        )
    }

    private companion object {
        const val MASTER_KEY_ALIAS = "aicoachstock_credential_master_key"
        const val FILE_NAME = "aicoachstock_kis_credentials"

        const val K_APP_KEY = "kis_app_key"
        const val K_APP_SECRET = "kis_app_secret"
        const val K_ENV = "kis_env"
        const val K_ACCESS_TOKEN = "kis_access_token"
        const val K_ACCESS_TOKEN_EXP = "kis_access_token_expires_at"
        const val K_APPROVAL_KEY = "kis_approval_key"
        const val K_APPROVAL_KEY_EXP = "kis_approval_key_expires_at"
        const val K_ACCOUNT_NO = "kis_account_no"
        const val K_PRODUCT_CODE = "kis_product_code"
    }
}
