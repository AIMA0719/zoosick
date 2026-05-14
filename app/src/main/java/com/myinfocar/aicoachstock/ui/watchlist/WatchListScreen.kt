package com.myinfocar.aicoachstock.ui.watchlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.SkeletonShimmer
import com.myinfocar.aicoachstock.ui.common.StockRow
import com.myinfocar.aicoachstock.ui.theme.AppTokens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.market.MarketHours
import com.myinfocar.aicoachstock.domain.model.Currency
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.repository.WatchListEntry
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchListUiState(
    val entries: List<WatchListEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class WatchListViewModel @Inject constructor(
    private val repo: WatchListRepository,
    private val marketDataSource: MarketDataSource,
) : ViewModel() {

    val uiState: StateFlow<WatchListUiState> = repo.observe()
        .map { WatchListUiState(entries = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchListUiState(),
        )

    /** ticker → 최신 MarketTick. 폴링으로 갱신. */
    private val _ticks = MutableStateFlow<Map<String, MarketTick>>(emptyMap())
    val ticks: StateFlow<Map<String, MarketTick>> = _ticks.asStateFlow()

    init {
        // 신규 ticker가 들어오면 장 시간 무관 즉시 1회 fetch (REST는 비장시간에도 전일 종가 반환).
        viewModelScope.launch {
            var prev: Set<String> = emptySet()
            uiState.collect { state ->
                val curr = state.entries.map { it.item.ticker }.toSet()
                val added = curr - prev
                val removed = prev - curr
                if (added.isNotEmpty()) {
                    refreshTicks(state.entries.filter { it.item.ticker in added })
                }
                if (removed.isNotEmpty()) {
                    _ticks.update { it.filterKeys { k -> k in curr } }
                }
                prev = curr
            }
        }
        // 장 열림 시 30초 폴링으로 전체 갱신.
        viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val entries = uiState.value.entries
                if (entries.isNotEmpty() && MarketHours.anyOpen()) {
                    refreshTicks(entries)
                }
            }
        }
    }

    private suspend fun refreshTicks(entries: List<WatchListEntry>) {
        val updates = mutableMapOf<String, MarketTick>()
        for (e in entries) {
            val market = e.stock?.market ?: Market.KR
            val tick = marketDataSource.fetchClosePrice(e.item.ticker, market).getOrNull()
            if (tick != null) updates[e.item.ticker] = tick
        }
        if (updates.isEmpty()) return
        _ticks.update { current ->
            val merged = current.toMutableMap()
            var changed = false
            for ((t, v) in updates) {
                if (merged[t]?.price != v.price || merged[t]?.changePct != v.changePct) {
                    merged[t] = v
                    changed = true
                }
            }
            if (changed) merged else current
        }
    }

    /** 사용자가 당겨서 새로고침할 때 호출. */
    fun refresh() {
        viewModelScope.launch {
            val entries = uiState.value.entries
            if (entries.isNotEmpty()) refreshTicks(entries)
        }
    }

    fun add(ticker: String, name: String, market: Market, note: String?) {
        viewModelScope.launch {
            val currency = if (market == Market.KR) Currency.KRW else Currency.USD
            val defaultStock = Stock(
                ticker = ticker,
                nameKo = name.ifBlank { ticker },
                nameEn = null,
                exchange = market.defaultExchange(),
                sector = null,
                currency = currency,
            )
            repo.add(ticker = ticker, note = note?.ifBlank { null }, defaultStock = defaultStock)
        }
    }

    fun updateNote(id: String, note: String?) {
        viewModelScope.launch { repo.updateNote(id, note?.ifBlank { null }) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(
    onSearchClick: () -> Unit = {},
    onItemClick: (String) -> Unit = {},
    viewModel: WatchListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val ticks by viewModel.ticks.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WatchListEntry?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "관심종목 ${if (state.entries.isNotEmpty()) state.entries.size else ""}".trim(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "종목 검색")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "관심종목 추가")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingSkeleton(modifier = Modifier.padding(padding))
            state.entries.isEmpty() -> EmptyState(modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = AppTokens.space16,
                    vertical = AppTokens.space12,
                ),
                verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(state.entries, key = { it.item.id }) { entry ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                        exit = fadeOut(),
                    ) {
                        WatchListCard(
                            entry = entry,
                            tick = ticks[entry.item.ticker],
                            onClick = { onItemClick(entry.item.ticker) },
                            onLongClick = { editingEntry = entry },
                            onDelete = { viewModel.remove(entry.item.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWatchDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { ticker, name, market, note ->
                viewModel.add(ticker, name, market, note)
                showAddDialog = false
            },
        )
    }

    editingEntry?.let { entry ->
        EditNoteDialog(
            initialNote = entry.item.note.orEmpty(),
            onDismiss = { editingEntry = null },
            onSubmit = { newNote ->
                viewModel.updateNote(entry.item.id, newNote)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppTokens.space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("관심 가는 종목을 모아두세요", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(AppTokens.space8))
        Text(
            "상단 + 버튼이나 🔍 검색으로 추가하면 현재가가 실시간으로 표시됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.space16, vertical = AppTokens.space12),
        verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
    ) {
        repeat(5) {
            SkeletonShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                cornerRadius = AppTokens.radius16,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchListCard(
    entry: WatchListEntry,
    tick: MarketTick?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val market = entry.stock?.market ?: Market.KR
    AppCard(padding = 0.dp) {
        Column {
            StockRow(
                name = entry.stock?.nameKo ?: entry.item.ticker,
                ticker = entry.item.ticker,
                market = market,
                price = tick?.price,
                changePct = tick?.changePct,
                exchangeLabel = entry.stock?.exchange?.label(),
                onClick = onClick,
                onLongClick = onLongClick,
                trailing = {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            if (!entry.item.note.isNullOrBlank()) {
                Text(
                    entry.item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppTokens.space16,
                            end = AppTokens.space16,
                            bottom = AppTokens.space12,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWatchDialog(
    onDismiss: () -> Unit,
    onSubmit: (ticker: String, name: String, market: Market, note: String?) -> Unit,
) {
    var ticker by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var market by rememberSaveable { mutableStateOf(Market.KR) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관심종목 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Market.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = market == m,
                            onClick = { market = m },
                            shape = SegmentedButtonDefaults.itemShape(i, Market.entries.size),
                        ) { Text(m.label()) }
                    }
                }
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase().trim(); error = null },
                    label = { Text("종목코드") },
                    placeholder = { Text(if (market == Market.KR) "예: 005930" else "예: NVDA") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("종목명 (선택)") },
                    placeholder = { Text(if (market == Market.KR) "삼성전자" else "NVIDIA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("메모 (선택)") },
                    placeholder = { Text("예: 차트 30일선 돌파 시 진입 검토") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (ticker.isBlank()) {
                    error = "종목코드를 입력해주세요"
                } else {
                    onSubmit(ticker, name, market, note)
                }
            }) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var note by rememberSaveable(initialNote) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모 편집") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("메모") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(note) }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
