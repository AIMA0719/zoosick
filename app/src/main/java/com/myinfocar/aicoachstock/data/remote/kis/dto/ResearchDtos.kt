package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ─────────────────────────────────────────────────────────────
 * 국내주식 추정실적 (TR_ID HHKST668300C0)
 * GET /uapi/domestic-stock/v1/quotations/estimate-perform
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class EstimatePerformResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: EstimatePerformOutput1? = null,
    @SerialName("output2") val output2: List<EstimatePerformOutput2> = emptyList(),
)

@Serializable
data class EstimatePerformOutput1(
    @SerialName("sht_cd") val shtCd: String? = null,
    @SerialName("item_kor_nm") val itemKorNm: String? = null,
    @SerialName("estdate") val estdate: String? = null,
    @SerialName("rcmd_reason") val rcmdReason: String? = null,
    @SerialName("capital") val capital: String? = null,
    @SerialName("forn_item_lmtrt") val fornItemLmtrt: String? = null,
)

@Serializable
data class EstimatePerformOutput2(
    @SerialName("data1") val data1: String? = null,
    @SerialName("data2") val data2: String? = null,
    @SerialName("data3") val data3: String? = null,
    @SerialName("data4") val data4: String? = null,
    @SerialName("data5") val data5: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 국내주식 종목투자의견 (TR_ID FHKST663300C0)
 * GET /uapi/domestic-stock/v1/quotations/invest-opinion
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class InvestOpinionResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: List<InvestOpinionItem> = emptyList(),
)

@Serializable
data class InvestOpinionItem(
    @SerialName("stck_bsop_date") val stckBsopDate: String? = null, // 발표일
    @SerialName("invt_opnn") val invtOpnn: String? = null,           // 투자의견 (매수/매도/중립 등)
    @SerialName("invt_opnn_cls_code") val invtOpnnClsCode: String? = null,
    @SerialName("rgbf_invt_opnn") val rgbfInvtOpnn: String? = null,
    @SerialName("mbcr_name") val mbcrName: String? = null,           // 회원사명 (증권사)
    @SerialName("hts_goal_prc") val htsGoalPrc: String? = null,      // 목표주가
    @SerialName("stck_prdy_clpr") val stckPrdyClpr: String? = null,  // 전일 종가
    @SerialName("stck_nday_esdg") val stckNdayEsdg: String? = null,
    @SerialName("nday_dprt") val ndayDprt: String? = null,
    @SerialName("stft_esdg") val stftEsdg: String? = null,
    @SerialName("dprt") val dprt: String? = null,                     // 괴리율
)

