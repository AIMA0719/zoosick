package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.principle.PrincipleDao
import com.myinfocar.aicoachstock.data.local.db.principle.toDomain
import com.myinfocar.aicoachstock.data.local.db.principle.toEntity
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradingPrincipleRepositoryImpl @Inject constructor(
    private val dao: PrincipleDao,
) : TradingPrincipleRepository {

    override fun observeAll(): Flow<List<TradingPrinciple>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<TradingPrinciple>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: String): TradingPrinciple? =
        dao.findById(id)?.toDomain()

    override suspend fun save(principle: TradingPrinciple) {
        dao.upsert(principle.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun count(): Int = dao.count()
}
