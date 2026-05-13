package com.myinfocar.aicoachstock.data.remote.kis.ws

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myinfocar.aicoachstock.R
import com.myinfocar.aicoachstock.domain.market.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * PoC #3 — 한투 WebSocket 시세 구독을 Foreground Service로 30분+ 유지.
 *
 *  - foregroundServiceType="dataSync" (Android 14+ 필수)
 *  - 알림 채널: LOW 중요도 (소리 없음), ongoing.
 *  - onCreate → 채널 생성 → startForeground → KisWebSocketStream.connect()
 *  - connectionState / currentSubscriptions 변화 시 알림 본문 갱신
 *  - onDestroy → WS disconnect (runBlocking로 즉시 종료)
 *
 * 사용자 액션:
 *  - PoC 화면에서 "FGS 시작" 버튼 → ContextCompat.startForegroundService
 *  - "FGS 종료" 버튼 → context.stopService
 *
 * Doze/배터리 최적화는 본 개발에서 화이트리스트 안내 화면 추가 예정.
 */
@AndroidEntryPoint
class KisWsMarketDataService : Service() {

    @Inject lateinit var stream: KisWebSocketStream

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startInForeground(initialText = "시세 연결 시도 중…")

        scope.launch {
            try {
                stream.connect()
            } catch (t: Throwable) {
                Timber.w(t, "FGS에서 WS connect 실패")
                stopSelf()
            }
        }

        // 상태 변화 시 알림 본문 갱신.
        scope.launch {
            combine(stream.connectionState, stream.currentSubscriptions) { state, subs ->
                state to subs.size
            }.collect { (state, subCount) ->
                val text = when (state) {
                    ConnectionState.CONNECTED -> "✅ 연결됨 · 구독 ${subCount}개"
                    ConnectionState.CONNECTING -> "연결 시도 중…"
                    ConnectionState.DEGRADED -> "WS 끊김 · REST 폴백 중"
                    ConnectionState.DISCONNECTED -> "연결 안 됨"
                }
                notify(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // disconnect는 짧음 (WS close 메시지 한 번). main thread block 허용 범위.
        runBlocking { runCatching { stream.disconnect() } }
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(initialText: String) {
        val notification = buildNotification(initialText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: foregroundServiceType 명시 (매니페스트 + startForeground 호출 둘 다).
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AICoachStock — 실시간 시세")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "실시간 시세 연결",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "한투 WebSocket 구독 유지 상태 알림"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "kis_ws_market_data_v1"
        private const val NOTIFICATION_ID = 0xA001

        fun start(context: Context) {
            val intent = Intent(context, KisWsMarketDataService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, KisWsMarketDataService::class.java)
            context.stopService(intent)
        }
    }
}
