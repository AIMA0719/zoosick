package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.model.WatchListItem
import kotlinx.coroutines.flow.Flow

interface WatchListRepository {
    fun observe(): Flow<List<WatchListEntry>>
    suspend fun findById(id: String): WatchListItem?

    /** ticker로 항목 추가. defaultStock이 있으면 stocks 테이블에 함께 upsert. id 반환. */
    suspend fun add(ticker: String, note: String?, defaultStock: Stock?): String

    suspend fun updateNote(id: String, note: String?)
    suspend fun remove(id: String)
}

/** WatchListItem + 메타(Stock)를 함께 가지는 화면용 모델. stock은 미입력일 수 있음. */
data class WatchListEntry(
    val item: WatchListItem,
    val stock: Stock?,
)
