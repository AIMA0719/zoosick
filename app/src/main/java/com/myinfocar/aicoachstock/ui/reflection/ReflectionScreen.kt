package com.myinfocar.aicoachstock.ui.reflection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Trade
import com.myinfocar.aicoachstock.domain.model.TradeReflection
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.reflection.ReflectionEvent
import com.myinfocar.aicoachstock.domain.reflection.ReflectionService
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import com.myinfocar.aicoachstock.domain.repository.TradeReflectionRepository
import com.myinfocar.aicoachstock.domain.repository.TradeRepository
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ReflectionUiState(
    val trade: Trade? = null,
    val stockName: String? = null,
    val savedReflection: TradeReflection? = null,
    val principles: Map<String, TradingPrinciple> = emptyMap(),
    val streamingText: String = "",
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ReflectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tradeRepo: TradeRepository,
    private val stockRepo: StockRepository,
    private val principleRepo: TradingPrincipleRepository,
    private val reflectionRepo: TradeReflectionRepository,
    private val service: ReflectionService,
) : ViewModel() {

    private val tradeId: String = checkNotNull(savedStateHandle["tradeId"]) {
        "tradeId 인자가 라우트에 없음"
    }

    private val _ui = MutableStateFlow(ReflectionUiState())
    val ui: StateFlow<ReflectionUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val trade = tradeRepo.findById(tradeId)
            val stockName = trade?.let { stockRepo.findByTicker(it.ticker)?.nameKo }
            _ui.update { it.copy(trade = trade, stockName = stockName) }
        }
        viewModelScope.launch {
            reflectionRepo.observeByTradeId(tradeId).collect { saved ->
                _ui.update { it.copy(savedReflection = saved) }
            }
        }
        viewModelScope.launch {
            principleRepo.observeAll().collect { list ->
                _ui.update { it.copy(principles = list.associateBy { p -> p.id }) }
            }
        }
    }

    fun generate() {
        if (_ui.value.isGenerating || _ui.value.trade == null) return
        viewModelScope.launch {
            _ui.update { it.copy(isGenerating = true, errorMessage = null, streamingText = "") }
            service.generateForTrade(tradeId).collect { ev ->
                when (ev) {
                    is ReflectionEvent.Preparing -> _ui.update {
                        it.copy(streamingText = "데이터 준비 중…")
                    }
                    is ReflectionEvent.LoadingModel -> _ui.update {
                        it.copy(streamingText = "Gemma 4 E4B 로드 중… (약 10초)")
                    }
                    is ReflectionEvent.Streaming -> _ui.update {
                        it.copy(streamingText = ev.partial)
                    }
                    is ReflectionEvent.Completed -> _ui.update {
                        it.copy(isGenerating = false, streamingText = "")
                    }
                    is ReflectionEvent.Failed -> _ui.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = ev.cause.message ?: "실패",
                        )
                    }
                }
            }
        }
    }

    fun saveMyNote(text: String) {
        val current = _ui.value.savedReflection ?: return
        viewModelScope.launch {
            reflectionRepo.updateMyNote(current.id, text.takeIf { it.isNotBlank() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionScreen(
    onBack: () -> Unit,
    viewModel: ReflectionViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val saved = state.savedReflection
    var myNote by rememberSaveable(saved?.id) { mutableStateOf(saved?.myNote.orEmpty()) }

    // 저장된 myNote가 외부 갱신(다른 디바이스, 재진입)되면 입력값 동기화.
    LaunchedEffect(saved?.id, saved?.myNote) {
        myNote = saved?.myNote.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 매매 복기") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        if (state.trade == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
            TradeSummaryCard(trade = state.trade!!, stockName = state.stockName)

            val displayText = when {
                state.isGenerating -> state.streamingText
                else -> saved?.aiAnalysis.orEmpty()
            }

            if (displayText.isNotEmpty()) {
                AnalysisCard(text = displayText, generating = state.isGenerating)
            }

            if (saved != null && saved.ruleViolations.isNotEmpty()) {
                ViolationsCard(violations = saved.ruleViolations, lookup = state.principles)
            }

            saved?.lesson?.let { LessonCard(it) }

            if (saved != null) {
                MyNoteCard(
                    value = myNote,
                    onChange = { myNote = it },
                    onSave = { viewModel.saveMyNote(myNote) },
                    pristine = myNote == (saved.myNote.orEmpty()),
                )
            }

            Button(
                onClick = viewModel::generate,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(if (saved == null) "🤖 AI 복기 생성" else "재생성")
                }
            }

            state.errorMessage?.let { msg ->
                Text("❌ $msg", color = MaterialTheme.colorScheme.error)
            }

            DisclaimerCard()

            saved?.let {
                MetaText(it)
            }
        }
    }
}

@Composable
private fun TradeSummaryCard(trade: Trade, stockName: String?) {
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    val moneyFmt = when (trade.market) {
        Market.KR -> NumberFormat.getNumberInstance(Locale.KOREA)
        Market.US -> NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sideText = if (trade.side == TradeSide.BUY) "매수" else "매도"
                Text(
                    "$sideText  ·  ${trade.ticker}${stockName?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "${moneyFmt.format(trade.price)} × ${trade.qty}주  =  ${moneyFmt.format(trade.price * trade.qty)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${dateFmt.format(trade.executedAt)}  ·  감정: ${trade.emotionTag.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            trade.reasonText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("이유: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AnalysisCard(text: String, generating: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI 분석", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (generating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ViolationsCard(violations: List<String>, lookup: Map<String, TradingPrinciple>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "⚠ 위반된 원칙 ${violations.size}건",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                violations.forEach { id ->
                    val rule = lookup[id]?.ruleText ?: id
                    AssistChip(onClick = {}, label = { Text(rule) })
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "💡 교훈",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                lesson,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyNoteCard(
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    pristine: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("내 메모", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("AI 의견에 동의/반박, 추가 관찰 …") },
                minLines = 2,
            )
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSave, enabled = !pristine) { Text("저장") }
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Text(
        "본 응답은 코칭 보조이며 매매 책임은 본인에게 있습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MetaText(reflection: TradeReflection) {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    Text(
        "생성: ${fmt.format(reflection.createdAt)}  ·  모델: ${reflection.modelVersion}  ·  ${reflection.latencyMs ?: "-"}ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
