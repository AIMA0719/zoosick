package com.myinfocar.aicoachstock.data.remote.kis.auth

import com.myinfocar.aicoachstock.data.remote.kis.dto.ApprovalRequest
import com.myinfocar.aicoachstock.data.remote.kis.dto.TokenRequest
import com.myinfocar.aicoachstock.data.remote.kis.rest.KisAuthApi
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import retrofit2.HttpException
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 OpenAPI 인증 흐름 (REST 토큰 + WS approval_key).
 *
 * - access_token: expires_in(초) 응답값으로 만료 계산
 * - approval_key: 응답에 만료 없음 → 보수적으로 24h 가정 (PRD NEEDS CLARIFICATION)
 * - 만료 시 ensureAccessToken/ensureApprovalKey가 자동 재발급
 */
@Singleton
class KisAuthService @Inject constructor(
    private val api: KisAuthApi,
    private val store: ApiCredentialStore,
) {

    suspend fun refreshAccessToken(): Result<String> = kisCall {
        val creds = store.current() ?: error("API 키가 설정되지 않았습니다")
        val url = creds.env.restBaseUrl + "/oauth2/tokenP"
        val response = api.issueToken(
            url = url,
            req = TokenRequest(
                appKey = creds.appKey,
                appSecret = creds.appSecret,
            ),
        )
        val expiresAt = Instant.now().plusSeconds(response.expiresIn)
        store.saveAccessToken(response.accessToken, expiresAt)
        response.accessToken
    }

    suspend fun refreshApprovalKey(): Result<String> = kisCall {
        val creds = store.current() ?: error("API 키가 설정되지 않았습니다")
        val url = creds.env.restBaseUrl + "/oauth2/Approval"
        val response = api.issueApprovalKey(
            url = url,
            req = ApprovalRequest(
                appKey = creds.appKey,
                secretKey = creds.appSecret,
            ),
        )
        val expiresAt = Instant.now().plus(APPROVAL_KEY_TTL_HOURS, ChronoUnit.HOURS)
        store.saveApprovalKey(response.approvalKey, expiresAt)
        response.approvalKey
    }

    /**
     * 한투 REST 호출 래퍼.
     * HttpException 시 errorBody까지 메시지에 담아서 한투 응답 원본(error_code/error_description)을 볼 수 있게.
     */
    private suspend inline fun <T> kisCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            Result.failure(RuntimeException("HTTP ${e.code()} — ${body ?: e.message()}"))
        } catch (e: Throwable) {
            Result.failure(e)
        }

    /** 만료 임박/만료면 자동 재발급. 유효한 access_token 반환. */
    suspend fun ensureAccessToken(): Result<String> {
        val now = Instant.now()
        val cur = store.current()
        // 만료 1분 전부터 미리 갱신 (race 방지)
        val withSafetyMargin = now.plusSeconds(60)
        return if (cur?.accessToken != null &&
            cur.accessTokenExpiresAt != null &&
            cur.accessTokenExpiresAt.isAfter(withSafetyMargin)
        ) {
            Result.success(cur.accessToken)
        } else {
            refreshAccessToken()
        }
    }

    suspend fun ensureApprovalKey(): Result<String> {
        val now = Instant.now()
        val cur = store.current()
        val withSafetyMargin = now.plusSeconds(60)
        return if (cur?.approvalKey != null &&
            cur.approvalKeyExpiresAt != null &&
            cur.approvalKeyExpiresAt.isAfter(withSafetyMargin)
        ) {
            Result.success(cur.approvalKey)
        } else {
            refreshApprovalKey()
        }
    }

    private companion object {
        const val APPROVAL_KEY_TTL_HOURS = 24L
    }
}
