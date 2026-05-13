package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ─────────────────────────────────────────────────────────────
 * 국내 잔고조회 (TR_ID TTTC8434R / VTTC8434R)
 * GET /uapi/domestic-stock/v1/trading/inquire-balance
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class BalanceResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk100") val ctxAreaFk100: String? = null,
    @SerialName("ctx_area_nk100") val ctxAreaNk100: String? = null,
    @SerialName("output1") val output1: List<BalanceHolding> = emptyList(),
    @SerialName("output2") val output2: List<BalanceSummary> = emptyList(),
)

@Serializable
data class BalanceHolding(
    @SerialName("pdno") val pdno: String? = null,          // 종목코드
    @SerialName("prdt_name") val prdtName: String? = null,  // 종목명
    @SerialName("hldg_qty") val hldgQty: String? = null,    // 보유수량
    @SerialName("ord_psbl_qty") val ordPsblQty: String? = null,
    @SerialName("pchs_avg_pric") val pchsAvgPric: String? = null, // 매입평균가
    @SerialName("pchs_amt") val pchsAmt: String? = null,            // 매입금액
    @SerialName("prpr") val prpr: String? = null,                    // 현재가
    @SerialName("evlu_amt") val evluAmt: String? = null,             // 평가금액
    @SerialName("evlu_pfls_amt") val evluPflsAmt: String? = null,   // 평가손익금액
    @SerialName("evlu_pfls_rt") val evluPflsRt: String? = null,     // 평가손익율 (%)
    @SerialName("fltt_rt") val flttRt: String? = null,               // 등락율
    @SerialName("bfdy_cprs_icdc") val bfdyCprsIcdc: String? = null,  // 전일대비
)

@Serializable
data class BalanceSummary(
    @SerialName("dnca_tot_amt") val dncaTotAmt: String? = null,       // 예수금
    @SerialName("nxdy_excc_amt") val nxdyExccAmt: String? = null,
    @SerialName("prvs_rcdl_excc_amt") val prvsRcdlExccAmt: String? = null, // D+2 예수금
    @SerialName("tot_evlu_amt") val totEvluAmt: String? = null,         // 총평가금액
    @SerialName("nass_amt") val nassAmt: String? = null,                  // 순자산금액
    @SerialName("pchs_amt_smtl_amt") val pchsAmtSmtlAmt: String? = null, // 매입금액합계
    @SerialName("evlu_amt_smtl_amt") val evluAmtSmtlAmt: String? = null, // 평가금액합계
    @SerialName("evlu_pfls_smtl_amt") val evluPflsSmtlAmt: String? = null, // 평가손익합계
    @SerialName("scts_evlu_amt") val sctsEvluAmt: String? = null,         // 유가증권평가금액
    @SerialName("bfdy_tot_asst_evlu_amt") val bfdyTotAsstEvluAmt: String? = null,
    @SerialName("asst_icdc_amt") val asstIcdcAmt: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 기간별 손익 일별합산 조회 (TR_ID TTTC8708R) — 실전 전용
 * GET /uapi/domestic-stock/v1/trading/inquire-period-profit
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class PeriodProfitResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: List<PeriodProfitDaily> = emptyList(),
    @SerialName("output2") val output2: List<PeriodProfitSummary> = emptyList(),
)

@Serializable
data class PeriodProfitDaily(
    @SerialName("trad_dt") val tradDt: String? = null,       // 거래일자 YYYYMMDD
    @SerialName("buy_amt") val buyAmt: String? = null,        // 매수금액
    @SerialName("sll_amt") val sllAmt: String? = null,        // 매도금액
    @SerialName("rlzt_pfls") val rlztPfls: String? = null,    // 실현손익
    @SerialName("fee") val fee: String? = null,                 // 수수료
    @SerialName("loan_int") val loanInt: String? = null,
    @SerialName("tl_tax") val tlTax: String? = null,
    @SerialName("pfls_rt") val pflsRt: String? = null,        // 손익률
)

@Serializable
data class PeriodProfitSummary(
    @SerialName("sll_qty_smtl") val sllQtySmtl: String? = null,
    @SerialName("sll_tr_amt_smtl") val sllTrAmtSmtl: String? = null,
    @SerialName("sll_fee_smtl") val sllFeeSmtl: String? = null,
    @SerialName("buy_qty_smtl") val buyQtySmtl: String? = null,
    @SerialName("buy_tr_amt_smtl") val buyTrAmtSmtl: String? = null,
    @SerialName("rlzt_pfls_smtl") val rlztPflsSmtl: String? = null,    // 누적 실현손익
    @SerialName("pfls_rt_smtl") val pflsRtSmtl: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 해외주식 잔고 (TR_ID TTTS3012R / VTTS3012R)
 * GET /uapi/overseas-stock/v1/trading/inquire-balance
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OverseasBalanceResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk200") val ctxAreaFk200: String? = null,
    @SerialName("ctx_area_nk200") val ctxAreaNk200: String? = null,
    @SerialName("output1") val output1: List<OverseasHolding> = emptyList(),
    @SerialName("output2") val output2: OverseasBalanceSummary? = null,
)

