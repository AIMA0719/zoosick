package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.Stock
import kotlinx.coroutines.flow.Flow

interface StockRepository {
    suspend fun findByTicker(ticker: String): Stock?
    suspend fun save(stock: Stock)
    fun search(query: String): Flow<List<Stock>>
}
