package com.myinfocar.aicoachstock.data.local.db.coach

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 코치 세션 내 메시지. role = USER/COACH/SYSTEM.
 *
 * contextRefs: AppTypeConverters의 contextRefsToJson 자동 변환.
 *   주입된 컨텍스트(trades/principles/reflections) ID 목록을 보존.
 */
@Entity(
    tableName = "coach_messages",
    foreignKeys = [
        ForeignKey(
            entity = CoachSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "createdAt"])],
)
data class CoachMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val contextRefs: Map<String, List<String>>,
    val modelVersion: String?,
    val tokenCount: Int?,
    val latencyMs: Long?,
    val createdAt: Long,
)
