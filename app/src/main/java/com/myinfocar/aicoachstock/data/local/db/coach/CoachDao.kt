package com.myinfocar.aicoachstock.data.local.db.coach

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachSessionDao {

    @Query("SELECT * FROM coach_sessions ORDER BY lastMessageAt DESC")
    fun observeAll(): Flow<List<CoachSessionEntity>>

    @Query("SELECT * FROM coach_sessions WHERE id = :id")
    suspend fun findById(id: String): CoachSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CoachSessionEntity)

    @Query("UPDATE coach_sessions SET lastMessageAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("UPDATE coach_sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("DELETE FROM coach_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CoachMessageDao {

    @Query("SELECT * FROM coach_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<CoachMessageEntity>>

    @Query("SELECT * FROM coach_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun findBySession(sessionId: String): List<CoachMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CoachMessageEntity)

    @Query("DELETE FROM coach_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
