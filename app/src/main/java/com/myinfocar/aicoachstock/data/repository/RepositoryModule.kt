package com.myinfocar.aicoachstock.data.repository

import com.myinfocar.aicoachstock.domain.repository.CoachRepository
import com.myinfocar.aicoachstock.domain.repository.EntryChecklistRepository
import com.myinfocar.aicoachstock.domain.repository.PriceAlertRepository
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.TradeReflectionRepository
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTradingPrincipleRepository(
        impl: TradingPrincipleRepositoryImpl,
    ): TradingPrincipleRepository

    @Binds
    @Singleton
    abstract fun bindTradeRepository(
        impl: TradeRepositoryImpl,
    ): TradeRepository

    @Binds
    @Singleton
    abstract fun bindStockRepository(
        impl: StockRepositoryImpl,
    ): StockRepository

    @Binds
    @Singleton
    abstract fun bindWatchListRepository(
        impl: WatchListRepositoryImpl,
    ): WatchListRepository

    @Binds
    @Singleton
    abstract fun bindTradeReflectionRepository(
        impl: TradeReflectionRepositoryImpl,
    ): TradeReflectionRepository

    @Binds
    @Singleton
    abstract fun bindCoachRepository(
        impl: CoachRepositoryImpl,
    ): CoachRepository

    @Binds
    @Singleton
    abstract fun bindEntryChecklistRepository(
        impl: EntryChecklistRepositoryImpl,
    ): EntryChecklistRepository

    @Binds
    @Singleton
    abstract fun bindPriceAlertRepository(
        impl: PriceAlertRepositoryImpl,
    ): PriceAlertRepository
}
