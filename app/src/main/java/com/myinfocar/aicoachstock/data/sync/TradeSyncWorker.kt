package com.myinfocar.aicoachstock.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myinfocar.aicoachstock.domain.sync.TradeImportService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 매일 16:00 KST(장 마감 후 30분) 한투 체결 내역 자동 import.
 *
 *  - NetworkType.CONNECTED 제약 (Wi-Fi/모바일 모두 허용 — 텍스트 응답 ~수십KB)
 *  - 실패 시 LINEAR 백오프 15분
 *  - PeriodicWork 24h 주기, initialDelay로 다음 16:00 KST에 첫 실행 맞춤
 *  - PROD 환경 + 계좌번호 미설정 시 즉시 success (스킵)
 */
@HiltWorker
class TradeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importService: TradeImportService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = importService.importRecent()
            result.fold(
                onSuccess = { summary ->
                    Timber.i("TradeSyncWorker OK inserted=${summary.inserted}")
                    Result.success()
                },
                onFailure = { e ->
                    val msg = e.message.orEmpty()
                    // 계좌/키 미설정은 사용자 액션 대기 — 재시도 의미 X. 그냥 success.
                    if (msg.contains("API 키 미설정") || msg.contains("계좌번호 미설정")) {
                        Timber.i("TradeSyncWorker skip: $msg")
                        Result.success()
                    } else {
                        Timber.w(e, "TradeSyncWorker 실패 — 재시도 예약")
                        Result.retry()
                    }
                },
            )
        } catch (t: Throwable) {
            Timber.w(t, "TradeSyncWorker 예외 — 재시도")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "trade_sync_daily"

        /**
         * 매일 16:00 KST 자동 import. KEEP 정책 — 이미 스케줄 있으면 유지.
         * Application.onCreate에서 매번 호출해도 안전.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val initialDelay = computeInitialDelayUntil1600Kst()
            val request = PeriodicWorkRequestBuilder<TradeSyncWorker>(
                24L, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15L, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val TARGET_TIME: LocalTime = LocalTime.of(16, 0)

        private fun computeInitialDelayUntil1600Kst(): Duration {
            val now = LocalDateTime.now(KST)
            var target = LocalDateTime.of(now.toLocalDate(), TARGET_TIME)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target)
        }
    }
}
