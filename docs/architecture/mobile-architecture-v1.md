# AI Usage 모바일 아키텍처 v1

- 작성일: 2026-10-16
- 상태: **상세 설계 제안 / 미구현**
- 의사결정: [ADR-0001](./ADR-0001-mobile-monorepo.md)
- 근거: [Provider 인증 현황](../research/provider-auth-matrix-2026-10-16.md), [Android 소스 감사](../research/android-oss-source-audit-2026-10-15.md)

## 제품 제약

1. 네 가지 독립 표시 대상을 유지한다: `claude_code`, `codex`, `antigravity_gemini`, `antigravity_claude`. Gemini CLI와 Antigravity Gemini, Claude Code와 Antigravity Claude는 서로 다른 quota다.
2. MVP는 Local-first 및 자체 백엔드 0개로 시작한다. 화면 활성 시 수동 갱신과 OS가 허용하는 최선의 백그라운드 갱신만 제공한다.
3. 공식 허용 근거가 없는 Provider 인증이나 비공개 quota endpoint를 운영 기능으로 구현하지 않는다.
4. Rust 추출 과정에서 기존 Windows `ai-usage.exe` 명칭, 설치기, taskbar 복구, CI와 릴리스 동작을 유지한다.
5. 토큰·쿠키·인증 헤더는 소스, 로그, 위젯, 알림, 진단, 크래시 데이터에 남기지 않는다.

## 구성 요소

```text
Android                              iOS (향후)
Compose 대시보드                     SwiftUI 대시보드
Glance 위젯                          WidgetKit / App Group 캐시
WorkManager                          BGAppRefreshTask
Android 인증 보안 저장소             iOS Keychain
       |                                  |
       +--------- 앱별 동기화 조정 --------+
                          |
                ProviderCapabilityGate
                  (승인 상태 검사)
                          |
               승인된 Provider 어댑터
                CredentialProvider
                 HTTP / QuotaParser
                          |
                  ai-usage-core (Rust)
                  모델 / 정규화 / Alert
                          |
                   비밀정보 없는 캐시
                        /   \
                     위젯   대시보드
```

### 책임 분리

| 계층 | 책임 | 금지 사항 |
|---|---|---|
| Rust Core | 순수 모델·데이터 검증·잔여율 정규화·알림 중복 규칙 | OS API, 인증 파일, 토큰, 스케줄러 접근 |
| Provider Adapter | 허용된 endpoint의 프로토콜·응답 변환 | 확인되지 않은 API 활성화 |
| CredentialProvider | 해당 OS의 권한·보안 저장소 관리 | UI·위젯으로 원본 토큰 전달 |
| SyncCoordinator | 수동/예약 실행·중복 방지·재시도 | 무한 polling, 인증 실패 무차별 재시도 |
| SnapshotStore | Provider별 성공 결과 원자적 저장 | 실패를 잔여율 0으로 덮어쓰기 |
| UI/Widget | 캐시된 데이터 표현·접근성 | credential 직접 읽기 |

## 공통 데이터 계약

```text
ProviderId:
  claude_code | codex | antigravity_gemini | antigravity_claude

ProviderCapability:
  auth: unknown | permitted | denied
  quota_api: unknown | permitted | denied
  distribution: unknown | permitted | denied
  evidence_urls: URL[]
  verified_on: date?

QuotaSnapshot:
  schema_version: 1
  provider: ProviderId
  window_id: string             # 5h / 7d / 모델별 식별자
  remaining_fraction: float?    # 0.0 ~ 1.0; null은 미확인
  reset_at_utc: epoch_seconds?
  fetched_at_utc: epoch_seconds?
  state: fresh | stale | auth_required | rate_limited |
         offline | unavailable | unsupported | invalid_response

AlertKey:
  provider + window_id + reset_at_utc + threshold
```

**핵심 불변식:** `0%`는 실제 소진이 검증된 경우에만 사용하고, 조회 이력이 없으면 `null`로 둔다. NaN·무한대·0~1 범위를 벗어난 비율은 조용히 보정하여 정상값처럼 보이지 말고 비정상 응답으로 처리한다. UI는 사용률·잔여율 변환을 수행하지 않는다.

한 Provider의 실패가 다른 Provider의 성공 결과를 막지 않는다. 마지막 성공 시각은 Provider/Window별로 유지하고, UTC 저장 후 사용자 언어·시간대로 표시한다.

## 인터페이스 초안

```text
CredentialProvider:
  auth_state(provider)
  authorize(provider)            # 허용된 인증 방식만
  access_token(provider) -> secret handle
  revoke(provider)

QuotaProvider:
  capability(provider) -> ProviderCapability
  fetch(provider, credential_handle) -> Result<QuotaSnapshot[], Error>

SnapshotStore:
  read(provider)
  commit(provider, successful_snapshots)
  mark_stale(provider, reason)   # 기존 값 삭제 금지

SyncCoordinator:
  manual_sync(provider_set)
  scheduled_sync(provider_set)
  enforce_backoff_and_dedup()

WidgetProjection:
  from_cache(snapshots, now) -> UI-only data
```

Credential의 원문은 HTTP transport 내부에서만 사용한다. Antigravity 자격 증명이 Gemini/Claude 모델 버킷에 함께 쓰이더라도 표시 상태와 알림 키는 분리한다.

## Android 구현

