package com.myinfocar.aicoachstock.data.local.db.coach

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 코치 채팅 세션. CoachMessage가 sessionId로 참조.
 * 세션 삭제 시 메시지도 함께 CASCADE.
 */
@Entity(
    tableName = "coach_sessions",
    indices = [Index(value = ["lastMessageAt"])],
)
data class CoachSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topicTicker: String?,
    val startedAt: Long,
    val lastMessageAt: Long,
)
