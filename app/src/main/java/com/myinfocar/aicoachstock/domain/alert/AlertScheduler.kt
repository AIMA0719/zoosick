package com.myinfocar.aicoachstock.domain.alert

import com.myinfocar.aicoachstock.domain.model.PriceAlert

/**
 * 가격 알림 스케줄링 추상화.
 *
 * 구현체는 PriceWatchService(Foreground Service)와 협력해 tick 단위 감지.
 * - 활성 알림 등록 시 해당 종목을 MarketDataStream에 ACTIVE_ALERT 우선순위로 구독 요청
 * - target_price 도달 시 NotificationCompat로 푸시 + status=TRIGGERED 갱신
 */
interface AlertScheduler {
    fun register(alert: PriceAlert)
    fun cancel(alertId: String)

    /** 앱 시작 시 활성 알림들을 한 번에 재등록. */
    fun reschedule(activeAlerts: List<PriceAlert>)
}
