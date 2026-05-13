# AICoachStock — 데이터 모델

> 이 문서는 앱에서 다루는 핵심 데이터의 구조를 정의합니다.
> Room + SQLCipher로 폰 내부에 암호화 저장됩니다.

---

## 전체 구조

```
TradingPrinciple (내 매매 원칙)
        |
        +-- 참조 (FK soft ref)
        v
EntryChecklist (진입 체크) ----+
                                |
WatchList ---1:N---> Stock      |
                       |        |
                       v        |
                     Trade <----+
                       |
                       +---1:1---> TradeReflection (AI 복기)
                       |
                       +---1:N---> PriceAlert (손절/익절)

CoachSession ---1:N---> CoachMessage
                            |
                            +-- context_refs[] (Trade/Principle 참조)

MarketTickState (실시간 시세 메모리 캐시, WebSocket tick으로 갱신)
SubscriptionRegistry (구독 종목 + 우선순위, 41종목 한도 관리)
ApiCredential (한투 API 키, EncryptedSharedPreferences로 별도 저장)
```

---

## 엔티티 상세

### TradingPrinciple (매매 원칙)
사용자가 정한 규칙. AI 복기·체크리스트의 판단 기준.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid-1234 | O |
| category | 카테고리 | ENTRY / EXIT / RISK / PSYCHE | O |
| rule_text | 규칙 본문 | "PER 30 이하만 진입" | O |
| weight | 중요도 (1~5) | 4 | O |
| is_active | 활성 여부 | true | O |
| order_index | 표시 순서 | 0 | O |
| created_at | 생성일 | 2026-05-13 | O |
| updated_at | 수정일 | 2026-05-13 | O |

### Stock (종목 메타)
종목 기본 정보. WatchList·Trade가 참조.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| ticker | 종목코드 (PK) | "005930" / "NVDA" | O |
| name_ko | 한글명 | "삼성전자" | O |
| name_en | 영문명 | "Samsung Electronics" | X |
| market | 시장 구분 | KOSPI / KOSDAQ / NYSE / NASDAQ | O |
| sector | 섹터 | "반도체" | X |
| currency | 통화 | KRW / USD | O |

### WatchList (관심종목)
사용자가 즐겨찾기한 종목.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| ticker | Stock 참조 | "005930" | O |
| note | 메모 | "차트 30일선 돌파 확인" | X |
| added_at | 추가일 | 2026-05-13 | O |
| order_index | 정렬 순서 | 0 | O |

### Trade (매매 기록)
실제 체결한 매수/매도. 사용자 수동 입력.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| ticker | Stock 참조 | "005930" | O |
| market | 시장 구분 | KR / US | O |
| side | 매수/매도 | BUY / SELL | O |
| qty | 수량 | 100 | O |
| price | 체결가 (원/USD) | 72500 | O |
| fee | 수수료 | 35 | X |
| executed_at | 체결 시각 | 2026-05-13 10:23 | O |
| reason_text | 매매 이유 (자유 입력) | "20일선 돌파, 거래량 급증" | X |
| emotion_tag | 감정 태그 | CONFIDENT / FOMO / FEAR / CALM / CONFUSED | X |
| linked_checklist_id | 진입 체크리스트 ID (있다면) | uuid | X |
| created_at | 입력일 | 2026-05-13 | O |

### TradeReflection (AI 복기)
Trade 1건당 1개. Gemma 4 E4B 생성.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| trade_id | Trade FK | uuid-trade | O |
| ai_analysis | AI 분석 본문 | "원칙 #3(PER 30↓) 위반 가능성. 진입 시점 시총…" | O |
| rule_violations | 위반 원칙 ID 배열 (JSON) | ["principle-3","principle-7"] | X |
| lesson | 도출된 교훈 | "FOMO 진입 후 손절선 미설정" | X |
| my_note | 사용자 추가 메모 | "다음엔 거래량 함께 확인" | X |
| sentiment_score | AI 추정 감정 점수 (-1~1) | -0.3 | X |
| model_version | 사용 모델 | "gemma-4-e4b" | O |
| latency_ms | 응답 시간 | 12340 | X |
| created_at | 생성일 | 2026-05-13 | O |

### EntryChecklist (진입 체크리스트)
진입 전 원칙 기반 체크 + AI 판정.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| ticker | 대상 종목 | "005930" | O |
| answers | 원칙별 응답 (JSON) | `{"principle-1":"YES","principle-2":"NO"}` | O |
| user_note | 자유 메모 | "급등주 추격 아님 확인" | X |
| ai_verdict | AI 의견 본문 | "GO. 원칙 7개 중 6개 충족…" | O |
| decision | 최종 결정 | GO / HOLD / STOP | O |
| current_price | 작성 시점 시세 | 72500 | X |
| executed | 실제 매매로 이어졌나 | true / false | X |
| created_at | 생성일 | 2026-05-13 | O |

### PriceAlert (가격 알림)
손절/익절 라인 감시.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| ticker | 대상 종목 | "005930" | O |
| linked_trade_id | 연결 매매기록 (있다면) | uuid-trade | X |
| target_price | 목표가 | 68000 | O |
| type | 알림 종류 | STOP_LOSS / TAKE_PROFIT | O |
| direction | 방향 (현재가 대비) | BELOW / ABOVE | O |
| status | 상태 | ACTIVE / TRIGGERED / CANCELED | O |
| triggered_at | 발동 시각 | 2026-05-14 13:45 | X |
| ai_message | 발동 시 AI 코멘트 | "원칙대로 손절 검토. 감정 매매 주의." | X |
| created_at | 생성일 | 2026-05-13 | O |

