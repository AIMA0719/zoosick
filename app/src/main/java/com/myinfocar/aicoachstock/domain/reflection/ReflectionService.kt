package com.myinfocar.aicoachstock.domain.reflection

import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeReflection
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.TradeReflectionRepository
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 매매 1건 복기 — 활성 원칙 + 매매 정보 → LLM → TradeReflection 저장.
 *
 *  - 모델 응답에서 'LESSON: ...' / 'VIOLATED: id1,id2' 마커로 lesson·위반원칙 분리.
 *  - 위반 원칙 ID는 활성 원칙 집합으로 sanity-filter (모델 환각 방지).
 *  - 디스클레이머는 UI에서 별도 카드로 항상 표시 (PRD: 본 응답은 코칭 보조이며 매매 책임은 본인).
 *  - 모델 로드는 매 호출마다 idempotent (이미 로드되어 있으면 즉시 반환).
 */
@Singleton
class ReflectionService @Inject constructor(
    private val engine: LLMEngine,
    private val tradeRepo: TradeRepository,
    private val stockRepo: StockRepository,
    private val principleRepo: TradingPrincipleRepository,
    private val reflectionRepo: TradeReflectionRepository,
) {

    fun generateForTrade(tradeId: String): Flow<ReflectionEvent> = flow {
        emit(ReflectionEvent.Preparing)

        val trade = tradeRepo.findById(tradeId)
        if (trade == null) {
            emit(ReflectionEvent.Failed(IllegalStateException("매매를 찾을 수 없습니다 (id=$tradeId)")))
            return@flow
        }
        val stock = stockRepo.findByTicker(trade.ticker)
        val activePrinciples = principleRepo.observeActive().first()

        emit(ReflectionEvent.LoadingModel)
        engine.load().onFailure { e ->
            emit(ReflectionEvent.Failed(e))
            return@flow
        }

        val systemPrompt = buildSystemPrompt(activePrinciples)
        val userPrompt = buildUserPrompt(trade, stock)

        val accumulated = StringBuilder()
        engine.generate(systemPrompt, userPrompt, context = emptyList())
            .collect { ev ->
                when (ev) {
                    is GenerationEvent.Token -> {
                        accumulated.append(ev.text)
                        emit(ReflectionEvent.Streaming(accumulated.toString()))
                    }
                    is GenerationEvent.Final -> {
                        val parsed = parseResponse(accumulated.toString(), activePrinciples)
                        val reflection = TradeReflection(
                            id = UUID.randomUUID().toString(),
                            tradeId = tradeId,
                            aiAnalysis = parsed.analysis,
                            ruleViolations = parsed.violations,
                            lesson = parsed.lesson,
                            myNote = null,
                            sentimentScore = null,
                            modelVersion = engine.modelVersion,
                            latencyMs = ev.latencyMs,
                            createdAt = Instant.now(),
                        )
                        reflectionRepo.save(reflection)
                        emit(ReflectionEvent.Completed(reflection))
                    }
                    is GenerationEvent.Error -> emit(ReflectionEvent.Failed(ev.cause))
                }
            }
    }

    private fun buildSystemPrompt(principles: List<TradingPrinciple>): String {
        val rules = principles.joinToString("\n") { p ->
            "- [${p.id}] (${p.category.name}, 가중치 ${p.weight}) ${p.ruleText}"
        }
        return """당신은 친근한 매매 복기 코치입니다. 사용자가 체결한 매매 1건을 아래 원칙에 비추어 분석합니다.

[활성 원칙]
${if (rules.isBlank()) "(원칙 미등록 — 일반적 관점에서 분석)" else rules}

응답 형식 (반드시 지켜주세요):
1. 본문: 잘한 점·아쉬운 점을 2~3문장. 단정하지 말고 "그럴 수 있다" 톤.
2. 다음 줄: "LESSON: <한 줄 교훈>"
3. 마지막 줄: "VIOLATED: <위반 원칙 id를 쉼표로 구분>" (위반 없으면 NONE)

투자 권유 금지. 최종 판단은 사용자 본인의 책임입니다.
""".trimIndent()
    }

    private fun buildUserPrompt(trade: Trade, stock: Stock?): String {
        val side = when (trade.side) {
            TradeSide.BUY -> "매수"
            TradeSide.SELL -> "매도"
        }
        val priceStr = when (trade.market) {
            Market.KR -> "${trade.price.toLong()}원"
            Market.US -> "$${"%.2f".format(trade.price)}"
        }
        return buildString {
            append("종목 ${trade.ticker}")
            stock?.nameKo?.let { append(" ($it)") }
            append(" — $side ${trade.qty}주 @ $priceStr\n")
            append("체결 시각: ${trade.executedAt}\n")
            append("감정 태그: ${trade.emotionTag.name}\n")
            trade.reasonText?.takeIf { it.isNotBlank() }?.let {
                append("매매 이유: $it\n")
            }
        }
    }

    private data class Parsed(
        val analysis: String,
        val violations: List<String>,
        val lesson: String?,
    )

    private fun parseResponse(text: String, principles: List<TradingPrinciple>): Parsed {
        val lines = text.lines()
        val validIds = principles.map { it.id }.toSet()

        val violatedLine = lines.lastOrNull { it.trim().startsWith("VIOLATED:") }
        val violations = violatedLine
            ?.substringAfter("VIOLATED:")
            ?.trim()
            ?.takeIf { it.uppercase() != "NONE" && it.isNotBlank() }
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it in validIds }
            ?: emptyList()

        val lessonLine = lines.lastOrNull { it.trim().startsWith("LESSON:") }
        val lesson = lessonLine?.substringAfter("LESSON:")?.trim()?.takeIf { it.isNotBlank() }

        val analysis = lines
            .filterNot { it.trim().startsWith("VIOLATED:") || it.trim().startsWith("LESSON:") }
            .joinToString("\n")
            .trim()

        return Parsed(analysis = analysis, violations = violations, lesson = lesson)
    }
}

sealed interface ReflectionEvent {
    data object Preparing : ReflectionEvent
    data object LoadingModel : ReflectionEvent
    data class Streaming(val partial: String) : ReflectionEvent
    data class Completed(val reflection: TradeReflection) : ReflectionEvent
    data class Failed(val cause: Throwable) : ReflectionEvent
}
