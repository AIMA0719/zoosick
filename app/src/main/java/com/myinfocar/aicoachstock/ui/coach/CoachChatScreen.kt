package com.myinfocar.aicoachstock.ui.coach

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.myinfocar.aicoachstock.domain.coach.CoachEvent
import com.myinfocar.aicoachstock.domain.coach.CoachService
import com.myinfocar.aicoachstock.domain.model.CoachMessage
import com.myinfocar.aicoachstock.domain.model.CoachMessageRole
import com.myinfocar.aicoachstock.domain.model.CoachSession
import com.myinfocar.aicoachstock.domain.repository.CoachRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachChatUiState(
    val session: CoachSession? = null,
    val messages: List<CoachMessage> = emptyList(),
    val streamingPartial: String? = null,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CoachChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coachRepo: CoachRepository,
    private val service: CoachService,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"]) {
        "sessionId 인자가 라우트에 없음"
    }

    private val _ui = MutableStateFlow(CoachChatUiState())
    val ui: StateFlow<CoachChatUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(session = coachRepo.findSession(sessionId)) }
        }
        viewModelScope.launch {
            coachRepo.observeMessages(sessionId).collect { msgs ->
                _ui.update { it.copy(messages = msgs) }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _ui.value.isGenerating) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    streamingPartial = "",
                )
            }
            service.sendMessage(sessionId, trimmed).collect { ev ->
                when (ev) {
                    CoachEvent.UserStored -> { /* observeMessages가 즉시 반영 */ }
                    CoachEvent.LoadingModel -> _ui.update {
                        it.copy(isLoadingModel = true, streamingPartial = "")
                    }
                    is CoachEvent.Streaming -> _ui.update {
                        it.copy(isLoadingModel = false, streamingPartial = ev.partial)
                    }
                    is CoachEvent.Completed -> _ui.update {
                        it.copy(
                            isGenerating = false,
                            isLoadingModel = false,
                            streamingPartial = null,
                        )
                    }
                    is CoachEvent.Failed -> _ui.update {
                        it.copy(
                            isGenerating = false,
                            isLoadingModel = false,
                            streamingPartial = null,
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
fun CoachChatScreen(
    onBack: () -> Unit,
    viewModel: CoachChatViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.streamingPartial) {
        val streamingExtra = if (state.streamingPartial != null) 1 else 0
        val target = state.messages.size + streamingExtra - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.session?.title ?: "코치 채팅")
                        state.session?.topicTicker?.let {
                            Text(
                                "종목 $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                value = input,
                onChange = { input = it },
                onSend = {
                    val toSend = input
                    input = ""
                    viewModel.send(toSend)
                },
                enabled = !state.isGenerating,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.messages.isEmpty() && state.streamingPartial == null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("코치에게 첫 메시지를 보내보세요", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "활성 원칙과 최근 매매 10건이 자동으로 컨텍스트에 들어갑니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(msg)
                    }
                    val partial = state.streamingPartial
                    if (partial != null) {
                        item("streaming") {
                            PartialBubble(text = partial, isLoadingModel = state.isLoadingModel)
                        }
                    }
                }
            }

            state.errorMessage?.let { msg ->
                Text(
                    "❌ $msg",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Text(
                "본 응답은 코칭 보조이며 매매 책임은 본인에게 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: CoachMessage) {
    val isUser = msg.role == CoachMessageRole.USER
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier.fillMaxWidth()) {
        if (isUser) Spacer(Modifier.weight(0.15f))
        Card(
            modifier = Modifier.weight(0.85f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            colors = CardDefaults.cardColors(containerColor = bg),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "나" else "코치",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.content, style = MaterialTheme.typography.bodyMedium, color = fg)
            }
        }
        if (!isUser) Spacer(Modifier.weight(0.15f))
    }
}

@Composable
private fun PartialBubble(text: String, isLoadingModel: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.weight(0.85f),
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "코치",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isLoadingModel) "Gemma 4 E4B 로드 중… (약 10초)"
                    else if (text.isEmpty()) "생성 중…"
                    else text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(0.15f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("메시지를 입력하세요") },
            enabled = enabled,
            maxLines = 4,
        )
        Spacer(Modifier.size(8.dp))
        IconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기")
        }
    }
}
