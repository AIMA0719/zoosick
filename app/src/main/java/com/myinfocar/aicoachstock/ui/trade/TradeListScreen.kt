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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class TradeListUiState(
    val items: List<Trade> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TradeListViewModel @Inject constructor(
    repo: TradeRepository,
) : ViewModel() {
    val uiState: StateFlow<TradeListUiState> = repo.observeAll()
        .map { TradeListUiState(items = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TradeListUiState(),
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeListScreen(
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onReflectClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: TradeListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("매매기록") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
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
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.items.isEmpty() -> EmptyState(modifier = Modifier.padding(padding))

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
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
