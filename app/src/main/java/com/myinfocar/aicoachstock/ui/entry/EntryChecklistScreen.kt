package com.myinfocar.aicoachstock.ui.entry

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.entry.EntryChecklistService
import com.myinfocar.aicoachstock.domain.entry.EntryEvent
import com.myinfocar.aicoachstock.domain.model.EntryChecklist
import com.myinfocar.aicoachstock.domain.model.EntryDecision
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryChecklistUiState(
    val principles: List<TradingPrinciple> = emptyList(),
    val streamingText: String = "",
    val isGenerating: Boolean = false,
    val result: EntryChecklist? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class EntryChecklistViewModel @Inject constructor(
    private val service: EntryChecklistService,
) : ViewModel() {

    private val _ui = MutableStateFlow(EntryChecklistUiState())
    val ui: StateFlow<EntryChecklistUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(principles = service.loadPrinciples()) }
        }
    }

    fun evaluate(
        ticker: String,
        answers: Map<String, String>,
        userNote: String?,
        currentPrice: Double?,
    ) {
        if (_ui.value.isGenerating) return
        viewModelScope.launch {
            _ui.update { it.copy(isGenerating = true, errorMessage = null, streamingText = "", result = null) }
            service.evaluate(ticker, answers, userNote, currentPrice).collect { ev ->
                when (ev) {
                    EntryEvent.LoadingModel -> _ui.update {
                        it.copy(streamingText = "Gemma 4 E4B 로드 중… (약 10초)")
                    }
                    is EntryEvent.Streaming -> _ui.update { it.copy(streamingText = ev.partial) }
                    is EntryEvent.Completed -> _ui.update {
                        it.copy(isGenerating = false, streamingText = "", result = ev.checklist)
                    }
                    is EntryEvent.Failed -> _ui.update {
                        it.copy(
                            isGenerating = false,
                            streamingText = "",
                            errorMessage = ev.cause.message ?: "실패",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryChecklistScreen(
    onBack: () -> Unit,
    viewModel: EntryChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    var ticker by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    val answers = remember { mutableStateMapOf<String, String>() }

    // 새 원칙 목록이 들어오면 응답 키 초기화.
    LaunchedEffect(state.principles) {
        state.principles.forEach { p -> answers.getOrPut(p.id) { "" } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("진입 체크리스트") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = ticker,
                onValueChange = { ticker = it.uppercase() },
                label = { Text("종목 코드") },
                placeholder = { Text("예: 005930 또는 NVDA") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("현재가 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("원칙별 응답", style = MaterialTheme.typography.titleMedium)
            if (state.principles.isEmpty()) {
                Text(
                    "활성 원칙이 없습니다. 원칙 화면에서 먼저 등록하세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.principles.forEach { p ->
                    PrincipleAnswerRow(
                        principle = p,
                        value = answers[p.id].orEmpty(),
                        onChange = { answers[p.id] = it },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("추가 메모 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (state.streamingText.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "AI 평가 진행 중",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                        Text(state.streamingText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.result?.let { result ->
                DecisionCard(result)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("AI 의견", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(result.aiVerdict, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.evaluate(
                        ticker = ticker.trim(),
                        answers = answers.toMap(),
                        userNote = note.trim().takeIf { it.isNotBlank() },
                        currentPrice = priceText.toDoubleOrNull(),
                    )
                },
                enabled = !state.isGenerating && ticker.isNotBlank() && state.principles.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("🤖 AI 평가 받기")
            }

            state.errorMessage?.let { msg ->
                Text("❌ $msg", color = MaterialTheme.colorScheme.error)
            }

            Text(
                "본 응답은 코칭 보조이며 매매 책임은 본인에게 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrincipleAnswerRow(
    principle: TradingPrinciple,
    value: String,
    onChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "[${principle.category.name}] ${principle.ruleText}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("YES", "NO").forEach { opt ->
                    FilterChip(
                        selected = value.equals(opt, ignoreCase = true),
                        onClick = { onChange(opt) },
                        label = { Text(if (opt == "YES") "충족" else "미충족") },
                    )
                }
            }
            OutlinedTextField(
                value = if (value == "YES" || value == "NO") "" else value,
                onValueChange = onChange,
                placeholder = { Text("(또는 자유 텍스트로 응답)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 1,
            )
        }
    }
}

@Composable
private fun DecisionCard(result: EntryChecklist) {
    val (label, color) = when (result.decision) {
        EntryDecision.GO -> "GO  ·  진입 추천" to Color(0xFF2E7D32)
        EntryDecision.HOLD -> "HOLD  ·  추가 확인" to Color(0xFFF9A825)
        EntryDecision.STOP -> "STOP  ·  진입 비추천" to Color(0xFFC62828)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
