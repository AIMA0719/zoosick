package com.myinfocar.aicoachstock.ui.holdings

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.myinfocar.aicoachstock.domain.model.Market
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HoldingsUiState(
    val market: Market = Market.KR,
    val isLoading: Boolean = false,
    val holdings: List<Holding> = emptyList(),
    val summary: AccountSummary? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HoldingsViewModel @Inject constructor(
    private val accountService: AccountService,
) : ViewModel() {

    private val _ui = MutableStateFlow(HoldingsUiState())
    val ui: StateFlow<HoldingsUiState> = _ui.asStateFlow()

    fun setMarket(m: Market) {
        if (_ui.value.market == m) return
        _ui.update { it.copy(market = m, holdings = emptyList(), summary = null, errorMessage = null) }
        refresh()
    }

    fun refresh() {
        if (_ui.value.isLoading) return
        _ui.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = when (_ui.value.market) {
                Market.KR -> accountService.fetchKrBalance()
                Market.US -> accountService.fetchUsBalance()
            }
            result.fold(
                onSuccess = { (h, s) ->
                    _ui.update { it.copy(isLoading = false, holdings = h, summary = s) }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "조회 실패")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsScreen(
    onBack: () -> Unit,
    viewModel: HoldingsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) { if (state.holdings.isEmpty() && state.errorMessage == null) viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("보유 종목") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Market.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = state.market == m,
                        onClick = { viewModel.setMarket(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Market.entries.size),
                    ) { Text(if (m == Market.KR) "국내" else "해외") }
                }
            }

            state.summary?.let { SummaryCard(it) }

            state.errorMessage?.let { msg ->
                Text(
                    "❌ $msg",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.holdings.isEmpty() && state.errorMessage == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "보유 종목이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.holdings, key = { "${it.market}-${it.ticker}" }) { h ->
                        HoldingCard(h)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(s: AccountSummary) {
    val pnlColor = com.myinfocar.aicoachstock.ui.common.pnlColor(s.unrealizedPnl)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "총 평가금액",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMoney(s.totalEvaluation, s.currencyCode),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("매입금액", style = MaterialTheme.typography.labelSmall)
                    Text(formatMoney(s.totalBuyAmount, s.currencyCode))
                }
                Column {
                    Text("평가손익", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${formatMoney(s.unrealizedPnl, s.currencyCode)} (${"%+.2f".format(s.unrealizedPnlRate)}%)",
                        color = pnlColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            s.cashDeposit?.let {
                Text("예수금: ${formatMoney(it, s.currencyCode)}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HoldingCard(h: Holding) {
    val pnlColor = com.myinfocar.aicoachstock.ui.common.pnlColor(h.unrealizedPnl)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(8.dp))
                Text(h.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(4.dp))
                h.exchangeCode?.let {
                    AssistChip(onClick = {}, label = { Text(it) }, enabled = false)
                }
            }
            Text(
                "${h.qty}주  ·  평단 ${formatPrice(h.avgBuyPrice, h.currencyCode)}  ·  현재 ${formatPrice(h.currentPrice, h.currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "평가 ${formatMoney(h.evaluationAmount, h.currencyCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${formatMoney(h.unrealizedPnl, h.currencyCode)} (${"%+.2f".format(h.unrealizedPnlRate)}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pnlColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun formatMoney(value: Double, currency: String): String = when (currency) {
    "USD" -> "$${"%,.2f".format(value)}"
    else -> "%,d원".format(value.toLong())
}

private fun formatPrice(value: Double, currency: String): String = when (currency) {
    "USD" -> "$${"%.2f".format(value)}"
    else -> "%,d".format(value.toLong())
}
