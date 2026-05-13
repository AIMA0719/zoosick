package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.EntryChecklist
import kotlinx.coroutines.flow.Flow

interface EntryChecklistRepository {
    fun observeAll(): Flow<List<EntryChecklist>>
    fun observeByTicker(ticker: String): Flow<List<EntryChecklist>>
    suspend fun findById(id: String): EntryChecklist?
    suspend fun save(checklist: EntryChecklist)
    suspend fun markExecuted(id: String, executed: Boolean)
    suspend fun delete(id: String)
}
