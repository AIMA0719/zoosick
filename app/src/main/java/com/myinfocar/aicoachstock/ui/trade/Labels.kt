package com.myinfocar.aicoachstock.ui.trade

import com.myinfocar.aicoachstock.domain.model.EmotionTag
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.domain.model.TradeSide

fun TradeSide.label(): String = when (this) {
    TradeSide.BUY -> "매수"
    TradeSide.SELL -> "매도"
}

fun Market.label(): String = when (this) {
    Market.KR -> "국내"
    Market.US -> "미국"
}

fun EmotionTag.label(): String = when (this) {
    EmotionTag.NONE -> "없음"
    EmotionTag.CONFIDENT -> "확신"
    EmotionTag.FOMO -> "FOMO"
    EmotionTag.FEAR -> "공포"
    EmotionTag.CALM -> "평정"
    EmotionTag.CONFUSED -> "혼란"
}
