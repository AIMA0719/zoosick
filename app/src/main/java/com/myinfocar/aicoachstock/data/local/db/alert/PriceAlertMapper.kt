package com.myinfocar.aicoachstock.data.local.db.alert

import com.myinfocar.aicoachstock.domain.model.PriceAlert
import com.myinfocar.aicoachstock.domain.model.PriceAlertDirection
import com.myinfocar.aicoachstock.domain.model.PriceAlertStatus
import com.myinfocar.aicoachstock.domain.model.PriceAlertType
import java.time.Instant

fun PriceAlertEntity.toDomain(): PriceAlert = PriceAlert(
    id = id,
    ticker = ticker,
    linkedTradeId = linkedTradeId,
    targetPrice = targetPrice,
    type = PriceAlertType.valueOf(type),
    direction = PriceAlertDirection.valueOf(direction),
    status = PriceAlertStatus.valueOf(status),
    triggeredAt = triggeredAt?.let(Instant::ofEpochMilli),
    aiMessage = aiMessage,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun PriceAlert.toEntity(): PriceAlertEntity = PriceAlertEntity(
    id = id,
    ticker = ticker,
    linkedTradeId = linkedTradeId,
    targetPrice = targetPrice,
    type = type.name,
    direction = direction.name,
    status = status.name,
    triggeredAt = triggeredAt?.toEpochMilli(),
    aiMessage = aiMessage,
    createdAt = createdAt.toEpochMilli(),
)
