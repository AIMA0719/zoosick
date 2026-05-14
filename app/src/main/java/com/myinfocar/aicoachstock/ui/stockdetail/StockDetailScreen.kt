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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.myinfocar.aicoachstock.data.remote.kis.dto.AskingPriceResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.DailyChartBar
import com.myinfocar.aicoachstock.data.remote.kis.dto.InvestorDailyItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasNewsItem
import com.myinfocar.aicoachstock.data.remote.kis.dto.StockInfoOutput
import com.myinfocar.aicoachstock.domain.advisor.AdvisorEvent
import com.myinfocar.aicoachstock.domain.advisor.TradingAdvisorService
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val advisorService: TradingAdvisorService,
) : ViewModel() {

    private val argTicker: String = checkNotNull(savedStateHandle["ticker"]) { "ticker 인자 없음" }

    private val _ui = MutableStateFlow(StockDetailUiState(ticker = argTicker))
    val ui: StateFlow<StockDetailUiState> = _ui.asStateFlow()

    init {
        load()
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
                    investors = investors,
                    news = news,
                    info = info,
                )
            }
        }
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
    viewModel: StockDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            if (state.chart.isNotEmpty()) ChartCard(state.chart)
            state.asking?.let { AskingCard(it, state.market) }
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
private fun ChartCard(bars: List<DailyChartBar>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("일봉 차트  (최근 ${bars.size}거래일)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            // bars는 최근→과거 순으로 옴 — 시간 순으로 뒤집기.
            val prices = bars.mapNotNull { it.stckClpr?.toDoubleOrNull() }.reversed()
            PriceLineChart(prices = prices)
            val first = prices.firstOrNull() ?: 0.0
            val last = prices.lastOrNull() ?: 0.0
            if (first > 0) {
                val pct = (last - first) / first * 100
                Text(
                    "기간 등락률 ${"%+.2f".format(pct)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = com.myinfocar.aicoachstock.ui.common.pnlColor(pct),
                )
            }
        }
    }
}

@Composable
private fun AskingCard(resp: AskingPriceResponse, market: Market) {
    val levels = resp.output1 ?: return
    val sums = resp.output2
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("호가  (5호가)", style = MaterialTheme.typography.titleMedium)
            sums?.let {
                if (!it.antcCnpr.isNullOrBlank() && it.antcCnpr != "0") {
                    Text(
                        "예상체결가 ${it.antcCnpr}  (${it.antcCntgPrdyCtrt}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val askColor = com.myinfocar.aicoachstock.ui.common.KrDownBlue
            val bidColor = com.myinfocar.aicoachstock.ui.common.KrUpRed
            listOf(
                "매도1" to (levels.askp1 to levels.askpRsqn1),
                "매도2" to (levels.askp2 to levels.askpRsqn2),
                "매도3" to (levels.askp3 to levels.askpRsqn3),
            ).forEach { (label, pair) -> LevelRow(label, pair.first, pair.second, askColor) }
            listOf(
                "매수1" to (levels.bidp1 to levels.bidpRsqn1),
                "매수2" to (levels.bidp2 to levels.bidpRsqn2),
                "매수3" to (levels.bidp3 to levels.bidpRsqn3),
            ).forEach { (label, pair) -> LevelRow(label, pair.first, pair.second, bidColor) }
            Text(
                "총 매도잔량 ${levels.totalAskpRsqn ?: "-"} / 총 매수잔량 ${levels.totalBidpRsqn ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelRow(label: String, price: String?, qty: String?, color: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(48.dp), color = color, style = MaterialTheme.typography.bodyMedium)
        Text(price ?: "-", modifier = Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyMedium)
        Text(qty ?: "-", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InvestorCard(rows: List<InvestorDailyItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("뉴스 (한투 해외뉴스종합)", style = MaterialTheme.typography.titleMedium)
            news.forEach { n ->
                Column {
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
