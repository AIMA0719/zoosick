package com.myinfocar.aicoachstock.domain.model

/**
 * 차트 봉 단위 (Stage 15 신설).
 *
 * - 분봉(MIN_1/5/15/60): 한투 `timeChart` (`FHKST03010200`) 사용. 한투 응답이 1분봉 단위라 5/15/60분은 클라이언트에서 집계.
 * - 기간(DAY/WEEK/MONTH/YEAR): 한투 `dailyChart` (`FHKST03010100`)의 `FID_PERIOD_DIV_CODE` 직매핑.
 */
enum class Timeframe(
    val isIntraday: Boolean,
    val kisPeriodCode: String?,
    val intradayMinutes: Int?,
    val labelKo: String,
) {
    MIN_1(true, null, 1, "1분"),
    MIN_5(true, null, 5, "5분"),
    MIN_15(true, null, 15, "15분"),
    MIN_60(true, null, 60, "60분"),
    DAY(false, "D", null, "일"),
    WEEK(false, "W", null, "주"),
    MONTH(false, "M", null, "월"),
    YEAR(false, "Y", null, "년");

    companion object {
        val DEFAULT: Timeframe = DAY
        val INTRADAY_SET: List<Timeframe> = listOf(MIN_1, MIN_5, MIN_15, MIN_60)
        val PERIOD_SET: List<Timeframe> = listOf(DAY, WEEK, MONTH, YEAR)
    }
}