@Serializable
data class OverseasHolding(
    @SerialName("cano") val cano: String? = null,
    @SerialName("ovrs_pdno") val ovrsPdno: String? = null,        // 해외 종목코드
    @SerialName("ovrs_item_name") val ovrsItemName: String? = null,
    @SerialName("ovrs_excg_cd") val ovrsExcgCd: String? = null,   // NAS/NYS/AMS
    @SerialName("ord_psbl_qty") val ordPsblQty: String? = null,
    @SerialName("ovrs_cblc_qty") val ovrsCblcQty: String? = null, // 해외잔고수량
    @SerialName("pchs_avg_pric") val pchsAvgPric: String? = null,
    @SerialName("frcr_pchs_amt1") val frcrPchsAmt: String? = null, // 외화매입금액
    @SerialName("now_pric2") val nowPric: String? = null,           // 현재가
    @SerialName("ovrs_stck_evlu_amt") val ovrsStckEvluAmt: String? = null, // 평가금액 외화
    @SerialName("frcr_evlu_pfls_amt") val frcrEvluPflsAmt: String? = null,  // 평가손익 외화
    @SerialName("evlu_pfls_rt") val evluPflsRt: String? = null,
    @SerialName("tr_crcy_cd") val trCrcyCd: String? = null,         // 통화 USD 등
)

@Serializable
data class OverseasBalanceSummary(
    @SerialName("frcr_pchs_amt1") val frcrPchsAmt: String? = null,
    @SerialName("ovrs_rlzt_pfls_amt") val ovrsRlztPflsAmt: String? = null,
    @SerialName("ovrs_tot_pfls") val ovrsTotPfls: String? = null,
    @SerialName("rlzt_erng_rt") val rlztErngRt: String? = null,
    @SerialName("tot_evlu_pfls_amt") val totEvluPflsAmt: String? = null,
    @SerialName("tot_pftrt") val totPftrt: String? = null,
    @SerialName("bass_exrt") val bassExrt: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 해외주식 주문체결내역 (TR_ID TTTS3035R / VTTS3035R)
 * GET /uapi/overseas-stock/v1/trading/inquire-ccnl
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OverseasCcnlResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk200") val ctxAreaFk200: String? = null,
    @SerialName("ctx_area_nk200") val ctxAreaNk200: String? = null,
    @SerialName("output") val output: List<OverseasCcnlItem> = emptyList(),
)

@Serializable
data class OverseasCcnlItem(
    @SerialName("ord_dt") val ordDt: String? = null,             // 주문일자
    @SerialName("ord_gno_brno") val ordGnoBrno: String? = null,
    @SerialName("odno") val odno: String? = null,                  // 주문번호
    @SerialName("orgn_odno") val orgnOdno: String? = null,
    @SerialName("sll_buy_dvsn_cd") val sllBuyDvsnCd: String? = null, // 01=매도, 02=매수
    @SerialName("sll_buy_dvsn_cd_name") val sllBuyDvsnName: String? = null,
    @SerialName("rvse_cncl_dvsn") val rvseCnclDvsn: String? = null,
    @SerialName("rvse_cncl_dvsn_name") val rvseCnclDvsnName: String? = null,
    @SerialName("pdno") val pdno: String? = null,                  // 종목코드
    @SerialName("prdt_name") val prdtName: String? = null,
    @SerialName("ft_ord_qty") val ftOrdQty: String? = null,
    @SerialName("ft_ord_unpr3") val ftOrdUnpr: String? = null,
    @SerialName("ft_ccld_qty") val ftCcldQty: String? = null,     // 체결수량
    @SerialName("ft_ccld_unpr3") val ftCcldUnpr: String? = null,   // 체결단가
    @SerialName("ft_ccld_amt3") val ftCcldAmt: String? = null,
    @SerialName("nccs_qty") val nccsQty: String? = null,           // 미체결수량
    @SerialName("prcs_stat_name") val prcsStatName: String? = null,
    @SerialName("rjct_rson") val rjctRson: String? = null,
    @SerialName("ord_tmd") val ordTmd: String? = null,
    @SerialName("tr_mket_name") val trMketName: String? = null,
    @SerialName("tr_natn_name") val trNatnName: String? = null,
    @SerialName("ovrs_excg_cd") val ovrsExcgCd: String? = null,   // NASD/NYSE/AMEX 등
    @SerialName("tr_crcy_cd") val trCrcyCd: String? = null,        // USD
)
