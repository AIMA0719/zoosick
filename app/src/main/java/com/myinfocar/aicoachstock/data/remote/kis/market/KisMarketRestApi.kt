package com.myinfocar.aicoachstock.data.remote.kis.market

import com.myinfocar.aicoachstock.data.remote.kis.dto.KrPriceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.UsPriceResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 한투 REST 시세 조회. base URL은 KisEnv.restBaseUrl을 @Url로 동적 주입.
 *
 *  - 모든 호출에 authorization/appkey/appsecret/tr_id 헤더 필수.
 *  - 분당 호출 한도(공식 20회/초) 초과 금지 — RateLimiter 적용.
 */
interface KisMarketRestApi {

    /** 국내 주식 현재가. TR_ID: FHKST01010100 */
    @GET
    suspend fun fetchKrPrice(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("fid_cond_mrkt_div_code") mrktDivCode: String = "J",
        @Query("fid_input_iscd") ticker: String,
    ): KrPriceResponse

    /** 해외 주식 현재가. TR_ID: HHDFS00000300 */
    @GET
    suspend fun fetchUsPrice(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("AUTH") auth: String = "",
        @Query("EXCD") excd: String,
        @Query("SYMB") symbol: String,
    ): UsPriceResponse
}
