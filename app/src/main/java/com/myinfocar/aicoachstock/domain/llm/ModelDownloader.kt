package com.myinfocar.aicoachstock.domain.llm

import kotlinx.coroutines.flow.Flow

/**
 * Gemma .task 파일(~3GB) 다운로드 추상화.
 *
 * - Wi-Fi 기본, 명시 동의 시 셀룰러 (PRD 절대 하지 마: 셀룰러 기본 허용 금지)
 * - HTTP Range 기반 재개 (3GB 재다운로드 부담 회피)
 * - SHA-256 무결성 검증 (PRD 절대 하지 마: 검증 없이 로드 금지)
 * - filesDir 또는 noBackupFilesDir에만 저장 (외부 저장소 금지)
 */
interface ModelDownloader {
    /** 모델 파일이 앱 내부 저장소에 존재하고 SHA-256이 맞으면 true. */
    suspend fun isModelReady(): Boolean

    /** 다운로드 시작/재개. 진행률·상태 Flow. */
    fun download(): Flow<DownloadEvent>

    /** SHA-256 검증. */
    suspend fun verify(): Result<Unit>

    /** 재다운로드/롤백을 위한 파일 삭제. */
    suspend fun delete(): Result<Unit>
}

sealed interface DownloadEvent {
    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : DownloadEvent

    data object Paused : DownloadEvent
    data object Verifying : DownloadEvent
    data object Completed : DownloadEvent

    data class Failed(
        val reason: Reason,
        val cause: Throwable?,
    ) : DownloadEvent

    enum class Reason {
        NO_NETWORK,
        METERED_NETWORK_BLOCKED,
        DISK_FULL,
        CHECKSUM_MISMATCH,
        HTTP_ERROR,
        UNKNOWN,
    }
}
