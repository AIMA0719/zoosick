package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.reflection.TradeReflectionDao
import com.myinfocar.aicoachstock.data.local.db.reflection.toDomain
import com.myinfocar.aicoachstock.data.local.db.reflection.toEntity
import com.myinfocar.aicoachstock.domain.model.TradeReflection
import com.myinfocar.aicoachstock.domain.repository.TradeReflectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradeReflectionRepositoryImpl @Inject constructor(
    private val dao: TradeReflectionDao,
) : TradeReflectionRepository {

    override suspend fun findByTradeId(tradeId: String): TradeReflection? =
        dao.findByTradeId(tradeId)?.toDomain()

    override fun observeByTradeId(tradeId: String): Flow<TradeReflection?> =
        dao.observeByTradeId(tradeId).map { it?.toDomain() }

    override fun observeAll(): Flow<List<TradeReflection>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(reflection: TradeReflection) {
        dao.upsert(reflection.toEntity())
    }

    override suspend fun updateMyNote(id: String, myNote: String?) {
        dao.updateMyNote(id, myNote)
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
