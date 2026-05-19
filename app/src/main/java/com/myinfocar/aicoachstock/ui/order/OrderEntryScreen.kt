package com.myinfocar.aicoachstock.ui.order

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.market.MarketDataSource
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderType
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.order.OrderConfirmation
import com.myinfocar.aicoachstock.domain.order.OrderIntent
import com.myinfocar.aicoachstock.domain.order.OrderService
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.KrDownBlue
import com.myinfocar.aicoachstock.ui.common.KrUpRed
import com.myinfocar.aicoachstock.ui.common.PrimaryButton
import com.myinfocar.aicoachstock.ui.theme.AppTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderEntryUiState(
    val ticker: String = "",
    val market: Market = Market.KR,
    val side: TradeSide = TradeSide.BUY,
    val nameKo: String? = null,
    val currentPrice: Double? = null,
    val orderType: OrderType = OrderType.LIMIT,
    val priceText: String = "",
    val quantityText: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val submittedOrder: Order? = null,
) {
    val quantity: Int? get() = quantityText.toIntOrNull()?.takeIf { it > 0 }
    val price: Double? get() = priceText.toDoubleOrNull()?.takeIf { it > 0 }
    val expectedTotal: Double?
        get() {
            val q = quantity ?: return null
            val p = when (orderType) {
                OrderType.LIMIT -> price ?: return null
                OrderType.MARKET -> currentPrice ?: return null
            }
            return q * p
        }
    val canSubmit: Boolean
        get() = !submitting && quantity != null &&
                (orderType == OrderType.MARKET || price != null)
}

@HiltViewModel
class OrderEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockRepo: StockRepository,
    private val marketDataSource: MarketDataSource,
    private val orderService: OrderService,
) : ViewModel() {

    private val argTicker: String = checkNotNull(savedStateHandle["ticker"]) { "ticker 인자 없음" }
    private val argSide: TradeSide = runCatching {
        TradeSide.valueOf(savedStateHandle.get<String>("side") ?: "BUY")
    }.getOrDefault(TradeSide.BUY)

    private val _ui = MutableStateFlow(OrderEntryUiState(ticker = argTicker, side = argSide))
    val ui: StateFlow<OrderEntryUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val stock = stockRepo.findByTicker(argTicker)
            val market = stock?.market ?: Market.KR
            val tick = marketDataSource.fetchClosePrice(argTicker, market).getOrNull()
            _ui.update {
                it.copy(
                    market = market,
                    nameKo = stock?.nameKo,
                    currentPrice = tick?.price,
                    priceText = tick?.price?.let { p -> formatInputPrice(p, market) } ?: "",
                )
            }
        }
    }

    fun onPriceChange(value: String) {
        _ui.update { it.copy(priceText = value.filter { c -> c.isDigit() || c == '.' }) }
    }

    fun onQuantityChange(value: String) {
        _ui.update { it.copy(quantityText = value.filter { c -> c.isDigit() }) }
    }

    fun onOrderTypeChange(type: OrderType) {
        _ui.update { it.copy(orderType = type) }
    }

    /**
     * 외부(Composable)에서 BiometricPrompt 통과 후 호출.
     * confirmation은 일회용 — 호출자가 인증 직후 생성하여 전달.
     */
    fun submit(confirmation: OrderConfirmation) {
        val st = _ui.value
        if (!st.canSubmit) return
        val qty = st.quantity ?: return
        val pricePayload: Double? = when (st.orderType) {
            OrderType.LIMIT -> st.price ?: return
            OrderType.MARKET -> null
        }
        _ui.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val intent = OrderIntent.Place(
                ticker = st.ticker,
                market = st.market,
                side = st.side,
                orderType = st.orderType,
                qty = qty,
                price = pricePayload,
                confirmation = confirmation,
                excgCode = if (st.market == Market.US) "NASD" else null,
            )
            val result = orderService.placeOrder(intent)
            result.fold(
                onSuccess = { order ->
                    _ui.update { it.copy(submitting = false, submittedOrder = order) }
                    viewModelScope.launch { orderService.pollExecution(order.id) }
                },
                onFailure = { e ->
                    _ui.update { it.copy(submitting = false, errorMessage = e.message ?: "주문 실패") }
                }
            )
        }
    }

    fun clearError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    fun setError(msg: String) {
        _ui.update { it.copy(errorMessage = msg) }
    }
}

