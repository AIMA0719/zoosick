package com.myinfocar.aicoachstock.ui.poc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.llm.DownloadEvent
import com.myinfocar.aicoachstock.domain.llm.GenerationEvent
import com.myinfocar.aicoachstock.domain.llm.LLMEngine
import com.myinfocar.aicoachstock.domain.llm.ModelDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PoC #1 검증 화면 — Gemma 4 E4B + LiteRT-LM.
 *
 *  1) 모델 다운로드(~3.66GB, Wi-Fi 권장) — Range 재개, SHA-256 자동 기록.
 *  2) 모델 로드 — Engine.initialize() (10초+).
 *  3) 추론 1턴 — Conversation.sendMessageAsync 토큰 스트리밍.
 *
 * 본 개발에서는 다운로드 UX(셀룰러 동의·재시도)·로드 캐시·취소 처리를 강화한다.
 */

data class DownloadUi(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val phase: Phase = Phase.IDLE,
    val errorMessage: String? = null,
) {
    enum class Phase { IDLE, DOWNLOADING, VERIFYING, COMPLETED, FAILED }
}

data class InferenceUi(
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val output: String = "",
    val latencyMs: Long? = null,
    val tokenCount: Int = 0,
    val errorMessage: String? = null,
)

data class LlmPocUiState(
    val modelReady: Boolean = false,
    val download: DownloadUi = DownloadUi(),
    val inference: InferenceUi = InferenceUi(),
)

