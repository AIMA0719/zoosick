package com.myinfocar.aicoachstock.data.llm

import android.content.Context
import com.myinfocar.aicoachstock.domain.llm.DownloadEvent
import com.myinfocar.aicoachstock.domain.llm.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * .litertlm 모델 파일을 OkHttp Range 다운로드로 받는다.
 *
 * - filesDir/models/ 아래에 .part로 누적, 완료 시 rename + SHA-256 기록.
 * - 부분 파일이 남아 있으면 Range: bytes=<resumeFrom>- 로 재개.
 * - SHA-256은 다운로드 완료 후 산출·저장 (중도 정지 시 누적 digest 깨짐 방지).
 *   이후 verify()로 매번 재계산해 무결성 보장.
 * - Wi-Fi 강제/미터드 차단은 호출자(WorkManager 제약·UX)가 책임.
 *
 * PRD 절대 하지 마: SHA-256 검증 없이 .task 로드 금지 → LiteRtLmLLMEngine.load() 전에
 * verify() 통과를 확인해야 한다.
 */
@Singleton
class HttpRangeModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val modelLocation: ModelLocation,
    private val modelSource: ModelSource,
) : ModelDownloader {

    // 대용량 다운로드용: 청크간 60초만 보고 전체 시간 제한 없음.
    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val targetFile: File get() = modelLocation.file(context)
    private val partFile: File get() = File(targetFile.parentFile, "${targetFile.name}.part")
    private val sha256File: File get() = File(targetFile.parentFile, "${targetFile.name}.sha256")

    override suspend fun isModelReady(): Boolean = withContext(Dispatchers.IO) {
        targetFile.exists() && verify().isSuccess
    }

    override fun download(): Flow<DownloadEvent> = flow {
        targetFile.parentFile?.mkdirs()

        // verify()가 파일 부재까지 실패로 잡으므로 외부 존재 체크는 생략 (TOCTOU 회피).
        emit(DownloadEvent.Verifying)
        if (verify().isSuccess) {
            emit(DownloadEvent.Completed)
            return@flow
        }
        // 검증 실패한 잔재는 지우고 처음부터.
        targetFile.delete()
        sha256File.delete()

        var resumeFrom = if (partFile.exists()) partFile.length() else 0L
        val initialResume = resumeFrom

        // primary + mirrors 순차 시도. 한 호스트 실패 시 다음 호스트로.
        var response: okhttp3.Response? = null
        var lastError: Throwable? = null
        for (candidate in modelSource.all()) {
            val request = Request.Builder()
                .url(candidate)
                .apply { if (initialResume > 0L) header("Range", "bytes=$initialResume-") }
                .build()
            try {
                val r = httpClient.newCall(request).execute()
                if (r.isSuccessful) {
                    // Range를 요청했으나 서버가 무시하고 200(full body) 반환 시,
                    // resumeFrom에 seek해서 append하면 파일이 손상됨. resumeFrom=0으로 강제.
                    if (initialResume > 0L && r.code != 206) {
                        Timber.w("mirror=${candidate}가 Range를 무시함(code=${r.code}). 처음부터 재다운로드.")
                        resumeFrom = 0L
                        partFile.delete()
                    }
                    response = r
                    break
                } else {
                    Timber.w("모델 다운로드 후보 실패 url=$candidate code=${r.code}")
                    lastError = IllegalStateException("HTTP ${r.code} from $candidate")
                    r.close()
                }
            } catch (t: Throwable) {
                Timber.w(t, "모델 다운로드 후보 예외 url=$candidate")
                lastError = t
            }
        }

        if (response == null) {
            emit(
                DownloadEvent.Failed(
                    DownloadEvent.Reason.HTTP_ERROR,
                    lastError ?: IllegalStateException("모든 미러 실패"),
                )
            )
            return@flow
        }

        val body = response.body
        if (body == null) {
            response.close()
            emit(
                DownloadEvent.Failed(
                    DownloadEvent.Reason.HTTP_ERROR,
                    IllegalStateException("응답 본문 없음"),
                )
            )
            return@flow
        }

        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0L) contentLength + resumeFrom else -1L
        var downloaded = resumeFrom

        try {
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.seek(resumeFrom)
                body.byteStream().use { input ->
                    val buf = ByteArray(BUFFER_BYTES)
                    while (coroutineContext.isActive) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        raf.write(buf, 0, read)
                        downloaded += read
                        emit(DownloadEvent.Progress(downloaded, totalBytes))
                    }
                }
            }
            response.close()

            emit(DownloadEvent.Verifying)
            val sha = computeSha256(partFile)
            if (!partFile.renameTo(targetFile)) {
                emit(
                    DownloadEvent.Failed(
                        DownloadEvent.Reason.UNKNOWN,
                        IllegalStateException("rename 실패: $partFile → $targetFile"),
                    )
                )
                return@flow
            }
            sha256File.writeText(sha)
            Timber.i("모델 다운로드 완료 size=$downloaded sha=$sha")
            emit(DownloadEvent.Completed)
        } catch (t: Throwable) {
            response.close()
            emit(DownloadEvent.Failed(DownloadEvent.Reason.UNKNOWN, t))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun verify(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(targetFile.exists()) { "모델 파일 없음" }
            require(sha256File.exists()) { "SHA-256 기록 없음" }
            val expected = sha256File.readText().trim()
            val actual = computeSha256(targetFile)
            require(actual.equals(expected, ignoreCase = true)) {
                "SHA-256 불일치 (expected $expected, actual $actual)"
            }
        }
    }

    override suspend fun delete(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            targetFile.delete()
            partFile.delete()
            sha256File.delete()
            Unit
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}

/** 앱 내부 저장소 안의 .litertlm 파일 위치. */
data class ModelLocation(val fileName: String) {
    fun file(context: Context): File = File(context.filesDir, "models/$fileName")
}

/**
 * 다운로드 원본 URL + 미러 후보.
 *
 * primary 실패(네트워크/HTTP 4xx-5xx) 시 mirrors를 순차 시도. 한 호스트가 차단돼도 다른 호스트로 복구.
 */
data class ModelSource(
    val url: String,
    val mirrors: List<String> = emptyList(),
) {
    fun all(): List<String> = listOf(url) + mirrors
}
