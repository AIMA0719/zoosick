package com.myinfocar.aicoachstock.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myinfocar.aicoachstock.data.remote.kis.stockmaster.StockMasterSyncService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 한투 종목 마스터 주기 동기화 워커.
 *
 *  - 주기: 7일(매주). 종목 마스터는 자주 안 바뀜.
 *  - 제약: UNMETERED(Wi-Fi). 합산 ~5MB라 모바일 데이터도 OK지만 안전하게 Wi-Fi 우선.
 *  - 실패: 지수 백오프 30분~. mst 다운로드 실패는 일시적인 경우가 많음.
 *  - 최초 1회: enqueueOnceIfNeeded()로 OneTime trigger (앱 첫 실행 시 검색 빈 결과 방지).
 */
@HiltWorker
class StockMasterSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncService: StockMasterSyncService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            syncService.syncAll().fold(
                onSuccess = { summary ->
                    Timber.i("StockMasterSyncWorker OK total=${summary.total}")
                    Result.success()
                },
                onFailure = { e ->
                    Timber.w(e, "StockMasterSyncWorker 실패 — 재시도")
                    Result.retry()
                },
            )
        } catch (t: Throwable) {
            Timber.w(t, "StockMasterSyncWorker 예외 — 재시도")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "stock_master_sync_weekly"
        const val UNIQUE_ONE_TIME = "stock_master_sync_initial"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<StockMasterSyncWorker>(
                7L, TimeUnit.DAYS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** 최초 1회 즉시 트리거 — 마스터 비어있을 때 검색이 빈 결과 안 되도록. */
        fun enqueueOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // 첫 동기화는 모바일도 허용
                .build()
            val request = OneTimeWorkRequestBuilder<StockMasterSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
            wm.cancelUniqueWork(UNIQUE_ONE_TIME)
        }
    }
}
