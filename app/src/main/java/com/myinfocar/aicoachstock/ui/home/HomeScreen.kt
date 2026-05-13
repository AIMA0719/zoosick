package com.myinfocar.aicoachstock.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.account.AccountService
import com.myinfocar.aicoachstock.domain.account.AccountSummary
import com.myinfocar.aicoachstock.domain.account.Holding
import com.myinfocar.aicoachstock.domain.briefing.BriefingEvent
import com.myinfocar.aicoachstock.domain.briefing.MarketBriefingService
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.repository.WatchListEntry
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import com.myinfocar.aicoachstock.domain.stockinfo.StockInfoService
import com.myinfocar.aicoachstock.data.remote.kis.dto.DividendItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val krHoldings: List<Holding> = emptyList(),
    val krSummary: AccountSummary? = null,
    val watchEntries: List<WatchListEntry> = emptyList(),
    val watchTicks: Map<String, MarketTick> = emptyMap(),
    val upcomingDividends: List<DividendItem> = emptyList(),
    val briefingPhase: String? = null,
    val briefingStreaming: String = "",
    val briefingResult: String? = null,
    val briefingError: String? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountService: AccountService,
    private val stockInfoService: StockInfoService,
    private val marketDataSource: MarketDataSource,
    private val watchListRepo: WatchListRepository,
    private val briefingService: MarketBriefingService,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_ui.value.isLoading) return
        _ui.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val entries = watchListRepo.observe().first()
            _ui.update { it.copy(watchEntries = entries) }

            val balance = accountService.fetchKrBalance()
            val holdings = balance.getOrNull()?.first ?: emptyList()
            val summary = balance.getOrNull()?.second
            _ui.update {
                it.copy(
                    krHoldings = holdings,
                    krSummary = summary,
                    errorMessage = balance.exceptionOrNull()?.message,
                )
            }

            val tickerSet = holdings.map { it.ticker }.toSet()
            val dividends = if (tickerSet.isNotEmpty()) {
                stockInfoService.fetchDividends(fromDays = 0, toDays = 90)
                    .filter { it.shtCd in tickerSet }
                    .take(5)
            } else emptyList()
            _ui.update { it.copy(upcomingDividends = dividends) }

            // 관심 가격은 WatchListViewModel이 30초마다 폴링하므로 여기선 미니뷰용 상위 5개만 한 번 채움.
            val ticks = mutableMapOf<String, MarketTick>()
            for (e in entries.take(5)) {
                val market = e.stock?.market ?: Market.KR
                marketDataSource.fetchClosePrice(e.item.ticker, market).getOrNull()?.let {
                    ticks[e.item.ticker] = it
                }
            }
            _ui.update { it.copy(isLoading = false, watchTicks = ticks) }
        }
    }

    fun generateBriefing() {
        if (_ui.value.isGenerating) return
        _ui.update {
            it.copy(
                isGenerating = true,
                briefingPhase = null,
                briefingStreaming = "",
                briefingResult = null,
                briefingError = null,
            )
        }
        viewModelScope.launch {
            briefingService.generate().collect { ev ->
                when (ev) {
                    is BriefingEvent.Gathering -> _ui.update { it.copy(briefingPhase = ev.message) }
                    BriefingEvent.LoadingModel -> _ui.update { it.copy(briefingPhase = "Gemma 4 E4B 로드 중… (약 10초)") }
                    is BriefingEvent.Streaming -> _ui.update {
                        it.copy(briefingPhase = null, briefingStreaming = ev.partial)
                    }
                    is BriefingEvent.Completed -> _ui.update {
                        it.copy(isGenerating = false, briefingStreaming = "", briefingResult = ev.text)
                    }
                    is BriefingEvent.Failed -> _ui.update {
                        it.copy(isGenerating = false, briefingStreaming = "", briefingError = ev.cause.message ?: "실패")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStock: (String) -> Unit,
    onOpenHoldings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AICoachStock") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BriefingCard(state, onGenerate = viewModel::generateBriefing) }
            item { AssetSummaryCard(state.krSummary, onOpenHoldings) }
            if (state.upcomingDividends.isNotEmpty()) {
                item { DividendCard(state.upcomingDividends) }
            }
            item { Text("📊 내 보유", style = MaterialTheme.typography.titleMedium) }
            if (state.krHoldings.isEmpty()) {
                item {
                    Text(
                        "보유 종목 없음",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            } else {
                items(state.krHoldings.take(5), key = { it.ticker }) { h ->
                    HoldingMiniCard(h, onClick = { onOpenStock(h.ticker) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👀 관심 종목", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onOpenWatchlist) { Text("전체") }
                }
            }
            if (state.watchEntries.isEmpty()) {
                item {
                    Text(
                        "관심 종목 없음 — 관심 탭에서 추가하세요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            } else {
                items(state.watchEntries.take(5), key = { it.item.id }) { entry ->
                    WatchMiniCard(
                        entry = entry,
                        tick = state.watchTicks[entry.item.ticker],
                        onClick = { onOpenStock(entry.item.ticker) },
                    )
                }
            }
            state.errorMessage?.let {
                item {
                    Text("⚠ $it", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun BriefingCard(state: HomeUiState, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "🌅 오늘 AI 코칭 브리핑",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "한투 실데이터 (외국인·기관·예수금·내 보유·관심) + 활성 원칙을 종합한 오늘의 한 줄.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onGenerate, enabled = !state.isGenerating, modifier = Modifier.fillMaxWidth()) {
                if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(if (state.briefingResult == null) "브리핑 생성" else "다시 생성")
            }
            state.briefingPhase?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val display = state.briefingResult ?: state.briefingStreaming.takeIf { it.isNotBlank() }
            display?.let {
                Card {
                    Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.briefingError?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AssetSummaryCard(summary: AccountSummary?, onClick: () -> Unit) {
    val s = summary
    val pnlColor = com.myinfocar.aicoachstock.ui.common.pnlColor(s?.unrealizedPnl)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💼 내 자산 (한투 계좌)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s == null) {
                Text(
                    "한투 키 / 계좌번호를 설정하면 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "%,d원".format(s.totalEvaluation.toLong()),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "%+,d원  (%+.2f%%)".format(s.unrealizedPnl.toLong(), s.unrealizedPnlRate),
                    style = MaterialTheme.typography.titleMedium,
                    color = pnlColor,
                )
                s.cashDeposit?.let {
                    Text("예수금 ${"%,d".format(it.toLong())}원", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DividendCard(items: List<DividendItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💰 다가오는 배당 (보유 종목)", style = MaterialTheme.typography.titleMedium)
            items.forEach { d ->
                Row {
                    Text(d.recordDate.orEmpty(), modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                    Text(d.isinName.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${d.perStoDiviAmt ?: "-"}원", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HoldingMiniCard(h: Holding, onClick: () -> Unit) {
    val pnlColor = com.myinfocar.aicoachstock.ui.common.pnlColor(h.unrealizedPnl)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(h.name.ifBlank { h.ticker }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${h.qty}주 / 평단 ${h.avgBuyPrice.toLong()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%,d원".format(h.evaluationAmount.toLong()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("%+.2f%%".format(h.unrealizedPnlRate), color = pnlColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WatchMiniCard(entry: WatchListEntry, tick: MarketTick?, onClick: () -> Unit) {
    val market = entry.stock?.market ?: Market.KR
    val pctColor = com.myinfocar.aicoachstock.ui.common.pnlColor(tick?.changePct)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.stock?.nameKo ?: entry.item.ticker, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(entry.item.ticker, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                tick?.let {
                    val priceText = when (market) {
                        Market.KR -> "%,d원".format(it.price.toLong())
                        Market.US -> "$${"%.2f".format(it.price)}"
                    }
                    Text(priceText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = pctColor)
                    Text("%+.2f%%".format(it.changePct ?: 0.0), color = pctColor, style = MaterialTheme.typography.bodyMedium)
                } ?: Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

