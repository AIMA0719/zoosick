package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.alert.PriceAlertDao
import com.myinfocar.aicoachstock.data.local.db.alert.toDomain
import com.myinfocar.aicoachstock.data.local.db.alert.toEntity
import com.myinfocar.aicoachstock.domain.model.PriceAlert
import com.myinfocar.aicoachstock.domain.model.PriceAlertStatus
import com.myinfocar.aicoachstock.domain.repository.PriceAlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceAlertRepositoryImpl @Inject constructor(
    private val dao: PriceAlertDao,
) : PriceAlertRepository {

    override fun observeAll(): Flow<List<PriceAlert>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findActive(): List<PriceAlert> =
        dao.findByStatus(PriceAlertStatus.ACTIVE.name).map { it.toDomain() }

    override suspend fun findById(id: String): PriceAlert? =
        dao.findById(id)?.toDomain()

    override suspend fun save(alert: PriceAlert) {
        dao.upsert(alert.toEntity())
    }

    override suspend fun updateStatus(id: String, status: PriceAlertStatus, triggeredAt: Instant?) {
        dao.updateStatus(id, status.name, triggeredAt?.toEpochMilli())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun markTriggeredIfActive(id: String, triggeredAt: Instant): Boolean {
        val affected = dao.compareAndSetStatus(
            id = id,
            fromStatus = PriceAlertStatus.ACTIVE.name,
            toStatus = PriceAlertStatus.TRIGGERED.name,
            triggeredAt = triggeredAt.toEpochMilli(),
        )
        return affected > 0
    }
}
