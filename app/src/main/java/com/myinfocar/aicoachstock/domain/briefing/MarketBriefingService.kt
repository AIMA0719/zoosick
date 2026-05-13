package com.myinfocar.aicoachstock.domain.briefing

import com.myinfocar.aicoachstock.domain.account.AccountService
import com.myinfocar.aicoachstock.domain.account.Holding
import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import com.myinfocar.aicoachstock.domain.repository.WatchListEntry
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 매일 아침 한 줄짜리 시장 브리핑 + 코칭 포인트 생성.
 *
 *  - KOSPI 7일 외국인/기관 흐름
 *  - 시장 예수금 추이
 *  - 내 보유 종목 평가손익 요약
 *  - 내 관심 종목 어제 등락 + 오늘 시작 흐름
 *  - 활성 매매 원칙
 *  → LLM에 단일 user message로 주입 → "오늘 한 줄" + "주목 포인트" + "원칙 체크" 출력
 */
@Singleton
class MarketBriefingService @Inject constructor(
    private val engine: LLMEngine,
    private val stockInfoService: StockInfoService,
    private val accountService: AccountService,
    private val marketDataSource: MarketDataSource,
    private val watchListRepo: WatchListRepository,
    private val principleRepo: TradingPrincipleRepository,
) {

    fun generate(): Flow<BriefingEvent> = flow {
        emit(BriefingEvent.Gathering("시장 동향 수집 중…"))
        val marketInvestor = stockInfoService.fetchMarketInvestorDaily(market = "0001", daysBack = 7)
        val marketFunds = stockInfoService.fetchMarketFunds()

        emit(BriefingEvent.Gathering("보유 / 관심 종목 조회 중…"))
        val balanceResult = accountService.fetchKrBalance()
        val krHoldings: List<Holding> = balanceResult.getOrNull()?.first ?: emptyList()
        val krSummary: com.myinfocar.aicoachstock.domain.account.AccountSummary? = balanceResult.getOrNull()?.second
        val watchEntries: List<WatchListEntry> = watchListRepo.observe().first()
        val watchTicks: List<Pair<WatchListEntry, MarketTick?>> = watchEntries.map { e ->
            val market = e.stock?.market ?: com.myinfocar.aicoachstock.domain.model.Market.KR
            e to marketDataSource.fetchClosePrice(e.item.ticker, market).getOrNull()
        }
        val principles = principleRepo.observeActive().first()

        emit(BriefingEvent.LoadingModel)
        engine.load().onFailure { emit(BriefingEvent.Failed(it)); return@flow }

        val systemPrompt = buildSystemPrompt(principles)
        val userPrompt = buildUserPrompt(
            marketInvestor = marketInvestor,
            marketFunds = marketFunds,
            holdings = krHoldings,
            holdingsSummary = krSummary,
            watchTicks = watchTicks,
        )

        val acc = StringBuilder()
        engine.generate(systemPrompt, userPrompt, context = emptyList()).collect { ev ->
            when (ev) {
                is GenerationEvent.Token -> {
                    acc.append(ev.text)
                    emit(BriefingEvent.Streaming(acc.toString()))
                }
                is GenerationEvent.Final -> emit(BriefingEvent.Completed(acc.toString().trim()))
                is GenerationEvent.Error -> emit(BriefingEvent.Failed(ev.cause))
            }
        }
    }

    private fun buildSystemPrompt(principles: List<TradingPrinciple>): String {
        val rules = principles.joinToString("\n") { "- (${it.category.name}) ${it.ruleText}" }
        return """당신은 매일 아침 매매 코칭 브리핑을 주는 보조입니다. 실데이터 위주로 짧게 정리합니다.

[활성 원칙]
${if (rules.isBlank()) "(원칙 미등록)" else rules}

응답 형식 (반드시 지킬 것):
1. 첫 줄: "🌅 오늘 한 줄: <한 문장>"
2. "📊 시장 (외국인·기관·예수금)" — 2~3문장
3. "💼 내 보유" — 평가손익 요약 1문장 + 주목 포인트
4. "👀 관심 종목" — 가장 강하게 움직인 1~2개만
5. "✅ 원칙 체크" — 오늘 가장 중요한 활성 원칙 1개 환기
6. 마지막에 "본 응답은 코칭 보조이며 매매 책임은 본인" 한 줄.

투자 권유·매수 추천 톤 금지. 일반 정보 정리 + 사용자 본인 원칙에 비춘 환기.
짧고 단정하게. 8~12줄 내외.
""".trimIndent()
    }

    private fun buildUserPrompt(
        marketInvestor: List<com.myinfocar.aicoachstock.data.remote.kis.dto.MarketInvestorDailyItem>,
        marketFunds: List<com.myinfocar.aicoachstock.data.remote.kis.dto.MarketFundsItem>,
        holdings: List<Holding>,
        holdingsSummary: com.myinfocar.aicoachstock.domain.account.AccountSummary?,
        watchTicks: List<Pair<WatchListEntry, MarketTick?>>,
    ): String = buildString {
        append("[KOSPI 외국인/기관 매매동향 — 최근 ${marketInvestor.size}일]\n")
        marketInvestor.take(7).forEach { d ->
            append("- ${d.bstpNmixPrdyDate}: KOSPI ${d.bstpNmixPrpr} (${d.bstpNmixPrdyCtrt}%) / ")
            append("외국인 ${d.frgnNtbyQty} / 기관 ${d.orgnNtbyQty} / 개인 ${d.prsnNtbyQty}\n")
        }

        if (marketFunds.isNotEmpty()) {
            append("\n[시장 예수금 (최근)]\n")
            marketFunds.take(3).forEach { f ->
                append("- ${f.bassDt}: 고객예탁금 ${f.custDpstMnyAmt} / 신용잔고 ${f.crdLonBlceAmt}\n")
            }
        }

        if (holdings.isNotEmpty()) {
            append("\n[내 보유 ${holdings.size}종목]\n")
            holdingsSummary?.let {
                append("총 평가 ${"%,d".format(it.totalEvaluation.toLong())}원 / 평가손익 ${"%+,d".format(it.unrealizedPnl.toLong())}원 (${"%+.2f".format(it.unrealizedPnlRate)}%)\n")
            }
            holdings.take(5).forEach { h ->
                append("- ${h.ticker} ${h.name} ${h.qty}주 / 손익 ${"%+.2f".format(h.unrealizedPnlRate)}%\n")
            }
        }

        if (watchTicks.isNotEmpty()) {
            append("\n[관심 종목 ${watchTicks.size}개 — 현재가]\n")
            watchTicks.take(10).forEach { (entry, tick) ->
                val ticker = entry.item.ticker
                val name = entry.stock?.nameKo ?: "-"
                if (tick != null) {
                    append("- $ticker $name: ${tick.price} (${"%+.2f".format(tick.changePct ?: 0.0)}%)\n")
                } else {
                    append("- $ticker $name: 데이터 없음\n")
                }
            }
        }

        append("\n위 자료로 오늘 한 줄 브리핑 + 주목 포인트 + 원칙 체크 부탁드립니다.\n")
    }
}

sealed interface BriefingEvent {
    data class Gathering(val message: String) : BriefingEvent
    data object LoadingModel : BriefingEvent
    data class Streaming(val partial: String) : BriefingEvent
    data class Completed(val text: String) : BriefingEvent
    data class Failed(val cause: Throwable) : BriefingEvent
}
