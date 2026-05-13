package com.myinfocar.aicoachstock.ui.watchlist

import com.myinfocar.aicoachstock.domain.model.Exchange
import com.myinfocar.aicoachstock.domain.model.Market

fun Exchange.label(): String = when (this) {
    Exchange.KOSPI -> "코스피"
    Exchange.KOSDAQ -> "코스닥"
    Exchange.NYSE -> "NYSE"
    Exchange.NASDAQ -> "NASDAQ"
}

fun Market.label(): String = when (this) {
    Market.KR -> "국내"
    Market.US -> "미국"
}

/** Market에 따른 기본 Exchange (한투 API 전 임시값). */
fun Market.defaultExchange(): Exchange = when (this) {
    Market.KR -> Exchange.KOSPI
    Market.US -> Exchange.NASDAQ
}
