# AICoachStock — 프로젝트 스펙

> AI가 코드를 짤 때 지켜야 할 규칙과 절대 하면 안 되는 것.
> 이 문서를 AI에게 항상 함께 공유하세요.

---

## 기술 스택

| 영역 | 선택 | 이유 |
|------|------|------|
| 언어 | Kotlin (JDK 17) | Android 1st-class, 코루틴/Compose 친화 |
| UI | Jetpack Compose (XML 없음) | 사용자 선택. ViewModel/StateFlow와 자연스러운 결합 |
| 아키텍처 | ViewModel + StateFlow (단방향 데이터 흐름) | Orbit MVI 미사용. 단순성 우선 |
| DI | Hilt | infoCar 친숙도, 컴파일 타임 검증 |
| 비동기 | Coroutines + Flow | Android 표준 |
| 영속화 | Room 2.6+ + **SQLCipher** | 매매·일지 데이터 암호화 필수 |
| 보안 키 저장 | EncryptedSharedPreferences (AndroidX Security Crypto) | 한투 API 키 별도 보관 |
| 네트워크 (REST) | Retrofit2 + OkHttp + kotlinx-serialization | 한투 OpenAPI 토큰/검색/종가 |
| 네트워크 (WebSocket) | OkHttp WebSocket + kotlinx-serialization | 한투 실시간 시세 스트림 |
| 온디바이스 LLM | MediaPipe LLM Inference API (Gemma 4 E4B `.task`) | Google AI Edge 표준. 미지원 시 Gemma 3n E4B 폴백 |
| 백그라운드 | Foreground Service (Android 14+ `dataSync` 타입) | 장 시간대 WebSocket 연결 유지 |
| 모델 다운로드 | WorkManager + OkHttp Range 다운로드 | Gemma 4 E4B `.task` ~3GB, 재개·재시도·진행률 |
| 알림 | NotificationCompat + Channel | 손절/익절/원칙 위반 의심 |
| 로깅 | Timber | 디버그/릴리즈 분리 |
| 테스트 | JUnit5 + Turbine + MockK + Robolectric | StateFlow·Flow 테스트 |
| 빌드 | Gradle Kotlin DSL, AGP 8.x | compileSdk 35 / minSdk 31 (Z Fold 7 단일 기기 타깃이라 최신) |

> **참고**: infoCar 프로젝트 스택과 유사하나 본 앱은 Orbit MVI 미사용. ViewModel + StateFlow만.

---

## 프로젝트 구조

```
AICoachStock/
├── app/
│   └── src/main/
│       ├── java/com/myinfocar/aicoachstock/
│       │   ├── MainActivity.kt
│       │   ├── AICoachApplication.kt           # @HiltAndroidApp
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── dashboard/                   # 메인
│       │   │   ├── principle/                   # 매매 원칙 화면
│       │   │   ├── watchlist/                   # 관심종목
│       │   │   ├── stock/                       # 종목 상세
│       │   │   ├── trade/                       # 매매기록 입력/리스트
│       │   │   ├── reflection/                  # AI 복기
│       │   │   ├── checklist/                   # 진입 체크리스트
│       │   │   ├── coach/                       # 코치 채팅
│       │   │   ├── alert/                       # 알림 관리
│       │   │   ├── settings/                    # API 키, 폴링 주기 등
│       │   │   └── common/                      # 공용 Composable
│       │   ├── domain/                          # 순수 비즈니스 로직 (Android 의존 X)
│       │   │   ├── model/                       # TradingPrinciple, Trade …
│       │   │   ├── repository/                  # 인터페이스
│       │   │   └── usecase/                     # GenerateReflectionUseCase 등
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── db/                      # Room DAO/Entity
│       │   │   │   ├── secure/                  # EncryptedSharedPreferences 래퍼
│       │   │   │   └── cache/                   # MarketQuoteCache (메모리)
│       │   │   ├── remote/
│       │   │   │   ├── kis/                     # 한투 OpenAPI
│       │   │   │   │   ├── rest/
│       │   │   │   │   │   ├── KisAuthApi.kt              # 토큰·approval_key 발급
│       │   │   │   │   │   ├── KisQuoteApi.kt             # 국내 종가/검색
│       │   │   │   │   │   └── KisOverseasApi.kt          # 해외 종가/검색
│       │   │   │   │   ├── ws/
│       │   │   │   │   │   ├── KisWebSocketClient.kt      # 연결·재연결·heartbeat
│       │   │   │   │   │   ├── KisSubscriptionManager.kt  # 41종목 우선순위 큐
│       │   │   │   │   │   ├── KisTickParser.kt           # pipe-delimited 파싱
│       │   │   │   │   │   └── TrCodes.kt                 # H0STCNT0 등 상수
│       │   │   │   │   └── dto/
│       │   │   ├── llm/
│       │   │   │   ├── LLMEngine.kt             # 인터페이스
│       │   │   │   ├── GemmaMediaPipeEngine.kt  # MediaPipe 구현
│       │   │   │   ├── ModelDownloader.kt       # WorkManager 다운로드
│       │   │   │   ├── ModelFileResolver.kt     # 파일 경로/존재여부/SHA-256
│       │   │   │   ├── PromptBuilder.kt
│       │   │   │   └── ContextRetriever.kt      # RAG 컨텍스트 추출
│       │   │   └── repository/                  # impl
│       │   ├── service/
│       │   │   ├── PriceWatchService.kt         # Foreground Service
│       │   │   └── NotificationHelper.kt
│       │   └── di/                              # Hilt Modules
│       ├── res/
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

### 핵심 추상화 (선구현 인터페이스)
```kotlin
interface LLMEngine {
    suspend fun load(): Result<Unit>
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        context: List<ContextChunk>,
        params: GenerationParams = GenerationParams()
    ): Flow<GenerationEvent>   // streaming token / final / error
    fun unload()
    val modelVersion: String
}

