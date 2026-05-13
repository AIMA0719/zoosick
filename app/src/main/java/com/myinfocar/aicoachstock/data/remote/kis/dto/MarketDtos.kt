package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 국내주식 현재가 조회 (TR_ID: FHKST01010100)
 * GET /uapi/domestic-stock/v1/quotations/inquire-price
 *  params: fid_cond_mrkt_div_code=J, fid_input_iscd=종목코드(6자리)
 */
@Serializable
data class KrPriceResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: KrPriceOutput? = null,
)

@Serializable
data class KrPriceOutput(
    /** 현재가 */
    @SerialName("stck_prpr") val price: String? = null,
    /** 전일대비 */
    @SerialName("prdy_vrss") val change: String? = null,
    /** 전일대비율 */
    @SerialName("prdy_ctrt") val changePct: String? = null,
    /** 누적거래량 */
    @SerialName("acml_vol") val volumeCum: String? = null,
    /** 종목명 */
    @SerialName("hts_kor_isnm") val nameKo: String? = null,
    /** 시장 구분 (KOSPI/KOSDAQ) */
    @SerialName("bstp_kor_isnm") val bstpKorIsnm: String? = null,
    /** 시장 분류 코드 */
    @SerialName("rprs_mrkt_kor_name") val marketName: String? = null,
)

/**
 * 해외주식 현재가 (TR_ID: HHDFS00000300)
 * GET /uapi/overseas-price/v1/quotations/price
 *  params: AUTH=, EXCD=NAS/NYS/AMS, SYMB=종목코드
 */
@Serializable
data class UsPriceResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: UsPriceOutput? = null,
)

@Serializable
data class UsPriceOutput(
    /** 현재가 */
    @SerialName("last") val price: String? = null,
    /** 전일대비(부호 포함) */
    @SerialName("diff") val change: String? = null,
    /** 등락률 */
    @SerialName("rate") val changePct: String? = null,
    /** 거래량 */
    @SerialName("tvol") val volumeCum: String? = null,
    /** 종목명(영문) */
    @SerialName("rsym") val rsym: String? = null,
)
