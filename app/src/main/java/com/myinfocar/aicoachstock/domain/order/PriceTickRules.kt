package com.myinfocar.aicoachstock.domain.order

import com.myinfocar.aicoachstock.domain.model.Market
import kotlin.math.roundToLong

/**
 * 호가 단위 규칙 (Stage 16-3 신설).
 *
 * - KOSPI/KOSDAQ: 가격대별 1/5/10/50/100/500/1000원.
 *   - < 2,000원: 1원
 *   - < 5,000원: 5원
 *   - < 20,000원: 10원
 *   - < 50,000원: 50원
 *   - < 200,000원: 100원
 *   - < 500,000원: 500원
 *   - ≥ 500,000원: 1,000원
 * - 미국: 보통 $0.01 (low-price tier 변동 있음 — Phase 1은 단순 1센트).
 *
 * 한투 거부(APBK0918/0919) 방지를 위해 사용자 입력을 가장 가까운 valid tick으로 보정.
 */
object PriceTickRules {

    /** market별 호가 단위 (입력 가격 기준). */
    fun tickFor(market: Market, price: Double): Double = when (market) {
        Market.KR -> when {
            price < 2_000 -> 1.0
            price < 5_000 -> 5.0
            price < 20_000 -> 10.0
            price < 50_000 -> 50.0
            price < 200_000 -> 100.0
            price < 500_000 -> 500.0
            else -> 1_000.0
        }
        Market.US -> 0.01
    }

    /** 사용자 입력 가격을 호가 단위에 맞춰 가장 가까운 값으로 보정. */
    fun snap(market: Market, price: Double): Double {
        if (price <= 0) return price
        val tick = tickFor(market, price)
        return ((price / tick).roundToLong() * tick).coerceAtLeast(tick)
    }

    /** 입력값이 이미 호가 단위에 부합하는지. UI 검증용. */
    fun isValid(market: Market, price: Double): Boolean {
        if (price <= 0) return false
        val tick = tickFor(market, price)
        val multiplier = price / tick
        // 부동소수 오차 1% tolerance.
        return kotlin.math.abs(multiplier - multiplier.roundToLong()) < 0.001
    }
}