interface MarketDataStream {
    val connectionState: StateFlow<ConnectionState>      // DISCONNECTED/CONNECTING/CONNECTED/DEGRADED

    suspend fun connect()
    suspend fun disconnect()

    /** 구독 등록. 41종목 한도 초과 시 우선순위(priority 낮을수록 우선) 큐에서 자동 교체 */
    fun subscribe(targets: List<SubscriptionTarget>)
    fun unsubscribe(tickers: List<String>)

    /** 종목별 실시간 tick Flow. 미구독 종목 ticker 입력 시 emptyFlow + REST 폴백 트리거 */
    fun ticks(ticker: String): Flow<MarketTick>

    /** 디버그·진단용 */
    val currentSubscriptions: StateFlow<List<SubscriptionTarget>>
}

data class SubscriptionTarget(
    val ticker: String,
    val market: Market,                  // KR / US
    val reason: SubscriptionReason,      // ACTIVE_ALERT / WATCHLIST / OPEN_POSITION
    val priority: Int                    // 1=최상
)

interface MarketDataSource {              // REST 폴백 + 단발 쿼리
    suspend fun fetchClosePrice(ticker: String, market: Market): Result<Quote>
    suspend fun searchStocks(query: String): Result<List<Stock>>
    suspend fun fetchFundamentals(ticker: String): Result<Fundamentals>
}

interface AlertScheduler {
    fun register(alert: PriceAlert)
    fun cancel(alertId: String)
    fun reschedule(activeAlerts: List<PriceAlert>)
}

interface ModelDownloader {
    /** 모델 파일이 앱 내부 저장소에 이미 존재하고 SHA-256이 맞으면 true */
    suspend fun isModelReady(): Boolean

    /** 다운로드 시작/재개. 진행률·상태를 Flow로 발행 */
    fun download(): Flow<DownloadEvent>

    suspend fun verify(): Result<Unit>   // SHA-256
    suspend fun delete(): Result<Unit>   // 재다운로드/롤백
}

