package com.myinfocar.aicoachstock.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.PrimaryButton
import com.myinfocar.aicoachstock.ui.common.StockRow
import com.myinfocar.aicoachstock.ui.common.pnlColor
import com.myinfocar.aicoachstock.ui.watchlist.label
import com.myinfocar.aicoachstock.ui.theme.AppTokens
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("AICoachStock", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = AppTokens.space16,
                vertical = AppTokens.space12,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTokens.space12),
        ) {
            item { BriefingCard(state, onGenerate = viewModel::generateBriefing) }
            item { AssetSummaryCard(state.krSummary, onOpenHoldings) }
            if (state.upcomingDividends.isNotEmpty()) {
                item { DividendCard(state.upcomingDividends) }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AppTokens.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "내 보유",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenHoldings) { Text("전체") }
                }
            }
            if (state.krHoldings.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            "보유 종목 없음 — 한투 키를 설정하면 표시됩니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.krHoldings.take(5), key = { it.ticker }) { h ->
                    HoldingMiniCard(h, onClick = { onOpenStock(h.ticker) })
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AppTokens.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "관심 종목",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenWatchlist) { Text("전체") }
                }
            }
            if (state.watchEntries.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            "관심 종목 없음 — 관심 탭에서 추가하세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.watchEntries.take(5), key = { it.item.id }) { entry ->
                    WatchMiniRow(
                        entry = entry,
                        tick = state.watchTicks[entry.item.ticker],
                        onClick = { onOpenStock(entry.item.ticker) },
                    )
                }
            }
            state.errorMessage?.let {
                item {
                    AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(AppTokens.space16)) }
        }
    }
}

@Composable
private fun BriefingCard(state: HomeUiState, onGenerate: () -> Unit) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        padding = AppTokens.space20,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space12)) {
            Text(
                "오늘 AI 코칭 브리핑",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "외국인·기관·예수금·내 보유·관심 + 활성 원칙을 종합한 오늘의 한 줄.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            PrimaryButton(
                text = if (state.briefingResult == null) "브리핑 생성" else "다시 생성",
                onClick = onGenerate,
                isLoading = state.isGenerating,
                enabled = !state.isGenerating,
            )
            state.briefingPhase?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            val display = state.briefingResult ?: state.briefingStreaming.takeIf { it.isNotBlank() }
            AnimatedVisibility(
                visible = display != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                display?.let {
                    AppCard(
                        containerColor = MaterialTheme.colorScheme.surface,
                        padding = AppTokens.space16,
                    ) {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.briefingError?.let {
                Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AssetSummaryCard(summary: AccountSummary?, onClick: () -> Unit) {
    val s = summary
    val color = pnlColor(s?.unrealizedPnl)
    AppCard(
        onClick = onClick,
        padding = AppTokens.space20,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Text(
                "내 자산",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s == null) {
                Text(
                    "한투 키 / 계좌번호를 설정하면 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "%,d원".format(s.totalEvaluation.toLong()),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    "%+,d원  (%+.2f%%)".format(s.unrealizedPnl.toLong(), s.unrealizedPnlRate),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                s.cashDeposit?.let {
                    Text(
                        "예수금 ${"%,d".format(it.toLong())}원",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DividendCard(items: List<DividendItem>) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space8)) {
            Text("다가오는 배당", style = MaterialTheme.typography.titleMedium)
            items.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.recordDate.orEmpty(),
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        d.isinName.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${d.perStoDiviAmt ?: "-"}원",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingMiniCard(h: Holding, onClick: () -> Unit) {
    val color = pnlColor(h.unrealizedPnl)
    AppCard(onClick = onClick, padding = AppTokens.space16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    h.name.ifBlank { h.ticker },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${h.qty}주 · 평단 ${"%,d".format(h.avgBuyPrice.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%,d원".format(h.evaluationAmount.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "%+.2f%%".format(h.unrealizedPnlRate),
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun WatchMiniRow(entry: WatchListEntry, tick: MarketTick?, onClick: () -> Unit) {
    val market = entry.stock?.market ?: Market.KR
    AppCard(padding = 0.dp) {
        StockRow(
            name = entry.stock?.nameKo ?: entry.item.ticker,
            ticker = entry.item.ticker,
            market = market,
            price = tick?.price,
            changePct = tick?.changePct,
            exchangeLabel = entry.stock?.exchange?.label(),
            onClick = onClick,
        )
    }
}
