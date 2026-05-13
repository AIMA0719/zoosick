package com.myinfocar.aicoachstock.domain.market

import com.myinfocar.aicoachstock.domain.model.Market
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 장 시간대 판정. 휴장일·공휴일은 Phase 1 OOS — 평일 기준만.
 *
 * KR: 09:00 – 15:30 (KST, Asia/Seoul)
 * US: 22:30 – 05:00 (KST, 표준시 기준. DST는 23:30 – 06:00). 본 개발 단순화로 KST 기준 fixed window.
 */
object MarketHours {

    private val KST = ZoneId.of("Asia/Seoul")
    private val KR_OPEN = LocalTime.of(9, 0)
    private val KR_CLOSE = LocalTime.of(15, 30)
    private val US_OPEN_KST = LocalTime.of(22, 30)
    private val US_CLOSE_KST = LocalTime.of(5, 0)
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** 휴장일 set. StockInfoService가 한투 API에서 fetch해서 주입. */
    @Volatile
    private var krHolidayDates: Set<String> = emptySet()

    fun setKrHolidayDates(dates: Set<String>) {
        krHolidayDates = dates
    }

    fun isOpen(market: Market, now: ZonedDateTime = ZonedDateTime.now(KST)): Boolean {
        val kst = now.withZoneSameInstant(KST)
        val day = kst.dayOfWeek
        val t = kst.toLocalTime()
        return when (market) {
            Market.KR -> {
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false
                val today = kst.toLocalDate().format(DATE_FMT)
                if (krHolidayDates.contains(today)) return false
                !t.isBefore(KR_OPEN) && t.isBefore(KR_CLOSE)
            }
            Market.US -> {
                // KST 기준 NY 정규장 매핑:
                //   월 22:30~23:59          (= NY 월 09:30~)
                //   화~금 00:00~05:00, 22:30~23:59
                //   토 00:00~05:00          (= NY 금 ~16:00 폐장)
                //   일 = 항상 닫힘
                val morning = t.isBefore(US_CLOSE_KST)
                val evening = !t.isBefore(US_OPEN_KST)
                when (day) {
                    DayOfWeek.MONDAY -> evening
                    DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> morning || evening
                    DayOfWeek.SATURDAY -> morning
                    DayOfWeek.SUNDAY -> false
                    else -> false
                }
            }
        }
    }

    fun anyOpen(now: ZonedDateTime = ZonedDateTime.now(KST)): Boolean =
        isOpen(Market.KR, now) || isOpen(Market.US, now)
}
