package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.stock.StockDao
import com.myinfocar.aicoachstock.data.local.db.stock.toDomain
import com.myinfocar.aicoachstock.data.local.db.stock.toEntity
import com.myinfocar.aicoachstock.data.local.db.watchlist.WatchListDao
import com.myinfocar.aicoachstock.data.local.db.watchlist.toDomain
import com.myinfocar.aicoachstock.data.local.db.watchlist.toEntity
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.model.WatchListItem
import com.myinfocar.aicoachstock.domain.repository.WatchListEntry
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchListRepositoryImpl @Inject constructor(
    private val watchListDao: WatchListDao,
    private val stockDao: StockDao,
) : WatchListRepository {

    override fun observe(): Flow<List<WatchListEntry>> =
        watchListDao.observeWithStocks().map { list ->
            list.map { row ->
                WatchListEntry(
                    item = row.item.toDomain(),
                    stock = row.stock?.toDomain(),
                )
            }
        }

    override suspend fun findById(id: String): WatchListItem? =
        watchListDao.findById(id)?.toDomain()

    override suspend fun add(ticker: String, note: String?, defaultStock: Stock?): String {
        if (defaultStock != null) {
            val existing = stockDao.findByTicker(ticker)
            if (existing == null) {
                stockDao.upsert(defaultStock.toEntity())
            }
        }
        val id = UUID.randomUUID().toString()
        val item = WatchListItem(
            id = id,
            ticker = ticker,
            note = note,
            addedAt = Instant.now(),
            orderIndex = watchListDao.count(),
        )
        watchListDao.upsert(item.toEntity())
        return id
    }

    override suspend fun updateNote(id: String, note: String?) {
        val existing = watchListDao.findById(id) ?: return
        watchListDao.upsert(existing.copy(note = note))
    }

    override suspend fun remove(id: String) {
        watchListDao.deleteById(id)
    }
}
