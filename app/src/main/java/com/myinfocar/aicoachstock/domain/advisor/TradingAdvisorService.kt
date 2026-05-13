package com.myinfocar.aicoachstock.domain.advisor

import com.myinfocar.aicoachstock.domain.account.AccountService
import com.myinfocar.aicoachstock.domain.account.Holding
import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 특정 종목에 대해 BUY/HOLD/SELL 추천 + 근거.
 *
 *  - 한투 API에서 실데이터를 모두 수집해서 LLM 프롬프트에 주입:
 *    현재가/등락 · 일봉 30일 · 재무비율 3기 · 종목 메타 ·
 *    개인/외국인/기관 매매동향 5일 · 호가 잔량 · (해외) 뉴스 헤드라인 ·
 *    활성 매매 원칙 · 보유 여부(평단·평가손익) · 최근 매매 본인 기록
 *
 *  - 응답 형식: 본문 + "RECOMMENDATION: BUY/HOLD/SELL" + "CONFIDENCE: 0~100"
 *  - 투자 권유 톤 금지 — "이런 관점에서 볼 수 있다" 톤. 최종 책임은 본인.
 */
@Singleton
class TradingAdvisorService @Inject constructor(
    private val engine: LLMEngine,
    private val stockRepo: StockRepository,
    private val stockInfoService: StockInfoService,
    private val marketDataSource: MarketDataSource,
    private val accountService: AccountService,
    private val principleRepo: TradingPrincipleRepository,
    private val tradeRepo: TradeRepository,
) {

    fun analyze(ticker: String): Flow<AdvisorEvent> = flow {
        emit(AdvisorEvent.GatheringData("종목 정보 수집 중…"))

        val stock = stockRepo.findByTicker(ticker)
        val market = stock?.market ?: Market.KR
        val isKr = market == Market.KR

        emit(AdvisorEvent.GatheringData("현재가 / 재무 / 차트 / 투자자 동향 조회…"))
        // 모든 한투 API를 동시에 launch — KisRateLimiter가 wire-level에서 직렬화하지만,
        // suspend/응답 파싱이 다음 호출의 await와 겹쳐서 전체 latency가 줄어듦.
        val (tick, info, finance, chart, investors, asking, news, estimate, opinions, holding) =
            coroutineScope {
                val tickD = async { marketDataSource.fetchClosePrice(ticker, market).getOrNull() }
                val infoD = async { if (isKr) stockInfoService.fetchStockInfo(ticker) else null }
                val financeD = async { if (isKr) stockInfoService.fetchFinanceRatio(ticker, 3) else emptyList() }
                val chartD = async { if (isKr) stockInfoService.fetchDailyChart(ticker, 30) else emptyList() }
                val investorsD = async { if (isKr) stockInfoService.fetchInvestorTrend(ticker, 5) else emptyList() }
                val askingD = async { if (isKr) stockInfoService.fetchAskingPrice(ticker) else null }
                val newsD = async {
                    if (market == Market.US) stockInfoService.fetchOverseasNews(symbol = ticker, limit = 5)
                    else emptyList()
                }
                val estimateD = async { if (isKr) stockInfoService.fetchEstimatePerform(ticker) else null }
                val opinionsD = async {
                    if (isKr) stockInfoService.fetchInvestOpinion(ticker, daysBack = 60, limit = 5) else emptyList()
                }
                val holdingD = async {
                    runCatching {
                        if (isKr) accountService.fetchKrBalance().getOrNull()?.first?.firstOrNull { it.ticker == ticker }
                        else accountService.fetchUsBalance().getOrNull()?.first?.firstOrNull { it.ticker == ticker }
                    }.getOrNull()
                }
                AdvisorFetchBundle(
                    tickD.await(), infoD.await(), financeD.await(), chartD.await(),
                    investorsD.await(), askingD.await(), newsD.await(),
                    estimateD.await(), opinionsD.await(), holdingD.await(),
                )
            }

        val principles = principleRepo.observeActive().first()
        val myTrades = tradeRepo.observeByTicker(ticker).first().take(5)

        emit(AdvisorEvent.LoadingModel)
        engine.load().onFailure { e ->
            emit(AdvisorEvent.Failed(e))
            return@flow
        }

        val systemPrompt = buildSystemPrompt(principles)
        val userPrompt = buildUserPrompt(
            ticker = ticker,
            market = market,
            stockNameKo = info?.prdtName ?: stock?.nameKo,
            sector = info?.idxBztpSmallName ?: info?.stdIndustryName ?: stock?.sector,
            tick = tick,
            chart = chart,
            finance = finance,
            investors = investors,
            asking = asking,
            news = news,
            estimate = estimate,
            opinions = opinions,
            holding = holding,
            myTrades = myTrades.map {
                "${it.side.name} ${it.qty}주 @ ${it.price} (${it.emotionTag.name})"
            },
        )

        val accumulated = StringBuilder()
        engine.generate(systemPrompt, userPrompt, context = emptyList()).collect { ev ->
            when (ev) {
                is GenerationEvent.Token -> {
                    accumulated.append(ev.text)
                    emit(AdvisorEvent.Streaming(accumulated.toString()))
                }
                is GenerationEvent.Final -> {
                    val parsed = parseResponse(accumulated.toString())
                    emit(AdvisorEvent.Completed(parsed))
                }
                is GenerationEvent.Error -> emit(AdvisorEvent.Failed(ev.cause))
            }
        }
    }

    private fun buildSystemPrompt(principles: List<TradingPrinciple>): String {
        val rules = principles.joinToString("\n") { p ->
            "- (${p.category.name}, w${p.weight}) ${p.ruleText}"
        }
        return """당신은 매매 코칭 보조입니다. 한투 OpenAPI 실데이터를 보고 사용자가 지금 어떻게 행동하면 좋을지 의견을 줍니다.

[활성 원칙]
${if (rules.isBlank()) "(원칙 미등록)" else rules}

응답 형식 (반드시 지킬 것):
1. 본문 (3~6문장):
   - 현재 가격 / 추세 / 거래량 / 외국인·기관 흐름을 짧게 종합
   - 활성 원칙 중 강하게 만족/위반되는 점이 있으면 언급
   - 보유 중이면 평단 대비 손익을 짚어주기
   - 단정 톤 금지, "이런 관점에서 볼 수 있다" 정도
2. 다음 줄: "RECOMMENDATION: BUY" 또는 "HOLD" 또는 "SELL"
3. 마지막 줄: "CONFIDENCE: 0-100" (확신도 백분율)

지침:
- 투자 권유·매매 지시 금지. 코칭 톤.
- 데이터가 부족하면 "데이터 부족"이라 말하고 HOLD + 낮은 confidence로 표기.
- 미국 종목인데 데이터가 일부 없을 수 있음 — 그 경우 명시.
""".trimIndent()
    }

    private fun buildUserPrompt(
        ticker: String,
        market: Market,
        stockNameKo: String?,
        sector: String?,
        tick: MarketTick?,
        chart: List<com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar>,
        finance: List<com.myinfocar.aicoachstock.data.remote.kis.dto.FinanceRatioItem>,
        investors: List<com.myinfocar.aicoachstock.data.remote.kis.dto.InvestorDailyItem>,
        asking: com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse?,
        news: List<com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasNewsItem>,
        estimate: com.myinfocar.aicoachstock.data.remote.kis.dto.EstimatePerformResponse?,
        opinions: List<com.myinfocar.aicoachstock.data.remote.kis.dto.InvestOpinionItem>,
        holding: Holding?,
        myTrades: List<String>,
    ): String = buildString {
        append("[종목] $ticker")
        stockNameKo?.let { append(" / $it") }
        append(" / 시장: ${market.name}")
        sector?.let { append(" / 섹터: $it") }
        append("\n\n")

        if (tick != null) {
            append("[현재가] ${tick.price}")
            tick.changePct?.let { append("  (${"%+.2f".format(it)}%)") }
            append("\n")
        }

        if (chart.isNotEmpty()) {
            val first = chart.firstOrNull()?.stckClpr?.toDoubleOrNull()
            val last = chart.lastOrNull()?.stckClpr?.toDoubleOrNull()
            val highs = chart.mapNotNull { it.stckHgpr?.toDoubleOrNull() }
            val lows = chart.mapNotNull { it.stckLwpr?.toDoubleOrNull() }
            if (first != null && last != null && last > 0) {
                val pct = (first - last) / last * 100
                append("[일봉 ${chart.size}일] 시작 $last → 최근 $first  (${"%+.2f".format(pct)}%)\n")
            }
            if (highs.isNotEmpty()) append("  기간 고가 ${highs.max()} / 저가 ${lows.minOrNull() ?: "-"}\n")
        }

        if (asking != null) {
            asking.output1?.let { l ->
                append("[호가] 매도1 ${l.askp1} (${l.askpRsqn1}) / 매수1 ${l.bidp1} (${l.bidpRsqn1})\n")
                append("  총 매도잔량 ${l.totalAskpRsqn} / 총 매수잔량 ${l.totalBidpRsqn}\n")
            }
            asking.output2?.let { s ->
                if (!s.antcCnpr.isNullOrBlank() && s.antcCnpr != "0") {
                    append("  예상체결가 ${s.antcCnpr} (${s.antcCntgPrdyCtrt}%)\n")
                }
            }
        }

        if (finance.isNotEmpty()) {
            append("[재무비율]\n")
            finance.forEach { f ->
                append("- ${f.stacYymm.orEmpty()}: ROE ${f.roeVal ?: "-"}%, EPS ${f.eps ?: "-"}, ")
                append("부채비율 ${f.lbltRate ?: "-"}%, 매출증가 ${f.grs ?: "-"}%, 영업이익증가 ${f.bsopPrfiInrt ?: "-"}%\n")
            }
        }

        if (investors.isNotEmpty()) {
            append("[투자자별 매매동향 (최근 ${investors.size}일, 순매수수량)]\n")
            investors.forEach { d ->
                append("- ${d.stckBsopDate}: 개인 ${d.prsnNtbyQty} / 외국인 ${d.frgnNtbyQty} / 기관 ${d.orgnNtbyQty}\n")
            }
        }

        if (news.isNotEmpty()) {
            append("[해외 뉴스 헤드라인 (최근 ${news.size}건)]\n")
            news.forEach { n ->
                append("- (${n.dataDt}) ${n.title}\n")
            }
        }

        if (estimate?.output2?.isNotEmpty() == true) {
            append("[추정실적 (애널리스트 컨센서스)]\n")
            estimate.output1?.rcmdReason?.let { append("- 추천사유: $it\n") }
            estimate.output2.take(4).forEach { e ->
                append("- ${e.data1.orEmpty()} / 매출 ${e.data2.orEmpty()} / 영업이익 ${e.data3.orEmpty()} / 순이익 ${e.data4.orEmpty()} / EPS ${e.data5.orEmpty()}\n")
            }
        }

        if (opinions.isNotEmpty()) {
            append("[증권사 투자의견 (최근 ${opinions.size}건)]\n")
            opinions.forEach { o ->
                append("- ${o.stckBsopDate} ${o.mbcrName.orEmpty()} ${o.invtOpnn.orEmpty()} / 목표가 ${o.htsGoalPrc.orEmpty()} (괴리 ${o.dprt.orEmpty()}%)\n")
            }
        }

        if (holding != null) {
            append("\n[내 보유]\n")
            append("- 보유 ${holding.qty}주, 평단 ${holding.avgBuyPrice}, 현재 ${holding.currentPrice}\n")
            append("- 평가손익 ${holding.unrealizedPnl} (${"%+.2f".format(holding.unrealizedPnlRate)}%)\n")
        } else {
            append("\n[내 보유] 미보유\n")
        }

        if (myTrades.isNotEmpty()) {
            append("\n[이 종목 최근 내 매매 ${myTrades.size}건]\n")
            myTrades.forEach { append("- $it\n") }
        }

        append("\n[질문] 위 데이터를 종합해서 지금 BUY/HOLD/SELL 중 어떤 자세가 합리적일지 짚어주세요.\n")
    }

    data class Parsed(
        val analysis: String,
        val recommendation: Recommendation,
        val confidence: Int,
    )

    enum class Recommendation { BUY, HOLD, SELL }

    private fun parseResponse(text: String): Parsed {
        val lines = text.lines()
        val recLine = lines.lastOrNull { it.trim().startsWith("RECOMMENDATION:") }
        val rec = recLine?.substringAfter("RECOMMENDATION:")?.trim()?.uppercase()?.let {
            runCatching { Recommendation.valueOf(it) }.getOrNull()
        } ?: Recommendation.HOLD

        val confLine = lines.lastOrNull { it.trim().startsWith("CONFIDENCE:") }
        val conf = confLine
            ?.substringAfter("CONFIDENCE:")
            ?.trim()
            ?.filter { it.isDigit() }
            ?.toIntOrNull()
            ?.coerceIn(0, 100)
            ?: 30

        val body = lines.filterNot {
            val t = it.trim()
            t.startsWith("RECOMMENDATION:") || t.startsWith("CONFIDENCE:")
        }.joinToString("\n").trim()

        return Parsed(analysis = body, recommendation = rec, confidence = conf)
    }
}

/** 한투 API 병렬 호출 결과 묶음. componentN으로 destructure. */
private data class AdvisorFetchBundle(
    val tick: com.myinfocar.aicoachstock.domain.model.MarketTick?,
    val info: com.myinfocar.aicoachstock.data.remote.kis.dto.StockInfoOutput?,
    val finance: List<com.myinfocar.aicoachstock.data.remote.kis.dto.FinanceRatioItem>,
    val chart: List<com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar>,
    val investors: List<com.myinfocar.aicoachstock.data.remote.kis.dto.InvestorDailyItem>,
    val asking: com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse?,
    val news: List<com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasNewsItem>,
    val estimate: com.myinfocar.aicoachstock.data.remote.kis.dto.EstimatePerformResponse?,
    val opinions: List<com.myinfocar.aicoachstock.data.remote.kis.dto.InvestOpinionItem>,
    val holding: Holding?,
)

sealed interface AdvisorEvent {
    data class GatheringData(val message: String) : AdvisorEvent
    data object LoadingModel : AdvisorEvent
    data class Streaming(val partial: String) : AdvisorEvent
    data class Completed(val parsed: TradingAdvisorService.Parsed) : AdvisorEvent
    data class Failed(val cause: Throwable) : AdvisorEvent
}
