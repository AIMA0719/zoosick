package com.myinfocar.aicoachstock.data.remote.kis.market

import com.myinfocar.aicoachstock.data.remote.kis.dto.BalanceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyCcldResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasBalanceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasCcnlResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.PeriodProfitResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 한투 OpenAPI 거래(주문/체결) 조회. 본 Phase 1에선 자동 주문 X — 체결 내역만 import.
 *
 *  - inquire-daily-ccld: 일별 주문/체결 내역 (TR_ID TTTC8001R / VTTC8001R)
 *  - 권한 필요: 한투 OpenAPI 신청 시 "주식 잔고/체결 조회" 활성화
 */
interface KisTradingApi {

    /** 주식일별주문체결조회. 응답이 페이징 — ctx_area_nk100 이어받아 반복 호출. */
    @GET
    suspend fun fetchDailyCcld(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("tr_cont") trCont: String = "",
        @Header("custtype") custType: String = "P",
        @Query("CANO") accountNo: String,
        @Query("ACNT_PRDT_CD") productCode: String,
        @Query("INQR_STRT_DT") startDate: String,
        @Query("INQR_END_DT") endDate: String,
        @Query("SLL_BUY_DVSN_CD") sllBuyDvsnCd: String = "00",
        @Query("INQR_DVSN") inqrDvsn: String = "01",
        @Query("PDNO") pdno: String = "",
        @Query("CCLD_DVSN") ccldDvsn: String = "01",
        @Query("ORD_GNO_BRNO") ordGnoBrno: String = "",
        @Query("ODNO") odno: String = "",
        @Query("INQR_DVSN_3") inqrDvsn3: String = "00",
        @Query("INQR_DVSN_1") inqrDvsn1: String = "",
        @Query("CTX_AREA_FK100") ctxFk100: String = "",
        @Query("CTX_AREA_NK100") ctxNk100: String = "",
    ): DailyCcldResponse

    /** 국내 주식잔고조회. TR_ID: TTTC8434R(실전) / VTTC8434R(모의) */
    @GET
    suspend fun fetchBalance(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("tr_cont") trCont: String = "",
        @Header("custtype") custType: String = "P",
        @Query("CANO") accountNo: String,
        @Query("ACNT_PRDT_CD") productCode: String,
        @Query("AFHR_FLPR_YN") afhrFlprYn: String = "N",
        @Query("OFL_YN") oflYn: String = "",
        @Query("INQR_DVSN") inqrDvsn: String = "02",
        @Query("UNPR_DVSN") unprDvsn: String = "01",
        @Query("FUND_STTL_ICLD_YN") fundSttlIcldYn: String = "N",
        @Query("FNCG_AMT_AUTO_RDPT_YN") fncgAmtAutoRdptYn: String = "N",
        @Query("PRCS_DVSN") prcsDvsn: String = "01",
        @Query("CTX_AREA_FK100") ctxFk100: String = "",
        @Query("CTX_AREA_NK100") ctxNk100: String = "",
    ): BalanceResponse

    /** 기간별 손익 일별합산조회. TR_ID: TTTC8708R (실전 전용). */
    @GET
    suspend fun fetchPeriodProfit(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("tr_cont") trCont: String = "",
        @Header("custtype") custType: String = "P",
        @Query("CANO") accountNo: String,
        @Query("ACNT_PRDT_CD") productCode: String,
        @Query("INQR_STRT_DT") startDate: String,
        @Query("INQR_END_DT") endDate: String,
        @Query("SORT_DVSN") sortDvsn: String = "00",
        @Query("PDNO") pdno: String = "",
        @Query("CBLC_DVSN") cblcDvsn: String = "00",
        @Query("CTX_AREA_FK100") ctxFk100: String = "",
        @Query("CTX_AREA_NK100") ctxNk100: String = "",
    ): PeriodProfitResponse

    /** 해외주식 잔고. TR_ID: TTTS3012R / VTTS3012R */
    @GET
    suspend fun fetchOverseasBalance(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("tr_cont") trCont: String = "",
        @Header("custtype") custType: String = "P",
        @Query("CANO") accountNo: String,
        @Query("ACNT_PRDT_CD") productCode: String,
        @Query("OVRS_EXCG_CD") ovrsExcgCd: String = "NASD",
        @Query("TR_CRCY_CD") trCrcyCd: String = "USD",
        @Query("CTX_AREA_FK200") ctxFk200: String = "",
        @Query("CTX_AREA_NK200") ctxNk200: String = "",
    ): OverseasBalanceResponse

    /** 해외주식 주문체결내역. TR_ID: TTTS3035R / VTTS3035R */
    @GET
    suspend fun fetchOverseasCcnl(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("tr_cont") trCont: String = "",
        @Header("custtype") custType: String = "P",
        @Query("CANO") accountNo: String,
        @Query("ACNT_PRDT_CD") productCode: String,
        @Query("PDNO") pdno: String = "%",
        @Query("ORD_STRT_DT") startDate: String,
        @Query("ORD_END_DT") endDate: String,
        @Query("SLL_BUY_DVSN") sllBuyDvsn: String = "00",
        @Query("CCLD_NCCS_DVSN") ccldNccsDvsn: String = "01",
        @Query("OVRS_EXCG_CD") ovrsExcgCd: String = "%",
        @Query("SORT_SQN") sortSqn: String = "DS",
        @Query("ORD_DT") ordDt: String = "",
        @Query("ORD_GNO_BRNO") ordGnoBrno: String = "",
        @Query("ODNO") odno: String = "",
        @Query("CTX_AREA_FK200") ctxFk200: String = "",
        @Query("CTX_AREA_NK200") ctxNk200: String = "",
    ): OverseasCcnlResponse
}
