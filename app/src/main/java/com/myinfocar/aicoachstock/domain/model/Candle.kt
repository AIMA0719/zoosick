package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 캔들 1개 (OHLC + 거래량). Stage 15 신설.
 *
 * ts: 봉 시작 시각 (KST 기준 분/일/주/월/년 경계).
 * volume: 해당 봉의 거래량. 분봉은 한투 누적 거래량(`cum_vol`)을 차분으로 계산.
 *
 * 한국 관례: 상승(close >= open) 빨강, 하락 파랑. 색상 결정은 UI 측.
 */
data class Candle(
    val ts: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val timeframe: Timeframe,
) {
    val isUp: Boolean get() = close >= open
}
