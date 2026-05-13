package com.myinfocar.aicoachstock.domain.auth

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * 한투 OpenAPI 자격증명 보관소.
 *
 * - 평문 Room 저장 X (Room이 노출돼도 인증정보는 별도 키스토어로 격리)
 * - EncryptedSharedPreferences + Android Keystore MasterKey 기반
 * - Access Token / approval_key는 만료 시각 함께 저장 → 자동 재발급 로직에서 참조
 */
interface ApiCredentialStore {
    fun observeCredentials(): Flow<ApiCredentials?>

    suspend fun current(): ApiCredentials?

    suspend fun saveAppKey(appKey: String, appSecret: String, env: KisEnv)
    suspend fun saveAccessToken(token: String, expiresAt: Instant)
    suspend fun saveApprovalKey(key: String, expiresAt: Instant)
    /** 한투 계좌번호 (8자리) + 상품코드 (2자리, 보통 "01"). 체결 import에 필요. */
    suspend fun saveAccount(accountNo: String, productCode: String)

    suspend fun clear()
}

data class ApiCredentials(
    val appKey: String,
    val appSecret: String,
    val env: KisEnv,
    val accessToken: String?,
    val accessTokenExpiresAt: Instant?,
    val approvalKey: String?,
    val approvalKeyExpiresAt: Instant?,
    val accountNo: String? = null,
    val productCode: String? = null,
) {
    fun isAccessTokenValid(now: Instant = Instant.now()): Boolean =
        accessToken != null && accessTokenExpiresAt != null && accessTokenExpiresAt.isAfter(now)

    fun isApprovalKeyValid(now: Instant = Instant.now()): Boolean =
        approvalKey != null && approvalKeyExpiresAt != null && approvalKeyExpiresAt.isAfter(now)
}

/**
 * 한투 OpenAPI 환경. **실전(PROD) 전용** — 모의(VTS)는 사용하지 않음.
 * (과거 호환: enum이지만 PROD 단일 멤버. 사용자 선택 UI 제거.)
 */
enum class KisEnv(val restBaseUrl: String, val wsUrl: String) {
    PROD(
        restBaseUrl = "https://openapi.koreainvestment.com:9443",
        wsUrl = "ws://ops.koreainvestment.com:21000",
    ),
}
