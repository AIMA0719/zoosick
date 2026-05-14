package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.stock.StockDao
import com.myinfocar.aicoachstock.data.local.db.stock.toDomain
import com.myinfocar.aicoachstock.data.local.db.stock.toEntity
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val dao: StockDao,
) : StockRepository {

    override suspend fun findByTicker(ticker: String): Stock? =
        dao.findByTicker(ticker)?.toDomain()

    override suspend fun save(stock: Stock) {
        dao.upsert(stock.toEntity())
    }

    override fun search(query: String): Flow<List<Stock>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    override suspend fun searchOnce(query: String): List<Stock> =
        dao.searchByText(query).map { it.toDomain() }

    override suspend fun masterCount(): Int = dao.count()
}
