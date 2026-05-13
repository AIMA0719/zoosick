package com.myinfocar.aicoachstock.ui.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.model.EmotionTag
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class TradeEditUiState(
    val editingId: String? = null,
    val isLoading: Boolean = false,
    val ticker: String = "",
    val market: Market = Market.KR,
    val side: TradeSide = TradeSide.BUY,
    val qtyText: String = "",
    val priceText: String = "",
    val feeText: String = "",
    val executedAt: Instant = Instant.now(),
    val reasonText: String = "",
    val emotionTag: EmotionTag = EmotionTag.NONE,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class TradeEditViewModel @Inject constructor(
    private val repo: TradeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["id"]
    private var originalCreatedAt: Instant = Instant.now()
    private var originalLinkedChecklistId: String? = null

    private val _ui = MutableStateFlow(
        TradeEditUiState(editingId = editingId, isLoading = editingId != null),
    )
    val ui: StateFlow<TradeEditUiState> = _ui.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val existing = repo.findById(editingId)
                if (existing != null) {
                    originalCreatedAt = existing.createdAt
                    originalLinkedChecklistId = existing.linkedChecklistId
                    _ui.update {
                        it.copy(
                            ticker = existing.ticker,
                            market = existing.market,
                            side = existing.side,
                            qtyText = existing.qty.toString(),
                            priceText = formatNumber(existing.price),
                            feeText = existing.fee?.let(::formatNumber).orEmpty(),
                            executedAt = existing.executedAt,
                            reasonText = existing.reasonText.orEmpty(),
                            emotionTag = existing.emotionTag,
                            isLoading = false,
                        )
                    }
                } else {
                    _ui.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onTickerChange(v: String) = _ui.update { it.copy(ticker = v.uppercase().trim(), saveError = null) }
    fun onMarketChange(v: Market) = _ui.update { it.copy(market = v) }
    fun onSideChange(v: TradeSide) = _ui.update { it.copy(side = v) }
    fun onQtyChange(v: String) = _ui.update { it.copy(qtyText = v.filter { c -> c.isDigit() }, saveError = null) }
    fun onPriceChange(v: String) = _ui.update {
        it.copy(priceText = v.filter { c -> c.isDigit() || c == '.' }, saveError = null)
    }
    fun onFeeChange(v: String) = _ui.update {
        it.copy(feeText = v.filter { c -> c.isDigit() || c == '.' })
    }
    fun onDateChange(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val currentTime = _ui.value.executedAt.atZone(zone).toLocalTime()
        val newInstant = date.atTime(currentTime).atZone(zone).toInstant()
        _ui.update { it.copy(executedAt = newInstant) }
    }
    fun onReasonChange(v: String) = _ui.update { it.copy(reasonText = v) }
    fun onEmotionChange(v: EmotionTag) = _ui.update { it.copy(emotionTag = v) }

    fun save() {
        val s = _ui.value
        val ticker = s.ticker.trim()
        if (ticker.isBlank()) {
            _ui.update { it.copy(saveError = "종목코드를 입력해주세요") }
            return
        }
        val qty = s.qtyText.toIntOrNull()
        if (qty == null || qty <= 0) {
            _ui.update { it.copy(saveError = "수량을 정확히 입력해주세요") }
            return
        }
        val price = s.priceText.toDoubleOrNull()
        if (price == null || price <= 0.0) {
            _ui.update { it.copy(saveError = "가격을 정확히 입력해주세요") }
            return
        }
        val fee = s.feeText.takeIf { it.isNotBlank() }?.toDoubleOrNull()

        _ui.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val now = Instant.now()
                val trade = Trade(
                    id = editingId ?: UUID.randomUUID().toString(),
                    ticker = ticker,
                    market = s.market,
                    side = s.side,
                    qty = qty,
                    price = price,
                    fee = fee,
                    executedAt = s.executedAt,
                    reasonText = s.reasonText.trim().ifBlank { null },
                    emotionTag = s.emotionTag,
                    linkedChecklistId = originalLinkedChecklistId,
                    createdAt = if (editingId == null) now else originalCreatedAt,
                )
                repo.save(trade)
                _ui.update { it.copy(isSaving = false, isSaved = true) }
            } catch (t: Throwable) {
                _ui.update { it.copy(isSaving = false, saveError = t.message ?: "저장 실패") }
            }
        }
    }

    fun delete() {
        val id = editingId ?: return
        viewModelScope.launch {
            repo.delete(id)
            _ui.update { it.copy(isDeleted = true) }
        }
    }
}

private fun formatNumber(d: Double): String {
    return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TradeEditScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TradeEditViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved || state.isDeleted) onSaved()
    }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editingId == null) "새 매매" else "매매 편집") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.editingId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = !state.isSaving) {
                        Text("저장")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 매수/매도
            Text("매수 / 매도", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TradeSide.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = state.side == s,
                        onClick = { viewModel.onSideChange(s) },
                        shape = SegmentedButtonDefaults.itemShape(i, TradeSide.entries.size),
                    ) { Text(s.label()) }
                }
            }

            // 시장
            Text("시장", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Market.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = state.market == m,
                        onClick = { viewModel.onMarketChange(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Market.entries.size),
                    ) { Text(m.label()) }
                }
            }

            // 종목코드
            OutlinedTextField(
                value = state.ticker,
                onValueChange = viewModel::onTickerChange,
                label = { Text("종목코드") },
                placeholder = { Text(if (state.market == Market.KR) "예: 005930" else "예: NVDA") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 수량 / 가격
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.qtyText,
                    onValueChange = viewModel::onQtyChange,
                    label = { Text("수량") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("가격") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // 수수료
            OutlinedTextField(
                value = state.feeText,
                onValueChange = viewModel::onFeeChange,
                label = { Text("수수료 (선택)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 체결일
            val dateFormatter = remember {
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
            }
            OutlinedTextField(
                value = dateFormatter.format(state.executedAt),
                onValueChange = {},
                readOnly = true,
                label = { Text("체결일") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("선택") }
                },
            )

            // 매매 이유
            OutlinedTextField(
                value = state.reasonText,
                onValueChange = viewModel::onReasonChange,
                label = { Text("매매 이유 (선택)") },
                placeholder = { Text("예: 20일선 돌파 + 거래량 급증") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            // 감정 태그
            Text("감정", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EmotionTag.entries.forEach { tag ->
                    FilterChip(
                        selected = state.emotionTag == tag,
                        onClick = { viewModel.onEmotionChange(tag) },
                        label = { Text(tag.label()) },
                    )
                }
            }

            // Save error
            state.saveError?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialMillis = state.executedAt.atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.onDateChange(date)
                    }
                    showDatePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
        ) { DatePicker(state = pickerState) }
    }

    // Delete confirm dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("매매기록 삭제") },
            text = { Text("이 매매기록을 삭제합니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }
}
