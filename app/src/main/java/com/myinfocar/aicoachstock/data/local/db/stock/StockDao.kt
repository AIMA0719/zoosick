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

    /**
     * 자연어 + 코드 통합 검색. nameKo / nameEn / ticker 부분 매칭.
     * 정렬 우선순위: 정확 일치 > 접두 일치(ticker) > 접두 일치(이름) > 부분 일치.
     * Phase 1 종목 마스터(~12,000건)에 대해 LIKE만으로 충분히 빠름 (인덱스 X — Phase 2 검토).
     */
    @Query(
        "SELECT * FROM stocks " +
            "WHERE nameKo LIKE '%' || :query || '%' " +
            "   OR nameEn LIKE '%' || :query || '%' " +
            "   OR ticker LIKE '%' || :query || '%' " +
            "ORDER BY " +
            "   CASE " +
            "     WHEN ticker = :query THEN 0 " +
            "     WHEN ticker LIKE :query || '%' THEN 1 " +
            "     WHEN nameKo LIKE :query || '%' THEN 2 " +
            "     WHEN nameEn LIKE :query || '%' THEN 3 " +
            "     ELSE 4 " +
            "   END ASC, " +
            "   ticker ASC " +
            "LIMIT 50",
    )
    suspend fun searchByText(query: String): List<StockEntity>

    @Query("SELECT COUNT(*) FROM stocks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StockEntity>)

    @Query("DELETE FROM stocks WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)
}
