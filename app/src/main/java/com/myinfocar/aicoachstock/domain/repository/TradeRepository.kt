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

    /** 이미 import된 한투 주문번호(odno) 전체. 중복 import 방지에 사용. */
    suspend fun importedOrderNos(): Set<String>

    /** 중복(externalOrderNo unique)이면 무시. inserted = true이면 신규. */
    suspend fun saveIfAbsent(trade: Trade): Boolean
}
