# ADR-0001: AI Usage 모바일 모노레포 단계적 도입

- 작성일: 2026-10-16
- 상태: **아키텍처 방향 결정 / 구현 전**
- 대상: `fiverocks-dev/ai-usage-monitor`
- 연관: [모바일 상세 설계](./mobile-architecture-v1.md), [공개 프로젝트 조사](../research/mobile-oss-landscape-2026-10-15.md), [Android 코드 분석](../research/android-oss-source-audit-2026-10-15.md), [Provider 인증 검토](../research/provider-auth-matrix-2026-10-16.md)

## 배경

기존 Windows 프로그램 `ai-usage.exe`는 작업 표시줄에 네이티브 자식 창을 배치한다. 표시 대상은 Claude Code, Codex, Antigravity Gemini, Antigravity Claude 네 가지다. 현재 CLI 설정 파일, WSL, Windows Credential Manager에 의존하는 인증 정보 탐색 방식은 모바일 샌드박스에서 그대로 쓸 수 없다. 루트 `Cargo.toml`, `src/`, `build.rs`와 Windows용 `ci.yml`, `release.yml`이 존재한다. 2026-10-16 확인 당시 작업 표시줄 복구 PR #10은 미병합 상태였다.

## 결정

**현재 저장소를 제품 모노레포로 유지한다.** Android를 Kotlin/Jetpack Compose 및 Glance로 우선 개발하고, 초기에 Mock quota와 수동 갱신 UX만 구현한다. iOS는 이후 SwiftUI/WidgetKit으로 별도 구현한다. Rust 공유 계층은 이미 검증된 순수 모델·정규화·알림 규칙만 점진적으로 추출한다. 인증, 생명주기, 백그라운드 작업, 위젯, 플랫폼 UI는 각각의 앱이 소유한다.

기본 구조는 **Local-first / 자체 백엔드 없음**이다. 제3자 Provider의 실제 인증·quota 조회는 공식 허용 근거가 확보된 기능에만 활성화한다. 기술적 작동 사례만으로 공개 배포 권한을 추정하지 않는다.

Windows의 현재 루트 패키지는 Android 초기 작업 중 옮기지 않는다. Windows 코드를 `apps/windows/`로 이전하는 작업은 빌드·설치·배포 동등성을 검증하는 별도 PR에서 진행한다.

## 목표 디렉터리 구조 (아직 구현되지 않음)

```text
ai-usage-monitor/
  apps/
    windows/                # 안정화 후 기존 Windows 코드 이동
    android/                # Kotlin, Compose, Glance, WorkManager
    ios/                    # 후속 SwiftUI, WidgetKit, BGTask
  crates/
    ai-usage-core/          # OS/인증 의존성이 없는 순수 도메인
    ai-usage-providers/     # 공식적으로 허용된 Provider 어댑터만
  docs/
    architecture/
    research/
  .github/workflows/
    ci.yml                  # 기존 Windows CI 유지
    release.yml             # 기존 Windows 릴리스 유지
    android-ci.yml          # 후속, 경로 필터 적용
    ios-ci.yml              # 후속, macOS 빌드 환경 필요
```

## 고려했으나 현재 채택하지 않은 대안

| 대안 | 장점 | 지금 채택하지 않는 이유 |
|---|---|---|
| 새 `ai-usage-mobile` 저장소 | 권한·릴리스 독립 | 공유 코드 경계 미확정, 문서·Issue 분산 |
| Flutter 동시 개발 | UI 코드 공유 | 위젯·인증·백그라운드는 여전히 플랫폼별 구현 필요 |
| Kotlin Multiplatform 전면 재작성 | 공통 런타임 확대 | 기존 Rust와 공유 책임 중복, 조기 이관 비용 |
| Desktop Bridge 기본 구조 | 모바일 직접 토큰 접근 감소 | PC 상시 실행·연결·페어링 보안 부담 |
| Rust Workspace 전체 즉시 이전 | 디렉터리 일관성 | Windows CI·설치·릴리스 회귀 위험 |

## 초기 범위에서 제외

실제 Provider 로그인 및 토큰/쿠키 가져오기, 비공식 quota API 호출, 서버 배포, Windows 작업 표시줄 UI 재작성, 정확한 1·5·15분 백그라운드 갱신 보장, 모바일 공개 출시.

## 결정의 영향

- 장점: 로드맵·데이터 규칙·연구 자료 통합, 초기 이전 위험 최소화, 서버 운영비 없이 시작 가능.
- 비용: 다중 언어 빌드 관리, 경로별 CI·릴리스 분리, 장기적으로 복잡해질 가능성.
- 현재 저장소는 **공개**되어 있다. 토큰·서명 키·Provisioning Profile·개인 인증 자료를 커밋하지 않는다. 접근권한·공개 범위가 실제로 달라지면 비공개 별도 저장소를 재검토한다.
- CI 산출물과 릴리스는 [조직 아티팩트/릴리스 정책](https://github.com/fiverocksgames/devops-standards/blob/main/ci/ARTIFACT_RELEASE_POLICY.md)의 정확한 커밋 SHA 및 권한 분리 원칙을 따른다.

## 저장소 분리 재검토 조건

모바일이 독립 제품·팀이 되거나, 출시 주기와 권한 통제가 충돌하거나, 실제 공유 테스트 코드가 적거나, 공개/비공개 경계가 다르거나, 경로별 CI 분리가 비효율적일 때 새로운 ADR로 판단한다.

## 단계별 승인 조건

1. **G0:** Windows PR #10 처리, 기존 빌드·CI 기준선 확보.
2. **G1:** Provider별 공식 인증·quota API·재배포 허용 근거. 미확인은 기능 비활성화.
3. **G2:** Android Mock Dashboard/Widget와 오류·만료 상태 검증.
4. **G3:** Rust 공통 계층 단위 테스트와 Windows 회귀 검증.
5. **G4:** 실기기 백그라운드·배터리·자격 증명 저장 검증.
6. **G5:** 스토어 심사, 개인정보 고지, 제3자 서비스 정책 검토.

## 기준 문서

공통 정책은 [devops-standards](https://github.com/fiverocksgames/devops-standards)를 참조한다. 예상하지 못한 제약·장애는 [spec-underflow](https://github.com/fiverocks-dev/spec-underflow), 재사용 도구는 [fiverocks-dev/tools](https://github.com/fiverocks-dev/tools)를 확인한다. 이 ADR 자체는 개발 착수나 Provider 정책 우회를 승인하지 않는다.
