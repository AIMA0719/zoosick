package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 5호가 스냅샷 (Stage 15 신설).
 *
 * - WebSocket(`H0STASP0`)에서 실시간 스트림.
 * - REST(`FHKST01010200` inquire-asking-price-exp-ccn)에서도 같은 모델로 매핑(폴백).
 *
 * asks: 매도 호가 (낮은 가격 → 높은 가격, 1~5호가).
 * bids: 매수 호가 (높은 가격 → 낮은 가격, 1~5호가).
 */
data class OrderBookLevel(
    val price: Double,
    val quantity: Long,
)

data class OrderBookSnapshot(
    val ticker: String,
    val asks: List<OrderBookLevel>,
    val bids: List<OrderBookLevel>,
    val totalAskQty: Long,
    val totalBidQty: Long,
    val expectedPrice: Double?,
    val ts: Instant,
    val source: TickSource,
)
