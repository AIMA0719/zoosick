package com.myinfocar.aicoachstock.ui.search

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.Stock
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.WatchListRepository
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.EmptyState
import com.myinfocar.aicoachstock.ui.common.pnlColor
import com.myinfocar.aicoachstock.ui.theme.AppTokens
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
        // uppercase 자동 변환 제거 — 한글 입력 지원. 마스터 검색은 LIKE라 대소문자 영향 적음.
        _ui.update { it.copy(query = q) }
    }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isEmpty() || _ui.value.isSearching) return
        viewModelScope.launch {
            _ui.update { it.copy(isSearching = true, errorMessage = null, results = emptyList(), addedTicker = null) }
            val result = marketDataSource.searchStocks(q)
            result.fold(
                onSuccess = { stocks ->
                    // 1차: 가격 없이 즉시 표시 (검색 결과 50개까지 빨리 보이도록).
                    val initialHits = stocks.map { SearchHit(it, tick = null) }
                    _ui.update { it.copy(isSearching = false, results = initialHits) }

                    // 2차: 상위 10개에 한해서만 현재가 비동기 fetch — 한투 단일조회 한도/속도 보호.
                    stocks.take(10).forEach { s ->
                        val tick = marketDataSource.fetchClosePrice(s.ticker, s.market).getOrNull()
                        if (tick != null) {
                            _ui.update { state ->
                                state.copy(
                                    results = state.results.map { hit ->
                                        if (hit.stock.ticker == s.ticker) hit.copy(tick = tick) else hit
                                    },
                                )
                            }
                        }
                    }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("종목 검색", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTokens.space16, vertical = AppTokens.space12),
            verticalArrangement = Arrangement.spacedBy(AppTokens.space12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("종목명 또는 코드") },
                    placeholder = { Text("예: 삼성전자, 애플, 005930, AAPL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(AppTokens.space8))
                Button(
                    onClick = viewModel::search,
                    enabled = !state.isSearching && state.query.isNotBlank(),
                ) {
                    if (state.isSearching) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, contentDescription = "검색")
                }
            }

            Text(
                "종목명(한글/영문)이나 코드로 검색하세요. 상위 10개 결과만 현재가를 표시합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let { msg ->
                Text(
                    "❌ $msg",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.addedTicker?.let { ticker ->
                Text(
                    "✅ $ticker 관심종목 추가 완료",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            when {
                state.isSearching -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.results.isEmpty() && state.query.isBlank() -> EmptyState(
                    title = "종목을 검색해 보세요",
                    description = "삼성전자, 애플처럼 이름으로 검색하거나 005930, AAPL 같은 코드로 찾을 수 있어요.",
                    icon = "🔍",
                )

                state.results.isEmpty() && !state.isSearching && state.errorMessage == null && state.addedTicker == null -> Text(
                    "🔍 위에 종목코드를 입력하고 검색을 누르세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
                    contentPadding = PaddingValues(bottom = AppTokens.space24),
                ) {
                    items(state.results, key = { "${it.stock.ticker}-${it.stock.exchange}" }) { hit ->
                        ResultCard(hit = hit, onAdd = { viewModel.addToWatchList(hit.stock) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(hit: SearchHit, onAdd: () -> Unit) {
    AppCard(padding = AppTokens.space16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    hit.stock.nameKo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AppTokens.space2))
                Text(
                    "${hit.stock.ticker} · ${hit.stock.exchange.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppTokens.space4))
                val priceText = when (hit.stock.market) {
                    Market.KR -> hit.tick?.price?.toLong()?.let { "${"%,d".format(it)}원" } ?: "—"
                    Market.US -> hit.tick?.price?.let { "$${"%.2f".format(it)}" } ?: "—"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        priceText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    hit.tick?.changePct?.let {
                        Spacer(Modifier.size(AppTokens.space8))
                        Text(
                            "${"%+.2f".format(it)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = pnlColor(it),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(AppTokens.touchTarget),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "관심종목에 추가",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
