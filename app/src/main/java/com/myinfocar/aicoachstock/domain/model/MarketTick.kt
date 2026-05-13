package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * WebSocket tick 또는 REST 폴백으로 받은 종목의 최신 시세 스냅샷.
 * 메모리 only (Room 영속화 X). UI는 StateFlow로 구독.
 */
data class MarketTick(
    val ticker: String,
    val price: Double,
    val change: Double?,
    val changePct: Double?,
    val volumeCum: Long?,
    val lastTickAt: Instant,
    val source: TickSource,
)

/**
 * WebSocket 구독 대상 + 우선순위.
 * 41종목 한도 초과 시 priority 낮을수록(=숫자가 작을수록) 우선 유지.
 */
data class SubscriptionTarget(
    val ticker: String,
    val market: Market,
    val reason: SubscriptionReason,
    val priority: Int,
)
