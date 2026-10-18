# Android Mock MVP 구현 계획

- 작성일: 2026-10-18
- 상태: **계획 / 미구현**
- 연관 PR: [#12 — 모바일 조사·아키텍처 문서](https://github.com/fiverocks-dev/ai-usage-monitor/pull/12)
- 설계 기준: [모바일 아키텍처 v1](./mobile-architecture-v1.md), [ADR-0001](./ADR-0001-mobile-monorepo.md)

## 목표

기존 Windows 프로그램, `src/`, 루트 `Cargo.toml`, `ci.yml`, `release.yml`을 유지하면서 `apps/android/`에 인증·외부 API 의존성이 **전혀 없는** Kotlin/Jetpack Compose 앱과 Glance 위젯의 첫 골격을 추가한다.

## 초기 구현 범위

| 구성 요소 | 범위 | 완료 조건 |
|---|---|---|
| Android 프로젝트 | Gradle Kotlin DSL, Compose, 최소한의 앱 엔트리 | 지정된 Android SDK에서 빌드 가능 |
| 모델 | `ProviderId`, `QuotaSnapshot`, `QuotaStatus` | Claude Code, Codex, Antigravity Gemini, Antigravity Claude 구별 |
| Mock 데이터 | 모든 Provider의 5h/7d 등 **예시** window | 실데이터나 인증정보 없음 |
| Dashboard | Provider 카드, 잔여율, reset 시각, 마지막 성공 시각, 수동 갱신 | null과 0%를 다르게 표시 |
| Widget | Glance, 인증정보 없는 표시용 snapshot cache | 캐시에서 UI 갱신 가능 |
| 상태 | Fresh, Stale, AuthRequired, RateLimited, Offline, Unsupported | 상태별 텍스트/시각·접근성 설명 |
| 테스트 | 파서 아닌 모델·상태·뷰 모델 테스트 | null/0/10/100/NaN/120%·타임스탬프 시나리오 |
| 문서 | 한국어 우선 README, Mock 사용법 | 로컬 빌드 및 테스트 절차 명확 |

첫 단계에서 WorkManager 네트워크 polling, Keystore credential, 정식 OAuth, 비공식 endpoint, 사용자 쿠키 수동 입력, 동적 인증정보 가져오기를 구현하지 않는다. Kotlin과 Rust의 FFI 역시 Core 분리 효과 검증 이후에 결정한다.

## 권장 작업 분할

1. **Android 스캐폴드** — `apps/android/` 신규 구조, Mock 빌드, 한국어 README. Windows 파일 변경 금지.
2. **Dashboard** — 네 Provider 카드, QuotaSnapshot 상태·접근성, Mock 변경 버튼.
3. **위젯** — 스냅샷 저장소와 Glance projection, stale/missing 정보 표시.
4. **테스트·CI** — 경계 상태 테스트, Android 빌드 검증 및 경로별 실행. Windows CI/release는 영향 없어야 한다.
5. **후속 별도 작업** — Provider 정책 승인, Rust Core 추출, 백그라운드 작업, iOS.

## 검증 및 회귀 방지

- Windows PR [#10](https://github.com/fiverocks-dev/ai-usage-monitor/pull/10)과 [#11](https://github.com/fiverocks-dev/ai-usage-monitor/pull/11)의 병합/CI 영향을 별도로 확인한다.
- Android Mock PR에서는 실제 Provider 요청이 없음을 정적 검사하고, 인증정보가 소스·로그·위젯에 없는지 확인한다.
- 스케줄러 실행 간격은 처음부터 약속하지 않는다.
- 조직 [아티팩트·릴리스 정책](https://github.com/fiverocksgames/devops-standards/blob/main/ci/ARTIFACT_RELEASE_POLICY.md)을 적용해 CI의 검증과 배포 권한을 분리한다.
- 코드 수정은 문서 PR과 분리된 브랜치에서 진행한다.

## 작업 추적 제약

2026-10-18 현재 GitHub Issue 생성 시 API에서 `410 Issues has been disabled in this repository`가 반환되었다. 저장소 설정을 임의로 바꾸지 않는다. Issues가 활성화되기 전까지는 문서 PR #12의 이 계획 파일과 추후 Android 구현 PR을 기준으로 작업한다. 이 오류는 코드 문제나 도구 권한 오류라고 단정하지 않는다.
