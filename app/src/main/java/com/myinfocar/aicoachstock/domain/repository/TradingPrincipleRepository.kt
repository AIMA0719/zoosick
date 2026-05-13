package com.myinfocar.aicoachstock.domain.repository

import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import kotlinx.coroutines.flow.Flow

interface TradingPrincipleRepository {
    fun observeAll(): Flow<List<TradingPrinciple>>
    fun observeActive(): Flow<List<TradingPrinciple>>
    suspend fun findById(id: String): TradingPrinciple?
    suspend fun save(principle: TradingPrinciple)
    suspend fun delete(id: String)
    suspend fun count(): Int
}
