package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /oauth2/tokenP
 * 한투 OpenAPI REST용 Access Token 발급.
 */
@Serializable
data class TokenRequest(
    @SerialName("grant_type") val grantType: String = "client_credentials",
    @SerialName("appkey") val appKey: String,
    @SerialName("appsecret") val appSecret: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("access_token_token_expired") val accessTokenExpired: String? = null,
)

/**
 * POST /oauth2/Approval
 * WebSocket 시세용 approval_key 발급. REST access_token과 별개 인증.
 */
@Serializable
data class ApprovalRequest(
    @SerialName("grant_type") val grantType: String = "client_credentials",
    @SerialName("appkey") val appKey: String,
    @SerialName("secretkey") val secretKey: String,
)

@Serializable
data class ApprovalResponse(
    @SerialName("approval_key") val approvalKey: String,
)
