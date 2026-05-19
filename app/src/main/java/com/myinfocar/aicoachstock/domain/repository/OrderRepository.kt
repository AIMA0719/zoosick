package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderStatus
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeAll(): Flow<List<Order>>
    fun observeByTicker(ticker: String): Flow<List<Order>>

    /** SUBMITTED / PARTIAL — 진행 중인 주문. polling 대상. */
    suspend fun findOpen(): List<Order>

    suspend fun findById(id: String): Order?
    suspend fun findByKrxOrderNo(odno: String): Order?

    suspend fun upsert(order: Order)
    suspend fun updateStatus(id: String, status: OrderStatus, errorMessage: String? = null)
    suspend fun deleteById(id: String)
}
