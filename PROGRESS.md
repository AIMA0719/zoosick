# AICoachStock 진척 현황 (Handoff)

> **다음 작업 시 이 파일 먼저 읽고 시작.** PRD는 `docs/AICoachStock-PRD/` 안에 5개.

마지막 업데이트: 2026-05-13

---

## 한 문장 요약
Android Z Fold 7 타깃 온디바이스 AI 주식 코치 앱. **PoC 4종 검증 통과 + Phase 1 Stage 1(AI 매매 복기) 코드 완성**, 사용자 실기기 검증 대기.

---

## 핵심 결정 (확정됨)

| 항목 | 값 |
|---|---|
| **LLM** | Gemma 4 E4B (`.litertlm`, 3.66GB) + **LiteRT-LM 0.11.0** (MediaPipe tasks-genai는 deprecated) |
| **모델 호스트** | HuggingFace `litert-community/gemma-4-E4B-it-litert-lm` 의 `gemma-4-E4B-it.litertlm` |
| **SHA-256** | 호스트 미공개 → 다운로드 시 자동 산출·저장, 매 verify()에서 재계산 |
| **한투 API** | 실전 키. REST(HTTPS) + WS(ws://, network_security_config로 도메인 cleartext 예외) |
| **DB** | Room v4 + SQLCipher + Keystore-backed passphrase (32B EncryptedSP) |
| **API 키** | EncryptedSharedPreferences (Room 분리) |
| **빌드** | Kotlin 2.2.21 / KSP 2.2.21-2.0.4 / Hilt 2.57.2 / AGP 8.7.2 |
| **applicationId** | `com.myinfocar.aicoachstock` (debug `.debug`) |
| **해외 실시간** | Phase 1 KR만. 미국 권한은 한투에 별도 신청(영업일 1~7일) — 사용자가 시작 추천 |
| **41종목 한도** | 본 개발 안에서 실측 (현재는 sort + take 단순 cap) |

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

### Phase 1 Stage 1: AI 매매 복기
- DB: `TradeReflectionEntity` (FK→Trade, CASCADE, UNIQUE tradeId) / DAO / Mapper
- Repo: `TradeReflectionRepository` + Impl
- Service: `ReflectionService` — LLM 호출 + `LESSON:` / `VIOLATED:` 마커 파싱 + 활성 원칙 sanity-filter
- UI: `ReflectionScreen` (Trade 요약 / 스트리밍 분석 / 위반 chip / 교훈 카드 / 내 메모 / 디스클레이머)
- 라우트: `reflections/{tradeId}`, TradeList 카드 우하단 "🤖 AI 복기" 진입
- AppDatabase v3 → v4 (`fallbackToDestructiveMigration` 정책 유지)
- ✅ BUILD SUCCESSFUL — 사용자 실기기 검증 **대기 중**

---

## 🚧 진행 중 / 다음 시작점

**바로 다음**: 사용자가 Stage 1 실기기 검증 (`installDebug` → 원칙 등록 → 매매 입력 → AI 복기 생성). 결과 확인 후 Stage 2 진입.

검증 OK면 Stage 1 task `#7` completed로 마크 후 Stage 2 시작.

---

## ⏳ Phase 1 남은 Stage (vertical slice, 매 stage 끝나면 빌드 검증)

| Stage | 작업 | 의존 |
|---|---|---|
| **2** | **코치 채팅** (CoachSession/CoachMessage DB + ChatViewModel + ChatScreen, 스트리밍) | LLM PoC 인프라 |
| 3 | **진입 체크리스트** (EntryChecklist DB + GO/HOLD/STOP AI 판정) | LLM, 원칙 |
| 4 | **한투 REST 시세 폴백 + 종목 검색** (MarketDataSource 구현, KisMarketRestApi) | KIS REST |
| 5 | **WS 본 개발 강화** (41종목 우선순위 큐, 지수 백오프, 비장시간 스케줄) | PoC #2 코어 |
| 6 | **PriceAlert + AlertScheduler** (손절/익절 알림 트리거 — FGS 안에서 tick 감지) | PoC #3, MarketDataSource |
| 7 | **종목 리서치 Q&A** (Stock 메타 + 수동 재무 + AI 질의) | LLM, Stage 4 |
| 8 | **모델 다운로더 강화** (WorkManager + Wi-Fi 강제 + 미러 호스트) | PoC #1 코어 |

---

## 📂 핵심 파일 위치

### Domain
- `domain/llm/LLMEngine.kt` `ModelDownloader.kt`
- `domain/market/MarketDataStream.kt` `MarketDataSource.kt`
- `domain/alert/AlertScheduler.kt`
- `domain/auth/ApiCredentialStore.kt`
- `domain/repository/*` (TradeRepository, StockRepository, WatchListRepository, TradingPrincipleRepository, TradeReflectionRepository)
- `domain/reflection/ReflectionService.kt` ← Stage 1

### Data
- `data/llm/LiteRtLmLLMEngine.kt` `HttpRangeModelDownloader.kt` `LLMModule.kt`
- `data/remote/kis/ws/KisWebSocketStream.kt` `KisWsMarketDataService.kt` `MarketDataModule.kt`
- `data/remote/kis/auth/KisAuthService.kt` `rest/KisAuthApi.kt`
- `data/local/db/` (AppDatabase v4, TypeConverters, principle / trade / stock / watchlist / reflection 5개 엔티티)
- `data/local/secure/SecurePassphraseProvider.kt` `ApiCredentialStoreImpl.kt`

### UI
- `ui/poc/LlmPocScreen.kt` (모델 다운로드/로드/추론 PoC)
- `ui/poc/KisWsPocScreen.kt` (WS PoC + FGS 30분+ 검증)
- `ui/reflection/ReflectionScreen.kt` ← Stage 1
- `ui/principle/` `ui/trade/` `ui/watchlist/` `ui/settings/` `ui/navigation/`

---

## 🚨 코드 작성 시 매번 점검 (PRD `04_PROJECT_SPEC.md`)

**절대 하지 마**
- API 키/토큰 하드코딩, 평문 Room 저장, 자동 주문, 외부 LLM 전송, 목업 응답, 투자 권유 톤
- FGS 타입 미지정, POST_NOTIFICATIONS 누락, 광범위 외부 저장소 권한
- 모델 .task SHA-256 검증 없이 로드, HTTP 평문, 한투 REST 분당 한도 무시, WS 41종목 한도 무시
- 비장시간 WS 유지 (KR 09:00–15:30 / US 22:30–05:00 KST)
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
# 디버그 빌드
./gradlew.bat assembleDebug

# 실기기 설치 (Z Fold 7 USB + USB 디버깅 ON)
./gradlew.bat installDebug

# instrumented test (SQLCipher PoC)
./gradlew.bat :app:connectedDebugAndroidTest
```

빌드 시간: 풀빌드 ~45초, 증분 ~20초.

---

## ⚠️ 알아둘 함정

1. **Stock에는 `name` 없음** — `nameKo` / `nameEn` 사용
2. **AppDatabase v4 `fallbackToDestructiveMigration`** — schema 변경 시 사용자 DB 와이프됨. Phase 1 후반에 Migration 작성하며 제거.
3. **HuggingFace 파일명**: repo는 `...-litert-lm`, 파일은 `....litertlm` (확장자). 헷갈리지 말 것.
4. **한투 WS는 `ws://` 평문** — `network_security_config.xml`에서 `ops.koreainvestment.com`만 cleartext 예외
5. **Kotlin 2.2 엄격**: inline 명명 주석 `/* foo = */` 금지, KDoc 안에 `/*` 패턴 금지 (둘 다 nested comment 오해)
