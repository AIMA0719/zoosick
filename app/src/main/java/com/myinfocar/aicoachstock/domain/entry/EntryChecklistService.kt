package com.myinfocar.aicoachstock.domain.entry

import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.model.EntryChecklist
import com.myinfocar.aicoachstock.domain.model.EntryDecision
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.EntryChecklistRepository
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 진입 체크리스트 — 활성 원칙에 대한 사용자 응답 + AI 판정(GO/HOLD/STOP).
 *
 *  - 응답 형식: 본문 + "DECISION: GO/HOLD/STOP" 마커.
 *  - 판정 미발견 시 HOLD로 폴백 (안전한 기본값).
 */
@Singleton
class EntryChecklistService @Inject constructor(
    private val engine: LLMEngine,
    private val principleRepo: TradingPrincipleRepository,
    private val entryRepo: EntryChecklistRepository,
) {

    /** 활성 원칙 한 번에 반환 (UI에서 응답 폼 렌더링용). */
    suspend fun loadPrinciples(): List<TradingPrinciple> = principleRepo.observeActive().first()

    /**
     * 평가 후 EntryChecklist 저장. 토큰 스트리밍.
     * answers는 principleId → "YES"/"NO"/자유 텍스트.
     */
    fun evaluate(
        ticker: String,
        answers: Map<String, String>,
        userNote: String?,
        currentPrice: Double?,
    ): Flow<EntryEvent> = flow {
        emit(EntryEvent.LoadingModel)
        engine.load().onFailure { e ->
            emit(EntryEvent.Failed(e))
            return@flow
        }

        val principles = principleRepo.observeActive().first()
        val systemPrompt = buildSystemPrompt(principles)
        val userPrompt = buildUserPrompt(ticker, principles, answers, userNote, currentPrice)

        val accumulated = StringBuilder()
        engine.generate(systemPrompt, userPrompt, context = emptyList())
            .collect { ev ->
                when (ev) {
                    is GenerationEvent.Token -> {
                        accumulated.append(ev.text)
                        emit(EntryEvent.Streaming(accumulated.toString()))
                    }
                    is GenerationEvent.Final -> {
                        val parsed = parseResponse(accumulated.toString())
                        val checklist = EntryChecklist(
                            id = UUID.randomUUID().toString(),
                            ticker = ticker,
                            answers = answers,
                            userNote = userNote,
                            aiVerdict = parsed.verdict,
                            decision = parsed.decision,
                            currentPrice = currentPrice,
                            executed = false,
                            createdAt = Instant.now(),
                        )
                        entryRepo.save(checklist)
                        emit(EntryEvent.Completed(checklist))
                    }
                    is GenerationEvent.Error -> emit(EntryEvent.Failed(ev.cause))
                }
            }
    }

    private fun buildSystemPrompt(principles: List<TradingPrinciple>): String {
        val rules = principles.joinToString("\n") { p ->
            "- [${p.id}] (${p.category.name}, 가중치 ${p.weight}) ${p.ruleText}"
        }
        return """당신은 진입 체크리스트를 검토하는 매매 코치입니다.

[활성 원칙]
${if (rules.isBlank()) "(원칙 미등록)" else rules}

사용자가 한 종목 진입 전, 각 원칙에 대해 응답한 결과를 검토하고 GO/HOLD/STOP 중 하나를 추천합니다.

응답 형식 (반드시 지켜주세요):
1. 본문: 2~4문장으로 핵심 우려·강점 짚기. 단정 톤 금지.
2. 마지막 줄: "DECISION: GO" 또는 "DECISION: HOLD" 또는 "DECISION: STOP"

판단 기준 (참고):
- GO: 활성 원칙 대부분 충족, 큰 결격 없음
- HOLD: 일부 모호하거나 추가 확인 필요
- STOP: 결격이 분명함, 진입 비추천

투자 권유 금지. 최종 책임은 사용자 본인.
""".trimIndent()
    }

    private fun buildUserPrompt(
        ticker: String,
        principles: List<TradingPrinciple>,
        answers: Map<String, String>,
        userNote: String?,
        currentPrice: Double?,
    ): String = buildString {
        append("종목: $ticker\n")
        currentPrice?.let { append("현재가: $it\n") }
        append("\n[원칙별 응답]\n")
        principles.forEach { p ->
            val a = answers[p.id]?.trim().orEmpty()
            append("- ${p.ruleText} → ${a.ifBlank { "(미응답)" }}\n")
        }
        userNote?.takeIf { it.isNotBlank() }?.let { append("\n[추가 메모]\n$it\n") }
    }

    private data class Parsed(val verdict: String, val decision: EntryDecision)

    private fun parseResponse(text: String): Parsed {
        val lines = text.lines()
        val decisionLine = lines.lastOrNull { it.trim().startsWith("DECISION:") }
        val decision = decisionLine
            ?.substringAfter("DECISION:")
            ?.trim()
            ?.uppercase()
            ?.let { runCatching { EntryDecision.valueOf(it) }.getOrNull() }
            ?: EntryDecision.HOLD

        val verdict = lines
            .filterNot { it.trim().startsWith("DECISION:") }
            .joinToString("\n")
            .trim()

        return Parsed(verdict = verdict, decision = decision)
    }
}

sealed interface EntryEvent {
    data object LoadingModel : EntryEvent
    data class Streaming(val partial: String) : EntryEvent
    data class Completed(val checklist: EntryChecklist) : EntryEvent
    data class Failed(val cause: Throwable) : EntryEvent
}
