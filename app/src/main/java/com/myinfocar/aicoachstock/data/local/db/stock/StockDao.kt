package com.myinfocar.aicoachstock.data.local.db.stock

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM stocks WHERE ticker = :ticker")
    suspend fun findByTicker(ticker: String): StockEntity?

    @Query(
        "SELECT * FROM stocks " +
            "WHERE nameKo LIKE '%' || :query || '%' " +
            "   OR ticker LIKE '%' || :query || '%' " +
            "ORDER BY ticker ASC LIMIT 50",
    )
    fun search(query: String): Flow<List<StockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StockEntity)

    @Query("DELETE FROM stocks WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)
}
