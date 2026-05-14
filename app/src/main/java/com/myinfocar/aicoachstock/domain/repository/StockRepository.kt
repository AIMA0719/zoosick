package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.Stock
import kotlinx.coroutines.flow.Flow

interface StockRepository {
    suspend fun findByTicker(ticker: String): Stock?
    suspend fun save(stock: Stock)
    fun search(query: String): Flow<List<Stock>>

    /** stocks 테이블에서 nameKo/nameEn/ticker 부분 매칭 검색. 종목 마스터 적재 후 자연어 검색 경로. */
    suspend fun searchOnce(query: String): List<Stock>

    /** stocks 테이블 행 수. 0이면 마스터 미적재 상태. */
    suspend fun masterCount(): Int
}
