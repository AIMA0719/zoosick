package com.myinfocar.aicoachstock.data.remote.kis.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ─────────────────────────────────────────────────────────────
 * 국내 주식 현금 매수/매도 (TTTC0802U / TTTC0801U)
 * POST /uapi/domestic-stock/v1/trading/order-cash
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class DomesticOrderCashRequest(
    @SerialName("CANO") val accountNo: String,
    @SerialName("ACNT_PRDT_CD") val productCode: String,
    @SerialName("PDNO") val ticker: String,
    /** 주문구분. "00"=지정가, "01"=시장가, "02"=조건부지정가 등. */
    @SerialName("ORD_DVSN") val orderDivision: String,
    @SerialName("ORD_QTY") val quantity: String,
    /** 주문단가. 시장가는 "0". */
    @SerialName("ORD_UNPR") val unitPrice: String,
)

/* ─────────────────────────────────────────────────────────────
 * 국내 주식 정정/취소 (TTTC0803U)
 * POST /uapi/domestic-stock/v1/trading/order-rvsecncl
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class DomesticOrderRevisionRequest(
    @SerialName("CANO") val accountNo: String,
    @SerialName("ACNT_PRDT_CD") val productCode: String,
    /** 원주문 조직번호. */
    @SerialName("KRX_FWDG_ORD_ORGNO") val originOrgNo: String,
    /** 원주문 번호 (ODNO). */
    @SerialName("ORGN_ODNO") val originOrderNo: String,
    @SerialName("ORD_DVSN") val orderDivision: String,
    /** "01"=정정, "02"=취소. */
    @SerialName("RVSE_CNCL_DVSN_CD") val revCnclDvsn: String,
    @SerialName("ORD_QTY") val quantity: String,
    @SerialName("ORD_UNPR") val unitPrice: String,
    /** 잔량 전부 주문여부. "Y"/"N". */
    @SerialName("QTY_ALL_ORD_YN") val qtyAllOrdYn: String = "N",
)

/* ─────────────────────────────────────────────────────────────
 * 해외 주식 매수/매도 (TTTT1002U / TTTT1006U)
 * POST /uapi/overseas-stock/v1/trading/order
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OverseasOrderRequest(
    @SerialName("CANO") val accountNo: String,
    @SerialName("ACNT_PRDT_CD") val productCode: String,
    /** 거래소 코드: NASD / NYSE / AMEX 등. */
    @SerialName("OVRS_EXCG_CD") val excgCode: String,
    @SerialName("PDNO") val ticker: String,
    @SerialName("ORD_QTY") val quantity: String,
    /** 해외 주문단가 (USD, 소수점). 시장가는 "0". */
    @SerialName("OVRS_ORD_UNPR") val unitPrice: String,
    @SerialName("ORD_SVR_DVSN_CD") val ordSvrDvsnCd: String = "0",
    /** 주문구분. "00"=지정가. */
    @SerialName("ORD_DVSN") val orderDivision: String = "00",
    @SerialName("SLL_TYPE") val sellType: String = "00",
)

/* ─────────────────────────────────────────────────────────────
 * 해외 주식 정정/취소 (TTTT1004U)
 * POST /uapi/overseas-stock/v1/trading/order-rvsecncl
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OverseasOrderRevisionRequest(
    @SerialName("CANO") val accountNo: String,
    @SerialName("ACNT_PRDT_CD") val productCode: String,
    @SerialName("OVRS_EXCG_CD") val excgCode: String,
    @SerialName("PDNO") val ticker: String,
    @SerialName("ORGN_ODNO") val originOrderNo: String,
    /** "01"=정정, "02"=취소. */
    @SerialName("RVSE_CNCL_DVSN_CD") val revCnclDvsn: String,
    @SerialName("ORD_QTY") val quantity: String,
    @SerialName("OVRS_ORD_UNPR") val unitPrice: String,
    @SerialName("MGCO_APTM_ODNO") val mgcoAptmOdno: String = "",
    @SerialName("ORD_SVR_DVSN_CD") val ordSvrDvsnCd: String = "0",
)

