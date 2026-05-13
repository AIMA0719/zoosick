package com.myinfocar.aicoachstock.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.myinfocar.aicoachstock.domain.model.CoachSession
import com.myinfocar.aicoachstock.domain.repository.CoachRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@HiltViewModel
class CoachListViewModel @javax.inject.Inject constructor(
    private val coachRepo: CoachRepository,
) : ViewModel() {

    val sessions: StateFlow<List<CoachSession>> =
        coachRepo.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pendingCreate = MutableStateFlow<String?>(null)
    val pendingCreate: StateFlow<String?> = _pendingCreate.asStateFlow()

    fun create(title: String, topicTicker: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val s = coachRepo.createSession(
                title = title.takeIf { it.isNotBlank() } ?: "새 대화",
                topicTicker = topicTicker?.takeIf { it.isNotBlank() },
            )
            onCreated(s.id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { coachRepo.deleteSession(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachListScreen(
    onOpenSession: (String) -> Unit,
    viewModel: CoachListViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CoachSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AI 코치 채팅") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("새 대화") },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 대화가 없습니다", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "오른쪽 아래 '새 대화'로 시작하세요. 활성 원칙과 최근 매매가 자동으로 컨텍스트로 들어갑니다.",
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
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDelete = { deleteTarget = session },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var title by rememberSaveable { mutableStateOf("") }
        var ticker by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("새 대화") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("제목 (선택)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = ticker,
                        onValueChange = { ticker = it.uppercase() },
                        label = { Text("주제 종목 코드 (선택)") },
                        placeholder = { Text("예: 005930 또는 NVDA") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreate = false
                    viewModel.create(title.trim(), ticker.trim()) { id -> onOpenSession(id) }
                }) { Text("시작") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("취소") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("대화 삭제") },
            text = { Text("'${target.title}' 대화와 모든 메시지를 삭제합니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deleteTarget = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SessionCard(
    session: CoachSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                val sub = buildString {
                    session.topicTicker?.let { append("종목 $it  ·  ") }
                    append(fmt.format(session.lastMessageAt))
                }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제")
            }
        }
    }
}
