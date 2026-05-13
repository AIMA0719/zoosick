package com.myinfocar.aicoachstock.data.remote.kis.ws

import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.market.ConnectionState
import com.myinfocar.aicoachstock.domain.market.MarketDataStream
import com.myinfocar.aicoachstock.domain.market.MarketHours
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.SubscriptionTarget
import com.myinfocar.aicoachstock.domain.model.TickSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 OpenAPI WebSocket 클라이언트.
 *
 *  - URL은 ApiCredentials.env.wsUrl(ws://). network_security_config가 도메인 cleartext 예외 허용.
 *  - 구독: tr_id=H0STCNT0 (국내 실시간 체결가). approval_key는 ensureApprovalKey()로 자동 발급.
 *  - heartbeat: 한투가 60초마다 보내는 PINGPONG을 동일 페이로드로 echo.
 *  - tick 응답: `0|H0STCNT0|<count>|<caret-fields>` 평문. 첫 char 1은 암호화 — Phase 1 OOS.
 *
 * PoC 단순화 (본 개발에서 강화):
 *  - 41종목 한도는 sort + take로 단순 컷. priority 큐 + 자동 재구독 본 개발에서.
 *  - 지수 백오프 재연결 본 개발에서 (PoC는 connect/disconnect 명시 호출만).
 *
 * PoC 디버깅용으로 rawMessages SharedFlow 노출 — 본 개발에서는 인터페이스 외부 API 제거.
 */
@Singleton
class KisWebSocketStream @Inject constructor(
    okHttpClient: OkHttpClient,
    private val store: ApiCredentialStore,
    private val authService: KisAuthService,
    private val json: Json,
) : MarketDataStream {

    private val client: OkHttpClient = okHttpClient.newBuilder()
        // OkHttp가 보내는 자동 ping은 끔 — 한투의 PINGPONG 시스템과 충돌 방지.
        .pingInterval(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SubscriptionTarget>>(emptyList())
    override val currentSubscriptions: StateFlow<List<SubscriptionTarget>> = _subscriptions.asStateFlow()

    /** WS가 마지막으로 CONNECTED된 epoch millis. DISCONNECTED·실패 시 null. PoC #3 elapsed 측정용. */
    private val _connectedSinceEpochMillis = MutableStateFlow<Long?>(null)
    val connectedSinceEpochMillis: StateFlow<Long?> = _connectedSinceEpochMillis.asStateFlow()

    private val tickBus = MutableSharedFlow<MarketTick>(extraBufferCapacity = 256)

    /** PoC 디버깅용 — 도착한 원본 텍스트(첫 1KB까지 truncate)를 그대로 흘려준다. */
    private val _rawMessages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val rawMessages: SharedFlow<String> = _rawMessages.asSharedFlow()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var activeApprovalKey: String? = null

    /** 의도된 연결(사용자가 connect 요청한 상태)인지. true면 onClosed/onFailure에서 자동 재연결. */
    @Volatile
    private var intendedConnected: Boolean = false

    /** 재연결 시도 회수. 성공 시 0으로 리셋. */
    @Volatile
    private var reconnectAttempts: Int = 0

    /** 재연결 대기 중인 job — 중복 트리거 방지. */
    @Volatile
    private var reconnectJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect() {
        intendedConnected = true
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        val creds = store.current()
        if (creds == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            error("API 키 미설정 — Settings에서 App Key/Secret을 먼저 저장하세요.")
        }

        if (!MarketHours.anyOpen()) {
            // 비장시간이면 연결을 시도하지 않고 DEGRADED 상태로 대기.
            // 본 개발에서 AlarmManager로 장 시작 1분 전 자동 재시도.
            Timber.i("KIS WS: 비장시간 — 연결 보류, DEGRADED 상태로 유지")
            _connectionState.value = ConnectionState.DEGRADED
            return
        }

        openOnce(creds.env.wsUrl)
    }

    private suspend fun openOnce(wsUrl: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val approvalKey = authService.ensureApprovalKey().getOrElse { cause ->
            _connectionState.value = ConnectionState.DISCONNECTED
            throw cause
        }
        activeApprovalKey = approvalKey
        val request = Request.Builder().url(toHttpUrl(wsUrl)).build()
        webSocket = client.newWebSocket(request, Listener())
    }

    override suspend fun disconnect() {
        intendedConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(NORMAL_CLOSE, "client disconnect")
        webSocket = null
        activeApprovalKey = null
        _subscriptions.value = emptyList()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedSinceEpochMillis.value = null
        reconnectAttempts = 0
    }

    override fun subscribe(targets: List<SubscriptionTarget>) {
        val ws = webSocket ?: run {
            // 연결 안 됐어도 desired 구독 목록은 유지. 재연결 시 자동 재구독.
            _subscriptions.value = targets.sortedBy { it.priority }.take(SUBSCRIPTION_LIMIT)
            return
        }
        val approvalKey = activeApprovalKey ?: return
        val newDesired = targets.sortedBy { it.priority }.take(SUBSCRIPTION_LIMIT)
        val currentTickers = _subscriptions.value.map { it.ticker }.toSet()
        val newTickers = newDesired.map { it.ticker }.toSet()

        val toAdd = newDesired.filter { it.ticker !in currentTickers }
        val toRemove = currentTickers - newTickers

        toRemove.forEach { ws.send(buildControlMessage(approvalKey, it, register = false)) }
        toAdd.forEach { ws.send(buildControlMessage(approvalKey, it.ticker, register = true)) }

        _subscriptions.value = newDesired
    }

    override fun unsubscribe(tickers: List<String>) {
        val ws = webSocket
        val approvalKey = activeApprovalKey
        if (ws != null && approvalKey != null) {
            tickers.forEach { t ->
                ws.send(buildControlMessage(approvalKey, t, register = false))
            }
        }
        _subscriptions.update { list -> list.filterNot { it.ticker in tickers } }
    }

    /** 연결 직후 desired 구독을 일괄 재등록. */
    private fun resubscribeAll() {
        val ws = webSocket ?: return
        val approvalKey = activeApprovalKey ?: return
        _subscriptions.value.forEach { target ->
            ws.send(buildControlMessage(approvalKey, target.ticker, register = true))
        }
    }

    /** 지수 백오프 재연결 스케줄. 1→2→4→8→16→30s 상한, 최대 무한 시도 (사용자가 disconnect 호출 시까지). */
    private fun scheduleReconnect() {
        if (!intendedConnected) return
        if (reconnectJob?.isActive == true) return
        if (!MarketHours.anyOpen()) {
            // 비장시간이면 DEGRADED 상태로 두고 대기.
            _connectionState.value = ConnectionState.DEGRADED
            return
        }
        val attempt = reconnectAttempts + 1
        reconnectAttempts = attempt
        val delaySec = minOf(30L, (1L shl (attempt - 1).coerceAtMost(5)))
        Timber.i("KIS WS: ${delaySec}s 후 재연결 시도 #$attempt")
        _connectionState.value = ConnectionState.DEGRADED
        reconnectJob = scope.launch {
            delay(delaySec * 1000L)
            if (!intendedConnected) return@launch
            val creds = store.current() ?: return@launch
            runCatching { openOnce(creds.env.wsUrl) }
                .onFailure { Timber.w(it, "KIS WS 재연결 실패 #$attempt") }
        }
    }

    override fun ticks(ticker: String): Flow<MarketTick> =
        tickBus.asSharedFlow().filter { it.ticker == ticker }

    /** ws://... 그대로 OkHttp Request.Builder에 url로 넣으면 OkHttp가 거부 → http://...로 치환. */
    private fun toHttpUrl(wsUrl: String): String = wsUrl
        .replaceFirst("ws://", "http://")
        .replaceFirst("wss://", "https://")

    private fun buildControlMessage(
        approvalKey: String,
        ticker: String,
        register: Boolean,
    ): String {
        val payload = buildJsonObject {
            put("header", buildJsonObject {
                put("approval_key", approvalKey)
                put("custtype", "P")
                put("tr_type", if (register) "1" else "2")
                put("content-type", "utf-8")
            })
            put("body", buildJsonObject {
                put("input", buildJsonObject {
                    put("tr_id", TR_ID_KOSPI_EXEC)
                    put("tr_key", ticker)
                })
            })
        }
        return json.encodeToString(JsonElement.serializer(), payload)
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.i("KIS WS 연결 성공 code=${response.code}")
            _connectedSinceEpochMillis.value = System.currentTimeMillis()
            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0
            // 의도된 desired 구독 자동 재등록 (재연결 후 복구).
            resubscribeAll()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _rawMessages.tryEmit(text.take(MAX_RAW_LOG_CHARS))

            if (text.startsWith("{")) {
                handleJsonControl(webSocket, text)
            } else {
                handleTickFrame(text)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.i("KIS WS 종료 code=$code reason=$reason")
            _connectedSinceEpochMillis.value = null
            this@KisWebSocketStream.webSocket = null
            if (intendedConnected) {
                scheduleReconnect()
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.w(t, "KIS WS 실패 code=${response?.code}")
            _connectedSinceEpochMillis.value = null
            this@KisWebSocketStream.webSocket = null
            if (intendedConnected) {
                scheduleReconnect()
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun handleJsonControl(webSocket: WebSocket, text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val trId = obj["header"]?.jsonObject?.get("tr_id")?.jsonPrimitive?.contentOrNull
        if (trId == TR_ID_PINGPONG) {
            // 한투 규칙: 받은 PINGPONG 페이로드 그대로 echo.
            webSocket.send(text)
        }
        // 구독 ACK는 별도 처리 없음 (rt_cd "0"이면 성공). 본 개발에서 화면 indicator.
    }

    private fun handleTickFrame(text: String) {
        // 포맷: `<encrypted-flag>|<tr_id>|<count>|<caret-fields>`
        val parts = text.split("|", limit = 4)
        if (parts.size < 4) return
        val encrypted = parts[0]
        if (encrypted != "0") return // 0=평문만 처리, 1=암호화 (Phase 1 OOS)
        val trId = parts[1]
        if (trId != TR_ID_KOSPI_EXEC) return
        val countSafe = parts[2].toIntOrNull() ?: 1
        val payload = parts[3]

        val fields = payload.split("^")
        // H0STCNT0: count*46 필드 (사양). count > 1 시 chunked로 각 종목 분리.
        // 최소 필드 수보다 부족하면 skip.
        if (fields.size < KOSPI_EXEC_FIELDS_REQUIRED) return

        val perTick = KOSPI_EXEC_FIELDS_PER_TICK
        if (fields.size >= countSafe * perTick) {
            fields.chunked(perTick).take(countSafe).forEach { chunk ->
                parseKospiExecTick(chunk)?.let { tickBus.tryEmit(it) }
            }
        } else {
            // 사양과 다른 케이스: 첫 종목만 안전 처리.
            parseKospiExecTick(fields)?.let { tickBus.tryEmit(it) }
        }
    }

    /**
     * H0STCNT0 필드 인덱스 (한투 OpenAPI 명세):
     *   0: MKSC_SHRN_ISCD (종목코드)
     *   1: STCK_CNTG_HOUR (체결시간 HHMMSS)
     *   2: STCK_PRPR (현재가)
     *   3: PRDY_VRSS_SIGN (전일대비부호 1상한 2상승 3보합 4하한 5하락)
     *   4: PRDY_VRSS (전일대비)
     *   5: PRDY_CTRT (전일대비율 %)
     *  13: ACML_VOL (누적거래량)
     */
    private fun parseKospiExecTick(fields: List<String>): MarketTick? {
        val ticker = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val price = fields.getOrNull(2)?.toDoubleOrNull() ?: return null
        val sign = fields.getOrNull(3)
        val changeAbs = fields.getOrNull(4)?.toDoubleOrNull()
        val signedChange = when (sign) {
            "4", "5" -> changeAbs?.unaryMinus() // 하한/하락
            else -> changeAbs
        }
        val changePct = fields.getOrNull(5)?.toDoubleOrNull()
        val volume = fields.getOrNull(13)?.toLongOrNull()
        return MarketTick(
            ticker = ticker,
            price = price,
            change = signedChange,
            changePct = changePct,
            volumeCum = volume,
            lastTickAt = Instant.now(),
            source = TickSource.WS_LIVE,
        )
    }

    private companion object {
        const val TR_ID_KOSPI_EXEC = "H0STCNT0"
        const val TR_ID_PINGPONG = "PINGPONG"
        const val SUBSCRIPTION_LIMIT = 41
        const val NORMAL_CLOSE = 1000
        const val MAX_RAW_LOG_CHARS = 1024
        const val KOSPI_EXEC_FIELDS_REQUIRED = 14
        const val KOSPI_EXEC_FIELDS_PER_TICK = 46
    }
}
