package com.myinfocar.aicoachstock.data.local.db.reflection

import com.myinfocar.aicoachstock.domain.model.TradeReflection
import java.time.Instant

fun TradeReflectionEntity.toDomain(): TradeReflection = TradeReflection(
    id = id,
    tradeId = tradeId,
    aiAnalysis = aiAnalysis,
    ruleViolations = ruleViolations,
    lesson = lesson,
    myNote = myNote,
    sentimentScore = sentimentScore,
    modelVersion = modelVersion,
    latencyMs = latencyMs,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun TradeReflection.toEntity(): TradeReflectionEntity = TradeReflectionEntity(
    id = id,
    tradeId = tradeId,
    aiAnalysis = aiAnalysis,
    ruleViolations = ruleViolations,
    lesson = lesson,
    myNote = myNote,
    sentimentScore = sentimentScore,
    modelVersion = modelVersion,
    latencyMs = latencyMs,
    createdAt = createdAt.toEpochMilli(),
)
