package com.myinfocar.aicoachstock.domain.account

import com.myinfocar.aicoachstock.data.remote.kis.KisRateLimiter
import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.data.remote.kis.market.KisTradingApi
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.model.Market
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 계정 정보 조회 — 잔고 (KR + US) + 기간 손익.
 *
 *  - 자동 매매 X. 조회만.
 *  - rate limiter 통과.
 *  - 실전(PROD) 전용 — 모의 분기 제거.
 */
@Singleton
class AccountService @Inject constructor(
    private val tradingApi: KisTradingApi,
    private val authService: KisAuthService,
    private val store: ApiCredentialStore,
    private val rateLimiter: KisRateLimiter,
) {

    // 짧은 TTL 캐시 — 홈/Advisor/Briefing이 같은 잔고를 짧은 시간 안에 동시에 요청해도 페이징 1번만 실행.
    @Volatile private var krBalanceCache: Pair<List<Holding>, AccountSummary>? = null
    @Volatile private var krBalanceCachedAtMs: Long = 0L

    suspend fun fetchKrBalance(forceRefresh: Boolean = false): Result<Pair<List<Holding>, AccountSummary>> {
        if (!forceRefresh) {
            val cached = krBalanceCache
            if (cached != null && System.currentTimeMillis() - krBalanceCachedAtMs < BALANCE_CACHE_TTL_MS) {
                return Result.success(cached)
            }
        }
        return fetchKrBalanceUncached().also { r ->
            r.getOrNull()?.let {
                krBalanceCache = it
                krBalanceCachedAtMs = System.currentTimeMillis()
            }
        }
    }

    private suspend fun fetchKrBalanceUncached(): Result<Pair<List<Holding>, AccountSummary>> = runCatching {
        val creds = store.current() ?: throw IllegalStateException("API 키 미설정")
        val cano = creds.accountNo ?: throw IllegalStateException("계좌번호 미설정")
        val prdt = creds.productCode ?: "01"
        val token = authService.ensureAccessToken().getOrElse { throw it }

        val trId = "TTTC8434R"
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/inquire-balance"

        val holdings = mutableListOf<Holding>()
        var sumPchsAmt = 0.0
        var sumEvluAmt = 0.0
        var sumPfls = 0.0
        var cash: Double? = null
        var totAsset: Double? = null

        var ctxFk = ""
        var ctxNk = ""
        var trCont = ""
        // for+break를 써야 마지막 페이지 후 루프 종료. repeat { return@repeat }은 continue 의미라서 마지막 ctxNk로 19번 더 호출됨.
        for (page in 0 until MAX_PAGES) {
            rateLimiter.await()
            val resp = try {
                tradingApi.fetchBalance(
                    url = url,
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    trId = trId,
                    trCont = trCont,
                    accountNo = cano,
                    productCode = prdt,
                    ctxFk100 = ctxFk,
                    ctxNk100 = ctxNk,
                )
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                throw RuntimeException("한투 HTTP ${e.code()} (TR=$trId) — ${body ?: e.message()}", e)
            }
            if (resp.rtCd != "0") {
                throw RuntimeException("한투 rt_cd=${resp.rtCd} msg=${resp.msg1.orEmpty()}")
            }
            for (h in resp.output1) {
                val qty = h.hldgQty?.toIntOrNull() ?: 0
                if (qty == 0) continue // 당일 전량매도 잔재 skip
                holdings += Holding(
                    ticker = h.pdno.orEmpty().trim(),
                    name = h.prdtName.orEmpty().trim(),
                    market = Market.KR,
                    qty = qty,
                    avgBuyPrice = h.pchsAvgPric?.toDoubleOrNull() ?: 0.0,
                    currentPrice = h.prpr?.toDoubleOrNull() ?: 0.0,
                    totalBuyAmount = h.pchsAmt?.toDoubleOrNull() ?: 0.0,
                    evaluationAmount = h.evluAmt?.toDoubleOrNull() ?: 0.0,
                    unrealizedPnl = h.evluPflsAmt?.toDoubleOrNull() ?: 0.0,
                    unrealizedPnlRate = h.evluPflsRt?.toDoubleOrNull() ?: 0.0,
                    currencyCode = "KRW",
                )
            }
            resp.output2.firstOrNull()?.let { s ->
                sumPchsAmt = s.pchsAmtSmtlAmt?.toDoubleOrNull() ?: sumPchsAmt
                sumEvluAmt = s.evluAmtSmtlAmt?.toDoubleOrNull() ?: sumEvluAmt
                sumPfls = s.evluPflsSmtlAmt?.toDoubleOrNull() ?: sumPfls
                cash = s.dncaTotAmt?.toDoubleOrNull() ?: cash
                totAsset = s.nassAmt?.toDoubleOrNull() ?: totAsset
            }
            val nextNk = resp.ctxAreaNk100?.trim().orEmpty()
            if (nextNk.isEmpty()) break
            ctxFk = resp.ctxAreaFk100?.trim().orEmpty()
            ctxNk = nextNk
            trCont = "N"
        }

        val rate = if (sumPchsAmt > 0) sumPfls / sumPchsAmt * 100.0 else 0.0
        val summary = AccountSummary(
            market = Market.KR,
            totalEvaluation = sumEvluAmt,
            totalBuyAmount = sumPchsAmt,
            unrealizedPnl = sumPfls,
            unrealizedPnlRate = rate,
            cashDeposit = cash,
            totalAssetValue = totAsset,
            currencyCode = "KRW",
        )
        Timber.i("KR Balance: ${holdings.size} holdings, evlu=$sumEvluAmt pfls=$sumPfls")
        holdings to summary
    }

    /**
     * US 잔고는 거래소별로 endpoint를 분리해서 호출해야 한다. NASD/NYSE/AMEX 모두 합산.
     * (KIS overseas-stock inquire-balance는 OVRS_EXCG_CD로 한 번에 한 거래소만 반환.)
     */
    suspend fun fetchUsBalance(): Result<Pair<List<Holding>, AccountSummary>> = runCatching {
        val allHoldings = mutableListOf<Holding>()
        var totalBuy = 0.0
        var totalEval = 0.0
        var totalPnl = 0.0
        var lastError: Throwable? = null
        for (excg in US_EXCHANGES) {
            fetchUsBalanceForExchange(excg).fold(
                onSuccess = { (h, s) ->
                    allHoldings += h
                    totalBuy += s.totalBuyAmount
                    totalEval += s.totalEvaluation
                    totalPnl += s.unrealizedPnl
                },
                onFailure = { lastError = it },
            )
        }
        if (allHoldings.isEmpty() && lastError != null) throw lastError!!
        val rate = if (totalBuy > 0) totalPnl / totalBuy * 100.0 else 0.0
        allHoldings to AccountSummary(
            market = Market.US,
            totalEvaluation = totalEval,
            totalBuyAmount = totalBuy,
            unrealizedPnl = totalPnl,
            unrealizedPnlRate = rate,
            cashDeposit = null,
            totalAssetValue = null,
            currencyCode = "USD",
        )
    }

    private suspend fun fetchUsBalanceForExchange(exchange: String): Result<Pair<List<Holding>, AccountSummary>> = runCatching {
        val creds = store.current() ?: throw IllegalStateException("API 키 미설정")
        val cano = creds.accountNo ?: throw IllegalStateException("계좌번호 미설정")
        val prdt = creds.productCode ?: "01"
        val token = authService.ensureAccessToken().getOrElse { throw it }

        val trId = "TTTS3012R"
        val url = creds.env.restBaseUrl + "/uapi/overseas-stock/v1/trading/inquire-balance"

        val holdings = mutableListOf<Holding>()
        var ctxFk = ""
        var ctxNk = ""
        var trCont = ""
        var summary: AccountSummary? = null

        for (page in 0 until MAX_PAGES) {
            rateLimiter.await()
            val resp = try {
                tradingApi.fetchOverseasBalance(
                    url = url,
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    trId = trId,
                    trCont = trCont,
                    accountNo = cano,
                    productCode = prdt,
                    ovrsExcgCd = exchange,
                    ctxFk200 = ctxFk,
                    ctxNk200 = ctxNk,
                )
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                throw RuntimeException("한투 HTTP ${e.code()} (TR=$trId) — ${body ?: e.message()}", e)
            }
            if (resp.rtCd != "0") {
                throw RuntimeException("한투 rt_cd=${resp.rtCd} msg=${resp.msg1.orEmpty()}")
            }
            for (h in resp.output1) {
                val qty = h.ovrsCblcQty?.toDoubleOrNull()?.toInt() ?: 0
                if (qty == 0) continue
                holdings += Holding(
                    ticker = h.ovrsPdno.orEmpty().trim(),
                    name = h.ovrsItemName.orEmpty().trim(),
                    market = Market.US,
                    qty = qty,
                    avgBuyPrice = h.pchsAvgPric?.toDoubleOrNull() ?: 0.0,
                    currentPrice = h.nowPric?.toDoubleOrNull() ?: 0.0,
                    totalBuyAmount = h.frcrPchsAmt?.toDoubleOrNull() ?: 0.0,
                    evaluationAmount = h.ovrsStckEvluAmt?.toDoubleOrNull() ?: 0.0,
                    unrealizedPnl = h.frcrEvluPflsAmt?.toDoubleOrNull() ?: 0.0,
                    unrealizedPnlRate = h.evluPflsRt?.toDoubleOrNull() ?: 0.0,
                    currencyCode = h.trCrcyCd ?: "USD",
                    exchangeCode = h.ovrsExcgCd,
                )
            }
            resp.output2?.let { s ->
                summary = AccountSummary(
                    market = Market.US,
                    totalEvaluation = (s.frcrPchsAmt?.toDoubleOrNull() ?: 0.0) +
                        (s.totEvluPflsAmt?.toDoubleOrNull() ?: 0.0),
                    totalBuyAmount = s.frcrPchsAmt?.toDoubleOrNull() ?: 0.0,
                    unrealizedPnl = s.totEvluPflsAmt?.toDoubleOrNull() ?: 0.0,
                    unrealizedPnlRate = s.totPftrt?.toDoubleOrNull() ?: 0.0,
                    cashDeposit = null,
                    totalAssetValue = null,
                    currencyCode = "USD",
                )
            }
            val nextNk = resp.ctxAreaNk200?.trim().orEmpty()
            if (nextNk.isEmpty()) break
            ctxFk = resp.ctxAreaFk200?.trim().orEmpty()
            ctxNk = nextNk
            trCont = "N"
        }
        val sum = summary ?: AccountSummary(
            market = Market.US,
            totalEvaluation = holdings.sumOf { it.evaluationAmount },
            totalBuyAmount = holdings.sumOf { it.totalBuyAmount },
            unrealizedPnl = holdings.sumOf { it.unrealizedPnl },
            unrealizedPnlRate = 0.0,
            currencyCode = "USD",
        )
        holdings to sum
    }

    suspend fun fetchPeriodProfit(daysBack: Int = 30): Result<PeriodProfitTotal> = runCatching {
        val creds = store.current() ?: throw IllegalStateException("API 키 미설정")
        val cano = creds.accountNo ?: throw IllegalStateException("계좌번호 미설정")
        val prdt = creds.productCode ?: "01"
        val token = authService.ensureAccessToken().getOrElse { throw it }

        val today = LocalDate.now(KST)
        val start = today.minusDays(daysBack.toLong())
        val startStr = start.format(DATE_FMT)
        val endStr = today.format(DATE_FMT)
        val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/inquire-period-profit"

        val days = mutableListOf<PeriodProfitDay>()
        var totalBuy = 0.0
        var totalSell = 0.0
        var totalRealized = 0.0
        var totalFee = 0.0
        var pnlRate = 0.0

        // inquire-period-profit은 단발 호출 (페이징 미지원). 한 번만 호출.
        rateLimiter.await()
        val resp = try {
            tradingApi.fetchPeriodProfit(
                url = url,
                authorization = "Bearer $token",
                appKey = creds.appKey,
                appSecret = creds.appSecret,
                trId = "TTTC8708R",
                trCont = "",
                accountNo = cano,
                productCode = prdt,
                startDate = startStr,
                endDate = endStr,
                ctxFk100 = "",
                ctxNk100 = "",
            )
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            throw RuntimeException("한투 HTTP ${e.code()} (TR=TTTC8708R) — ${body ?: e.message()}", e)
        }
        if (resp.rtCd != "0") {
            throw RuntimeException("한투 rt_cd=${resp.rtCd} msg=${resp.msg1.orEmpty()}")
        }
        resp.output1.forEach { d ->
            days += PeriodProfitDay(
                date = d.tradDt.orEmpty(),
                buyAmount = d.buyAmt?.toDoubleOrNull() ?: 0.0,
                sellAmount = d.sllAmt?.toDoubleOrNull() ?: 0.0,
                realizedPnl = d.rlztPfls?.toDoubleOrNull() ?: 0.0,
                fee = d.fee?.toDoubleOrNull() ?: 0.0,
                pnlRate = d.pflsRt?.toDoubleOrNull() ?: 0.0,
            )
        }
        resp.output2.firstOrNull()?.let { s ->
            totalBuy = s.buyTrAmtSmtl?.toDoubleOrNull() ?: totalBuy
            totalSell = s.sllTrAmtSmtl?.toDoubleOrNull() ?: totalSell
            totalRealized = s.rlztPflsSmtl?.toDoubleOrNull() ?: totalRealized
            totalFee = s.sllFeeSmtl?.toDoubleOrNull() ?: totalFee
            pnlRate = s.pflsRtSmtl?.toDoubleOrNull() ?: pnlRate
        }

        PeriodProfitTotal(
            rangeStart = startStr,
            rangeEnd = endStr,
            totalBuy = totalBuy,
            totalSell = totalSell,
            totalRealizedPnl = totalRealized,
            totalFee = totalFee,
            pnlRate = pnlRate,
            days = days,
        )
    }

    private companion object {
        const val BALANCE_CACHE_TTL_MS = 60_000L
        const val MAX_PAGES = 20
        val US_EXCHANGES = listOf("NASD", "NYSE", "AMEX")
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
