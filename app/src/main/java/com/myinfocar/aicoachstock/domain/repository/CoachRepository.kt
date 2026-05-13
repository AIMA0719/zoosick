package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.CoachMessage
import com.myinfocar.aicoachstock.domain.model.CoachSession
import kotlinx.coroutines.flow.Flow

interface CoachRepository {
    fun observeSessions(): Flow<List<CoachSession>>
    suspend fun findSession(id: String): CoachSession?
    suspend fun createSession(title: String, topicTicker: String?): CoachSession
    suspend fun renameSession(id: String, title: String)
    suspend fun deleteSession(id: String)
    suspend fun touch(id: String)

    fun observeMessages(sessionId: String): Flow<List<CoachMessage>>
    suspend fun findMessages(sessionId: String): List<CoachMessage>
    suspend fun appendMessage(message: CoachMessage)
}
