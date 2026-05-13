package com.myinfocar.aicoachstock.data.local.db.stock

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stocks")
data class StockEntity(
    @PrimaryKey val ticker: String,
    val nameKo: String,
    val nameEn: String?,
    val exchange: String,    // Exchange.name
    val sector: String?,
    val currency: String,    // Currency.name
)
