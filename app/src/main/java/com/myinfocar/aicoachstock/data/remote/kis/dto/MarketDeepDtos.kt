package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ─────────────────────────────────────────────────────────────
 * 주식현재가 호가/예상체결 (TR_ID FHKST01010200)
 * GET /uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class AskingPriceResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: AskingPriceLevels? = null,
    @SerialName("output2") val output2: AskingPriceSummary? = null,
)

@Serializable
data class AskingPriceLevels(
    @SerialName("aspr_acpt_hour") val asprAcptHour: String? = null,
    @SerialName("askp1") val askp1: String? = null,
    @SerialName("askp2") val askp2: String? = null,
    @SerialName("askp3") val askp3: String? = null,
    @SerialName("askp4") val askp4: String? = null,
    @SerialName("askp5") val askp5: String? = null,
    @SerialName("bidp1") val bidp1: String? = null,
    @SerialName("bidp2") val bidp2: String? = null,
    @SerialName("bidp3") val bidp3: String? = null,
    @SerialName("bidp4") val bidp4: String? = null,
    @SerialName("bidp5") val bidp5: String? = null,
    @SerialName("askp_rsqn1") val askpRsqn1: String? = null,
    @SerialName("askp_rsqn2") val askpRsqn2: String? = null,
    @SerialName("askp_rsqn3") val askpRsqn3: String? = null,
    @SerialName("askp_rsqn4") val askpRsqn4: String? = null,
    @SerialName("askp_rsqn5") val askpRsqn5: String? = null,
    @SerialName("bidp_rsqn1") val bidpRsqn1: String? = null,
    @SerialName("bidp_rsqn2") val bidpRsqn2: String? = null,
    @SerialName("bidp_rsqn3") val bidpRsqn3: String? = null,
    @SerialName("bidp_rsqn4") val bidpRsqn4: String? = null,
    @SerialName("bidp_rsqn5") val bidpRsqn5: String? = null,
    @SerialName("total_askp_rsqn") val totalAskpRsqn: String? = null,
    @SerialName("total_bidp_rsqn") val totalBidpRsqn: String? = null,
    @SerialName("ntby_aspr_rsqn") val ntbyAsprRsqn: String? = null,
)

@Serializable
data class AskingPriceSummary(
    @SerialName("stck_prpr") val stckPrpr: String? = null,    // 현재가
    @SerialName("stck_oprc") val stckOprc: String? = null,    // 시가
    @SerialName("stck_hgpr") val stckHgpr: String? = null,    // 고가
    @SerialName("stck_lwpr") val stckLwpr: String? = null,    // 저가
    @SerialName("stck_sdpr") val stckSdpr: String? = null,    // 기준가
    @SerialName("antc_cnpr") val antcCnpr: String? = null,    // 예상체결가
    @SerialName("antc_cntg_vrss") val antcCntgVrss: String? = null,
    @SerialName("antc_cntg_prdy_ctrt") val antcCntgPrdyCtrt: String? = null,
    @SerialName("antc_vol") val antcVol: String? = null,
    @SerialName("vi_cls_code") val viClsCode: String? = null, // 변동성 완화장치
)

/* ─────────────────────────────────────────────────────────────
 * 주식현재가 투자자 (TR_ID FHKST01010900)
 * GET /uapi/domestic-stock/v1/quotations/inquire-investor
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class InvestorTrendResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: List<InvestorDailyItem> = emptyList(),
)

@Serializable
data class InvestorDailyItem(
    @SerialName("stck_bsop_date") val stckBsopDate: String? = null,
    @SerialName("stck_clpr") val stckClpr: String? = null,
    @SerialName("prdy_vrss") val prdyVrss: String? = null,
    @SerialName("prdy_vrss_sign") val prdyVrssSign: String? = null,
    @SerialName("prsn_ntby_qty") val prsnNtbyQty: String? = null,  // 개인 순매수
    @SerialName("frgn_ntby_qty") val frgnNtbyQty: String? = null,  // 외국인 순매수
    @SerialName("orgn_ntby_qty") val orgnNtbyQty: String? = null,  // 기관 순매수
    @SerialName("prsn_ntby_tr_pbmn") val prsnNtbyTrPbmn: String? = null,
    @SerialName("frgn_ntby_tr_pbmn") val frgnNtbyTrPbmn: String? = null,
    @SerialName("orgn_ntby_tr_pbmn") val orgnNtbyTrPbmn: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 해외뉴스종합 제목 (TR_ID HHPSTH60100C1)
 * GET /uapi/overseas-price/v1/quotations/news-title
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OverseasNewsResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("outblock1") val outblock1: List<OverseasNewsItem> = emptyList(),
)

@Serializable
data class OverseasNewsItem(
    @SerialName("info_gb") val infoGb: String? = null,
    @SerialName("news_key") val newsKey: String? = null,
    @SerialName("data_dt") val dataDt: String? = null,
    @SerialName("data_tm") val dataTm: String? = null,
    @SerialName("class_cd") val classCd: String? = null,
    @SerialName("class_name") val className: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("nation_cd") val nationCd: String? = null,
    @SerialName("exchange_cd") val exchangeCd: String? = null,
    @SerialName("symb") val symb: String? = null,
    @SerialName("symb_name") val symbName: String? = null,
    @SerialName("title") val title: String? = null,
)
