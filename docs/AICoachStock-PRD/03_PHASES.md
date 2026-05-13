# AICoachStock — Phase 분리 계획

> 한 번에 다 만들면 복잡해져서 품질이 떨어집니다.
> Phase별로 나눠서 각각 "진짜 동작하는 제품"을 만듭니다.

---

## Phase 1: 풀세트 MVP (예상 기간: 6~8주)

### 목표
한국·미국 시장 시세를 받아오고, 매매기록을 AI로 복기·체크하며, 손절·익절 알림까지 동작하는 **완성형 1인 코치 앱**.

### 기능
- [ ] 매매 원칙 CRUD (TradingPrinciple — 카테고리/중요도/순서)
- [ ] 종목 검색 + 관심종목 등록/정렬/삭제 (WatchList, Stock)
- [ ] 매매기록 수동 입력 (Trade — 종목·가격·수량·이유·감정 태그)
- [ ] AI 매매 복기 (TradeReflection — Gemma 4 E4B 추론, 원칙 위반 자동 표시)
- [ ] 코치 채팅 (CoachSession + CoachMessage, 컨텍스트 자동 주입)
- [ ] 진입 체크리스트 (EntryChecklist — 원칙 응답 + AI 판정 GO/HOLD/STOP)
- [ ] 종목 리서치 Q&A (종목 메타 + 수동 재무 입력 + AI 질의응답)
- [ ] **한투 OpenAPI WebSocket 실시간 시세** — `approval_key` 발급, 국내(H0STCNT0)·해외(HDFSCNT0) 구독, 자동 재연결·재구독, 41종목 우선순위 큐
- [ ] 한투 REST 폴백 — 비장시간 종가, WebSocket 장애 시
- [ ] 손절/익절 알림 (PriceAlert + Foreground Service로 WebSocket 유지 + tick 단위 감지)
- [ ] **Gemma 4 E4B 모델 자동 다운로드 (~3GB)** — 첫 실행 시 Wi-Fi 권장, 진행률·일시정지·재개·SHA-256 검증·재시도
- [ ] Gemma 4 E4B 모델 로드 (MediaPipe LLM Inference API)
- [ ] Room + SQLCipher 암호화 저장
- [ ] 한투 API 키 EncryptedSharedPreferences 보관

### 데이터 (Phase 1에서 쓰는 엔티티)
- TradingPrinciple
- WatchList / Stock
- Trade / TradeReflection
- EntryChecklist
- PriceAlert
- CoachSession / CoachMessage
- MarketQuoteCache
- ApiCredential (별도)

### 인증
- 앱 로그인 없음
- 한투 API 키만 EncryptedSharedPreferences에 로컬 저장
- (선택) 앱 진입 시 시스템 생체인증(BiometricPrompt) — 코드 자리만 잡고 토글로 켤 수 있게

### "진짜 제품" 체크리스트
- [ ] 실제 Room DB 연결 (목업 데이터 X)
- [ ] 실제 한투 OpenAPI 연동 (Mock Server X)
- [ ] 실제 Gemma 4 E4B 모델 추론 (응답 stub X)
- [ ] Foreground Service가 백그라운드에서 정상 동작 (Doze·App Standby 대응)
- [ ] 앱 강제 종료·재시작 시 데이터 보존
- [ ] APK 사이드로드로 본인 폰(Z Fold 7)에서 실사용 1주 이상

### 가장 까다로운 부분 (선행 검증)
1. **Gemma 4 E4B `.task` 자체 다운로드 + 로드** — 외부 호스트(Hugging Face 등)에서 약 3GB 다운로드, 앱 내부 저장소 보관, MediaPipe LLM Inference API로 로드. 다운로드는 WorkManager 또는 DownloadManager 사용, 무결성 검증(SHA-256), 재시도·일시정지·재개 지원.
2. **MediaPipe LLM Inference API의 Gemma 4 지원 여부** — 출시 직후라 SDK 호환성 확인 필요. 미지원 시 Gemma 3n E4B로 임시 대체.
3. **한투 OpenAPI 토큰 + approval_key 갱신** — Access Token 만료 시 자동 재발급, WebSocket용 approval_key 별도 발급·갱신.
4. **WebSocket 안정 운영** — 자동 재연결(지수 백오프), heartbeat, 재구독 큐, 41종목 한도 우선순위, Android 14+ Foreground Service(`dataSync`) 타입.
5. **컨텍스트 길이 관리** — E4B 입력 토큰 한도 안에서 원칙 + 관련 매매 N건을 RAG로 주입하는 전략.

### Phase 1 시작 프롬프트 (AI에게 코드 시킬 때 복사)
```
이 PRD를 읽고 Phase 1을 구현해주세요.
@PRD/01_PRD.md
@PRD/02_DATA_MODEL.md
@PRD/04_PROJECT_SPEC.md

Phase 1 범위:
- 매매 원칙 CRUD
- 종목 검색/관심종목
- 매매기록 입력
- AI 매매 복기 (Gemma 4 E4B)
- 코치 채팅
- 진입 체크리스트
- 종목 리서치 Q&A
- 한투 OpenAPI 시세 (국내+해외)
- 손절/익절 알림 (Foreground Service)

반드시 지켜야 할 것:
- 04_PROJECT_SPEC.md의 "절대 하지 마" 목록 준수
- 실제 DB 연결 (Room + SQLCipher, 목업 X)
- 한투 API 키는 EncryptedSharedPreferences 사용
- Gemma 4 E4B 미지원 환경이면 Gemma 3n E4B로 폴백 가능하게 추상화
- UI는 순수 Jetpack Compose + ViewModel + StateFlow
- DI는 Hilt
- 비동기는 Coroutines + Flow

먼저 모듈 구조와 핵심 인터페이스(LLMEngine, MarketDataSource, AlertScheduler)를 제시한 뒤 구현 순서를 확인 받고 진행하세요.
```

