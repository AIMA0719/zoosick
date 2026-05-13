package com.myinfocar.aicoachstock.domain.market

import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.Stock

/**
 * REST 기반 단발 조회. WebSocket 폴백·비장시간 종가 조회용.
 *
 * - 토큰버킷·디바운스 적용 (PRD: 분당 호출 한도 무시 금지)
 * - HTTPS 필수, 평문 X
 */
interface MarketDataSource {
    /** 종가 또는 가장 최근 체결가. WS 폴백/비장시간용. */
    suspend fun fetchClosePrice(ticker: String, market: Market): Result<MarketTick>

    /** 종목 검색. 한투 REST 검색 API. */
    suspend fun searchStocks(query: String): Result<List<Stock>>

    /**
     * 재무 정보(시가총액, PER, PBR, 배당률 등).
     * 종목 리서치 Q&A 화면에서 LLM 컨텍스트로 주입.
     */
    suspend fun fetchFundamentals(ticker: String): Result<Fundamentals>
}

data class Fundamentals(
    val ticker: String,
    val marketCap: Long?,
    val per: Double?,
    val pbr: Double?,
    val dividendYield: Double?,
    val week52High: Double?,
    val week52Low: Double?,
)
