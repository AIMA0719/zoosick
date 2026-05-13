package com.myinfocar.aicoachstock.domain.account

import com.myinfocar.aicoachstock.domain.model.Market

/**
 * 한투 계좌의 보유 종목 1개. 메모리 only — 호출마다 새로 받아옴.
 * KR/US 통합 모델.
 */
data class Holding(
    val ticker: String,
    val name: String,
    val market: Market,
    val qty: Int,
    val avgBuyPrice: Double,
    val currentPrice: Double,
    val totalBuyAmount: Double,
    val evaluationAmount: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlRate: Double,
    val currencyCode: String, // "KRW" / "USD"
    val exchangeCode: String? = null, // NAS/NYS/AMS (US만)
)

data class AccountSummary(
    val market: Market,
    val totalEvaluation: Double,         // 총 평가금액
    val totalBuyAmount: Double,           // 매입금액 합계
    val unrealizedPnl: Double,           // 평가 손익 합계
    val unrealizedPnlRate: Double,        // 손익율 (%)
    val cashDeposit: Double? = null,      // 예수금
    val totalAssetValue: Double? = null,  // 순자산
    val currencyCode: String = "KRW",
)

data class PeriodProfitDay(
    val date: String,           // YYYYMMDD
    val buyAmount: Double,
    val sellAmount: Double,
    val realizedPnl: Double,
    val fee: Double,
    val pnlRate: Double,
)

data class PeriodProfitTotal(
    val rangeStart: String,
    val rangeEnd: String,
    val totalBuy: Double,
    val totalSell: Double,
    val totalRealizedPnl: Double,
    val totalFee: Double,
    val pnlRate: Double,
    val days: List<PeriodProfitDay>,
)
