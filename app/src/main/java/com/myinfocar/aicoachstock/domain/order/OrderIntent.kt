package com.myinfocar.aicoachstock.domain.order

import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderType
import com.myinfocar.aicoachstock.domain.model.TradeSide

/**
 * 생체 인증 통과 영수증 (Stage 16).
 *
 * - 한투 주문 송신 직전 BiometricPrompt를 통과한 사용자만 들고 있는 일회성 토큰.
 * - UI(Phase D)에서 BiometricPrompt 콜백 성공 시 생성하여 OrderIntent에 packaging.
 * - sourceFlow는 디버깅 라벨 (예: "OrderEntryScreen", "OrdersScreen.cancel").
 */
data class OrderConfirmation(val sourceFlow: String)

/**
 * 주문 의도. OrderService.placeOrder의 단일 진입점.
 */
sealed class OrderIntent {
    abstract val confirmation: OrderConfirmation

    /** 신규 매수/매도. */
    data class Place(
        val ticker: String,
        val market: Market,
        val side: TradeSide,
        val orderType: OrderType,
        val qty: Int,
        val price: Double?,
        val linkedPrincipleIds: List<String> = emptyList(),
        /** 해외 전용: NASD / NYSE / AMEX 등. KR이면 null. */
        val excgCode: String? = null,
        override val confirmation: OrderConfirmation,
    ) : OrderIntent()

    /** 기존 주문 정정. origin은 DB에 SUBMITTED/PARTIAL 상태인 Order. */
    data class Revise(
        val origin: Order,
        val newQty: Int,
        val newPrice: Double?,
        override val confirmation: OrderConfirmation,
    ) : OrderIntent()

    /** 기존 주문 취소. */
    data class Cancel(
        val origin: Order,
        override val confirmation: OrderConfirmation,
    ) : OrderIntent()
}
