package com.myinfocar.aicoachstock.data.remote.kis

/**
 * 한투 OpenAPI TR_ID 상수 — 실전(PROD) 전용.
 * 모의(VTS) TR_ID는 사용하지 않음 (memory: feedback-no-paper-trading).
 */
object KisTrId {
    // 시세
    const val DOMESTIC_PRICE = "FHKST01010100"          // 국내 주식 현재가
    const val DOMESTIC_ASKING_PRICE = "FHKST01010200"    // 호가/예상체결
    const val DOMESTIC_INVESTOR = "FHKST01010900"        // 투자자별 일별 순매수
    const val DOMESTIC_DAILY_CHART = "FHKST03010100"     // 일/주/월/년 차트
    const val DOMESTIC_TIME_CHART = "FHKST03010200"      // 분봉 차트
    const val OVERSEAS_PRICE = "HHDFS00000300"           // 해외 주식 현재가

    // 종목 정보 / 재무
    const val STOCK_INFO = "CTPF1604R"                    // 주식 기본 조회
    const val FINANCIAL_RATIO = "FHKST66430300"           // 재무비율
    const val ESTIMATE_PERFORM = "HHKST668300C0"          // 추정실적
    const val INVEST_OPINION = "FHKST663300C0"            // 종목 투자의견

    // 시장
    const val MARKET_INVESTOR_DAILY = "FHPTJ04040000"     // 시장 투자자 일별
    const val MARKET_FUNDS = "FHPST01060000"              // 시장 예수금
    const val OVERSEAS_NEWS = "HHPSTH60100C1"             // 해외 뉴스

    // 거래소 정보
    const val COUNTRIES_HOLIDAY = "CTOS5011R"             // 휴장일
    const val DIVIDEND = "HHKDB669102C0"                  // 배당 일정

    // 계좌 (실전 전용)
    const val DAILY_CCLD = "TTTC8001R"                     // 일별 주문체결 (3개월 이내)
    const val BALANCE = "TTTC8434R"                        // 주식 잔고
    const val PERIOD_PROFIT = "TTTC8708R"                  // 기간별 손익
    const val OVERSEAS_BALANCE = "TTTS3012R"               // 해외 잔고
    const val OVERSEAS_CCNL = "TTTS3035R"                  // 해외 주문체결
}