sealed interface DownloadEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadEvent
    data object Paused : DownloadEvent
    data object Verifying : DownloadEvent
    data object Completed : DownloadEvent
    data class Failed(val reason: Reason, val cause: Throwable?) : DownloadEvent
    enum class Reason { NO_NETWORK, METERED_NETWORK_BLOCKED, DISK_FULL, CHECKSUM_MISMATCH, HTTP_ERROR, UNKNOWN }
}
```

### 모델 다운로드 흐름 (Phase 1)

1. 앱 첫 진입 → `ModelDownloader.isModelReady()` 체크
2. false면 온보딩의 "모델 준비" 단계로 라우팅
3. 화면에서 다운로드 시작 (기본: Wi-Fi 전용. 사용자가 명시 동의 시 셀룰러 허용)
4. WorkManager `OneTimeWorkRequest` + `setRequiredNetworkType(UNMETERED)`로 백그라운드 수행
5. OkHttp `Range` 헤더로 부분 다운로드 재개 지원
6. 완료 시 SHA-256 검증 → 일치하면 `models/gemma-4-e4b-it.task`로 rename, 불일치면 삭제 후 재시도
7. 다운로드 중 앱 종료해도 WorkManager가 이어받음. 알림에 진행률 표시
8. 다운로드 호스트(기본/미러)와 예상 체크섬은 `BuildConfig`로 주입, 변경 시 앱 업데이트 필요

### 모델 파일 저장 위치
- 경로: `context.filesDir / "models" / "gemma-4-e4b-it.task"` (앱 전용, 자동 백업 제외)
- 또는 `context.noBackupFilesDir` 사용 (Google 자동 백업이 3GB 업로드하는 사고 방지)
- **외부 저장소·다운로드 폴더 사용 금지** (다른 앱이 변조 가능)

---

## 절대 하지 마 (DO NOT)

> AI에게 코드를 시킬 때 이 목록을 반드시 함께 공유하세요.

- [ ] 한투 App Key/Secret/Access Token을 **코드·리소스·로그에 하드코딩하지 마** — 무조건 EncryptedSharedPreferences
- [ ] 매매 데이터·일지를 **평문 Room에 저장하지 마** — SQLCipher 필수, passphrase는 Android Keystore에 보관
- [ ] **자동 주문(매수/매도) 코드를 작성하지 마** — Phase 1~3 전부 Out of Scope
- [ ] 사용자 매매 데이터·일지·원칙을 **외부 서버·외부 LLM(OpenAI/Anthropic/Gemini Cloud)으로 전송하지 마** — 온디바이스 추론 한정
- [ ] **목업/하드코딩 응답으로 "완성"이라고 보고하지 마** — 실제 한투 API, 실제 Gemma 모델로 검증
- [ ] AI 응답을 **투자 권유처럼 단정하지 마** — 모든 출력에 "최종 판단은 본인" 톤 유지
- [ ] Foreground Service를 **타입 미지정으로 등록하지 마** (Android 14+ 필수)
- [ ] 알림 권한(POST_NOTIFICATIONS, Android 13+) **체크 없이 알림 시도하지 마**
- [ ] `WRITE_EXTERNAL_STORAGE`·`READ_EXTERNAL_STORAGE` 같은 광범위 권한 요청하지 마 (Scoped Storage)
- [ ] 모델 파일을 **외부 저장소·다운로드 폴더**에 저장하지 마 (변조 위험). `filesDir`/`noBackupFilesDir`만 사용
- [ ] 모델 다운로드를 **셀룰러로 기본 허용하지 마** (3GB 데이터 사고). 기본 Wi-Fi 전용, 사용자 명시 동의 시에만 셀룰러
- [ ] 다운로드한 `.task` 파일을 **SHA-256 검증 없이 LLM 엔진에 로드하지 마** (변조·손상 방지)
- [ ] 인터넷 권한만 가지고 **HTTP 평문 호출하지 마** — 한투 API는 HTTPS 필수, network_security_config 명시
- [ ] 한투 REST를 **분당 호출 한도 무시하고 마구잡이로 호출하지 마** — 토큰버킷·디바운스 적용
- [ ] WebSocket 구독을 **41종목 한도 무시하고 무제한 등록하지 마** — 우선순위 큐로 강제
- [ ] WebSocket 끊김 시 **즉시 폭주 재연결하지 마** — 지수 백오프 (1s → 2s → 4s → 8s → 30s 상한)
- [ ] 비장시간에 **WebSocket 연결 유지하지 마** — 종료하고 REST 종가만 사용
- [ ] 차트 라이브러리를 **무겁게 도입하지 마** (Phase 1 차트 없음. Phase 2에 가벼운 라이브러리만)
- [ ] Compose에서 **`remember { mutableStateOf }`를 ViewModel 대용으로 쓰지 마** — 상태는 ViewModel + StateFlow

---

## 항상 해 (ALWAYS DO)

- [ ] 변경 전에 **모듈 영향도와 인터페이스 변경**을 먼저 보여주고 확인 받기
- [ ] 한투 API 키·토큰은 EncryptedSharedPreferences. **만료 시 자동 재발급**
- [ ] LLM 응답은 **Streaming**으로 받아 UI에 즉시 반영 (대기 체감 ↓)
- [ ] 모든 화면에 **에러 상태 + 빈 상태(Empty State) + 로딩 상태** UI를 명시적으로
- [ ] AI 응답 본문 끝에 **"본 응답은 코칭 보조이며 매매 책임은 본인에게 있다"** 디스클레이머 자동 첨부
- [ ] Foreground Service는 **장 시간대(한국장 09:00~15:30 / 미장 22:30~05:00 KST)에만** WebSocket 연결 (배터리)
- [ ] WebSocket 메시지 파싱은 한투 포맷(pipe-delimited `|` 구분) 기준. tr_id별 필드 위치 상수로 분리
- [ ] WebSocket heartbeat 수신·송신 모두 처리. 일정 시간(~30초) 무응답 시 재연결
- [ ] approval_key는 Access Token과 별개. 만료 정책 분리해 갱신
- [ ] DB·키저장소·LLM 엔진 모두 **인터페이스로 추상화** (테스트 더블 주입 가능하게)
- [ ] PR 단위로 **Room Migration** 작성. 스키마 바꿀 때마다 마이그레이션 테스트
- [ ] Z Fold 7 **폴드/언폴드 두 화면 비율** 모두에서 레이아웃 확인 (Compose `BoxWithConstraints` 활용)
- [ ] 폴링 주기·알림 정책은 **사용자 설정 화면**에서 조정 가능하게

---

## 테스트 방법

```bash
# 로컬 실행 (Z Fold 7 USB 연결)
./gradlew installDebug

