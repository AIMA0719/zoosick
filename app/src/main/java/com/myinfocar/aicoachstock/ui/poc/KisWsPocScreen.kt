package com.myinfocar.aicoachstock.ui.poc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myinfocar.aicoachstock.data.remote.kis.ws.KisWebSocketStream
import com.myinfocar.aicoachstock.data.remote.kis.ws.KisWsMarketDataService
import com.myinfocar.aicoachstock.domain.market.ConnectionState
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.SubscriptionReason
import com.myinfocar.aicoachstock.domain.model.SubscriptionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PoC #2 검증 화면 — 한투 OpenAPI WebSocket.
 *
 *  1) connect → approval_key 자동 발급 + WS 접속
 *  2) 종목코드 입력 (예: 005930 삼성전자) → subscribe
 *  3) raw 메시지 + 파싱된 tick 동시 표시
 *  4) PINGPONG echo는 자동 (UI 노출 X)
 */
data class KisWsPocUi(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val lastTick: MarketTick? = null,
    val rawLines: List<String> = emptyList(),
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
    /** WS CONNECTED 시점부터의 경과 millis. DISCONNECTED면 null. FGS 30분+ 유지 검증용. */
    val elapsedMs: Long? = null,
)

@HiltViewModel
class KisWsPocViewModel @Inject constructor(
    private val stream: KisWebSocketStream,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(KisWsPocUi())
    val ui: StateFlow<KisWsPocUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            stream.connectionState.collectLatest { state ->
                _ui.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            stream.rawMessages.collectLatest { line ->
                _ui.update { it.copy(rawLines = (listOf(line) + it.rawLines).take(MAX_RAW_LINES)) }
            }
        }
        // 1Hz elapsed time tick — connectedSinceEpochMillis가 null이면 elapsedMs도 null.
        viewModelScope.launch {
            combine(stream.connectedSinceEpochMillis, oneHzTicker()) { since, _ ->
                since?.let { System.currentTimeMillis() - it }
            }.collect { elapsed ->
                _ui.update { it.copy(elapsedMs = elapsed) }
            }
        }
    }

    fun startForegroundService() {
        KisWsMarketDataService.start(appContext)
    }

    fun stopForegroundService() {
        KisWsMarketDataService.stop(appContext)
    }

    private fun oneHzTicker() = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    fun connect() {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                stream.connect()
            } catch (t: Throwable) {
                _ui.update { it.copy(errorMessage = "연결 실패: ${t.message}") }
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            stream.disconnect()
            _ui.update { it.copy(lastTick = null, rawLines = emptyList()) }
        }
    }

    fun subscribe(ticker: String) {
        val cleaned = ticker.trim().padStart(6, '0').takeIf { it.length == 6 } ?: return
        stream.subscribe(
            listOf(
                SubscriptionTarget(
                    ticker = cleaned,
                    market = Market.KR,
                    reason = SubscriptionReason.WATCHLIST,
                    priority = 0,
                )
            )
        )
        viewModelScope.launch {
            stream.ticks(cleaned).collectLatest { tick ->
                _ui.update { it.copy(lastTick = tick) }
            }
        }
    }

    private companion object {
        const val MAX_RAW_LINES = 30
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KisWsPocScreen(
    onBack: () -> Unit,
    viewModel: KisWsPocViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    var ticker by rememberSaveable { mutableStateOf("005930") }
    val context = LocalContext.current

    // POST_NOTIFICATIONS 권한 launcher (Android 13+). 권한 받으면 FGS 시작.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startForegroundService()
    }

    fun tryStartFgs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.startForegroundService()
            else notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startForegroundService()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("한투 WS PoC") },
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
            ConnectionCard(
                state = state.connectionState,
                isBusy = state.isBusy,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
            )
            ForegroundServiceCard(
                state = state.connectionState,
                elapsedMs = state.elapsedMs,
                onStartFgs = ::tryStartFgs,
                onStopFgs = viewModel::stopForegroundService,
            )
            SubscribeCard(
                connected = state.connectionState == ConnectionState.CONNECTED,
                ticker = ticker,
                onTickerChange = { ticker = it.filter { c -> c.isDigit() }.take(6) },
                onSubscribe = { viewModel.subscribe(ticker) },
            )
            state.lastTick?.let { TickCard(it) }
            if (state.rawLines.isNotEmpty()) RawMessagesCard(state.rawLines)
            state.errorMessage?.let { msg ->
                Text("❌ $msg", color = MaterialTheme.colorScheme.error)
            }

            Text(
                "장 시간(KST 09:00–15:30) 아니면 tick이 안 와요. 비장시간엔 raw 메시지가 구독 ACK만 보일 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForegroundServiceCard(
    state: ConnectionState,
    elapsedMs: Long?,
    onStartFgs: () -> Unit,
    onStopFgs: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Foreground Service (30분+ 유지)", style = MaterialTheme.typography.titleMedium)
            Text(
                "FGS 시작 시 알림 권한 요청 → dataSync 타입으로 백그라운드에서도 WS 유지. 30분 통과하면 ✅ 마크.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val elapsedText = elapsedMs?.let(::formatElapsed) ?: "—"
            val passed30min = (elapsedMs ?: 0L) >= 30L * 60 * 1000
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "경과: $elapsedText",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (passed30min) {
                    Text("✅ 30분+", color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartFgs) { Text("FGS 시작") }
                OutlinedButton(onClick = onStopFgs) { Text("FGS 종료") }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun ConnectionCard(
    state: ConnectionState,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("연결", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(state.name) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = when (state) {
                            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                            ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                            ConnectionState.DEGRADED -> MaterialTheme.colorScheme.error
                            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = !isBusy && state == ConnectionState.DISCONNECTED,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("approval_key 발급 + WS 연결")
                    }
                }
                if (state == ConnectionState.CONNECTED) {
                    OutlinedButton(onClick = onDisconnect) { Text("연결 종료") }
                }
            }
        }
    }
}

@Composable
private fun SubscribeCard(
    connected: Boolean,
    ticker: String,
    onTickerChange: (String) -> Unit,
    onSubscribe: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("종목 구독 (H0STCNT0 국내 체결가)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = ticker,
                onValueChange = onTickerChange,
                label = { Text("종목코드 (6자리)") },
                placeholder = { Text("005930 = 삼성전자") },
                enabled = connected,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSubscribe,
                enabled = connected && ticker.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("구독") }
        }
    }
}

@Composable
private fun TickCard(tick: MarketTick) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("✅ 마지막 Tick (파싱 성공)", style = MaterialTheme.typography.titleMedium)
            Text("${tick.ticker}  ·  ${tick.lastTickAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("현재가: ${"%,.0f".format(tick.price)}원", style = MaterialTheme.typography.headlineSmall)
            tick.change?.let { Text("전일대비: ${"%+,.0f".format(it)}원") }
            tick.changePct?.let { Text("등락률: ${"%+.2f".format(it)}%") }
            tick.volumeCum?.let { Text("누적거래량: ${"%,d".format(it)}") }
        }
    }
}

@Composable
private fun RawMessagesCard(lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Raw 메시지 (최근 30건, 디버깅용)", style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
