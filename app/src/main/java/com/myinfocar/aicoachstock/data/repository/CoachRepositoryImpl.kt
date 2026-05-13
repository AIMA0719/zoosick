package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.data.local.db.coach.CoachMessageDao
import com.myinfocar.aicoachstock.data.local.db.coach.CoachSessionDao
import com.myinfocar.aicoachstock.data.local.db.coach.toDomain
import com.myinfocar.aicoachstock.data.local.db.coach.toEntity
import com.myinfocar.aicoachstock.domain.model.CoachMessage
import com.myinfocar.aicoachstock.domain.model.CoachSession
import com.myinfocar.aicoachstock.domain.repository.CoachRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepositoryImpl @Inject constructor(
    private val sessionDao: CoachSessionDao,
    private val messageDao: CoachMessageDao,
) : CoachRepository {

    override fun observeSessions(): Flow<List<CoachSession>> =
        sessionDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun findSession(id: String): CoachSession? =
        sessionDao.findById(id)?.toDomain()

    override suspend fun createSession(title: String, topicTicker: String?): CoachSession {
        val now = Instant.now()
        val session = CoachSession(
            id = UUID.randomUUID().toString(),
            title = title,
            topicTicker = topicTicker,
            startedAt = now,
            lastMessageAt = now,
        )
        sessionDao.upsert(session.toEntity())
        return session
    }

    override suspend fun renameSession(id: String, title: String) {
        sessionDao.rename(id, title)
    }

    override suspend fun deleteSession(id: String) {
        sessionDao.delete(id)
    }

    override suspend fun touch(id: String) {
        sessionDao.touch(id, Instant.now().toEpochMilli())
    }

    override fun observeMessages(sessionId: String): Flow<List<CoachMessage>> =
        messageDao.observeBySession(sessionId).map { it.map { e -> e.toDomain() } }

    override suspend fun findMessages(sessionId: String): List<CoachMessage> =
        messageDao.findBySession(sessionId).map { it.toDomain() }

    override suspend fun appendMessage(message: CoachMessage) {
        messageDao.insert(message.toEntity())
        sessionDao.touch(message.sessionId, message.createdAt.toEpochMilli())
    }
}
