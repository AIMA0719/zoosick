package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.order.OrderDao
import com.myinfocar.aicoachstock.data.local.db.order.toDomain
import com.myinfocar.aicoachstock.data.local.db.order.toEntity
import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderStatus
import com.myinfocar.aicoachstock.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val dao: OrderDao,
) : OrderRepository {

    override fun observeAll(): Flow<List<Order>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeByTicker(ticker: String): Flow<List<Order>> =
        dao.observeByTicker(ticker).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findOpen(): List<Order> = dao.findByStatuses(
        listOf(OrderStatus.SUBMITTED.name, OrderStatus.PARTIAL.name)
    ).map { it.toDomain() }

    override suspend fun findById(id: String): Order? = dao.findById(id)?.toDomain()

    override suspend fun findByKrxOrderNo(odno: String): Order? =
        dao.findByKrxOrderNo(odno)?.toDomain()

    override suspend fun upsert(order: Order) {
        dao.upsert(order.toEntity())
    }

    override suspend fun updateStatus(id: String, status: OrderStatus, errorMessage: String?) {
        val existing = dao.findById(id) ?: return
        val completedAt = when (status) {
            OrderStatus.FILLED, OrderStatus.CANCELED, OrderStatus.REJECTED -> Instant.now().toEpochMilli()
            else -> existing.completedAt
        }
        dao.upsert(
            existing.copy(
                status = status.name,
                errorMessage = errorMessage ?: existing.errorMessage,
                completedAt = completedAt,
            )
        )
    }

    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
