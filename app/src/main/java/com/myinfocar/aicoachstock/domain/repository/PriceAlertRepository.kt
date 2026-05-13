package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.PriceAlert
import com.myinfocar.aicoachstock.domain.model.PriceAlertStatus
import kotlinx.coroutines.flow.Flow

interface PriceAlertRepository {
    fun observeAll(): Flow<List<PriceAlert>>
    suspend fun findActive(): List<PriceAlert>
    suspend fun findById(id: String): PriceAlert?
    suspend fun save(alert: PriceAlert)
    suspend fun updateStatus(id: String, status: PriceAlertStatus, triggeredAt: java.time.Instant? = null)
    suspend fun delete(id: String)

    /**
     * Atomic: ACTIVE → TRIGGERED 전환에 성공한 경우만 true. 호출자(evaluate)는 true일 때만 notify.
     * 동시에 cancel/delete가 일어나도 알림 중복/유령 알림 방지.
     */
    suspend fun markTriggeredIfActive(id: String, triggeredAt: java.time.Instant): Boolean
}
