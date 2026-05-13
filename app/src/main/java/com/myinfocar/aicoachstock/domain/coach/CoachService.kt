package com.myinfocar.aicoachstock.domain.coach

import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.model.CoachMessage
import com.myinfocar.aicoachstock.domain.model.CoachMessageRole
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.CoachRepository
import com.myinfocar.aicoachstock.domain.repository.StockRepository
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
 * 코치 채팅 — 활성 원칙 + 최근 매매 N건을 컨텍스트로 주입.
 *
 *  - 디스클레이머는 UI에서 별도 표시.
 *  - 토픽 종목이 있으면 해당 종목 매매를 우선 적재 (없으면 전체 최근).
 *  - 멀티턴: 같은 세션의 과거 메시지는 last N개만 첨부 (context length 보호).
 */
@Singleton
class CoachService @Inject constructor(
    private val engine: LLMEngine,
    private val coachRepo: CoachRepository,
    private val principleRepo: TradingPrincipleRepository,
    private val tradeRepo: TradeRepository,
    private val stockRepo: StockRepository,
) {

    /** 사용자 메시지를 저장한 뒤, AI 응답을 스트리밍하며 누적 텍스트를 emit. 완료 시 메시지 저장. */
    fun sendMessage(sessionId: String, userText: String): Flow<CoachEvent> = flow {
        val session = coachRepo.findSession(sessionId)
        if (session == null) {
            emit(CoachEvent.Failed(IllegalStateException("세션을 찾을 수 없습니다 (id=$sessionId)")))
            return@flow
        }

        val now = Instant.now()
        val userMsg = CoachMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = CoachMessageRole.USER,
            content = userText,
            contextRefs = emptyMap(),
            modelVersion = null,
            tokenCount = null,
            latencyMs = null,
            createdAt = now,
        )
        coachRepo.appendMessage(userMsg)
        emit(CoachEvent.UserStored)

        emit(CoachEvent.LoadingModel)
        engine.load().onFailure { e ->
            emit(CoachEvent.Failed(e))
            return@flow
        }

        val activePrinciples = principleRepo.observeActive().first()
        val recentTrades = if (session.topicTicker != null) {
            tradeRepo.observeByTicker(session.topicTicker).first().take(RECENT_TRADES_LIMIT)
        } else {
            tradeRepo.observeAll().first().take(RECENT_TRADES_LIMIT)
        }

        val priorMessages = coachRepo.findMessages(sessionId)
            .filter { it.id != userMsg.id }
            .takeLast(HISTORY_LIMIT)

        val systemPrompt = buildSystemPrompt(activePrinciples, session.topicTicker, recentTrades)
        val userPrompt = buildUserPrompt(priorMessages, userText)

        val contextRefs = mapOf(
            "principles" to activePrinciples.map { it.id },
            "trades" to recentTrades.map { it.id },
        )

        val accumulated = StringBuilder()
        engine.generate(systemPrompt, userPrompt, context = emptyList())
            .collect { ev ->
                when (ev) {
                    is GenerationEvent.Token -> {
                        accumulated.append(ev.text)
                        emit(CoachEvent.Streaming(accumulated.toString()))
                    }
                    is GenerationEvent.Final -> {
                        val coachMsg = CoachMessage(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            role = CoachMessageRole.COACH,
                            content = accumulated.toString().trim(),
                            contextRefs = contextRefs,
                            modelVersion = engine.modelVersion,
                            tokenCount = ev.tokenCount,
                            latencyMs = ev.latencyMs,
                            createdAt = Instant.now(),
                        )
                        coachRepo.appendMessage(coachMsg)
                        emit(CoachEvent.Completed(coachMsg))
                    }
                    is GenerationEvent.Error -> emit(CoachEvent.Failed(ev.cause))
                }
            }
    }

    private suspend fun buildSystemPrompt(
        principles: List<TradingPrinciple>,
        topicTicker: String?,
        recentTrades: List<Trade>,
    ): String {
        val rules = principles.joinToString("\n") { p ->
            "- (${p.category.name}, w${p.weight}) ${p.ruleText}"
        }
        val tradesBlock = recentTrades.joinToString("\n") { t -> formatTradeLine(t) }
        val topicHint = topicTicker?.let { "현재 대화 주제 종목: $it" } ?: "특정 종목 지정 없음 — 사용자 질문에 따라 답변"

        return """당신은 친근한 1인 매매 코치입니다. 사용자와 짧은 대화 형식으로 코칭합니다.

[활성 원칙]
${if (rules.isBlank()) "(원칙 미등록)" else rules}

[최근 매매 ${recentTrades.size}건]
${if (tradesBlock.isBlank()) "(매매 기록 없음)" else tradesBlock}

[컨텍스트]
$topicHint

응답 지침:
- 한국어로, 2~5문장 내외로 답변.
- 단정하지 말고 "그럴 수 있다" 톤. 투자 권유·매매 지시 금지.
- 사용자 원칙·매매기록 안에서 패턴이 보이면 부드럽게 짚어주기.
- 최종 판단은 사용자 본인의 책임이라는 점을 자연스럽게 환기.
""".trimIndent()
    }

    private fun buildUserPrompt(history: List<CoachMessage>, userText: String): String {
        if (history.isEmpty()) return userText
        val historyBlock = history.joinToString("\n") { m ->
            val tag = when (m.role) {
                CoachMessageRole.USER -> "사용자"
                CoachMessageRole.COACH -> "코치"
                CoachMessageRole.SYSTEM -> "시스템"
            }
            "$tag: ${m.content}"
        }
        return "[이전 대화]\n$historyBlock\n\n[새 질문]\n$userText"
    }

    private fun formatTradeLine(trade: Trade): String {
        val side = if (trade.side == TradeSide.BUY) "매수" else "매도"
        val price = when (trade.market) {
            Market.KR -> "${trade.price.toLong()}원"
            Market.US -> "$${"%.2f".format(trade.price)}"
        }
        return "- ${trade.ticker} $side ${trade.qty}주 @ $price · 감정 ${trade.emotionTag.name}"
    }

    private companion object {
        const val RECENT_TRADES_LIMIT = 10
        const val HISTORY_LIMIT = 8
    }
}

sealed interface CoachEvent {
    data object UserStored : CoachEvent
    data object LoadingModel : CoachEvent
    data class Streaming(val partial: String) : CoachEvent
    data class Completed(val message: CoachMessage) : CoachEvent
    data class Failed(val cause: Throwable) : CoachEvent
}
