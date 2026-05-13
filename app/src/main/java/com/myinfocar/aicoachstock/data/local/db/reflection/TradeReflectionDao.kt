package com.myinfocar.aicoachstock.data.local.db.reflection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeReflectionDao {

    @Query("SELECT * FROM trade_reflections WHERE tradeId = :tradeId")
    suspend fun findByTradeId(tradeId: String): TradeReflectionEntity?

    @Query("SELECT * FROM trade_reflections WHERE tradeId = :tradeId")
    fun observeByTradeId(tradeId: String): Flow<TradeReflectionEntity?>

    @Query("SELECT * FROM trade_reflections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TradeReflectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TradeReflectionEntity)

    @Query("UPDATE trade_reflections SET myNote = :myNote WHERE id = :id")
    suspend fun updateMyNote(id: String, myNote: String?)

    @Query("DELETE FROM trade_reflections WHERE id = :id")
    suspend fun deleteById(id: String)
}
