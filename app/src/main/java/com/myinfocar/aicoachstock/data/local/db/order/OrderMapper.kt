package com.myinfocar.aicoachstock.data.local.db.order

import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderStatus
import com.myinfocar.aicoachstock.domain.model.OrderType
import com.myinfocar.aicoachstock.domain.model.TradeSide
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }
private val idListSerializer = ListSerializer(String.serializer())

fun OrderEntity.toDomain(): Order = Order(
    id = id,
    ticker = ticker,
    market = runCatching { Market.valueOf(market) }.getOrDefault(Market.KR),
    side = runCatching { TradeSide.valueOf(side) }.getOrDefault(TradeSide.BUY),
    orderType = runCatching { OrderType.valueOf(orderType) }.getOrDefault(OrderType.LIMIT),
    qty = qty,
    price = price,
    filledQty = filledQty,
    avgFillPrice = avgFillPrice,
    status = runCatching { OrderStatus.valueOf(status) }.getOrDefault(OrderStatus.PENDING),
    krxOrderNo = krxOrderNo,
    krxOrderOrgNo = krxOrderOrgNo,
    originOrderNo = originOrderNo,
    linkedPrincipleIds = runCatching {
        json.decodeFromString(idListSerializer, linkedPrincipleIdsJson)
    }.getOrDefault(emptyList()),
    createdAt = Instant.ofEpochMilli(createdAt),
    submittedAt = submittedAt?.let(Instant::ofEpochMilli),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    errorMessage = errorMessage,
    rawMsgCd = rawMsgCd,
)

fun Order.toEntity(): OrderEntity = OrderEntity(
    id = id,
    ticker = ticker,
    market = market.name,
    side = side.name,
    orderType = orderType.name,
    qty = qty,
    price = price,
    filledQty = filledQty,
    avgFillPrice = avgFillPrice,
    status = status.name,
    krxOrderNo = krxOrderNo,
    krxOrderOrgNo = krxOrderOrgNo,
    originOrderNo = originOrderNo,
    linkedPrincipleIdsJson = json.encodeToString(idListSerializer, linkedPrincipleIds),
    createdAt = createdAt.toEpochMilli(),
    submittedAt = submittedAt?.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
    errorMessage = errorMessage,
    rawMsgCd = rawMsgCd,
)
