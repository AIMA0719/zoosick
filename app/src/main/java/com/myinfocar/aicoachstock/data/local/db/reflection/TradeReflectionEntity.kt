package com.myinfocar.aicoachstock.data.local.db.reflection

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myinfocar.aicoachstock.data.local.db.trade.TradeEntity

/**
 * AI 매매 복기 1건. Trade 1:1 (FK + UNIQUE index로 강제).
 * Trade 삭제 시 CASCADE — 사실 데이터를 지웠는데 복기만 남는 건 의미 없음.
 *
 * ruleViolations: TradingPrinciple.id 목록 — AppTypeConverters의 stringListToJson 자동 변환.
 */
@Entity(
    tableName = "trade_reflections",
    foreignKeys = [
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["id"],
            childColumns = ["tradeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tradeId"], unique = true)],
)
data class TradeReflectionEntity(
    @PrimaryKey val id: String,
    val tradeId: String,
    val aiAnalysis: String,
    val ruleViolations: List<String>,
    val lesson: String?,
    val myNote: String?,
    val sentimentScore: Double?,
    val modelVersion: String,
    val latencyMs: Long?,
    val createdAt: Long,
)
