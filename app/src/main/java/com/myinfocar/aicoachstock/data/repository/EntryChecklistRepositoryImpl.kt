package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.entry.EntryChecklistDao
import com.myinfocar.aicoachstock.data.local.db.entry.toDomain
import com.myinfocar.aicoachstock.data.local.db.entry.toEntity
import com.myinfocar.aicoachstock.domain.model.EntryChecklist
import com.myinfocar.aicoachstock.domain.repository.EntryChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryChecklistRepositoryImpl @Inject constructor(
    private val dao: EntryChecklistDao,
) : EntryChecklistRepository {

    override fun observeAll(): Flow<List<EntryChecklist>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByTicker(ticker: String): Flow<List<EntryChecklist>> =
        dao.observeByTicker(ticker).map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: String): EntryChecklist? =
        dao.findById(id)?.toDomain()

    override suspend fun save(checklist: EntryChecklist) {
        dao.upsert(checklist.toEntity())
    }

    override suspend fun markExecuted(id: String, executed: Boolean) {
        dao.markExecuted(id, executed)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }
}
