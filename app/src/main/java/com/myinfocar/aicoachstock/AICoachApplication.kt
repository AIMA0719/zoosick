package com.myinfocar.aicoachstock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myinfocar.aicoachstock.data.sync.TradeSyncWorker
import com.myinfocar.aicoachstock.domain.market.MarketHours
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AICoachApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var stockInfoService: StockInfoService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        TradeSyncWorker.enqueue(this)
        appScope.launch {
            val dates = stockInfoService.fetchKrHolidayDates()
            if (dates.isNotEmpty()) {
                MarketHours.setKrHolidayDates(dates)
                Timber.i("Loaded ${dates.size} KR holiday dates")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
