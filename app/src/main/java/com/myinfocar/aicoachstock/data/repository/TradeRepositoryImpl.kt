package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.trade.TradeDao
import com.myinfocar.aicoachstock.data.local.db.trade.toDomain
import com.myinfocar.aicoachstock.data.local.db.trade.toEntity
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradeRepositoryImpl @Inject constructor(
    private val dao: TradeDao,
) : TradeRepository {

    override fun observeAll(): Flow<List<Trade>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByTicker(ticker: String): Flow<List<Trade>> =
        dao.observeByTicker(ticker).map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: String): Trade? = dao.findById(id)?.toDomain()

    override suspend fun save(trade: Trade) {
        dao.upsert(trade.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun count(): Int = dao.count()
}
