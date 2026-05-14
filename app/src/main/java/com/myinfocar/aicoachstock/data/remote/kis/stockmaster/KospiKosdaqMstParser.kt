package com.myinfocar.aicoachstock.data.remote.kis.stockmaster

import com.myinfocar.aicoachstock.domain.model.Currency
import com.myinfocar.aicoachstock.domain.model.Exchange
import com.myinfocar.aicoachstock.domain.model.Stock

/**
 * 한투 KOSPI/KOSDAQ 종목 마스터 파서.
 *
 *  파일: kospi_code.mst / kosdaq_code.mst (cp949, 고정폭)
 *  각 줄 구조: [head ...][tail (KOSPI 228자 / KOSDAQ 222자 고정 통계)]
 *  head:
 *    [0..9)  단축코드 (rstrip)
 *    [9..21) 표준코드 (rstrip)
 *    [21..)  한글 종목명 (strip)
 *
 *  통계(tail)는 매매수량단위·재무지표 등이 들어 있지만 우리는 종목명/코드만 추출.
 */
object KospiKosdaqMstParser {

    enum class KrMarket(val tailSize: Int, val exchange: Exchange) {
        KOSPI(tailSize = 228, exchange = Exchange.KOSPI),
        KOSDAQ(tailSize = 222, exchange = Exchange.KOSDAQ),
    }

    fun parse(text: String, market: KrMarket): List<Stock> {
        val tailSize = market.tailSize
        return text.lineSequence()
            .mapNotNull { row -> parseLine(row, tailSize, market.exchange) }
            .toList()
    }

    private fun parseLine(row: String, tailSize: Int, exchange: Exchange): Stock? {
        if (row.length <= tailSize + 21) return null
        val head = row.substring(0, row.length - tailSize)
        if (head.length < 21) return null
        val shortCode = head.substring(0, 9).trimEnd()
        val nameKo = head.substring(21).trim()
        if (shortCode.isBlank() || nameKo.isBlank()) return null
        // 국내 단축코드는 통상 6자리 숫자. 7자리 ETF/스팩 등 예외도 있으니 길이 강제 X.
        return Stock(
            ticker = shortCode,
            nameKo = nameKo,
            nameEn = null,
            exchange = exchange,
            sector = null,
            currency = Currency.KRW,
        )
    }
}
