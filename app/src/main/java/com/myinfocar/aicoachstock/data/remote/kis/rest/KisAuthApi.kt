package com.myinfocar.aicoachstock.data.remote.kis.rest

import com.myinfocar.aicoachstock.data.remote.kis.dto.ApprovalRequest
import com.myinfocar.aicoachstock.data.remote.kis.dto.ApprovalResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.TokenRequest
import com.myinfocar.aicoachstock.data.remote.kis.dto.TokenResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * 한투 OpenAPI 인증 엔드포인트.
 *
 * Base URL은 환경(PROD/VTS)에 따라 다르므로 @Url 어노테이션으로 동적 지정.
 *  - PROD: https://openapi.koreainvestment.com:9443
 *  - VTS:  https://openapivts.koreainvestment.com:29443
 */
interface KisAuthApi {

    @POST
    suspend fun issueToken(
        @Url url: String,
        @Body req: TokenRequest,
    ): TokenResponse

    @POST
    suspend fun issueApprovalKey(
        @Url url: String,
        @Body req: ApprovalRequest,
    ): ApprovalResponse
}