/* ─────────────────────────────────────────────────────────────
 * 주문 응답 (국내/해외 공통 형태)
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OrderResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: OrderResponseOutput? = null,
)

@Serializable
data class OrderResponseOutput(
    /** 주문 조직번호. */
    @SerialName("KRX_FWDG_ORD_ORGNO") val krxFwdgOrdOrgno: String? = null,
    /** 주문번호 (ODNO). */
    @SerialName("ODNO") val odno: String? = null,
    /** 주문시각 HHMMSS. */
    @SerialName("ORD_TMD") val ordTmd: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 국내 미체결 조회 (TTTC8036R)
 * GET /uapi/domestic-stock/v1/trading/inquire-psbl-rvsecncl
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class DomesticOpenOrdersResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk100") val ctxAreaFk100: String? = null,
    @SerialName("ctx_area_nk100") val ctxAreaNk100: String? = null,
    @SerialName("output") val output: List<DomesticOpenOrderItem> = emptyList(),
)

@Serializable
data class DomesticOpenOrderItem(
    @SerialName("ord_gno_brno") val ordGnoBrno: String? = null,
    @SerialName("odno") val odno: String? = null,
    @SerialName("orgn_odno") val orgnOdno: String? = null,
    @SerialName("ord_dvsn_name") val ordDvsnName: String? = null,
    @SerialName("sll_buy_dvsn_cd") val sllBuyDvsnCd: String? = null,
    @SerialName("sll_buy_dvsn_cd_name") val sllBuyDvsnCdName: String? = null,
    @SerialName("pdno") val pdno: String? = null,
    @SerialName("prdt_name") val prdtName: String? = null,
    @SerialName("rvse_cncl_dvsn_cd") val rvseCnclDvsnCd: String? = null,
    @SerialName("ord_qty") val ordQty: String? = null,
    @SerialName("ord_unpr") val ordUnpr: String? = null,
    @SerialName("ord_tmd") val ordTmd: String? = null,
    @SerialName("tot_ccld_qty") val totCcldQty: String? = null,
    @SerialName("psbl_qty") val psblQty: String? = null,
)

/* ─────────────────────────────────────────────────────────────
 * 해외 미체결 조회 (TTTS3018R)
 * GET /uapi/overseas-stock/v1/trading/inquire-nccs
 * ───────────────────────────────────────────────────────────── */

@Serializable
data class OverseasOpenOrdersResponse(
    @SerialName("rt_cd") val rtCd: String,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("ctx_area_fk200") val ctxAreaFk200: String? = null,
    @SerialName("ctx_area_nk200") val ctxAreaNk200: String? = null,
    @SerialName("output") val output: List<OverseasOpenOrderItem> = emptyList(),
)

@Serializable
data class OverseasOpenOrderItem(
    @SerialName("odno") val odno: String? = null,
    @SerialName("orgn_odno") val orgnOdno: String? = null,
    @SerialName("pdno") val pdno: String? = null,
    @SerialName("prdt_name") val prdtName: String? = null,
    @SerialName("ovrs_excg_cd") val ovrsExcgCd: String? = null,
    @SerialName("ord_qty") val ordQty: String? = null,
    @SerialName("ord_unpr") val ordUnpr: String? = null,
    @SerialName("ft_ord_unpr3") val ftOrdUnpr3: String? = null,
    @SerialName("sll_buy_dvsn_cd") val sllBuyDvsnCd: String? = null,
    @SerialName("ord_tmd") val ordTmd: String? = null,
    @SerialName("rvse_cncl_dvsn") val rvseCnclDvsn: String? = null,
    @SerialName("nccs_qty") val nccsQty: String? = null,
    @SerialName("tot_ccld_qty") val totCcldQty: String? = null,
)
