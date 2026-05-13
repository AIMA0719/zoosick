package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.Trade
import kotlinx.coroutines.flow.Flow

interface TradeRepository {
    fun observeAll(): Flow<List<Trade>>
    fun observeByTicker(ticker: String): Flow<List<Trade>>
    suspend fun findById(id: String): Trade?
    suspend fun save(trade: Trade)
    suspend fun delete(id: String)
    suspend fun count(): Int
}
