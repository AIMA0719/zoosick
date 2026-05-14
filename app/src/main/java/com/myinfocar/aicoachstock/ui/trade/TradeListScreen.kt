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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.EmptyState
import com.myinfocar.aicoachstock.ui.common.KrDownBlue
import com.myinfocar.aicoachstock.ui.common.KrUpRed
import com.myinfocar.aicoachstock.ui.common.ListLoadingSkeleton
import com.myinfocar.aicoachstock.ui.common.pnlColor
import com.myinfocar.aicoachstock.ui.theme.AppTokens
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
                        onSuccess = { s -> "동기화: 신규 ${s.inserted}건 (${s.range.first}~${s.range.second})" },
                        onFailure = { e -> "동기화 실패: ${e.message}" },
                    ),
                )
            }
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
    val profit by viewModel.profit.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(syncState.message) {
        val msg = syncState.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.dismissSyncMessage()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("매매기록", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = viewModel::syncNow, enabled = !syncState.isSyncing) {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "한투 체결 동기화")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = "매매기록 추가")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> ListLoadingSkeleton(modifier = Modifier.padding(padding))

            state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                profit?.let {
                    Box(modifier = Modifier.padding(horizontal = AppTokens.space16, vertical = AppTokens.space12)) {
                        ProfitSummaryCard(it)
                    }
                }
                EmptyState(
                    title = "아직 기록된 매매가 없어요",
                    description = "+ 버튼으로 체결한 매매를 기록하거나, 우상단 🔄 로 한투 체결을 가져오세요.",
                    icon = "📝",
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = AppTokens.space16,
                    vertical = AppTokens.space12,
                ),
                verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                profit?.let {
                    item { ProfitSummaryCard(it) }
                }
                items(state.items, key = { it.id }) { trade ->
                    TradeCard(
                        trade = trade,
                        onClick = { onEditClick(trade.id) },
                        onReflect = { onReflectClick(trade.id) },
                    )
                }
                item { Spacer(Modifier.height(AppTokens.space24)) }
            }
        }
    }
}

@Composable
private fun ProfitSummaryCard(profit: PeriodProfitTotal) {
    val color = pnlColor(profit.totalRealizedPnl)
    AppCard(padding = AppTokens.space20) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Text(
                "최근 30일 실현손익",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${profit.rangeStart} ~ ${profit.rangeEnd}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppTokens.space4))
            Text(
                "${"%+,d".format(profit.totalRealizedPnl.toLong())}원",
                style = MaterialTheme.typography.displaySmall,
                color = color,
            )
            Text(
                "${"%+.2f".format(profit.pnlRate)}%",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppTokens.space4))
            Text(
                "매수 ${"%,d".format(profit.totalBuy.toLong())}원 · 매도 ${"%,d".format(profit.totalSell.toLong())}원 · 수수료 ${"%,d".format(profit.totalFee.toLong())}원",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeCard(trade: Trade, onClick: () -> Unit, onReflect: () -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
        .withZone(ZoneId.systemDefault())
    val moneyFormat = remember(trade.market) {
        when (trade.market) {
            Market.KR -> NumberFormat.getNumberInstance(Locale.KOREA)
            Market.US -> NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
        }
    }

    AppCard(onClick = onClick, padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SideBadge(side = trade.side)
                Spacer(Modifier.width(AppTokens.space8))
                Text(
                    trade.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(AppTokens.space8))
                Text(
                    trade.market.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trade.isImported) {
                    Spacer(Modifier.width(AppTokens.space4))
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = "한투 자동 import",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    dateFormatter.format(trade.executedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${moneyFormat.format(trade.price)} × ${trade.qty}주",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    moneyFormat.format(trade.price * trade.qty),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!trade.reasonText.isNullOrBlank()) {
                Text(
                    trade.reasonText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "감정 ${trade.emotionTag.label()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReflect) {
                    Text("AI 복기")
                }
            }
        }
    }
}

@Composable
private fun SideBadge(side: TradeSide) {
    val bg = when (side) {
        TradeSide.BUY -> KrUpRed
        TradeSide.SELL -> KrDownBlue
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(AppTokens.radius8))
            .padding(horizontal = AppTokens.space8, vertical = 2.dp),
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
