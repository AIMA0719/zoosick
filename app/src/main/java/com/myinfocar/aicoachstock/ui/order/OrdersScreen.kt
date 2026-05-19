package com.myinfocar.aicoachstock.ui.order

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderStatus
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.order.OrderConfirmation
import com.myinfocar.aicoachstock.domain.order.OrderIntent
import com.myinfocar.aicoachstock.domain.order.OrderService
import com.myinfocar.aicoachstock.domain.repository.OrderRepository
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.KrDownBlue
import com.myinfocar.aicoachstock.ui.common.KrUpRed
import com.myinfocar.aicoachstock.ui.theme.AppTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val errorMessage: String? = null,
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val orderService: OrderService,
) : ViewModel() {

    val orders: StateFlow<List<Order>> = orderRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(OrdersUiState())
    val ui: StateFlow<OrdersUiState> = _ui.asStateFlow()

    fun cancel(order: Order, confirmation: OrderConfirmation) {
        viewModelScope.launch {
            val result = orderService.placeOrder(OrderIntent.Cancel(order, confirmation))
            result.onFailure { e -> _ui.update { it.copy(errorMessage = e.message ?: "취소 실패") } }
            result.onSuccess { newOrder ->
                viewModelScope.launch { orderService.pollExecution(newOrder.id) }
            }
        }
    }

    fun clearError() = _ui.update { it.copy(errorMessage = null) }
    fun setError(msg: String) = _ui.update { it.copy(errorMessage = msg) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val orders by viewModel.orders.collectAsState()
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("주문 내역") },
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
                .padding(padding),
        ) {
            state.errorMessage?.let { msg ->
                AppCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    padding = AppTokens.space12,
                    modifier = Modifier.padding(AppTokens.space16),
                ) {
                    Text(
                        "❌ $msg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "아직 주문 내역이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AppTokens.space16),
                verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        onCancel = if (canCancel(order)) {
                            {
                                val activity = context as? FragmentActivity ?: return@OrderCard
                                scope.launch {
                                    val result = activity.authenticateForOrder(
                                        title = "취소 확인",
                                        subtitle = "${order.ticker} ${order.qty}주",
                                        description = "이 주문을 취소합니다. 본인 확인이 필요합니다.",
                                    )
                                    when (result) {
                                        BiometricAuthResult.Success ->
                                            viewModel.cancel(order, OrderConfirmation("OrdersScreen.cancel"))
                                        is BiometricAuthResult.Failure -> viewModel.setError("인증 실패: ${result.message}")
                                        is BiometricAuthResult.Unsupported -> viewModel.setError(result.message)
                                    }
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }
}

private fun canCancel(order: Order): Boolean =
    order.status == OrderStatus.SUBMITTED || order.status == OrderStatus.PARTIAL

@Composable
private fun OrderCard(order: Order, onCancel: (() -> Unit)?) {
    val sideColor = if (order.side == TradeSide.BUY) KrUpRed else KrDownBlue
    AppCard(modifier = Modifier.fillMaxWidth(), padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (order.side == TradeSide.BUY) "매수" else "매도",
                    color = sideColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                StatusChip(order.status)
            }
            Text(
                "${order.ticker}  ·  ${order.market.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "수량 ${order.qty}주" + (order.price?.let { "  /  가격 ${formatNumber(it, order.market.name)}" } ?: "  /  시장가"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (order.filledQty > 0) {
                Text(
                    "체결 ${order.filledQty}주" + (order.avgFillPrice?.let { "  /  평균 ${formatNumber(it, order.market.name)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            order.krxOrderNo?.let {
                Text("ODNO $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            order.errorMessage?.let {
                Text("❌ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (onCancel != null) {
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("취소") }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: OrderStatus) {
    val (label, color) = when (status) {
        OrderStatus.PENDING -> "송신 중" to Color(0xFF6B7684)
        OrderStatus.SUBMITTED -> "접수" to Color(0xFF3182F6)
        OrderStatus.PARTIAL -> "부분체결" to Color(0xFFFB923C)
        OrderStatus.FILLED -> "체결" to KrUpRed
        OrderStatus.CANCELED -> "취소" to Color(0xFF6B7684)
        OrderStatus.REJECTED -> "거부" to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
}

private fun formatNumber(value: Double, market: String): String = when (market) {
    "KR" -> "%,d".format(value.toLong())
    "US" -> "$${"%,.2f".format(value)}"
    else -> value.toString()
}
