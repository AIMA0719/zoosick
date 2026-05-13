package com.myinfocar.aicoachstock.data.remote.kis.market

import com.myinfocar.aicoachstock.data.remote.kis.KisRateLimiter
import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.market.Fundamentals
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.Currency
import com.myinfocar.aicoachstock.domain.model.Exchange
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.model.TickSource
import retrofit2.HttpException
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 REST 시세 폴백 + 종목 메타 조회 구현.
 *
 *  - WS 끊김·비장시간 종가 조회용.
 *  - RateLimiter: 분당 ~1000회 (한투 공식 20회/초) 초과 방지. 보수적으로 100ms gap.
 *  - 응답을 Stock 캐시에 저장하지 않고 호출자에게만 반환 (UpsertStock은 호출자 책임).
 *
 *  종목 검색: 한투에 통합 검색 API가 없어서, 입력한 ticker를 정확 일치로 단일 조회한 뒤 매칭 시 결과 반환.
 *  (Phase 2 이후 종목 마스터 파일 다운로드 + 로컬 색인 검토.)
 */
@Singleton
class KisMarketDataSource @Inject constructor(
    private val api: KisMarketRestApi,
    private val authService: KisAuthService,
    private val store: ApiCredentialStore,
    private val rateLimiter: KisRateLimiter,
    private val stockInfoService: com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService,
) : MarketDataSource {

    override suspend fun fetchClosePrice(ticker: String, market: Market): Result<MarketTick> =
        runCatching {
            val token = ensureToken()
            val creds = currentCredsOrError()
            when (market) {
                Market.KR -> {
                    val url = creds.envRestBase() + "/uapi/domestic-stock/v1/quotations/inquire-price"
                    val resp = rateLimiter.callWithRetry("inquire-price-kr") {
                        api.fetchKrPrice(
                            url = url,
                            authorization = "Bearer $token",
                            appKey = creds.appKey,
                            appSecret = creds.appSecret,
                            trId = "FHKST01010100",
                            ticker = ticker,
                        )
                    }
                    if (resp.rtCd != "0") {
                        if (com.myinfocar.aicoachstock.data.remote.kis.KisRateLimiter
                                .isRateLimitMessage(resp.msgCd, resp.msg1)
                        ) {
                            // rt_cd로 떨어진 rate-limit — 한 번 백오프 후 재시도.
                            rateLimiter.backoff(0)
                            val resp2 = rateLimiter.callWithRetry("inquire-price-kr-retry") {
                                api.fetchKrPrice(
                                    url = url,
                                    authorization = "Bearer $token",
                                    appKey = creds.appKey,
                                    appSecret = creds.appSecret,
                                    trId = "FHKST01010100",
                                    ticker = ticker,
                                )
                            }
                            if (resp2.rtCd != "0") {
                                throw RuntimeException("KIS rt_cd=${resp2.rtCd} msg=${resp2.msg1.orEmpty()}")
                            }
                            val out2 = resp2.output ?: throw RuntimeException("KIS output 비어있음")
                            return@runCatching MarketTick(
                                ticker = ticker,
                                price = out2.price?.toDoubleOrNull() ?: 0.0,
                                change = out2.change?.toDoubleOrNull(),
                                changePct = out2.changePct?.toDoubleOrNull(),
                                volumeCum = out2.volumeCum?.toLongOrNull(),
                                lastTickAt = Instant.now(),
                                source = TickSource.REST_FALLBACK,
                            )
                        }
                        throw RuntimeException("KIS rt_cd=${resp.rtCd} msg=${resp.msg1.orEmpty()}")
                    }
                    val out = resp.output ?: throw RuntimeException("KIS output 비어있음")
                    MarketTick(
                        ticker = ticker,
                        price = out.price?.toDoubleOrNull() ?: 0.0,
                        change = out.change?.toDoubleOrNull(),
                        changePct = out.changePct?.toDoubleOrNull(),
                        volumeCum = out.volumeCum?.toLongOrNull(),
                        lastTickAt = Instant.now(),
                        source = TickSource.REST_FALLBACK,
                    )
                }
                Market.US -> {
                    val excd = guessUsExchange(ticker)
                    val url = creds.envRestBase() + "/uapi/overseas-price/v1/quotations/price"
                    val resp = rateLimiter.callWithRetry("inquire-price-us") {
                        api.fetchUsPrice(
                            url = url,
                            authorization = "Bearer $token",
                            appKey = creds.appKey,
                            appSecret = creds.appSecret,
                            trId = "HHDFS00000300",
                            excd = excd,
                            symbol = ticker,
                        )
                    }
                    if (resp.rtCd != "0") {
                        throw RuntimeException("KIS rt_cd=${resp.rtCd} msg=${resp.msg1.orEmpty()}")
                    }
                    val out = resp.output ?: throw RuntimeException("KIS output 비어있음")
                    MarketTick(
                        ticker = ticker,
                        price = out.price?.toDoubleOrNull() ?: 0.0,
                        change = out.change?.toDoubleOrNull(),
                        changePct = out.changePct?.toDoubleOrNull(),
                        volumeCum = out.volumeCum?.toLongOrNull(),
                        lastTickAt = Instant.now(),
                        source = TickSource.REST_FALLBACK,
                    )
                }
            }
        }.onFailure { Timber.w(it, "fetchClosePrice 실패 ticker=$ticker market=$market") }

    override suspend fun searchStocks(query: String): Result<List<Stock>> = runCatching {
        val trimmed = query.trim().uppercase()
        if (trimmed.isEmpty()) return@runCatching emptyList()

        // 6자리 숫자 → 국내 단일 조회 시도. 알파벳 → 미국 단일 조회 시도. 둘 다 가능한 경우 양쪽 시도.
        val candidates = mutableListOf<Stock>()
        val isKrFormat = trimmed.length == 6 && trimmed.all { it.isDigit() }
        val isUsFormat = trimmed.all { it.isLetterOrDigit() } && trimmed.length in 1..6 && !isKrFormat

        if (isKrFormat) {
            fetchClosePrice(trimmed, Market.KR).getOrNull()?.let { tick ->
                // 한투 search-stock-info로 정확한 메타 조회 (실패 시 휴리스틱 fallback).
                val info = stockInfoService.fetchStockInfo(trimmed)
                val name = info?.prdtName?.takeIf { it.isNotBlank() }
                    ?: krStockName(trimmed) ?: trimmed
                val exchange = when {
                    info?.mketIdCd == "STK" -> Exchange.KOSPI
                    info?.mketIdCd == "KSQ" -> Exchange.KOSDAQ
                    else -> guessKrExchange(trimmed)
                }
                candidates += Stock(
                    ticker = trimmed,
                    nameKo = name,
                    nameEn = info?.prdtEngName,
                    exchange = exchange,
                    sector = info?.idxBztpSmallName ?: info?.stdIndustryName,
                    currency = Currency.KRW,
                )
            }
        }
        if (isUsFormat) {
            fetchClosePrice(trimmed, Market.US).getOrNull()?.let {
                candidates += Stock(
                    ticker = trimmed,
                    nameKo = trimmed,
                    nameEn = trimmed,
                    exchange = guessUsExchangeMeta(trimmed),
                    sector = null,
                    currency = Currency.USD,
                )
            }
        }
        candidates
    }

    /** 국내 단일 조회로 한국어 종목명을 추출. 실패해도 비치명적. */
    private suspend fun krStockName(ticker: String): String? = runCatching {
        val token = ensureToken()
        val creds = currentCredsOrError()
        rateLimiter.await()
        val url = creds.envRestBase() + "/uapi/domestic-stock/v1/quotations/inquire-price"
        val resp = api.fetchKrPrice(
            url = url,
            authorization = "Bearer $token",
            appKey = creds.appKey,
            appSecret = creds.appSecret,
            trId = "FHKST01010100",
            ticker = ticker,
        )
        resp.output?.nameKo?.takeIf { it.isNotBlank() }
    }.getOrNull()

    override suspend fun fetchFundamentals(ticker: String): Result<Fundamentals> = runCatching {
        // Phase 1에서는 KIS Fundamentals 엔드포인트 미구현. Phase 2에서 별도 정의.
        Fundamentals(ticker, null, null, null, null, null, null)
    }

    private suspend fun ensureToken(): String =
        authService.ensureAccessToken().getOrElse { throw it }

    private suspend fun currentCredsOrError() =
        store.current() ?: throw IllegalStateException("API 키가 설정되지 않았습니다")

    private fun guessKrExchange(ticker: String): Exchange =
        // 6자리 시작 코드 휴리스틱 — 정확 분류는 종목마스터 다운로드 후.
        if (ticker.startsWith("3") || ticker.startsWith("2")) Exchange.KOSDAQ else Exchange.KOSPI

    private fun guessUsExchange(ticker: String): String {
        // 사용자가 특정 거래소 강제하지 않는 한 일단 NAS 시도.
        // 추후 종목 마스터로 정확 분류.
        return "NAS"
    }

    private fun guessUsExchangeMeta(ticker: String): Exchange = Exchange.NASDAQ
}

private fun com.myinfocar.aicoachstock.domain.auth.ApiCredentials.envRestBase() = env.restBaseUrl

private fun HttpException.statusInfo() = "HTTP ${code()}"
