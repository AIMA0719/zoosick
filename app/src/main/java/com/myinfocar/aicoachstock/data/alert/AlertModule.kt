package com.myinfocar.aicoachstock.data.alert

import com.myinfocar.aicoachstock.domain.alert.AlertScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertBindingModule {

    @Binds
    @Singleton
    abstract fun bindAlertScheduler(impl: AlertSchedulerImpl): AlertScheduler
}
