package com.myinfocar.aicoachstock.ui.trade

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
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
import com.myinfocar.aicoachstock.domain.account.PeriodProfitTotal
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import com.myinfocar.aicoachstock.domain.sync.TradeImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class TradeListUiState(
    val items: List<Trade> = emptyList(),
    val isLoading: Boolean = true,
)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class TradeListViewModel @Inject constructor(
    repo: TradeRepository,
    private val importService: TradeImportService,
    private val accountService: AccountService,
) : ViewModel() {
    val uiState: StateFlow<TradeListUiState> = repo.observeAll()
        .map { TradeListUiState(items = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TradeListUiState(),
        )

    private val _sync = MutableStateFlow(SyncUiState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    private val _profit = MutableStateFlow<PeriodProfitTotal?>(null)
    val profit: StateFlow<PeriodProfitTotal?> = _profit.asStateFlow()

    init {
        viewModelScope.launch {
            // 실패는 무시 — 누적 손익은 보조 정보.
            accountService.fetchPeriodProfit(30).getOrNull()?.let { _profit.value = it }
        }
    }

    fun syncNow() {
        if (_sync.value.isSyncing) return
        _sync.update { it.copy(isSyncing = true, message = null) }
        viewModelScope.launch {
            val result = importService.importRecent()
            _sync.update {
                it.copy(
                    isSyncing = false,
                    message = result.fold(
                        onSuccess = { s -> "✅ 동기화: 신규 ${s.inserted}건 (${s.range.first}~${s.range.second})" },
                        onFailure = { e -> "❌ ${e.message}" },
                    ),
                )
            }
            // 동기화 후 손익도 갱신.
            accountService.fetchPeriodProfit(30).getOrNull()?.let { _profit.value = it }
        }
    }

    fun dismissSyncMessage() {
        _sync.update { it.copy(message = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeListScreen(
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onReflectClick: (String) -> Unit,
    viewModel: TradeListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val syncState by viewModel.sync.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(syncState.message) {
        val msg = syncState.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.dismissSyncMessage()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("매매기록") },
                actions = {
                    IconButton(onClick = viewModel::syncNow, enabled = !syncState.isSyncing) {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "한투 체결 동기화")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "매매기록 추가")
            }
        },
    ) { padding ->
        val profit by viewModel.profit.collectAsState()
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            profit?.let { ProfitSummaryCard(it) }
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.items.isEmpty() -> EmptyState(modifier = Modifier)

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { trade ->
                        TradeCard(
                            trade = trade,
                            onClick = { onEditClick(trade.id) },
                            onReflect = { onReflectClick(trade.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitSummaryCard(profit: PeriodProfitTotal) {
    val pnlColor = com.myinfocar.aicoachstock.ui.common.pnlColor(profit.totalRealizedPnl)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "최근 30일 실현손익  (${profit.rangeStart} ~ ${profit.rangeEnd})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${"%+,d".format(profit.totalRealizedPnl.toLong())}원  (${"%+.2f".format(profit.pnlRate)}%)",
                style = MaterialTheme.typography.headlineSmall,
                color = pnlColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "매수 ${"%,d".format(profit.totalBuy.toLong())}원  ·  매도 ${"%,d".format(profit.totalSell.toLong())}원  ·  수수료 ${"%,d".format(profit.totalFee.toLong())}원",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("아직 기록된 매매가 없어요.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "+ 버튼으로 체결한 매매를 기록해보세요.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeCard(trade: Trade, onClick: () -> Unit, onReflect: () -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    val moneyFormat = remember(trade.market) {
        when (trade.market) {
            Market.KR -> NumberFormat.getNumberInstance(Locale.KOREA)
            Market.US -> NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
        }
    }

    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SideBadge(side = trade.side)
                Spacer(Modifier.width(8.dp))
                Text(
                    trade.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(trade.market.label()) },
                    enabled = false,
                )
                if (trade.isImported) {
                    Spacer(Modifier.width(4.dp))
                    AssistChip(
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = "한투 자동 import",
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        label = { Text("자동") },
                        enabled = false,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    dateFormatter.format(trade.executedAt),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${moneyFormat.format(trade.price)} × ${trade.qty}주",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    moneyFormat.format(trade.price * trade.qty),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (trade.reasonText != null && trade.reasonText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    trade.reasonText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "감정: ${trade.emotionTag.label()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = onReflect) {
                    Text("🤖 AI 복기")
                }
            }
        }
    }
}

@Composable
private fun SideBadge(side: TradeSide) {
    val bg = when (side) {
        TradeSide.BUY -> Color(0xFFE53935)
        TradeSide.SELL -> Color(0xFF1E88E5)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            side.label(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun <T> remember(key: T, calculation: () -> NumberFormat): NumberFormat =
    androidx.compose.runtime.remember(key) { calculation() }
