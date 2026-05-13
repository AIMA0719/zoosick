package com.myinfocar.aicoachstock.data.local.db.watchlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchListDao {

    @Query("SELECT * FROM watchlist_items ORDER BY orderIndex ASC, addedAt DESC")
    fun observeAll(): Flow<List<WatchListEntity>>

    @Transaction
    @Query("SELECT * FROM watchlist_items ORDER BY orderIndex ASC, addedAt DESC")
    fun observeWithStocks(): Flow<List<WatchListWithStock>>

    @Query("SELECT * FROM watchlist_items WHERE id = :id")
    suspend fun findById(id: String): WatchListEntity?

    @Query("SELECT COUNT(*) FROM watchlist_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchListEntity)

    @Query("DELETE FROM watchlist_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