@HiltViewModel
class LlmPocViewModel @Inject constructor(
    private val downloader: ModelDownloader,
    private val engine: LLMEngine,
) : ViewModel() {

    private val _ui = MutableStateFlow(LlmPocUiState())
    val ui: StateFlow<LlmPocUiState> = _ui.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            val ready = downloader.isModelReady()
            _ui.update { it.copy(modelReady = ready) }
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    download = DownloadUi(phase = DownloadUi.Phase.DOWNLOADING)
                )
            }
            downloader.download().collect { event ->
                when (event) {
                    is DownloadEvent.Progress -> _ui.update { state ->
                        state.copy(
                            download = state.download.copy(
                                downloadedBytes = event.downloadedBytes,
                                totalBytes = event.totalBytes,
                                phase = DownloadUi.Phase.DOWNLOADING,
                            ),
                        )
                    }
                    DownloadEvent.Verifying -> _ui.update { state ->
                        state.copy(download = state.download.copy(phase = DownloadUi.Phase.VERIFYING))
                    }
                    DownloadEvent.Paused -> { /* PoC에선 표시만 — 본 개발에서 일시정지 UI */ }
                    DownloadEvent.Completed -> _ui.update { state ->
                        state.copy(
                            modelReady = true,
                            download = state.download.copy(phase = DownloadUi.Phase.COMPLETED),
                        )
                    }
                    is DownloadEvent.Failed -> _ui.update { state ->
                        state.copy(
                            download = state.download.copy(
                                phase = DownloadUi.Phase.FAILED,
                                errorMessage = "${event.reason}: ${event.cause?.message ?: ""}",
                            ),
                        )
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _ui.update {
            it.copy(download = it.download.copy(phase = DownloadUi.Phase.IDLE))
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            downloader.delete()
            _ui.update {
                LlmPocUiState(modelReady = false)
            }
        }
    }

    fun loadEngine() {
        if (_ui.value.inference.isLoading || _ui.value.inference.loaded) return
        viewModelScope.launch {
            _ui.update { it.copy(inference = it.inference.copy(isLoading = true, errorMessage = null)) }
            engine.load().fold(
                onSuccess = {
                    _ui.update {
                        it.copy(inference = it.inference.copy(isLoading = false, loaded = true))
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            inference = it.inference.copy(
                                isLoading = false,
                                errorMessage = "로드 실패: ${e.message}",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun unloadEngine() {
        engine.unload()
        _ui.update {
            it.copy(inference = InferenceUi())
        }
    }

    fun generate(prompt: String) {
        if (_ui.value.inference.isGenerating || !_ui.value.inference.loaded) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    inference = it.inference.copy(
                        isGenerating = true,
                        output = "",
                        latencyMs = null,
                        tokenCount = 0,
                        errorMessage = null,
                    ),
                )
            }
            engine.generate(
                systemPrompt = "당신은 한국어로 답하는 친근한 코치입니다. 본 응답은 코칭 보조이며 매매 책임은 본인.",
                userPrompt = prompt,
                context = emptyList(),
            ).collect { ev ->
                when (ev) {
                    is GenerationEvent.Token -> _ui.update { state ->
                        state.copy(
                            inference = state.inference.copy(output = state.inference.output + ev.text),
                        )
                    }
                    is GenerationEvent.Final -> _ui.update { state ->
                        state.copy(
                            inference = state.inference.copy(
                                isGenerating = false,
                                output = ev.fullText,
                                tokenCount = ev.tokenCount,
                                latencyMs = ev.latencyMs,
                            ),
                        )
                    }
                    is GenerationEvent.Error -> _ui.update { state ->
                        state.copy(
                            inference = state.inference.copy(
                                isGenerating = false,
                                errorMessage = "추론 실패: ${ev.cause.message}",
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        engine.unload()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmPocScreen(
    onBack: () -> Unit,
    viewModel: LlmPocViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    var prompt by rememberSaveable { mutableStateOf("자기소개를 한 문장으로 해줘.") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 모델 PoC") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DownloadCard(
                ui = state.download,
                modelReady = state.modelReady,
                onStart = viewModel::startDownload,
                onCancel = viewModel::cancelDownload,
                onDelete = viewModel::deleteModel,
            )
            InferenceCard(
                modelReady = state.modelReady,
                ui = state.inference,
                prompt = prompt,
                onPromptChange = { prompt = it },
                onLoad = viewModel::loadEngine,
                onUnload = viewModel::unloadEngine,
                onGenerate = { viewModel.generate(prompt) },
            )
        }
    }
}

@Composable
private fun DownloadCard(
    ui: DownloadUi,
    modelReady: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("1. 모델 다운로드", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gemma 4 E4B (.litertlm, 약 3.66GB). Wi-Fi 권장 — 중단되면 Range 재개.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val percent = if (ui.totalBytes > 0L) {
                ((ui.downloadedBytes.toDouble() / ui.totalBytes) * 100).toInt()
            } else null

            when (ui.phase) {
                DownloadUi.Phase.DOWNLOADING -> {
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("$percent%  (${ui.downloadedBytes.formatBytes()} / ${ui.totalBytes.formatBytes()})")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("다운로드 중… ${ui.downloadedBytes.formatBytes()}")
                    }
                }
                DownloadUi.Phase.VERIFYING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("  SHA-256 검증 중…")
                    }
                }
                DownloadUi.Phase.COMPLETED -> Text(
                    "✅ 다운로드 + 무결성 검증 완료",
                    color = MaterialTheme.colorScheme.primary,
                )
                DownloadUi.Phase.FAILED -> Text(
                    "❌ ${ui.errorMessage ?: "실패"}",
                    color = MaterialTheme.colorScheme.error,
                )
                DownloadUi.Phase.IDLE -> {
                    if (modelReady) Text("✅ 모델 이미 보유 + 검증됨", color = MaterialTheme.colorScheme.primary)
                    else Text("아직 다운로드 전")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = ui.phase != DownloadUi.Phase.DOWNLOADING && ui.phase != DownloadUi.Phase.VERIFYING && !modelReady,
                ) { Text(if (ui.phase == DownloadUi.Phase.FAILED) "다시 시도" else "다운로드 시작") }

                if (ui.phase == DownloadUi.Phase.DOWNLOADING) {
                    OutlinedButton(onClick = onCancel) { Text("취소") }
                }
                if (modelReady) {
                    OutlinedButton(onClick = onDelete) { Text("삭제") }
                }
            }
        }
    }
}

@Composable
private fun InferenceCard(
    modelReady: Boolean,
    ui: InferenceUi,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onGenerate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("2. 모델 로드 + 3. 추론", style = MaterialTheme.typography.titleMedium)
            Text(
                "로드는 Engine.initialize() — 약 10초+. 추론은 토큰 스트리밍.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLoad,
                    enabled = modelReady && !ui.loaded && !ui.isLoading,
                ) {
                    if (ui.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(if (ui.loaded) "✅ 로드됨" else "모델 로드")
                    }
                }
                if (ui.loaded) {
                    OutlinedButton(onClick = onUnload) { Text("언로드") }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("프롬프트") },
                modifier = Modifier.fillMaxWidth(),
                enabled = ui.loaded && !ui.isGenerating,
            )

            Button(
                onClick = onGenerate,
                enabled = ui.loaded && !ui.isGenerating && prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("추론 실행")
                }
            }

            if (ui.output.isNotEmpty() || ui.isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = if (ui.output.isEmpty()) "…" else ui.output,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (ui.latencyMs != null) {
                Text(
                    "⏱ ${ui.latencyMs}ms  ·  ${ui.tokenCount} 토큰  ·  ${
                        if (ui.tokenCount > 0) "%.1f tok/s".format(ui.tokenCount * 1000.0 / ui.latencyMs)
                        else "-"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ui.errorMessage?.let { msg ->
                Text("❌ $msg", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun Long.formatBytes(): String = when {
    this < 0L -> "?"
    this < 1024L -> "$this B"
    this < 1024L * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024L * 1024 * 1024 -> "%.1f MB".format(this / 1024.0 / 1024)
    else -> "%.2f GB".format(this / 1024.0 / 1024 / 1024)
}
