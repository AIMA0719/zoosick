package com.myinfocar.aicoachstock.ui.watchlist

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
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
        viewModelScope.launch {
            while (true) {
                val entries = uiState.value.entries
                if (entries.isNotEmpty() && MarketHours.anyOpen()) {
                    val activeTickers = entries.map { it.item.ticker }.toSet()
                    val updates = mutableMapOf<String, MarketTick>()
                    for (e in entries) {
                        val market = e.stock?.market ?: Market.KR
                        val tick = marketDataSource.fetchClosePrice(e.item.ticker, market).getOrNull()
                        if (tick != null) updates[e.item.ticker] = tick
                    }
                    _ticks.update { current ->
                        // 1) 더 이상 관심 목록에 없는 ticker 제거 (메모리 누수 방지)
                        // 2) 값이 실제로 바뀐 ticker만 반영 (불필요한 recomposition 방지)
                        val pruned = current.filterKeys { it in activeTickers }
                        val merged = pruned.toMutableMap()
                        var changed = pruned.size != current.size
                        for ((t, v) in updates) {
                            if (merged[t]?.price != v.price || merged[t]?.changePct != v.changePct) {
                                merged[t] = v
                                changed = true
                            }
                        }
                        if (changed) merged else current
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
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
        topBar = {
            TopAppBar(
                title = { Text("관심종목") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "종목 검색")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "관심종목 추가")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.entries.isEmpty() -> EmptyState(modifier = Modifier.padding(padding))

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(state.entries, key = { it.item.id }) { entry ->
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
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("아직 관심종목이 없어요.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "+ 버튼으로 관심 가는 종목을 추가하세요. (한투 검색 연동은 다음 단계)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WatchListCard(
    entry: WatchListEntry,
    tick: MarketTick?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.item.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                entry.stock?.let { stock ->
                    Text(
                        stock.nameKo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(stock.exchange.label()) },
                        enabled = false,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", modifier = Modifier.size(20.dp))
                }
            }
            tick?.let {
                Spacer(Modifier.height(6.dp))
                val market = entry.stock?.market ?: Market.KR
                val priceText = when (market) {
                    Market.KR -> "%,d원".format(it.price.toLong())
                    Market.US -> "$${"%.2f".format(it.price)}"
                }
                val pctColor = com.myinfocar.aicoachstock.ui.common.pnlColor(it.changePct)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        priceText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = pctColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${"%+.2f".format(it.changePct ?: 0.0)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pctColor,
                    )
                }
            }
            if (!entry.item.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    entry.item.note,
                    style = MaterialTheme.typography.bodyMedium,
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
