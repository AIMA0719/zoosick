package com.myinfocar.aicoachstock.domain.market

import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.SubscriptionTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 한투 WebSocket 실시간 시세 스트림 추상화.
 *
 * - 41종목 한도 안에서 priority 큐로 구독 자동 교체
 * - 끊김 시 지수 백오프 재연결 (1→2→4→8→30s 상한)
 * - heartbeat 송수신, ~30초 무응답 시 재연결
 * - 비장시간엔 disconnect (배터리)
 *
 * PRD 04_PROJECT_SPEC의 추상화 계약을 따름.
 */
interface MarketDataStream {
    val connectionState: StateFlow<ConnectionState>
    val currentSubscriptions: StateFlow<List<SubscriptionTarget>>

    suspend fun connect()
    suspend fun disconnect()

    /** 구독 등록. 41종목 한도 초과 시 priority 낮을수록 우선 유지(=숫자가 작을수록). */
    fun subscribe(targets: List<SubscriptionTarget>)

    fun unsubscribe(tickers: List<String>)

    /**
     * 종목별 실시간 tick Flow.
     * 미구독 종목 ticker 입력 시 emptyFlow + REST 폴백 트리거를 구현체가 처리.
     */
    fun ticks(ticker: String): Flow<MarketTick>
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,

    /** WS 끊김 후 REST 폴링으로 폴백 중. */
    DEGRADED,
}