- `apps/android/`: Kotlin + Jetpack Compose, Glance, WorkManager, Repository, Keystore 기반 보안 저장소.
- 초기 화면: Provider 카드 네 개, 각 quota window, 남은 비율, 리셋 시간, 마지막 갱신 시각, 로그인 필요/만료/오류/미지원 상태, 수동 갱신.
- 첫 배경 동기화: 네트워크 제약과 백오프를 갖춘 15~30분 **best-effort** 주기 작업. Foreground Service를 기본으로 사용하지 않는다. Doze·제조사 최적화·강제 종료 시 지연 또는 미실행 가능성을 UX에 표시한다.
- 갱신 결과 저장 후 위젯을 업데이트한다. 수동 갱신과 예약 동기화는 중복 실행을 병합한다.
- 알림: 잔여 10/20/30% 임계값, Provider+Window+리셋 시각+임계값 기준 중복 방지. 즉시 알림을 보장하지 않는다.
- 토큰 저장소와 위젯 캐시를 분리하고 백업·진단·로그 유출을 테스트한다. 예전 `EncryptedSharedPreferences` 사용 코드를 그대로 복사하지 않고 현재 지원되는 Keystore 기반 방식을 검토한다.

## iOS 후속 구현

- SwiftUI 대시보드, WidgetKit 홈/잠금 화면 위젯, 비밀정보 없는 App Group 캐시.
- 토큰은 Keychain에만 저장한다. `BGAppRefreshTask`는 OS의 기회적 실행이며 고정 간격이 아니다.
- Live Activity/Dynamic Island를 자유로운 네트워크 polling 경로로 사용하지 않는다.
- iOS 빌드·서명에는 macOS/Xcode 실행 환경이 필요하며 기존 Windows self-hosted runner로 대체할 수 없다.

## 동기화와 오류 처리

```text
OS 스케줄러 또는 사용자 요청
 -> Capability 승인 상태 검사
 -> Credential 유효성 / 중복 실행 / Rate Limit 확인
 -> Provider 요청(시간 제한 적용)
 -> 응답 구조 검증, Rust Core 정규화
 -> 성공한 Provider의 캐시만 원자적으로 저장
 -> 위젯 갱신 / 임계값 알림 검사
 -> 실패한 Provider는 원래 값을 보존하고 stale/error 부여
 -> 다음 허용 시각에 조건부 재시도
```

`401/403`과 정책 거부는 기계적으로 재시도하지 않는다. `429`는 `Retry-After` 초 또는 HTTP 날짜를 해석하고, 횟수 제한·지수 백오프·지터·취소 가능 대기를 적용한다. 원본 에러 응답·URL·헤더가 로그에 비밀정보를 포함하지 않도록 주의한다.

## Windows 호환성 및 Rust 추출

1. 현재 Windows 패키지를 유지한 채 순수 Core crate와 고정 입력 fixture 테스트를 추가한다.
2. 한 Provider씩 Core 정규화 함수 호출로 교체하고 기존 화면 결과를 fixture와 비교한다.
3. 네 가지 표시 대상, threshold, 초기화 시간, 언어, Credential 탐색에 대한 회귀 테스트를 완료한다.
4. PR #10이 main에 반영되고 Windows 빌드·설치·릴리스 동등성이 검증된 뒤에만 `apps/windows/` 이동을 별도 PR로 검토한다.
5. Rust ↔ Kotlin/Swift FFI는 이득이 입증될 때 도입한다. 초기 Mock은 JSON fixture만으로도 충분하다.

## CI 및 배포

- Android Mock 단계에서 Windows `ci.yml`, `release.yml`은 변경하지 않는다.
- 별도 `android-ci.yml`을 추가할 때 `apps/android/**`, `crates/ai-usage-core/**` 등 관련 경로에만 실행한다.
- Rust 단위 테스트, Gradle 빌드/린트/테스트, 승인된 실기기 테스트를 분리한다.
- 아티팩트는 정확한 commit SHA를 기준으로 식별한다. 일상 CI와 실제 공개 배포의 권한을 분리한다.
- 릴리스 서명 키, 인증 자료 및 Apple 프로비저닝 파일은 Git에 넣지 않는다.
- Windows 검증 항목: 실행 파일 이름, 제품 메타데이터, 설치·삭제·업데이트, WSL 인증 경로, taskbar 복구.

## 단계별 검증 표

| 단계 | 산출물 | 통과 기준 |
|---|---|---|
| 0 | ADR 및 설계 문서 | 리뷰 후 main 반영 |
| 1 | Android Mock 앱·위젯 | 오류·만료·미지원 표시와 수동 갱신 검증 |
| 2 | Rust Core | 정규화 테스트, Windows 회귀 없음 |
| 3 | Provider 인증 | 공식 승인 증빙, 미승인 기능 비활성화 |
| 4 | 백그라운드 및 알림 | Doze·비행기 모드·재부팅·배터리·중복 알림 테스트 |
| 5 | 공개 배포 | 개인정보·스토어 정책·서명·접근성 확인 |

필수 테스트 시나리오: 데이터 미조회(0%와 구분), 10%·0% 잔여량, 리셋 경계, 잘못된 120% 응답, 401/429, 일부 Provider 성공, 시간대/DST 변화, 강제 종료, 로그아웃 후 위젯, 앱 업그레이드.

## 미해결 사항

- 4개 Provider 모두 제3자 모바일 구독 quota 접근 권한은 확정되지 않았다.
- Android 최소 SDK, 앱 ID, iOS 최소 버전 및 출시 인증 설정은 추후 결정한다.
- 공개 모노레포와 비공개 개발 저장소 분리는 접근권한·배포 요구가 바뀌면 재검토한다.
- 실제 구현 시작 및 스토어 심사 전에 공식 플랫폼 문서 최신 내용을 다시 확인한다.