### CoachSession (코치 세션)
대화 그룹. 종목별·일자별로 묶을 수 있음.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| title | 세션 제목 (자동 생성/수정 가능) | "삼성전자 진입 검토" | O |
| topic_ticker | 관련 종목 (선택) | "005930" | X |
| started_at | 시작 시각 | 2026-05-13 09:00 | O |
| last_message_at | 마지막 메시지 | 2026-05-13 09:15 | O |

### CoachMessage (코치 메시지)
세션 내 개별 메시지.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| id | 고유 식별자 | uuid | O |
| session_id | CoachSession FK | uuid-session | O |
| role | 발화자 | USER / COACH / SYSTEM | O |
| content | 본문 | "이 종목 지금 들어가도 될까?" | O |
| context_refs | 함께 주입한 컨텍스트 IDs (JSON) | `{"trades":["t-1"],"principles":["p-3"]}` | X |
| model_version | 모델 (COACH일 때) | "gemma-4-e4b" | X |
| token_count | 토큰 수 | 412 | X |
| latency_ms | 응답 시간 (COACH일 때) | 8200 | X |
| created_at | 시각 | 2026-05-13 09:01 | O |

### MarketTickState (실시간 시세 상태, 메모리 only)
WebSocket 스트림으로 받은 최신 tick의 메모리 캐시. 영속화 X. UI는 StateFlow로 구독.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| ticker | 종목코드 (Key) | "005930" | O |
| price | 현재가 (마지막 체결가) | 72500 | O |
| change | 전일 대비 | +1500 | X |
| change_pct | 등락률 | 2.11 | X |
| volume_cum | 누적 거래량 | 12_345_678 | X |
| last_tick_at | 마지막 체결 시각 | 2026-05-13 10:23:45.123 | O |
| source | 데이터 출처 | WS_LIVE / REST_FALLBACK / CLOSED_PRICE | O |

### SubscriptionRegistry (구독 관리, 메모리 only)
WebSocket 구독 중인 종목 목록 + 우선순위. 41종목 한도 관리.

| 필드 | 설명 | 예시 | 필수 |
|------|------|------|------|
| ticker | 종목코드 | "005930" | O |
| market | KR / US | KR | O |
| tr_id | 한투 TR 코드 | H0STCNT0 / HDFSCNT0 | O |
| reason | 구독 이유 | WATCHLIST / ACTIVE_ALERT / ACTIVE_TRADE | O |
| priority | 우선순위 (1=최상) | 1 | O |
| subscribed_at | 구독 시각 | 2026-05-13 09:00 | O |

### ApiCredential (별도 보관)
**Room이 아닌 EncryptedSharedPreferences (AndroidX Security Crypto)** 에 저장.

| 키 | 설명 |
|------|------|
| kis_app_key | 한투 App Key |
| kis_app_secret | 한투 App Secret |
| kis_access_token | 발급된 Access Token |
| kis_token_expires_at | 토큰 만료 시각 |
| kis_account_no | (P2에서 계좌 조회 시) |

---

## 관계 요약

- **TradingPrinciple** N개 ← **EntryChecklist.answers**의 키로 참조 (soft FK)
- **Stock** 1개 ← **WatchList / Trade / PriceAlert / MarketTickState / SubscriptionRegistry** N개 참조
- **Trade** 1개 ← **TradeReflection** 1개, **PriceAlert** N개
- **CoachSession** 1개 ← **CoachMessage** N개
- **CoachMessage.context_refs**는 **Trade / TradingPrinciple** IDs를 JSON 배열로 보관 (soft FK)

---

## 왜 이 구조인가

### 분리 원칙
1. **민감도 분리**: API 키는 Room이 아닌 EncryptedSharedPreferences. Room이 노출돼도 인증정보는 별도 키스토어.
2. **AI 결과 vs. 사실 분리**: `Trade`(사실 기록) ↔ `TradeReflection`(AI 분석)을 1:1 별 테이블로. 모델 교체·재생성 시 사실 데이터 보존.
3. **세션 vs. 메시지 분리**: 코치 채팅을 세션 단위로 묶어 RAG 컨텍스트 효율적 구성.

### 확장성
- **Phase 2 통계**: `Trade + TradeReflection.rule_violations + Trade.emotion_tag` 조합으로 별도 테이블 추가 없이 집계 가능.
- **Phase 3 음성 입력**: `Trade.reason_text` / `CoachMessage.content`가 텍스트라 STT 결과 그대로 주입 가능.
- **Phase 3 원칙 자동 개선**: `rule_violations` 누적치로 가장 자주 깨진 원칙 추출 → AI 수정안 생성.
- **Phase 3 클라우드 백업**: 모든 테이블이 `id` UUID 기반이라 머지 충돌 적음.

### 단순성
- 실시간 시세는 WebSocket tick 그대로 메모리 StateFlow로만 유지 (DB 저장 X). 앱 재시작 시 마지막 종가만 REST로 1회 조회.
- 매매 통계는 별도 테이블 없이 `Trade` 쿼리로 도출 (Phase 2에서 머터리얼라이즈드 뷰 검토).

---

## [NEEDS CLARIFICATION]

- [ ] **감정 태그 enum 확정** — 자유 입력 허용 여부 (현재 5개 enum + 사용자 정의 가능 안)
- [ ] **rule_violations 표현** — principle_id 배열 vs. {principle_id, severity} 객체 배열
- [ ] **종가 표시 데이터** — 앱 재시작 직후·비장시간 종가를 Room에 캐시할지(daily snapshot) 여부
- [ ] **다중 보유 평단 처리** — 같은 종목 분할 매수 시 평단 계산을 Trade 쿼리로 할지 별도 Position 테이블을 둘지 (Phase 2 통계와 직결)
- [ ] **CoachMessage.context_refs 정규화** — JSON 컬럼 vs. 정규화된 별도 조인 테이블 (검색·통계 필요도에 따라)
