package com.myinfocar.aicoachstock.data.local.db.order

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE ticker = :ticker ORDER BY createdAt DESC")
    fun observeByTicker(ticker: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status IN (:statuses) ORDER BY createdAt DESC")
    suspend fun findByStatuses(statuses: List<String>): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun findById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE krxOrderNo = :odno LIMIT 1")
    suspend fun findByKrxOrderNo(odno: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OrderEntity)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteById(id: String)
}
