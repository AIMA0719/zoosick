package com.myinfocar.aicoachstock.data.local.db.trade

import com.myinfocar.aicoachstock.domain.model.EmotionTag
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeSide
import java.time.Instant

fun TradeEntity.toDomain(): Trade = Trade(
    id = id,
    ticker = ticker,
    market = Market.valueOf(market),
    side = TradeSide.valueOf(side),
    qty = qty,
    price = price,
    fee = fee,
    executedAt = Instant.ofEpochMilli(executedAt),
    reasonText = reasonText,
    emotionTag = EmotionTag.valueOf(emotionTag),
    linkedChecklistId = linkedChecklistId,
    createdAt = Instant.ofEpochMilli(createdAt),
    externalOrderNo = externalOrderNo,
)

fun Trade.toEntity(): TradeEntity = TradeEntity(
    id = id,
    ticker = ticker,
    market = market.name,
    side = side.name,
    qty = qty,
    price = price,
    fee = fee,
    executedAt = executedAt.toEpochMilli(),
    reasonText = reasonText,
    emotionTag = emotionTag.name,
    linkedChecklistId = linkedChecklistId,
    createdAt = createdAt.toEpochMilli(),
    externalOrderNo = externalOrderNo,
)
