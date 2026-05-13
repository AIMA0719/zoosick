package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 주식일별주문체결조회.
 *
 * GET /uapi/domestic-stock/v1/trading/inquire-daily-ccld
 *  TR_ID 실전: TTTC8001R  /  모의: VTTC8001R
 *
 * params: CANO, ACNT_PRDT_CD, INQR_STRT_DT(YYYYMMDD), INQR_END_DT(YYYYMMDD),
 *         SLL_BUY_DVSN_CD("00"=전체), INQR_DVSN("01"=정순/"00"=역순),
 *         PDNO(""), CCLD_DVSN("01"=체결), ORD_GNO_BRNO(""), ODNO(""),
 *         INQR_DVSN_3("00"), INQR_DVSN_1(""), CTX_AREA_FK100(""), CTX_AREA_NK100("")
 */
@Serializable
data class DailyCcldResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: List<DailyCcldItem> = emptyList(),
    /** 연속조회 키 — 다음 페이지 호출 시 CTX_AREA_NK100에 넣음. 비어있으면 마지막. */
    @SerialName("ctx_area_nk100") val ctxAreaNk100: String? = null,
    @SerialName("ctx_area_fk100") val ctxAreaFk100: String? = null,
)

@Serializable
data class DailyCcldItem(
    @SerialName("ord_dt") val ordDt: String? = null,            // 주문일자 YYYYMMDD
    @SerialName("ord_tmd") val ordTmd: String? = null,          // 주문시각 HHMMSS
    @SerialName("ord_gno_brno") val ordGnoBrno: String? = null,
    @SerialName("odno") val odno: String? = null,               // 주문번호 — unique key
    @SerialName("orgn_odno") val orgnOdno: String? = null,
    @SerialName("ord_dvsn_name") val ordDvsnName: String? = null,
    /** 매도/매수 구분: "01"=매도, "02"=매수 */
    @SerialName("sll_buy_dvsn_cd") val sllBuyDvsnCd: String? = null,
    @SerialName("sll_buy_dvsn_cd_name") val sllBuyDvsnName: String? = null,
    @SerialName("pdno") val pdno: String? = null,               // 종목코드
    @SerialName("prdt_name") val prdtName: String? = null,      // 종목명(한글)
    @SerialName("ord_qty") val ordQty: String? = null,
    @SerialName("ord_unpr") val ordUnpr: String? = null,
    /** 총체결수량 — 0이면 미체결 */
    @SerialName("tot_ccld_qty") val totCcldQty: String? = null,
    /** 평균체결가 */
    @SerialName("avg_prvs") val avgPrvs: String? = null,
    @SerialName("tot_ccld_amt") val totCcldAmt: String? = null,
    @SerialName("rmn_qty") val rmnQty: String? = null,
    @SerialName("cncl_yn") val cnclYn: String? = null,          // Y면 취소된 주문
)
