package com.myinfocar.aicoachstock.data.local.db.watchlist

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.myinfocar.aicoachstock.data.local.db.stock.StockEntity

@Entity(tableName = "watchlist_items")
data class WatchListEntity(
    @PrimaryKey val id: String,
    val ticker: String,       // soft FK to stocks.ticker
    val note: String?,
    val addedAt: Long,
    val orderIndex: Int,
)

/** Watchlist 조회 시 stock 메타 함께 가져오기 (LEFT JOIN 효과). */
data class WatchListWithStock(
    @Embedded val item: WatchListEntity,
    @Relation(parentColumn = "ticker", entityColumn = "ticker")
    val stock: StockEntity?,
)
