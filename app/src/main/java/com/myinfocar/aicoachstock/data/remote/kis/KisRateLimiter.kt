package com.myinfocar.aicoachstock.data.remote.kis

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 OpenAPI 호출 빈도 제한 공유 limiter — **실전(PROD) 전용**.
 *
 *  - 한투 실전 일반 조회 ~20회/초 한도이지만, 무거운 API(잔고/체결/재무)는 1초 3~5회로 빡빡.
 *  - 홈 대시보드가 한 번에 잔고 + 관심종목 N개 가격 + 배당 + 예수금을 연속 호출하므로
 *    보수적으로 250ms gap (1초 4회).
 *  - REST 호출 직전 await()로 토큰 획득. rate limit 응답 시 callWithRetry로 자동 재시도.
 */
@Singleton
class KisRateLimiter @Inject constructor() {

    private val mutex = Mutex()
    private var lastCallAt: Long = 0L

    /**
     * @param minGapMs 호출 직전 최소 대기 간격(ms). 기본 [GAP_MS]. 무거운 엔드포인트
     *   (overseas-ccnl 등 KIS가 초당 1회로 빡빡하게 막는 API)는 더 크게 지정.
     */
    suspend fun await(minGapMs: Long = GAP_MS) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallAt
            if (elapsed < minGapMs) {
                delay(minGapMs - elapsed)
            }
            lastCallAt = System.currentTimeMillis()
        }
    }

    /** rate limit 응답 받으면 호출자가 백오프 후 재시도. attempt는 0부터. */
    suspend fun backoff(attempt: Int) {
        val ms = (BACKOFF_BASE_MS * (1L shl attempt.coerceAtMost(3))).coerceAtMost(BACKOFF_MAX_MS)
        delay(ms)
    }

    /**
     * REST 호출을 limiter 통과시키고, rate-limit 에러(HttpException 또는 rt_cd 코드)면
     * 백오프 후 자동 재시도. 다른 에러는 throw.
     *
     * 호출자는 응답을 받은 후 별도로 rt_cd 체크 + isRateLimitMessage()로 fail 처리해도 됨.
     */
    suspend inline fun <T> callWithRetry(
        label: String = "kis",
        minGapMs: Long = GAP_MS,
        crossinline block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            await(minGapMs)
            try {
                return block()
            } catch (e: HttpException) {
                val bodyText = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val rateLimited = isRateLimitMessage(null, bodyText) || e.code() == 429 || e.code() == 500
                if (rateLimited && attempt < MAX_RETRIES) {
                    Timber.w("KIS rate limit ($label, code=${e.code()}) — backoff attempt=$attempt")
                    backoff(attempt)
                    attempt++
                    continue
                }
                throw e
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    companion object {
        const val GAP_MS = 250L
        const val BACKOFF_BASE_MS = 1_500L
        const val BACKOFF_MAX_MS = 8_000L
        const val MAX_RETRIES = 3

        fun isRateLimitMessage(msgCd: String?, msg: String?): Boolean {
            val text = "${msgCd.orEmpty()} ${msg.orEmpty()}".lowercase()
            return text.contains("초당") ||
                text.contains("초과") ||
                text.contains("egw00201") ||
                text.contains("rate") ||
                text.contains("too many")
        }
    }
}
