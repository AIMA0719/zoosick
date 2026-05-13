package com.myinfocar.aicoachstock.domain.sync

import com.myinfocar.aicoachstock.data.remote.kis.KisRateLimiter
import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyCcldItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyCcldResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasCcnlItem
import com.myinfocar.aicoachstock.data.remote.kis.market.KisTradingApi
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.auth.KisEnv
import com.myinfocar.aicoachstock.domain.model.EmotionTag
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import retrofit2.HttpException
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 OpenAPI에서 내 계좌의 일별 체결 내역을 가져와 Trade로 import.
 *
 *  - 미체결(tot_ccld_qty == 0) / 취소(cncl_yn == "Y") 제외.
 *  - externalOrderNo(odno)로 중복 방지 — saveIfAbsent.
 *  - 페이징: tr_cont = "N"(다음 페이지) / 마지막 페이지면 ctx_area_nk100 비어있음.
 *  - 호출은 KST 기준 날짜(YYYYMMDD)로 지정. 기본 최근 7일.
 *
 *  PRD 04 준수:
 *   - 토큰은 authService.ensureAccessToken으로 자동 보장
 *   - 응답 errorCode 그대로 노출 (디버깅 용이성)
 */
@Singleton
class TradeImportService @Inject constructor(
    private val tradingApi: KisTradingApi,
    private val authService: KisAuthService,
    private val store: ApiCredentialStore,
    private val tradeRepo: TradeRepository,
    private val stockRepo: StockRepository,
    private val rateLimiter: KisRateLimiter,
) {

    suspend fun importRecent(daysBack: Int = DEFAULT_DAYS_BACK): Result<ImportSummary> = runCatching {
        val creds = store.current() ?: throw IllegalStateException("API 키 미설정")
        val cano = creds.accountNo ?: throw IllegalStateException("계좌번호 미설정")
        val prdt = creds.productCode ?: throw IllegalStateException("계좌 상품코드 미설정")
        require(cano.length == 8 && cano.all { it.isDigit() }) {
            "CANO는 숫자 8자리여야 합니다 (현재 ${cano.length}자리)"
        }
        val token = authService.ensureAccessToken().getOrElse { throw it }

        val today = LocalDate.now(KST)
        val start = today.minusDays(daysBack.toLong())
        val startStr = start.format(DATE_FMT)
        val endStr = today.format(DATE_FMT)
        val trId = TR_ID_PROD

        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/inquire-daily-ccld"
        val auth = "Bearer $token"

        val collected = mutableListOf<DailyCcldItem>()
        var ctxFk = ""
        var ctxNk = ""
        var trCont = ""

        for (page in 0 until MAX_PAGES) {
            val resp: DailyCcldResponse = callWithRateLimit(env = creds.env, label = "inquire-daily-ccld") {
                tradingApi.fetchDailyCcld(
                    url = url,
                    authorization = auth,
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    trId = trId,
                    trCont = trCont,
                    accountNo = cano,
                    productCode = prdt,
                    startDate = startStr,
                    endDate = endStr,
                    ctxFk100 = ctxFk,
                    ctxNk100 = ctxNk,
                )
            }
            if (resp.rtCd != "0") {
                if (KisRateLimiter.isRateLimitMessage(resp.msgCd, resp.msg1)) {
                    // rate limit인데 응답 코드로 떨어진 경우 — 외부 호출자가 다시 시도하도록 명확한 메시지.
                    throw RuntimeException(
                        "한투 호출 빈도 제한 (msg=${resp.msg1.orEmpty()}). 몇 초 후 다시 시도하세요.",
                    )
                }
                throw RuntimeException(
                    "한투 rt_cd=${resp.rtCd} msg_cd=${resp.msgCd.orEmpty()} msg=${resp.msg1.orEmpty()}",
                )
            }
            collected += resp.output1
            val nextNk = resp.ctxAreaNk100?.trim().orEmpty()
            if (nextNk.isEmpty()) break
            ctxFk = resp.ctxAreaFk100?.trim().orEmpty()
            ctxNk = nextNk
            trCont = "N"
        }

        var inserted = 0
        var skipped = 0
        var ignored = 0
        for (item in collected) {
            val trade = mapToTrade(item)
            if (trade == null) {
                ignored++
                continue
            }
            // 종목 메타도 함께 upsert (이름이 있으면).
            item.prdtName?.takeIf { it.isNotBlank() }?.let { name ->
                runCatching {
                    val existing = stockRepo.findByTicker(trade.ticker)
                    if (existing == null) {
                        stockRepo.save(
                            com.myinfocar.aicoachstock.domain.model.Stock(
                                ticker = trade.ticker,
                                nameKo = name,
                                nameEn = null,
                                exchange = guessKrExchange(trade.ticker),
                                sector = null,
                                currency = com.myinfocar.aicoachstock.domain.model.Currency.KRW,
                            )
                        )
                    }
                }
            }
            if (tradeRepo.saveIfAbsent(trade)) inserted++ else skipped++
        }

        Timber.i("TradeImport KR: range=$startStr..$endStr collected=${collected.size} inserted=$inserted skipped=$skipped ignored=$ignored")

        // 해외 체결도 함께 시도 (실패해도 KR 결과는 유지).
        val overseas = runCatching { importOverseasRecent(creds.env, creds.appKey, creds.appSecret, cano, prdt, token, startStr, endStr) }
        val overseasInserted = overseas.getOrElse {
            Timber.w(it, "해외 체결 import 실패 (KR은 정상)")
            0
        }

        ImportSummary(
            range = startStr to endStr,
            collected = collected.size,
            inserted = inserted + overseasInserted,
            skippedDuplicate = skipped,
            ignored = ignored,
        )
    }

    /** 해외 주식 체결 import. 실패 시 0 반환은 호출자 책임. */
    private suspend fun importOverseasRecent(
        env: KisEnv,
        appKey: String,
        appSecret: String,
        cano: String,
        prdt: String,
        token: String,
        startStr: String,
        endStr: String,
    ): Int {
        val trId = "TTTS3035R"
        val url = env.restBaseUrl + "/uapi/overseas-stock/v1/trading/inquire-ccnl"
        val auth = "Bearer $token"

        val collected = mutableListOf<OverseasCcnlItem>()
        var ctxFk = ""
        var ctxNk = ""
        var trCont = ""
        for (page in 0 until MAX_PAGES) {
            val resp = callWithRateLimit(
                env = env,
                label = "overseas-ccnl",
                minGapMs = OVERSEAS_GAP_MS,
            ) {
                tradingApi.fetchOverseasCcnl(
                    url = url,
                    authorization = auth,
                    appKey = appKey,
                    appSecret = appSecret,
                    trId = trId,
                    trCont = trCont,
                    accountNo = cano,
                    productCode = prdt,
                    startDate = startStr,
                    endDate = endStr,
                    ctxFk200 = ctxFk,
                    ctxNk200 = ctxNk,
                )
            }
            if (resp.rtCd != "0") {
                if (KisRateLimiter.isRateLimitMessage(resp.msgCd, resp.msg1)) {
                    throw RuntimeException("호출 빈도 제한 (overseas-ccnl): ${resp.msg1.orEmpty()}")
                }
                throw RuntimeException("overseas-ccnl rt_cd=${resp.rtCd} msg=${resp.msg1.orEmpty()}")
            }
            collected += resp.output
            val nextNk = resp.ctxAreaNk200?.trim().orEmpty()
            if (nextNk.isEmpty()) break
            ctxFk = resp.ctxAreaFk200?.trim().orEmpty()
            ctxNk = nextNk
            trCont = "N"
        }
        var inserted = 0
        for (item in collected) {
            val t = mapOverseasToTrade(item) ?: continue
            if (tradeRepo.saveIfAbsent(t)) inserted++
        }
        Timber.i("TradeImport US: collected=${collected.size} inserted=$inserted")
        return inserted
    }

    private fun mapOverseasToTrade(item: OverseasCcnlItem): Trade? {
        val ticker = item.pdno?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val odno = item.odno?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val side = when (item.sllBuyDvsnCd) {
            "01" -> TradeSide.SELL
            "02" -> TradeSide.BUY
            else -> return null
        }
        // 정정/취소 주문은 import에서 제외.
        if (item.rvseCnclDvsn != "00" && item.rvseCnclDvsn != null && item.rvseCnclDvsn.isNotBlank()) {
            // "00" = 일반, 다른 값은 취소/정정.
            if (item.rvseCnclDvsn != "00") return null
        }
        val ccldQty = item.ftCcldQty?.toDoubleOrNull()?.toInt() ?: return null
        if (ccldQty <= 0) return null
        val price = item.ftCcldUnpr?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val executedAt = parseExecutedAt(item.ordDt, item.ordTmd) ?: Instant.now()

        return Trade(
            id = UUID.randomUUID().toString(),
            ticker = ticker,
            market = Market.US,
            side = side,
            qty = ccldQty,
            price = price,
            fee = null,
            executedAt = executedAt,
            reasonText = null,
            emotionTag = EmotionTag.NONE,
            linkedChecklistId = null,
            createdAt = Instant.now(),
            externalOrderNo = odno,
        )
    }

    /** Limiter+retry는 KisRateLimiter에 위임하고 여기선 메시지 wrap만 담당. */
    private suspend inline fun <T> callWithRateLimit(
        env: KisEnv,
        label: String,
        minGapMs: Long = KisRateLimiter.GAP_MS,
        crossinline block: suspend () -> T,
    ): T = try {
        rateLimiter.callWithRetry(label = label, minGapMs = minGapMs) { block() }
    } catch (e: HttpException) {
        val bodyText = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        throw RuntimeException(
            "한투 HTTP ${e.code()} ($label, env=$env) — ${bodyText ?: e.message()}",
            e,
        )
    }

    private fun mapToTrade(item: DailyCcldItem): Trade? {
        if (item.cnclYn == "Y") return null
        val ticker = item.pdno?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val odno = item.odno?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val side = when (item.sllBuyDvsnCd) {
            "01" -> TradeSide.SELL
            "02" -> TradeSide.BUY
            else -> return null
        }
        val ccldQty = item.totCcldQty?.toIntOrNull() ?: return null
        if (ccldQty <= 0) return null
        val price = item.avgPrvs?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val executedAt = parseExecutedAt(item.ordDt, item.ordTmd) ?: Instant.now()

        return Trade(
            id = UUID.randomUUID().toString(),
            ticker = ticker,
            market = Market.KR,
            side = side,
            qty = ccldQty,
            price = price,
            fee = null,
            executedAt = executedAt,
            reasonText = null,
            emotionTag = EmotionTag.NONE,
            linkedChecklistId = null,
            createdAt = Instant.now(),
            externalOrderNo = odno,
        )
    }

    private fun parseExecutedAt(ymd: String?, hms: String?): Instant? {
        if (ymd.isNullOrBlank()) return null
        val date = runCatching { LocalDate.parse(ymd, DATE_FMT) }.getOrNull() ?: return null
        val time = if (hms != null && hms.length >= 4) {
            runCatching {
                val padded = hms.padEnd(6, '0').take(6)
                LocalTime.parse(padded, TIME_FMT)
            }.getOrNull()
        } else null
        val dt = LocalDateTime.of(date, time ?: LocalTime.MIDNIGHT)
        return dt.atZone(KST).toInstant()
    }

    private fun guessKrExchange(ticker: String): com.myinfocar.aicoachstock.domain.model.Exchange =
        // 휴리스틱: 2/3으로 시작하면 KOSDAQ 가능성 높음. 정확 분류는 종목마스터 다운로드 후.
        if (ticker.startsWith("3") || ticker.startsWith("2"))
            com.myinfocar.aicoachstock.domain.model.Exchange.KOSDAQ
        else
            com.myinfocar.aicoachstock.domain.model.Exchange.KOSPI

    data class ImportSummary(
        val range: Pair<String, String>,
        val collected: Int,
        val inserted: Int,
        val skippedDuplicate: Int,
        val ignored: Int,
    )

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
        const val TR_ID_PROD = "TTTC8001R"
        const val DEFAULT_DAYS_BACK = 7
        const val MAX_PAGES = 20

        /**
         * 해외주식 주문체결내역(inquire-ccnl)은 KIS가 초당 1회로 빡빡하게 막아서
         * 250ms 기본 간격으론 500(EGW00201)이 자주 떨어진다. 보수적으로 1000ms.
         */
        const val OVERSEAS_GAP_MS = 1_000L
    }
}
