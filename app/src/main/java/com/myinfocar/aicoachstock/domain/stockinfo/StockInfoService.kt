package com.myinfocar.aicoachstock.domain.stockinfo

import com.myinfocar.aicoachstock.data.remote.kis.KisRateLimiter
import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar
import com.myinfocar.aicoachstock.data.remote.kis.dto.DividendItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.EstimatePerformResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.FinanceRatioItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.InvestOpinionItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.InvestorDailyItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.MarketFundsItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.MarketInvestorDailyItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasNewsItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.StockInfoOutput
import com.myinfocar.aicoachstock.data.remote.kis.dto.TimeChartBar
import com.myinfocar.aicoachstock.data.remote.kis.market.KisStockInfoApi
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.model.Candle
import com.myinfocar.aicoachstock.domain.model.Timeframe
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 종목 메타 + 재무비율 + 일봉 차트 + 휴장일 조회.
 *
 *  - 종목 리서치 LLM 컨텍스트에 자동 주입할 수 있는 데이터 묶음 제공.
 *  - 휴장일은 in-memory 캐시 (24h TTL).
 */
@Singleton
class StockInfoService @Inject constructor(
    private val api: KisStockInfoApi,
    private val authService: KisAuthService,
    private val store: ApiCredentialStore,
    private val rateLimiter: KisRateLimiter,
) {

    @Volatile
    private var holidayCache: Map<String, Boolean>? = null   // YYYYMMDD -> isHoliday
    @Volatile
    private var holidayCacheStampMs: Long = 0L

    /** 종목 기본정보 (이름/섹터/52w/상장주식수). 실패시 null. */
    suspend fun fetchStockInfo(ticker: String): StockInfoOutput? = runCatching {
        val creds = store.current() ?: return@runCatching null
        val token = authService.ensureAccessToken().getOrElse { return@runCatching null }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/search-stock-info"
        api.searchStockInfo(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "CTPF1604R",
            pdno = ticker,
        ).output
    }.onFailure { Timber.w(it, "fetchStockInfo 실패 $ticker") }.getOrNull()

    /** 재무비율 — 결산년월 내림차순 최근 N개. */
    suspend fun fetchFinanceRatio(ticker: String, limit: Int = 4): List<FinanceRatioItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/finance/financial-ratio"
        val resp = api.financialRatio(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST66430300",
            fidInputIscd = ticker,
        )
        if (resp.rtCd == "0") resp.output.take(limit) else emptyList()
    }.onFailure {
        Timber.w(it, "fetchFinanceRatio 실패 $ticker")
        if (it is HttpException) {
            runCatching { Timber.w("body=${it.response()?.errorBody()?.string()}") }
        }
    }.getOrElse { emptyList() }

    /** 일봉 차트 (최근 daysBack일). */
    suspend fun fetchDailyChart(ticker: String, daysBack: Int = 30): List<DailyChartBar> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val today = LocalDate.now(KST)
        val start = today.minusDays(daysBack.toLong() + 5L) // 휴일 buffer
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
        val resp = api.dailyChart(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST03010100",
            fidInputIscd = ticker,
            fidInputDate1 = start.format(DATE_FMT),
            fidInputDate2 = today.format(DATE_FMT),
        )
        if (resp.rtCd == "0") resp.output2.filter { it.stckClpr != null } else emptyList()
    }.onFailure { Timber.w(it, "fetchDailyChart 실패 $ticker") }.getOrElse { emptyList() }

    /** 호가/예상체결. */
    suspend fun fetchAskingPrice(ticker: String): AskingPriceResponse? = runCatching {
        val creds = store.current() ?: return@runCatching null
        val token = authService.ensureAccessToken().getOrElse { return@runCatching null }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn"
        api.askingPrice(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST01010200",
            fidInputIscd = ticker,
        ).takeIf { it.rtCd == "0" }
    }.onFailure { Timber.w(it, "fetchAskingPrice 실패 $ticker") }.getOrNull()

    /** 투자자별(개인/외국인/기관) 매매동향. */
    suspend fun fetchInvestorTrend(ticker: String, days: Int = 10): List<InvestorDailyItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-investor"
        val resp = api.investorTrend(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST01010900",
            fidInputIscd = ticker,
        )
        if (resp.rtCd == "0") resp.output.take(days) else emptyList()
    }.onFailure { Timber.w(it, "fetchInvestorTrend 실패 $ticker") }.getOrElse { emptyList() }

    /** 해외 뉴스 헤드라인 (전체 또는 특정 종목). 실전 전용. */
    suspend fun fetchOverseasNews(symbol: String? = null, limit: Int = 10): List<OverseasNewsItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/overseas-price/v1/quotations/news-title"
        val resp = api.overseasNews(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "HHPSTH60100C1",
            symb = symbol.orEmpty(),
        )
        if (resp.rtCd == "0") resp.outblock1.take(limit) else emptyList()
    }.onFailure { Timber.w(it, "fetchOverseasNews 실패") }.getOrElse { emptyList() }

    /** 추정실적 (애널리스트 컨센서스). */
    suspend fun fetchEstimatePerform(ticker: String): EstimatePerformResponse? = runCatching {
        val creds = store.current() ?: return@runCatching null
        val token = authService.ensureAccessToken().getOrElse { return@runCatching null }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/estimate-perform"
        api.estimatePerform(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "HHKST668300C0",
            shtCd = ticker,
        ).takeIf { it.rtCd == "0" }
    }.onFailure { Timber.w(it, "fetchEstimatePerform 실패 $ticker") }.getOrNull()

    /** 종목 투자의견 (증권사 목표가). 최근 N건. */
    suspend fun fetchInvestOpinion(ticker: String, daysBack: Int = 90, limit: Int = 5): List<InvestOpinionItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val today = LocalDate.now(KST)
        val from = today.minusDays(daysBack.toLong())
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/invest-opinion"
        val resp = api.investOpinion(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST663300C0",
            fidInputIscd = ticker,
            fidInputDate1 = from.format(DATE_FMT),
            fidInputDate2 = today.format(DATE_FMT),
        )
        if (resp.rtCd == "0") resp.output.take(limit) else emptyList()
    }.onFailure { Timber.w(it, "fetchInvestOpinion 실패 $ticker") }.getOrElse { emptyList() }

    /** 분봉 차트. 최근 30분봉. */
    suspend fun fetchTimeChart(ticker: String): List<TimeChartBar> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val now = java.time.LocalTime.now(KST)
        val hourStr = "%02d%02d00".format(now.hour, now.minute)
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
        val resp = api.timeChart(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST03010200",
            fidInputIscd = ticker,
            fidInputHour = hourStr,
        )
        if (resp.rtCd == "0") resp.output2 else emptyList()
    }.onFailure { Timber.w(it, "fetchTimeChart 실패 $ticker") }.getOrElse { emptyList() }

    /**
     * 타임프레임 통합 캔들 조회 (Stage 15).
     *
     * - Intraday(MIN_1/5/15/60): 한투 timeChart(분봉) 호출. 응답이 1분봉이라 5/15/60분은 집계.
     * - Period(DAY/WEEK/MONTH/YEAR): 한투 dailyChart의 FID_PERIOD_DIV_CODE 분기.
     */
    suspend fun fetchCandles(
        ticker: String,
        timeframe: Timeframe,
        count: Int = 60,
    ): List<Candle> = if (timeframe.isIntraday) {
        fetchIntradayCandles(ticker, timeframe, count)
    } else {
        fetchPeriodCandles(ticker, timeframe, count)
    }

    private suspend fun fetchIntradayCandles(
        ticker: String,
        timeframe: Timeframe,
        count: Int,
    ): List<Candle> = runCatching {
        val minute1 = fetchTimeChart(ticker)
            .mapNotNull { it.toCandle1Min() }
            .sortedBy { it.ts }
        val aggregated = if (timeframe.intradayMinutes == 1) minute1 else aggregateMinutes(minute1, timeframe)
        aggregated.takeLast(count)
    }.onFailure { Timber.w(it, "fetchIntradayCandles 실패 $ticker $timeframe") }.getOrElse { emptyList() }

    private suspend fun fetchPeriodCandles(
        ticker: String,
        timeframe: Timeframe,
        count: Int,
    ): List<Candle> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val period = timeframe.kisPeriodCode ?: "D"
        val today = LocalDate.now(KST)
        val backDays = when (timeframe) {
            Timeframe.DAY -> count + 10L
            Timeframe.WEEK -> count * 7L + 14L
            Timeframe.MONTH -> count * 31L + 31L
            Timeframe.YEAR -> count * 366L + 366L
            else -> count + 10L
        }
        val start = today.minusDays(backDays)
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
        val resp = api.dailyChart(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST03010100",
            fidInputIscd = ticker,
            fidInputDate1 = start.format(DATE_FMT),
            fidInputDate2 = today.format(DATE_FMT),
            fidPeriodDivCode = period,
        )
        if (resp.rtCd != "0") emptyList()
        else resp.output2.mapNotNull { it.toCandle(timeframe) }
            .sortedBy { it.ts }
            .takeLast(count)
    }.onFailure { Timber.w(it, "fetchPeriodCandles 실패 $ticker $timeframe") }.getOrElse { emptyList() }

    /** 1분봉(TimeChartBar) → Candle 매퍼. 실패시 null. */
    private fun com.myinfocar.aicoachstock.data.remote.kis.dto.TimeChartBar.toCandle1Min(): Candle? {
        val date = stckBsopDate?.takeIf { it.length == 8 } ?: return null
        val time = stckCntgHour?.takeIf { it.length == 6 } ?: return null
        val o = stckOprc?.toDoubleOrNull() ?: return null
        val h = stckHgpr?.toDoubleOrNull() ?: return null
        val l = stckLwpr?.toDoubleOrNull() ?: return null
        val c = stckPrpr?.toDoubleOrNull() ?: return null
        val v = cntgVol?.toLongOrNull() ?: 0L
        val ldt = LocalDateTime.parse("$date$time", TIMESTAMP_FMT)
        return Candle(
            ts = ldt.atZone(KST).toInstant(),
            open = o, high = h, low = l, close = c, volume = v,
            timeframe = Timeframe.MIN_1,
        )
    }

    /** 일/주/월/년봉(DailyChartBar) → Candle 매퍼. 실패시 null. */
    private fun com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar.toCandle(tf: Timeframe): Candle? {
        val date = stckBsopDate?.takeIf { it.length == 8 } ?: return null
        val o = stckOprc?.toDoubleOrNull() ?: return null
        val h = stckHgpr?.toDoubleOrNull() ?: return null
        val l = stckLwpr?.toDoubleOrNull() ?: return null
        val c = stckClpr?.toDoubleOrNull() ?: return null
        val v = acmlVol?.toLongOrNull() ?: 0L
        val ld = LocalDate.parse(date, DATE_FMT)
        return Candle(
            ts = ld.atStartOfDay(KST).toInstant(),
            open = o, high = h, low = l, close = c, volume = v,
            timeframe = tf,
        )
    }

    /** 1분봉 리스트를 timeframe 분 단위로 묶어 집계. KST 분 경계 기준. */
    private fun aggregateMinutes(minute1: List<Candle>, timeframe: Timeframe): List<Candle> {
        val span = timeframe.intradayMinutes ?: return minute1
        if (minute1.isEmpty() || span <= 1) return minute1
        val grouped = minute1.groupBy { c ->
            val ld = c.ts.atZone(KST).toLocalDateTime()
            val bucketMinute = (ld.hour * 60 + ld.minute) / span * span
            ld.toLocalDate().atTime(bucketMinute / 60, bucketMinute % 60).atZone(KST).toInstant()
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { (ts, bars) ->
                Candle(
                    ts = ts,
                    open = bars.first().open,
                    high = bars.maxOf { it.high },
                    low = bars.minOf { it.low },
                    close = bars.last().close,
                    volume = bars.sumOf { it.volume },
                    timeframe = timeframe,
                )
            }
    }

    /** 배당 일정. fromDays~toDays 기간. */
    suspend fun fetchDividends(fromDays: Int = 0, toDays: Int = 60, ticker: String? = null): List<DividendItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val today = LocalDate.now(KST)
        val from = today.plusDays(fromDays.toLong()).format(DATE_FMT)
        val to = today.plusDays(toDays.toLong()).format(DATE_FMT)
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/ksdinfo/dividend"
        val resp = api.dividend(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "HHKDB669102C0",
            fromDate = from,
            toDate = to,
            shtCd = ticker.orEmpty(),
        )
        if (resp.rtCd == "0") resp.output1 else emptyList()
    }.onFailure { Timber.w(it, "fetchDividends 실패") }.getOrElse { emptyList() }

    /** 시장 전체 투자자별 매매동향 (최근 N일). KOSPI=0001, KOSDAQ=1001. */
    suspend fun fetchMarketInvestorDaily(market: String = "0001", daysBack: Int = 7): List<MarketInvestorDailyItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val today = LocalDate.now(KST)
        val from = today.minusDays(daysBack.toLong())
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-investor-daily-by-market"
        val resp = api.marketInvestorDaily(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHPTJ04040000",
            fidInputIscd = market,
            fidInputDate1 = from.format(DATE_FMT),
            fidInputDate2 = today.format(DATE_FMT),
        )
        if (resp.rtCd == "0") resp.output else emptyList()
    }.onFailure { Timber.w(it, "fetchMarketInvestorDaily 실패") }.getOrElse { emptyList() }

    /** 시장 예수금 / 신용잔고 추이. */
    suspend fun fetchMarketFunds(): List<MarketFundsItem> = runCatching {
        val creds = store.current() ?: return@runCatching emptyList()
        val token = authService.ensureAccessToken().getOrElse { return@runCatching emptyList() }
        rateLimiter.await()
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/quotations/mktfunds"
        val resp = api.marketFunds(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHPST01060000",
        )
        if (resp.rtCd == "0") resp.output else emptyList()
    }.onFailure { Timber.w(it, "fetchMarketFunds 실패") }.getOrElse { emptyList() }

    /** 한국 휴장일 set (YYYYMMDD). 캐시 24h. */
    suspend fun fetchKrHolidayDates(): Set<String> {
        val now = System.currentTimeMillis()
        val cached = holidayCache
        if (cached != null && now - holidayCacheStampMs < CACHE_TTL_MS) {
            return cached.filterValues { it }.keys
        }
        val creds = store.current() ?: return emptySet()
        val token = authService.ensureAccessToken().getOrElse { return emptySet() }
        return runCatching {
            rateLimiter.await()
            val today = LocalDate.now(KST).format(DATE_FMT)
            val url = creds.env.restBaseUrl + "/uapi/overseas-stock/v1/quotations/countries-holiday"
            val map = mutableMapOf<String, Boolean>()
            var ctxFk = ""
            var ctxNk = ""
            for (page in 0 until 5) {
                val resp = api.countriesHoliday(
                    url = url,
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    trId = "CTOS5011R",
                    bassDate = today,
                    ctxAreaFk = ctxFk,
                    ctxAreaNk = ctxNk,
                )
                if (resp.rtCd != "0") break
                resp.output.forEach { item ->
                    val date = item.bassDt ?: return@forEach
                    val isOpen = item.opbzYn?.uppercase() == "Y"
                    // 한국 시장만 집계, 비영업일 = 휴장
                    if (item.acplNatnCd == "KR" || item.acplNatnName?.contains("한국") == true) {
                        map[date] = !isOpen
                    }
                }
                val nextNk = resp.ctxAreaNk?.trim().orEmpty()
                if (nextNk.isEmpty()) break
                ctxFk = resp.ctxAreaFk?.trim().orEmpty()
                ctxNk = nextNk
            }
            holidayCache = map
            holidayCacheStampMs = now
            map.filterValues { it }.keys
        }.onFailure { Timber.w(it, "휴장일 조회 실패") }.getOrElse { emptySet() }
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val TIMESTAMP_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}
