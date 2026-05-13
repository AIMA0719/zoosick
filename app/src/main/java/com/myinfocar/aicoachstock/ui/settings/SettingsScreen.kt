package com.myinfocar.aicoachstock.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.auth.ApiCredentials
import com.myinfocar.aicoachstock.domain.auth.KisEnv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val creds: ApiCredentials? = null,
    val isTokenLoading: Boolean = false,
    val isApprovalLoading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: ApiCredentialStore,
    private val authService: KisAuthService,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            store.observeCredentials().collect { creds ->
                _ui.update { it.copy(creds = creds, isLoading = false) }
            }
        }
    }

    fun saveKey(appKey: String, appSecret: String, env: KisEnv) {
        viewModelScope.launch {
            store.saveAppKey(appKey.trim(), appSecret.trim(), env)
            _ui.update { it.copy(message = "키 저장 완료") }
        }
    }

    fun testToken() {
        _ui.update { it.copy(isTokenLoading = true, message = null) }
        viewModelScope.launch {
            val result = authService.refreshAccessToken()
            _ui.update {
                it.copy(
                    isTokenLoading = false,
                    message = result.fold(
                        onSuccess = { "✅ access_token 발급 성공" },
                        onFailure = { e -> "❌ access_token 발급 실패: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun testApproval() {
        _ui.update { it.copy(isApprovalLoading = true, message = null) }
        viewModelScope.launch {
            val result = authService.refreshApprovalKey()
            _ui.update {
                it.copy(
                    isApprovalLoading = false,
                    message = result.fold(
                        onSuccess = { "✅ approval_key 발급 성공" },
                        onFailure = { e -> "❌ approval_key 발급 실패: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
            _ui.update { it.copy(message = "키·토큰 모두 초기화 완료") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLlmPoc: () -> Unit = {},
    onOpenKisWsPoc: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
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
            ApiKeySection(
                creds = state.creds,
                onSave = viewModel::saveKey,
                onClear = viewModel::clearAll,
            )
            HorizontalDivider()
            Text("AI 모델", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gemma 4 E4B 다운로드 / 로드 / 추론 PoC. 본 개발 전 검증용.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenLlmPoc,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("AI 모델 PoC 열기") }
            OutlinedButton(
                onClick = onOpenKisWsPoc,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("한투 WS PoC 열기") }
            HorizontalDivider()
            TokenSection(
                creds = state.creds,
                isTokenLoading = state.isTokenLoading,
                isApprovalLoading = state.isApprovalLoading,
                onTestToken = viewModel::testToken,
                onTestApproval = viewModel::testApproval,
            )
            state.message?.let { msg ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissMessage,
                    title = { Text("알림") },
                    text = { Text(msg) },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissMessage) { Text("확인") }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeySection(
    creds: ApiCredentials?,
    onSave: (appKey: String, appSecret: String, env: KisEnv) -> Unit,
    onClear: () -> Unit,
) {
    var appKey by rememberSaveable(creds?.appKey) { mutableStateOf(creds?.appKey.orEmpty()) }
    var appSecret by rememberSaveable(creds?.appSecret) { mutableStateOf(creds?.appSecret.orEmpty()) }
    var env by rememberSaveable(creds?.env) { mutableStateOf(creds?.env ?: KisEnv.PROD) }
    var secretVisible by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    Text("한투 OpenAPI 인증", style = MaterialTheme.typography.titleMedium)
    Text(
        "App Key/Secret은 EncryptedSharedPreferences에 암호화 저장됩니다. 외부 전송 없음.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("환경", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        KisEnv.entries.forEachIndexed { i, e ->
            SegmentedButton(
                selected = env == e,
                onClick = { env = e },
                shape = SegmentedButtonDefaults.itemShape(i, KisEnv.entries.size),
            ) { Text(if (e == KisEnv.PROD) "실전 (PROD)" else "모의 (VTS)") }
        }
    }

    OutlinedTextField(
        value = appKey,
        onValueChange = { appKey = it.trim() },
        label = { Text("App Key") },
        placeholder = { Text("PSMX...") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = appSecret,
        onValueChange = { appSecret = it.trim() },
        label = { Text("App Secret") },
        singleLine = true,
        visualTransformation = if (secretVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { secretVisible = !secretVisible }) {
                Text(if (secretVisible) "숨기기" else "표시")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onSave(appKey, appSecret, env) },
            enabled = appKey.isNotBlank() && appSecret.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) { Text("저장") }

        if (creds != null) {
            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.weight(1f),
            ) { Text("초기화") }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("키 초기화") },
            text = { Text("저장된 App Key/Secret/Token을 모두 삭제합니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClear()
                }) { Text("초기화", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun TokenSection(
    creds: ApiCredentials?,
    isTokenLoading: Boolean,
    isApprovalLoading: Boolean,
    onTestToken: () -> Unit,
    onTestApproval: () -> Unit,
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }

    Text("발급 테스트", style = MaterialTheme.typography.titleMedium)
    Text(
        "App Key/Secret 저장 후 동작 검증.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val canTest = creds != null

    // Access Token
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("REST access_token", style = MaterialTheme.typography.bodyLarge)
            val tokenStatus = when {
                creds?.accessToken == null -> "미발급"
                creds.accessTokenExpiresAt == null -> "발급됨"
                else -> "발급 ~ ${dateFormatter.format(creds.accessTokenExpiresAt)}"
            }
            Text(
                tokenStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onTestToken,
            enabled = canTest && !isTokenLoading,
        ) {
            if (isTokenLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("발급")
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    // Approval Key
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("WS approval_key", style = MaterialTheme.typography.bodyLarge)
            val approvalStatus = when {
                creds?.approvalKey == null -> "미발급"
                creds.approvalKeyExpiresAt == null -> "발급됨"
                else -> "발급 ~ ${dateFormatter.format(creds.approvalKeyExpiresAt)}"
            }
            Text(
                approvalStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onTestApproval,
            enabled = canTest && !isApprovalLoading,
        ) {
            if (isApprovalLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("발급")
            }
        }
    }
}
