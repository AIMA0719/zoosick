# AICoachStock 진척 현황 (Handoff)

> **다음 작업 시 이 파일 먼저 읽고 시작.** PRD는 `docs/AICoachStock-PRD/` 안에 5개.

마지막 업데이트: 2026-05-19 (Stage 16-2: 매수/매도 UI + 생체 인증 — 안정성 보강만 대기)

---

## 한 문장 요약
Android Z Fold 7 타깃 온디바이스 AI 주식 코치 앱. **PoC 4종 + Phase 1 Stage 1~12 코드 완성**. Stage 12에서 모의(VTS) 영구 제거 + 추정실적·증권사 의견·분봉·배당·시장 동향 API 통합 + 홈 대시보드 + AI 일일 시장 브리핑. 한투 OpenAPI 실전 전용 종합 증권+AI 코칭 앱.

---

## 핵심 결정 (확정됨)

| 항목 | 값 |
|---|---|
| **LLM** | Gemma 4 E4B (`.litertlm`, 3.66GB) + **LiteRT-LM 0.11.0** (MediaPipe tasks-genai는 deprecated) |
| **모델 호스트** | HuggingFace `litert-community/gemma-4-E4B-it-litert-lm` 의 `gemma-4-E4B-it.litertlm` + hf-mirror.com 미러 |
| **SHA-256** | 호스트 미공개 → 다운로드 시 자동 산출·저장, 매 verify()에서 재계산 |
| **한투 API** | 실전 키. REST(HTTPS) + WS(ws://, network_security_config로 도메인 cleartext 예외) |
| **DB** | Room v8 + SQLCipher + Keystore-backed passphrase (32B EncryptedSP) |
| **API 키** | EncryptedSharedPreferences (Room 분리) |
| **빌드** | Kotlin 2.2.21 / KSP 2.2.21-2.0.4 / Hilt 2.57.2 / AGP 8.7.2 / hilt-work 1.2.0 |
| **applicationId** | `com.myinfocar.aicoachstock` (debug `.debug`) |
| **해외 실시간** | Phase 1 KR만. 미국 권한은 한투에 별도 신청(영업일 1~7일) — 사용자가 시작 추천 |
| **41종목 한도** | 본 개발 안에서 실측 (현재는 sort + take 단순 cap, Stage 5에서 차분 update로 강화) |

---

## ✅ 완료

### PoC 4종 (검증 통과)
1. **#4 SQLCipher + Keystore passphrase 왕복** — instrumented test 3개 GREEN
2. **#1 Gemma 4 E4B + LiteRT-LM 추론** — 다운로드 + 로드 + 토큰 스트리밍 OK
3. **#2 한투 WS approval_key + tick** — H0STCNT0 구독 + 파싱 OK
4. **#3 FGS(dataSync) 30분+ 유지** — 화면 끄고 30분 유지 OK

### 의존성 마이그레이션
- `com.google.mediapipe:tasks-genai 0.10.27` → `com.google.ai.edge.litertlm:litertlm-android 0.11.0`
- 매니페스트: `usesCleartextTraffic` 제거 → `networkSecurityConfig`로 `ops.koreainvestment.com`만 예외
- `Json.encodeToString` 시 `encodeDefaults = true` (한투 `grant_type` 누락 버그 fix)
- `androidx.hilt:hilt-work` + `hilt-compiler` 추가 (Stage 8 WorkManager)

### Phase 1 Stage 1: AI 매매 복기 (코드 완성)
- DB: `TradeReflectionEntity` (FK→Trade, CASCADE, UNIQUE tradeId) / DAO / Mapper
- Repo: `TradeReflectionRepository` + Impl
- Service: `ReflectionService` — LLM 호출 + `LESSON:` / `VIOLATED:` 마커 파싱 + 활성 원칙 sanity-filter
- UI: `ReflectionScreen` (Trade 요약 / 스트리밍 분석 / 위반 chip / 교훈 카드 / 내 메모 / 디스클레이머)
- 라우트: `reflections/{tradeId}`, TradeList 카드 우하단 "🤖 AI 복기" 진입
- AppDatabase v3 → v4

### Phase 1 Stage 2: 코치 채팅 ✨ NEW
- DB: `CoachSessionEntity` (last_message_at index) + `CoachMessageEntity` (FK CASCADE, role/contextRefs JSON)
- DAO: `CoachSessionDao` / `CoachMessageDao`, observe + insert + touch + rename + delete
- Repo: `CoachRepository` + Impl — 세션 생성/관리, 메시지 append 시 sessionDao.touch
- Service: `CoachService` — 활성 원칙 + 최근 매매 10건 + 이전 메시지 8건을 컨텍스트로 묶어 LLM 호출
- UI: `CoachListScreen` (세션 목록/생성/삭제) + `CoachChatScreen` (말풍선 스타일, 토큰 스트리밍, 디스클레이머)
- 라우트: `coach`(목록) / `coach/chat/{sessionId}`
- AppDatabase v4 → v5

### Phase 1 Stage 3: 진입 체크리스트 ✨ NEW
- DB: `EntryChecklistEntity` (ticker+createdAt index, answers JSON)
- DAO: `EntryChecklistDao` (observeAll/byTicker, markExecuted)
- Repo: `EntryChecklistRepository` + Impl
- Service: `EntryChecklistService` — 활성 원칙별 응답 → LLM → `DECISION: GO/HOLD/STOP` 마커 파싱 (미발견 시 HOLD 폴백)
- UI: `EntryChecklistScreen` — ticker/현재가/원칙별 응답(FilterChip YES/NO + 자유 텍스트)/메모, 색상 코드된 판정 카드
- 라우트: `entry`. Settings에서 진입.
- AppDatabase v5 → v6

### Phase 1 Stage 4: 한투 REST 시세 + 종목 검색 ✨ NEW
- DTO: `KrPriceResponse`(FHKST01010100) / `UsPriceResponse`(HHDFS00000300)
- API: `KisMarketRestApi` — Retrofit, @Url 동적 + authorization/appkey/appsecret/tr_id 헤더
- Source: `KisMarketDataSource` — `MarketDataSource` 구현. Mutex-based rate limiter (100ms gap)
  - `fetchClosePrice`: 국내/미국 분기, 토큰 자동 보장
  - `searchStocks`: 6자리 숫자 → KR / 알파벳 → US 단일조회 매칭 (한투 통합 검색 부재로 휴리스틱)
  - `fetchFundamentals`: stub (Phase 2 별도 정의)
- DI: `MarketDataApiModule` + `MarketDataSourceBindingModule`
- UI: `StockSearchScreen` — 검색 + 결과 카드(현재가, 등락률) + "관심종목 추가" 액션
- 라우트: `stocks/search`. WatchList TopBar 검색 아이콘 + Settings 진입점.

### Phase 1 Stage 5: WS 본 개발 강화 ✨ NEW
- `MarketHours` — KR 09:00–15:30, US 22:30–05:00 KST window 판정. 비장시간 자동 보류.
- `KisWebSocketStream` 강화:
  - **지수 백오프 재연결**: 1→2→4→8→16→30s 상한, intendedConnected가 true인 한 무한 시도
  - **차분 구독 업데이트**: 새 desired 목록과 현재 구독 set diff → toAdd/toRemove만 send (불필요한 unsubscribe→subscribe 트래픽 차단)
  - **자동 재구독**: onOpen 시 desired 구독을 일괄 재등록 (재연결 후 복구)
  - **multi-tick frame 처리**: count*46 필드 chunked로 전체 종목 emit (PoC는 첫 종목만)
  - **비장시간 게이트**: connect() 시 시간 체크하고 DEGRADED 유지

### Phase 1 Stage 6: PriceAlert + AlertScheduler ✨ NEW
- DB: `PriceAlertEntity` (status+ticker index)
- DAO: `PriceAlertDao` (observeAll, findByStatus, updateStatus, delete)
- Repo: `PriceAlertRepository` + Impl
- `PriceAlertNotifier` — IMPORTANCE_HIGH 채널, BigTextStyle, 손절🔴/익절🟢 emoji
- `AlertSchedulerImpl` — `MarketDataStream.ticks(ticker)` collect, direction(ABOVE/BELOW)으로 도달 감지, 트리거 시 Notifier + status=TRIGGERED + 다른 알림 미사용 ticker는 unsubscribe
- FGS(`KisWsMarketDataService`)가 시작 시 `priceAlertRepo.findActive()` → `alertScheduler.reschedule()`
- UI: `PriceAlertScreen` — 카드별 상태/타입/방향/타깃, 추가 다이얼로그(타입 선택 → direction 자동 BELOW/ABOVE), 취소/삭제
- 라우트: `alerts`. Settings 진입점.
- AppDatabase v6 → v7

### Phase 1 Stage 7: 종목 리서치 Q&A ✨ NEW
- `ResearchService` — Stock 메타 + 현재가(REST) + 사용자 수동 입력 재무 메모 + 질문 → LLM
  - 시스템 프롬프트: 투자 권유 금지, "확인되지 않음" 표현, 검증 없는 사용자 메모는 결론 근거 X
- UI: `ResearchScreen` — ticker/재무메모/질문 입력, 스트리밍 응답, 응답 카드 + 응답 시간 표시
- 라우트: `research`. Settings 진입점.

### Phase 1 Stage 8: 모델 다운로더 강화 ✨ NEW
- **미러 호스트**: `ModelSource(url, mirrors)` — primary 실패 시 mirrors 순차 시도, 한 호스트 차단돼도 복구
  - 기본 미러: `hf-mirror.com`
- **WorkManager 백그라운드 다운로드**: `ModelDownloadWorker` (HiltWorker, CoroutineWorker)
  - **NetworkType.UNMETERED 강제** (Wi-Fi only, PRD 절대 하지 마 준수)
  - `setRequiresBatteryNotLow(true)` + `setRequiresStorageNotLow(true)`
  - `ExistingWorkPolicy.KEEP` + 진행률 setProgress
  - 실패 시 Result.retry (WorkManager 자체 백오프)
- `AICoachApplication`이 `Configuration.Provider`로 직접 초기화 + HiltWorkerFactory 주입
- `AndroidManifest.xml`에 `WorkManagerInitializer` 제거 (Hilt 통합 시 필수)
- UI: `LlmPocScreen` DownloadCard에 "백그라운드 다운로드" / "BG 취소" 버튼 추가

✅ 8개 stage 모두 `./gradlew.bat assembleDebug` SUCCESSFUL

### Phase 1 Stage 9: 매매 자동 동기화 (한투 체결 import) ✨ NEW
- **TradeEntity 확장**: `externalOrderNo` (한투 odno, nullable unique) — null=수동, non-null=한투 import
- **AppDatabase v7 → v8**
- **`ApiCredentialStore` 확장**: `accountNo`(CANO 8자리) + `productCode`(ACNT_PRDT_CD, 보통 "01") EncryptedSharedPreferences 저장
- **API**: `KisTradingApi.fetchDailyCcld` — `/uapi/domestic-stock/v1/trading/inquire-daily-ccld` (실전 TR_ID `TTTC8001R` / 모의 `VTTC8001R`)
  - 페이징 처리 (ctx_area_nk100 + tr_cont = "N")
  - 미체결(`tot_ccld_qty == 0`) / 취소(`cncl_yn == "Y"`) 제외
- **`TradeImportService.importRecent(daysBack=7)`** — 응답을 `Trade`로 매핑(`avg_prvs` = 평균체결가, `tot_ccld_qty` = 실체결수량), 종목 메타도 함께 upsert, 중복은 `saveIfAbsent`로 skip
- **수동 트리거 UI**:
  - 매매 탭 TopBar에 🔄 동기화 아이콘 → 스낵바로 "신규 N건" 결과 표시
  - 자동 import된 거래는 카드에 ☁️ "자동" chip
  - Settings에 계좌번호 입력 + "🔄 지금 동기화" 버튼
- **백그라운드 자동**:
  - `TradeSyncWorker` (HiltWorker, PeriodicWorkRequest 24h)
  - `Application.onCreate`에서 KEEP 정책으로 enqueue — 최초 실행 시 다음 16:00 KST까지 initialDelay 계산
  - `NetworkType.CONNECTED` 제약, 실패 시 LINEAR 백오프 15분
  - API 키/계좌번호 미설정은 자동 skip (재시도 X)
- **PRD 정책**: "자동 주문 실행"은 여전히 금지. 본 기능은 *이미 체결된* 거래 조회만 — 자동매매 봇과 무관.

✅ `installDebug` SUCCESSFUL — SM-F711N에 설치 완료

### Phase 1 Stage 10: 한투 OpenAPI 11종 통합 ✨ NEW
사용자가 받아둔 한투 OpenAPI 명세서 9개(엑셀, 국내 5 + 해외 4, 총 ~200 API) 분석 후 PRD 부합한 **조회 API만** 통합.

**PRD 금지로 제외**: 주식주문(현금/신용/정정취소), 예약주문, 해외주식 주문/예약/주간주문 — 자동 주문 실행은 절대 금지.

**통합 완료 API 11종**:

| # | TR_ID | 엔드포인트 | 우리 앱 통합 위치 |
|---|---|---|---|
| 1 | `TTTC8434R` / `VTTC8434R` | `inquire-balance` | **HoldingsScreen** (국내 보유 종목 + 평가손익) |
| 2 | `TTTS3012R` / `VTTS3012R` | `overseas-stock inquire-balance` | **HoldingsScreen** (해외 보유 종목, USD) |
| 3 | `TTTC8708R` | `inquire-period-profit` | **매매 탭 상단** 30일 누적 실현손익 카드 |
| 4 | `TTTS3035R` / `VTTS3035R` | `overseas-stock inquire-ccnl` | **TradeImportService** — 미국 매매 자동 import 확장 |
| 5 | `CTPF1604R` | `search-stock-info` | **StockSearch + ResearchService** — 정확한 종목명/거래소/섹터 (휴리스틱 fallback) |
| 6 | `FHKST66430300` | `finance/financial-ratio` | **ResearchService 자동 컨텍스트** — ROE/EPS/BPS/부채비율/매출증가율/영업이익증가율 (최근 3기) |
| 7 | `FHKST03010100` | `inquire-daily-itemchartprice` | **ResearchService 자동 컨텍스트** — 30일 OHLC → 기간 등락률/고저 |
| 8 | `CTOS5011R` | `countries-holiday` | **MarketHours** — KR 휴장일 캐시 24h, Application.onCreate에서 fetch |
| 9 | `TTTC8001R` / `VTTC8001R` | `inquire-daily-ccld` | (Stage 9에서 이미) 국내 매매 자동 import |
| 10 | `FHKST01010100` | `inquire-price` | (PoC #2에서 이미) 국내 현재가 |
| 11 | `HHDFS00000300` | `overseas-price` | (Stage 4에서 이미) 해외 현재가 |

**핵심 변경**:
- `KisTradingApi`에 fetchBalance / fetchPeriodProfit / fetchOverseasBalance / fetchOverseasCcnl 4종 추가
- 새 `KisStockInfoApi` (search-stock-info / financial-ratio / dailyChart / countriesHoliday)
- 새 도메인 패키지 `domain/account/` — `AccountService` + `Holding` / `AccountSummary` / `PeriodProfitTotal`
- 새 도메인 패키지 `domain/stockinfo/` — `StockInfoService` (재무/일봉/메타/휴장일)
- `ResearchService`가 KR 종목 질의 시 재무비율 + 일봉 + 종목메타 **자동 LLM 컨텍스트 주입**
- `KisMarketDataSource.searchStocks` — 휴리스틱 거래소 추측 제거, 한투 mket_id_cd 기반 정확 분류
- `MarketHours` — KR 휴장일 set 주입, isOpen()에서 체크
- `TradeImportService` — KR + US 모두 한 번에 import
- `AICoachApplication.onCreate` — 시작 시 휴장일 fetch 후 MarketHours 주입
- Settings에 **📊 보유 종목** 진입점 추가
- 매매 탭 상단에 **30일 실현손익 카드** (양수 녹색 / 음수 빨강)

**모의 환경 제약**:
- `TTTC8708R` (기간손익)은 모의투자 미지원 — env != PROD면 "실전 계정만 지원" 메시지로 fail-fast
- 다른 조회 API는 모의 TR_ID로 자동 분기 (VTTC.../VTTS...)

✅ `installDebug` SUCCESSFUL

### Phase 1 Stage 11: 종목 상세 화면 + AI BUY/HOLD/SELL 코칭 ✨ NEW
사용자 요청: "토스/키움/카카오 증권/한투 앱처럼 종목 상세에서 차트·호가 다 보이고, 실데이터로 언제 사고 팔지 코칭 받고 싶다."

**추가된 한투 API 3종**:
- `FHKST01010200` `inquire-asking-price-exp-ccn` — 5호가 + 잔량 + 예상체결가
- `FHKST01010900` `inquire-investor` — 일별 개인/외국인/기관 순매수량
- `HHPSTH60100C1` `news-title` — 해외 뉴스 헤드라인 (실전 전용, 종목 심볼 필터)

**새 화면 `StockDetailScreen`** (`stocks/{ticker}`):
- 상단: 종목명/섹터 chip / 현재가 (한국 관례 — 상승 빨강, 하락 파랑) / 등락 / 거래량
- **일봉 차트 (30일)** — Compose Canvas로 직접 그리기, 라이브러리 X. 첫 → 마지막 등락에 따라 색상 자동
- **5호가 카드** — 매도1~3 + 매수1~3 + 총잔량 + 예상체결가
- **투자자별 매매 카드** — 최근 5일 개인/외국인/기관 순매수량
- **종목 정보 카드** — 한투 search-stock-info: 종목명/업종/상장주식수/자본금/당해년도 고저
- **뉴스 카드** (해외 종목) — 한투 news-title 헤드라인
- **🤖 AI 코칭 카드** — BUY/HOLD/SELL 추천 + 확신도 progress bar + 본문 (상승 BUY 빨강 / SELL 파랑 / HOLD 노랑)

**`TradingAdvisorService`** (새 도메인):
- 종목 1개에 대해 한투 API에서 *모든 실데이터* 한 번에 fetch:
  - 현재가 (`inquire-price`)
  - 일봉 30일 (`inquire-daily-itemchartprice`)
  - 재무비율 3기 (`financial-ratio`)
  - 종목 메타 (`search-stock-info`)
  - 투자자별 매매 5일 (`inquire-investor`)
  - 호가 잔량 (`inquire-asking-price-exp-ccn`)
  - 해외 뉴스 (`news-title` — US 종목만)
  - 본인 보유 여부 (`inquire-balance` — KR/US 자동 분기)
  - 활성 매매 원칙 (Room)
  - 이 종목 최근 본인 매매 5건 (Room)
- 위 데이터를 모두 LLM 프롬프트에 단일 user message로 주입
- 응답에서 `RECOMMENDATION: BUY/HOLD/SELL` + `CONFIDENCE: 0-100` 마커 파싱
- 미발견 시 안전한 HOLD + 30 폴백

**관심 탭 강화**:
- `WatchListViewModel`이 30초 폴링으로 `MarketDataSource.fetchClosePrice` 호출 → `ticks` StateFlow 갱신
- `MarketHours.anyOpen()` 체크 — 비장시간엔 폴링 skip (REST 한도 보호)
- 카드에 가격 + 등락률 실시간 표시 (상승 빨강 / 하락 파랑)
- 카드 **터치** → 종목 상세 화면 / 카드 **롱터치** → 메모 편집 (기존 동작 보존)

**라우트**:
- `stocks/{ticker}` — StockDetailScreen
- WatchListScreen에서 `onItemClick(ticker)` → `navigate(stockDetail(ticker))`

**Compose Canvas 차트**:
- `PriceLineChart` — 외부 라이브러리 없이 라인 차트만 그림
- 시작점과 끝점에 작은 원 표시
- 마지막 가격 > 첫 가격 → 녹색, 반대 → 빨강 (글로벌 관례; 상승이라는 추세를 표시)

✅ `installDebug` SUCCESSFUL

### Phase 1 Stage 12: 종합 증권+AI 코칭 앱 탈바꿈 ✨ NEW
사용자 요청: "모의는 절대 안 함" + "미사용 API 활용해서 진짜 증권 앱 + AI 코칭 슬로건에 걸맞게 탈바꿈".

**환경 단순화 (memory 저장됨)**:
- `KisEnv` enum에서 VTS 제거 — PROD 단일 멤버
- 모든 코드의 `if env == PROD` 분기 단순화 (잔고/체결/손익 등 항상 실전 TR_ID 사용)
- `KisRateLimiter` — PROD 단일 gap(60ms)로 단순화, ApiCredentialStore 의존성 제거
- Settings UI에서 환경 선택 SegmentedButton 제거
- 모의 미지원 TR(`TTTC8708R` 등) fail-fast 제거 — 그냥 호출
- 관련 memory: `feedback_no_paper_trading.md` (다음 세션도 자동 적용)

**추가된 한투 API 7종**:

| TR_ID | 엔드포인트 | 우리 앱 활용 |
|---|---|---|
| `HHKST668300C0` | `estimate-perform` | **AdvisorService 자동 컨텍스트** — 애널리스트 컨센서스 매출/영업이익/순이익/EPS |
| `FHKST663300C0` | `invest-opinion` | **AdvisorService 자동 컨텍스트** — 증권사별 투자의견 + 목표주가 + 괴리율 (최근 60일 5건) |
| `FHKST03010200` | `inquire-time-itemchartprice` | **분봉 차트** — StockInfoService.fetchTimeChart (호출자가 토글로 사용) |
| `HHKDB669102C0` | `ksdinfo/dividend` | **홈 화면 "다가오는 배당" 카드** — 보유 종목과 매칭, 90일 내 배당 일정 |
| `FHPTJ04040000` | `inquire-investor-daily-by-market` | **AI 시장 브리핑** — KOSPI 7일 외국인/기관/개인 흐름 |
| `FHPST01060000` | `mktfunds` | **AI 시장 브리핑** — 시장 예수금 + 신용잔고 추이 |

**새 화면 — 홈 대시보드** (`home`):
- BottomNav **첫 탭으로 진입** (시작점 변경: `PRINCIPLES` → `HOME`)
- **🌅 오늘 AI 코칭 브리핑 카드** — 버튼 클릭 시 LLM이 종합 분석:
  - KOSPI 외국인/기관 7일 흐름
  - 시장 예수금 추이
  - 내 보유 종목 평가손익
  - 관심 종목 N개 현재가
  - 활성 매매 원칙
  → "오늘 한 줄 + 시장 + 내 보유 + 관심 + 원칙 체크" 형식
- **💼 내 자산 카드** — 총 평가금액 / 평가손익 / 예수금 (한투 잔고)
- **💰 다가오는 배당 카드** — 보유 종목 중 90일 내 배당 일정만 필터
- **📊 내 보유 미니** — 상위 5개 (터치 → 종목 상세)
- **👀 관심 종목 미니** — 상위 5개 (현재가 자동 폴링, 터치 → 종목 상세)

**새 서비스 — `MarketBriefingService`**:
- 한투 시장 동향 API들 (외국인/기관/예수금) + 내 보유/관심/원칙 → LLM 단일 user message
- 응답: "🌅 오늘 한 줄 → 📊 시장 → 💼 내 보유 → 👀 관심 → ✅ 원칙 체크"
- 투자 권유 톤 금지, 8~12줄 짧게

**`TradingAdvisorService` 강화**:
- 추정실적(`HHKST668300C0`) + 증권사 투자의견(`FHKST663300C0`) 자동 컨텍스트 추가
- AI 응답이 매출/영업이익 전망 + 증권사 목표가 vs 현재가 괴리율까지 종합

**BottomNav 재정렬 (6탭)**:
- 홈 / 관심 / 매매 / 코치 / 원칙 / 설정 (실제 증권 앱처럼 홈 우선)

✅ `installDebug` SUCCESSFUL

### Phase 1 Stage 13: 종목 마스터 + 자연어 검색 ✨ NEW
사용자 요청: "삼성전자, 애플 같은 자연어로도 검색 가능해야".

**문제**: 한투 OpenAPI에 통합 종목 검색 API가 없어 기존 `searchStocks`는 정확한 ticker(`005930`/`AAPL`)만 받음. "삼성전자" 입력 시 결과 0건.

**해결 — 한투 공식 종목 마스터 파일 다운로드**:
- 한투가 공개한 mst/cod zip 파일 (API 키 불필요, 누구나 다운로드)
- KOSPI/KOSDAQ: cp949 고정폭 (`kospi_code.mst` / `kosdaq_code.mst`)
- NASDAQ/NYSE: cp949 탭 구분 (`nasmst.cod` / `nysmst.cod`)
- 합산 ~12,000 종목 (KOSPI ~900 + KOSDAQ ~1,800 + NASDAQ ~5,000 + NYSE ~3,500 정도)

**새 파일**:
- `data/remote/kis/stockmaster/`
  - `KisStockMasterDownloader.kt` — OkHttp + ZipInputStream + cp949 디코드
  - `KospiKosdaqMstParser.kt` — head[0..9]=단축코드, [21..]=한글명, tail 228/222 잘라냄
  - `OverseasCodMstParser.kt` — \t split, Security type 2(Stock)+3(ETP)만 포함
  - `StockMasterSyncService.kt` — 4종 다운로드 → ticker dedup → `stockDao.upsertAll`
- `data/sync/StockMasterSyncWorker.kt` — 주1회 PeriodicWork(UNMETERED) + 최초 OneTime(CONNECTED)

**기존 파일 변경**:
- `StockDao.kt` — `searchByText(query)` 추가 (nameKo/nameEn/ticker LIKE + 정확/접두/부분 정렬), `count()`, `upsertAll()`
- `StockRepository` / `StockRepositoryImpl` — `searchOnce(query)` / `masterCount()` 추가
- `KisMarketDataSource.searchStocks` — **마스터 검색 1순위, 단일조회 fallback**. 마스터 비어있으면 기존 동작 유지.
- `StockSearchScreen` — placeholder "삼성전자, 애플, 005930, AAPL", 결과 50개까지 표시. 상위 10개만 비동기 가격 fetch (한투 한도 보호).
- `AICoachApplication.onCreate` — `StockMasterSyncWorker.enqueuePeriodic` + `masterCount() == 0` 시 `enqueueOnce`

**아키텍처 결정**:
- **별도 master 테이블 없이** 기존 `stocks` 테이블 재활용 — DB 마이그레이션 불필요, AppDatabase v8 유지.
- REPLACE 정책 — 사용자가 검색·관심으로 추가한 메타도 마스터 데이터로 덮어쓰임(개선).
- 우선주/스팩/ETF 포함 — Phase 1은 다 검색 가능. 필터 노이즈는 Phase 2.
- AMEX는 Exchange enum에 별도 값 없어 일단 스킵 (필요 시 enum 확장).

**한투 mst 포맷 (검증된 정보)**:
- URL: `https://new.real.download.dws.co.kr/common/master/{kospi_code.mst|kosdaq_code.mst|nasmst.cod|nysmst.cod}.zip`
- 한투 GitHub `koreainvestment/open-trading-api/stocks_info/kis_kospi_code_mst.py` 의 컬럼 너비 그대로 적용

✅ `./gradlew.bat assembleDebug` BUILD SUCCESSFUL (58s)

**실기기 검증 시 확인**:
1. 첫 실행 후 ~30초 내 stocks 테이블에 ~12,000건 적재
2. "삼성전자" / "애플" / "테슬라" / "엔비디아" 한글 검색 동작
3. "tesla" / "AAPL" 영문 검색 동작
4. 첫 동기화 전엔 기존 단일조회 fallback이 동작 (코드 직접 입력은 됨)
5. 매주 자동 갱신 (Wi-Fi 연결 시)

### UI 정리 (BottomNav 5탭)
- BottomTab에 **SETTINGS** 추가 (5개: 원칙/매매/관심/코치/설정)
- 기존 Principle/Trade/WatchList/Coach 화면의 우상단 ⚙️ 아이콘 모두 제거
- SettingsScreen은 BottomTab 진입 시 navigationIcon(뒤로) 숨김 (`onBack: () -> Unit?` nullable)

### Phase 1 Stage 14: 수동 주문 허용 PRD 개정 ✨ NEW
사용자 요청: "토스 증권 앱처럼 실시간 차트 + 매수/매도 전부, 실제 API 연결" → 기존 PRD가 "자동 주문 코드 작성 금지"를 Phase 1~3 전체에 걸어둔 상태였음. **자동 발주만 금지로 좁히고 매 건 생체 인증을 거친 수동 주문은 허용**으로 개정.

**개정된 PRD 문서 4종**:
- `04_PROJECT_SPEC.md` — "절대 하지 마"에 자동 발주(AI/예약/반복/외부 트리거)만 금지 유지 + 생체 인증 미적용 / IN_FLIGHT 가드 미적용 / 한투 raw 에러 노출 / 송신 전 체결 표시 4건 신규 금지. "항상 해"에 OrderEntity 영구 로그 / BiometricPrompt 강제 / msg_cd 한국어 매핑 / 미체결 5초 polling 30초 timeout / 호가 단위 규칙 준수 5건 신규 추가. ENV 표에 KIS_PRODUCT_CODE 신규.
- `01_PRD.md` — 차별점 표 "증권사 앱" 행을 "코치 + 매매 실행(수동·생체 인증) 통합"으로 갱신. 핵심 기능 표에 "실시간 차트 풀세트" / "실시간 5호가" / "수동 매수/매도/정정/취소" 3개 P1 신규. "안 만드는 것"의 "자동 주문" → "자동 발주(AI 자동/예약/반복/외부 트리거)"로 좁힘. 시나리오 5(수동 주문 + 생체 인증) 신규.
- `02_DATA_MODEL.md` — Order 엔티티 신설(id/ticker/market/side/order_type/qty/price/filled_qty/avg_fill_price/status/krx_order_no/krx_order_org_no/origin_order_no/linked_principle_ids/created_at/submittedAt/completed_at/error_message/raw_msg_cd). 상태 전이 PENDING → SUBMITTED → FILLED/PARTIAL/CANCELED/REJECTED. Trade.externalOrderNo == Order.krx_order_no 자연 키로 연계(Stage 9 컬럼 재활용, 별도 FK 신설 없음). 정정/취소는 origin_order_no로 자기 참조.
- `03_PHASES.md` — Phase 1 기능 목록에 Stage 15(실시간 차트) / Stage 16(수동 주문) 신규. 가장 까다로운 부분에 6번(WS 틱 → 캔들 반영, 누적 거래량 차분 계산) / 7번(주문 멱등성 + 미체결 polling) 추가.

**다음 작업**:
- Stage 15: 실시간 차트 풀세트 — `StockDetailScreen` + `KisWebSocketStream`(H0STASP0 호가 TR 추가 구독) + `KisStockInfoApi`(분봉 API는 이미 통합됨, UI 토글만)
- Stage 16: 수동 주문 — 한투 주문 4종(국내) + 4종(해외) 통합, OrderEntity + DAO + Repository(AppDatabase v8→v9), `OrderEntryScreen` / `OrderConfirmScreen` / `OrdersScreen`, BiometricPrompt 게이트

### Phase 1 Stage 15: 실시간 차트 토스 풀세트 ✨ NEW
사용자 요청: "토스 증권 앱처럼 내가 선택한 주식의 실시간 차트". Stage 11에서 30일 일봉 라인 차트만 있던 종목 상세를 한 Canvas에 캔들+라인 토글 / 분봉·일·주·월·년 토글 / MA 5·20·60 / 거래량 / 크로스헤어 / 실시간 5호가까지 통합.

**도메인 모델 신설**:
- `domain/model/Candle.kt` — ts/open/high/low/close/volume/timeframe + isUp.
- `domain/model/Timeframe.kt` — MIN_1/5/15/60(intraday) / DAY/WEEK/MONTH/YEAR(period) 8개. kisPeriodCode/intradayMinutes/labelKo 부가 정보.
- `domain/model/OrderBookSnapshot.kt` — asks/bids 5호가 + totalAskQty/totalBidQty + expectedPrice + source(WS_LIVE/REST_FALLBACK).
- `domain/model/Enums.kt`에 ChartType { LINE, CANDLE } 추가.

**StockInfoService 확장**:
- `fetchCandles(ticker, timeframe, count=60)` — Intraday는 한투 `timeChart`(`FHKST03010200`) 1분봉 응답 → 5/15/60분 KST 분 경계로 클라이언트 집계 (open=첫 1분 open, high=max, low=min, close=마지막 1분 close, volume=합). Period는 `dailyChart`(`FHKST03010100`)의 FID_PERIOD_DIV_CODE = D/W/M/Y 직매핑. backDays buffer를 W/M/Y마다 다르게.
- `TimeChartBar` / `DailyChartBar` → `Candle` 매퍼 (KST 봉 시작 시각 기준).

**RealtimeChart Composable 신설** (`ui/stockdetail/RealtimeChart.kt`, 240줄):
- 한 Canvas에 캔들(또는 라인) + MA + 거래량 + 크로스헤어 통합.
- 영역 비율: 상단 70% 캔들+MA, 하단 25% 거래량, 5% 갭. Y축 그리드 5등분.
- 한국 관례: 상승 KrUpRed, 하락 KrDownBlue, 동가 회색.
- MA 5/20/60 색상 = 노랑/주황/보라.
- 크로스헤어: `detectDragGesturesAfterLongPress` → 길게 누름 + 드래그 시 십자선 + 시점 봉 강조.

**MarketDataStream 인터페이스 + KisWebSocketStream 강화**:
- 인터페이스에 `books(ticker): Flow<OrderBookSnapshot>` 신규.
- `H0STASP0` 호가 TR 추가 구독 — `subscribe`/`unsubscribe`/`resubscribeAll`/`buildControlMessage` 가 체결+호가 둘 다 메시지 전송. 사용자 결정 "각자 카운트(한투 스펙)" 그대로 별도 슬롯 가정.
- `handleTickFrame` trId 분기 → `dispatchExecFields` / `dispatchAskingFields`.
- `parseKospiAskingTick` — H0STASP0 필드 인덱스 0=종목코드, 3..7=매도1~5, 13..17=매수1~5, 23..27=매도잔량1~5, 33..37=매수잔량1~5, 43=총매도잔량, 44=총매수잔량, 56=예상체결가. KOSPI_ASKING_FIELDS_PER_TICK=59 가정(운영 시 검증).
- `bookBus` SharedFlow extraBufferCapacity=256.

**StockDetailViewModel + UI 통합**:
- `StockDetailUiState`에 timeframe/chartType/candles/candlesLoading/crosshairIndex/orderBook 신규 필드.
- `marketDataStream` 주입. `startTickStream`가 SubscriptionTarget(priority=1)로 subscribe → `ticks(argTicker)` collect → `mergeTickIntoCandles`로 마지막 캔들 high/low/close 갱신 또는 `nextBucketAfter`로 분 경계 넘으면 신규 봉 push (KST LocalDateTime 기준 plusMinutes/plusDays/plusWeeks/plusMonths/plusYears).
- `startBookStream`가 `books(argTicker)` collect → orderBook 갱신.
- `setTimeframe`/`setChartType`/`setCrosshair` 액션. setTimeframe 호출 시 fetchCandles 재호출 + crosshair reset.
- 초기 load 시 한투 `inquire-asking-price-exp-ccn` 응답을 `AskingPriceResponse.toOrderBookSnapshot`로 변환해 REST 폴백 1회 채움 → WS 도착 시 source가 WS_LIVE로 바뀜.
- 신규 `ChartCard` Composable — LazyRow FilterChip 8개(Timeframe.entries) + AssistChip(캔들/라인) + RealtimeChart + CrosshairLabel(O/H/L/C+거래량+시각) + ChartSummary(기간 등락률).
- 신규 `OrderBookCard` Composable — source label(실시간/REST) + 예상체결가 + 매도 5호가(위→아래, 5→1) + 매수 5호가(1→5) + 총 잔량. `LevelRow` 시그니처를 non-null String로 정리.

**처리하지 않은 항목 (Stage 15 OOS)**:
- H0STASP0 필드 인덱스 운영 검증 — 한투 응답 받고 KOSPI_ASKING_EXPECTED_INDEX(56) 등 조정 가능성.
- 분봉 거래량을 1분 cntg_vol 합산으로 처리 — WS 틱이 마지막 캔들에 들어올 때 volume은 그대로(0이거나 기존 값). 누적 거래량 차분 계산은 다음 보강.
- 차트 Y축 가격 라벨 / X축 시간 라벨 — 시각만 표시 (CrosshairLabel에서). 항상 표시되는 가격축 라벨은 다음 보강.
- 해외 종목 분봉/호가는 한투 KR 전용 TR이라 미적용. US 종목은 일봉/주봉/월봉/년봉 + 라인만 동작.

✅ `./gradlew.bat assembleDebug` BUILD SUCCESSFUL (23s)

### Phase 1 Stage 16-1: 한투 주문 API 통합 (API/DB/Service 레이어) ✨ NEW
Stage 14 PRD 개정으로 "사용자 명시 확인 수동 주문 허용"이 정책화된 다음, 실제 한투 주문 API를 통합하고 DB·도메인 서비스 레이어까지 완성. UI(OrderEntryScreen 등)는 Stage 16-2, 보안 강화(BiometricPrompt UI 통합)는 Stage 16-3에서.

**도메인 모델 + Enum**:
- `domain/model/Enums.kt` — OrderType { LIMIT, MARKET }, OrderStatus { PENDING / SUBMITTED / FILLED / PARTIAL / CANCELED / REJECTED } 추가.
- `domain/model/Order.kt` 신규 — id/ticker/market/side/orderType/qty/price/filledQty/avgFillPrice/status/krxOrderNo/krxOrderOrgNo/originOrderNo(자기참조)/linkedPrincipleIds(JSON)/createdAt/submittedAt/completedAt/errorMessage/rawMsgCd. isTerminal 헬퍼.

**Room (AppDatabase v8 → v9, destructive 유지)**:
- `data/local/db/order/OrderEntity.kt` 신규 — 동일 필드 평면. indices ticker/status/krxOrderNo.
- `OrderDao.kt` 신규 — observeAll/observeByTicker/findByStatuses/findById/findByKrxOrderNo/upsert/deleteById.
- `OrderMapper.kt` 신규 — toDomain/toEntity, linkedPrincipleIdsJson은 kotlinx.serialization JSON으로 인코딩/디코딩.
- `AppDatabase.kt` — entities에 OrderEntity 추가, version 9, orderDao() abstract 추가.
- `DatabaseModule.kt` — provideOrderDao.
- 사용자 안내: "지금 사용자 배포 안 함" 결정에 따라 Migration 작성 없이 fallbackToDestructiveMigration 유지.

**Repository 레이어**:
- `domain/repository/OrderRepository.kt` 신규 — observeAll/observeByTicker/findOpen/findById/findByKrxOrderNo/upsert/updateStatus/deleteById.
- `data/repository/OrderRepositoryImpl.kt` 신규 — DAO 위임 + Flow.map 도메인 변환. updateStatus는 terminal(FILLED/CANCELED/REJECTED) 도달 시 completedAt 자동 세팅.
- `RepositoryModule.kt` — bindOrderRepository.

**한투 주문 API (KisTradingApi 확장, 신규 8종 POST/GET)**:
- 국내 매수 `placeDomesticBuy` (`TTTC0802U`) · 매도 `placeDomesticSell` (`TTTC0801U`) · 정정·취소 `reviseDomesticOrder` (`TTTC0803U`).
- 국내 미체결 `fetchDomesticOpenOrders` (`TTTC8036R`).
- 해외 매수 `placeOverseasBuy` (`TTTT1002U`) · 매도 `placeOverseasSell` (`TTTT1006U`) · 정정·취소 `reviseOverseasOrder` (`TTTT1004U`).
- 해외 미체결 `fetchOverseasOpenOrders` (`TTTS3018R`).
- POST 본문은 `data/remote/kis/dto/OrderDtos.kt` 신규에 정의 — `DomesticOrderCashRequest` (CANO/ACNT_PRDT_CD/PDNO/ORD_DVSN/ORD_QTY/ORD_UNPR), `DomesticOrderRevisionRequest` (KRX_FWDG_ORD_ORGNO/ORGN_ODNO/RVSE_CNCL_DVSN_CD/QTY_ALL_ORD_YN), `OverseasOrderRequest`(OVRS_EXCG_CD/OVRS_ORD_UNPR), `OverseasOrderRevisionRequest`. 응답은 `OrderResponse`(rt_cd/msg_cd/msg1) + `OrderResponseOutput`(KRX_FWDG_ORD_ORGNO/ODNO/ORD_TMD). 미체결은 `DomesticOpenOrdersResponse`/`OverseasOpenOrdersResponse` 각각.
- 한투 실전 주문에 필요한 hashkey 헤더는 선택적(`Header("hashkey") hashKey: String? = null`)으로 둠 — 운영에서 거부 시 별도 hashkey 발급 API 추가.

**OrderService 도메인 (`domain/order/`)**:
- `OrderIntent.kt` 신규 — sealed class Place/Revise/Cancel + 일회용 `OrderConfirmation(sourceFlow)` (BiometricPrompt 통과 영수증, UI에서 인증 성공 시 생성).
- `OrderService.kt` 신규 — 핵심 책임:
  - **placeOrder(intent: OrderIntent): Result<Order>** — sendMutex.withLock로 동시 송신 차단(빠른 더블탭·복귀 시 이중 송신 방지). PENDING Order 영구 기록 → credentials/token 확보 → KIS API 호출 → 응답에 따라 SUBMITTED(ODNO/KRX_FWDG_ORD_ORGNO 채움) 또는 REJECTED(에러 메시지 영구 기록) 분기.
  - **dispatchPlace/Revise/Cancel** — Market(KR/US)별로 적절한 한투 API 함수 + DTO 매핑. KR 시장가 = ORD_DVSN "01", US 시장가 = "31".
  - **pollExecution(orderId)** — 5초 간격 30초 timeout. checkExecution이 미체결 응답에서 ODNO 못 찾으면 FILLED 추정, 찾으면 totCcldQty로 PARTIAL/SUBMITTED 판정.
  - **mapKisError(msgCd, msg1)** — PRD에 명문화한 한국어 매핑. APBK0556=잔고부족, APBK0918/0919=호가단위 오류, APBK0013=장 종료, APBK0571/0908=거래정지, APBK0666=수량초과, APBK0017=가격범위, APBK0024=동시주문한도. 미매핑은 "주문 실패 (msg_cd=…)" 형식으로 raw msg1 노출 차단.

**Stage 16-1 OOS**:
- BiometricPrompt UI 게이트(Phase D에서). OrderConfirmation은 인터페이스만 정의 — 실제 인증 통과 검증 로직은 UI 측 BiometricAuth helper와 연동 예정.
- OrderEntryScreen / OrderConfirmScreen / OrdersScreen (Phase D).
- 미체결 → Trade 자동 import는 기존 Stage 9 TradeImportService가 담당. OrderService.pollExecution은 Order 상태 갱신만, Trade 생성은 별도.
- 한투 hashkey 헤더 발급 — 운영에서 hashkey 강제 응답 받으면 KisAuthService에 hashkey 함수 추가.
- US 거래소 코드 자동 결정(NASD/NYSE/AMEX) — 현재는 호출자가 OrderIntent.excgCode로 명시. 종목 메타 기반 자동화는 다음 보강.

✅ `./gradlew.bat assembleDebug` BUILD SUCCESSFUL (26s)

### Phase 1 Stage 16-2: 매수/매도 UI + 생체 인증 게이트 ✨ NEW
Stage 16-1에서 만든 OrderService를 사용자가 실제로 쓸 수 있는 화면을 추가. **자동 발주는 절대 없고**, 매 건마다 BiometricPrompt(BIOMETRIC_STRONG | DEVICE_CREDENTIAL) 통과 후에만 한투 송신.

**의존성 추가**:
- `androidx.biometric:biometric:1.1.0` — BiometricPrompt API.
- `androidx.fragment:fragment-ktx:1.8.5` — BiometricPrompt가 FragmentActivity 호스트를 요구.
- `MainActivity`를 `ComponentActivity` → `FragmentActivity`로 변경 (FragmentActivity가 ComponentActivity의 슈퍼클래스라 enableEdgeToEdge/AndroidEntryPoint 등 그대로 동작).

**BiometricAuth 헬퍼** (`ui/order/BiometricAuth.kt`):
- `FragmentActivity.authenticateForOrder(...)` 확장 함수 — suspendCancellableCoroutine로 BiometricPrompt를 코루틴으로 감싸기.
- canAuthenticate() 결과를 `BiometricAuthResult.Unsupported`(NO_HARDWARE/HW_UNAVAILABLE/NONE_ENROLLED)로 사용자 친화적 메시지 매핑.
- 단발 실패(`onAuthenticationFailed`)는 무시(사용자 재시도 허용), 명시적 에러/취소만 `Failure`.

**OrderEntryScreen + OrderEntryViewModel** (`ui/order/OrderEntryScreen.kt`):
- 입력: 주문 종류(LIMIT/MARKET FilterChip) / 가격(시장가일 땐 disabled) / 수량 / 예상 금액 자동 계산 / 현재가 표시.
- 송신 버튼 — 매수=KrUpRed, 매도=KrDownBlue. 토스 패턴.
- 버튼 클릭 시 `LocalContext as FragmentActivity` 캐스팅 → `authenticateForOrder(...)` → 성공 시 `OrderConfirmation` 생성 → `viewModel.submit(confirmation)`.
- ViewModel.submit이 `OrderService.placeOrder(OrderIntent.Place)` 호출, 성공 시 `OrderService.pollExecution`을 별도 viewModelScope로 launch(UI는 즉시 OrdersScreen으로 이동).
- 사용자 결정 "2단계 확인 미선택" 반영 — 단일 입력 화면에 모든 정보(예상 금액, 안내문) 노출.
- 디스클레이머 영구 노출: "주문 직전 생체 인증을 요청합니다. 자동 발주는 절대 없으며, 송신은 본인 확인 후에만 진행됩니다."

**OrdersScreen + OrdersViewModel** (`ui/order/OrdersScreen.kt`):
- LazyColumn으로 모든 주문 카드 표시 (`OrderRepository.observeAll` → StateFlow).
- 각 카드: 매수/매도 배지 + StatusChip(송신 중/접수/부분체결/체결/취소/거부) + 수량·가격(시장가 표시) + 체결량 + ODNO + 에러 메시지.
- SUBMITTED/PARTIAL 상태는 "취소" 버튼 노출 → BiometricPrompt 통과 → `OrderService.placeOrder(OrderIntent.Cancel)` + 후속 pollExecution.

**StockDetailScreen 통합**:
- Scaffold `bottomBar`에 **매수(빨강)/매도(파랑) 50:50 BottomBar**. 한국 관례 색상 그대로.
- TopAppBar actions에 주문 내역 진입 아이콘 추가.
- `onPlaceOrder(ticker, side)` / `onOpenOrders` 콜백 시그니처 추가.

**AppNavGraph 라우트**:
- `STOCK_ORDER = "stocks/{ticker}/order?side={side}"` — OrderEntryScreen. side 쿼리 파라미터(default BUY).
- `ORDERS = "orders"` — OrdersScreen.
- `AppRoutes.stockOrder(ticker, side)` 헬퍼.
- StockDetailScreen → OrderEntryScreen → 송신 성공 시 `popUpTo(STOCK_DETAIL) { inclusive = false }`로 OrdersScreen 진입.

**Stage 16-2 OOS (16-3에서)**:
- 잔고/예수금 표시 (한투 inquire-balance 통합) — 현재가만 표시.
- 호가 단위(가격대별 1/5/10/50/100/500/1000원) 자동 보정 — 현재는 사용자 입력 그대로 송신.
- 일일 주문 한도 (사용자 결정 "미선택"으로 OOS).
- 한투 hashkey 헤더 발급 — 운영에서 강제 응답 받으면 추가.

✅ `./gradlew.bat assembleDebug` BUILD SUCCESSFUL (2m 10s, biometric/fragment 의존성 첫 다운로드 포함)

### UI Stage A+B: 토스 증권 톤 개편 ✨ NEW (worktree: `ui-toss-overhaul`)
사용자 요청: "토스 증권 앱처럼 일괄 개편" + "클릭 이펙트/화면 전환 애니메이션" + "관심 종목 카드에 현재가 표시 + 클릭 시 상세".

**Foundation (Stage A)**:
- `Color.kt` 전면 재정의 — **토스 블루 `#3182F6`** 강조색 + 거의 흑백 neutral 팔레트 (Background `#F7F8FA`, Surface `#FFFFFF`, TextPrimary `#191F28`, TextSecondary `#6B7684`, Outline `#E5E8EB`)
- `Theme.kt` — **`dynamicColor` 제거** (라이트 고정, 브랜드 일관성). 상태바 색 자동 매칭.
- `Type.kt` — display(40/32/28sp Bold) / headline / title / body / label 11종 위계 전면 정의 (시스템 폰트 + FontWeight)
- 새 파일: `Shape.kt` (corner radius 6~24dp) / `Tokens.kt` (`AppTokens` — space/radius/높이 토큰)
- 기존 Primary/PrimaryDark/ErrorRed 심볼은 alias로 유지 (하위 호환)

**Components (Stage B)** — `ui/common/` 추가:
- `AppCard` — flat (elevation 0), 16dp 라운드, 클릭 시 ripple + **scale 0.98 애니메이션**
- `PrimaryButton` / `SecondaryButton` — 56dp 풀폭, 14dp 라운드, Bold
- `StockRow` — 표준 종목 행 (좌: 이름·코드, 우: 가격·등락 컬러). **price null이면 shimmer skeleton**
- `SkeletonShimmer` — 회색 박스 + 좌→우 shimmer 애니메이션 (1.1s)
- `SectionHeader` — 타이틀+서브타이틀+트레일링 슬롯
- `DisclaimerBox` — 작은 회색 디스클레이머 박스

**관심 종목 카드 현재가 미표시 버그 fix**:
- `WatchListViewModel.init` 분리: ① 신규 ticker 들어오면 **즉시 1회 fetch** (장 시간 무관), ② 장 열림 시 30초 폴링
- 기존 `MarketHours.anyOpen()` 가드가 비장시간에 fetch를 막아서 카드 가격이 영영 안 채워지던 문제 해결
- `refresh()` public 메서드 추가 — TopBar 새로고침 아이콘과 연결
- WatchListCard 새 디자인: `AppCard` + `StockRow` (가격 자리에 항상 표시 — 로딩 중엔 shimmer)
- TopAppBar에서 FAB 제거 → 상단 actions에 🔄/🔍/+ 통합 (토스 스타일)

**클릭/네비게이션 애니메이션**:
- `AppNavGraph` NavHost-level 디폴트 transition — 새 화면: **slideStart + fade 260ms**, 뒤로가기: **slideEnd + fade 260ms**
- `AppCard` 클릭 시 ripple + scale 0.98 → 1.0 (`animateFloatAsState`, 120ms)
- 관심 카드 enter: fadeIn + slideInVertically (offsetY / 6)

**NavigationBar 토스 톤**:
- containerColor = surface (흰)
- 선택 시 indicator = primaryContainer (`#E8F1FE`), 아이콘·라벨 = primary
- 비선택 = onSurfaceVariant
- tonalElevation 0

✅ `./gradlew.bat assembleDebug` BUILD SUCCESSFUL (worktree)

**다음 후속(미진행)**:
- HomeScreen / HoldingsScreen / StockDetailScreen 카드 → `AppCard` 마이그레이션 (도메인 로직 영향 X, 시각 개선만)
- 폼(Trade/Principle 편집·Dialog) OutlinedTextField → 토스 인풋 스타일
- Pretendard 폰트 자산 추가 (`res/font/`)
- BottomNav 6→4탭 슬림화 (원칙/설정 → "메뉴" 탭으로 통합)

---

## 🚧 진행 중 / 다음 시작점

**바로 다음 (Stage 16-3 = Phase E)**: 안정성 보강.
- 호가 단위(가격대별 1/5/10/50/100/500/1000원) 자동 보정 헬퍼 — 사용자 입력 시 잘못된 단위는 가까운 단위로 보정.
- 잔고/예수금 표시 — OrderEntryScreen에 한투 inquire-balance 통합.
- 한투 hashkey 헤더 발급 — 운영에서 hashkey 강제 응답 시 KisAuthService.ensureHashKey 추가.
- KisRateLimiter 주문 전용 더 보수적 적용 (예: 별도 mutex로 주문 송신 동시 1건만).

**병행**: Phase 1 전체 실기기 검증 (`installDebug` → 원칙 등록 → 매매 입력 → 복기/채팅/체크리스트/검색/알림/리서치/BG 다운로드/체결 자동 import).

회귀 검증 항목:
1. Stage 1 (복기): 기존 동작 유지 확인
2. Stage 2 (채팅): 세션 생성 → 메시지 전송 → 스트리밍 OK
3. Stage 3 (체크리스트): 원칙별 응답 → AI 평가 → GO/HOLD/STOP
4. Stage 4 (검색): 종목코드 → REST 응답 + 관심종목 추가
5. Stage 5 (WS): FGS 30분 유지 + 강제 끊김 시 지수 백오프 재연결
6. Stage 6 (알림): 알림 등록 → tick 도달 → 푸시 + status=TRIGGERED
7. Stage 7 (리서치): 종목 + 메모 + 질문 → AI 답변
8. Stage 8 (BG 다운로드): WorkManager Wi-Fi 강제 + 진행률

검증 OK면 Phase 1 출시 후보. Phase 2 (통계 대시보드) 진입.

---

## 📂 핵심 파일 위치

### Domain
- `domain/llm/LLMEngine.kt` `ModelDownloader.kt`
- `domain/market/MarketDataStream.kt` `MarketDataSource.kt` `MarketHours.kt` ✨
- `domain/alert/AlertScheduler.kt`
- `domain/auth/ApiCredentialStore.kt`
- `domain/repository/*` (Trade, Stock, WatchList, TradingPrinciple, TradeReflection, **Coach**, **EntryChecklist**, **PriceAlert** ✨)
- `domain/reflection/ReflectionService.kt`
- `domain/coach/CoachService.kt` ✨
- `domain/entry/EntryChecklistService.kt` ✨
- `domain/research/ResearchService.kt` ✨

### Data
- `data/llm/LiteRtLmLLMEngine.kt` `HttpRangeModelDownloader.kt` `ModelDownloadWorker.kt` ✨ `LLMModule.kt`
- `data/remote/kis/ws/KisWebSocketStream.kt` (백오프/차분 구독/multi-tick) `KisWsMarketDataService.kt`
- `data/remote/kis/market/KisMarketRestApi.kt` `KisMarketDataSource.kt` ✨
- `data/remote/kis/auth/KisAuthService.kt` `rest/KisAuthApi.kt`
- `data/local/db/` (AppDatabase v7, 9 entities: principle / trade / stock / watchlist / reflection / coach session+message / entry / alert)
- `data/local/secure/SecurePassphraseProvider.kt` `ApiCredentialStoreImpl.kt`
- `data/alert/AlertSchedulerImpl.kt` `PriceAlertNotifier.kt` ✨

### UI
- `ui/poc/LlmPocScreen.kt` (PoC #1 + BG 다운로드) / `ui/poc/KisWsPocScreen.kt` (PoC #2/3)
- `ui/reflection/ReflectionScreen.kt`
- `ui/coach/CoachListScreen.kt` `CoachChatScreen.kt` ✨
- `ui/entry/EntryChecklistScreen.kt` ✨
- `ui/search/StockSearchScreen.kt` ✨
- `ui/alert/PriceAlertScreen.kt` ✨
- `ui/research/ResearchScreen.kt` ✨
- `ui/principle/` `ui/trade/` `ui/watchlist/` `ui/settings/` `ui/navigation/`

---

## 🚨 코드 작성 시 매번 점검 (PRD `04_PROJECT_SPEC.md`)

**절대 하지 마**
- API 키/토큰 하드코딩, 평문 Room 저장, 자동 주문, 외부 LLM 전송, 목업 응답, 투자 권유 톤
- FGS 타입 미지정, POST_NOTIFICATIONS 누락, 광범위 외부 저장소 권한
- 모델 .task SHA-256 검증 없이 로드, HTTP 평문, 한투 REST 분당 한도 무시, WS 41종목 한도 무시
- 비장시간 WS 유지 (KR 09:00–15:30 / US 22:30–05:00 KST) — Stage 5에서 MarketHours로 가드
- Compose `remember { mutableStateOf }` ViewModel 대용 (반드시 ViewModel + StateFlow)
- KDoc 안에 `/*` 패턴 (Kotlin 2.2 nested comment로 해석됨 — 풀어쓰기)

**항상 해**
- 변경 전 영향도 알림 + 사용자 확인
- 한투 토큰 만료 시 자동 재발급 (이미 `KisAuthService.ensureAccessToken/ensureApprovalKey` 있음)
- LLM 응답 스트리밍, 모든 화면 에러/빈/로딩 상태
- AI 응답에 "코칭 보조, 매매 책임은 본인" 디스클레이머
- WS heartbeat (한투 PINGPONG echo는 이미 구현)
- DB/LLM/WS 인터페이스 추상화로 테스트 더블 주입 가능
- Z Fold 7 폴드/언폴드 두 비율 레이아웃 확인 (BoxWithConstraints)

---

## 🛠 빌드 + 검증 명령

```powershell
# 디버그 빌드 (마지막 확인: BUILD SUCCESSFUL)
./gradlew.bat assembleDebug

# 실기기 설치 (Z Fold 7 USB + USB 디버깅 ON)
./gradlew.bat installDebug

# instrumented test (SQLCipher PoC)
./gradlew.bat :app:connectedDebugAndroidTest

# 단위 테스트
./gradlew.bat :app:testDebugUnitTest
```

빌드 시간: 풀빌드 ~3분, 증분 ~15초.

---

## ⚠️ 알아둘 함정

1. **Stock에는 `name` 없음** — `nameKo` / `nameEn` 사용
2. **AppDatabase v7 `fallbackToDestructiveMigration`** — schema 변경 시 사용자 DB 와이프됨. Phase 1 후반에 Migration 작성하며 제거.
3. **HuggingFace 파일명**: repo는 `...-litert-lm`, 파일은 `....litertlm` (확장자). 헷갈리지 말 것.
4. **한투 WS는 `ws://` 평문** — `network_security_config.xml`에서 `ops.koreainvestment.com`만 cleartext 예외
5. **Kotlin 2.2 엄격**: inline 명명 주석 `/* foo = */` 금지, KDoc 안에 `/*` 패턴 금지 (둘 다 nested comment 오해)
6. **WorkManager Hilt 통합**: `AndroidManifest.xml`에서 `WorkManagerInitializer` `tools:node="remove"` 필수. 빠지면 기본 초기화가 Hilt 설정을 무시하고 worker 주입 실패.
7. **AlertScheduler tick 구독은 FGS 안에서만 동작**: 앱이 백그라운드인데 FGS가 꺼져 있으면 알림 감지 불가. PoC #3 화면 또는 본 개발 시작 화면에서 FGS 자동 시작 UX 필요.
8. **MarketHours 휴장일 OOS**: 평일 시간 윈도만 판정. 공휴일/대체휴일/조기 폐장은 Phase 2 이후.
9. **종목 검색 휴리스틱**: 통합 검색 API 부재로 단일 조회 매칭. 사용자가 정확한 ticker를 입력해야 함. Phase 2에서 종목 마스터 다운로드 + 로컬 색인.
