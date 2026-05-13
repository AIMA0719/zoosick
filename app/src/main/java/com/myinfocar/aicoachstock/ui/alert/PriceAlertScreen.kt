package com.myinfocar.aicoachstock.ui.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import com.myinfocar.aicoachstock.domain.alert.AlertScheduler
import com.myinfocar.aicoachstock.domain.model.PriceAlert
import com.myinfocar.aicoachstock.domain.model.PriceAlertDirection
import com.myinfocar.aicoachstock.domain.model.PriceAlertStatus
import com.myinfocar.aicoachstock.domain.model.PriceAlertType
import com.myinfocar.aicoachstock.domain.repository.PriceAlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PriceAlertViewModel @Inject constructor(
    private val repo: PriceAlertRepository,
    private val scheduler: AlertScheduler,
) : ViewModel() {

    val alerts: StateFlow<List<PriceAlert>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(ticker: String, type: PriceAlertType, targetPrice: Double) {
        val direction = when (type) {
            PriceAlertType.STOP_LOSS -> PriceAlertDirection.BELOW
            PriceAlertType.TAKE_PROFIT -> PriceAlertDirection.ABOVE
        }
        val alert = PriceAlert(
            id = UUID.randomUUID().toString(),
            ticker = ticker,
            linkedTradeId = null,
            targetPrice = targetPrice,
            type = type,
            direction = direction,
            status = PriceAlertStatus.ACTIVE,
            triggeredAt = null,
            aiMessage = null,
            createdAt = Instant.now(),
        )
        viewModelScope.launch {
            repo.save(alert)
            scheduler.register(alert)
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            repo.updateStatus(id, PriceAlertStatus.CANCELED)
            scheduler.cancel(id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            // 진행 중인 evaluate가 CAS에서 즉시 빠져나오도록 status부터 변경 (race 축소).
            repo.updateStatus(id, PriceAlertStatus.CANCELED)
            scheduler.cancel(id)
            repo.delete(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertScreen(
    onBack: () -> Unit,
    viewModel: PriceAlertViewModel = hiltViewModel(),
) {
    val alerts by viewModel.alerts.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("가격 알림") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("알림 추가") },
            )
        },
    ) { padding ->
        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("등록된 알림이 없습니다", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "FGS가 실행 중일 때 활성 알림은 WebSocket tick에 자동 연결됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        onCancel = { viewModel.cancel(alert.id) },
                        onDelete = { viewModel.delete(alert.id) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddAlertDialog(
            onDismiss = { showAdd = false },
            onSubmit = { ticker, type, price ->
                viewModel.add(ticker, type, price)
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertCard(alert: PriceAlert, onCancel: () -> Unit, onDelete: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())
    val (label, container) = when (alert.status) {
        PriceAlertStatus.ACTIVE -> "활성" to MaterialTheme.colorScheme.surfaceVariant
        PriceAlertStatus.TRIGGERED -> "트리거됨" to MaterialTheme.colorScheme.primaryContainer
        PriceAlertStatus.CANCELED -> "취소됨" to MaterialTheme.colorScheme.surface
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.ticker, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(8.dp))
                    AssistChip(onClick = {}, label = { Text(label) }, enabled = false)
                }
                Spacer(Modifier.size(4.dp))
                val typeText = when (alert.type) {
                    PriceAlertType.STOP_LOSS -> "🔴 손절"
                    PriceAlertType.TAKE_PROFIT -> "🟢 익절"
                }
                val dirArrow = when (alert.direction) {
                    PriceAlertDirection.BELOW -> "↓"
                    PriceAlertDirection.ABOVE -> "↑"
                }
                Text(
                    "$typeText  ·  $dirArrow ${"%,.2f".format(alert.targetPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "등록 ${fmt.format(alert.createdAt)}" +
                        (alert.triggeredAt?.let { " · 트리거 ${fmt.format(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (alert.status == PriceAlertStatus.ACTIVE) {
                TextButton(onClick = onCancel) { Text("취소") }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertDialog(
    onDismiss: () -> Unit,
    onSubmit: (ticker: String, type: PriceAlertType, target: Double) -> Unit,
) {
    var ticker by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(PriceAlertType.STOP_LOSS) }
    var price by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알림 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PriceAlertType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(i, PriceAlertType.entries.size),
                        ) { Text(if (t == PriceAlertType.STOP_LOSS) "🔴 손절" else "🟢 익절") }
                    }
                }
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase() },
                    label = { Text("종목코드") },
                    placeholder = { Text("예: 005930 또는 NVDA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("목표가") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toDoubleOrNull()
                if (ticker.isNotBlank() && p != null && p > 0.0) {
                    onSubmit(ticker.trim(), type, p)
                }
            }) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
