package com.myinfocar.aicoachstock.domain.model

/**
 * 종목 메타. WatchList·Trade·PriceAlert가 ticker로 참조.
 *
 * ticker: 종목코드. 국내는 6자리 숫자("005930"), 미국은 알파벳("NVDA").
 */
data class Stock(
    val ticker: String,
    val nameKo: String,
    val nameEn: String?,
    val exchange: Exchange,
    val sector: String?,
    val currency: Currency,
) {
    val market: Market
        get() = when (exchange) {
            Exchange.KOSPI, Exchange.KOSDAQ -> Market.KR
            Exchange.NYSE, Exchange.NASDAQ -> Market.US
        }
}
