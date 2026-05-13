package com.myinfocar.aicoachstock.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchHit> = emptyList(),
    val errorMessage: String? = null,
    val addedTicker: String? = null,
)

data class SearchHit(
    val stock: Stock,
    val tick: MarketTick?,
)

@HiltViewModel
class StockSearchViewModel @Inject constructor(
    private val marketDataSource: MarketDataSource,
    private val stockRepo: StockRepository,
    private val watchListRepo: WatchListRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(StockSearchUiState())
    val ui: StateFlow<StockSearchUiState> = _ui.asStateFlow()

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q.uppercase()) }
    }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isEmpty() || _ui.value.isSearching) return
        viewModelScope.launch {
            _ui.update { it.copy(isSearching = true, errorMessage = null, results = emptyList(), addedTicker = null) }
            val result = marketDataSource.searchStocks(q)
            result.fold(
                onSuccess = { stocks ->
                    val hits = stocks.map { s ->
                        val tick = marketDataSource.fetchClosePrice(s.ticker, s.market).getOrNull()
                        SearchHit(s, tick)
                    }
                    _ui.update { it.copy(isSearching = false, results = hits) }
                },
                onFailure = { e ->
                    _ui.update { it.copy(isSearching = false, errorMessage = e.message ?: "검색 실패") }
                },
            )
        }
    }

    fun addToWatchList(stock: Stock) {
        viewModelScope.launch {
            stockRepo.save(stock)
            watchListRepo.add(stock.ticker, note = null, defaultStock = stock)
            _ui.update { it.copy(addedTicker = stock.ticker) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockSearchScreen(
    onBack: () -> Unit,
    viewModel: StockSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("종목 검색 (한투 REST)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("종목코드") },
                    placeholder = { Text("예: 005930 또는 NVDA") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = viewModel::search,
                    enabled = !state.isSearching && state.query.isNotBlank(),
                ) {
                    if (state.isSearching) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Icon(Icons.Default.Search, contentDescription = "검색")
                }
            }

            Text(
                "한투 REST 단일 조회 기반. 6자리 숫자 → 국내, 알파벳 → 미국으로 시도합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let { msg ->
                Text("❌ $msg", color = MaterialTheme.colorScheme.error)
            }
            state.addedTicker?.let { ticker ->
                Text("✅ $ticker 관심종목 추가 완료", color = MaterialTheme.colorScheme.primary)
            }

            if (state.isSearching) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.results.isEmpty() && !state.isSearching && state.query.isNotBlank() && state.errorMessage == null && state.addedTicker == null) {
                Text(
                    "🔍 위에 종목코드를 입력하고 검색을 누르세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { "${it.stock.ticker}-${it.stock.exchange}" }) { hit ->
                        ResultCard(hit = hit, onAdd = { viewModel.addToWatchList(hit.stock) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultCard(hit: SearchHit, onAdd: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.stock.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(8.dp))
                Text(hit.stock.nameKo, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(hit.stock.exchange.name) }, enabled = false)
            }
            Spacer(Modifier.height(4.dp))
            val priceText = when (hit.stock.market) {
                Market.KR -> hit.tick?.price?.toLong()?.let { "${"%,d".format(it)}원" } ?: "—"
                Market.US -> hit.tick?.price?.let { "$${"%.2f".format(it)}" } ?: "—"
            }
            Text(
                "현재가 $priceText" + (hit.tick?.changePct?.let { "  ·  ${"%+.2f".format(it)}%" }.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd) { Text("관심종목에 추가") }
        }
    }
}
