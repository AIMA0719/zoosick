package com.myinfocar.aicoachstock.data.local.db.entry

import com.myinfocar.aicoachstock.domain.model.EntryChecklist
import com.myinfocar.aicoachstock.domain.model.EntryDecision
import java.time.Instant

fun EntryChecklistEntity.toDomain(): EntryChecklist = EntryChecklist(
    id = id,
    ticker = ticker,
    answers = answers,
    userNote = userNote,
    aiVerdict = aiVerdict,
    decision = EntryDecision.valueOf(decision),
    currentPrice = currentPrice,
    executed = executed,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun EntryChecklist.toEntity(): EntryChecklistEntity = EntryChecklistEntity(
    id = id,
    ticker = ticker,
    answers = answers,
    userNote = userNote,
    aiVerdict = aiVerdict,
    decision = decision.name,
    currentPrice = currentPrice,
    executed = executed,
    createdAt = createdAt.toEpochMilli(),
)
