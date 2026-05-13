package com.myinfocar.aicoachstock.data.local.db.entry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryChecklistDao {

    @Query("SELECT * FROM entry_checklists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryChecklistEntity>>

    @Query("SELECT * FROM entry_checklists WHERE ticker = :ticker ORDER BY createdAt DESC")
    fun observeByTicker(ticker: String): Flow<List<EntryChecklistEntity>>

    @Query("SELECT * FROM entry_checklists WHERE id = :id")
    suspend fun findById(id: String): EntryChecklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EntryChecklistEntity)

    @Query("UPDATE entry_checklists SET executed = :executed WHERE id = :id")
    suspend fun markExecuted(id: String, executed: Boolean)

    @Query("DELETE FROM entry_checklists WHERE id = :id")
    suspend fun delete(id: String)
}
