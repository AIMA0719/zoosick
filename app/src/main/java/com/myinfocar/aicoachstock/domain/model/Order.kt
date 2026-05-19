package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 매수/매도 주문 (Stage 16 신설).
 *
 * - 자동 발주 금지 — 사용자가 매 건마다 BiometricPrompt로 명시 확인.
 * - Trade와는 자연 키 연계: `Trade.externalOrderNo` == `Order.krxOrderNo`.
 * - 정정/취소는 `originOrderNo`로 원주문 ODNO 참조 (자기 참조).
 *
 * 상태 전이: PENDING → SUBMITTED → FILLED/PARTIAL/CANCELED/REJECTED (또는 PENDING → REJECTED).
 */
data class Order(
    val id: String,
    val ticker: String,
    val market: Market,
    val side: TradeSide,
    val orderType: OrderType,
    val qty: Int,
    val price: Double?,
    val filledQty: Int,
    val avgFillPrice: Double?,
    val status: OrderStatus,
    val krxOrderNo: String?,
    val krxOrderOrgNo: String?,
    val originOrderNo: String?,
    val linkedPrincipleIds: List<String>,
    val createdAt: Instant,
    val submittedAt: Instant?,
    val completedAt: Instant?,
    val errorMessage: String?,
    val rawMsgCd: String?,
) {
    val isTerminal: Boolean
        get() = status == OrderStatus.FILLED ||
                status == OrderStatus.CANCELED ||
                status == OrderStatus.REJECTED
}
