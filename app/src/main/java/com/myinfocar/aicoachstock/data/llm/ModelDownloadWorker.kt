package com.myinfocar.aicoachstock.data.llm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myinfocar.aicoachstock.domain.llm.DownloadEvent
import com.myinfocar.aicoachstock.domain.llm.ModelDownloader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import androidx.work.Data

/**
 * Gemma 모델 ~3GB 다운로드를 WorkManager 백그라운드로 위임.
 *
 *  - NetworkType.UNMETERED 강제 (Wi-Fi 또는 무제한 회선만)
 *  - requiresBatteryNotLow + requiresStorageNotLow
 *  - 앱 종료 후에도 진행 (다른 OS 환경 정책에 따라)
 *  - 진행률은 setProgress로 업데이트 → 호출자가 WorkInfo.observe로 구독
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloader: ModelDownloader,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            var lastReason: String? = null
            downloader.download().collect { ev ->
                when (ev) {
                    is DownloadEvent.Progress -> setProgress(
                        Data.Builder()
                            .putLong(KEY_DOWNLOADED, ev.downloadedBytes)
                            .putLong(KEY_TOTAL, ev.totalBytes)
                            .build()
                    )
                    DownloadEvent.Paused -> { /* worker 자체는 일시정지 없음 */ }
                    DownloadEvent.Verifying -> { /* 진행률 100% 상태로 진입 */ }
                    DownloadEvent.Completed -> { /* loop 종료 후 success */ }
                    is DownloadEvent.Failed -> {
                        Timber.w(ev.cause, "ModelDownloadWorker 실패 reason=${ev.reason}")
                        lastReason = ev.reason.name
                    }
                }
            }
            if (lastReason != null) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (t: Throwable) {
            Timber.w(t, "ModelDownloadWorker 예외")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "gemma_model_download"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"

        /**
         * Wi-Fi 강제 + 배터리/저장공간 여유 시에만 실행. 이미 다운로드 중이면 KEEP.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi 강제 (PRD 절대 하지 마)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
