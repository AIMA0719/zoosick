package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.TradeReflection
import kotlinx.coroutines.flow.Flow

interface TradeReflectionRepository {
    suspend fun findByTradeId(tradeId: String): TradeReflection?
    fun observeByTradeId(tradeId: String): Flow<TradeReflection?>
    fun observeAll(): Flow<List<TradeReflection>>
    suspend fun save(reflection: TradeReflection)
    suspend fun updateMyNote(id: String, myNote: String?)
    suspend fun delete(id: String)
}
