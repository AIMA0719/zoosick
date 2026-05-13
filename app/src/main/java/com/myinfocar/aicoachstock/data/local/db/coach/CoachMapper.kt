package com.myinfocar.aicoachstock.data.local.db.coach

import com.myinfocar.aicoachstock.domain.model.CoachMessage
import com.myinfocar.aicoachstock.domain.model.CoachMessageRole
import com.myinfocar.aicoachstock.domain.model.CoachSession
import java.time.Instant

fun CoachSessionEntity.toDomain(): CoachSession = CoachSession(
    id = id,
    title = title,
    topicTicker = topicTicker,
    startedAt = Instant.ofEpochMilli(startedAt),
    lastMessageAt = Instant.ofEpochMilli(lastMessageAt),
)

fun CoachSession.toEntity(): CoachSessionEntity = CoachSessionEntity(
    id = id,
    title = title,
    topicTicker = topicTicker,
    startedAt = startedAt.toEpochMilli(),
    lastMessageAt = lastMessageAt.toEpochMilli(),
)

fun CoachMessageEntity.toDomain(): CoachMessage = CoachMessage(
    id = id,
    sessionId = sessionId,
    role = CoachMessageRole.valueOf(role),
    content = content,
    contextRefs = contextRefs,
    modelVersion = modelVersion,
    tokenCount = tokenCount,
    latencyMs = latencyMs,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun CoachMessage.toEntity(): CoachMessageEntity = CoachMessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    contextRefs = contextRefs,
    modelVersion = modelVersion,
    tokenCount = tokenCount,
    latencyMs = latencyMs,
    createdAt = createdAt.toEpochMilli(),
)
