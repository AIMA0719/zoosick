# AICoachStock — 디자인 문서

> Show Me The PRD로 생성됨 (2026-05-13)
> 대상 기기: Galaxy Z Fold 7
> 핵심 컨셉: **온디바이스 Gemma 4 E4B + 한투 OpenAPI**로 만드는 개인 주식 코치 앱

---

## 한 줄 요약

매매 원칙·매매기록·시세를 폰 안에서만 다루며, AI 코치가 진입 검토·복기·손절 규율을 도와주는 **나만의 트레이딩 코치**.

---

## 문서 구성

| 문서 | 내용 | 언제 읽나 |
|------|------|----------|
| [01_PRD.md](./01_PRD.md) | 뭘 만드는지, 누가 쓰는지, 안 만드는 것 | 프로젝트 시작 전 / 스코프 흔들릴 때 |
| [02_DATA_MODEL.md](./02_DATA_MODEL.md) | 엔티티 9종 + 관계 + 분리 원칙 | DB·Room 설계할 때 |
| [03_PHASES.md](./03_PHASES.md) | Phase 1(풀세트 MVP) / 2(통계) / 3(고도화) | 개발 순서·로드맵 정할 때 |
| [04_PROJECT_SPEC.md](./04_PROJECT_SPEC.md) | 기술 스택, 디렉토리, "절대 하지 마" 목록 | AI에게 코드 시킬 때마다 |

---

## 핵심 결정 요약

| 항목 | 결정 |
|------|------|
| 플랫폼 | Android Only (Z Fold 7) |
| 매매 스타일 타깃 | 한국장 + 미장 혼합 (스윙/장투) |
| API 활용 | 한투 OpenAPI **WebSocket 실시간 시세** + REST (조회만, 주문 X) |
| AI 모델 | Gemma 4 E4B 온디바이스 (폴백: Gemma 3n E4B) |
| 추론 엔진 | MediaPipe LLM Inference API |
| 코치 역할 | 매매일지 복기 + 진입 체크리스트 + 손절/익절 규율 + 종목 Q&A (4종 모두) |
| MVP 범위 | 풀세트 (6~8주) |
| 기술 스택 | Kotlin + Compose + ViewModel/StateFlow + Hilt + Room+SQLCipher + Retrofit |
| 인증 | 로그인 없음 + 한투 API 키만 EncryptedSharedPreferences 로컬 저장 |
| 자동매매 | **금지** (Phase 1~3 전부) |

---

## 다음 단계

1. **선행 검증 (1주)** — Phase 1 본 개발 전에 다음 4가지를 작은 PoC로 확인
   - Gemma 4 E4B `.task` 파일을 MediaPipe LLM Inference API로 로드해서 추론되는가
   - 한투 OpenAPI Access Token + WebSocket approval_key 발급, 1종목 실시간 tick 수신 성공
   - Foreground Service가 Z Fold 7 / Android 15에서 WebSocket 30분 이상 유지 + 자동 재연결 동작
   - Room + SQLCipher passphrase를 Android Keystore에서 안전하게 발급/재사용하는가

2. **Phase 1 본 개발 (6~8주)** — [03_PHASES.md](./03_PHASES.md)의 "Phase 1 시작 프롬프트"를 복사해서 AI에게 전달

3. **자가 사용 1개월** — 본인 폰에서 실사용하며 매매기록·복기 데이터 축적

4. **Phase 2 진입 (3~4주)** — 누적 데이터 30건 이상이면 통계·시각화 단계

---

## 미결 사항 (NEEDS CLARIFICATION 종합)

### PRD 차원
- [ ] 모델 다운로드 호스트 URL + 정식 `.task` SHA-256 (Hugging Face 우선, 미러 1개)
- [ ] 다운로드 실패 시 Gemma 3n E4B 자동 폴백 제안 여부
- [ ] MediaPipe LLM Inference API의 Gemma 4 지원 시점 — 미지원 시 Gemma 3n E4B 폴백
- [ ] 한투 해외주식 실시간 시세 권한 (일반 가입자 = 15분 지연인지 확인)
- [ ] WebSocket 41종목 한도 검증 + 국내·해외 통합 연결 가능 여부
- [ ] WebSocket 5분 이상 연결 실패 시 REST 폴백 정책 확정
- [ ] 컨텍스트 길이 전략 (E4B 입력 토큰 한도 안에서 RAG 컨텍스트 선택)
- [ ] 감정 태그 enum 확정 (5개 고정 vs. 사용자 정의 허용)

### 데이터 모델 차원
- [ ] `rule_violations` 표현 (ID 배열 vs. {id, severity} 객체)
- [ ] 시세 캐시 영속성 (메모리 only vs. Room 짧은 TTL)
- [ ] 다중 보유 평단 처리 (`Trade` 쿼리 도출 vs. 별도 `Position` 테이블)
- [ ] `CoachMessage.context_refs` JSON vs. 정규화

### 스펙 차원
- [ ] Foreground Service 타입 (`dataSync` vs. `specialUse`)
- [ ] 앱 ID(applicationId) — 예: `com.myinfocar.aicoachstock`
- [ ] minSdk 확정 (Z Fold 7 단일 기기라 31~35 사이 자유)

---

## 주의 (Disclaimer)

- 본 문서·앱은 **개인 사용 목적**이며 투자 자문이 아닙니다.
- 모든 AI 출력은 참고용이며 매매 책임은 사용자 본인입니다.
- 외부 배포(Play Store 등) 시 자본시장법상 등록 요건을 별도 검토해야 합니다.
