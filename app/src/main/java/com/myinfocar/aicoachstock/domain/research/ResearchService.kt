package com.myinfocar.aicoachstock.domain.research

import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 종목 리서치 Q&A.
 *
 *  - 종목 메타(Stock) + 현재가 시세(MarketDataSource) + 사용자 입력 재무 메모 → LLM Q&A.
 *  - 투자 권유 금지. 일반 정보 정리 톤.
 *
 *  Phase 1은 단발 질의. 대화 누적은 코치 채팅에서 처리.
 */
@Singleton
class ResearchService @Inject constructor(
    private val engine: LLMEngine,
    private val stockRepo: StockRepository,
    private val marketDataSource: MarketDataSource,
    private val stockInfoService: StockInfoService,
) {

    fun ask(
        ticker: String,
        manualFinancials: String?,
        question: String,
    ): Flow<ResearchEvent> = flow {
        emit(ResearchEvent.Preparing)

        val stock = stockRepo.findByTicker(ticker)
        val tick = stock?.let { marketDataSource.fetchClosePrice(ticker, it.market).getOrNull() }

        // 한투 API에서 재무/일봉 자동 주입 (KR만). 실패해도 사용자 입력은 유지.
        val isKr = stock?.market == Market.KR || (stock == null && ticker.length == 6 && ticker.all { it.isDigit() })
        val finance = if (isKr) stockInfoService.fetchFinanceRatio(ticker, limit = 3) else emptyList()
        val chart = if (isKr) stockInfoService.fetchDailyChart(ticker, daysBack = 30) else emptyList()
        val info = if (isKr) stockInfoService.fetchStockInfo(ticker) else null

        emit(ResearchEvent.LoadingModel)
        engine.load().onFailure { e ->
            emit(ResearchEvent.Failed(e))
            return@flow
        }

        val systemPrompt = buildSystemPrompt()
        val autoFinancials = buildAutoFinancialsBlock(finance, chart, info)
        val combinedFinancials = listOfNotNull(
            autoFinancials.takeIf { it.isNotBlank() },
            manualFinancials?.takeIf { it.isNotBlank() }?.let { "(사용자 추가 메모)\n$it" },
        ).joinToString("\n\n").takeIf { it.isNotBlank() }
        val userPrompt = buildUserPrompt(ticker, stock, tick?.price, combinedFinancials, question)

        val accumulated = StringBuilder()
        engine.generate(systemPrompt, userPrompt, context = emptyList())
            .collect { ev ->
                when (ev) {
                    is GenerationEvent.Token -> {
                        accumulated.append(ev.text)
                        emit(ResearchEvent.Streaming(accumulated.toString()))
                    }
                    is GenerationEvent.Final -> emit(
                        ResearchEvent.Completed(accumulated.toString().trim(), ev.latencyMs)
                    )
                    is GenerationEvent.Error -> emit(ResearchEvent.Failed(ev.cause))
                }
            }
    }

    private fun buildAutoFinancialsBlock(
        finance: List<com.myinfocar.aicoachstock.data.remote.kis.dto.FinanceRatioItem>,
        chart: List<com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar>,
        info: com.myinfocar.aicoachstock.data.remote.kis.dto.StockInfoOutput?,
    ): String = buildString {
        if (info != null) {
            append("(자동 조회: 종목 메타 — 한투 OpenAPI)\n")
            info.prdtName?.let { append("- 종목명: $it\n") }
            info.idxBztpSmallName?.let { append("- 업종: $it\n") }
            info.lstgStqt?.let { append("- 상장주식수: $it\n") }
            info.dryyHgpr?.let { append("- 당해년도 최고가: $it\n") }
            info.dryyLwpr?.let { append("- 당해년도 최저가: $it\n") }
        }
        if (finance.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("(자동 조회: 재무비율 — 결산년월별)\n")
            finance.forEach { f ->
                append("- ${f.stacYymm.orEmpty()}: ")
                append("ROE ${f.roeVal ?: "-"}%, ")
                append("EPS ${f.eps ?: "-"}, ")
                append("BPS ${f.bps ?: "-"}, ")
                append("부채비율 ${f.lbltRate ?: "-"}%, ")
                append("매출증가율 ${f.grs ?: "-"}%, ")
                append("영업이익증가율 ${f.bsopPrfiInrt ?: "-"}%\n")
            }
        }
        if (chart.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("(자동 조회: 일봉 ${chart.size}개 — 최근→과거)\n")
            val first = chart.firstOrNull()?.stckClpr?.toDoubleOrNull()
            val last = chart.lastOrNull()?.stckClpr?.toDoubleOrNull()
            if (first != null && last != null && last > 0) {
                val pct = (first - last) / last * 100
                append("- 기간 등락률: ${"%+.2f".format(pct)}% (시작 $last → 최근 $first)\n")
            }
            val highs = chart.mapNotNull { it.stckHgpr?.toDoubleOrNull() }
            val lows = chart.mapNotNull { it.stckLwpr?.toDoubleOrNull() }
            if (highs.isNotEmpty()) append("- 기간 고가: ${highs.max()}\n")
            if (lows.isNotEmpty()) append("- 기간 저가: ${lows.min()}\n")
        }
    }

    private fun buildSystemPrompt(): String = """당신은 종목 리서치를 도와주는 분석 보조입니다.

지침:
- 한국어로, 단정하지 말고 일반 정보를 정리하는 톤.
- 투자 권유·매수/매도 의견 제시 금지. "이렇게 볼 수도 있다" 정도로 한정.
- 사용자가 입력한 재무 메모는 검증되지 않은 자료임을 인지하고, 그 정보를 근거로 결론짓지 말 것.
- 추측이 필요하면 명시. "확인되지 않음" 표현 사용.
- 4~6문장 내외.
- 최종 판단은 사용자 본인의 책임이라는 안내를 짧게 포함.
""".trimIndent()

    private fun buildUserPrompt(
        ticker: String,
        stock: Stock?,
        currentPrice: Double?,
        manualFinancials: String?,
        question: String,
    ): String = buildString {
        append("[종목 정보]\n")
        append("- 코드: $ticker\n")
        stock?.let {
            append("- 한국명: ${it.nameKo}\n")
            it.nameEn?.let { en -> append("- 영문명: $en\n") }
            append("- 거래소: ${it.exchange.name}\n")
            append("- 통화: ${it.currency.name}\n")
            it.sector?.let { sec -> append("- 섹터: $sec\n") }
        }
        currentPrice?.let {
            val priceText = when (stock?.market) {
                Market.KR -> "${it.toLong()}원"
                Market.US -> "$${"%.2f".format(it)}"
                null -> "$it"
            }
            append("- 현재가: $priceText\n")
        }
        manualFinancials?.takeIf { it.isNotBlank() }?.let {
            append("\n[사용자 입력 재무 메모]\n$it\n")
        }
        append("\n[질문]\n$question\n")
    }
}

sealed interface ResearchEvent {
    data object Preparing : ResearchEvent
    data object LoadingModel : ResearchEvent
    data class Streaming(val partial: String) : ResearchEvent
    data class Completed(val text: String, val latencyMs: Long) : ResearchEvent
    data class Failed(val cause: Throwable) : ResearchEvent
}
