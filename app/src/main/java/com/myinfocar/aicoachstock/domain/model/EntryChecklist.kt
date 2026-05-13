package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 진입 체크리스트. 원칙별 응답 + AI 판정.
 *
 * answers: principleId -> 응답("YES"/"NO"/사용자 자유 텍스트).
 * executed: 이 체크 후 실제 Trade로 이어졌는가.
 */
data class EntryChecklist(
    val id: String,
    val ticker: String,
    val answers: Map<String, String>,
    val userNote: String?,
    val aiVerdict: String,
    val decision: EntryDecision,
    val currentPrice: Double?,
    val executed: Boolean,
    val createdAt: Instant,
)
