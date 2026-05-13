package com.myinfocar.aicoachstock.data.local.db.trade

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * externalOrderNo: 한투 자동 import 시 odno(주문번호). null이면 수동 입력 (MANUAL).
 *   non-null이면 IMPORTED — unique index로 중복 import 방지.
 */
@Entity(
    tableName = "trades",
    indices = [Index(value = ["externalOrderNo"], unique = true)],
)
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
    val externalOrderNo: String? = null,
)