private fun formatInputPrice(value: Double, market: Market): String = when (market) {
    Market.KR -> value.toLong().toString()
    Market.US -> "%.2f".format(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEntryScreen(
    onBack: () -> Unit,
    onOpenOrdersList: () -> Unit,
    viewModel: OrderEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sideColor = if (state.side == TradeSide.BUY) KrUpRed else KrDownBlue
    val sideLabel = if (state.side == TradeSide.BUY) "매수" else "매도"

    LaunchedEffect(state.submittedOrder) {
        if (state.submittedOrder != null) {
            // 송신 성공 시 OrdersScreen으로 이동.
            onOpenOrdersList()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${state.nameKo ?: state.ticker} $sideLabel", style = MaterialTheme.typography.titleLarge)
                        Text("${state.ticker}  ·  ${state.market.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppTokens.space16),
            verticalArrangement = Arrangement.spacedBy(AppTokens.space12),
        ) {
            AppCard(padding = AppTokens.space16) {
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
                    Text("현재가", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.currentPrice?.let { formatPrice(it, state.market) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            AppCard(padding = AppTokens.space16) {
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space12)) {
                    Text("주문 종류", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
                        FilterChip(
                            selected = state.orderType == OrderType.LIMIT,
                            onClick = { viewModel.onOrderTypeChange(OrderType.LIMIT) },
                            label = { Text("지정가") },
                        )
                        FilterChip(
                            selected = state.orderType == OrderType.MARKET,
                            onClick = { viewModel.onOrderTypeChange(OrderType.MARKET) },
                            label = { Text("시장가") },
                        )
                    }
                    OutlinedTextField(
                        value = state.priceText,
                        onValueChange = viewModel::onPriceChange,
                        enabled = state.orderType == OrderType.LIMIT,
                        label = { Text(if (state.orderType == OrderType.LIMIT) "주문 가격" else "시장가 — 입력 불필요") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.quantityText,
                        onValueChange = viewModel::onQuantityChange,
                        label = { Text("주문 수량") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.expectedTotal?.let { total ->
                        Text(
                            "예상 금액 ${formatPrice(total, state.market)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = sideColor,
                        )
                    }
                    Text(
                        "주문 직전 생체 인증을 요청합니다. 자동 발주는 절대 없으며, 송신은 본인 확인 후에만 진행됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.errorMessage?.let { msg ->
                AppCard(containerColor = MaterialTheme.colorScheme.errorContainer, padding = AppTokens.space16) {
                    Text(
                        "❌ $msg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = "$sideLabel 주문 보내기",
                onClick = {
                    val activity = context as? FragmentActivity ?: run {
                        viewModel.clearError()
                        return@PrimaryButton
                    }
                    scope.launch {
                        val nameSuffix = state.nameKo?.let { " · $it" } ?: ""
                        val result = activity.authenticateForOrder(
                            title = "$sideLabel 주문 확인",
                            subtitle = "${state.ticker}$nameSuffix",
                            description = "${state.quantityText.ifBlank { "0" }}주를 ${if (state.orderType == OrderType.MARKET) "시장가" else state.priceText.ifBlank { "0" }}로 ${sideLabel}합니다. 송신 전 본인 확인이 필요합니다.",
                        )
                        when (result) {
                            BiometricAuthResult.Success -> viewModel.submit(
                                OrderConfirmation(sourceFlow = "OrderEntryScreen.$sideLabel")
                            )
                            is BiometricAuthResult.Failure -> viewModel.setError("인증 실패: ${result.message}")
                            is BiometricAuthResult.Unsupported -> viewModel.setError(result.message)
                        }
                    }
                },
                enabled = state.canSubmit,
                isLoading = state.submitting,
            )
        }
    }
}

@Composable
private fun formatPrice(value: Double, market: Market): String = when (market) {
    Market.KR -> "%,d원".format(value.toLong())
    Market.US -> "$${"%,.2f".format(value)}"
}
