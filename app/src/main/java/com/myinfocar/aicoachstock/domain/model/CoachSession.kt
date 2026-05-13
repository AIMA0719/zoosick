package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/** 코치 채팅 세션. 종목별·일자별로 묶을 수 있음. */
data class CoachSession(
    val id: String,
    val title: String,
    val topicTicker: String?,
    val startedAt: Instant,
    val lastMessageAt: Instant,
)

/**
 * 코치 세션 내 개별 메시지.
 *
 * contextRefs: 함께 LLM 프롬프트에 주입된 컨텍스트 ID 목록.
 *   key는 "trades"/"principles"/"reflections" 등, value는 ID 목록.
 *   Phase 1은 JSON 컬럼으로 저장. (Phase 2 분석 필요도에 따라 정규화 검토.)
 */
data class CoachMessage(
    val id: String,
    val sessionId: String,
    val role: CoachMessageRole,
    val content: String,
    val contextRefs: Map<String, List<String>>,
    val modelVersion: String?,
    val tokenCount: Int?,
    val latencyMs: Long?,
    val createdAt: Instant,
)
