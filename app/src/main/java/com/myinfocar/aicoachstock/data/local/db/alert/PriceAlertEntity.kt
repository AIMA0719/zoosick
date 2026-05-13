package com.myinfocar.aicoachstock.data.local.db.alert

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_alerts",
    indices = [Index(value = ["status", "ticker"])],
)
data class PriceAlertEntity(
    @PrimaryKey val id: String,
    val ticker: String,
    val linkedTradeId: String?,
    val targetPrice: Double,
    val type: String,         // PriceAlertType.name
    val direction: String,    // PriceAlertDirection.name
    val status: String,       // PriceAlertStatus.name
    val triggeredAt: Long?,
    val aiMessage: String?,
    val createdAt: Long,
)
