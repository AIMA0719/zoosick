package com.myinfocar.aicoachstock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myinfocar.aicoachstock.data.sync.StockMasterSyncWorker
import com.myinfocar.aicoachstock.data.sync.TradeSyncWorker
import com.myinfocar.aicoachstock.domain.market.MarketHours
import com.myinfocar.aicoachstock.domain.repository.StockRepository
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
    @Inject lateinit var stockRepository: StockRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        TradeSyncWorker.enqueue(this)
        StockMasterSyncWorker.enqueuePeriodic(this)
        appScope.launch {
            val dates = stockInfoService.fetchKrHolidayDates()
            if (dates.isNotEmpty()) {
                MarketHours.setKrHolidayDates(dates)
                Timber.i("Loaded ${dates.size} KR holiday dates")
            }
        }
        appScope.launch {
            // 마스터 비어있으면 첫 동기화 즉시 트리거 — 검색이 빈 결과 안 되도록.
            if (stockRepository.masterCount() == 0) {
                StockMasterSyncWorker.enqueueOnce(this@AICoachApplication)
                Timber.i("종목 마스터 비어있음 → 즉시 동기화 트리거")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
