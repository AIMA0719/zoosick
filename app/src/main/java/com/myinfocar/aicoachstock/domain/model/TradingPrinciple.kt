package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/**
 * 사용자가 정한 매매 규칙. AI 복기·진입 체크리스트의 판단 기준.
 *
 * weight: 1~5 (5가 최상위 중요도).
 * orderIndex: 화면 표시 순서. 사용자가 드래그해서 변경 가능.
 */
data class TradingPrinciple(
    val id: String,
    val category: PrincipleCategory,
    val ruleText: String,
    val weight: Int,
    val isActive: Boolean,
    val orderIndex: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(weight in 1..5) { "weight must be 1..5 (got $weight)" }
        require(ruleText.isNotBlank()) { "ruleText must not be blank" }
    }
}
