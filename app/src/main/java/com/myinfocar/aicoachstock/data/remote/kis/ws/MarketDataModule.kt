package com.myinfocar.aicoachstock.data.remote.kis.ws

import com.myinfocar.aicoachstock.domain.market.MarketDataStream
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketDataBindingModule {

    @Binds
    @Singleton
    abstract fun bindMarketDataStream(impl: KisWebSocketStream): MarketDataStream
}
