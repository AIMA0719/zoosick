package com.myinfocar.aicoachstock.data.local.db.principle

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrincipleDao {

    @Query("SELECT * FROM principles ORDER BY orderIndex ASC, createdAt ASC")
    fun observeAll(): Flow<List<PrincipleEntity>>

    @Query("SELECT * FROM principles WHERE isActive = 1 ORDER BY orderIndex ASC")
    fun observeActive(): Flow<List<PrincipleEntity>>

    @Query("SELECT * FROM principles WHERE id = :id")
    suspend fun findById(id: String): PrincipleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PrincipleEntity)

    @Query("DELETE FROM principles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM principles")
    suspend fun count(): Int
}
