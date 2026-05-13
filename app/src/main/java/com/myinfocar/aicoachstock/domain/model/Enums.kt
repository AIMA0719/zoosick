package com.myinfocar.aicoachstock.domain.model

/** 매매 원칙 카테고리. PRD 02_DATA_MODEL.md 기준. */
enum class PrincipleCategory { ENTRY, EXIT, RISK, PSYCHE }

/** 매수/매도 구분. */
enum class TradeSide { BUY, SELL }

/**
 * 감정 태그. Phase 1은 5개 고정 + NONE.
 * 사용자 정의 허용은 Phase 2 이후 검토 (PRD NEEDS CLARIFICATION).
 */
enum class EmotionTag { NONE, CONFIDENT, FOMO, FEAR, CALM, CONFUSED }

/** 거래소(시장). Stock의 상장 시장 구분. */
enum class Exchange { KOSPI, KOSDAQ, NYSE, NASDAQ }

/** 시장 권역. Trade·WS 구독에서 사용 (KR/US 단순화). */
enum class Market { KR, US }

/** 통화. */
enum class Currency { KRW, USD }

/** 진입 체크리스트 최종 판정. */
enum class EntryDecision { GO, HOLD, STOP }

/** 가격 알림 종류. */
enum class PriceAlertType { STOP_LOSS, TAKE_PROFIT }

/** 가격 알림 방향 (목표가가 현재가 대비 어느 쪽인가). */
enum class PriceAlertDirection { BELOW, ABOVE }

/** 가격 알림 상태. */
enum class PriceAlertStatus { ACTIVE, TRIGGERED, CANCELED }

/** 코치 메시지 발화자. */
enum class CoachMessageRole { USER, COACH, SYSTEM }

/** WebSocket 구독 사유. 41종목 한도 안에서 우선순위 결정. */
enum class SubscriptionReason { ACTIVE_ALERT, OPEN_POSITION, WATCHLIST }

/** 시세 데이터 출처. */
enum class TickSource { WS_LIVE, REST_FALLBACK, CLOSED_PRICE }
