package com.myinfocar.aicoachstock.domain.order

import com.myinfocar.aicoachstock.data.remote.kis.KisRateLimiter
import com.myinfocar.aicoachstock.data.remote.kis.auth.KisAuthService
import com.myinfocar.aicoachstock.data.remote.kis.dto.DomesticOrderCashRequest
import com.myinfocar.aicoachstock.data.remote.kis.dto.DomesticOrderRevisionRequest
import com.myinfocar.aicoachstock.data.remote.kis.dto.OrderResponse
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasOrderRequest
import com.myinfocar.aicoachstock.data.remote.kis.dto.OverseasOrderRevisionRequest
import com.myinfocar.aicoachstock.data.remote.kis.market.KisTradingApi
import com.myinfocar.aicoachstock.domain.auth.ApiCredentialStore
import com.myinfocar.aicoachstock.domain.auth.ApiCredentials
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.Order
import com.myinfocar.aicoachstock.domain.model.OrderStatus
import com.myinfocar.aicoachstock.domain.model.OrderType
import com.myinfocar.aicoachstock.domain.model.TradeSide
import com.myinfocar.aicoachstock.domain.repository.OrderRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 매수/매도 주문 송신 + 미체결 polling 도메인 서비스 (Stage 16 신설).
 *
 * 규칙 (PRD 04_PROJECT_SPEC.md Stage 14 개정):
 *  - 자동 발주 금지. UI는 반드시 BiometricPrompt를 거쳐 OrderConfirmation을 생성한 뒤 호출.
 *  - sendMutex로 동시 송신 차단(빠른 더블탭·복귀 시 이중 송신 방지).
 *  - 한투 raw msg_cd → 한국어 사용자 메시지로 매핑.
 *  - 주문 송신 직후 SUBMITTED 표시 후 5초 간격 30초 polling으로 FILLED/PARTIAL/CANCELED 갱신.
 *
 * 호가 단위(가격대별 1/5/10/50/100/500/1000원) 사용자 입력 검증은 UI 측(OrderEntryScreen)에서.
 */