---

## Phase 2: 통계·시각화 (예상 기간: 3~4주)

### 전제 조건
- Phase 1이 본인 폰에서 안정적으로 1개월 이상 사용된 상태
- 매매기록·복기 데이터 최소 30건 이상 누적

### 목표
누적된 매매·복기 데이터를 **자기 패턴**으로 보여줘 코칭 효과를 강화.

### 기능
- [ ] 수익 대시보드 (일·주·월 수익률, 누적 수익)
- [ ] 승률/속이익률·속이해율 통계 (`Trade` 쿼리 기반)
- [ ] 감정 태그 트렌드 (감정별 손익 상관, 시간대별 분포)
- [ ] 원칙 위반 패턴 리포트 (가장 자주 깨진 원칙 Top 5 + 추세)
- [ ] 보유 기간 분포·평균 보유 시간
- [ ] 종목별·시장별 수익 분해

### 추가 데이터
- (필요시) Position 머터리얼라이즈드 뷰 — 평단/현재 보유 수량 캐시
- 통계 결과 자체는 별도 영속 테이블 없이 쿼리/메모이제이션

### 통합 테스트
- Phase 1 기능이 여전히 정상 동작하는지 회귀 테스트
- 통계 산출이 매매기록 추가/삭제에 동기 반영되는지

---

## Phase 3: 고도화 (추후)

### 전제 조건
- Phase 1 + 2가 6개월 이상 본인 사용으로 안정화
- 데이터 100건 이상 누적 (AI 원칙 개선 제안의 신뢰성 확보)

### 목표
입력 마찰을 줄이고, AI가 능동적으로 코칭 품질을 끌어올리는 단계.

### 기능
- [ ] 음성 입력 (Android STT — 매매 이유·메모·채팅)
- [ ] 원칙 자동 개선 제안 (반복 위반 원칙에 대한 AI 수정안)
- [ ] 키움 OpenAPI 이중화 (한투 장애 시 폴백)
- [ ] 클라우드 백업 (E2EE, 사용자 선택형 — Google Drive 또는 사용자 지정 WebDAV)
- [ ] (선택) 위젯·바로가기 (자주 보는 종목 즉시 시세)
- [ ] (선택) 모델 업그레이드 메커니즘 (Gemma 후속 버전 사이드로드 슬롯)

### 주의사항
- 클라우드 백업은 **민감 데이터** — 반드시 사용자 측 키로 E2EE. 평문 업로드 금지
- 음성 인식은 온디바이스 STT 우선 (Google STT 클라우드 사용 시 사용자 명시 동의)
- 키움 OpenAPI는 PC 전용 OpenAPI+가 아닌 모바일 가능한 API(KAOPENAPI Mobile) 사용 여부 사전 확인

---

## Phase 로드맵 요약

| Phase | 핵심 기능 | 상태 |
|-------|----------|------|
| Phase 1 (MVP) | 매매기록+AI 복기, 코치 채팅, 진입 체크리스트, 시세, 손절·익절 알림 | 시작 전 |
| Phase 2 | 수익 대시보드, 감정·원칙 패턴 통계 | Phase 1 완료 후 |
| Phase 3 | 음성, 원칙 자동 개선, 키움 이중화, 클라우드 백업 | Phase 2 완료 후 |

---

## 리스크 & 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| Gemma 4 E4B MediaPipe 미지원 (출시 직후) | Phase 1 핵심 마비 | Gemma 3n E4B 폴백 인터페이스 마련 |
| 모델 다운로드 호스트 차단·요금제 | 첫 실행 불가 | Wi-Fi 강제, 미러 URL 2개 이상, 사용자가 외부에서 받은 `.task` 수동 import 옵션 |
| 다운로드 중 끊김 | 3GB 재다운로드 부담 | HTTP Range 요청 기반 재개, WorkManager 백그라운드 |
| WebSocket 구독 한도(41) 초과 | 일부 종목 시세 누락 | 활성 알림 > 보유 종목 > 관심종목 순 우선순위, 초과 종목은 REST 폴링으로 보조 |
| WebSocket 끊김·재연결 실패 | 손절 알림 지연·누락 | 지수 백오프 재연결, heartbeat, 5분 이상 실패 시 REST 폴링 자동 폴백 + 사용자 알림 |
| Foreground Service 배터리 이슈 | 사용자 불만 | 장 시간대(09:00~15:30 KST / 미장 22:30~05:00 KST)에만 연결, 비장시간 종료 |
| 모델 응답 품질 (한국어·국내장 컨텍스트) | 코칭 가치 ↓ | 시스템 프롬프트 + few-shot 예시 강화 |
| 매매기록 손실 | 신뢰 붕괴 | Phase 1부터 자동 로컬 백업(앱 외부 저장소 export) |
| API 키 유출 | 계좌 정보 노출 (조회 권한만이라도 민감) | EncryptedSharedPreferences + Phase 1 후반 KeyStore 마이그레이션 |
