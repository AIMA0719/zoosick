package com.myinfocar.aicoachstock.ui.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.research.ResearchEvent
import com.myinfocar.aicoachstock.domain.research.ResearchService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResearchUiState(
    val streamingText: String = "",
    val isGenerating: Boolean = false,
    val finalText: String? = null,
    val latencyMs: Long? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ResearchViewModel @Inject constructor(
    private val service: ResearchService,
) : ViewModel() {

    private val _ui = MutableStateFlow(ResearchUiState())
    val ui: StateFlow<ResearchUiState> = _ui.asStateFlow()

    fun ask(ticker: String, manualFinancials: String?, question: String) {
        if (_ui.value.isGenerating) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    streamingText = "",
                    finalText = null,
                    latencyMs = null,
                )
            }
            service.ask(ticker, manualFinancials, question).collect { ev ->
                when (ev) {
                    ResearchEvent.Preparing -> _ui.update { it.copy(streamingText = "준비 중…") }
                    ResearchEvent.LoadingModel -> _ui.update {
                        it.copy(streamingText = "Gemma 4 E4B 로드 중… (약 10초)")
                    }
                    is ResearchEvent.Streaming -> _ui.update { it.copy(streamingText = ev.partial) }
                    is ResearchEvent.Completed -> _ui.update {
                        it.copy(
                            isGenerating = false,
                            streamingText = "",
                            finalText = ev.text,
                            latencyMs = ev.latencyMs,
                        )
                    }
                    is ResearchEvent.Failed -> _ui.update {
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
fun ResearchScreen(
    onBack: () -> Unit,
    viewModel: ResearchViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    var ticker by rememberSaveable { mutableStateOf("") }
    var financials by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("종목 리서치 Q&A") },
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
                label = { Text("종목코드") },
                placeholder = { Text("예: 005930 또는 NVDA") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = financials,
                onValueChange = { financials = it },
                label = { Text("재무 메모 (선택)") },
                placeholder = {
                    Text(
                        "예: 매출 ↑20%, 영업이익률 12%, PER 18, 부채비율 80%",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("질문") },
                placeholder = { Text("예: 현재 밸류에이션을 어떻게 봐야 할까?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Button(
                onClick = {
                    viewModel.ask(
                        ticker = ticker.trim(),
                        manualFinancials = financials.trim().takeIf { it.isNotBlank() },
                        question = question.trim(),
                    )
                },
                enabled = !state.isGenerating && ticker.isNotBlank() && question.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("🤖 AI 답변 받기")
            }

            if (state.streamingText.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "AI 답변 생성 중",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(state.streamingText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.finalText?.let { text ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("AI 답변", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(8.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                        state.latencyMs?.let {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "응답 ${it}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