@Singleton
class OrderService @Inject constructor(
    private val tradingApi: KisTradingApi,
    private val authService: KisAuthService,
    private val store: ApiCredentialStore,
    private val rateLimiter: KisRateLimiter,
    private val orderRepo: OrderRepository,
) {
    private val sendMutex = Mutex()

    /**
     * 신규 매수/매도/정정/취소 주문 송신.
     *
     * @return Result.success(Order) — SUBMITTED 상태의 Order. polling은 별도 호출자가 시작.
     *         Result.failure(IllegalStateException) — 송신 실패. Order는 REJECTED로 영구 기록됨.
     */
    suspend fun placeOrder(intent: OrderIntent): Result<Order> = sendMutex.withLock {
        Timber.i("OrderService.placeOrder source=${intent.confirmation.sourceFlow} type=${intent::class.simpleName}")
        val pending = buildPendingOrder(intent)
        orderRepo.upsert(pending)

        val creds = store.current()
            ?: return@withLock failOrder(pending, "API 키 미설정 — 설정에서 한투 키를 먼저 등록하세요")
        val accountNo = creds.accountNo
            ?: return@withLock failOrder(pending, "계좌번호 미설정 — 설정에서 한투 계좌번호를 등록하세요")
        val productCode = creds.productCode ?: "01"
        val token = authService.ensureAccessToken().getOrElse {
            return@withLock failOrder(pending, "한투 토큰 발급 실패: ${it.message}")
        }

        rateLimiter.await()
        val resp = runCatching {
            when (intent) {
                is OrderIntent.Place -> dispatchPlace(intent, creds, token, accountNo, productCode)
                is OrderIntent.Revise -> dispatchRevise(intent, creds, token, accountNo, productCode)
                is OrderIntent.Cancel -> dispatchCancel(intent, creds, token, accountNo, productCode)
            }
        }
        return@withLock resp.fold(
            onSuccess = { r -> handleOrderResponse(pending, r) },
            onFailure = { e ->
                Timber.w(e, "OrderService 네트워크 실패")
                failOrder(pending, e.message ?: "네트워크 오류")
            },
        )
    }

    private suspend fun dispatchPlace(
        intent: OrderIntent.Place,
        creds: ApiCredentials,
        token: String,
        accountNo: String,
        productCode: String,
    ): OrderResponse = when (intent.market) {
        Market.KR -> {
            val body = DomesticOrderCashRequest(
                accountNo = accountNo,
                productCode = productCode,
                ticker = intent.ticker,
                orderDivision = if (intent.orderType == OrderType.MARKET) "01" else "00",
                quantity = intent.qty.toString(),
                unitPrice = (intent.price ?: 0.0).toLong().toString(),
            )
            val url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/order-cash"
            val trId = if (intent.side == TradeSide.BUY) "TTTC0802U" else "TTTC0801U"
            if (intent.side == TradeSide.BUY) {
                tradingApi.placeDomesticBuy(url, "Bearer $token", creds.appKey, creds.appSecret, trId, body = body)
            } else {
                tradingApi.placeDomesticSell(url, "Bearer $token", creds.appKey, creds.appSecret, trId, body = body)
            }
        }
        Market.US -> {
            val excg = intent.excgCode ?: "NASD"
            val body = OverseasOrderRequest(
                accountNo = accountNo,
                productCode = productCode,
                excgCode = excg,
                ticker = intent.ticker,
                quantity = intent.qty.toString(),
                unitPrice = (intent.price ?: 0.0).toString(),
                orderDivision = if (intent.orderType == OrderType.MARKET) "31" else "00",
            )
            val url = creds.env.restBaseUrl + "/uapi/overseas-stock/v1/trading/order"
            if (intent.side == TradeSide.BUY) {
                tradingApi.placeOverseasBuy(url, "Bearer $token", creds.appKey, creds.appSecret, body = body)
            } else {
                tradingApi.placeOverseasSell(url, "Bearer $token", creds.appKey, creds.appSecret, body = body)
            }
        }
    }

    private suspend fun dispatchRevise(
        intent: OrderIntent.Revise,
        creds: ApiCredentials,
        token: String,
        accountNo: String,
        productCode: String,
    ): OrderResponse {
        val origin = intent.origin
        return when (origin.market) {
            Market.KR -> {
                val body = DomesticOrderRevisionRequest(
                    accountNo = accountNo,
                    productCode = productCode,
                    originOrgNo = origin.krxOrderOrgNo ?: "",
                    originOrderNo = origin.krxOrderNo ?: "",
                    orderDivision = if (origin.orderType == OrderType.MARKET) "01" else "00",
                    revCnclDvsn = "01",
                    quantity = intent.newQty.toString(),
                    unitPrice = (intent.newPrice ?: 0.0).toLong().toString(),
                )
                tradingApi.reviseDomesticOrder(
                    url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/order-rvsecncl",
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    body = body,
                )
            }
            Market.US -> {
                val body = OverseasOrderRevisionRequest(
                    accountNo = accountNo,
                    productCode = productCode,
                    excgCode = "NASD",
                    ticker = origin.ticker,
                    originOrderNo = origin.krxOrderNo ?: "",
                    revCnclDvsn = "01",
                    quantity = intent.newQty.toString(),
                    unitPrice = (intent.newPrice ?: 0.0).toString(),
                )
                tradingApi.reviseOverseasOrder(
                    url = creds.env.restBaseUrl + "/uapi/overseas-stock/v1/trading/order-rvsecncl",
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    body = body,
                )
            }
        }
    }

    private suspend fun dispatchCancel(
        intent: OrderIntent.Cancel,
        creds: ApiCredentials,
        token: String,
        accountNo: String,
        productCode: String,
    ): OrderResponse {
        val origin = intent.origin
        return when (origin.market) {
            Market.KR -> {
                val body = DomesticOrderRevisionRequest(
                    accountNo = accountNo,
                    productCode = productCode,
                    originOrgNo = origin.krxOrderOrgNo ?: "",
                    originOrderNo = origin.krxOrderNo ?: "",
                    orderDivision = if (origin.orderType == OrderType.MARKET) "01" else "00",
                    revCnclDvsn = "02",
                    quantity = origin.qty.toString(),
                    unitPrice = "0",
                    qtyAllOrdYn = "Y",
                )
                tradingApi.reviseDomesticOrder(
                    url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/order-rvsecncl",
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    body = body,
                )
            }
            Market.US -> {
                val body = OverseasOrderRevisionRequest(
                    accountNo = accountNo,
                    productCode = productCode,
                    excgCode = "NASD",
                    ticker = origin.ticker,
                    originOrderNo = origin.krxOrderNo ?: "",
                    revCnclDvsn = "02",
                    quantity = origin.qty.toString(),
                    unitPrice = "0",
                )
                tradingApi.reviseOverseasOrder(
                    url = creds.env.restBaseUrl + "/uapi/overseas-stock/v1/trading/order-rvsecncl",
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    body = body,
                )
            }
        }
    }

    private suspend fun handleOrderResponse(pending: Order, r: OrderResponse): Result<Order> {
        if (r.rtCd == "0") {
            val submitted = pending.copy(
                status = OrderStatus.SUBMITTED,
                krxOrderNo = r.output?.odno?.takeIf { it.isNotBlank() } ?: pending.krxOrderNo,
                krxOrderOrgNo = r.output?.krxFwdgOrdOrgno?.takeIf { it.isNotBlank() } ?: pending.krxOrderOrgNo,
                submittedAt = Instant.now(),
                rawMsgCd = r.msgCd,
            )
            orderRepo.upsert(submitted)
            return Result.success(submitted)
        }
        return failOrder(pending, mapKisError(r.msgCd, r.msg1), r.msgCd)
    }

    private suspend fun failOrder(pending: Order, msg: String, msgCd: String? = null): Result<Order> {
        val rejected = pending.copy(
            status = OrderStatus.REJECTED,
            errorMessage = msg,
            rawMsgCd = msgCd ?: pending.rawMsgCd,
            completedAt = Instant.now(),
        )
        orderRepo.upsert(rejected)
        Timber.w("OrderService 송신 실패 — $msg msgCd=$msgCd")
        return Result.failure(IllegalStateException(msg))
    }

    /**
     * 송신 직후 미체결 polling. 5초 간격 30초 timeout.
     *
     * 한투 미체결 응답에서 해당 ODNO를 찾으면 PARTIAL/SUBMITTED 유지.
     * 응답에 없으면 체결 완료 가능성 — Trade import는 Stage 9 TradeImportService가 별도로 처리하므로
     * 여기선 FILLED로 표시.
     */
    suspend fun pollExecution(orderId: String) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
            delay(POLL_INTERVAL_MS)
            val order = orderRepo.findById(orderId) ?: return
            if (order.isTerminal) return
            val updated = runCatching { checkExecution(order) }.getOrNull() ?: continue
            orderRepo.upsert(updated)
            if (updated.isTerminal) return
        }
    }

    private suspend fun checkExecution(order: Order): Order? {
        val odno = order.krxOrderNo ?: return null
        val creds = store.current() ?: return null
        val accountNo = creds.accountNo ?: return null
        val productCode = creds.productCode ?: "01"
        val token = authService.ensureAccessToken().getOrElse { return null }
        rateLimiter.await()
        return when (order.market) {
            Market.KR -> {
                val resp = tradingApi.fetchDomesticOpenOrders(
                    url = creds.env.restBaseUrl + "/uapi/domestic-stock/v1/trading/inquire-psbl-rvsecncl",
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    accountNo = accountNo,
                    productCode = productCode,
                )
                if (resp.rtCd != "0") return null
                val item = resp.output.firstOrNull { it.odno == odno }
                if (item == null) {
                    // 미체결에 없음 → 체결 완료 가능성
                    order.copy(status = OrderStatus.FILLED, completedAt = Instant.now())
                } else {
                    val filled = item.totCcldQty?.toIntOrNull() ?: 0
                    val partialStatus = if (filled in 1 until order.qty) OrderStatus.PARTIAL else OrderStatus.SUBMITTED
                    order.copy(status = partialStatus, filledQty = filled)
                }
            }
            Market.US -> {
                val resp = tradingApi.fetchOverseasOpenOrders(
                    url = creds.env.restBaseUrl + "/uapi/overseas-stock/v1/trading/inquire-nccs",
                    authorization = "Bearer $token",
                    appKey = creds.appKey,
                    appSecret = creds.appSecret,
                    accountNo = accountNo,
                    productCode = productCode,
                )
                if (resp.rtCd != "0") return null
                val item = resp.output.firstOrNull { it.odno == odno }
                if (item == null) {
                    order.copy(status = OrderStatus.FILLED, completedAt = Instant.now())
                } else {
                    val filled = item.totCcldQty?.toIntOrNull() ?: 0
                    val partialStatus = if (filled in 1 until order.qty) OrderStatus.PARTIAL else OrderStatus.SUBMITTED
                    order.copy(status = partialStatus, filledQty = filled)
                }
            }
        }
    }

    private fun buildPendingOrder(intent: OrderIntent): Order {
        val now = Instant.now()
        return when (intent) {
            is OrderIntent.Place -> Order(
                id = UUID.randomUUID().toString(),
                ticker = intent.ticker,
                market = intent.market,
                side = intent.side,
                orderType = intent.orderType,
                qty = intent.qty,
                price = intent.price,
                filledQty = 0,
                avgFillPrice = null,
                status = OrderStatus.PENDING,
                krxOrderNo = null,
                krxOrderOrgNo = null,
                originOrderNo = null,
                linkedPrincipleIds = intent.linkedPrincipleIds,
                createdAt = now,
                submittedAt = null,
                completedAt = null,
                errorMessage = null,
                rawMsgCd = null,
            )
            is OrderIntent.Revise -> Order(
                id = UUID.randomUUID().toString(),
                ticker = intent.origin.ticker,
                market = intent.origin.market,
                side = intent.origin.side,
                orderType = intent.origin.orderType,
                qty = intent.newQty,
                price = intent.newPrice,
                filledQty = 0,
                avgFillPrice = null,
                status = OrderStatus.PENDING,
                krxOrderNo = null,
                krxOrderOrgNo = null,
                originOrderNo = intent.origin.krxOrderNo,
                linkedPrincipleIds = intent.origin.linkedPrincipleIds,
                createdAt = now,
                submittedAt = null,
                completedAt = null,
                errorMessage = null,
                rawMsgCd = null,
            )
            is OrderIntent.Cancel -> Order(
                id = UUID.randomUUID().toString(),
                ticker = intent.origin.ticker,
                market = intent.origin.market,
                side = intent.origin.side,
                orderType = intent.origin.orderType,
                qty = intent.origin.qty,
                price = null,
                filledQty = 0,
                avgFillPrice = null,
                status = OrderStatus.PENDING,
                krxOrderNo = null,
                krxOrderOrgNo = null,
                originOrderNo = intent.origin.krxOrderNo,
                linkedPrincipleIds = intent.origin.linkedPrincipleIds,
                createdAt = now,
                submittedAt = null,
                completedAt = null,
                errorMessage = null,
                rawMsgCd = null,
            )
        }
    }

    /**
     * 한투 msg_cd → 한국어 사용자 메시지.
     *
     * 운영에서 자주 보는 코드만 매핑. 미매핑은 raw msg1을 그대로 노출하지 않고 "주문 실패 (msg_cd=…)" 형식.
     */
    private fun mapKisError(msgCd: String?, msg1: String?): String = when (msgCd) {
        "APBK0556" -> "주문 가능 금액 부족 — 예수금을 확인하세요"
        "APBK0918", "APBK0919" -> "호가 단위 오류 — 가격을 호가 단위에 맞춰 입력하세요"
        "APBK0013" -> "장 종료 또는 시간 외 — 장 시간에 다시 시도하세요"
        "APBK0571", "APBK0908" -> "거래정지 종목 — 주문할 수 없습니다"
        "APBK0666" -> "주문 수량 초과 — 보유 수량을 확인하세요"
        "APBK0017" -> "주문 가격 범위를 벗어났습니다"
        "APBK0024" -> "동일 종목 동시 주문 한도 초과"
        else -> "주문 실패 (msg_cd=${msgCd ?: "unknown"})"
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val POLL_TIMEOUT_MS = 30_000L
    }
}
