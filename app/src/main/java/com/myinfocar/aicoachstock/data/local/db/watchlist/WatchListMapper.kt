package com.myinfocar.aicoachstock.data.local.db.watchlist

import com.myinfocar.aicoachstock.domain.model.WatchListItem
import java.time.Instant

fun WatchListEntity.toDomain(): WatchListItem = WatchListItem(
    id = id,
    ticker = ticker,
    note = note,
    addedAt = Instant.ofEpochMilli(addedAt),
    orderIndex = orderIndex,
)

fun WatchListItem.toEntity(): WatchListEntity = WatchListEntity(
    id = id,
    ticker = ticker,
    note = note,
    addedAt = addedAt.toEpochMilli(),
    orderIndex = orderIndex,
)
