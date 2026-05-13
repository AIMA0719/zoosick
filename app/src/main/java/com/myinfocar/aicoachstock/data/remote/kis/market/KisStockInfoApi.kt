package com.myinfocar.aicoachstock.data.remote.kis.market

import com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.CountriesHolidayResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.DividendResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.EstimatePerformResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.FinanceRatioResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.InvestOpinionResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.InvestorTrendResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.MarketFundsResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.MarketInvestorDailyResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasNewsResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.SearchStockInfoResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.TimeChartResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 한투 OpenAPI 종목 정보 / 재무 / 차트 / 휴장일.
 *
 *  - 종목 기본조회: search-stock-info (TR_ID CTPF1604R)
 *  - 재무비율: finance/financial-ratio (FHKST66430300)
 *  - 일봉 차트: inquire-daily-itemchartprice (FHKST03010100)
 *  - 해외 휴장일: overseas-stock countries-holiday (CTOS5011R)
 */
interface KisStockInfoApi {

    /** 주식 기본 조회 (이름/섹터/상장주식수/52w high-low). TR_ID: CTPF1604R */
    @GET
    suspend fun searchStockInfo(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("PRDT_TYPE_CD") prdtTypeCd: String = "300",
        @Query("PDNO") pdno: String,
    ): SearchStockInfoResponse

    /** 재무비율 (ROE/EPS/BPS/부채비율/유보율 등). TR_ID: FHKST66430300 */
    @GET
    suspend fun financialRatio(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_DIV_CLS_CODE") fidDivClsCode: String = "0",
        @Query("fid_cond_mrkt_div_code") fidCondMrktDivCode: String = "J",
        @Query("fid_input_iscd") fidInputIscd: String,
    ): FinanceRatioResponse

    /** 일/주/월/년 차트 조회. TR_ID: FHKST03010100. period_code = D/W/M/Y */
    @GET
    suspend fun dailyChart(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_COND_MRKT_DIV_CODE") fidCondMrktDivCode: String = "J",
        @Query("FID_INPUT_ISCD") fidInputIscd: String,
        @Query("FID_INPUT_DATE_1") fidInputDate1: String,
        @Query("FID_INPUT_DATE_2") fidInputDate2: String,
        @Query("FID_PERIOD_DIV_CODE") fidPeriodDivCode: String = "D",
        @Query("FID_ORG_ADJ_PRC") fidOrgAdjPrc: String = "0",
    ): DailyChartResponse

    /** 국가별 휴장일 조회. TR_ID: CTOS5011R */
    @GET
    suspend fun countriesHoliday(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("BASS_DT") bassDate: String,
        @Query("CTX_AREA_NK") ctxAreaNk: String = "",
        @Query("CTX_AREA_FK") ctxAreaFk: String = "",
    ): CountriesHolidayResponse

    /** 호가/예상체결. TR_ID: FHKST01010200 */
    @GET
    suspend fun askingPrice(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_COND_MRKT_DIV_CODE") fidCondMrktDivCode: String = "J",
        @Query("FID_INPUT_ISCD") fidInputIscd: String,
    ): AskingPriceResponse

    /** 투자자별 매매동향(개인/외국인/기관 일별 순매수). TR_ID: FHKST01010900 */
    @GET
    suspend fun investorTrend(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_COND_MRKT_DIV_CODE") fidCondMrktDivCode: String = "J",
        @Query("FID_INPUT_ISCD") fidInputIscd: String,
    ): InvestorTrendResponse

    /** 해외 뉴스종합 (제목). TR_ID: HHPSTH60100C1. */
    @GET
    suspend fun overseasNews(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("INFO_GB") infoGb: String = "",
        @Query("CLASS_CD") classCd: String = "",
        @Query("NATION_CD") nationCd: String = "",
        @Query("EXCHANGE_CD") exchangeCd: String = "",
        @Query("SYMB") symb: String = "",
        @Query("DATA_DT") dataDt: String = "",
        @Query("DATA_TM") dataTm: String = "",
        @Query("CTS") cts: String = "",
    ): OverseasNewsResponse

    /** 추정실적. TR_ID: HHKST668300C0 */
    @GET
    suspend fun estimatePerform(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("SHT_CD") shtCd: String,
    ): EstimatePerformResponse

    /** 종목 투자의견 (증권사 목표가). TR_ID: FHKST663300C0 */
    @GET
    suspend fun investOpinion(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_COND_MRKT_DIV_CODE") fidCondMrktDivCode: String = "J",
        @Query("FID_COND_SCR_DIV_CODE") fidCondScrDivCode: String = "16633",
        @Query("FID_INPUT_ISCD") fidInputIscd: String,
        @Query("FID_INPUT_DATE_1") fidInputDate1: String,
        @Query("FID_INPUT_DATE_2") fidInputDate2: String,
    ): InvestOpinionResponse

    /** 주식 분봉 차트. TR_ID: FHKST03010200. INPUT_HOUR = HHMMSS (현재 시각 또는 끝 시각). */
    @GET
    suspend fun timeChart(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_ETC_CLS_CODE") fidEtcClsCode: String = "",
        @Query("FID_COND_MRKT_DIV_CODE") fidCondMrktDivCode: String = "J",
        @Query("FID_INPUT_ISCD") fidInputIscd: String,
        @Query("FID_INPUT_HOUR_1") fidInputHour: String,
        @Query("FID_PW_DATA_INCU_YN") fidPwDataIncuYn: String = "N",
    ): TimeChartResponse

    /** 예탁원정보 - 배당일정. TR_ID: HHKDB669102C0 */
    @GET
    suspend fun dividend(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("CTS") cts: String = "",
        @Query("GB1") gb1: String = "0",
        @Query("F_DT") fromDate: String,        // YYYYMMDD
        @Query("T_DT") toDate: String,
        @Query("SHT_CD") shtCd: String = "",     // 종목코드(공란이면 전체)
        @Query("HIGH_GB") highGb: String = "",
    ): DividendResponse

    /** 시장별 투자자매매동향 (일별 누적). TR_ID: FHPTJ04040000 */
    @GET
    suspend fun marketInvestorDaily(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
        @Query("FID_COND_MRKT_DIV_CODE") fidCondMrktDivCode: String = "U",
        @Query("FID_INPUT_ISCD") fidInputIscd: String = "0001", // 0001=KOSPI, 1001=KOSDAQ
        @Query("FID_INPUT_DATE_1") fidInputDate1: String,
        @Query("FID_INPUT_DATE_2") fidInputDate2: String,
        @Query("FID_INPUT_ISCD_1") fidInputIscd1: String = "0000",
    ): MarketInvestorDailyResponse

    /** 시장 예수금 / 신용잔고. TR_ID: FHPST01060000 */
    @GET
    suspend fun marketFunds(
        @Url url: String,
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String,
        @Header("custtype") custType: String = "P",
    ): MarketFundsResponse
}
