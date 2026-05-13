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

    /**
     * suspend: 호출자가 이 함수 완료 후 `repo.delete(id)`를 호출하기 때문에
     * 구현체는 동기적으로 ticker를 확보하고 WS unsubscribe까지 끝낸 뒤 return해야 함.
     * (과거: 비동기 launch에서 repo.findById를 했더니 delete가 먼저 끝나서 ticker=null → 구독 leak)
     */
    suspend fun cancel(alertId: String)

    /** 앱 시작 시 활성 알림들을 한 번에 재등록. */
    fun reschedule(activeAlerts: List<PriceAlert>)
}
