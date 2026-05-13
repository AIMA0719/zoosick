package com.myinfocar.aicoachstock.data.local.db.trade

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey val id: String,
    val ticker: String,
    val market: String,      // Market.name
    val side: String,        // TradeSide.name
    val qty: Int,
    val price: Double,
    val fee: Double?,
    val executedAt: Long,    // epoch millis
    val reasonText: String?,
    val emotionTag: String,  // EmotionTag.name
    val linkedChecklistId: String?,
    val createdAt: Long,
)
