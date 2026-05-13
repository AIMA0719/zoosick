package com.myinfocar.aicoachstock.data.remote.kis.market

import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MarketDataApiModule {

    @Provides
    @Singleton
    fun provideKisMarketRestApi(retrofit: Retrofit): KisMarketRestApi =
        retrofit.create(KisMarketRestApi::class.java)

    @Provides
    @Singleton
    fun provideKisTradingApi(retrofit: Retrofit): KisTradingApi =
        retrofit.create(KisTradingApi::class.java)

    @Provides
    @Singleton
    fun provideKisStockInfoApi(retrofit: Retrofit): KisStockInfoApi =
        retrofit.create(KisStockInfoApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketDataSourceBindingModule {

    @Binds
    @Singleton
    abstract fun bindMarketDataSource(impl: KisMarketDataSource): MarketDataSource
}
