package com.myinfocar.aicoachstock.ui.common

import com.myinfocar.aicoachstock.domain.model.Market

/** KR=원 정수, US=$.소수2. 가격 단위 표시. */
fun formatPrice(value: Double, market: Market): String = when (market) {
    Market.KR -> "%,d원".format(value.toLong())
    Market.US -> "$${"%.2f".format(value)}"
}

/** 금액 (평가/매입/손익 등). currencyCode = "KRW" / "USD". */
fun formatMoney(value: Double, currencyCode: String): String = when (currencyCode) {
    "USD" -> "$${"%,.2f".format(value)}"
    else -> "%,d원".format(value.toLong())
}

/** 부호 포함 금액. 양수면 +, 음수면 -. */
fun formatSignedMoney(value: Double, currencyCode: String): String = when (currencyCode) {
    "USD" -> "${if (value >= 0) "+" else ""}$${"%,.2f".format(value)}"
    else -> "%+,d원".format(value.toLong())
}

/** 손익률 (%) 부호 포함. */
fun formatPct(value: Double?): String = if (value == null) "—" else "%+.2f%%".format(value)
