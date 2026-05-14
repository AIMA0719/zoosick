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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.pnlColor
import com.myinfocar.aicoachstock.ui.theme.AppTokens
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("보유 종목", style = MaterialTheme.typography.titleLarge) },
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
                colors = TopAppBarDefaults.topAppBarColors(
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
            verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Market.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = state.market == m,
                            onClick = { viewModel.setMarket(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, Market.entries.size),
                        ) { Text(if (m == Market.KR) "국내" else "해외") }
                    }
                }
            }
            state.summary?.let { item { SummaryCard(it) } }

            when {
                state.isLoading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppTokens.space24),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                state.errorMessage != null -> item {
                    AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "❌ ${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                state.holdings.isEmpty() -> item {
                    AppCard {
                        Text(
                            "보유 종목이 없습니다.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> items(state.holdings, key = { "${it.market}-${it.ticker}" }) { h ->
                    HoldingCard(h)
                }
            }
            item { Spacer(Modifier.height(AppTokens.space16)) }
        }
    }
}

@Composable
private fun SummaryCard(s: AccountSummary) {
    val color = pnlColor(s.unrealizedPnl)
    AppCard(padding = AppTokens.space20) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Text(
                "총 평가금액",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMoney(s.totalEvaluation, s.currencyCode),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(AppTokens.space4))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.space16)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "매입금액",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatMoney(s.totalBuyAmount, s.currencyCode),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "평가손익",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${formatMoney(s.unrealizedPnl, s.currencyCode)}  ${"%+.2f".format(s.unrealizedPnlRate)}%",
                        color = color,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            s.cashDeposit?.let {
                Text(
                    "예수금 ${formatMoney(it, s.currencyCode)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HoldingCard(h: Holding) {
    val color = pnlColor(h.unrealizedPnl)
    AppCard(padding = AppTokens.space16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    h.name.ifBlank { h.ticker },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${h.ticker}  ·  ${h.qty}주  ·  평단 ${formatPrice(h.avgBuyPrice, h.currencyCode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(h.evaluationAmount, h.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatMoney(h.unrealizedPnl, h.currencyCode)}  ${"%+.2f".format(h.unrealizedPnlRate)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
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
