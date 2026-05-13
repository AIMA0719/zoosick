package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * AI가 생성한 매매 복기. Trade 1건당 1개.
 *
 * ruleViolations: 위반으로 판단된 TradingPrinciple.id 목록.
 *   (PRD: 단순 id 배열로 시작, Phase 2에서 severity 객체화 검토.)
 * sentimentScore: -1.0(부정) ~ 1.0(긍정). null = 미산출.
 */
data class TradeReflection(
    val id: String,
    val tradeId: String,
    val aiAnalysis: String,
    val ruleViolations: List<String>,
    val lesson: String?,
    val myNote: String?,
    val sentimentScore: Double?,
    val modelVersion: String,
    val latencyMs: Long?,
    val createdAt: Instant,
)
