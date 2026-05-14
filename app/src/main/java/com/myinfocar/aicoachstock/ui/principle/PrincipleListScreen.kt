package com.myinfocar.aicoachstock.ui.principle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import com.myinfocar.aicoachstock.domain.repository.TradingPrincipleRepository
import com.myinfocar.aicoachstock.ui.common.AppCard
import com.myinfocar.aicoachstock.ui.common.EmptyState
import com.myinfocar.aicoachstock.ui.common.ListLoadingSkeleton
import com.myinfocar.aicoachstock.ui.theme.AppTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class PrincipleListUiState(
    val items: List<TradingPrinciple> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PrincipleListViewModel @Inject constructor(
    private val repo: TradingPrincipleRepository,
) : ViewModel() {

    val uiState: StateFlow<PrincipleListUiState> = repo.observeAll()
        .map { PrincipleListUiState(items = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrincipleListUiState(),
        )

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun toggleActive(principle: TradingPrinciple) {
        viewModelScope.launch {
            repo.save(principle.copy(isActive = !principle.isActive, updatedAt = Instant.now()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipleListScreen(
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: PrincipleListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("매매 원칙", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = "원칙 추가")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> ListLoadingSkeleton(modifier = Modifier.padding(padding))

            state.items.isEmpty() -> EmptyState(
                modifier = Modifier.padding(padding),
                title = "아직 등록된 원칙이 없어요",
                description = "+ 버튼으로 진입·청산·자금관리·심리 원칙을 추가해보세요.",
                icon = "📏",
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = AppTokens.space16,
                    vertical = AppTokens.space12,
                ),
                verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(state.items, key = { it.id }) { p ->
                    PrincipleCard(
                        principle = p,
                        onClick = { onEditClick(p.id) },
                        onToggleActive = { viewModel.toggleActive(p) },
                        onDelete = { viewModel.delete(p.id) },
                    )
                }
                item { Spacer(Modifier.height(AppTokens.space24)) }
            }
        }
    }
}

@Composable
private fun PrincipleCard(
    principle: TradingPrinciple,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(onClick = onClick, padding = AppTokens.space16) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(label = principle.category.label())
                Spacer(Modifier.width(AppTokens.space8))
                Text(
                    "★".repeat(principle.weight),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Switch(checked = principle.isActive, onCheckedChange = { onToggleActive() })
            }
            Text(
                principle.ruleText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (principle.isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(AppTokens.space4))
                    Text("삭제")
                }
                TextButton(onClick = onClick) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(AppTokens.space4))
                    Text("편집")
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(label: String) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(AppTokens.radius8),
            )
            .padding(horizontal = AppTokens.space8, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