# 단위 테스트
./gradlew testDebugUnitTest

# 린트
./gradlew lintDebug

# 릴리즈 APK (사이드로드)
./gradlew assembleRelease
```

### 수동 QA 체크리스트 (Phase 1 종료 시)
1. 앱 첫 실행 → 한투 API 키 입력 → 토큰 발급 성공
2. 관심종목 5개 등록 후 시세가 10초 내 갱신
3. 매매기록 1건 입력 → "AI 복기 요청" → 15초 내 결과 표시
4. 진입 체크리스트 작성 → AI GO/HOLD/STOP 결과 + 근거
5. PriceAlert 등록 후 임의로 target_price를 현재가 근처로 설정 → 알림 발동
6. 앱 종료 후 5분 뒤 시세 변동 시 알림 정상 수신
7. 코치 채팅에서 "지난주 매매 어땠어?" 질문 → 최근 매매 컨텍스트 반영 답변
8. 앱 강제 종료 → 재실행 시 데이터 보존
9. 비행기 모드 → AI 복기·채팅 동작 확인 (온디바이스 정상), 시세 조회는 오프라인 안내

---

## 배포 방법

- 개인 사용이므로 **Play Store 출시 없음**
- 릴리즈 APK 빌드 → 본인 폰(Z Fold 7) 사이드로드
- 키스토어는 로컬 보관 + 1Password 또는 안전한 외부 백업
- (선택) GitHub Actions로 master 푸시 시 APK 빌드 → Release 아티팩트로 업로드

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk → 폰으로 전송 후 설치
```

---

## 환경변수 / 비밀

| 변수명 | 설명 | 어디서 발급 |
|--------|------|------------|
| KIS_APP_KEY | 한투 OpenAPI App Key | 한투 OpenAPI 포털 (https://apiportal.koreainvestment.com) |
| KIS_APP_SECRET | 한투 OpenAPI App Secret | 동일 |
| KIS_ENV | PROD / VTS 토글 (실전 / 모의) | 사용자 선택 |
| KIS_ACCESS_TOKEN | REST용 토큰 (앱 자동 발급) | `/oauth2/tokenP` 응답 |
| KIS_APPROVAL_KEY | WebSocket 인증용 키 (앱 자동 발급) | `/oauth2/Approval` 응답 |
| KIS_ACCOUNT_NO | (Phase 2 계좌조회 시) | 한투 계좌번호 |
| KEYSTORE_PASSWORD | 릴리즈 서명용 | 본인 생성 |
| SQLCIPHER_PASSPHRASE_KEY_ALIAS | Keystore alias | 코드에서 자동 생성 |

> 위 값은 **앱 내 설정 화면에서 사용자 입력 → EncryptedSharedPreferences 저장**. `.env`나 `gradle.properties`에 하드코딩 금지.
> 키스토어 파일과 비번만 `~/.android-keys/aicoachstock/`에 평문 노출 없이 보관.

---

## 면책 / 법적 주의

- 본 앱은 **개인 사용 목적**이며 투자 자문업·투자 권유에 해당하지 않음
- 모든 AI 출력은 **참고용**이며 매매 결정·손익 책임은 사용자 본인
- 한투 OpenAPI 이용약관 준수 (호출 한도, 데이터 재배포 금지 등)
- 배포(Play Store 등)할 경우 자본시장법상 투자자문업 등록 필요 여부 검토 후 진행

---

## [NEEDS CLARIFICATION]

- [ ] **모델 다운로드 호스트 URL + 체크섬** — Hugging Face(`google/gemma-4-e4b-it-litert`) 우선. 라이선스 동의 토큰 필요 시 처리 방식, 미러 URL 1개 이상 확보, 정식 `.task` 파일 SHA-256 값 확인 후 BuildConfig 주입
- [ ] **MediaPipe LLM Inference API의 Gemma 4 지원 시점** — 현재 SDK 버전 호환성
- [ ] **한투 해외주식 실시간 시세 권한** — 일반 가입자 대상 지연시세(15분) vs. 실시간 신청 필요 여부
- [ ] **Foreground Service 타입** — `dataSync` 우선 검토 (WebSocket 시세 스트림 = 데이터 동기화 성격)
- [ ] **WebSocket 구독 한도** — 한투 공식 문서 기준 41종목으로 가정. 변경 가능성 확인 필요
- [ ] **국내·해외 연결 통합 여부** — 1 WebSocket으로 H0STCNT0(국내) + HDFSCNT0(해외) 혼합 구독 가능한지 검증
- [ ] **앱 ID(applicationId)** — 예: `com.myinfocar.aicoachstock`. infoCar 패키지와 충돌 없이 확정
