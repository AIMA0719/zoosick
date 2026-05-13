package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 실제 체결한 매수/매도. 사용자 수동 입력 또는 한투 API import.
 *
 * price: 국내 KRW, 미국 USD. 화폐 단위는 Stock.currency로 해석.
 * linkedChecklistId: 진입 체크리스트에서 시작된 매매면 그 ID.
 * externalOrderNo: 한투 자동 import면 odno(주문번호). null이면 사용자 수동 입력(MANUAL).
 */
data class Trade(
    val id: String,
    val ticker: String,
    val market: Market,
    val side: TradeSide,
    val qty: Int,
    val price: Double,
    val fee: Double?,
    val executedAt: Instant,
    val reasonText: String?,
    val emotionTag: EmotionTag,
    val linkedChecklistId: String?,
    val createdAt: Instant,
    val externalOrderNo: String? = null,
) {
    init {
        require(qty > 0) { "qty must be > 0 (got $qty)" }
        require(price > 0.0) { "price must be > 0 (got $price)" }
    }

    val isImported: Boolean get() = externalOrderNo != null
}
