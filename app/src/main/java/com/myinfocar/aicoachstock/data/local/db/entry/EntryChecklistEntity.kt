package com.myinfocar.aicoachstock.data.local.db.entry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 진입 체크리스트 1건. principleId → 응답 텍스트의 맵을 JSON으로 보존.
 *
 * decision: EntryDecision.name. AI 판정 결과(GO/HOLD/STOP).
 * executed: 사용자가 이 체크 후 실제 매매로 진행했는가.
 */
@Entity(
    tableName = "entry_checklists",
    indices = [Index(value = ["ticker", "createdAt"])],
)
data class EntryChecklistEntity(
    @PrimaryKey val id: String,
    val ticker: String,
    val answers: Map<String, String>,
    val userNote: String?,
    val aiVerdict: String,
    val decision: String,
    val currentPrice: Double?,
    val executed: Boolean,
    val createdAt: Long,
)
