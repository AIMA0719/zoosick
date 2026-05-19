package com.myinfocar.aicoachstock.ui.stockdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.PrimaryButton
import com.myinfocar.aicoachstock.ui.theme.AppTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar
import com.myinfocar.aicoachstock.data.remote.kis.dto.InvestorDailyItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasNewsItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.StockInfoOutput
import com.myinfocar.aicoachstock.domain.advisor.AdvisorEvent
import com.myinfocar.aicoachstock.domain.advisor.TradingAdvisorService
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.market.MarketDataStream
import com.myinfocar.aicoachstock.domain.model.Candle
import com.myinfocar.aicoachstock.domain.model.ChartType
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.OrderBookLevel
import com.myinfocar.aicoachstock.domain.model.OrderBookSnapshot
import com.myinfocar.aicoachstock.domain.model.SubscriptionReason
import com.myinfocar.aicoachstock.domain.model.SubscriptionTarget
import com.myinfocar.aicoachstock.domain.model.TickSource
import com.myinfocar.aicoachstock.domain.model.Timeframe
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class StockDetailUiState(
    val ticker: String = "",
    val market: Market = Market.KR,
    val nameKo: String? = null,
    val sector: String? = null,
    val tick: MarketTick? = null,
    val chart: List<DailyChartBar> = emptyList(),
    val asking: AskingPriceResponse? = null,
    val investors: List<InvestorDailyItem> = emptyList(),
    val news: List<OverseasNewsItem> = emptyList(),
    val info: StockInfoOutput? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    // Stage 15 차트 풀세트
    val timeframe: Timeframe = Timeframe.DAY,
    val chartType: ChartType = ChartType.CANDLE,
    val candles: List<Candle> = emptyList(),
    val candlesLoading: Boolean = false,
    val crosshairIndex: Int? = null,
    val orderBook: OrderBookSnapshot? = null,
    // AI 코칭 상태
    val advisorStreaming: String = "",
    val advisorIsGenerating: Boolean = false,
    val advisorResult: TradingAdvisorService.Parsed? = null,
    val advisorPhaseMessage: String? = null,
    val advisorError: String? = null,
)

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockRepo: StockRepository,
    private val stockInfoService: StockInfoService,
    private val marketDataSource: MarketDataSource,
    private val marketDataStream: MarketDataStream,
    private val advisorService: TradingAdvisorService,
) : ViewModel() {

    private val argTicker: String = checkNotNull(savedStateHandle["ticker"]) { "ticker 인자 없음" }

    private val _ui = MutableStateFlow(StockDetailUiState(ticker = argTicker))
    val ui: StateFlow<StockDetailUiState> = _ui.asStateFlow()

    init {
        load()
        startTickStream()
        startBookStream()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, errorMessage = null) }
            val stock = stockRepo.findByTicker(argTicker)
            val market = stock?.market ?: Market.KR
            val isKr = market == Market.KR

            val tick = marketDataSource.fetchClosePrice(argTicker, market).getOrNull()
            val info = if (isKr) stockInfoService.fetchStockInfo(argTicker) else null
            val chart = if (isKr) stockInfoService.fetchDailyChart(argTicker, 30) else emptyList()
            val asking = if (isKr) stockInfoService.fetchAskingPrice(argTicker) else null
            val investors = if (isKr) stockInfoService.fetchInvestorTrend(argTicker, 5) else emptyList()
            val news = if (market == Market.US) stockInfoService.fetchOverseasNews(symbol = argTicker, limit = 5) else emptyList()

            _ui.update {
                it.copy(
                    isLoading = false,
                    market = market,
                    nameKo = info?.prdtName ?: stock?.nameKo,
                    sector = info?.idxBztpSmallName ?: info?.stdIndustryName ?: stock?.sector,
                    tick = tick,
                    chart = chart,
                    asking = asking,
                    orderBook = asking?.toOrderBookSnapshot(argTicker),
                    investors = investors,
                    news = news,
                    info = info,
                )
            }
            // 차트 풀세트 — 기본 일봉 60개
            fetchCandlesFor(_ui.value.timeframe)
        }
    }

    private fun startBookStream() {
        viewModelScope.launch {
            marketDataStream.books(argTicker).collect { snap ->
                _ui.update { it.copy(orderBook = snap) }
            }
        }
    }

    private fun com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse.toOrderBookSnapshot(ticker: String): OrderBookSnapshot? {
        val o1 = output1 ?: return null
        fun level(price: String?, qty: String?): OrderBookLevel? {
            val p = price?.toDoubleOrNull() ?: return null
            if (p <= 0.0) return null
            return OrderBookLevel(p, qty?.toLongOrNull() ?: 0L)
        }
        val asks = listOfNotNull(
            level(o1.askp1, o1.askpRsqn1),
            level(o1.askp2, o1.askpRsqn2),
            level(o1.askp3, o1.askpRsqn3),
            level(o1.askp4, o1.askpRsqn4),
            level(o1.askp5, o1.askpRsqn5),
        )
        val bids = listOfNotNull(
            level(o1.bidp1, o1.bidpRsqn1),
            level(o1.bidp2, o1.bidpRsqn2),
            level(o1.bidp3, o1.bidpRsqn3),
            level(o1.bidp4, o1.bidpRsqn4),
            level(o1.bidp5, o1.bidpRsqn5),
        )
        if (asks.isEmpty() && bids.isEmpty()) return null
        val expected = output2?.antcCnpr?.toDoubleOrNull()?.takeIf { it > 0 }
        return OrderBookSnapshot(
            ticker = ticker,
            asks = asks,
            bids = bids,
            totalAskQty = o1.totalAskpRsqn?.toLongOrNull() ?: 0L,
            totalBidQty = o1.totalBidpRsqn?.toLongOrNull() ?: 0L,
            expectedPrice = expected,
            ts = Instant.now(),
            source = TickSource.REST_FALLBACK,
        )
    }

    fun setTimeframe(tf: Timeframe) {
        if (_ui.value.timeframe == tf && _ui.value.candles.isNotEmpty()) return
        _ui.update { it.copy(timeframe = tf, crosshairIndex = null) }
        viewModelScope.launch { fetchCandlesFor(tf) }
    }

    fun setChartType(ct: ChartType) {
        _ui.update { it.copy(chartType = ct) }
    }

    fun setCrosshair(idx: Int?) {
        _ui.update { it.copy(crosshairIndex = idx) }
    }

    private suspend fun fetchCandlesFor(tf: Timeframe) {
        _ui.update { it.copy(candlesLoading = true) }
        val candles = stockInfoService.fetchCandles(argTicker, tf, count = 60)
        _ui.update { it.copy(candles = candles, candlesLoading = false) }
    }

    /** WS 틱 구독 → 마지막 캔들 close 갱신 + 분 경계 신규 봉 push. */
    private fun startTickStream() {
        viewModelScope.launch {
            // 우선 종목 메타 조회로 market 확정 (load와 race 대비 한 번 더 조회).
            val market = stockRepo.findByTicker(argTicker)?.market ?: Market.KR
            marketDataStream.subscribe(
                listOf(
                    SubscriptionTarget(
                        ticker = argTicker,
                        market = market,
                        reason = SubscriptionReason.WATCHLIST,
                        priority = 1,
                    )
                )
            )
            marketDataStream.ticks(argTicker).collect { tick ->
                _ui.update { st ->
                    st.copy(
                        tick = tick,
                        candles = mergeTickIntoCandles(st.candles, tick, st.timeframe),
                    )
                }
            }
        }
    }

    /** 마지막 캔들 close 갱신 또는 분 경계 넘었으면 신규 봉 push. */
    private fun mergeTickIntoCandles(
        candles: List<Candle>,
        tick: MarketTick,
        tf: Timeframe,
    ): List<Candle> {
        if (candles.isEmpty()) return candles
        val last = candles.last()
        val nextBucket = nextBucketAfter(last.ts, tf)
        return if (!tick.lastTickAt.isBefore(nextBucket)) {
            candles + Candle(
                ts = nextBucket,
                open = tick.price,
                high = tick.price,
                low = tick.price,
                close = tick.price,
                volume = 0L,
                timeframe = tf,
            )
        } else {
            val updated = last.copy(
                high = maxOf(last.high, tick.price),
                low = minOf(last.low, tick.price),
                close = tick.price,
            )
            candles.dropLast(1) + updated
        }
    }

    private fun nextBucketAfter(start: Instant, tf: Timeframe): Instant {
        val zone = ZoneId.of("Asia/Seoul")
        val ldt = LocalDateTime.ofInstant(start, zone)
        val next = when (tf) {
            Timeframe.MIN_1, Timeframe.MIN_5, Timeframe.MIN_15, Timeframe.MIN_60 ->
                ldt.plusMinutes((tf.intradayMinutes ?: 1).toLong())
            Timeframe.DAY -> ldt.plusDays(1)
            Timeframe.WEEK -> ldt.plusWeeks(1)
            Timeframe.MONTH -> ldt.plusMonths(1)
            Timeframe.YEAR -> ldt.plusYears(1)
        }
        return next.atZone(zone).toInstant()
    }

    fun runAdvisor() {
        if (_ui.value.advisorIsGenerating) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    advisorIsGenerating = true,
                    advisorStreaming = "",
                    advisorResult = null,
                    advisorError = null,
                    advisorPhaseMessage = null,
                )
            }
            advisorService.analyze(argTicker).collect { ev ->
                when (ev) {
                    is AdvisorEvent.GatheringData -> _ui.update {
                        it.copy(advisorPhaseMessage = ev.message)
                    }
                    AdvisorEvent.LoadingModel -> _ui.update {
                        it.copy(advisorPhaseMessage = "Gemma 4 E4B 로드 중… (약 10초)")
                    }
                    is AdvisorEvent.Streaming -> _ui.update {
                        it.copy(advisorPhaseMessage = null, advisorStreaming = ev.partial)
                    }
                    is AdvisorEvent.Completed -> _ui.update {
                        it.copy(
                            advisorIsGenerating = false,
                            advisorStreaming = "",
                            advisorResult = ev.parsed,
                        )
                    }
                    is AdvisorEvent.Failed -> _ui.update {
                        it.copy(
                            advisorIsGenerating = false,
                            advisorStreaming = "",
                            advisorError = ev.cause.message ?: "분석 실패",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    onBack: () -> Unit,
    onPlaceOrder: (ticker: String, side: String) -> Unit,
    onOpenOrders: () -> Unit,
    viewModel: StockDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!state.isLoading) {
                OrderBottomBar(
                    ticker = state.ticker,
                    onBuy = { onPlaceOrder(state.ticker, "BUY") },
                    onSell = { onPlaceOrder(state.ticker, "SELL") },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.nameKo ?: state.ticker,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "${state.ticker}  ·  ${state.market.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenOrders) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "주문 내역")
                    }
                    IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppTokens.space16),
            verticalArrangement = Arrangement.spacedBy(AppTokens.space12),
        ) {
            PriceHeader(state)
            ChartCard(
                candles = state.candles,
                timeframe = state.timeframe,
                chartType = state.chartType,
                crosshairIndex = state.crosshairIndex,
                candlesLoading = state.candlesLoading,
                market = state.market,
                onTimeframeSelect = viewModel::setTimeframe,
                onChartTypeToggle = {
                    viewModel.setChartType(
                        if (state.chartType == ChartType.CANDLE) ChartType.LINE else ChartType.CANDLE
                    )
                },
                onCrosshairChange = viewModel::setCrosshair,
            )
            state.orderBook?.let { OrderBookCard(it, state.market) }
            if (state.investors.isNotEmpty()) InvestorCard(state.investors)
            state.info?.let { InfoCard(it) }
            if (state.news.isNotEmpty()) NewsCard(state.news)

            AdvisorSection(
                state = state,
                onRun = viewModel::runAdvisor,
            )

            Text(
                "본 응답은 코칭 보조이며 매매 책임은 본인에게 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PriceHeader(state: StockDetailUiState) {
    val tick = state.tick
    val priceColor = com.myinfocar.aicoachstock.ui.common.pnlColor(tick?.changePct)
    AppCard(padding = AppTokens.space20) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.nameKo ?: state.ticker,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(AppTokens.space8))
                state.sector?.let { AssistChip(onClick = {}, label = { Text(it) }, enabled = false) }
            }
            Text(
                tick?.let { formatPrice(it.price, state.market) } ?: "—",
                style = MaterialTheme.typography.displayMedium,
                color = priceColor,
            )
            tick?.let {
                val sign = if ((it.change ?: 0.0) > 0) "+" else if ((it.change ?: 0.0) < 0) "" else ""
                Text(
                    "$sign${it.change ?: 0}  (${"%+.2f".format(it.changePct ?: 0.0)}%)",
                    style = MaterialTheme.typography.titleMedium,
                    color = priceColor,
                )
                Text(
                    "거래량 ${it.volumeCum ?: "-"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(
    candles: List<Candle>,
    timeframe: Timeframe,
    chartType: ChartType,
    crosshairIndex: Int?,
    candlesLoading: Boolean,
    market: Market,
    onTimeframeSelect: (Timeframe) -> Unit,
    onChartTypeToggle: () -> Unit,
    onCrosshairChange: (Int?) -> Unit,
) {
    AppCard(padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("차트", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = onChartTypeToggle,
                    label = { Text(if (chartType == ChartType.CANDLE) "캔들" else "라인") },
                )
            }
            TimeframeTabs(selected = timeframe, onSelect = onTimeframeSelect)
            if (candlesLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (candles.isNotEmpty()) {
                RealtimeChart(
                    candles = candles,
                    chartType = chartType,
                    crosshairIndex = crosshairIndex,
                    onCrosshairChange = onCrosshairChange,
                )
                crosshairIndex?.let { idx ->
                    if (idx in candles.indices) CrosshairLabel(candles[idx], market)
                }
                ChartSummary(candles = candles)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "데이터 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeframeTabs(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
        items(Timeframe.entries.toList()) { tf ->
            FilterChip(
                selected = selected == tf,
                onClick = { onSelect(tf) },
                label = { Text(tf.labelKo) },
            )
        }
    }
}

@Composable
private fun CrosshairLabel(c: Candle, market: Market) {
    val zone = ZoneId.of("Asia/Seoul")
    val ldt = LocalDateTime.ofInstant(c.ts, zone)
    Column {
        Text(
            "${ldt.year}-%02d-%02d %02d:%02d".format(ldt.monthValue, ldt.dayOfMonth, ldt.hour, ldt.minute),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "O ${formatPrice(c.open, market)}  H ${formatPrice(c.high, market)}  L ${formatPrice(c.low, market)}  C ${formatPrice(c.close, market)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "거래량 ${"%,d".format(c.volume)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartSummary(candles: List<Candle>) {
    val first = candles.firstOrNull()?.close ?: return
    val last = candles.lastOrNull()?.close ?: return
    if (first <= 0) return
    val pct = (last - first) / first * 100
    Text(
        "기간 등락률 ${"%+.2f".format(pct)}%",
        style = MaterialTheme.typography.labelMedium,
        color = com.myinfocar.aicoachstock.ui.common.pnlColor(pct),
    )
}

@Composable
private fun OrderBookCard(book: OrderBookSnapshot, market: Market) {
    val askColor = com.myinfocar.aicoachstock.ui.common.KrDownBlue
    val bidColor = com.myinfocar.aicoachstock.ui.common.KrUpRed
    val sourceLabel = if (book.source == TickSource.WS_LIVE) "실시간" else "REST"
    AppCard(padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("호가 (5호가)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            book.expectedPrice?.let { p ->
                Text(
                    "예상체결가 ${formatPrice(p, market)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(AppTokens.space4))
            // 매도 호가: 5호가 위 → 1호가 아래 (시각적으로 매도창)
            val asks = book.asks.take(5)
            asks.reversed().forEachIndexed { i, level ->
                val rank = asks.size - i
                LevelRow("매도$rank", formatPrice(level.price, market), "%,d".format(level.quantity), askColor)
            }
            // 매수 호가: 1호가 위 → 5호가 아래
            book.bids.take(5).forEachIndexed { i, level ->
                LevelRow("매수${i + 1}", formatPrice(level.price, market), "%,d".format(level.quantity), bidColor)
            }
            Text(
                "총 매도잔량 ${"%,d".format(book.totalAskQty)} / 총 매수잔량 ${"%,d".format(book.totalBidQty)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelRow(label: String, price: String, qty: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(48.dp), color = color, style = MaterialTheme.typography.bodyMedium)
        Text(price, modifier = Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyMedium)
        Text(qty, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InvestorCard(rows: List<InvestorDailyItem>) {
    AppCard(padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Text("투자자별 순매수 (최근 ${rows.size}일)", style = MaterialTheme.typography.titleMedium)
            rows.forEach { d ->
                Text(
                    "${d.stckBsopDate}: 개인 ${d.prsnNtbyQty ?: "-"} / 외국인 ${d.frgnNtbyQty ?: "-"} / 기관 ${d.orgnNtbyQty ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(info: StockInfoOutput) {
    AppCard(padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Text("종목 정보", style = MaterialTheme.typography.titleMedium)
            info.prdtName?.let { Text("• $it", fontWeight = FontWeight.Medium) }
            info.idxBztpSmallName?.let { Text("• 업종: $it") }
            info.lstgStqt?.let { Text("• 상장주식수: $it") }
            info.capiAmt?.let { Text("• 자본금: $it") }
            info.dryyHgpr?.let { Text("• 당해년도 최고가: $it") }
            info.dryyLwpr?.let { Text("• 당해년도 최저가: $it") }
        }
    }
}

@Composable
private fun NewsCard(news: List<OverseasNewsItem>) {
    AppCard(padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space8)) {
            Text("뉴스 (한투 해외뉴스종합)", style = MaterialTheme.typography.titleMedium)
            news.forEach { n ->
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space2)) {
                    Text(n.title.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${n.dataDt ?: ""} ${n.source ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvisorSection(state: StockDetailUiState, onRun: () -> Unit) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        padding = AppTokens.space20,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space12)) {
            Text(
                "AI 코칭 — 지금 어떻게?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "현재가·차트·재무·외국인/기관·내 보유·활성 원칙을 종합해서 BUY/HOLD/SELL 의견과 근거를 제시합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            PrimaryButton(
                text = "AI 분석 받기",
                onClick = onRun,
                enabled = !state.advisorIsGenerating,
                isLoading = state.advisorIsGenerating,
            )
            state.advisorPhaseMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (state.advisorStreaming.isNotEmpty()) {
                AppCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(state.advisorStreaming, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.advisorResult?.let { res -> AdvisorResultCard(res) }
            state.advisorError?.let { err ->
                Text(
                    "❌ $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AdvisorResultCard(res: TradingAdvisorService.Parsed) {
    val (label, color) = when (res.recommendation) {
        TradingAdvisorService.Recommendation.BUY -> "BUY" to com.myinfocar.aicoachstock.ui.common.KrUpRed
        TradingAdvisorService.Recommendation.HOLD -> "HOLD" to Color(0xFFF59E0B)
        TradingAdvisorService.Recommendation.SELL -> "SELL" to com.myinfocar.aicoachstock.ui.common.KrDownBlue
    }
    AppCard(
        containerColor = color.copy(alpha = 0.10f),
        padding = AppTokens.space20,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.displaySmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(AppTokens.space12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "확신도 ${res.confidence}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(AppTokens.space4))
                    LinearProgressIndicator(
                        progress = { res.confidence / 100f },
                        color = color,
                        trackColor = color.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(res.analysis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatPrice(value: Double, market: Market): String = when (market) {
    Market.KR -> "%,d원".format(value.toLong())
    Market.US -> "$${"%.2f".format(value)}"
}

@Composable
private fun OrderBottomBar(
    ticker: String,
    onBuy: () -> Unit,
    onSell: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppTokens.space12),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.space8),
    ) {
        androidx.compose.material3.Button(
            onClick = onSell,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = com.myinfocar.aicoachstock.ui.common.KrDownBlue,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
        ) {
            Text("매도", fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.Button(
            onClick = onBuy,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = com.myinfocar.aicoachstock.ui.common.KrUpRed,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
        ) {
            Text("매수", fontWeight = FontWeight.Bold)
        }
    }
}