/* ─────────────────────────────────────────────────────────────
 * 국내주식 분봉 (TR_ID FHKST03010200)
 * GET /uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class TimeChartResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: TimeChartMeta? = null,
    @SerialName("output2") val output2: List<TimeChartBar> = emptyList(),
)

@Serializable
data class TimeChartMeta(
    @SerialName("prdy_vrss") val prdyVrss: String? = null,
    @SerialName("prdy_vrss_sign") val prdyVrssSign: String? = null,
    @SerialName("prdy_ctrt") val prdyCtrt: String? = null,
    @SerialName("stck_prdy_clpr") val stckPrdyClpr: String? = null,
    @SerialName("acml_vol") val acmlVol: String? = null,
    @SerialName("hts_kor_isnm") val htsKorIsnm: String? = null,
    @SerialName("stck_prpr") val stckPrpr: String? = null,
)

@Serializable
data class TimeChartBar(
    @SerialName("stck_bsop_date") val stckBsopDate: String? = null, // YYYYMMDD
    @SerialName("stck_cntg_hour") val stckCntgHour: String? = null, // HHMMSS
    @SerialName("stck_prpr") val stckPrpr: String? = null,           // 현재가
    @SerialName("stck_oprc") val stckOprc: String? = null,
    @SerialName("stck_hgpr") val stckHgpr: String? = null,
    @SerialName("stck_lwpr") val stckLwpr: String? = null,
    @SerialName("cntg_vol") val cntgVol: String? = null,
    @SerialName("acml_tr_pbmn") val acmlTrPbmn: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 예탁원정보 - 배당 일정 (TR_ID HHKDB669102C0)
 * GET /uapi/domestic-stock/v1/ksdinfo/dividend
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class DividendResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk") val ctxAreaFk: String? = null,
    @SerialName("ctx_area_nk") val ctxAreaNk: String? = null,
    @SerialName("output1") val output1: List<DividendItem> = emptyList(),
)

@Serializable
data class DividendItem(
    @SerialName("record_date") val recordDate: String? = null,       // 기준일
    @SerialName("divi_pay_dt") val diviPayDt: String? = null,         // 배당지급일
    @SerialName("sht_cd") val shtCd: String? = null,                   // 종목코드
    @SerialName("isin_name") val isinName: String? = null,             // 종목명
    @SerialName("divi_kind") val diviKind: String? = null,             // 배당구분 (현금/주식)
    @SerialName("face_val") val faceVal: String? = null,
    @SerialName("per_sto_divi_amt") val perStoDiviAmt: String? = null, // 1주당 배당금
    @SerialName("divi_rate") val diviRate: String? = null,             // 배당률
    @SerialName("stk_divi_rate") val stkDiviRate: String? = null,
    @SerialName("divi_status") val diviStatus: String? = null,         // 결산/중간/분기/반기
    @SerialName("stk_kind") val stkKind: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 시장별 투자자매매동향 (일별) — 시장 전체 큰손 흐름 (TR_ID FHPTJ04040000)
 * GET /uapi/domestic-stock/v1/quotations/inquire-investor-daily-by-market
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class MarketInvestorDailyResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: List<MarketInvestorDailyItem> = emptyList(),
)

@Serializable
data class MarketInvestorDailyItem(
    @SerialName("bstp_nmix_prdy_date") val bstpNmixPrdyDate: String? = null,
    @SerialName("bstp_nmix_prpr") val bstpNmixPrpr: String? = null,     // 지수
    @SerialName("bstp_nmix_prdy_vrss") val bstpNmixPrdyVrss: String? = null,
    @SerialName("bstp_nmix_prdy_ctrt") val bstpNmixPrdyCtrt: String? = null,
    @SerialName("frgn_seln_vol") val frgnSelnVol: String? = null,
    @SerialName("frgn_shnu_vol") val frgnShnuVol: String? = null,
    @SerialName("frgn_ntby_qty") val frgnNtbyQty: String? = null,        // 외국인 순매수 수량
    @SerialName("prsn_seln_vol") val prsnSelnVol: String? = null,
    @SerialName("prsn_shnu_vol") val prsnShnuVol: String? = null,
    @SerialName("prsn_ntby_qty") val prsnNtbyQty: String? = null,
    @SerialName("orgn_seln_vol") val orgnSelnVol: String? = null,
    @SerialName("orgn_shnu_vol") val orgnShnuVol: String? = null,
    @SerialName("orgn_ntby_qty") val orgnNtbyQty: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 시장 예수금 (TR_ID FHPST01060000)
 * GET /uapi/domestic-stock/v1/quotations/mktfunds
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class MarketFundsResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: List<MarketFundsItem> = emptyList(),
)

@Serializable
data class MarketFundsItem(
    @SerialName("bass_dt") val bassDt: String? = null,
    @SerialName("crd_lon_blce_amt") val crdLonBlceAmt: String? = null,    // 신용대출잔고
    @SerialName("cust_dpst_mny_amt") val custDpstMnyAmt: String? = null,   // 고객예탁금
    @SerialName("usd_invs_mxnp_amt") val usdInvsMxnpAmt: String? = null,
    @SerialName("comp_mxnp_amt") val compMxnpAmt: String? = null,
    @SerialName("std_id_amt") val stdIdAmt: String? = null,
    @SerialName("sale_estd_amt") val saleEstdAmt: String? = null,
)
