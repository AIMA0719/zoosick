package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 손절/익절 라인 감시.
 *
 * direction: 목표가가 현재가 대비 BELOW(아래)인지 ABOVE(위)인지.
 *   STOP_LOSS는 보통 BELOW, TAKE_PROFIT는 보통 ABOVE.
 */
data class PriceAlert(
    val id: String,
    val ticker: String,
    val linkedTradeId: String?,
    val targetPrice: Double,
    val type: PriceAlertType,
    val direction: PriceAlertDirection,
    val status: PriceAlertStatus,
    val triggeredAt: Instant?,
    val aiMessage: String?,
    val createdAt: Instant,
) {
    init {
        require(targetPrice > 0.0) { "targetPrice must be > 0 (got $targetPrice)" }
    }
}
