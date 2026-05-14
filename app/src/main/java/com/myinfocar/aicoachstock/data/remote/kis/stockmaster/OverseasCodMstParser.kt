package com.myinfocar.aicoachstock.data.remote.kis.stockmaster

import com.myinfocar.aicoachstock.domain.model.Currency
import com.myinfocar.aicoachstock.domain.model.Exchange
import com.myinfocar.aicoachstock.domain.model.Stock

/**
 * 한투 해외 종목 마스터 파서.
 *
 *  파일: {nas|nys|ams}mst.cod (cp949, 탭 구분 \t)
 *  컬럼(24개) 중 우리가 쓰는 것:
 *    [0]  National code
 *    [4]  Symbol (ticker)
 *    [6]  Korea name
 *    [7]  English name
 *    [8]  Security type — 1:Index, 2:Stock, 3:ETP(ETF), 4:Warrant
 *
 *  Phase 1은 **Stock(2) + ETP(3)**만 수집. Index/Warrant 제외 (검색 노이즈).
 */
object OverseasCodMstParser {

    fun parse(text: String, exchange: Exchange): List<Stock> {
        return text.lineSequence()
            .mapNotNull { row -> parseLine(row, exchange) }
            .toList()
    }

    private fun parseLine(row: String, exchange: Exchange): Stock? {
        if (row.isBlank()) return null
        val cols = row.split("\t")
        if (cols.size < 9) return null
        val symbol = cols[4].trim()
        if (symbol.isBlank()) return null
        val koreaName = cols.getOrNull(6)?.trim().orEmpty()
        val englishName = cols.getOrNull(7)?.trim().orEmpty()
        val securityType = cols.getOrNull(8)?.trim()
        if (securityType != STOCK && securityType != ETP) return null
        // ticker는 영문 대문자 표준화. 우리 검색·조회 경로가 uppercase 가정.
        val ticker = symbol.uppercase()
        // 한글명 비어 있으면 영문명으로 폴백 (그것도 없으면 ticker).
        val nameKo = koreaName.ifBlank { englishName.ifBlank { ticker } }
        return Stock(
            ticker = ticker,
            nameKo = nameKo,
            nameEn = englishName.ifBlank { null },
            exchange = exchange,
            sector = null,
            currency = Currency.USD,
        )
    }

    private const val STOCK = "2"
    private const val ETP = "3"
}
