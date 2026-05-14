package com.myinfocar.aicoachstock.data.remote.kis.stockmaster

import com.myinfocar.aicoachstock.data.local.db.stock.StockDao
import com.myinfocar.aicoachstock.data.local.db.stock.toEntity
import com.myinfocar.aicoachstock.domain.model.Exchange
import com.myinfocar.aicoachstock.domain.model.Stock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 종목 마스터 4종(KOSPI/KOSDAQ/NASDAQ/NYSE) 다운로드 + Room stocks 테이블 일괄 갱신.
 *
 *  - AMEX는 한투 Exchange enum에 별도 값 없음(현 enum: KOSPI/KOSDAQ/NYSE/NASDAQ) → 일단 스킵.
 *    필요 시 Exchange.AMEX 추가하며 ams 마스터 포함.
 *  - 부분 실패 허용: KOSPI만 받고 KOSDAQ 실패해도 받은 만큼은 저장.
 *  - REPLACE 정책 — 사용자가 검색·관심에 추가한 기존 종목 메타는 마스터 데이터로 덮어쓰임(개선).
 */
@Singleton
class StockMasterSyncService @Inject constructor(
    private val downloader: KisStockMasterDownloader,
    private val stockDao: StockDao,
) {

    data class SyncSummary(
        val kospi: Int,
        val kosdaq: Int,
        val nasdaq: Int,
        val nyse: Int,
    ) {
        val total: Int get() = kospi + kosdaq + nasdaq + nyse
    }

    suspend fun syncAll(): Result<SyncSummary> = runCatching {
        val collected = mutableListOf<Stock>()

        val kospi = downloadAndParseKr(URL_KOSPI, KospiKosdaqMstParser.KrMarket.KOSPI)
        collected += kospi
        val kosdaq = downloadAndParseKr(URL_KOSDAQ, KospiKosdaqMstParser.KrMarket.KOSDAQ)
        collected += kosdaq
        val nasdaq = downloadAndParseUs(URL_NASDAQ, Exchange.NASDAQ)
        collected += nasdaq
        val nyse = downloadAndParseUs(URL_NYSE, Exchange.NYSE)
        collected += nyse

        if (collected.isEmpty()) error("종목 마스터 다운로드 결과가 비어있음")

        // ticker 중복(예: 미국 동일 심볼이 NYSE/NASDAQ 양쪽에 있는 경우)은 뒤에 들어온 게 우선.
        // 한국 단축코드는 시장 간 충돌 없음.
        val deduped = collected.associateBy { it.ticker }.values.toList()
        stockDao.upsertAll(deduped.map { it.toEntity() })

        Timber.i(
            "종목 마스터 동기화 완료 KOSPI=${kospi.size} KOSDAQ=${kosdaq.size} " +
                "NASDAQ=${nasdaq.size} NYSE=${nyse.size} (dedup ${collected.size}→${deduped.size})",
        )

        SyncSummary(
            kospi = kospi.size,
            kosdaq = kosdaq.size,
            nasdaq = nasdaq.size,
            nyse = nyse.size,
        )
    }

    private suspend fun downloadAndParseKr(
        url: String,
        market: KospiKosdaqMstParser.KrMarket,
    ): List<Stock> = runCatching {
        val text = downloader.downloadAndUnzipCp949(url)
        KospiKosdaqMstParser.parse(text, market)
    }.getOrElse {
        Timber.w(it, "KR 종목 마스터 다운로드 실패 url=$url — 빈 결과로 진행")
        emptyList()
    }

    private suspend fun downloadAndParseUs(url: String, exchange: Exchange): List<Stock> =
        runCatching {
            val text = downloader.downloadAndUnzipCp949(url)
            OverseasCodMstParser.parse(text, exchange)
        }.getOrElse {
            Timber.w(it, "US 종목 마스터 다운로드 실패 url=$url — 빈 결과로 진행")
            emptyList()
        }

    private companion object {
        const val URL_KOSPI =
            "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"
        const val URL_KOSDAQ =
            "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip"
        const val URL_NASDAQ =
            "https://new.real.download.dws.co.kr/common/master/nasmst.cod.zip"
        const val URL_NYSE =
            "https://new.real.download.dws.co.kr/common/master/nysmst.cod.zip"
    }
}
