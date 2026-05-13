package com.myinfocar.aicoachstock.data.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.myinfocar.aicoachstock.R
import com.myinfocar.aicoachstock.domain.model.PriceAlert
import com.myinfocar.aicoachstock.domain.model.PriceAlertType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 가격 알림 트리거 시 사용자에게 푸시 알림 발송. */
@Singleton
class PriceAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        createChannelIfNeeded()
    }

    fun notify(alert: PriceAlert, currentPrice: Double) {
        val (title, emoji) = when (alert.type) {
            PriceAlertType.STOP_LOSS -> "손절 도달" to "🔴"
            PriceAlertType.TAKE_PROFIT -> "익절 도달" to "🟢"
        }
        val body = buildString {
            append("$emoji ${alert.ticker} 현재가 ")
            append("%.2f".format(currentPrice))
            append(" (목표 ")
            append("%.2f".format(alert.targetPrice))
            append(")")
            alert.aiMessage?.let { append("\n$it") }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("${alert.ticker} ${"%.2f".format(currentPrice)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(alert.id.hashCode(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "가격 알림 (손절/익절)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "설정한 가격 도달 시 즉시 알림"
        }
        nm.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "price_alert_v1"
    }
}
