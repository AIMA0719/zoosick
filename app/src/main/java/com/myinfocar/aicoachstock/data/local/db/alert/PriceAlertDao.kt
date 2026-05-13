package com.myinfocar.aicoachstock.data.local.db.alert

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {

    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE status = :status")
    suspend fun findByStatus(status: String): List<PriceAlertEntity>

    @Query("SELECT * FROM price_alerts WHERE id = :id")
    suspend fun findById(id: String): PriceAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PriceAlertEntity)

    @Query("UPDATE price_alerts SET status = :status, triggeredAt = :triggeredAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, triggeredAt: Long?)

    /**
     * CAS — 현재 status == fromStatus인 경우에만 toStatus로 변경. 반환값 = affected row 수.
     * evaluate가 0을 받으면 이미 다른 곳에서 CANCELED/TRIGGERED/삭제됨 → 알림 발사 X.
     */
    @Query(
        "UPDATE price_alerts SET status = :toStatus, triggeredAt = :triggeredAt " +
            "WHERE id = :id AND status = :fromStatus"
    )
    suspend fun compareAndSetStatus(
        id: String,
        fromStatus: String,
        toStatus: String,
        triggeredAt: Long?,
    ): Int

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun delete(id: String)
}
