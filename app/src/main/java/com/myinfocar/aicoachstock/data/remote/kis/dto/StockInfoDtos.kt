package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ─────────────────────────────────────────────────────────────
 * 주식 기본조회 (TR_ID CTPF1604R)
 * GET /uapi/domestic-stock/v1/quotations/search-stock-info
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class SearchStockInfoResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: StockInfoOutput? = null,
)

@Serializable
data class StockInfoOutput(
    @SerialName("pdno") val pdno: String? = null,
    @SerialName("prdt_type_cd") val prdtTypeCd: String? = null,
    @SerialName("prdt_name") val prdtName: String? = null,          // 종목명 한글
    @SerialName("prdt_eng_name") val prdtEngName: String? = null,
    @SerialName("prdt_abrv_name") val prdtAbrvName: String? = null,
    @SerialName("mket_id_cd") val mketIdCd: String? = null,         // STK(KOSPI) / KSQ(KOSDAQ) 등
    @SerialName("scty_grp_id_cd") val sctyGrpIdCd: String? = null,
    @SerialName("std_idst_clsf_cd_name") val stdIndustryName: String? = null, // 표준산업분류명 (섹터)
    @SerialName("idx_bztp_lcls_cd_name") val idxBztpLargeName: String? = null,
    @SerialName("idx_bztp_mcls_cd_name") val idxBztpMidName: String? = null,
    @SerialName("idx_bztp_scls_cd_name") val idxBztpSmallName: String? = null, // 업종 소분류
    @SerialName("lstg_stqt") val lstgStqt: String? = null,            // 상장주식수
    @SerialName("capi_amt") val capiAmt: String? = null,              // 자본금
    @SerialName("crdt_able_yn") val crdtAbleYn: String? = null,
    @SerialName("dryy_hgpr") val dryyHgpr: String? = null,            // 당해년도최고가
    @SerialName("dryy_lwpr") val dryyLwpr: String? = null,            // 당해년도최저가
)

/* ─────────────────────────────────────────────────────────────
 * 국내 재무비율 (TR_ID FHKST66430300)
 * GET /uapi/domestic-stock/v1/finance/financial-ratio
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class FinanceRatioResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: List<FinanceRatioItem> = emptyList(),
)

@Serializable
data class FinanceRatioItem(
    @SerialName("stac_yymm") val stacYymm: String? = null,       // 결산년월
    @SerialName("grs") val grs: String? = null,                  // 매출액증가율
    @SerialName("bsop_prfi_inrt") val bsopPrfiInrt: String? = null, // 영업이익증가율
    @SerialName("ntin_inrt") val ntinInrt: String? = null,         // 순이익증가율
    @SerialName("roe_val") val roeVal: String? = null,             // ROE
    @SerialName("eps") val eps: String? = null,                    // EPS
    @SerialName("sps") val sps: String? = null,                    // 주당매출액
    @SerialName("bps") val bps: String? = null,                    // BPS
    @SerialName("rsrv_rate") val rsrvRate: String? = null,         // 유보율
    @SerialName("lblt_rate") val lbltRate: String? = null,         // 부채비율
)

/* ─────────────────────────────────────────────────────────────
 * 국내 일봉 차트 (TR_ID FHKST03010100)
 * GET /uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class DailyChartResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: DailyChartMeta? = null,
    @SerialName("output2") val output2: List<DailyChartBar> = emptyList(),
)

@Serializable
data class DailyChartMeta(
    @SerialName("prdy_vrss") val prdyVrss: String? = null,
    @SerialName("prdy_vrss_sign") val prdyVrssSign: String? = null,
    @SerialName("prdy_ctrt") val prdyCtrt: String? = null,
    @SerialName("stck_prdy_clpr") val stckPrdyClpr: String? = null,
    @SerialName("acml_vol") val acmlVol: String? = null,
    @SerialName("acml_tr_pbmn") val acmlTrPbmn: String? = null,
    @SerialName("hts_kor_isnm") val htsKorIsnm: String? = null,
    @SerialName("stck_prpr") val stckPrpr: String? = null,
    @SerialName("stck_shrn_iscd") val stckShrnIscd: String? = null,
)

@Serializable
data class DailyChartBar(
    @SerialName("stck_bsop_date") val stckBsopDate: String? = null, // YYYYMMDD
    @SerialName("stck_clpr") val stckClpr: String? = null,           // 종가
    @SerialName("stck_oprc") val stckOprc: String? = null,           // 시가
    @SerialName("stck_hgpr") val stckHgpr: String? = null,           // 고가
    @SerialName("stck_lwpr") val stckLwpr: String? = null,           // 저가
    @SerialName("acml_vol") val acmlVol: String? = null,             // 누적거래량
    @SerialName("flng_cls_code") val flngClsCode: String? = null,
    @SerialName("prtt_rate") val prttRate: String? = null,
    @SerialName("mod_yn") val modYn: String? = null,
    @SerialName("prdy_vrss_sign") val prdyVrssSign: String? = null,
    @SerialName("prdy_vrss") val prdyVrss: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 해외 휴장일 (TR_ID CTOS5011R)
 * GET /uapi/overseas-stock/v1/quotations/countries-holiday
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class CountriesHolidayResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk") val ctxAreaFk: String? = null,
    @SerialName("ctx_area_nk") val ctxAreaNk: String? = null,
    @SerialName("output") val output: List<HolidayItem> = emptyList(),
)

@Serializable
data class HolidayItem(
    @SerialName("bass_dt") val bassDt: String? = null,           // 기준일자 YYYYMMDD
    @SerialName("tr_mket_cd") val trMketCd: String? = null,
    @SerialName("tr_mket_name") val trMketName: String? = null,
    @SerialName("acpl_natn_cd") val acplNatnCd: String? = null,  // 국가코드
    @SerialName("acpl_natn_name") val acplNatnName: String? = null,
    @SerialName("opbz_yn") val opbzYn: String? = null,           // 영업여부 Y/N
    @SerialName("tr_dy_cls_cd") val trDyClsCd: String? = null,   // 거래일구분
    @SerialName("dy_x") val dyX: String? = null,                  // 휴일사유
)
