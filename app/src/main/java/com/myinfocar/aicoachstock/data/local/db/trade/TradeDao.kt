package com.myinfocar.aicoachstock.data.local.db.trade

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Query("SELECT * FROM trades ORDER BY executedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE ticker = :ticker ORDER BY executedAt DESC")
    fun observeByTicker(ticker: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun findById(id: String): TradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM trades")
    suspend fun count(): Int
}
