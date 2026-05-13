package com.myinfocar.aicoachstock.ui.principle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.model.PrincipleCategory
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class PrincipleEditUiState(
    val editingId: String? = null,
    val isLoading: Boolean = false,
    val category: PrincipleCategory = PrincipleCategory.ENTRY,
    val ruleText: String = "",
    val weight: Int = 3,
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class PrincipleEditViewModel @Inject constructor(
    private val repo: TradingPrincipleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["id"]
    private var originalOrderIndex: Int = 0
    private var originalCreatedAt: Instant = Instant.now()

    private val _ui = MutableStateFlow(
        PrincipleEditUiState(editingId = editingId, isLoading = editingId != null),
    )
    val ui: StateFlow<PrincipleEditUiState> = _ui.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val existing = repo.findById(editingId)
                if (existing != null) {
                    originalOrderIndex = existing.orderIndex
                    originalCreatedAt = existing.createdAt
                    _ui.update {
                        it.copy(
                            category = existing.category,
                            ruleText = existing.ruleText,
                            weight = existing.weight,
                            isActive = existing.isActive,
                            isLoading = false,
                        )
                    }
                } else {
                    _ui.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onCategoryChange(value: PrincipleCategory) {
        _ui.update { it.copy(category = value) }
    }
    fun onRuleTextChange(value: String) {
        _ui.update { it.copy(ruleText = value, saveError = null) }
    }
    fun onWeightChange(value: Int) {
        _ui.update { it.copy(weight = value.coerceIn(1, 5)) }
    }
    fun onActiveChange(value: Boolean) {
        _ui.update { it.copy(isActive = value) }
    }

    fun save() {
        val s = _ui.value
        if (s.ruleText.isBlank()) {
            _ui.update { it.copy(saveError = "원칙 본문을 입력해주세요") }
            return
        }
        _ui.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val now = Instant.now()
                val orderIndex = if (editingId == null) repo.count() else originalOrderIndex
                val createdAt = if (editingId == null) now else originalCreatedAt
                val principle = TradingPrinciple(
                    id = editingId ?: UUID.randomUUID().toString(),
                    category = s.category,
                    ruleText = s.ruleText.trim(),
                    weight = s.weight,
                    isActive = s.isActive,
                    orderIndex = orderIndex,
                    createdAt = createdAt,
                    updatedAt = now,
                )
                repo.save(principle)
                _ui.update { it.copy(isSaving = false, isSaved = true) }
            } catch (t: Throwable) {
                _ui.update { it.copy(isSaving = false, saveError = t.message ?: "저장 실패") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipleEditScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: PrincipleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editingId == null) "새 원칙" else "원칙 편집") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
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
            Text("카테고리", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val categories = PrincipleCategory.entries
                categories.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = state.category == c,
                        onClick = { viewModel.onCategoryChange(c) },
                        shape = SegmentedButtonDefaults.itemShape(i, categories.size),
                    ) { Text(c.label()) }
                }
            }

            OutlinedTextField(
                value = state.ruleText,
                onValueChange = viewModel::onRuleTextChange,
                label = { Text("원칙 본문") },
                placeholder = { Text("예: PER 30 이하 + 20일선 위 + 거래량 증가 시에만 진입") },
                minLines = 3,
                maxLines = 8,
                isError = state.saveError != null,
                supportingText = state.saveError?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("중요도 (${state.weight}/5)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = state.weight.toFloat(),
                    onValueChange = { viewModel.onWeightChange(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.isActive, onCheckedChange = viewModel::onActiveChange)
                Spacer(Modifier.width(8.dp))
                Text("활성화 (체크리스트·복기에 적용)")
            }
        }
    }
}
