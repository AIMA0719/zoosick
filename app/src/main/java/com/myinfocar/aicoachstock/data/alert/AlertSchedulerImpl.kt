package com.myinfocar.aicoachstock.data.alert

import com.myinfocar.aicoachstock.domain.alert.AlertScheduler
import com.myinfocar.aicoachstock.domain.market.MarketDataStream
import com.myinfocar.aicoachstock.domain.market.MarketHours
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.MarketTick
import com.myinfocar.aicoachstock.domain.model.PriceAlert
import com.myinfocar.aicoachstock.domain.model.PriceAlertDirection
import com.myinfocar.aicoachstock.domain.model.PriceAlertStatus
import com.myinfocar.aicoachstock.domain.model.SubscriptionReason
import com.myinfocar.aicoachstock.domain.model.SubscriptionTarget
import com.myinfocar.aicoachstock.domain.repository.PriceAlertRepository
import com.myinfocar.aicoachstock.domain.repository.StockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 활성 PriceAlert를 MarketDataStream tick에 연결.
 *
 *  - register(): 알림을 ACTIVE_ALERT 우선순위로 WS 구독에 추가, 종목 tick을 collect해서 도달 감지.
 *  - tick.price가 direction에 따라 targetPrice를 넘어서면 PriceAlertNotifier로 푸시 + status=TRIGGERED.
 *  - cancel(): 구독 collect 해제 + WS unsubscribe (다른 알림이 같은 ticker를 잡고 있지 않으면).
 *
 *  본 구현은 FGS(KisWsMarketDataService) 안에서 호출되는 컴포넌트.
 */
@Singleton
class AlertSchedulerImpl @Inject constructor(
    private val stream: MarketDataStream,
    private val repo: PriceAlertRepository,
    private val notifier: PriceAlertNotifier,
    private val stockRepo: StockRepository,
) : AlertScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** alertId → tick collect Job. */
    private val watchJobs = ConcurrentHashMap<String, Job>()

    override fun register(alert: PriceAlert) {
        if (alert.status != PriceAlertStatus.ACTIVE) return
        if (watchJobs.containsKey(alert.id)) return

        scope.launch {
            val stock = stockRepo.findByTicker(alert.ticker)
            val market = stock?.market ?: Market.KR
            if (!MarketHours.isOpen(market)) {
                Timber.d("AlertScheduler: ${alert.ticker} 비장시간 — 구독 보류")
            }
            val target = SubscriptionTarget(
                ticker = alert.ticker,
                market = market,
                reason = SubscriptionReason.ACTIVE_ALERT,
                priority = 0, // ACTIVE_ALERT은 항상 최우선
            )
            // 기존 구독에 추가 (스트림이 currentSubscriptions 유지하므로 합쳐 호출).
            val merged = (stream.currentSubscriptions.value + target)
                .distinctBy { it.ticker }
            stream.subscribe(merged)
        }

        val job = stream.ticks(alert.ticker)
            .onEach { tick -> evaluate(alert, tick) }
            .launchIn(scope)
        watchJobs[alert.id] = job
    }

    private suspend fun evaluate(alert: PriceAlert, tick: MarketTick) {
        val reached = when (alert.direction) {
            PriceAlertDirection.ABOVE -> tick.price >= alert.targetPrice
            PriceAlertDirection.BELOW -> tick.price <= alert.targetPrice
        }
        if (!reached) return

        // Atomic CAS: ACTIVE → TRIGGERED 성공 시에만 알림. 동시 cancel/delete가 있어도 유령 알림 방지.
        val claimed = repo.markTriggeredIfActive(alert.id, Instant.now())
        if (!claimed) return

        val fresh = repo.findById(alert.id) ?: return
        notifier.notify(fresh, tick.price)

        watchJobs.remove(alert.id)?.cancel()
        val stillUsed = stream.currentSubscriptions.value.any { it.ticker == alert.ticker } &&
            watchJobs.keys.any { otherId ->
                repo.findById(otherId)?.ticker == alert.ticker
            }
        if (!stillUsed) stream.unsubscribe(listOf(alert.ticker))
    }

    override suspend fun cancel(alertId: String) {
        // delete()가 호출되기 전에 ticker를 확보해야 한다 — 호출 순서는 cancel() → repo.delete().
        // 비동기 launch로 미루면 delete가 먼저 완료돼 findById가 null이 되어 unsubscribe를 못함.
        val canceled = repo.findById(alertId)
        watchJobs.remove(alertId)?.cancel()
        if (canceled == null) return
        val ticker = canceled.ticker
        val stillUsed = watchJobs.keys.any { otherId ->
            repo.findById(otherId)?.ticker == ticker
        }
        if (!stillUsed) stream.unsubscribe(listOf(ticker))
    }

    override fun reschedule(activeAlerts: List<PriceAlert>) {
        // 기존 watch 모두 해제 후 다시 등록.
        watchJobs.values.forEach { it.cancel() }
        watchJobs.clear()
        activeAlerts.forEach { register(it) }
    }
}
